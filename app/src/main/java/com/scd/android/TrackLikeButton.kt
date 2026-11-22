package com.scd.android

import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TrackLikeButton(track: Track, modifier: Modifier, activeColor: Color, inactiveColor: Color, iconSize: Dp = 24.dp) {
    if (track.external) return
    val context = LocalContext.current.applicationContext
    val liked = Likes.isLiked(track.urn)
    val superliked = Likes.isSuperliked(track.urn)
    val action = stringResource(if (superliked) R.string.remove_superlike else R.string.add_superlike)
    val toggleSuperlike = {
        App.scope.launch(Dispatchers.Main) {
            val message = try {
                when {
                    Prefs.offline -> R.string.offline_mode_on
                    !Likes.setSuperlike(track, !superliked) -> R.string.superlike_failed
                    superliked -> R.string.superlike_removed
                    else -> return@launch
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiHttpException && e.serviceCode == "superlike_limit_reached") R.string.superlike_limit
                else R.string.superlike_failed
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        Unit
    }
    Box(
        modifier.clip(CircleShape).semantics {
            customActions = listOf(CustomAccessibilityAction(action) { toggleSuperlike(); true })
        }.combinedClickable(
            role = Role.Button,
            onClick = { App.scope.launch(Dispatchers.Main) { Likes.toggle(track) } },
            onDoubleClick = toggleSuperlike,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(when {
                superliked -> R.drawable.ic_superlike
                liked -> R.drawable.ic_heart_filled
                else -> R.drawable.ic_heart
            }),
            stringResource(when {
                superliked -> R.string.superliked
                liked -> R.string.notif_unlike
                else -> R.string.notif_like
            }),
            tint = if (liked) activeColor else inactiveColor,
            modifier = Modifier.size(iconSize),
        )
    }
}
