package com.scd.android

import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object LikesAutoCache {
    private var appContext: Context? = null

    @Volatile
    private var job: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun onLiked(track: Track) {
        val ctx = appContext ?: return
        if (!Prefs.autoCacheLikes || Prefs.offline || Downloads.isDownloaded(track.urn)) return
        Downloads.enqueue(ctx, listOf(track))
    }

    fun run(context: Context? = appContext) {
        val ctx = context?.applicationContext ?: return
        if (!Prefs.autoCacheLikes || Prefs.offline) return
        if (job?.isActive == true) return
        job = App.scope.launch {
            val pending = mutableListOf<Track>()
            var page = 0
            while (page < 40) {
                val res = runCatching { Api.likedTracks(page, 50) }.getOrNull() ?: break
                pending += res.collection.filter { !Downloads.isDownloaded(it.urn) && !it.starLocked }
                if (!res.has_more) break
                page++
            }
            val todo = pending.distinctBy { it.urn }
            Logs.add("download", "автокэш лайков: ${todo.size} к загрузке")
            if (todo.isNotEmpty()) {
                val label = ctx.getString(R.string.liked)
                Downloads.enqueue(ctx, todo, null, label, listKey = label)
            }
        }
    }
}
