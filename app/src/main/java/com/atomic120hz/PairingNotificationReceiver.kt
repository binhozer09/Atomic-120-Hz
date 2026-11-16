package com.atomic120hz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.NotificationManager
import java.util.concurrent.Executors

class PairingNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AtomicPairingNotification.ACTION_CONNECT) return
        val input = android.app.RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(AtomicPairingNotification.INPUT_KEY)?.toString()?.trim().orEmpty()
        val parts = input.split(Regex("\\s+"))
        if (parts.size != 2) {
            AtomicPairingNotification.show(context, "Formato inválido. Responda com PORTA CÓDIGO, por exemplo: 37123 123456.")
            return
        }
        val port = parts[0].toIntOrNull()
        val code = parts[1]
        if (port == null || port !in 1..65535 || !code.matches(Regex("\\d{6}"))) {
            AtomicPairingNotification.show(context, "Porta ou código inválido. Digite a porta e os 6 dígitos do código.")
            return
        }

        val pending = goAsync()
        Executors.newSingleThreadExecutor().execute {
            try {
                AtomicPairingNotification.show(context, "Conectando ao ADB… mantenha a Depuração sem fio ativada.")
                val result = AtomicAdbClient.pairConnectAndVerify(context.applicationContext, port, code)
                AtomicPairingNotification.show(context, "CONECTADO! ADB confirmou UID shell (2000). Abra o Atomic para aplicar as configurações.")
            } catch (e: Exception) {
                AtomicPairingNotification.show(context, "Falha no pareamento: " + (e.message ?: "erro desconhecido"))
            } finally {
                pending.finish()
            }
        }
    }
}
