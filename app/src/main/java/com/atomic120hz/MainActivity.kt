package com.atomic120hz

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.res.ColorStateList
import android.text.InputType
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.mdns.KadbMdnsAndroid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var selectedHz = 90
    private var adbReady = false
    private var busy = false
    private var adb: Kadb? = null
    private lateinit var codeInput: EditText
    private lateinit var logView: TextView
    private lateinit var pairButton: Button
    private lateinit var modeButton: Button
    private lateinit var hz90: Button
    private lateinit var hz120: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 15, 29)
        window.navigationBarColor = Color.rgb(5, 15, 29)
        buildUi()
    }

    override fun onDestroy() {
        adb?.close()
        adb = null
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(Color.rgb(5, 15, 29))
        }
        root.addView(label("⚛  ATOMIC 120 Hz", 27f, 0xFF55C9FF.toInt(), true), match())
        root.addView(label("ATOMIC ACCESS • ADB INTEGRADO", 12f, 0xFF9ABBD5.toInt()), match())
        root.addView(space(18))
        root.addView(label("PAREAMENTO AUTOMÁTICO", 16f, Color.WHITE, true), match())
        root.addView(label("1. Ative Opções do desenvolvedor e Depuração sem fio.\n2. Abra “Parear dispositivo com código”.\n3. Digite aqui o código de 6 dígitos. O Atomic detecta as portas automaticamente.", 13f, 0xFF9ABBD5.toInt()), match())
        codeInput = EditText(this).apply {
            hint = "Código de pareamento (6 dígitos)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF7894AD.toInt())
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            backgroundTintList = ColorStateList.valueOf(0xFF55C9FF.toInt())
        }
        root.addView(codeInput, match())
        root.addView(space(8))
        pairButton = Button(this).apply {
            text = "LIBERAR ACESSO"
            isAllCaps = false
            setOnClickListener { pairAndConnect() }
        }
        root.addView(pairButton, LinearLayout.LayoutParams(-1, dp(58)))
        root.addView(space(18))
        root.addView(label("FREQUÊNCIA DESEJADA", 14f, 0xFF55C9FF.toInt(), true), match())
        val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hz90 = Button(this).apply { text = "90 Hz"; isAllCaps = false; setOnClickListener { selectedHz = 90; updateChoices() } }
        hz120 = Button(this).apply { text = "120 Hz"; isAllCaps = false; setOnClickListener { selectedHz = 120; updateChoices() } }
        choices.addView(hz90, LinearLayout.LayoutParams(0, dp(50), 1f))
        choices.addView(hz120, LinearLayout.LayoutParams(0, dp(50), 1f))
        root.addView(choices, match())
        modeButton = Button(this).apply {
            text = "ATIVAR MODO"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { applyRefreshRate() }
        }
        root.addView(modeButton, LinearLayout.LayoutParams(-1, dp(62)))
        root.addView(space(12))
        root.addView(label("TERMINAL DE EXECUÇÃO", 14f, 0xFF55C9FF.toInt(), true), match())
        logView = TextView(this).apply {
            text = "Aguardando autorização ADB. Nenhum comando foi executado."
            textSize = 12f
            setTextColor(0xFFD2E8F8.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFF102943.toInt())
        }
        root.addView(logView, match())
        setContentView(ScrollView(this).apply { addView(root) })
        updateChoices()
    }

    private fun pairAndConnect() {
        if (busy) return
        val code = codeInput.text.toString().trim()
        if (!code.matches(Regex("\\d{6}"))) {
            appendLog("Informe o código de pareamento de 6 dígitos mostrado pelo Android.")
            return
        }
        setBusy(true)
        appendLog("Iniciando descoberta mDNS do serviço ADB de pareamento…")
        thread {
            val discovery = KadbMdnsAndroid(applicationContext)
            try {
                discovery.start()
                val pairEndpoint = runBlocking {
                    withTimeout(45_000) {
                        discovery.state.first { it.pairDevices.isNotEmpty() }.pairDevices.first()
                    }
                }
                runOnUiThread { appendLog("Porta de pareamento encontrada automaticamente. Realizando handshake TLS…") }
                runBlocking { Kadb.pair(pairEndpoint.host, pairEndpoint.port, code) }
                runOnUiThread { appendLog("Pareamento aceito. Procurando a porta segura de conexão…") }
                val connectEndpoint = runBlocking {
                    withTimeout(45_000) {
                        discovery.state.first { it.connectDevices.isNotEmpty() }.connectDevices.first()
                    }
                }
                val connection = Kadb.create(connectEndpoint.host, connectEndpoint.port)
                val check = connection.shell("echo ATOMIC_ADB_OK")
                if (check.exitCode != 0 || !check.allOutput.contains("ATOMIC_ADB_OK")) {
                    connection.close()
                    throw IllegalStateException("A conexão não confirmou o teste de shell: ${check.allOutput}")
                }
                adb?.close()
                adb = connection
                adbReady = true
                runOnUiThread {
                    appendLog("CONEXÃO CONFIRMADA: pareamento TLS concluído e teste de shell retornou ATOMIC_ADB_OK.")
                    pairButton.text = "ADB CONECTADO"
                    appendLog("Iniciando diagnóstico e configuração automática para 90 Hz…")
                }
                runAutomaticSetup(connection)
                runOnUiThread {
                    modeButton.isEnabled = true
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    adbReady = false
                    modeButton.isEnabled = false
                    appendLog("FALHA REAL NO PAREAMENTO/CONEXÃO: ${e.message ?: e.javaClass.simpleName}")
                    setBusy(false)
                }
            } finally {
                discovery.close()
            }
        }
    }


    /**
     * Rotina automática conservadora: coleta evidências reais do aparelho e só solicita
     * 90 Hz se o próprio dumpsys informar que há um modo de 90 Hz disponível.
     * Não tenta alterar limites internos do jogo nem propriedades protegidas do fabricante.
     */
    private fun runAutomaticSetup(connection: Kadb) {
        fun command(title: String, cmd: String): String {
            runOnUiThread { appendLog(title) }
            val result = connection.shell(cmd)
            if (result.exitCode != 0) {
                runOnUiThread { appendLog("COMANDO RECUSADO (código ${result.exitCode}): ${result.allOutput.take(500)}") }
                return ""
            }
            return result.allOutput.trim()
        }

        try {
            val androidVersion = command("Identificando versão do Android…", "getprop ro.build.version.release")
            val manufacturer = command("Identificando fabricante…", "getprop ro.product.manufacturer")
            val model = command("Identificando modelo…", "getprop ro.product.model")
            val hyperOs = command("Verificando versão HyperOS/MIUI…", "getprop ro.miui.ui.version.name")
            val peakBefore = command("Lendo limite máximo atual…", "settings get system peak_refresh_rate")
            val minBefore = command("Lendo limite mínimo atual…", "settings get system min_refresh_rate")
            val power = command("Verificando modo de economia de energia…", "settings get global low_power")
            val gamePath = command("Verificando instalação do Free Fire…", "pm path com.dts.freefireth")
            val displayDump = command("Lendo modos de exibição informados pelo Android…", "dumpsys display")
            val has90 = Regex("""(?i)(fps|refreshRate|refresh rate)[^\\n]{0,35}90(?:\\.0+)?""").containsMatchIn(displayDump) ||
                Regex("""(?i)90(?:\\.0+)?[^\\n]{0,35}(fps|refreshRate|refresh rate)""").containsMatchIn(displayDump)
            val has120 = Regex("""(?i)(fps|refreshRate|refresh rate)[^\\n]{0,35}120(?:\\.0+)?""").containsMatchIn(displayDump) ||
                Regex("""(?i)120(?:\\.0+)?[^\\n]{0,35}(fps|refreshRate|refresh rate)""").containsMatchIn(displayDump)

            runOnUiThread {
                appendLog("DIAGNÓSTICO: fabricante=$manufacturer; modelo=$model; Android=$androidVersion; HyperOS/MIUI=$hyperOs.")
                appendLog("ESTADO ANTERIOR: peak=$peakBefore; min=$minBefore; economia=$power.")
                appendLog(if (gamePath.contains("package:", true)) "Free Fire instalado." else "Free Fire não encontrado (com.dts.freefireth).")
                appendLog("MODOS DETECTADOS NO DUMPSYS: 90 Hz=$has90; 120 Hz=$has120.")
            }

            if (!has90) {
                runOnUiThread { appendLog("AUTOMAÇÃO PAUSADA: o relatório do sistema não confirmou modo de 90 Hz. Nenhuma alteração foi aplicada.") }
                return
            }

            val prefs = getSharedPreferences("atomic_refresh_backup", MODE_PRIVATE)
            if (!prefs.contains("peak")) {
                prefs.edit().putString("peak", peakBefore.ifBlank { "null" })
                    .putString("min", minBefore.ifBlank { "null" }).apply()
            }

            command("Solicitando 90 Hz por ADB autorizado…",
                "settings put system peak_refresh_rate 90 && settings put system min_refresh_rate 90")
            val peakAfter = command("Conferindo limite máximo aplicado…", "settings get system peak_refresh_rate")
            val minAfter = command("Conferindo limite mínimo aplicado…", "settings get system min_refresh_rate")
            val actual = command("Consultando taxa de atualização reportada pelo display…", "dumpsys display")
            val actual90 = Regex("""(?i)(renderFrameRate|active.*fps|refreshRate)[^\\n]{0,35}90(?:\\.0+)?""").containsMatchIn(actual)
            runOnUiThread {
                if (peakAfter == "90" && minAfter == "90") {
                    appendLog("CONFIGURAÇÕES ACEITAS: peak=$peakAfter; min=$minAfter.")
                } else {
                    appendLog("O fabricante não confirmou os dois limites: peak=$peakAfter; min=$minAfter.")
                }
                appendLog(if (actual90) "O relatório atual mostra referência a 90 Hz." else "A taxa ativa de 90 Hz ainda não foi comprovada. O sistema pode manter outro modo.")
                appendLog("IMPORTANTE: isso ajusta a tela. Não altera nem garante o limite de FPS interno do Free Fire.")
            }
        } catch (e: Exception) {
            runOnUiThread { appendLog("FALHA NO PROCESSO AUTOMÁTICO: ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    private fun applyRefreshRate() {
        val connection = adb
        if (!adbReady || connection == null || busy) {
            appendLog("ADB não está conectado. Faça o pareamento primeiro.")
            return
        }
        setBusy(true)
        thread {
            try {
                val prefs = getSharedPreferences("atomic_refresh_backup", MODE_PRIVATE)
                if (!prefs.contains("peak")) {
                    val peak = connection.shell("settings get system peak_refresh_rate").allOutput.trim()
                    val min = connection.shell("settings get system min_refresh_rate").allOutput.trim()
                    prefs.edit().putString("peak", peak).putString("min", min).apply()
                }
                val hz = selectedHz
                runOnUiThread { appendLog("Executando comandos ADB autorizados para solicitar ${hz} Hz…") }
                val result = connection.shell("settings put system peak_refresh_rate $hz && settings put system min_refresh_rate $hz")
                if (result.exitCode != 0) throw IllegalStateException(result.allOutput)
                val peakAfter = connection.shell("settings get system peak_refresh_rate").allOutput.trim()
                val minAfter = connection.shell("settings get system min_refresh_rate").allOutput.trim()
                if (peakAfter != hz.toString() || minAfter != hz.toString()) {
                    throw IllegalStateException("O sistema não confirmou os valores. peak=$peakAfter; min=$minAfter")
                }
                runOnUiThread {
                    appendLog("VALORES CONFIRMADOS PELO ANDROID: peak=$peakAfter; min=$minAfter. Isso não garante 90 FPS dentro de um jogo.")
                    modeButton.text = "RESTAURAR CONFIGURAÇÕES"
                    modeButton.setOnClickListener { restoreRefreshRate() }
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendLog("O SISTEMA RECUSOU OU NÃO CONFIRMOU A ALTERAÇÃO: ${e.message ?: e.javaClass.simpleName}")
                    setBusy(false)
                }
            }
        }
    }

    private fun restoreRefreshRate() {
        val connection = adb ?: return
        if (busy) return
        setBusy(true)
        thread {
            try {
                val prefs = getSharedPreferences("atomic_refresh_backup", MODE_PRIVATE)
                val oldPeak = prefs.getString("peak", "null") ?: "null"
                val oldMin = prefs.getString("min", "null") ?: "null"
                fun restore(key: String, value: String) {
                    require(value == "null" || value.matches(Regex("[0-9]+(\\.[0-9]+)?"))) { "Backup inválido para $key" }
                    val cmd = if (value == "null") "settings delete system $key" else "settings put system $key $value"
                    val response = connection.shell(cmd)
                    check(response.exitCode == 0) { response.allOutput }
                }
                restore("peak_refresh_rate", oldPeak)
                restore("min_refresh_rate", oldMin)
                prefs.edit().clear().apply()
                runOnUiThread {
                    appendLog("CONFIGURAÇÕES ANTERIORES RESTAURADAS: peak=$oldPeak; min=$oldMin.")
                    modeButton.text = "ATIVAR MODO"
                    modeButton.setOnClickListener { applyRefreshRate() }
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendLog("ERRO AO RESTAURAR: ${e.message}")
                    setBusy(false)
                }
            }
        }
    }

    private fun appendLog(message: String) {
        logView.text = logView.text.toString() + "\n> " + message
    }

    private fun setBusy(value: Boolean) {
        busy = value
        pairButton.isEnabled = !value
        modeButton.isEnabled = !value && adbReady
    }

    private fun updateChoices() {
        hz90.alpha = if (selectedHz == 90) 1f else .55f
        hz120.alpha = if (selectedHz == 120) 1f else .55f
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun match() = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
