package com.scd.android

import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.S)
@Suppress("DEPRECATION")
internal fun requestCarAssociation(
    activity: Activity,
    manager: CompanionDeviceManager,
    addresses: Set<String>,
    onPending: (IntentSender) -> Unit,
    onCreated: () -> Unit,
    onError: (String) -> Unit,
) {
    val request = AssociationRequest.Builder().setSingleDevice(addresses.size == 1).apply {
        if (addresses.isEmpty()) addDeviceFilter(BluetoothDeviceFilter.Builder().build())
        else addresses.forEach { addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(it).build()) }
    }.build()
    if (Build.VERSION.SDK_INT >= 33) {
        manager.associate(request, activity.mainExecutor, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) = onPending(intentSender)
            override fun onAssociationCreated(associationInfo: AssociationInfo) = onCreated()
            override fun onFailure(error: CharSequence?) = onError(error?.toString() ?: "association failed")
        })
    } else {
        manager.associate(request, object : CompanionDeviceManager.Callback() {
            override fun onDeviceFound(chooserLauncher: IntentSender) = onPending(chooserLauncher)
            override fun onFailure(error: CharSequence?) = onError(error?.toString() ?: "association failed")
        }, Handler(Looper.getMainLooper()))
    }
}
