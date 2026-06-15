package io.earlisreal.ejournal.demo

import io.earlisreal.ejournal.data.JsonCredentialsRepository
import io.earlisreal.ejournal.domain.marketdata.AlpacaProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.io.File
import kotlinx.coroutines.runBlocking

fun runCsvGenerator(args: Array<String>) {
    val httpClient = HttpClient(CIO)
    val credRepo = JsonCredentialsRepository(
        File(System.getProperty("user.home"), ".ejournal").toPath()
    )
    runBlocking { generateCsv(AlpacaProvider(httpClient, credRepo), args) }
    httpClient.close()
}
