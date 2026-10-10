package com.scd.android

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun iconLabel(@DrawableRes res: Int): String? {
    val label = when (res) {
        R.drawable.ic_play -> R.string.cd_play
        R.drawable.ic_pause -> R.string.cd_pause
        R.drawable.ic_next -> R.string.cd_next
        R.drawable.ic_prev -> R.string.cd_prev
        R.drawable.ic_shuffle, R.drawable.ic_shuffle_on -> R.string.notif_shuffle
        R.drawable.ic_repeat -> R.string.cd_repeat
        R.drawable.ic_heart -> R.string.notif_like
        R.drawable.ic_heart_filled -> R.string.notif_unlike
        R.drawable.ic_thumb_down, R.drawable.ic_thumb_down_filled -> R.string.cd_dislike
        R.drawable.ic_download -> R.string.cd_download
        R.drawable.ic_check -> R.string.cd_remove_download
        R.drawable.ic_lyrics -> R.string.cd_lyrics
        R.drawable.ic_equalizer -> R.string.equalizer
        R.drawable.ic_share -> R.string.cd_share
        R.drawable.ic_mix -> R.string.cd_track_radio
        R.drawable.ic_plus -> R.string.add_to_playlist
        R.drawable.ic_chevron_down -> R.string.cd_close
        R.drawable.ic_arrow_back -> R.string.cd_back
        R.drawable.ic_more_vert -> R.string.cd_more
        R.drawable.ic_settings -> R.string.settings
        R.drawable.ic_trash -> R.string.delete
        R.drawable.ic_search -> R.string.tab_search
        R.drawable.ic_clear -> R.string.cd_clear
        R.drawable.ic_refresh -> R.string.wave_refresh
        R.drawable.ic_edit -> R.string.rename
        else -> return null
    }
    return stringResource(label)
}
