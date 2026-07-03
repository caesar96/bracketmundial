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
import com.example.bracketmundial.data.AppDatabase
import com.example.bracketmundial.data.SyncRepository
import java.time.LocalTime

private const val CANAL_ID = "resultados"
private const val NOTIFICACION_ID = 1001
private val HORARIO_ACTIVO = 10..21 // 10:00-22:00, para cuidar la cuota gratuita de la API

class ResultSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (LocalTime.now().hour !in HORARIO_ACTIVO) return Result.success()

        val dao = AppDatabase.getInstance(applicationContext).teamDao()
        val resultado = SyncRepository(dao).sync()
        if (resultado.reintentable) return Result.retry()

        if (resultado.aplicados.isNotEmpty()) notificar(resultado.aplicados)
        return Result.success()
    }

    private fun notificar(aplicados: List<String>) {
        val context = applicationContext
        crearCanalSiHaceFalta(context)

        val abrirApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val texto = aplicados.joinToString("\n")
        val notificacion = NotificationCompat.Builder(context, CANAL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("¡Nuevos resultados!")
            .setContentText(aplicados.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrirApp)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICACION_ID, notificacion)
        }
    }

    private fun crearCanalSiHaceFalta(context: Context) {
        val canal = NotificationChannel(CANAL_ID, "Resultados del Mundial", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }
}
