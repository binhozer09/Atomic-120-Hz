package com.atomic120hz

import rikka.shizuku.Shizuku
import java.io.IOException

/**
 * Executa comandos através do processo shell fornecido pelo Shizuku.
 * O processo só é iniciado depois da autorização explícita do usuário.
 */
object AtomicShizukuClient {
    @Synchronized
    fun run(command: String): String {
        check(Shizuku.pingBinder()) { "O serviço Shizuku não está ativo." }
        check(Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            "O Atomic ainda não recebeu autorização do Shizuku."
        }
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        val stdout = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val stderr = process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IOException("Comando retornou código $exitCode. ${stderr.trim().ifBlank { stdout.trim() }}")
        }
        return stdout.trim().ifBlank { stderr.trim() }
    }
}
