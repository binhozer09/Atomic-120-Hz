package com.atomic120hz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.RemoteInput

object AtomicPairingNotification {
    const val CHANNEL_ID = "atomic_adb_pairing"
    const val NOTIFICATION_ID = 12090
    const val INPUT_KEY = "atomic_pairing_input"
    const val ACTION_CONNECT = "com.atomic120hz.ACTION_CONNECT_ADB"

    fun show(context: Context, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pareamento ADB do Atomic",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Digite a porta e o código da Depuração sem fio para conectar."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
        }

        val input = RemoteInput.Builder(INPUT_KEY)
            .setLabel("Porta e código: exemplo 37123 123456")
            .build()
        val intent = Intent(context, PairingNotificationReceiver::class.java).setAction(ACTION_CONNECT)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            12091,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val action = Notification.Action.Builder(
            android.R.drawable.ic_menu_send,
            "CONECTAR",
            pendingIntent
        ).addRemoteInput(input).setAllowGeneratedReplies(false).build()

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Atomic 120 Hz • Pareamento ADB")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message + "\nNa resposta, digite: PORTA CÓDIGO (ex.: 37123 123456)."))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(action)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
