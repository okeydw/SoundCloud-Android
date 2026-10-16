package com.scd.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

class LocalFilter {
    var open by mutableStateOf(false)
    var query by mutableStateOf("")

    val active: Boolean get() = open && query.isNotBlank()

    fun toggle() {
        open = !open
        if (!open) query = ""
    }
}

fun filterTracks(list: List<Track>, query: String): List<Track> {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return list
    return list.filter { t ->
        val hay = buildString {
            append(t.title.lowercase())
            append(' ')
            append(t.user?.username?.lowercase().orEmpty())
            append(' ')
            append(t.genre?.lowercase().orEmpty())
        }
        terms.all { hay.contains(it) }
    }
}

@Composable
fun LocalSearchOrb(filter: LocalFilter) {
    IconOrb(
        if (filter.open) R.drawable.ic_clear else R.drawable.ic_search,
        stringResource(if (filter.open) R.string.cd_clear else R.string.local_search_hint),
        size = 44.dp,
        iconSize = 18.dp,
        active = filter.open,
    ) { filter.toggle() }
}

@Composable
fun LocalSearchField(filter: LocalFilter, found: Int?) {
    val p = LocalGlass.current
    val focus = remember { FocusRequester() }
    val manager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .height(48.dp)
            .glass(RoundedCornerShape(16.dp), p, shadow = 0.dp)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), null, tint = p.text2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (filter.query.isEmpty()) {
                Text(stringResource(R.string.local_search_hint), style = GlassType.body.copy(fontSize = 15.sp), color = p.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = filter.query,
                onValueChange = { filter.query = it.take(80) },
                singleLine = true,
                textStyle = GlassType.body.copy(fontSize = 15.sp, color = p.text),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { manager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (found != null && filter.query.isNotBlank()) {
            Text(found.toString(), style = GlassType.mono.copy(fontSize = 12.sp), color = p.text3, modifier = Modifier.padding(horizontal = 8.dp))
        }
        if (filter.query.isNotEmpty()) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).clickable { filter.query = "" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_clear), stringResource(R.string.cd_clear), tint = p.text2, modifier = Modifier.size(16.dp))
            }
        }
    }
}
