package com.atomic120hz

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.IOException

object AtomicAdbClient {
    private const val HOST = "127.0.0.1"
    private const val TEST_MARKER = "ATOMIC_ADB_PROBE_OK"
    private const val END_MARKER = "ATOMIC_ADB_PROBE_END"

    private fun manager(context: Context): AbsAdbConnectionManager =
        AtomicAdbConnectionManager.getInstance(context.applicationContext)

    /**
     * Executes the real Android 11+ Wireless Debugging SPAKE2 pairing, discovers
     * the separate TLS ADB session port, connects, then proves shell access with id.
     */
    @Synchronized
    fun pairConnectAndVerify(context: Context, pairingPort: Int, pairingCode: String): String {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "O pareamento ADB sem fio exige Android 11 ou superior."
        }
        require(pairingPort in 1..65535) { "Porta de pareamento inválida." }
        require(pairingCode.matches(Regex("\\d{6}"))) { "O código precisa ter 6 dígitos." }

        val adb = manager(context)
        val paired = adb.pair(HOST, pairingPort, pairingCode)
        if (!paired) throw IOException("O daemon ADB recusou o pareamento. Confira porta e código.")

        // The pairing port and the ADB session port are different. autoConnect
        // resolves the _adb-tls-connect._tcp service instead of reusing pairingPort.
        val connected = adb.autoConnect(context.applicationContext, 15_000)
        if (!connected) {
            throw IOException("Pareamento concluído, mas não encontrei a porta TLS de conexão. Mantenha a Depuração sem fio ativa e tente novamente.")
        }

        val output = executeWithManager(adb, "echo $TEST_MARKER; id; echo $END_MARKER")
        if (!output.contains(TEST_MARKER) || !output.contains("uid=2000")) {
            try { adb.disconnect() } catch (_: Exception) {}
            throw IOException("A conexão abriu, mas o teste de shell não confirmou UID shell. Retorno: $output")
        }
        return output
    }

    @Synchronized
    fun execute(context: Context, command: String): String {
        require(command.isNotBlank()) { "Comando vazio." }
        return executeWithManager(manager(context), command)
    }

    @Synchronized
    fun disconnect(context: Context) {
        try { manager(context).disconnect() } catch (_: Exception) {}
    }

    private fun executeWithManager(adb: AbsAdbConnectionManager, command: String): String {
        val stream = adb.openStream("shell:$command")
        try {
            return stream.openInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
        } finally {
            try { stream.close() } catch (_: Exception) {}
        }
    }
}
