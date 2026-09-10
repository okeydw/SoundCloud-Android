package com.scd.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CacheTools {

    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    private val dirs = listOf("media2", "media", "http", "goplus", "images")

    fun sizeBytes(context: Context): Long {
        val root = context.applicationContext.cacheDir
        return dirs.sumOf { dirSize(File(root, it)) }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val root = context.applicationContext.cacheDir
        runCatching { MediaCache.clear() }
        runCatching { Api.http.cache?.evictAll() }
        runCatching { Images.clear() }
        ScDataSource.forgetResolved()
        WaveCache.clear()
        for (name in listOf("media", "goplus", "images")) {
            runCatching { File(root, name).deleteRecursively() }
        }
        runCatching { File(root, "goplus").mkdirs() }
        FeedCache.clear()
    }
}
