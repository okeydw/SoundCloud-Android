package com.scd.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun GlassList(
    modifier: Modifier = Modifier.fillMaxSize(),
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = dockContentPadding(),
    content: LazyListScope.() -> Unit,
) {
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = contentPadding, content = content)
        ScrollTopOrb(state)
    }
}

@Composable
fun BoxScope.ScrollTopOrb(state: LazyListState) {
    val (show, up) = rememberScrollTop(state)
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    AnimatedVisibility(
        visible = show,
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = LocalDockPadding.current + nav + 14.dp),
        enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.7f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.7f),
    ) {
        IconOrb(R.drawable.ic_arrow_up, stringResource(R.string.scroll_top), size = 48.dp, iconSize = 20.dp, onClick = up)
    }
}
