package com.scd.android

import androidx.compose.runtime.*
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController

data class QueuePreview(
    val previous: MediaMetadata? = null,
    val next: MediaMetadata? = null,
    val current: MediaMetadata? = null,
    val currentId: String? = null,
    val currentIndex: Int = -1,
    val previousIndex: Int = -1,
    val nextIndex: Int = -1,
    val previousId: String? = null,
    val nextId: String? = null,
)

@Composable
fun rememberQueuePreview(controller: MediaController?): QueuePreview {
    fun snapshot(): QueuePreview {
        val c = controller ?: return QueuePreview()
        fun at(index: Int) = index.takeIf { it in 0 until c.mediaItemCount }?.let { c.getMediaItemAt(it).mediaMetadata }
        return QueuePreview(
            at(c.previousMediaItemIndex), at(c.nextMediaItemIndex), c.currentMediaItem?.mediaMetadata,
            c.currentMediaItem?.mediaId, c.currentMediaItemIndex, c.previousMediaItemIndex, c.nextMediaItemIndex,
            c.previousMediaItemIndex.takeIf { it in 0 until c.mediaItemCount }?.let { c.getMediaItemAt(it).mediaId },
            c.nextMediaItemIndex.takeIf { it in 0 until c.mediaItemCount }?.let { c.getMediaItemAt(it).mediaId },
        )
    }
    var preview by remember(controller) { mutableStateOf(snapshot()) }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { preview = snapshot() }
        }
        controller?.addListener(listener)
        preview = snapshot()
        onDispose { controller?.removeListener(listener) }
    }
    return preview
}
