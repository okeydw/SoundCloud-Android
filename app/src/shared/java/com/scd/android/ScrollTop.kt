package com.scd.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

const val SCROLL_TOP_FROM = 3

suspend fun LazyListState.scrollToTop() {
    if (firstVisibleItemIndex > 24) scrollToItem(10)
    animateScrollToItem(0)
}

@Composable
fun rememberScrollTop(state: LazyListState, from: Int = SCROLL_TOP_FROM): Pair<Boolean, () -> Unit> {
    val scope = rememberCoroutineScope()
    val show by remember(state, from) { derivedStateOf { state.firstVisibleItemIndex >= from } }
    val up: () -> Unit = remember(state) { { scope.launch { state.scrollToTop() } } }
    BackHandler(enabled = show, onBack = up)
    return show to up
}
