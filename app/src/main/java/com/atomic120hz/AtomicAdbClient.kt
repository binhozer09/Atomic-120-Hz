package com.atomic120hz

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID

object AtomicAdbClient {
    private const val HOST = "127.0.0.1"
    private const val TEST_MARKER = "ATOMIC_ADB_PROBE_OK"
    private const val END_MARKER = "ATOMIC_ADB_PROBE_END"

    private fun manager(context: Context): AbsAdbConnectionManager =
        AtomicAdbConnectionManager.getInstance(context.applicationContext)

    @Synchronized
    fun pairConnectAndVerify(context: Context, pairingPort: Int, pairingCode: String): String {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "O pareamento ADB sem fio exige Android 11 ou superior."
        }
        require(pairingPort in 1..65535) { "Porta de pareamento inválida." }
        require(pairingCode.matches(Regex("\\d{6}"))) { "O código precisa ter 6 dígitos." }

        val adb = manager(context)
        if (adb.isConnected) {
            try { adb.disconnect() } catch (_: Exception) {}
        }
        val paired = adb.pair(HOST, pairingPort, pairingCode)
        if (!paired) throw IOException("O daemon ADB recusou o pareamento. Confira porta e código.")

        val connected = adb.connectTls(context.applicationContext, 15_000)
        if (!connected) {
            throw IOException("Pareamento concluído, mas não encontrei a porta TLS de conexão. Mantenha a Depuração sem fio ativa e tente novamente.")
        }

        val output = executeWithManager(adb, "echo $TEST_MARKER; id; echo $END_MARKER")
        if (!output.contains(TEST_MARKER) || !output.contains("uid=2000")) {
            try { adb.disconnect() } catch (_: Exception) {}
            throw IOException("A conexão abriu, mas o teste não confirmou UID shell (2000). Retorno: $output")
        }
        return output
    }

    @Synchronized
    fun execute(context: Context, command: String): String {
        require(command.isNotBlank()) { "Comando vazio." }
        val adb = manager(context)
        if (!adb.isConnected) throw IOException("ADB não está conectado. Reconecte antes de executar comandos.")
        return executeWithManager(adb, command)
    }

    fun isConnected(context: Context): Boolean = manager(context).isConnected

    @Synchronized
    fun disconnect(context: Context) {
        try { manager(context).disconnect() } catch (_: Exception) {}
    }

    /**
     * Runs one shell command and captures stdout, stderr and its exit code.
     * Reading stops at a unique marker rather than waiting for a clean EOF.
     */
    private fun executeWithManager(adb: AbsAdbConnectionManager, command: String): String {
        val marker = "ATOMIC_EXIT_" + UUID.randomUUID().toString().replace("-", "")
        val wrapped = "$command 2>&1; printf '\\n$marker:%s\\n' \"$?\""
        val stream = adb.openStream("shell:$wrapped")
        val output = StringBuilder()
        var exitCode: Int? = null
        try {
            val reader = BufferedReader(InputStreamReader(stream.openInputStream(), StandardCharsets.UTF_8))
            try {
                while (true) {
                    val line = try {
                        reader.readLine()
                    } catch (e: IOException) {
                        if (exitCode != null) break
                        throw IOException("A leitura do shell ADB falhou antes do retorno. " + e.message + ". Saída parcial: " + output, e)
                    } ?: break

                    if (line.startsWith("$marker:")) {
                        exitCode = line.substringAfter(':').trim().toIntOrNull()
                        break
                    }
                    if (output.isNotEmpty()) output.append('\n')
                    output.append(line)
                }
            } finally {
                try { reader.close() } catch (_: Exception) {}
            }
        } finally {
            try { stream.close() } catch (_: Exception) {}
        }

        if (exitCode == null) {
            throw IOException("O shell ADB encerrou sem devolver o código de saída. Saída: $output")
        }
        if (exitCode != 0) {
            throw IOException("Comando ADB retornou código " + exitCode + ": " + output)
        }
        return output.toString().trim().ifEmpty { "OK (código de saída 0)" }
    }
}
