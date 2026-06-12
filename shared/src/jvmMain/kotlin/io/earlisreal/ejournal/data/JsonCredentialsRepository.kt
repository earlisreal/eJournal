package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.repository.AlpacaCredentials
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class JsonCredentialsRepository(private val dir: Path) : CredentialsRepository {

    private val file: Path = dir.resolve("credentials.json")
    private val json = Json { prettyPrint = true }

    override fun getAlpacaCredentials(): AlpacaCredentials? {
        val alpaca = readRoot()["alpaca"] as? JsonObject ?: return null
        val keyId = (alpaca["keyId"] as? JsonPrimitive)?.contentOrNull ?: return null
        val secretKey = (alpaca["secretKey"] as? JsonPrimitive)?.contentOrNull ?: return null
        if (keyId.isBlank() || secretKey.isBlank()) return null
        return AlpacaCredentials(keyId, secretKey)
    }

    override fun setAlpacaCredentials(credentials: AlpacaCredentials) {
        val updated = buildJsonObject {
            readRoot().forEach { (key, value) -> if (key != "alpaca") put(key, value) }
            putJsonObject("alpaca") {
                put("keyId", credentials.keyId)
                put("secretKey", credentials.secretKey)
            }
        }
        writeAtomically(json.encodeToString(JsonObject.serializer(), updated))
    }

    private fun readRoot(): JsonObject =
        runCatching { json.parseToJsonElement(Files.readString(file)) as JsonObject }
            .getOrDefault(JsonObject(emptyMap()))

    private fun writeAtomically(content: String) {
        Files.createDirectories(dir)
        val temp = Files.createTempFile(dir, "credentials", ".tmp")
        Files.writeString(temp, content)
        restrictToOwner(temp)
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun restrictToOwner(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        } // non-POSIX filesystems (Windows): temp files are already user-scoped
    }
}
