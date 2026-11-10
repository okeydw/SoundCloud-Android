package com.scd.android

object AudioQualityPolicy {
    fun bestKnown(vararg labels: String?): Quality? = labels.mapNotNull(Quality::parse).maxByOrNull { it.rank }

    fun served(header: String?, known: String? = null, requested: Quality? = null): Quality =
        Quality.parse(header) ?: Quality.parse(known) ?: requested ?: Quality.SQ

    fun isHigh(header: String?, known: String?): Boolean = served(header, known).rank >= Quality.HQ.rank
}
