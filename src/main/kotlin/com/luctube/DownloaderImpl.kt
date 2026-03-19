package com.luctube

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

object DownloaderImpl : Downloader() {
    override fun execute(request: Request): Response {
        val url = URL(request.url())
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.requestMethod = request.httpMethod()

        // Headers
        request.headers().forEach { (key, values) ->
            values.forEach { conn.setRequestProperty(key, it) }
        }
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

        // Body
        if (request.dataToSend() != null) {
            conn.doOutput = true
            conn.outputStream.write(request.dataToSend())
        }

        val responseCode = conn.responseCode
        val responseBody = try {
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }

        val headers = conn.headerFields
            .filterKeys { it != null }
            .mapValues { it.value }

        return Response(responseCode, conn.responseMessage, headers, responseBody, request.url())
    }
}
