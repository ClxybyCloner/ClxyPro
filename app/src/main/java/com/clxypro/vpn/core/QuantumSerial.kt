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
    private val VALID_HASHES = setOf(
        // A7K2M
        "257513a9243b57cae302d1184a6f6a48278a46641a011b1983deeb867662770b",
        // X9P4L
        "e381e02fa08f80dc9c7145c94ac0120ef03b5e1cce674d1d0a8affaf9cdcd628",
        // B6R8Q
        "29f370ad23af6db549eb30fd408a0f365be5d10287973d37250d8e723ad84ea7",
        // T3N5W
        "b28eb80ceabba90c7ded955528cfd6fe36e8a2d2b1e7a12da16db3cb97b6196f",
        // H2V7C
        "f4b3839a62b6501f8bcde9b91d33c0c5419e24694d2ea454a043ef0699fdb60b",
        // M8D4Z
        "c319e48a33297d5ad98486b9e90b72f3b93acbe66c581af1adfd42dd98b02f1b",
        // Q5F9J
        "4b16347b1ff0b22346c2805e101abcf65ef11339e015997085cc73597704d6b6",
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
