package com.luctube

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.InfoItem

fun main() {
    NewPipe.init(DownloaderImpl)
    val port = System.getenv("PORT")?.toInt() ?: 8080

    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) { json() }
        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Get)
            allowHeader(HttpHeaders.ContentType)
        }

        routing {

            get("/health") {
                call.respond(mapOf("status" to "ok"))
            }

            get("/trending") {
                try {
                    val kioskList = ServiceList.YouTube.kioskList
                    val extractor = kioskList.defaultKioskExtractor
                    extractor.fetchPage()
                    val result = extractor.initialPage.items.mapNotNull { item ->
                        streamItemToJson(item)
                    }
                    call.respond(result)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "error")))
                }
            }

            get("/search") {
                val q = call.parameters["q"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing q"))
                try {
                    val linkHandler = ServiceList.YouTube.searchQHFactory.fromQuery(q, listOf("videos"), "")
                    val extractor = ServiceList.YouTube.getSearchExtractor(linkHandler)
                    extractor.fetchPage()
                    val items = extractor.initialPage.items.mapNotNull { item ->
                        streamItemToJson(item)
                    }
                    call.respond(mapOf("items" to items))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "error")))
                }
            }

            get("/streams/{videoId}") {
                val videoId = call.parameters["videoId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing videoId"))
                try {
                    val url = "https://www.youtube.com/watch?v=$videoId"
                    val info = StreamInfo.getInfo(ServiceList.YouTube, url)

                    val videoStreams = info.videoStreams
                        .filter { !it.content.isNullOrEmpty() }
                        .map { s ->
                            buildJsonObject {
                                put("url", s.content)
                                put("quality", s.resolution ?: "360p")
                                put("mimeType", s.format?.mimeType ?: "video/mp4")
                            }
                        }

                    val audioStreams = info.audioStreams
                        .filter { !it.content.isNullOrEmpty() }
                        .map { s ->
                            buildJsonObject {
                                put("url", s.content)
                                put("quality", "${s.averageBitrate}kbps")
                                put("mimeType", s.format?.mimeType ?: "audio/mp4")
                            }
                        }

                    val related = info.relatedItems.take(20).mapNotNull { item ->
                        streamItemToJson(item)
                    }

                    val hlsUrl = try { info.hlsUrl } catch (e: Exception) { null }

                    call.respond(buildJsonObject {
                        put("title", info.name ?: "")
                        put("description", info.description?.content ?: "")
                        put("uploadDate", info.textualUploadDate ?: "")
                        put("uploader", info.uploaderName ?: "")
                        put("uploaderUrl", "/channel/${info.uploaderUrl?.substringAfterLast("/") ?: ""}")
                        put("uploaderAvatar", JsonPrimitive(info.uploaderAvatars.firstOrNull()?.url ?: ""))
                        put("uploaderSubscriberCount", info.uploaderSubscriberCount)
                        put("thumbnailUrl", "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg")
                        put("hls", if (!hlsUrl.isNullOrEmpty()) JsonPrimitive(hlsUrl) else JsonNull)
                        put("duration", info.duration)
                        put("views", info.viewCount)
                        put("likes", info.likeCount)
                        put("videoStreams", JsonArray(videoStreams))
                        put("audioStreams", JsonArray(audioStreams))
                        put("relatedStreams", JsonArray(related))
                    })
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "error")))
                }
            }
        }
    }.start(wait = true)
}

fun streamItemToJson(item: InfoItem): JsonObject? {
    return try {
        val url = item.url ?: return null
        val vid = url.substringAfter("v=").substringBefore("&").take(20)
        if (vid.isBlank()) return null

        val streamItem = item as? StreamInfoItem

        buildJsonObject {
            put("url", "/watch?v=$vid")
            put("title", item.name ?: "")
            put("thumbnail", item.thumbnails.firstOrNull()?.url
                ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg")
            put("uploaderName", streamItem?.uploaderName ?: "")
            put("uploaderUrl", "/channel/${streamItem?.uploaderUrl?.substringAfterLast("/") ?: ""}")
            put("duration", streamItem?.duration ?: 0L)
            put("views", streamItem?.viewCount ?: 0L)
            put("uploadedDate", streamItem?.textualUploadDate ?: "")
        }
    } catch (e: Exception) {
        null
    }
}
