// FILE GUIDE: Initializes Firebase Messaging and displays incoming nearby listing pushes.
package com.nearnest.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nearnest.BuildConfig
import com.nearnest.MainActivity
import com.nearnest.R

/** Creates Firebase configuration from local.properties, keeping Firebase setup optional for builds. */
object HereBuyPush {
    const val CHANNEL_ID = "nearby_products"
    private const val PREFS = "herebuy_push"
    private const val TOKEN_KEY = "fcm_token"

    fun isConfigured(): Boolean = BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
        BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
        BuildConfig.FIREBASE_SENDER_ID.isNotBlank()

    fun initialize(context: Context): Boolean {
        if (!isConfigured()) return false
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build()
            FirebaseApp.initializeApp(context, options)
        }
        createChannel(context)
        return true
    }

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TOKEN_KEY, token).apply()
    }

    fun savedToken(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN_KEY, null)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Nearby products", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alerts about new products posted within your selected radius."
                }
            )
        }
    }

    fun show(context: Context, title: String, body: String, listingId: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            listingId?.let { putExtra("listing_id", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            context, listingId?.hashCode() ?: body.hashCode(), openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else Notification.Builder(context)
        notification.setSmallIcon(R.drawable.ic_nav_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_SOCIAL)
            .setPriority(Notification.PRIORITY_HIGH)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(listingId?.hashCode() ?: body.hashCode(), notification.build())
    }
}

/** Receives data-only FCM messages in both foreground and background app states. */
class HereBuyMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        HereBuyPush.saveToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        HereBuyPush.show(
            this,
            data["title"] ?: "New nearby product",
            data["body"] ?: "A product was posted near you.",
            data["listing_id"]
        )
    }
}
