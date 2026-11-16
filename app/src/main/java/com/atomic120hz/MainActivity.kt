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
        status.text = "Executando e verificando os comandos..."
        worker.execute {
            try {
                val min = AtomicShizukuClient.run("settings put system min_refresh_rate $selectedHz && settings get system min_refresh_rate")
                val max = AtomicShizukuClient.run("settings put system peak_refresh_rate $selectedHz && settings get system peak_refresh_rate")
                val preferred = if (selectedHz == 90) {
                    AtomicShizukuClient.run("cmd display set-user-preferred-display-mode 1080 2460 90 && cmd display get-user-preferred-display-mode")
                } else {
                    "Modo preferido do display não alterado: este aparelho não possui 120 Hz."
                }
                val active = AtomicShizukuClient.run("dumpsys display | grep -iE 'mActiveRenderFrameRate|mActiveSfDisplayMode|renderFrameRate' | head -n 12")
                runOnUiThread {
                    executeButton.isEnabled = true
                    enabled = true
                    status.text = "Tentativa de 90 Hz concluída. Preferência: $preferred. Mínimo: $min; máximo: $max. Estado ativo: $active. Se continuar em 60 Hz, o HyperOS está impondo outro limite."
                    refreshDisplayInfo()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    executeButton.isEnabled = true
                    status.text = "Falha ao executar: ${e.message}"
                }
            }
        }
    }

    private fun diagnoseFreeFireLimit() {
        if (!hasShizukuPermission()) return
        status.text = "Investigando políticas de tela, energia, temperatura e perfil de jogo..."
        diagnosticReport.text = "Coletando diagnóstico somente leitura..."
        diagnosticCopyButton.isEnabled = false
        worker.execute {
            try {
                val command = """
                    echo '===== ATOMIC 120 HZ - INVESTIGACAO DO LIMITE DE FPS ====='
                    echo 'Coleta somente leitura. Nenhuma configuracao sera alterada.'
                    echo
                    echo '===== 1. APARELHO E ANDROID ====='
                    getprop ro.product.manufacturer
                    getprop ro.product.model
                    getprop ro.build.version.release
                    getprop ro.build.version.sdk
                    getprop ro.miui.ui.version.name
                    getprop ro.build.version.incremental
                    echo
                    echo '===== 2. FREE FIRE INSTALADO ====='
                    dumpsys package com.dts.freefireth | grep -iE 'versionName|versionCode|targetSdk|enabled=' | head -n 20
                    echo
                    echo '===== 3. TAXAS CONFIGURADAS ====='
                    echo -n 'system.min_refresh_rate='; settings get system min_refresh_rate
                    echo -n 'system.peak_refresh_rate='; settings get system peak_refresh_rate
                    echo -n 'system.user_refresh_rate='; settings get system user_refresh_rate
                    echo -n 'system.user_peak_refresh_rate='; settings get system user_peak_refresh_rate
                    echo -n 'secure.min_refresh_rate='; settings get secure min_refresh_rate
                    echo -n 'secure.peak_refresh_rate='; settings get secure peak_refresh_rate
                    echo -n 'global.min_refresh_rate='; settings get global min_refresh_rate
                    echo -n 'global.peak_refresh_rate='; settings get global peak_refresh_rate
                    echo
                    echo '===== 4. POLITICA ATIVA DO DISPLAY ====='
                    dumpsys display 2>&1 | grep -iE -C 4 'mDisplayModeSpecs|mDesiredDisplayModeSpecs|mActiveRenderFrameRate|mActiveSfDisplayMode|mDefaultModeId|defaultModeId|userPreferredModeId|renderFrameRate|RefreshRateData|defaultPeakRefreshRate|RefreshRateLimit|Throttling|BlockingZone|baseModeId|primary=' | head -n 240
                    echo
                    echo '===== 5. PERFIL DE JOGO / GAME OVERLAY ====='
                    echo -n 'game_overlay Free Fire='
                    device_config get game_overlay com.dts.freefireth 2>&1
                    echo '--- game_overlay linhas relacionadas ---'
                    device_config list game_overlay 2>&1 | grep -iE 'freefire|dts|fps|mode' | head -n 100
                    echo '--- comandos game disponíveis ---'
                    cmd game help 2>&1 | head -n 80
                    echo '--- serviço Game ---'
                    dumpsys game 2>&1 | grep -i -A 10 -B 5 'com.dts.freefireth' | head -n 120
                    echo
                    echo '===== 6. TEMPERATURA / THERMAL ====='
                    dumpsys thermalservice 2>&1 | head -n 180
                    echo
                    echo '===== 7. ENERGIA / ECONOMIA ====='
                    dumpsys power 2>&1 | grep -iE -C 3 'mIsPowered|mPlugType|mBatteryLevel|low.power|power.save|Battery Saver|mPowerSave|mDeviceIdle|thermal|brightness' | head -n 140
                    echo
                    echo '===== 8. SERVICOS DE JOGO, DISPLAY E FABRICANTE ====='
                    dumpsys -l 2>&1 | grep -iE 'game|display|power|thermal|miui|perf|refresh|surface' | head -n 160
                    echo
                    echo '===== 9. PROPRIEDADES DO FABRICANTE RELACIONADAS A FPS ====='
                    getprop 2>&1 | grep -iE 'refresh|fps|frame.?rate|game.?mode|thermal|perf|power.?save' | head -n 160
                    echo
                    echo '===== 10. JANELA / ATIVIDADE EM PRIMEIRO PLANO ====='
                    dumpsys window 2>&1 | grep -iE 'mCurrentFocus|mFocusedApp' | head -n 20
                    dumpsys activity activities 2>&1 | grep -iE 'topResumedActivity|mResumedActivity' | head -n 20
                    echo
                    echo '===== 11. CAMADAS DO SURFACEFLINGER COM FREE FIRE ====='
                    dumpsys SurfaceFlinger --list 2>&1 | grep -iE 'freefire|dts.freefireth|SurfaceView' | head -n 100
                    echo
                    echo '===== FIM DO RELATORIO ====='
                """.trimIndent()
                val output = AtomicShizukuClient.run(command).take(24000)
                runOnUiThread {
                    diagnosticReport.text = output.ifBlank { "O sistema não retornou dados." }
                    diagnosticCopyButton.isEnabled = true
                    status.text = "Investigação concluída. Toque em COPIAR RELATÓRIO e envie o texto aqui."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    diagnosticReport.text = "Falha na investigação: \${e.message}"
                    diagnosticCopyButton.isEnabled = true
                    status.text = "Não foi possível concluir a investigação."
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
                AtomicShizukuClient.run("cmd display clear-user-preferred-display-mode")
                AtomicShizukuClient.run("settings delete system min_refresh_rate")
                AtomicShizukuClient.run("settings delete system peak_refresh_rate")
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
        if (!granted) status.text = "Conecte o Shizuku e autorize o Atomic antes de executar."
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
