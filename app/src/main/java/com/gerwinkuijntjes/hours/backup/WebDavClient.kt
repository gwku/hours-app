package com.gerwinkuijntjes.hours.backup

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** A folder on the server, as shown in the picker. */
data class DavFolder(
    val name: String,
    /** Path relative to the base URL, always starting with "/" and never ending in one. */
    val path: String
)

/**
 * Why a request did not do what was asked. Deliberately coarse: each case maps to
 * one sentence the user can act on, rather than an HTTP code they cannot.
 */
sealed interface DavError {
    data object Unauthorized : DavError
    data object Forbidden : DavError
    data object NotFound : DavError
    data object NotWebDav : DavError
    data object AlreadyExists : DavError
    data object OutOfSpace : DavError
    data object Unreachable : DavError
    data object InsecureConnection : DavError
    data class Server(val code: Int) : DavError
    data class Unexpected(val message: String) : DavError
}

/**
 * The little bit of WebDAV this app needs: list folders, make one, upload a file.
 *
 * Written against plain HttpURLConnection to avoid pulling in an HTTP library for
 * three verbs. Works with any WebDAV server; Nextcloud is simply one of them.
 */
class WebDavClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) {

    /** Folders directly inside [path], sorted by name. */
    suspend fun listFolders(path: String): Result<List<DavFolder>> = withContext(Dispatchers.IO) {
        val url = urlFor(path)
        request(url, "PROPFIND", PROPFIND_BODY.toByteArray(), depth = "1") { connection ->
            // A server that answers 200 with a login page is not speaking WebDAV
            // here, however friendly the status code looks. Check before parsing:
            // feeding HTML to an XML parser only produces a confusing crash.
            val contentType = connection.contentType.orEmpty()
            if (connection.responseCode != 207 || !contentType.contains("xml", ignoreCase = true)) {
                Log.w(
                    TAG,
                    "not webdav: sent=${connection.requestMethod} " +
                        "code=${connection.responseCode} type=$contentType"
                )
                throw DavException(DavError.NotWebDav)
            }
            val xml = connection.inputStream.bufferedReader().use { it.readText() }
            parseFolders(xml, path)
        }
    }

    suspend fun createFolder(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        request(urlFor(path), "MKCOL", null) { }
    }

    suspend fun upload(path: String, body: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        request(urlFor(path), "PUT", body, contentType = "application/json") { }
    }

    /** A cheap round trip that proves the address and the credentials are good. */
    suspend fun checkConnection(): Result<Unit> = listFolders("").map { }

    private fun urlFor(path: String): String {
        val clean = path.trim('/').split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return if (clean.isEmpty()) baseUrl.trimEnd('/') else "${baseUrl.trimEnd('/')}/$clean"
    }

