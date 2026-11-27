package com.clxypro.vpn.core

import java.security.MessageDigest

/**
 * First-connect gate for the CLXY Quantum (Aether) core: the user enters one
 * of the issued serials and Quantum unlocks, the way the Hyper core unlocks
 * with an email + password sign-in. Asked once; [Prefs.quantumUnlocked]
 * remembers the unlock.
 *
 * Only SHA-256 hashes are stored here — the serials themselves never appear
 * in the source.
 */
object QuantumSerial {
    // Rotated 2026-11-27: the previous 5-char serials were burned — their
    // plaintext sat as comments next to the hashes in the public repo since
    // v1.5.8. The new serials are 12 chars; only their hashes live here.
    // The serials themselves are handed to the owner in chat, never in code.
    private val VALID_HASHES = setOf(
        "2b205ae7577ae954f03890d0e7e4d4dfefdd3d45c93fcd68f08f78769f4c8b46",
        "360ac5242559a218171410effbe784e396f4fcff5436e2d2d8006ce0454b45b5",
        "80bcbb7c73d861f37ef825b360eeeb6c85fd6d44ba3351ef221b0e850af5540a",
        "61dcae65d329297dffc6c5de28e38c80b341ddf1dfb466966ff74407e4f84c54",
        "5f5cac4c5ad557746e8bd9e7571289867fe47c6a8ad6e55f89a6525ae22e0488",
        "d8afb795806113f63292067b5b005760563f8a1675bb5b1ff8023a2e12890134",
        "d608322903567e9ad2e4971aacdd2042a47b79908b94662cd1f1321e76cd1d08",
    )

    fun isValid(code: String): Boolean {
        val normalized = code.trim().uppercase().filter { it.isLetterOrDigit() }
        if (normalized.isEmpty()) return false
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return hash in VALID_HASHES
    }
}
