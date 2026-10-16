package com.scd.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

object LauncherIcons {
    const val VIOLET = "violet"
    const val BLUE = "blue"
    const val ORANGE = "orange"
    val all = listOf(VIOLET, BLUE, ORANGE)

    private fun alias(id: String) = "com.scd.android.Icon" + id.replaceFirstChar { it.uppercase() }

    fun default(context: Context): String = if (context.packageName.endsWith(".full")) VIOLET else BLUE

    fun current(context: Context): String {
        val pm = context.packageManager
        for (id in all) {
            val state = runCatching { pm.getComponentEnabledSetting(ComponentName(context.packageName, alias(id))) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return id
        }
        val def = default(context)
        val defState = runCatching { pm.getComponentEnabledSetting(ComponentName(context.packageName, alias(def))) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        return if (defState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) all.first { it != def } else def
    }

    fun apply(context: Context, id: String): Boolean {
        if (id !in all || id == current(context)) return false
        val pm = context.packageManager
        return runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, alias(id)),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            all.filter { it != id }.forEach { other ->
                pm.setComponentEnabledSetting(
                    ComponentName(context.packageName, alias(other)),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            Prefs.changeLauncherIcon(id)
            Logs.add("icon", "launcher → $id")
            true
        }.getOrElse {
            Logs.add("icon", "switch failed: ${it.javaClass.simpleName}")
            false
        }
    }

    fun canPin(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun pinCustom(context: Context, source: Bitmap, label: String): Boolean = runCatching {
        val size = 432
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val side = minOf(source.width, source.height)
        val src = Rect((source.width - side) / 2, (source.height - side) / 2, (source.width + side) / 2, (source.height + side) / 2)
        Canvas(out).drawBitmap(source, src, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val info = ShortcutInfoCompat.Builder(context, "custom-" + System.currentTimeMillis())
            .setShortLabel(label)
            .setIcon(IconCompat.createWithAdaptiveBitmap(out))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }.getOrElse {
        Logs.add("icon", "pin failed: ${it.javaClass.simpleName}")
        false
    }
}
