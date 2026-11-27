package com.clxypro.vpn.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.FileProvider
import com.clxypro.vpn.BuildConfig
import com.clxypro.vpn.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object GithubUpdates {
    const val RELEASES_PAGE = "https://github.com/ClxybyCloner/ClxyPro/releases"
    private const val API = "https://api.github.com/repos/ClxybyCloner/ClxyPro/releases"
    private val apkName = Regex("""ClxyPro-(\d+\.\d+\.\d+)-(armeabi-v7a|arm64-v8a|universal)\.apk""")

    private val autoChecked = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastCheckMillis = AtomicLong(0L)
    private const val CHECK_INTERVAL_MS = 15 * 60 * 1000L

    data class Release(
        val version: String,
        val tag: String,
        /** ABI of the asset this release points at (arm64-v8a, armeabi-v7a, universal). */
        val abi: String = "universal",
        /** Direct download URL of that asset; blank when the API gave none. */
        val downloadUrl: String = "",
        /** Asset size in bytes, 0 when unknown. */
        val size: Long = 0L,
    ) {
        val page: String get() = "$RELEASES_PAGE/tag/$tag"
    }

    /**
     * One silent check per process. On a filtered line it simply fails; the
     * button in Settings is still there to retry with the tunnel on.
     */
    fun autoCheck() {
        if (!autoChecked.compareAndSet(false, true)) return
        scope.launch {
            runCatching { check() }.getOrNull()?.let { publish(it) }
        }
    }

    /**
     * Arman-style check: runs on every app open and every successful
     * connect, so a new release is noticed the same day instead of only
     * after the process restarts. A newer release updates AppState (home
     * banner + Profile badge) and posts one notification per version.
     * Throttled to one look per 15 minutes so a burst of foreground
     * returns or reconnects does not hammer the GitHub API.
     */
    fun maybeCheck(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCheckMillis.get() < CHECK_INTERVAL_MS) return
        lastCheckMillis.set(now)
        val appContext = context.applicationContext
        scope.launch {
            val release = runCatching { check() }.getOrNull() ?: return@launch
            publish(release)
            if (compareVersions(release.version, BuildConfig.VERSION_NAME) > 0 &&
                Prefs.lastNotifiedUpdate != release.version
            ) {
                Prefs.lastNotifiedUpdate = release.version
                Notifications.showUpdate(appContext, release)
            }
        }
    }

    /** True only when the release is strictly newer than the installed version. */
    fun isNewer(release: Release): Boolean =
        compareVersions(release.version, BuildConfig.VERSION_NAME) > 0

    /** Records what a check found, so the home screen badge agrees with Settings. */
    fun publish(release: Release) {
        AppState.update { it.copy(update = if (isNewer(release)) release else null) }
    }

    /**
     * State of the in-app APK download, held here — not in the update
     * dialog. The download used to launch in the dialog's own
     * rememberCoroutineScope, so leaving the page cancelled it and the
     * partial file was deleted: the user had to start over. It now runs
     * on this object's app-scoped IO scope and survives the dialog; any
     * screen observes the same state and sees the live progress or the
     * finished file on return.
     */
    sealed interface DownloadState {
        val version: String
        data object Idle : DownloadState {
            override val version: String = ""
        }
        data class Downloading(val progress: Int, override val version: String) : DownloadState
        data class Done(val file: File, override val version: String) : DownloadState
        data class Failed(val message: String, override val version: String) : DownloadState
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    /**
     * Starts the APK download for [release] on the app-scoped scope.
     * No-op while any download is running, and no-op when this version
     * is already downloaded — the dialog then just offers Install.
     */
    fun startDownload(context: Context, release: Release) {
        val current = _downloadState.value
        if (current is DownloadState.Downloading) return
        if (current is DownloadState.Done && current.version == release.version &&
            current.file.exists()
        ) return
        val appContext = context.applicationContext
        _downloadState.value = DownloadState.Downloading(-1, release.version)
        scope.launch {
            try {
                val apk = download(appContext, release) { pct ->
                    _downloadState.value = DownloadState.Downloading(pct, release.version)
                }
                _downloadState.value = DownloadState.Done(apk, release.version)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _downloadState.value =
                    DownloadState.Failed(t.message ?: "Download failed", release.version)
            }
        }
    }

    /**
     * Clears a finished download state (after a fresh check, or to retry
     * a failure). A running download is never touched — it keeps going.
     */
    fun clearDownload() {
        if (_downloadState.value !is DownloadState.Downloading) {
            _downloadState.value = DownloadState.Idle
        }
    }

    /**
     * Only proxy mode needs the local front: a VPN already carries this request,
     * and there the front is opened just for LAN sharing, so it is usually absent.
     * Shared by the release check and the APK download.
     */
    private fun githubProxy(): Proxy =
        if (AppState.state.connected && Prefs.mode == AppState.Mode.PROXY) {
            Proxy(Proxy.Type.HTTP, InetSocketAddress(Prefs.LOOPBACK, Prefs.httpPort))
        } else {
            Proxy.NO_PROXY
        }

    suspend fun check(): Release = withContext(Dispatchers.IO) {
        val proxy = githubProxy()
        var newest: Release? = null
        for (page in 1..10) {
            ensureActive()
            val connection = URL("$API?per_page=100&page=$page").openConnection(proxy) as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "ClxyPro")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("GitHub HTTP ${connection.responseCode}")
                }
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                ensureActive()
                candidates(json, Build.SUPPORTED_ABIS.toList()).forEach { release ->
                    val previous = newest
                    if (previous == null || isBetterRelease(release, previous)) {
                        newest = release
                    }
                }
                if (connection.getHeaderField("Link")?.contains("rel=\"next\"") != true) {
                    return@withContext newest ?: throw IOException("No compatible versioned APK release")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Incomplete GitHub release list")
    }

    /**
     * Newest version wins; on a tie a versioned tag beats the rolling "latest"
     * tag, and the asset matching this device's own ABI beats the universal
     * one, so the download button fetches the smallest APK that runs here.
     */
    private fun isBetterRelease(candidate: Release, current: Release): Boolean {
        val byVersion = compareVersions(candidate.version, current.version)
        if (byVersion != 0) return byVersion > 0
        if ((current.tag == "latest") != (candidate.tag == "latest")) return candidate.tag != "latest"
        return abiRank(candidate.abi) > abiRank(current.abi)
    }

    private fun abiRank(abi: String): Int = when (abi) {
        Build.SUPPORTED_ABIS.firstOrNull() -> 2
        in Build.SUPPORTED_ABIS -> 1
        "universal" -> 0
        else -> -1
    }

    /**
     * Downloads the release's APK for this device straight from GitHub, with
     * progress (0..100, or -1 while the size is unknown). The file lands in
     * cacheDir/updates/ and is verified against the asset size and by asking
     * the package manager to parse it, so a truncated download never reaches
     * the installer. Throws IOException on any failure; a failed download
     * leaves no partial file behind.
     */
    suspend fun download(
        context: Context,
        release: Release,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        require(release.downloadUrl.isNotBlank()) { "Release has no download URL" }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val file = File(dir, "ClxyPro-${release.version}-${release.abi}.apk")
        val connection = URL(release.downloadUrl).openConnection(githubProxy()) as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.setRequestProperty("User-Agent", "ClxyPro")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub download HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.size
            var readSoFar = 0L
            var lastReported = -1
            onProgress(-1)
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        readSoFar += read
                        if (total > 0) {
                            val pct = ((readSoFar * 100) / total).toInt().coerceIn(0, 100)
                            if (pct != lastReported) {
                                lastReported = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
            if (total > 0 && readSoFar != total) {
                throw IOException("Download incomplete ($readSoFar of $total bytes)")
            }
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    file.absolutePath, PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            } ?: throw IOException("Downloaded file is not a valid APK")
            if (info.packageName != context.packageName) {
                throw IOException("Downloaded APK is for another app (${info.packageName})")
            }
            onProgress(100)
            file
        } catch (t: Throwable) {
            runCatching { file.delete() }
            throw t
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Fires the system installer for a downloaded APK. Returns false when the
     * user has not allowed installs from this app yet — the caller should
     * then send them to the unknown-apps setting.
     */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
        return true
    }

    /** Opens the per-app "allow installs from this app" setting. */
    fun openUnknownSourcesSetting(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    internal fun candidates(json: String, supportedAbis: List<String>): List<Release> {
        val releases = JSONArray(json)
        val result = mutableListOf<Release>()
        for (index in 0 until releases.length()) {
            val release = releases.getJSONObject(index)
            val tag = release.getString("tag_name")
            if (release.getBoolean("draft")) continue
            if (tag != "latest" && (release.getBoolean("prerelease") ||
                    !Regex("""v?\d+\.\d+\.\d+""").matches(tag))) continue
            val assets = release.getJSONArray("assets")
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.getJSONObject(assetIndex)
                if (asset.getString("state") != "uploaded" || asset.getLong("size") <= 0) continue
                val match = apkName.matchEntire(asset.getString("name")) ?: continue
                val (version, abi) = match.destructured
                val compatible = abi in supportedAbis || (abi == "universal" &&
                    supportedAbis.any { it == "arm64-v8a" || it == "armeabi-v7a" })
                if (!compatible || (tag != "latest" && tag.removePrefix("v") != version)) continue
                compareVersions(version, version)
                result += Release(
                    version,
                    tag,
                    abi,
                    asset.optString("browser_download_url", ""),
                    asset.getLong("size"),
                )
            }
        }
        return result
    }

    internal fun compareVersions(left: String, right: String): Int {
        fun parts(value: String): List<Long> {
            // Version names may carry a pre-release suffix (1.5.31-rc7);
            // compare the numeric part only.
            val base = value.substringBefore('-')
            require(Regex("""\d+\.\d+\.\d+""").matches(base))
            return base.split('.').map { it.toLong() }
        }
        return parts(left).zip(parts(right)).firstOrNull { it.first != it.second }
            ?.let { it.first.compareTo(it.second) } ?: 0
    }
}
