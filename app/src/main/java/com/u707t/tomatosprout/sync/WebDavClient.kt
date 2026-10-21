package com.u707t.tomatosprout.sync

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import javax.inject.Inject
import javax.inject.Singleton

class WebDavException(val code: Int, override val message: String) : Exception(message)

data class RemoteFile(val name: String, val isDirectory: Boolean, val etag: String?)

data class WebDavConfig(
    val server: String,
    val username: String,
    val password: String,
    val basePath: String,
)

/**
 * 自封装 WebDAV：PROPFIND / GET / PUT / DELETE / MKCOL，读取 ETag。
 * 不依赖任何托管后端 SDK，也不在未配置时发起请求。
 */
@Singleton
class WebDavClient @Inject constructor() {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun rootUrl(config: WebDavConfig): HttpUrl = buildUrl(config, "")

    fun testConnection(config: WebDavConfig): Result<Unit> = runCatching {
        val request = request(
            config, rootUrl(config), "PROPFIND",
            PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType()),
        ) {
            header("Depth", "0")
        }
        http.newCall(request).execute().use { response ->
            when (response.code) {
                200, 207 -> Unit
                401, 403 -> throw WebDavException(response.code, "认证失败（${response.code}），请检查用户名和密码")
                404 -> throw WebDavException(404, "路径不存在（404），请检查远程目录")
                else -> throw WebDavException(response.code, "服务器返回 ${response.code}")
            }
        }
    }

    fun ensureDirectory(config: WebDavConfig) {
        val segments = config.basePath.split('/').filter { it.isNotBlank() }
        var builder = config.server.trim().trimEnd('/').toHttpUrl()
        segments.forEach { segment ->
            builder = builder.newBuilder().addPathSegment(segment).build()
            val request = request(config, builder, "MKCOL")
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    200, 201, 204, 301, 405, 409 -> Unit
                    401, 403 -> throw WebDavException(response.code, "无法创建远程目录（${response.code}）")
                    else -> throw WebDavException(response.code, "创建远程目录失败（${response.code}）")
                }
            }
        }
    }

    fun list(config: WebDavConfig, url: HttpUrl = rootUrl(config)): List<RemoteFile> {
        val request = request(
            config, url, "PROPFIND",
            PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType()),
        ) {
            header("Depth", "1")
        }
        return http.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw WebDavException(response.code, "认证失败，请检查 WebDAV 配置")
            }
            if (response.code !in 200..299) throw WebDavException(response.code, "列目录失败（${response.code}）")
            parseFiles(response.body?.string().orEmpty())
        }
    }

    fun get(config: WebDavConfig, url: HttpUrl): ByteArray {
        val request = request(config, url, "GET")
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw WebDavException(response.code, "下载失败（${response.code}）")
            response.body?.bytes() ?: throw WebDavException(0, "下载内容为空")
        }
    }

    fun put(config: WebDavConfig, url: HttpUrl, bytes: ByteArray): String? {
        val request = request(
            config, url, "PUT",
            bytes.toRequestBody("application/json; charset=utf-8".toMediaType()),
        )
        return http.newCall(request).execute().use { response ->
            if (response.code !in 200..299) throw WebDavException(response.code, "上传失败（${response.code}）")
            response.header("ETag")
        }
    }

    fun delete(config: WebDavConfig, url: HttpUrl) {
        val request = request(config, url, "DELETE")
        http.newCall(request).execute().use { response ->
            if (response.code !in 200..299 && response.code != 404) {
                throw WebDavException(response.code, "删除远端文件失败（${response.code}）")
            }
        }
    }

    fun fileUrl(config: WebDavConfig, name: String): HttpUrl =
        rootUrl(config).newBuilder().addPathSegment(name).build()

    private fun buildUrl(config: WebDavConfig, _unused: String): HttpUrl {
        val base = config.server.trim().trimEnd('/').toHttpUrl()
        val builder = base.newBuilder()
        config.basePath.split('/').filter { it.isNotBlank() }.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private fun request(
        config: WebDavConfig,
        url: HttpUrl,
        method: String,
        body: okhttp3.RequestBody? = null,
        block: Request.Builder.() -> Unit = {},
    ): Request {
        val builder = Request.Builder().url(url).method(method, body)
        if (config.username.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))
        }
        builder.header("User-Agent", "TomatoSprout/1.0 (Android)")
        builder.block()
        return builder.build()
    }

    private fun parseFiles(xml: String): List<RemoteFile> {
        if (xml.isBlank()) return emptyList()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val responses = doc.getElementsByTagNameNS("DAV:", "response")
        val out = ArrayList<RemoteFile>()
        for (i in 0 until responses.length) {
            val response = responses.item(i) as? Element ?: continue
            val href = response.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent ?: continue
            val decoded = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href).trimEnd('/')
            val name = decoded.substringAfterLast('/')
            if (name.isBlank()) continue
            val isDir = response.getElementsByTagNameNS("DAV:", "collection").length > 0
            val etag = response.getElementsByTagNameNS("DAV:", "getetag").item(0)?.textContent
            out += RemoteFile(name, isDir, etag?.trim('"'))
        }
        return out
    }

    private companion object {
        const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getetag/><d:getcontentlength/></d:prop></d:propfind>"""
    }
}
