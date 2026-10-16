package com.scd.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class UploadPhase { Idle, Sending, Processing, Done, Failed }

data class PickedFile(val uri: Uri, val name: String, val size: Long, val mime: String?)

data class UploadRequest(
    val audio: PickedFile,
    val artwork: PickedFile?,
    val title: String,
    val description: String,
    val genre: String,
    val tags: String,
    val private: Boolean,
)

object TrackUpload {
    const val MAX_AUDIO_BYTES = 300L * 1024 * 1024
    const val MAX_ARTWORK_BYTES = 8L * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    var phase by mutableStateOf(UploadPhase.Idle)
        private set
    var sent by mutableLongStateOf(0L)
        private set
    var total by mutableLongStateOf(0L)
        private set
    var error by mutableStateOf<Int?>(null)
        private set
    var reason by mutableStateOf<String?>(null)
        private set
    var last by mutableStateOf<UploadRequest?>(null)
        private set
    var trackUrn by mutableStateOf<String?>(null)
        private set
    var direct by mutableStateOf(false)
        private set

    val busy: Boolean get() = phase == UploadPhase.Sending || phase == UploadPhase.Processing

    private var job: Job? = null

    fun describe(context: Context, uri: Uri): PickedFile? {
        val resolver = context.contentResolver
        var name: String? = null
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        return PickedFile(uri, name ?: uri.lastPathSegment ?: "track", size, resolver.getType(uri))
    }

    fun titleFrom(file: PickedFile): String =
        file.name.substringBeforeLast('.', file.name).replace('_', ' ').trim()

    fun tagList(input: String): String =
        input.split(',', '#', '\n')
            .map { it.replace("\"", "").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ") { if (it.any(Char::isWhitespace)) "\"$it\"" else it }

    fun start(context: Context, request: UploadRequest) {
        if (busy) return
        val resolver = context.applicationContext.contentResolver
        last = request
        error = null
        reason = null
        trackUrn = null
        direct = false
        sent = 0
        total = request.audio.size.coerceAtLeast(0)
        phase = UploadPhase.Sending
        job = App.scope.launch(Dispatchers.Main) {
            try {
                val answer = try {
                    Api.uploadTrack(withContext(Dispatchers.IO) { form(resolver, request, direct = false) })
                } catch (e: ApiHttpException) {
                    val (res, message) = serverError(e)
                    if (!retryable(e)) {
                        fail(res, message)
                        return@launch
                    }
                    viaSoundCloud(resolver, request) ?: run {
                        fail(res, message)
                        return@launch
                    }
                } catch (e: IOException) {
                    Logs.add("upload", "network: ${e.javaClass.simpleName}")
                    viaSoundCloud(resolver, request) ?: run {
                        fail(R.string.upload_err_network, null)
                        return@launch
                    }
                }
                val track = runCatching { json.parseToJsonElement(answer) as? JsonObject }.getOrNull()
                trackUrn = (track?.get("urn") as? JsonPrimitive)?.contentOrNull
                    ?: (track?.get("id") as? JsonPrimitive)?.contentOrNull?.let { "soundcloud:tracks:$it" }
                phase = UploadPhase.Done
                Logs.add("upload", "uploaded ${trackUrn ?: "track"} (${request.audio.size / 1024} KB)${if (direct) " directly" else ""}")
            } catch (e: CancellationException) {
                phase = UploadPhase.Idle
                Logs.add("upload", "cancelled")
                throw e
            } catch (e: UploadFileException) {
                fail(e.res, null)
            } catch (e: ApiHttpException) {
                fail(scError(e), scMessage(e))
            } catch (e: IOException) {
                Logs.add("upload", "soundcloud network: ${e.javaClass.simpleName}")
                fail(R.string.upload_err_sc_network, null)
            }
        }
    }

    private fun retryable(e: ApiHttpException): Boolean = e.code >= 500 || e.code == 401 || e.code == 429

    private suspend fun viaSoundCloud(resolver: ContentResolver, request: UploadRequest): String? {
        val token = Prefs.scWebToken.takeIf { it.isNotBlank() } ?: return null
        Logs.add("upload", "server failed, uploading straight to SoundCloud")
        direct = true
        sent = 0
        phase = UploadPhase.Sending
        val body = withContext(Dispatchers.IO) { form(resolver, request, direct = true) }
        return sendDirect(body, token)
    }

