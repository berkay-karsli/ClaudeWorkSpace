package com.dailytodo.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms are wiped on reboot and app update; Application.onCreate puts them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Starting the process runs TodoApp.onCreate, which reschedules every alert.
    }
}
