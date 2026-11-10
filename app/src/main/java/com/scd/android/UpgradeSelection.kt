package com.scd.android

import java.io.File

internal class UpgradeSelection(private val current: Quality?, val file: File) {
    var tier: Quality? = null
        private set
    var url: String = ""
        private set

    fun enoughFor(asked: Quality): Boolean = (tier?.rank ?: -1) >= asked.rank

    fun consider(candidate: File, actual: Quality, source: String) {
        if (actual.rank <= maxOf(current?.rank ?: -1, tier?.rank ?: -1)) return
        candidate.copyTo(file, overwrite = true)
        tier = actual
        url = source
    }
}
