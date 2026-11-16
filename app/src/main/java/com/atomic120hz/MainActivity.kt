package com.atomic120hz

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import java.io.IOException
import java.util.concurrent.Executors
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private var selectedHz = 90
    private var enabled = false
    private var shizukuReady = false
    private lateinit var status: TextView
    private lateinit var detected: TextView
    private lateinit var connectButton: Button
    private lateinit var executeButton: Button
    private lateinit var toggle: Button
    private lateinit var hz90: Button
    private lateinit var hz120: Button
    private lateinit var diagnosticStartButton: Button
    private lateinit var diagnosticStopButton: Button
    private lateinit var diagnosticReport: TextView
    private lateinit var diagnosticCopyButton: Button
    private lateinit var exploreFreeFireButton: Button
    private val worker = Executors.newSingleThreadExecutor()
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_CODE) {
            runOnUiThread {
                shizukuReady = grantResult == PackageManager.PERMISSION_GRANTED && Shizuku.pingBinder()
                updateConnectionUi()
                if (shizukuReady) status.text = "Shizuku autorizado. Toque em EXECUTAR COMANDOS."
                else status.text = "Permissão do Shizuku não concedida."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 15, 29)
        window.navigationBarColor = Color.rgb(5, 15, 29)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        buildUi()
        refreshDisplayInfo()
        checkShizuku()
    }

    override fun onResume() {
        super.onResume()
        checkShizuku()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        worker.shutdown()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(28), dp(22), dp(24))
            setBackgroundColor(Color.rgb(5, 15, 29))
        }
        root.addView(TextView(this).apply {
            text = "⚛  ATOMIC 120 Hz"
            textSize = 27f
            setTextColor(0xFF55C9FF.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, match())
        root.addView(TextView(this).apply {
            text = "AJUSTE DE FREQUÊNCIA • SHIZUKU"
            textSize = 12f
            letterSpacing = .12f
            setTextColor(0xFF9ABBD5.toInt())
            gravity = Gravity.CENTER
        }, match())
        root.addView(space(22))
        detected = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(18), dp(12), dp(18))
            setBackgroundColor(0xFF102943.toInt())
        }
        root.addView(detected, match())
        root.addView(space(18))

        root.addView(stepTitle("1. BAIXAR O SHIZUKU"))
        root.addView(TextView(this).apply {
            text = "Instale o Shizuku separadamente. Depois abra o Shizuku e inicie o serviço usando Depuração sem fio."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }, match())
        root.addView(space(8))
        root.addView(actionButton("BAIXAR SHIZUKU") { openShizukuDownload() }, buttonParams(52))
        root.addView(space(18))

        root.addView(stepTitle("2. CONECTAR E AUTORIZAR"))
        root.addView(TextView(this).apply {
            text = "No Shizuku, siga as instruções de Depuração sem fio do Android. Quando o serviço estiver iniciado, volte aqui e autorize o Atomic."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }, match())
        root.addView(space(8))
        connectButton = actionButton("CONECTAR AO SHIZUKU / AUTORIZAR") { connectShizuku() }
        root.addView(connectButton, buttonParams(56))
        root.addView(space(10))
        status = TextView(this).apply {
            text = "Aguardando o Shizuku."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }
        root.addView(status, match())
        root.addView(space(18))

        root.addView(stepTitle("3. EXECUTAR OS COMANDOS"))
        executeButton = actionButton("EXECUTAR COMANDOS PARA 90 FPS") { applySelectedMode() }
        root.addView(executeButton, buttonParams(60))
        root.addView(space(8))
        root.addView(actionButton("VERIFICAR LIMITAÇÃO DO FREE FIRE") { diagnoseFreeFireLimit() }, buttonParams(52))
        exploreFreeFireButton = actionButton("EXPLORAR PASTAS DO FREE FIRE (LEITURA)") { exploreFreeFireStorage() }
        root.addView(exploreFreeFireButton, buttonParams(56))
        root.addView(space(8))
        root.addView(space(8))
        root.addView(actionButton("TENTAR 90 FPS NO FREE FIRE") { tryFreeFire90Fps() }, buttonParams(58))
        root.addView(space(8))
        root.addView(actionButton("INVESTIGAR POLÍTICAS DO ANDROID / HYPEROS") { investigateAndroidFpsPolicy() }, buttonParams(56))
        root.addView(space(8))
        root.addView(actionButton("SOLICITAR 90 HZ (PRESERVA LIMITE TÉRMICO)") { force90Hz() }, buttonParams(58))
        root.addView(space(8))
        root.addView(actionButton("SOLICITAR MODO NATIVO DE DISPLAY 90 HZ") { requestNative90Hz() }, buttonParams(58))
        root.addView(space(18))

        root.addView(stepTitle("4. DIAGNÓSTICO REAL DE FPS"))
        root.addView(TextView(this).apply {
            text = "Toque em INICIAR, volte ao Free Fire e jogue por cerca de 30 segundos. Depois volte ao Atomic e toque em FINALIZAR para gerar o relatório."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }, match())
        root.addView(space(8))
        diagnosticStartButton = actionButton("INICIAR MEDIÇÃO") { startFpsDiagnostic() }
        root.addView(diagnosticStartButton, buttonParams(52))
        root.addView(space(8))
        diagnosticStopButton = actionButton("FINALIZAR E MOSTRAR RELATÓRIO") { finishFpsDiagnostic() }
        root.addView(diagnosticStopButton, buttonParams(52))
        root.addView(space(8))
        diagnosticReport = TextView(this).apply {
            text = "O relatório aparecerá aqui."
            textSize = 12f
            setTextColor(0xFFE1EDF7.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFF102943.toInt())
        }
        diagnosticCopyButton = actionButton("COPIAR RELATÓRIO") { copyDiagnosticLog() }
        root.addView(diagnosticCopyButton, buttonParams(52))
        root.addView(space(8))
        root.addView(diagnosticReport, match())
        root.addView(space(18))

        root.addView(stepTitle("FREQUÊNCIA DESEJADA"))
        val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        hz90 = actionButton("90 Hz") { choose(90) }
        hz120 = actionButton("120 Hz") { choose(120) }
        choices.addView(hz90, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(6) })
        choices.addView(hz120, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(6) })
        root.addView(choices, match())
        root.addView(space(10))
        toggle = actionButton("RESTAURAR CONFIGURAÇÕES") { restoreSettings() }
        root.addView(toggle, buttonParams(50))
        root.addView(space(16))
        root.addView(TextView(this).apply {
            text = "Importante: o Atomic executa e verifica os comandos permitidos pelo Android. Isso não força todo jogo a renderizar em 90 FPS; o próprio jogo e o sistema podem limitar a taxa."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF7894AD.toInt())
        }, match())
        setContentView(ScrollView(this).apply { addView(root) })
        updateConnectionUi()
        updateButtons()
    }

    private fun checkShizuku() {
        val installed = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: Exception) { false }
        val binderAlive = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        shizukuReady = installed && binderAlive &&
            try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false }
        if (!installed) status.text = "Shizuku não instalado. Use o botão BAIXAR SHIZUKU."
        else if (!binderAlive) status.text = "Shizuku instalado, mas o serviço não está iniciado. Inicie-o no Shizuku."
        else if (!shizukuReady) status.text = "Serviço ativo. Toque em CONECTAR / AUTORIZAR."
        else status.text = "Shizuku conectado e autorizado."
        updateConnectionUi()
    }

    private fun connectShizuku() {
        val installed = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: Exception) { false }
        if (!installed) {
            status.text = "Instale o Shizuku primeiro."
            openShizukuDownload()
            return
        }
        val alive = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        if (!alive) {
            status.text = "Abra o Shizuku e inicie o serviço por Depuração sem fio. Depois volte aqui."
            return
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                shizukuReady = true
                status.text = "Shizuku conectado e autorizado."
                updateConnectionUi()
            } else {
                status.text = "Aguardando você autorizar o Atomic na janela do Shizuku..."
                Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE)
            }
        } catch (e: Exception) {
            status.text = "Não foi possível solicitar a autorização: ${e.message}"
        }
        updateConnectionUi()
    }

    private fun applySelectedMode() {
        if (!hasShizukuPermission()) return
        executeButton.isEnabled = false
        status.text = "Salvando preferências e solicitando $selectedHz Hz nas camadas compatíveis..."
        worker.execute {
            try {
                val systemKeys = listOf("min_refresh_rate", "peak_refresh_rate", "user_refresh_rate", "user_peak_refresh_rate", "miui_refresh_rate")
                val prefs = getSharedPreferences("atomic_refresh_backup", MODE_PRIVATE)
                systemKeys.forEach { key ->
                    val old = AtomicShizukuClient.run("settings get system $key 2>&1 || true").trim()
                    if (!prefs.contains(key)) prefs.edit().putString(key, old).apply()
                }
                val oldGlobal = AtomicShizukuClient.run("settings get global user_preferred_refresh_rate 2>&1 || true").trim()
                if (!prefs.contains("global_user_preferred_refresh_rate")) prefs.edit().putString("global_user_preferred_refresh_rate", oldGlobal).apply()
                val writes = systemKeys.joinToString(" ; ") { "settings put system $it $selectedHz 2>&1" } +
                    " ; settings put global user_preferred_refresh_rate $selectedHz 2>&1"
                val verify = systemKeys.joinToString(" ; ") { "echo $it=\$(settings get system $it)" } +
                    " ; echo global_user_preferred_refresh_rate=\$(settings get global user_preferred_refresh_rate)"
                val result = AtomicShizukuClient.run("$writes ; echo '--- PREFERÊNCIAS GRAVADAS ---' ; $verify ; echo '--- DISPLAY ATIVO ---' ; dumpsys display | grep -iE 'mActiveSfDisplayMode|mActiveRenderFrameRate|mDesiredDisplayModeSpecs|mDisplayModeSpecs|renderFrameRate' | head -n 35")
                runOnUiThread {
                    executeButton.isEnabled = true
                    enabled = true
                    diagnosticReport.text = "PREFERÊNCIAS DE $selectedHz HZ SOLICITADAS\n" +
                        "Foram alteradas preferências conhecidas de taxa de atualização. Limites térmicos e políticas internas do jogo não foram forçados.\n\n$result"
                    status.text = "Preferências de $selectedHz Hz gravadas. A taxa ativa ainda depende das políticas do HyperOS e do jogo."
                    refreshDisplayInfo()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    executeButton.isEnabled = true
                    status.text = "Falha ao solicitar $selectedHz Hz: ${e.message}"
                }
            }
        }
    }

    private fun tryFreeFire90Fps() {
        if (!hasShizukuPermission()) return
        status.text = "Solicitando 90 Hz e verificando o perfil do Free Fire..."
        worker.execute {
            // Cada comando é executado separadamente: evita erros de parsing do shell
            // causados por encadeamento, pipes e substituição de comandos.
            fun check(command: String): String = try {
                AtomicShizukuClient.run(command).ifBlank { "(sem saída)" }
            } catch (e: Exception) {
                "ERRO: ${e.message ?: "falha sem detalhes"}"
            }

            val keys = listOf("min_refresh_rate", "peak_refresh_rate", "user_refresh_rate", "user_peak_refresh_rate", "miui_refresh_rate")
            val report = StringBuilder()
            report.appendLine("=== SOLICITAÇÃO DE 90 HZ ===")
            for (key in keys) {
                report.appendLine("$key: " + check("settings put system $key 90"))
            }
            report.appendLine("user_preferred_refresh_rate: " + check("settings put global user_preferred_refresh_rate 90"))

            report.appendLine("\\n=== VALORES GRAVADOS ===")
            for (key in keys) {
                report.appendLine("$key=" + check("settings get system $key"))
            }
            report.appendLine("global_user_preferred_refresh_rate=" + check("settings get global user_preferred_refresh_rate"))

            report.appendLine("\\n=== PERFIL ANDROID DO FREE FIRE ===")
            report.appendLine(check("device_config get game_overlay com.dts.freefireth"))

            report.appendLine("\\n=== GAME MANAGER (LINHAS RELEVANTES) ===")
            val gameDump = check("dumpsys game")
            report.appendLine(gameDump.lineSequence().filter { it.contains("com.dts.freefireth", true) || it.contains("fps", true) || it.contains("mode", true) }.take(50).joinToString("\\n").ifBlank { "(nenhuma linha relevante)" })

            report.appendLine("\\n=== CAMADA DO FREE FIRE ===")
            val layers = check("dumpsys SurfaceFlinger --list")
            report.appendLine(layers.lineSequence().filter { it.contains("com.dts.freefireth", true) || it.contains("FFMainActivity", true) }.take(30).joinToString("\\n").ifBlank { "(camada não localizada)" })

            report.appendLine("\\n=== DISPLAY ATIVO ===")
            val display = check("dumpsys display")
            report.appendLine(display.lineSequence().filter { it.contains("mActiveSfDisplayMode", true) || it.contains("mActiveRenderFrameRate", true) || it.contains("renderFrameRate", true) }.take(25).joinToString("\\n").ifBlank { "(dados de taxa não encontrados)" })

            runOnUiThread {
                diagnosticReport.text = "TENTATIVA DE 90 FPS — FREE FIRE\\n" +
                    "Comandos executados individualmente para evitar erro de sintaxe. A proteção térmica foi preservada.\\n\\n" +
                    report.toString().take(10000)
                status.text = "Diagnóstico concluído. Confira os erros individuais no relatório."
                refreshDisplayInfo()
            }
        }
    }

    private fun exploreFreeFireStorage() {
        if (!hasShizukuPermission()) return
        exploreFreeFireButton.isEnabled = false
        status.text = "Inspecionando caminhos do Free Fire. Nenhum arquivo será alterado..."
        worker.execute {
            fun read(command: String): String = try {
                AtomicShizukuClient.run(command).ifBlank { "(sem saída)" }
            } catch (e: Exception) {
                "BLOQUEADO/ERRO: ${e.message ?: "sem detalhes"}"
            }

            val report = StringBuilder()
            report.appendLine("=== ATOMIC 120 HZ — EXPLORADOR FREE FIRE ===")
            report.appendLine("Modo: SOMENTE LEITURA. Nenhum arquivo foi criado, apagado ou modificado.")
            report.appendLine()
            report.appendLine("=== IDENTIDADE E NÍVEL DE ACESSO DO SHIZUKU ===")
            report.appendLine(read("id; echo \\\"SELINUX:\\\"; getenforce 2>&1; echo \\\"MONTAGENS:\\\"; mount | grep -E \\\"/storage|/data/user|/mnt/user\\\" | head -n 50"))
            report.appendLine()
            report.appendLine("=== PACOTE INSTALADO ===")
            report.appendLine(read("dumpsys package com.dts.freefireth | grep -iE 'versionName|versionCode|codePath|dataDir|credentialProtectedDataDir|deviceProtectedDataDir|userId|targetSdk' | head -n 60"))
            report.appendLine()
            report.appendLine("=== CAMINHO DO APK ===")
            report.appendLine(read("pm path com.dts.freefireth 2>&1"))
            report.appendLine()
            report.appendLine("=== PASTA PRIVADA DO APLICATIVO ===")
            report.appendLine(read("ls -ld /data/user/0/com.dts.freefireth /data/data/com.dts.freefireth /data/user_de/0/com.dts.freefireth 2>&1"))
            report.appendLine("Nota: 'Permission denied' é o isolamento normal do Android para dados privados.")
            report.appendLine()
            report.appendLine("=== TESTE DE ACESSO A ANDROID/DATA (VÁRIOS CAMINHOS) ===")
            report.appendLine(read("for p in /sdcard/Android/data/com.dts.freefireth /storage/emulated/0/Android/data/com.dts.freefireth /mnt/user/0/primary/Android/data/com.dts.freefireth; do echo \\\"--- $p ---\\\"; ls -la \\\"$p\\\" 2>&1; done"))
            report.appendLine()
            report.appendLine("=== DADOS EXTERNOS DO JOGO ===")
            report.appendLine(read("ls -la /sdcard/Android/data/com.dts.freefireth 2>&1"))
            report.appendLine()
            report.appendLine("=== OBB / ARQUIVOS DE EXPANSÃO ===")
            report.appendLine(read("ls -la /sdcard/Android/obb/com.dts.freefireth 2>&1"))
            report.appendLine()
            report.appendLine("=== ARQUIVOS VISÍVEIS (APENAS NOMES, ATÉ 3 NÍVEIS) ===")
            report.appendLine(read("find /sdcard/Android/data/com.dts.freefireth -maxdepth 3 -type f 2>&1 | head -n 100"))
            report.appendLine()
            report.appendLine("=== PERMISSÕES DO PACOTE ===")
            report.appendLine(read("cmd appops get com.dts.freefireth 2>&1 | head -n 80"))
            report.appendLine()
            report.appendLine("=== PROCESSO DO JOGO (SE ESTIVER ABERTO) ===")
            report.appendLine(read("pidof com.dts.freefireth 2>&1"))
            report.appendLine()
            report.appendLine("=== RESULTADO ===")
            report.appendLine("Este relatório mostra o que o Android permite ao Shizuku/UID shell enxergar. Não tenta obter root, invadir sandbox, contornar anti-cheat ou alterar dados protegidos.")
            runOnUiThread {
                exploreFreeFireButton.isEnabled = true
                diagnosticReport.text = report.toString().take(14000)
                status.text = "Exploração concluída. Confira quais caminhos estão acessíveis e quais foram bloqueados."
            }
        }
    }

    private fun diagnoseFreeFireLimit() {
        if (!hasShizukuPermission()) return
        status.text = "Verificando configurações do sistema e perfil do Free Fire..."
        worker.execute {
            try {
                val command = """
                    echo '=== PACOTE FREE FIRE ==='
                    dumpsys package com.dts.freefireth | grep -iE 'versionName|versionCode|targetSdk' || true
                    echo '=== TAXAS CONFIGURADAS ==='
                    echo -n 'min_refresh_rate='; settings get system min_refresh_rate
                    echo -n 'peak_refresh_rate='; settings get system peak_refresh_rate
                    echo -n 'user_refresh_rate='; settings get system user_refresh_rate
                    echo -n 'user_peak_refresh_rate='; settings get system user_peak_refresh_rate
                    echo '=== PERFIL GAME OVERLAY ==='
                    device_config get game_overlay com.dts.freefireth 2>&1 || true
                    echo '=== JOGOS RECONHECIDOS PELO ANDROID ==='
                    cmd game list 2>&1 || true
                    echo '=== ESTADO DO DISPLAY ==='
                    dumpsys display | grep -iE 'mActiveDisplayMode|mActiveSfDisplayMode|defaultModeId|defaultPeakRefreshRate|renderFrameRate|refreshRate' | head -n 45 || true
                    echo '=== SERVICO DE JOGOS ==='
                    dumpsys game 2>&1 | grep -i -A 5 -B 3 'com.dts.freefireth' | head -n 60 || true
                """.trimIndent()
                val output = AtomicShizukuClient.run(command).take(10000)
                runOnUiThread {
                    diagnosticReport.text = "VERIFICAÇÃO DO LIMITE DO FREE FIRE\n" +
                        "Coleta somente leitura; nenhum arquivo ou configuração do jogo foi alterado.\n\n" +
                        output.ifBlank { "O sistema não retornou dados para esses itens." }
                    status.text = "Verificação concluída. Copie o relatório e envie aqui."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    diagnosticReport.text = "Falha na verificação: " + e.message
                    status.text = "Não foi possível concluir o diagnóstico."
                }
            }
        }
    }

    private fun investigateAndroidFpsPolicy() {
        if (!hasShizukuPermission()) return
        status.text = "Investigando políticas de FPS do Android e HyperOS (somente leitura)..."
        worker.execute {
            try {
                val command = """
                    echo '===== ATOMIC 120 HZ - POLITICAS ANDROID/HYPEROS ====='
                    echo '===== 1. PACOTE E PROCESSO DO FREE FIRE ====='
                    dumpsys package com.dts.freefireth | grep -iE 'versionName|versionCode|userId|targetSdk|enabled=' | head -n 35 || true
                    echo '===== 2. POLITICA GAME MANAGER ====='
                    dumpsys game_manager 2>&1 | grep -i -A 12 -B 5 -E 'com.dts.freefireth|game.?mode|intervention|fps|frame.?rate|downscale' | head -n 180 || true
                    echo '===== 3. SERVICO GAME ====='
                    dumpsys game 2>&1 | grep -i -A 12 -B 5 -E 'com.dts.freefireth|game.?mode|intervention|fps|frame.?rate|downscale' | head -n 180 || true
                    echo '===== 4. COMANDOS GAME DISPONIVEIS ====='
                    cmd game help 2>&1 | head -n 100 || true
                    echo '===== 5. DEVICE CONFIG GAME OVERLAY ====='
                    device_config get game_overlay com.dts.freefireth 2>&1 || true
                    device_config list game_overlay 2>&1 | grep -i -A 3 -B 3 'com.dts.freefireth' | head -n 80 || true
                    echo '===== 6. CONFIGURACOES GLOBAIS DE JOGOS E FPS ====='
                    settings list global | grep -iE 'game|fps|frame|refresh|refresh_rate|power' | head -n 140 || true
                    echo '===== 7. CONFIGURACOES SYSTEM DE DISPLAY ====='
                    settings list system | grep -iE 'game|fps|frame|refresh|refresh_rate|miui|thermal' | head -n 140 || true
                    echo '===== 8. CAMADAS DO FREE FIRE ====='
                    dumpsys SurfaceFlinger --list 2>&1 | grep -iE 'freefire|FFMainActivity|SurfaceView' | head -n 100 || true
                    echo '===== 9. TAXA ATIVA E LIMITADORES DO DISPLAY ====='
                    dumpsys display 2>&1 | grep -iE -A 3 -B 3 'mActiveSfDisplayMode|mActiveRenderFrameRate|mDesiredDisplayModeSpecs|mDisplayModeSpecs|mRefreshRateLimitations|mRefreshRateThrottling|thermal|low.?power|renderFrameRate|userPreferredModeId|supportedModes' | head -n 220 || true
                    echo '===== 10. PROPRIEDADES DO FABRICANTE RELACIONADAS A JOGOS ====='
                    getprop 2>/dev/null | grep -iE 'game|fps|frame.?rate|refresh|thermal|performance|power.?mode' | head -n 180 || true
                    echo '===== 11. PACOTES DE OTIMIZACAO PRESENTES (SEM ALTERAR) ====='
                    pm list packages | grep -iE 'game|securitycenter|performance|powerkeeper|thermal' | head -n 120 || true
                    echo '===== 12. ESTADO TERMICO E ENERGIA ====='
                    dumpsys thermalservice 2>&1 | head -n 100 || true
                    dumpsys power 2>&1 | grep -iE 'low.?power|power.?save|thermal|mode|interactive' | head -n 100 || true
                    echo '===== FIM - COLETA SOMENTE LEITURA ====='
                """.trimIndent()
                val output = AtomicShizukuClient.run(command).take(22000)
                runOnUiThread {
                    diagnosticReport.text = "INVESTIGAÇÃO DAS POLÍTICAS ANDROID/HYPEROS\n" +
                        "Coleta somente leitura. Nenhum serviço, pacote ou configuração foi desativado ou alterado.\n\n" +
                        output.ifBlank { "O sistema não retornou dados para esses itens." }
                    status.text = "Investigação concluída. Copie o relatório completo e envie aqui."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    diagnosticReport.text = "Falha na investigação: " + e.message
                    status.text = "Não foi possível concluir a investigação."
                }
            }
        }
    }

    private fun force90Hz() {
        if (!hasShizukuPermission()) return
        status.text = "Salvando valores atuais e solicitando 90 Hz em todas as preferências compatíveis..."
        worker.execute {
            try {
                val systemKeys = listOf("min_refresh_rate", "peak_refresh_rate", "user_refresh_rate", "user_peak_refresh_rate", "miui_refresh_rate")
                val prefs = getSharedPreferences("atomic_refresh_backup", MODE_PRIVATE)
                systemKeys.forEach { key ->
                    val old = AtomicShizukuClient.run("settings get system $key 2>&1 || true").trim()
                    if (!prefs.contains(key)) prefs.edit().putString(key, old).apply()
                }
                val oldGlobal = AtomicShizukuClient.run("settings get global user_preferred_refresh_rate 2>&1 || true").trim()
                if (!prefs.contains("global_user_preferred_refresh_rate")) prefs.edit().putString("global_user_preferred_refresh_rate", oldGlobal).apply()
                val writes = systemKeys.joinToString(" ; ") { "settings put system $it 90 2>&1" } +
                    " ; settings put global user_preferred_refresh_rate 90 2>&1"
                val verify = systemKeys.joinToString(" ; ") { "echo $it=\$(settings get system $it)" } +
                    " ; echo global_user_preferred_refresh_rate=\$(settings get global user_preferred_refresh_rate)"
                val result = AtomicShizukuClient.run(
                    "$writes ; echo '--- PREFERÊNCIAS GRAVADAS ---' ; $verify" +
                    " ; echo '--- ESTADO REAL DO DISPLAY ---' ; dumpsys display | grep -iE 'mActiveSfDisplayMode|mActiveRenderFrameRate|mActiveModeId|mDesiredDisplayModeSpecs|mDisplayModeSpecs|renderFrameRate|mRefreshRateThrottling' | head -n 55" +
                    " ; echo '--- PERFIL DE FPS DO FREE FIRE (LEITURA) ---' ; dumpsys game 2>&1 | grep -i -A 8 -B 3 'com.dts.freefireth' | head -n 45"
                )
                runOnUiThread {
                    status.text = "Preferências compatíveis de 90 Hz gravadas. Limites de jogo, hardware e temperatura continuam sob controle do sistema."
                    diagnosticReport.text = "ATOMIC 120 HZ — APLICAÇÃO AMPLIADA DE 90 HZ\n" +
                        "Valores anteriores salvos para restauração. Nenhum limite térmico foi alterado; políticas internas do jogo/HyperOS não são sobrescritas.\n\n" + result
                    refreshDisplayInfo()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Falha ao solicitar 90 Hz: ${e.message}" }
            }
        }
    }

    private fun requestNative90Hz() {
        if (!hasShizukuPermission()) return
        status.text = "Verificando a resolução física e aplicando as preferências de 90 Hz..."
        worker.execute {
            try {
                // Apenas lê a resolução física; nunca altera wm size.
                val sizeOutput = AtomicShizukuClient.run("wm size 2>&1 || true")
                val physicalMatch = Regex("(?im)^Physical size:\\s*(\\d+)x(\\d+)").find(sizeOutput)
                    ?: Regex("(?m)^(\\d+)x(\\d+)$").find(sizeOutput)
                    ?: throw IOException("Não foi possível ler a resolução física. Saída: $sizeOutput")
                val width = physicalMatch.groupValues[1]
                val height = physicalMatch.groupValues[2]
                val prefs = getSharedPreferences("atomic_refresh_backup", MODE_PRIVATE)
                val systemKeys = listOf(
                    "min_refresh_rate", "peak_refresh_rate", "user_refresh_rate",
                    "user_peak_refresh_rate", "miui_refresh_rate"
                )
                systemKeys.forEach { key ->
                    if (!prefs.contains(key)) {
                        val old = AtomicShizukuClient.run("settings get system $key 2>&1 || true").trim()
                        prefs.edit().putString(key, old).apply()
                    }
                }
                if (!prefs.contains("global_user_preferred_refresh_rate")) {
                    val old = AtomicShizukuClient.run("settings get global user_preferred_refresh_rate 2>&1 || true").trim()
                    prefs.edit().putString("global_user_preferred_refresh_rate", old).apply()
                }

                val before = AtomicShizukuClient.run("cmd display get-user-preferred-display-mode 2>&1 || true")
                val nativeAttempt = try {
                    AtomicShizukuClient.run("cmd display set-user-preferred-display-mode $width $height 90 2>&1")
                } catch (e: Exception) {
                    "O HyperOS recusou o comando nativo: ${e.message}"
                }

                // Fallback: preferências de taxa já identificadas no Redmi/HyperOS.
                // Não altera resolução, não desativa serviços e não remove proteção térmica.
                val writes = systemKeys.joinToString(" ; ") {
                    "settings put system $it 90 2>&1 || true"
                } + " ; settings put global user_preferred_refresh_rate 90 2>&1 || true"
                val verify = systemKeys.joinToString(" ; ") {
                    "echo $it=\$(settings get system $it 2>&1)"
                } + " ; echo global_user_preferred_refresh_rate=\$(settings get global user_preferred_refresh_rate 2>&1)"
                val fallbackResult = AtomicShizukuClient.run(
                    "$writes ; echo '--- CONFERENCIA DAS PREFERENCIAS ---' ; $verify"
                )
                val after = AtomicShizukuClient.run("cmd display get-user-preferred-display-mode 2>&1 || true")
                val state = AtomicShizukuClient.run(
                    "dumpsys display 2>&1 | grep -iE 'mActiveSfDisplayMode|mActiveRenderFrameRate|mActiveModeId|mDesiredDisplayModeSpecs|mDisplayModeSpecs|mRefreshRateThrottling|renderFrameRate|supportedModes' | head -n 100"
                )
                runOnUiThread {
                    diagnosticReport.text = "RESOLUÇÃO NATIVA + SOLICITAÇÃO DE 90 HZ\n" +
                        "Resolução física lida: $width x $height (nenhuma alteração de resolução foi executada).\n\n" +
                        "TENTATIVA DO DISPLAYMANAGER:\n$nativeAttempt\n\n" +
                        "PREFERÊNCIA NATIVA ANTES:\n$before\n\n" +
                        "PREFERÊNCIA NATIVA DEPOIS:\n$after\n\n" +
                        "$fallbackResult\n\nESTADO REAL DO DISPLAY:\n$state"
                    status.text = "Preferências de 90 Hz reaplicadas. Confira no relatório se o HyperOS ativou 90 Hz; a resolução foi preservada."
                    refreshDisplayInfo()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    diagnosticReport.text = "Falha ao solicitar 90 Hz: ${e.message}"
                    status.text = "Não foi possível concluir a solicitação."
                }
            }
        }
    }

    private fun startFpsDiagnostic() {
        if (!hasShizukuPermission()) return
        diagnosticStartButton.isEnabled = false
        diagnosticReport.text = "Iniciando captura do SurfaceFlinger..."
        worker.execute {
            try {
                val output = AtomicShizukuClient.run("dumpsys SurfaceFlinger --timestats -clear && dumpsys SurfaceFlinger --timestats -enable")
                runOnUiThread {
                    diagnosticStartButton.isEnabled = true
                    diagnosticReport.text = "MEDIÇÃO INICIADA. Agora volte ao Free Fire, jogue por aproximadamente 30 segundos e retorne ao Atomic. Depois toque em FINALIZAR.\\n\\n$output"
                    status.text = "Captura ativa. Jogue por 30 segundos e volte para finalizar."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    diagnosticStartButton.isEnabled = true
                    diagnosticReport.text = "Não foi possível iniciar a captura: ${e.message}\\nSeu Android pode não oferecer essa opção do SurfaceFlinger."
                }
            }
        }
    }

    private fun finishFpsDiagnostic() {
        if (!hasShizukuPermission()) return
        diagnosticStopButton.isEnabled = false
        diagnosticReport.text = "Lendo os quadros capturados..."
        worker.execute {
            try {
                val dump = AtomicShizukuClient.run("dumpsys SurfaceFlinger --timestats -dump")
                try {
                    AtomicShizukuClient.run("dumpsys SurfaceFlinger --timestats -disable")
                } catch (_: Exception) { }
                val report = dump.take(14000)
                val gameMentioned = dump.contains("com.dts.freefireth", ignoreCase = true) ||
                    dump.contains("freefire", ignoreCase = true)
                runOnUiThread {
                    diagnosticStopButton.isEnabled = true
                    diagnosticReport.text = (if (gameMentioned) {
                        "O relatório menciona o Free Fire. Confira as camadas e contagens abaixo.\\n\\n"
                    } else {
                        "O nome do Free Fire não apareceu claramente no relatório. Isso não prova ausência de captura; confira as camadas listadas.\\n\\n"
                    }) + report
                    status.text = "Diagnóstico finalizado. Envie uma captura deste relatório para análise."
                }
            } catch (e: Exception) {
                try { AtomicShizukuClient.run("dumpsys SurfaceFlinger --timestats -disable") } catch (_: Exception) { }
                runOnUiThread {
                    diagnosticStopButton.isEnabled = true
                    diagnosticReport.text = "Falha ao ler o relatório: ${e.message}"
                }
            }
        }
    }

    private fun copyDiagnosticLog() {
        val report = diagnosticReport.text?.toString().orEmpty()
        if (report.isBlank() || report == "O relatório aparecerá aqui.") {
            status.text = "Gere o relatório primeiro para copiar o log."
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Atomic 120 Hz - Log FPS", report))
        status.text = "Log copiado! Agora é só colar aqui na conversa."
    }

    private fun restoreSettings() {
        if (!hasShizukuPermission()) return
        toggle.isEnabled = false
        worker.execute {
            try {
                val prefs = getSharedPreferences("atomic_refresh_backup", MODE_PRIVATE)
                val keys = listOf("min_refresh_rate", "peak_refresh_rate", "user_refresh_rate", "miui_refresh_rate", "thermal_limit_refresh_rate")
                val restoreCommands = keys.mapNotNull { key ->
                    if (!prefs.contains(key)) null
                    else {
                        val old = prefs.getString(key, "null") ?: "null"
                        if (old == "null") "settings delete system $key" else "settings put system $key $old"
                    }
                }.toMutableList()
                if (prefs.contains("global_user_preferred_refresh_rate")) {
                    val oldGlobal = prefs.getString("global_user_preferred_refresh_rate", "null") ?: "null"
                    restoreCommands.add(if (oldGlobal == "null") "settings delete global user_preferred_refresh_rate" else "settings put global user_preferred_refresh_rate $oldGlobal")
                }
                if (restoreCommands.isNotEmpty()) {
                    AtomicShizukuClient.run(restoreCommands.joinToString(" && ") + " && cmd display clear-user-preferred-display-mode 2>/dev/null || true")
                    prefs.edit().clear().apply()
                } else {
                    AtomicShizukuClient.run("settings delete system min_refresh_rate && settings delete system peak_refresh_rate")
                }
                runOnUiThread {
                    enabled = false
                    toggle.isEnabled = true
                    status.text = "Preferências removidas. O sistema voltou a controlar a frequência."
                    refreshDisplayInfo()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    toggle.isEnabled = true
                    status.text = "Falha ao restaurar: ${e.message}"
                }
            }
        }
    }

    private fun hasShizukuPermission(): Boolean {
        val alive = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        val granted = alive && try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false }
        shizukuReady = granted
        updateConnectionUi()
        if (!granted) {
            status.text = if (alive) {
                "Solicitando autorização do Shizuku. Aceite a janela de permissão e toque novamente na função."
            } else {
                "Inicie o serviço Shizuku por Depuração sem fio antes de continuar."
            }
            if (alive) {
                try { Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE) } catch (_: Exception) { }
            }
        }
        return granted
    }

    private fun openShizukuDownload() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE")))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
        }
    }

    private fun choose(hz: Int) {
        selectedHz = hz
        executeButton.text = "EXECUTAR COMANDOS PARA $hz FPS"
        updateButtons()
        status.text = "Selecionado: $hz Hz. Conecte o Shizuku para aplicar."
    }

    private fun refreshDisplayInfo() {
        val display = window.decorView.display
        val modes = display?.supportedModes?.map { it.refreshRate.toInt().toString() }?.distinct()
        val current = display?.refreshRate
        detected.text = "FREQUÊNCIA REPORTADA\n" +
            (current?.let { "${it.toInt()} Hz" } ?: "Indisponível") +
            "\n\nModos informados: " + (modes?.joinToString(" / ")?.plus(" Hz") ?: "não disponíveis")
    }

    private fun updateConnectionUi() {
        if (!::connectButton.isInitialized || !::executeButton.isInitialized) return
        connectButton.text = if (shizukuReady) "SHIZUKU CONECTADO E AUTORIZADO" else "CONECTAR AO SHIZUKU / AUTORIZAR"
        executeButton.isEnabled = shizukuReady
        executeButton.alpha = if (shizukuReady) 1f else .55f
    }

    private fun updateButtons() {
        if (!::hz90.isInitialized) return
        hz90.alpha = if (selectedHz == 90) 1f else .55f
        hz120.alpha = if (selectedHz == 120) 1f else .55f
    }

    private fun stepTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 17f
        gravity = Gravity.CENTER
        setTextColor(0xFF55C9FF.toInt())
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(0xFF174568.toInt())
        setOnClickListener { action() }
    }

    private fun buttonParams(height: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(height)
    )

    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun match() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SHIZUKU_PERMISSION_CODE = 120
    }
}
