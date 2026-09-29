package com.voicemusic

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * Does nothing by itself. Having notification access enabled for this listener is what lets
 * [YouTubeMusicController] read and control YouTube Music's media session.
 */
class MediaNotificationListener : NotificationListenerService() {
    companion object {
        fun component(context: Context) = ComponentName(context, MediaNotificationListener::class.java)

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?: return false
            val me = component(context)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