    private suspend fun sendDirect(body: RequestBody, token: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.soundcloud.com/tracks")
            .header("Authorization", "OAuth $token")
            .header("Accept", "application/json")
            .post(body)
            .build()
        directClient.newCall(req).await().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                Logs.add("upload", "soundcloud HTTP ${res.code} ${text.take(120).replace('\n', ' ')}")
                throw ApiHttpException(res.code, text.take(600))
            }
            text
        }
    }

    private val directClient: OkHttpClient by lazy {
        Api.http.newBuilder()
            .cache(null)
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .callTimeout(60, TimeUnit.MINUTES)
            .build()
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    private fun scError(e: ApiHttpException): Int = when (e.code) {
        401, 403 -> R.string.upload_err_sc_session
        413 -> R.string.upload_err_too_large
        415 -> R.string.upload_err_format
        422 -> R.string.upload_err_invalid
        429 -> R.string.upload_err_busy
        else -> R.string.upload_err_failed
    }

    private fun scMessage(e: ApiHttpException): String? {
        val root = runCatching { json.parseToJsonElement(e.body) as? JsonObject }.getOrNull() ?: return null
        val first = (root["errors"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? JsonObject
        return ((first?.get("error_message") ?: root["message"] ?: root["error"]) as? JsonPrimitive)?.contentOrNull
    }

    fun cancel() {
        job?.cancel()
    }

    fun reset() {
        if (busy) return
        phase = UploadPhase.Idle
        error = null
        reason = null
        trackUrn = null
    }

    private fun fail(res: Int, message: String?) {
        error = res
        reason = message
        phase = UploadPhase.Failed
    }

    private fun serverError(e: ApiHttpException): Pair<Int, String?> {
        val root = runCatching { json.parseToJsonElement(e.body) as? JsonObject }.getOrNull()
        val code = (root?.get("code") as? JsonPrimitive)?.contentOrNull
        val message = (root?.get("message") as? JsonPrimitive)?.contentOrNull
        val res = when (code) {
            "upload_too_large" -> R.string.upload_err_too_large
            "upload_format_unsupported" -> R.string.upload_err_format
            "upload_artwork_unsupported" -> R.string.upload_err_artwork
            "upload_in_progress" -> R.string.upload_err_in_progress
            "uploads_busy" -> R.string.upload_err_busy
            "upload_invalid" -> R.string.upload_err_invalid
            else -> when (e.code) {
                401 -> R.string.upload_err_session
                403, 422 -> R.string.upload_err_rejected
                else -> R.string.upload_err_failed
            }
        }
        Logs.add("upload", "rejected ${e.code} ${code ?: ""}")
        return res to message
    }

    private class UploadFileException(val res: Int) : Exception()

    private fun form(resolver: ContentResolver, request: UploadRequest, direct: Boolean): MultipartBody {
        fun field(name: String) = if (direct) "track[$name]" else name
        val audio = request.audio
        if (audio.size == 0L) throw UploadFileException(R.string.upload_err_file)
        if (audio.size > MAX_AUDIO_BYTES) throw UploadFileException(R.string.upload_err_too_large)
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        val fields = listOf(
            "title" to request.title,
            "description" to request.description,
            "genre" to request.genre,
            "tag_list" to tagList(request.tags),
            "sharing" to if (request.private) "private" else "public",
        )
        for ((name, value) in fields) {
            val v = value.trim()
            if (v.isNotEmpty()) builder.addFormDataPart(field(name), v)
        }
        request.artwork?.let { art ->
            val mime = when {
                art.mime == "image/jpeg" || art.name.endsWith(".jpg", true) || art.name.endsWith(".jpeg", true) -> "image/jpeg"
                art.mime == "image/png" || art.name.endsWith(".png", true) -> "image/png"
                else -> throw UploadFileException(R.string.upload_err_artwork)
            }
            if (art.size > MAX_ARTWORK_BYTES) throw UploadFileException(R.string.upload_err_artwork_large)
            val bytes = runCatching { resolver.openInputStream(art.uri)?.use { it.readBytes() } }.getOrNull()
                ?: throw UploadFileException(R.string.upload_err_artwork)
            if (bytes.size > MAX_ARTWORK_BYTES) throw UploadFileException(R.string.upload_err_artwork_large)
            builder.addFormDataPart(if (direct) field("artwork_data") else "artwork", art.name, bytes.toRequestBody(mime.toMediaTypeOrNull()))
        }
        val audioBody = UriBody(resolver, audio.uri, audio.size, (audio.mime ?: "application/octet-stream").toMediaTypeOrNull()) { done ->
            App.scope.launch(Dispatchers.Main) {
                if (phase != UploadPhase.Sending && phase != UploadPhase.Processing) return@launch
                sent = done
                if (total > 0 && done >= total) phase = UploadPhase.Processing
            }
        }
        builder.addFormDataPart(if (direct) field("asset_data") else "asset", audio.name, audioBody)
        return builder.build()
    }

    private class UriBody(
        private val resolver: ContentResolver,
        private val uri: Uri,
        private val size: Long,
        private val type: MediaType?,
        private val onProgress: (Long) -> Unit,
    ) : RequestBody() {
        override fun contentType(): MediaType? = type

        override fun contentLength(): Long = size

        override fun writeTo(sink: BufferedSink) {
            val input = resolver.openInputStream(uri) ?: throw IOException("audio unreadable")
            input.use {
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                var reported = 0L
                while (true) {
                    val n = it.read(buffer)
                    if (n < 0) break
                    sink.write(buffer, 0, n)
                    done += n
                    if (done - reported >= 256 * 1024 || done == size) {
                        reported = done
                        onProgress(done)
                    }
                }
                onProgress(done)
            }
        }
    }
}
