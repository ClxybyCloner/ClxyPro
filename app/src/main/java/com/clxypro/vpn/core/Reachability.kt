package com.clxypro.vpn.core

import android.os.SystemClock
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * A core can report itself ready while the exit it chose carries nothing: the
 * SOCKS handshake is answered locally, and on a filtered link the blocking only
 * shows up at the first real request. That is the tunnel that "connects" but
 * leaves YouTube and Facebook shut, so the app asks for them through the core's
 * own SOCKS port before it says connected.
 *
 * The request is a hand-written SOCKS5 CONNECT that carries the hostname, so the
 * exit resolves it. Resolving on the phone first would test the local resolver
 * as much as the tunnel, and a filtered resolver would fail a tunnel that is
 * perfectly good. The TLS handshake then checks the certificate against the
 * name, exactly as a browser would. Any HTTP status proves the path end to end —
 * a 403 or a 404 is still a working tunnel — while a timeout, a reset or a TLS
 * failure does not. Both sites have to answer: a path that carries google but
 * drops the two blocked ones is the very failure this check exists for.
 */
object Reachability {
    data class Outcome(val ok: Boolean, val detail: String)

    private val TARGETS = listOf(
        "www.youtube.com" to "youtube",
        "www.facebook.com" to "facebook"
    )

    private const val HTTPS_PORT = 443
    private const val PER_REQUEST_MS = 6_000
    private const val HOP_CONNECT_MS = 2_000
    private const val BUDGET_MS = 45_000L
    private const val ROUNDS = 2
    private const val RETRY_PAUSE_MS = 1_500L

    /**
     * Blocking. [proxy] is the core's local SOCKS5 address; [cancelled] is polled
     * between requests so turning the tunnel off does not wait out the budget.
     *
     * The two sites are probed in parallel: asked one after another they cost
     * their latencies added up, and on a slow link that was ~5 s of the
     * connect time. There used to be a second round after a 3 s settle pause,
     * to catch a relay that answers once and drops seconds later — but that
     * cost ~6 s on every connect, and the tunnel watchdog already fails a
     * tunnel that dies right after connecting, so the second round is gone.
     */
    fun verify(proxy: String, cancelled: () -> Boolean = { false }): Outcome {
        val port = proxy.substringAfterLast(':').toIntOrNull()
        val host = proxy.substringBeforeLast(':').trim().removeSurrounding("[", "]")
        if (port == null || port !in 1..65535 || host.isBlank()) {
            // Nothing local to dial, so there is nothing this can prove. Let the
            // core's own watchdog be the judge rather than blocking the connect.
            return Outcome(true, "no local proxy to probe ($proxy)")
        }
        val deadline = SystemClock.elapsedRealtime() + BUDGET_MS
        val pending = TARGETS.toMutableList()
        val pool = Executors.newFixedThreadPool(TARGETS.size)
        try {
            repeat(ROUNDS) { round ->
                if (pending.isEmpty()) return@repeat
                if (cancelled()) return Outcome(false, "cancelled")
                if (SystemClock.elapsedRealtime() >= deadline) return@repeat
                val futures = pending.map { (name, label) ->
                    pool.submit<Boolean> { probe(host, port, name) } to label
                }
                val answered = mutableListOf<String>()
                futures.forEach { (future, label) ->
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    val ok = if (remaining <= 0) false else runCatching {
                        future.get(remaining, TimeUnit.MILLISECONDS)
                    }.getOrDefault(false)
                    if (ok) {
                        AppState.log("reachability: $label answered through the tunnel")
                        answered += label
                    }
                }
                pending.removeAll { (_, label) -> label in answered }
                if (pending.isNotEmpty() && round < ROUNDS - 1 &&
                    SystemClock.elapsedRealtime() < deadline
                ) {
                    runCatching { Thread.sleep(RETRY_PAUSE_MS) }
                }
            }
        } finally {
            pool.shutdownNow()
        }
        if (pending.isNotEmpty()) {
            return Outcome(false, "no answer from ${pending.joinToString(", ") { it.second }}")
        }
        return Outcome(true, "youtube and facebook answered")
    }

    /**
     * Opens the core's SOCKS port, asks it for `host:443` by name, and speaks
     * HTTPS over that stream. Name-based CONNECT is the whole point: the exit
     * resolves and dials, so the probe sees the same path a browser would.
     */
    private fun probe(hopHost: String, hopPort: Int, host: String): Boolean = runCatching {
        val raw = Socket()
        raw.connect(InetSocketAddress(hopHost, hopPort), HOP_CONNECT_MS)
        raw.use { socket ->
            socket.soTimeout = PER_REQUEST_MS
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // Greeting: SOCKS5 with no authentication.
            output.write(byteArrayOf(5, 1, 0))
            output.flush()
            val greeting = ByteArray(2)
            if (!readFully(input, greeting)) return false
            if (greeting[0] != 5.toByte() || greeting[1] != 0.toByte()) return false

            // CONNECT, address type 3 (domain name), then the port big-endian.
            val name = host.toByteArray(Charsets.US_ASCII)
            val request = ByteArray(7 + name.size)
            request[0] = 5
            request[1] = 1
            request[2] = 0
            request[3] = 3
            request[4] = name.size.toByte()
            name.copyInto(request, 5)
            request[5 + name.size] = (HTTPS_PORT shr 8).toByte()
            request[6 + name.size] = HTTPS_PORT.toByte()
            output.write(request)
            output.flush()

            val reply = ByteArray(4)
            if (!readFully(input, reply)) return false
            if (reply[0] != 5.toByte() || reply[1] != 0.toByte()) return false
            // Skip the bound address the reply carries before the stream starts.
            val skipped = when (reply[3].toInt() and 0xFF) {
                1 -> 4 + 2
                3 -> {
                    val length = input.read()
                    if (length < 0) return false
                    length + 2
                }
                4 -> 16 + 2
                else -> return false
            }
            if (!readFully(input, ByteArray(skipped))) return false

            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(socket, host, HTTPS_PORT, true) as SSLSocket
            tls.use { secure ->
                secure.soTimeout = PER_REQUEST_MS
                val params = secure.sslParameters
                // Asks for SNI (host is passed to createSocket) and rejects a
                // certificate that does not belong to the name.
                params.endpointIdentificationAlgorithm = "HTTPS"
                secure.sslParameters = params
                secure.startHandshake()

                val out = secure.outputStream
                out.write(
                    (
                        "HEAD / HTTP/1.1\r\n" +
                            "Host: $host\r\n" +
                            "User-Agent: ClxyPro\r\n" +
                            "Accept: */*\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(Charsets.US_ASCII)
                )
                out.flush()
                val status = secure.inputStream.bufferedReader(Charsets.US_ASCII).readLine()
                status != null && status.startsWith("HTTP/")
            }
        }
    }.getOrDefault(false)

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }
}
