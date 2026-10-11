package com.scd.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

enum class UpdateState { Idle, Downloading, Ready, Failed, ForeignKey, NeedsPermission }

object Updater {
    var asset by mutableStateOf<ApkAsset?>(null)
    var state by mutableStateOf(UpdateState.Idle)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    private var job: Job? = null
    private var file: File? = null

    fun pick(context: Context, assets: List<ApkAsset>): ApkAsset? {
        val full = context.packageName.endsWith(".full")
        val names = assets.filter { !it.name.contains("debug", ignoreCase = true) }
        return if (full) {
            names.firstOrNull { it.name.contains("-full", ignoreCase = true) }
        } else {
            names.firstOrNull { it.name.contains("-lite", ignoreCase = true) }
                ?: names.firstOrNull { !it.name.contains("-full", ignoreCase = true) }
        }
    }

    private fun dir(context: Context) = File(context.cacheDir, "updates").apply { mkdirs() }

    fun start(context: Context) {
        val app = context.applicationContext
        val a = asset ?: return
        if (state == UpdateState.Downloading) return
        if (state == UpdateState.Ready && file?.exists() == true) {
            install(app)
            return
        }
        state = UpdateState.Downloading
        progress = 0f
        job = App.scope.launch {
            val ok = runCatching { download(app, a) }.getOrElse {
                Logs.add("update", "download failed: ${it.javaClass.simpleName} ${it.message?.take(60) ?: ""}")
                null
            }
            withContext(Dispatchers.Main) {
                if (ok == null) {
                    state = UpdateState.Failed
                    return@withContext
                }
                file = ok
                state = if (sameSigner(app, ok)) UpdateState.Ready else UpdateState.ForeignKey
                if (state == UpdateState.Ready) install(app)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        if (state == UpdateState.Downloading) state = UpdateState.Idle
    }

    private suspend fun download(context: Context, a: ApkAsset): File? = withContext(Dispatchers.IO) {
        val folder = dir(context)
        folder.listFiles()?.forEach { it.delete() }
        val target = File(folder, "update.apk")
        val tmp = File(folder, "update.part")
        val req = Request.Builder().url(a.url).cacheControl(CacheControl.FORCE_NETWORK).build()
        Api.http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                Logs.add("update", "http ${res.code}")
                return@withContext null
            }
            val body = res.body ?: return@withContext null
            val total = body.contentLength().takeIf { it > 0 } ?: a.size
            body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val f = (done.toFloat() / total).coerceIn(0f, 1f)
                            withContext(Dispatchers.Main) { progress = f }
                        }
                    }
                }
            }
        }
        if (tmp.length() <= 0L || !tmp.renameTo(target)) return@withContext null
        Logs.add("update", "${a.name}: ${target.length() / 1024} KB")
        target
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo?): Set<String> {
        if (info == null) return emptySet()
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            info.signatures
        }
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.orEmpty().map { s -> md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun sameSigner(context: Context, apk: File): Boolean = runCatching {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return@runCatching false
        if (archive.packageName != context.packageName) {
            Logs.add("update", "package mismatch: ${archive.packageName}")
            return@runCatching false
        }
        val mine = signers(pm.getPackageInfo(context.packageName, flags))
        val theirs = signers(archive)
        val match = mine.isNotEmpty() && theirs.any { it in mine }
        if (!match) Logs.add("update", "signed with another key")
        match
    }.getOrDefault(false)

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun install(context: Context) {
        val apk = file?.takeIf { it.exists() } ?: run {
            state = UpdateState.Idle
            return
        }
        if (!canInstall(context)) {
            state = UpdateState.NeedsPermission
            openPermission(context)
            return
        }
        state = UpdateState.Ready
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Logs.add("update", "installer failed: ${it.javaClass.simpleName}") }
    }

    fun resumeIfReady(context: Context) {
        if (state == UpdateState.NeedsPermission && canInstall(context)) install(context)
    }
}
