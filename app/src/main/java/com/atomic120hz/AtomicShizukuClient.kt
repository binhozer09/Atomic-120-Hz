package com.atomic120hz

import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.IOException

/**
 * Executor compatível com a API Shizuku 13.1.5.
 * newProcess é privado/depreciado nessa versão; o acesso reflexivo é usado
 * apenas para manter o executor simples enquanto a migração para UserService
 * fica planejada. A execução exige autorização explícita do usuário.
 */
object AtomicShizukuClient {
    private val newProcessMethod by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
    }

    @Synchronized
    fun run(command: String): String {
        check(Shizuku.pingBinder()) { "O serviço Shizuku não está ativo." }
        check(Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            "O Atomic ainda não recebeu autorização do Shizuku."
        }
        val process = newProcessMethod.invoke(
            null, arrayOf("sh", "-c", command), null, null
        ) as ShizukuRemoteProcess
        val stdout = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val stderr = process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IOException("Comando retornou código $exitCode. ${stderr.trim().ifBlank { stdout.trim() }}")
        }
        return stdout.trim().ifBlank { stderr.trim() }
    }
}
