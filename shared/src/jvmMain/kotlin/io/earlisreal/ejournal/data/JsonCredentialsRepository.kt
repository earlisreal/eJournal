package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.repository.AlpacaBrokerSecrets
import io.earlisreal.ejournal.data.repository.AlpacaMarketDataCredentials
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.PortfolioBrokerCredentials
import io.earlisreal.ejournal.data.repository.TradeZeroBrokerCredentials
import io.earlisreal.ejournal.domain.model.Broker
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

    override fun getAlpacaMarketDataCredentials(): AlpacaMarketDataCredentials? {
        val alpaca = readRoot()["alpaca"] as? JsonObject ?: return null
        val keyId = alpaca.string("keyId") ?: return null
        val secretKey = alpaca.string("secretKey") ?: return null
        return AlpacaMarketDataCredentials(keyId, secretKey)
    }

    override fun setAlpacaMarketDataCredentials(credentials: AlpacaMarketDataCredentials) {
        val root = readRoot()
        val existing = root["alpaca"] as? JsonObject
        val updated = replaceObject(root, "alpaca") {
            existing?.forEach { (key, value) -> if (key != "keyId" && key != "secretKey") put(key, value) }
            put("keyId", credentials.keyId)
            put("secretKey", credentials.secretKey)
        }
        writeAtomically(json.encodeToString(JsonObject.serializer(), updated))
    }

    override fun getPortfolioBrokerCredentials(credentialRef: String): PortfolioBrokerCredentials? {
        val entry = (readRoot()["portfolioBrokers"] as? JsonObject)?.get(credentialRef) as? JsonObject
            ?: return null
        val keyId = entry.string("keyId") ?: return null
        val secretKey = entry.string("secretKey") ?: return null
        return when (entry.string("broker")?.uppercase()) {
            Broker.ALPACA.name, Broker.ALPACA.id.uppercase() -> AlpacaBrokerSecrets(keyId, secretKey)
            Broker.TRADEZERO.name, Broker.TRADEZERO.id.uppercase() ->
                TradeZeroBrokerCredentials(keyId, secretKey)
            else -> null
        }
    }

    override fun setPortfolioBrokerCredentials(
        credentialRef: String,
        credentials: PortfolioBrokerCredentials,
    ) {
        val root = readRoot()
        val existingEntries = root["portfolioBrokers"] as? JsonObject
        val updatedEntries = buildJsonObject {
            existingEntries?.forEach { (key, value) -> if (key != credentialRef) put(key, value) }
            putJsonObject(credentialRef) {
                put("broker", credentials.broker.name)
                put("keyId", credentials.keyId)
                put("secretKey", credentials.secretKey)
            }
        }
        val updated = replaceObject(root, "portfolioBrokers") {
            updatedEntries.forEach { (key, value) -> put(key, value) }
        }
        writeAtomically(json.encodeToString(JsonObject.serializer(), updated))
    }

    override fun deletePortfolioBrokerCredentials(credentialRef: String) {
        val root = readRoot()
        val existingEntries = root["portfolioBrokers"] as? JsonObject ?: return
        if (credentialRef !in existingEntries) return
        val updated = replaceObject(root, "portfolioBrokers") {
            existingEntries.forEach { (key, value) -> if (key != credentialRef) put(key, value) }
        }
        writeAtomically(json.encodeToString(JsonObject.serializer(), updated))
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun replaceObject(
        root: JsonObject,
        key: String,
        content: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): JsonObject = buildJsonObject {
        root.forEach { (existingKey, value) -> if (existingKey != key) put(existingKey, value) }
        putJsonObject(key, content)
    }

    private fun readRoot(): JsonObject =
        runCatching { json.parseToJsonElement(Files.readString(file)) as? JsonObject }
            .getOrNull()
            ?: JsonObject(emptyMap())

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
        }
    }
}
