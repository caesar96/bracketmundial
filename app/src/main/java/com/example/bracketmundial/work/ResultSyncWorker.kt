package com.example.bracketmundial.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bracketmundial.MainActivity
import com.example.bracketmundial.R
import com.example.bracketmundial.data.AppDatabase
import com.example.bracketmundial.data.SyncRepository
import java.time.LocalTime

private const val CHANNEL_ID = "results"
private const val NOTIFICATION_ID = 1001
private val ACTIVE_HOURS = 10..21 // 10:00-22:00, to protect the API's free quota

class ResultSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (LocalTime.now().hour !in ACTIVE_HOURS) return Result.success()

        val dao = AppDatabase.getInstance(applicationContext).teamDao()
        val result = SyncRepository(applicationContext, dao).sync()
        if (result.retryable) return Result.retry()

        if (result.applied.isNotEmpty()) sendNotification(result.applied)
        return Result.success()
    }

    private fun sendNotification(applied: List<String>) {
        val context = applicationContext
        createChannelIfNeeded(context)

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = applied.joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(applied.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannelIfNeeded(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
