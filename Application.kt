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
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory

fun main() {
    NewPipe.init(DownloaderImpl)

    embeddedServer(Netty, port = System.getenv("PORT")?.toInt() ?: 8080) {
        install(ContentNegotiation) { json() }
        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Get)
        }

        routing {
            get("/health") {
                call.respond(mapOf("status" to "ok"))
            }

            // Trending
            get("/trending") {
                val region = call.parameters["region"] ?: "FR"
                try {
                    val kiosk = ServiceList.YouTube.kioskList
                    kiosk.setCountryCode(region)
                    val extractor = kiosk.defaultKioskExtractor
                    extractor.fetchPage()
                    val items = extractor.initialPage.items.mapNotNull { item ->
                        try {
                            buildJsonObject {
                                put("url", "/watch?v=${item.url.substringAfter("v=")}")
                                put("title", item.name)
                                put("thumbnail", item.thumbnails.firstOrNull()?.url ?: "")
                                put("uploaderName", item.uploaderName ?: "")
                                put("uploaderUrl", "/channel/${item.uploaderUrl?.substringAfter("channel/") ?: ""}")
                                put("duration", item.duration)
                                put("views", item.viewCount)
                                put("uploadedDate", item.textualUploadDate ?: "")
                            }
                        } catch (e: Exception) { null }
                    }
                    call.respond(items)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            // Search
            get("/search") {
                val q = call.parameters["q"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing q"))
                try {
                    val searchHandler = ServiceList.YouTube.searchQHFactory
                        .fromQuery(q, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
                    val extractor = ServiceList.YouTube.getSearchExtractor(searchHandler)
                    extractor.fetchPage()
                    val items = extractor.initialPage.items.mapNotNull { item ->
                        try {
                            buildJsonObject {
                                put("url", "/watch?v=${item.url.substringAfter("v=")}")
                                put("title", item.name)
                                put("thumbnail", item.thumbnails.firstOrNull()?.url ?: "")
                                put("uploaderName", item.uploaderName ?: "")
                                put("uploaderUrl", "/channel/${item.uploaderUrl?.substringAfter("channel/") ?: ""}")
                                put("duration", item.duration)
                                put("views", item.viewCount)
                                put("uploadedDate", item.textualUploadDate ?: "")
                            }
                        } catch (e: Exception) { null }
                    }
                    call.respond(mapOf("items" to items))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            // Streams (video info)
            get("/streams/{videoId}") {
                val videoId = call.parameters["videoId"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing videoId"))
                try {
                    val url = "https://www.youtube.com/watch?v=$videoId"
                    val info = StreamInfo.getInfo(ServiceList.YouTube, url)

                    val videoStreams = info.videoStreams.filter { it.content != null }.map { s ->
                        buildJsonObject {
                            put("url", s.content)
                            put("quality", s.resolution ?: "360p")
                            put("mimeType", s.format?.mimeType ?: "video/mp4")
                        }
                    }

                    val related = info.relatedItems.take(20).mapNotNull { item ->
                        try {
                            buildJsonObject {
                                put("url", "/watch?v=${item.url.substringAfter("v=")}")
                                put("title", item.name)
                                put("thumbnail", item.thumbnails.firstOrNull()?.url ?: "")
                                put("uploaderName", item.uploaderName ?: "")
                                put("duration", item.duration)
                            }
                        } catch (e: Exception) { null }
                    }

                    call.respond(buildJsonObject {
                        put("title", info.name)
                        put("description", info.description?.content ?: "")
                        put("uploadDate", info.uploadDate?.date()?.time?.toString() ?: "")
                        put("uploader", info.uploaderName ?: "")
                        put("uploaderUrl", "/channel/${info.uploaderUrl?.substringAfter("channel/") ?: ""}")
                        put("uploaderAvatar", info.uploaderAvatars.firstOrNull()?.url ?: "")
                        put("uploaderSubscriberCount", info.uploaderSubscriberCount)
                        put("thumbnailUrl", "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg")
                        put("hls", info.hlsUrl ?: JsonNull)
                        put("duration", info.duration)
                        put("views", info.viewCount)
                        put("likes", info.likeCount)
                        put("videoStreams", JsonArray(videoStreams))
                        put("audioStreams", JsonArray(emptyList()))
                        put("relatedStreams", JsonArray(related))
                    })
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }
        }
    }.start(wait = true)
}
