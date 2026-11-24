import dev.edulinktwo.CredentialCodec
import dev.edulinktwo.CredentialStore
import dev.edulinktwo.BlobBox
import dev.edulinktwo.SavedCredential
import java.io.File

/** Fake blob box: reversible XOR seal with a magic header; open() throws on
 * bad data, simulating a wiped keystore key. */
class FakeBox : BlobBox {
    private val magic = byteArrayOf(0x42, 0x4F, 0x58)
    override fun seal(plain: ByteArray): ByteArray =
        magic + plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun open(blob: ByteArray): ByteArray {
        require(blob.size >= 3 && blob.copyOfRange(0, 3).contentEquals(magic)) { "cannot decrypt" }
        return blob.copyOfRange(3, blob.size).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }
}

var failures = 0
fun check(cond: Boolean, name: String) {
    if (cond) println("PASS $name") else { println("FAIL $name"); failures++ }
}

fun main() {
    val dir = File(System.getProperty("java.io.tmpdir"), "cred-test-" + System.nanoTime())
    dir.mkdirs()
    val file = File(dir, "credentials.enc")

    // 1. Round-trip with hostile characters: tabs, newlines, unicode, quotes.
    run {
        val s = CredentialStore(file, FakeBox())
        val nasty = "p@ss\twith\nnewlines\u00E9\u20AC\"'\\ "
        s.save("alice@school.example", "l_alice", nasty)
        val got = s.get("alice@school.example")
        check(got != null && got.username == "l_alice" && got.password == nasty, "round-trip hostile password")
    }

    // 2. Multiple accounts stay independent; persisted across store instances.
    run {
        val s = CredentialStore(file, FakeBox())
        s.save("bob@school.example", "l_bob", "second")
        val s2 = CredentialStore(file, FakeBox()) // reload from disk
        check(s2.get("alice@school.example") != null, "first account survives reload")
        check(s2.get("bob@school.example")?.password == "second", "second account independent")
        check(s2.has("bob@school.example") && !s2.has("nobody@x"), "has() true/false")
    }

    // 3. Overwrite same key replaces, does not duplicate.
    run {
        val s = CredentialStore(file, FakeBox())
        s.save("bob@school.example", "l_bob", "rotated")
        check(s.get("bob@school.example")?.password == "rotated", "overwrite replaces")
    }

    // 4. Remove forgets; missing get returns null.
    run {
        val s = CredentialStore(file, FakeBox())
        s.remove("bob@school.example")
        check(!s.has("bob@school.example") && s.get("bob@school.example") == null, "remove forgets")
        check(s.has("alice@school.example"), "remove leaves others")
    }

    // 5. Codec: unknown version byte -> empty.
    check(CredentialCodec.decode(byteArrayOf(99, 65, 66)).isEmpty(), "codec rejects unknown version")

    // 6. Codec: garbage body -> empty, never throws.
    check(CredentialCodec.decode(byteArrayOf(1) + "not base64!!!".toByteArray()).isEmpty(), "codec rejects garbage body")

    // 7. Codec: empty input -> empty.
    check(CredentialCodec.decode(ByteArray(0)).isEmpty(), "codec empty input")

    // 8. Store: undecryptable blob (key wiped) -> empty and file deleted.
    run {
        val f2 = File(dir, "credentials2.enc")
        f2.writeBytes(byteArrayOf(1, 2, 3, 4)) // no magic header
        val s = CredentialStore(f2, FakeBox())
        check(s.get("anything") == null, "undecryptable blob yields empty")
        check(!f2.exists(), "undecryptable blob deleted")
    }

    // 9. Codec round-trip of an empty map.
    check(CredentialCodec.decode(CredentialCodec.encode(emptyMap())).isEmpty(), "empty map round-trip")

    // 10. File on disk is not plaintext: username/password bytes absent from blob.
    run {
        val raw = file.readBytes()
        val asText = String(raw, Charsets.ISO_8859_1)
        check(!asText.contains("l_alice"), "blob contains no plaintext username")
    }

    dir.deleteRecursively()
    println(if (failures == 0) "ALL TESTS PASSED" else "$failures FAILURES")
    if (failures > 0) kotlin.system.exitProcess(1)
}
