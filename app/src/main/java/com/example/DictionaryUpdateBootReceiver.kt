package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver that listens for device boot and package update events
 * to re-schedule periodic dictionary updates.
 */
class DictionaryUpdateBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        Log.i(TAG, "Received broadcast action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            DictionaryUpdateScheduler.schedulePeriodicUpdate(context, forceReschedule = true)
        }
    }

    companion object {
        private const val TAG = "DictBootReceiver"
    }
}
