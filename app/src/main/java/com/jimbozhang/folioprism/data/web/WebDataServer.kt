package com.jimbozhang.folioprism.data.web

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString

/**
 * LAN HTTP layer for the web workspace: serves the packaged SPA and routes the
 * JSON API. All business logic lives in [WebApi]; this class only parses
 * requests and maps results to status codes.
 */
class WebDataServer(
    port: Int,
    private val assets: WebAssets,
    private val api: WebApi,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response =
        if (session.uri.startsWith("/api/")) apiResponse(session) else staticResponse(session)

    private fun staticResponse(session: IHTTPSession): Response {
        if (session.method != Method.GET) {
            return plain(Status.METHOD_NOT_ALLOWED, "仅支持 GET")
        }
        val path = when (val uri = session.uri) {
            "/", "" -> "index.html"
            else -> uri.removePrefix("/")
        }
        if (path.contains("..") || path.contains('\\') || path.contains('\u0000')) {
            return plain(Status.NOT_FOUND, "未知路径")
        }
        val bytes = assets.read(path) ?: return plain(Status.NOT_FOUND, "未知路径")
        return newFixedLengthResponse(
            Status.OK,
            webContentType(path),
            ByteArrayInputStream(bytes),
            bytes.size.toLong(),
        ).apply { addHeader("Cache-Control", "no-cache") }
    }

    private fun apiResponse(session: IHTTPSession): Response = runBlocking {
        try {
            route(session)
        } catch (e: IllegalArgumentException) {
            error(Status.BAD_REQUEST, e.message ?: "参数错误")
        } catch (e: Exception) {
            error(Status.INTERNAL_ERROR, e.message ?: "服务内部错误")
        }
    }

    private suspend fun route(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        return when (uri) {
            "/api/portfolio" -> {
                if (method != Method.GET) return methodNotAllowed()
                val body = api.portfolio()
                    ?: return plain(Status.NOT_FOUND, "数据文件不存在")
                jsonText(Status.OK, body)
            }

            "/api/overview" -> {
                if (method != Method.GET) return methodNotAllowed()
                json(api.overview())
            }

            "/api/chart" -> {
                if (method != Method.GET) return methodNotAllowed()
                val type = session.parameters["type"]?.firstOrNull() ?: "assets"
                val categoryId = session.parameters["categoryId"]?.firstOrNull()
                json(
                    api.chart(
                        type = type,
                        from = dateParam(session, "from"),
                        to = dateParam(session, "to"),
                        categoryId = categoryId,
                    ),
                )
            }

            "/api/flows" -> {
                if (method != Method.GET) return methodNotAllowed()
                json(api.flows(dateParam(session, "from"), dateParam(session, "to")))
            }

            "/api/expenses" -> {
                if (method != Method.GET) return methodNotAllowed()
                json(api.expenses(dateParam(session, "from"), dateParam(session, "to")))
            }

            "/api/quotes/refresh" -> {
                if (method != Method.POST) return methodNotAllowed()
                json(api.refreshQuotes())
            }

            "/api/events" -> {
                if (method != Method.POST) return methodNotAllowed()
                writeResult(api.createEvent(readBody(session)))
            }

            else -> {
                if (uri.startsWith("/api/events/")) {
                    val id = uri.removePrefix("/api/events/").trim('/')
                    when (method) {
                        Method.PUT -> writeResult(api.updateEvent(id, readBody(session)))
                        Method.DELETE -> writeResult(api.deleteEvent(id))
                        else -> methodNotAllowed()
                    }
                } else {
                    plain(Status.NOT_FOUND, "未知路径")
                }
            }
        }
    }

    private fun dateParam(session: IHTTPSession, name: String): LocalDate {
        val text = session.parameters[name]?.firstOrNull()
            ?: throw IllegalArgumentException("缺少参数 $name")
        return try {
            LocalDate.parse(text)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("日期格式无效：$text")
        }
    }

    private fun readBody(session: IHTTPSession): String {
        val files = mutableMapOf<String, String>()
        try {
            session.parseBody(files)
        } catch (e: Exception) {
            throw IllegalArgumentException("请求体读取失败")
        }
        // NanoHTTPD puts POST bodies under "postData", but PUT bodies are
        // written to a temp file referenced by "content".
        files["postData"]?.let { return it }
        val tempPath = files["content"] ?: return ""
        val tempFile = File(tempPath)
        return try {
            tempFile.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalArgumentException("请求体读取失败")
        } finally {
            tempFile.delete()
        }
    }

    private fun writeResult(result: WriteResult): Response = when (result) {
        is WriteResult.Ok -> json(VersionDto(result.version))
        is WriteResult.Invalid -> error(Status.BAD_REQUEST, result.message)
        WriteResult.NotFound -> error(Status.NOT_FOUND, "流水不存在")
        is WriteResult.Failed -> error(Status.INTERNAL_ERROR, result.message)
    }

    private inline fun <reified T> json(value: T): Response =
        jsonText(Status.OK, WEB_JSON.encodeToString(value))

    private fun error(status: Status, message: String): Response =
        jsonText(status, WEB_JSON.encodeToString(ErrorDto(message)))

    private fun jsonText(status: Status, body: String): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body)

    private fun plain(status: Status, body: String): Response =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", body)

    private fun methodNotAllowed(): Response =
        plain(Status.METHOD_NOT_ALLOWED, "不支持的请求方法")
}