    private fun <T> request(
        url: String,
        method: String,
        body: ByteArray?,
        depth: String? = null,
        contentType: String? = null,
        onSuccess: (HttpURLConnection) -> T
    ): Result<T> {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                setMethodEvenIfUnusual(method)
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Authorization", basicAuth())
                depth?.let { setRequestProperty("Depth", it) }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", contentType ?: "application/xml")
                    setFixedLengthStreamingMode(body.size)
                }
            }
        } catch (e: Exception) {
            return Result.failure(DavException(DavError.Unexpected(e.message.orEmpty())))
        }

        return try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            Log.i(TAG, "$method $url -> $code (sent as ${connection.requestMethod})")
            if (code in 200..299) {
                Result.success(onSuccess(connection))
            } else {
                Result.failure(DavException(errorFor(code)))
            }
        } catch (e: DavException) {
            Result.failure(e)
        } catch (e: SSLException) {
            Result.failure(DavException(DavError.InsecureConnection))
        } catch (e: UnknownHostException) {
            Result.failure(DavException(DavError.Unreachable))
        } catch (e: IOException) {
            Log.w(TAG, "$method $url failed", e)
            Result.failure(DavException(DavError.Unreachable))
        } catch (e: XmlPullParserException) {
            // The response was not the XML it claimed to be.
            Log.w(TAG, "$method $url returned unparseable XML", e)
            Result.failure(DavException(DavError.NotWebDav))
        } catch (e: Exception) {
            // Whatever else went wrong reading the response, it is a failed
            // request, never a crash in front of the person using the app.
            Log.w(TAG, "$method $url failed unexpectedly", e)
            Result.failure(DavException(DavError.Unexpected(e.message.orEmpty())))
        } finally {
            connection.disconnect()
        }
    }

    private fun errorFor(code: Int): DavError = when (code) {
        401 -> DavError.Unauthorized
        403 -> DavError.Forbidden
        404 -> DavError.NotFound
        // A server that does not know PROPFIND is not speaking WebDAV at this address.
        400, 405, 501 -> DavError.NotWebDav
        409 -> DavError.NotFound
        507 -> DavError.OutOfSpace
        else -> DavError.Server(code)
    }

    /**
     * HttpURLConnection only accepts the verbs HTTP/1.1 defined, and WebDAV's
     * PROPFIND and MKCOL are not among them. The method name lives in a protected
     * field on the public class, so it can be set directly when the setter says no.
     */
    private fun HttpURLConnection.setMethodEvenIfUnusual(method: String) {
        try {
            requestMethod = method
            return
        } catch (e: java.net.ProtocolException) {
            // Fall through and set the field directly.
        }

        // Over https Android hands back a wrapper that forwards everything to a
        // second connection object. Setting the method on the wrapper alone leaves
        // the delegate on its default of POST, because the request has a body, and
        // the server answers 405. So walk the delegate chain and set all of them.
        var target: Any? = this
        val visited = mutableSetOf<Any>()
        var applied = false
        while (target != null && visited.add(target)) {
            applied = writeMethodField(target, method) || applied
            target = delegateOf(target)
        }
        require(applied) { "cannot set request method $method" }
    }

    /** Set every field named "method" declared anywhere in [target]'s hierarchy. */
    private fun writeMethodField(target: Any, method: String): Boolean {
        var applied = false
        generateSequence<Class<*>>(target.javaClass) { it.superclass }.forEach { type ->
            runCatching {
                type.getDeclaredField("method").apply {
                    isAccessible = true
                    set(target, method)
                    applied = true
                }
            }
        }
        return applied
    }

    /** The connection a wrapper forwards to, if it is hiding behind one. */
    private fun delegateOf(target: Any): Any? =
        generateSequence<Class<*>>(target.javaClass) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { field ->
                field.name == "delegate" || field.name == "httpUrlConnection"
            }
            ?.runCatching {
                isAccessible = true
                get(target)
            }
            ?.getOrNull()
            ?.takeIf { it is HttpURLConnection }

    private fun basicAuth(): String {
        val raw = "$username:$password"
        return "Basic " + Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /**
     * Pull the collections out of a multistatus response.
     *
     * Namespace prefixes vary between servers, so the parser matches on local
     * names and ignores the prefix entirely.
     */
    private fun parseFolders(xml: String, parentPath: String): List<DavFolder> {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            .newPullParser()
        parser.setInput(xml.reader())

        val folders = mutableListOf<DavFolder>()
        var href: String? = null
        var isCollection = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "response" -> { href = null; isCollection = false }
                    "href" -> href = parser.nextText()
                    "collection" -> isCollection = true
                }
                XmlPullParser.END_TAG -> if (parser.name == "response") {
                    val path = href?.let { relativePath(it) }
                    // The folder being listed is in its own listing; compare both
                    // sides stripped of slashes so the root ("" against "/") matches.
                    val isSelf = path?.trim('/') == parentPath.trim('/')
                    if (isCollection && path != null && !isSelf) {
                        folders += DavFolder(path.substringAfterLast('/'), path)
                    }
                }
            }
            event = parser.next()
        }
        return folders.sortedBy { it.name.lowercase() }
    }

    /** Turn an absolute href from the server back into a path relative to the base. */
    private fun relativePath(href: String): String? {
        val decoded = URLDecoder.decode(href, "UTF-8").trimEnd('/')
        val basePath = URL(baseUrl).path.trimEnd('/')
        val hrefPath = runCatching { URL(URL(baseUrl), decoded).path }.getOrDefault(decoded)
            .trimEnd('/')
        if (!hrefPath.startsWith(basePath)) return null
        return hrefPath.removePrefix(basePath).ifEmpty { "" }
    }

    private companion object {
        const val TAG = "WebDavClient"

        /** Ask for the one property that says whether an entry is a folder. */
        val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>
        """.trimIndent()
    }
}

class DavException(val error: DavError) : Exception(error.toString())

/** The [DavError] behind a failure, or an [DavError.Unexpected] wrapper. */
fun Throwable.davError(): DavError =
    (this as? DavException)?.error ?: DavError.Unexpected(message.orEmpty())
