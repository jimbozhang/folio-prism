package com.jimbozhang.folioprism.data.web

import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.nio.file.Files
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebDataServerTest {

    private lateinit var dir: File
    private lateinit var api: FakeApi
    private lateinit var server: WebDataServer
    private lateinit var base: String

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("web-server").toFile()
        File(dir, "index.html").writeText("<html><body>Folio Prism</body></html>")
        File(dir, "js").mkdirs()
        File(dir, "js/app.js").writeText("console.log('ok')")
        api = FakeApi()
        val port = ServerSocket(0).use { it.localPort }
        server = WebDataServer(port, DirectoryWebAssets(dir), api)
        server.start()
        base = "http://127.0.0.1:$port"
    }

    @After
    fun tearDown() {
        server.stop()
        dir.deleteRecursively()
    }

    @Test
    fun staticAssetsAreServed() {
        val root = request("GET", "/")
        assertEquals(200, root.first)
        assertTrue(root.second.contains("Folio Prism"))
        assertTrue(root.third.orEmpty().contains("text/html"))

        val script = request("GET", "/js/app.js")
        assertEquals(200, script.first)
        assertTrue(script.third.orEmpty().contains("application/javascript"))
    }

    @Test
    fun unknownAndTraversalPathsAreRejected() {
        assertEquals(404, request("GET", "/nope").first)
        assertEquals(404, request("GET", "/js/../secret.txt").first)
        assertEquals(404, request("GET", "/%2e%2e/secret.txt").first)
        assertEquals(405, request("POST", "/").first)
    }

    @Test
    fun overviewIsServedAsJson() {
        val response = request("GET", "/api/overview")
        assertEquals(200, response.first)
        assertTrue(response.third.orEmpty().contains("application/json"))
        assertTrue(response.second.contains("\"totalValueCny\":\"1.00\""))
        assertEquals(405, request("POST", "/api/overview").first)
    }

    @Test
    fun chartRequiresValidDates() {
        val missing = request("GET", "/api/chart?type=assets")
        assertEquals(400, missing.first)
        assertTrue(missing.second.contains("from"))

        val invalid = request("GET", "/api/chart?type=assets&from=bad&to=2026-01-01")
        assertEquals(400, invalid.first)
        assertTrue(invalid.second.contains("日期格式无效"))
    }

    @Test
    fun chartPassesParameters() {
        val response = request(
            "GET",
            "/api/chart?type=category_assets&from=2026-01-01&to=2026-02-01&categoryId=cat_gold",
        )
        assertEquals(200, response.first)
        assertEquals("category_assets", api.lastChartType)
        assertEquals(LocalDate.of(2026, 1, 1), api.lastChartFrom)
        assertEquals(LocalDate.of(2026, 2, 1), api.lastChartTo)
        assertEquals("cat_gold", api.lastChartCategoryId)
    }

    @Test
    fun eventWritesReachApi() {
        val body = """{"type":"expense","date":"2026-11-19 10:00","expenseAmount":"1"}"""
        val created = request("POST", "/api/events", body)
        assertEquals(200, created.first)
        assertEquals(body, api.lastCreateBody)
        assertTrue(created.second.contains("\"version\":\"v1\""))

        val updated = request("PUT", "/api/events/evt_1", body)
        assertEquals(200, updated.first)
        assertEquals("evt_1", api.lastUpdateId)
        assertEquals(body, api.lastUpdateBody)

        val deleted = request("DELETE", "/api/events/evt_1")
        assertEquals(200, deleted.first)
        assertEquals("evt_1", api.lastDeleteId)
    }

    @Test
    fun writeResultsMapToStatusCodes() {
        api.writeResult = WriteResult.Invalid("金额无效")
        val invalid = request("POST", "/api/events", "{}")
        assertEquals(400, invalid.first)
        assertTrue(invalid.second.contains("金额无效"))

        api.writeResult = WriteResult.NotFound
        assertEquals(404, request("DELETE", "/api/events/missing").first)

        api.writeResult = WriteResult.Failed("磁盘错误")
        assertEquals(500, request("DELETE", "/api/events/x").first)
    }

    @Test
    fun flowsAndExpensesAreServed() {
        assertEquals(200, request("GET", "/api/flows?from=2026-01-01&to=2026-02-01").first)
        assertEquals(200, request("GET", "/api/expenses?from=2026-01-01&to=2026-02-01").first)
        assertEquals(400, request("GET", "/api/flows?from=2026-02-01&to=2026-01-01").first)
    }

    @Test
    fun dividendsAreServed() {
        val response = request("GET", "/api/dividends?from=2026-01-01&to=2026-02-01")
        assertEquals(200, response.first)
        assertTrue(response.second.contains("\"totalCny\":\"0\""))
        assertEquals(LocalDate.of(2026, 1, 1), api.lastDividendsFrom)
        assertEquals(LocalDate.of(2026, 2, 1), api.lastDividendsTo)
        assertEquals(400, request("GET", "/api/dividends?from=2026-02-01&to=2026-01-01").first)
        assertEquals(405, request("POST", "/api/dividends?from=2026-01-01&to=2026-02-01").first)
    }

    @Test
    fun downloadAndUploadAreServed() {
        val download = request("GET", "/api/download")
        assertEquals(200, download.first)
        assertTrue(download.third.orEmpty().contains("application/json"))
        assertTrue(download.second.contains("\"events\":[]"))

        val connection = URL(base + "/api/download").openConnection() as HttpURLConnection
        assertEquals("attachment; filename=\"folio-prism.json\"",
            connection.getHeaderField("Content-Disposition"))

        val uploaded = request("POST", "/api/upload", """{"events":[]}""")
        assertEquals(200, uploaded.first)
        assertTrue(uploaded.second.contains("\"version\""))
        assertEquals("""{"events":[]}""", api.lastUploadBody)
        assertEquals(405, request("GET", "/api/upload").first)
        assertEquals(405, request("POST", "/api/download").first)
    }

    @Test
    fun unknownApiPathsAreNotFound() {
        assertEquals(404, request("GET", "/api/nope").first)
        assertEquals(405, request("GET", "/api/events").first)
        assertEquals(405, request("GET", "/api/quotes/refresh").first)
    }

    @Test
    fun apkUploadRouting() {
        // Body is "PK" + two control bytes + "body" = 8 bytes.
        val uploaded = request("PUT", "/api/apk?name=folio-prism.apk", "PK\u0003\u0004body")
        assertEquals(200, uploaded.first)
        assertTrue(uploaded.second.contains("\"fileName\":\"folio-prism.apk\""))
        assertTrue(uploaded.second.contains("\"sizeBytes\":8"))
        assertEquals("folio-prism.apk", api.lastApkName)
        assertEquals(8L, api.lastApkSourceLength)
        assertEquals(405, request("POST", "/api/apk?name=x.apk", "x").first)
        // The POST above left a body on the keep-alive connection; the next
        // request on the same socket must still parse cleanly.
        assertEquals(405, request("GET", "/api/apk?name=x.apk").first)

        api.apkResult = ApkUploadResult.Invalid("文件名无效")
        val invalid = request("PUT", "/api/apk?name=bad", "body")
        assertEquals(400, invalid.first)
        assertTrue(invalid.second.contains("文件名无效"))

        api.apkResult = ApkUploadResult.Failed("磁盘错误")
        val failed = request("PUT", "/api/apk?name=x.apk", "body")
        assertEquals(500, failed.first)
        assertTrue(failed.second.contains("磁盘错误"))
    }

    private fun request(method: String, path: String, body: String? = null): Triple<Int, String, String?> {
        val connection = URL(base + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.readText().orEmpty()
        return Triple(code, text, connection.contentType)
    }

    private class FakeApi : WebApi {
        var lastCreateBody: String? = null
        var lastUpdateId: String? = null
        var lastUpdateBody: String? = null
        var lastDeleteId: String? = null
        var lastChartType: String? = null
        var lastChartFrom: LocalDate? = null
        var lastChartTo: LocalDate? = null
        var lastChartCategoryId: String? = null
        var lastDividendsFrom: LocalDate? = null
        var lastDividendsTo: LocalDate? = null
        var lastUploadBody: String? = null
        var lastApkName: String? = null
        var lastApkSourceLength: Long? = null
        var writeResult: WriteResult = WriteResult.Ok("v1")
        var apkResult: ApkUploadResult? = null

        override suspend fun portfolio(): String = """{"events":[]}"""

        override suspend fun replaceData(rawJson: String): WriteResult {
            lastUploadBody = rawJson
            return WriteResult.Ok("v-uploaded")
        }

        override suspend fun overview(): OverviewDto = OverviewDto(
            version = "v1",
            updatedAt = "2026-11-19T00:00:00+08:00",
            asOf = "2026-11-19",
            counts = CountsDto(0, 0, 0),
            totalValueCny = "1.00",
            dividendValueCny = "0",
            dividendShare = "0",
            portfolioValueCny = "1.00",
            portfolioShare = "1",
            hasMissingPrices = false,
            categories = emptyList(),
            positions = emptyList(),
            institutions = emptyList(),
            currencies = emptyList(),
            quote = QuoteStatusDto(),
        )

        override suspend fun chart(
            type: String,
            from: LocalDate,
            to: LocalDate,
            categoryId: String?,
        ): ChartDto {
            lastChartType = type
            lastChartFrom = from
            lastChartTo = to
            lastChartCategoryId = categoryId
            return ChartDto(type = type, from = from.toString(), to = to.toString())
        }

        override suspend fun flows(from: LocalDate, to: LocalDate): FlowsDto {
            require(!from.isAfter(to)) { "开始日期不能晚于结束日期" }
            return FlowsDto(emptyList())
        }

        override suspend fun expenses(from: LocalDate, to: LocalDate): ExpensesDto {
            require(!from.isAfter(to)) { "开始日期不能晚于结束日期" }
            return ExpensesDto(emptyList())
        }

        override suspend fun dividends(from: LocalDate, to: LocalDate): DividendsDto {
            require(!from.isAfter(to)) { "开始日期不能晚于结束日期" }
            lastDividendsFrom = from
            lastDividendsTo = to
            return DividendsDto(
                totalCny = "0",
                count = 0,
                incomplete = false,
                averagePerMonthCny = null,
                withdrawnCny = "0",
                withdrawnCount = 0,
                retainedCny = "0",
                retainedCount = 0,
            )
        }

        override suspend fun refreshQuotes(): QuoteStatusDto = QuoteStatusDto()

        override suspend fun createEvent(body: String): WriteResult {
            lastCreateBody = body
            return writeResult
        }

        override suspend fun updateEvent(id: String, body: String): WriteResult {
            lastUpdateId = id
            lastUpdateBody = body
            return writeResult
        }

        override suspend fun deleteEvent(id: String): WriteResult {
            lastDeleteId = id
            return writeResult
        }

        override suspend fun uploadApk(fileName: String, source: File?): ApkUploadResult {
            lastApkName = fileName
            lastApkSourceLength = source?.length()
            return apkResult ?: ApkUploadResult.Ok(fileName, source?.length() ?: 0L)
        }
    }
}
