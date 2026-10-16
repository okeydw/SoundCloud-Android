package com.scd.android

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException

object Sibling {
    private const val LITE = "com.scd.android"
    private const val FULL = "com.scd.android.full"
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    var lastImport by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    fun otherPackage(context: Context): String = if (context.packageName == FULL) LITE else FULL

    fun authority(pkg: String) = "$pkg.sibling"

    fun installed(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(otherPackage(context), 0)
        true
    }.getOrDefault(false)

    fun trusted(context: Context, pkg: String): Boolean = runCatching {
        context.packageManager.checkSignatures(pkg, context.packageName) == PackageManager.SIGNATURE_MATCH
    }.getOrDefault(false)

    fun available(context: Context): Boolean = installed(context) && trusted(context, otherPackage(context))

    fun exportDownloads(): String = json.encodeToString(ListSerializer(Track.serializer()), Downloads.tracks())

    private fun uri(context: Context, vararg path: String): Uri =
        Uri.Builder().scheme("content").authority(authority(otherPackage(context))).apply { path.forEach { appendPath(it) } }.build()

    private fun callOther(context: Context, method: String): Bundle? = runCatching {
        context.contentResolver.call(uri(context), method, null, null)
    }.getOrNull()

    suspend fun sync(context: Context, manual: Boolean = false): Pair<Int, Int>? = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val app = context.applicationContext
        if (!Prefs.siblingShare || !Downloads.ready) return@withContext null
        if (!available(app)) {
            if (manual) Logs.add("sibling", "${otherPackage(app)}: not installed or signed with another key")
            return@withContext null
        }
        mutex.withLock {
            busy = true
            try {
                val tracksJson = callOther(app, "downloads")?.getString("json")
                if (tracksJson == null) {
                    Logs.add("sibling", "${otherPackage(app)}: sharing is off there")
                    return@withLock null
                }
                val tracks = runCatching { json.decodeFromString(ListSerializer(Track.serializer()), tracksJson) }.getOrDefault(emptyList())
                var copied = 0
                for (t in tracks.reversed()) {
                    if (Downloads.isDownloaded(t.urn)) continue
                    val audio = runCatching { app.contentResolver.openInputStream(uri(app, "audio", t.urn)) }.getOrNull() ?: continue
                    val art = runCatching { app.contentResolver.openInputStream(uri(app, "art", t.urn)) }.getOrNull()
                    if (Downloads.importFrom(t, audio, art)) copied++
                }
                val meta = callOther(app, "playlists")
                val otherUser = meta?.getString("user")
                val sameUser = otherUser == Prefs.userUrn
                var lists = 0
                if (meta != null && sameUser) {
                    val imported = LocalPlaylists.importJson(meta.getString("json").orEmpty(), allowUnsynced = Prefs.userUrn == null)
                    lists = imported.size
                    val covers = meta.getStringArrayList("covers").orEmpty().toSet()
                    for (p in imported) {
                        for (key in listOfNotNull(p.id, p.remoteUrn)) {
                            if (key !in covers || PlaylistCovers.has(key)) continue
                            runCatching { app.contentResolver.openInputStream(uri(app, "cover", key)) }.getOrNull()
                                ?.let { PlaylistCovers.importStream(key, it) }
                        }
                    }
                }
                if (copied > 0 || lists > 0 || manual) {
                    Logs.add("sibling", "${otherPackage(app)}: $copied tracks, $lists playlists" + if (!sameUser) " (another account, playlists skipped)" else "")
                }
                (copied to lists).also { lastImport = it }
            } finally {
                busy = false
            }
        }
    }
}

class SiblingProvider : ContentProvider() {
    private companion object {
        val SAFE_KEY = Regex("[A-Za-z0-9:_.-]+")
    }

    override fun onCreate(): Boolean = true

    private fun allowed(): Boolean {
        val ctx = context ?: return false
        val caller = callingPackage ?: return false
        if (caller != Sibling.otherPackage(ctx)) return false
        Prefs.init(ctx)
        if (!Prefs.siblingShare || !Downloads.ready) return false
        return Sibling.trusted(ctx, caller)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!allowed()) return null
        return when (method) {
            "downloads" -> Bundle().apply { putString("json", Sibling.exportDownloads()) }
            "playlists" -> Bundle().apply {
                putString("json", LocalPlaylists.exportJson())
                putString("user", Prefs.userUrn)
                putStringArrayList(
                    "covers",
                    ArrayList(LocalPlaylists.visible.flatMap { listOfNotNull(it.id, it.remoteUrn) }.filter { PlaylistCovers.has(it) }),
                )
            }
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r" || !allowed()) throw FileNotFoundException()
        val ctx = context ?: throw FileNotFoundException()
        val seg = uri.pathSegments
        if (seg.size != 2) throw FileNotFoundException()
        val key = seg[1].takeIf { it.length in 1..200 && SAFE_KEY.matches(it) } ?: throw FileNotFoundException()
        val file = when (seg[0]) {
            "audio" -> Downloads.localFile(key)
            "art" -> Downloads.artPathOrNull(key)?.let { File(it) }
            "cover" -> PlaylistCovers.get(key)?.takeIf { it.exists() }
            else -> null
        } ?: throw FileNotFoundException()
        val canonical = file.canonicalFile
        val root = ctx.filesDir.canonicalPath + File.separator
        if (!canonical.path.startsWith(root) || !canonical.isFile) throw FileNotFoundException()
        return ParcelFileDescriptor.open(canonical, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
