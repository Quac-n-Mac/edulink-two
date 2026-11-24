package dev.edulinktwo

import java.io.File

/** One remembered sign-in: username + password for an account key (user@host). */
data class SavedCredential(val username: String, val password: String)

/**
 * Pure, dependency-free codec for the credential map, so it is testable off-device.
 * Envelope: byte 0 = format version, then one record per line:
 * b64(key) TAB b64(username) TAB b64(password) LF
 * Base64 fields make the format immune to tabs/newlines/unicode in real passwords.
 * Decoding never throws: unknown version or any corrupt record yields an empty map,
 * and the app simply falls back to manual entry.
 */
object CredentialCodec {
    const val VERSION: Byte = 1
    private val b64e = java.util.Base64.getEncoder()
    private val b64d = java.util.Base64.getDecoder()

    fun encode(creds: Map<String, SavedCredential>): ByteArray {
        val sb = StringBuilder()
        for ((key, c) in creds) {
            sb.append(b64e.encodeToString(key.toByteArray(Charsets.UTF_8)))
            sb.append('\t')
            sb.append(b64e.encodeToString(c.username.toByteArray(Charsets.UTF_8)))
            sb.append('\t')
            sb.append(b64e.encodeToString(c.password.toByteArray(Charsets.UTF_8)))
            sb.append('\n')
        }
        return byteArrayOf(VERSION) + sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): Map<String, SavedCredential> {
        if (bytes.isEmpty() || bytes[0] != VERSION) return emptyMap()
        val text = runCatching { String(bytes, 1, bytes.size - 1, Charsets.UTF_8) }
            .getOrElse { return emptyMap() }
        val out = LinkedHashMap<String, SavedCredential>()
        for (line in text.split('\n')) {
            if (line.isEmpty()) continue
            val parts = line.split('\t')
            // One bad record means the blob is not ours; fail closed, never guess.
            if (parts.size != 3) return emptyMap()
            val key = runCatching { String(b64d.decode(parts[0]), Charsets.UTF_8) }.getOrElse { return emptyMap() }
            val u = runCatching { String(b64d.decode(parts[1]), Charsets.UTF_8) }.getOrElse { return emptyMap() }
            val p = runCatching { String(b64d.decode(parts[2]), Charsets.UTF_8) }.getOrElse { return emptyMap() }
            out[key] = SavedCredential(u, p)
        }
        return out
    }
}

/** Encrypt/decrypt a blob. The Android implementation is KeystoreBox; tests inject a fake. */
interface BlobBox {
    fun seal(plain: ByteArray): ByteArray
    fun open(blob: ByteArray): ByteArray
}

/**
 * Remembered sign-ins, encrypted at rest. The file holds only the sealed blob;
 * plaintext credentials never touch disk, the repo or logs.
 */
class CredentialStore(private val file: File, private val box: BlobBox) {
    private val lock = Any()

    private fun loadAll(): MutableMap<String, SavedCredential> = synchronized(lock) {
        if (!file.exists()) return LinkedHashMap()
        val blob = runCatching { file.readBytes() }.getOrElse { return LinkedHashMap() }
        val plain = runCatching { box.open(blob) }.getOrElse {
            // Undecryptable (keystore key wiped, corrupt file): drop it and start empty.
            runCatching { file.delete() }
            return LinkedHashMap()
        }
        CredentialCodec.decode(plain).toMutableMap()
    }

    private fun persist(map: Map<String, SavedCredential>) {
        synchronized(lock) {
            file.parentFile?.mkdirs()
            file.writeBytes(box.seal(CredentialCodec.encode(map)))
        }
    }

    fun get(key: String): SavedCredential? = loadAll()[key]

    fun has(key: String): Boolean = loadAll().containsKey(key)

    fun save(key: String, username: String, password: String) {
        val m = loadAll()
        m[key] = SavedCredential(username, password)
        persist(m)
    }

    fun remove(key: String) {
        val m = loadAll()
        if (m.remove(key) != null) persist(m)
    }
}
