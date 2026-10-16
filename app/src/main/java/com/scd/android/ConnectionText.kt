package com.scd.android

val offlineByChoice: Boolean get() = Prefs.offline && Prefs.offlineManual

fun noConnectionText(): Int = if (offlineByChoice) R.string.offline_mode_on else R.string.no_internet

fun networkErrorText(): Int = if (offlineByChoice) R.string.offline_mode_on else R.string.error_network
