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
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var selectedHz = 90
    private var adbReady = false
    private var busy = false
    private var adb: Kadb? = null
    private lateinit var hostInput: EditText
    private lateinit var pairPortInput: EditText
    private lateinit var codeInput: EditText
    private lateinit var connectPortInput: EditText
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
        root.addView(label("PAREAMENTO ADB SEM FIO", 16f, Color.WHITE, true), match())
        root.addView(label("Ative Opções do desenvolvedor e Depuração sem fio. Na tela “Parear dispositivo com código”, use os dados exibidos abaixo.", 13f, 0xFF9ABBD5.toInt()), match())
        hostInput = field("IP do próprio aparelho (normalmente 127.0.0.1)", "127.0.0.1", InputType.TYPE_CLASS_TEXT)
        pairPortInput = field("Porta de pareamento", "", InputType.TYPE_CLASS_NUMBER)
        codeInput = field("Código de pareamento (6 dígitos)", "", InputType.TYPE_CLASS_NUMBER)
        connectPortInput = field("Porta de conexão ADB", "", InputType.TYPE_CLASS_NUMBER)
        root.addView(hostInput, match())
        root.addView(pairPortInput, match())
        root.addView(codeInput, match())
        root.addView(connectPortInput, match())
        root.addView(space(8))
        pairButton = Button(this).apply {
            text = "PAREAR E CONECTAR ATOMIC"
            isAllCaps = false
            setOnClickListener { pairAndConnect() }
        }
        root.addView(pairButton, LinearLayout.LayoutParams(-1, dp(56)))
        root.addView(space(18))
        root.addView(label("FREQUÊNCIA DESEJADA", 14f, 0xFF55C9FF.toInt(), true), match())
        val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hz90 = Button(this).apply { text = "90 Hz"; isAllCaps = false; setOnClickListener { selectedHz = 90; updateChoices() } }
        hz120 = Button(this).apply { text = "120 Hz"; isAllCaps = false; setOnClickListener { selectedHz = 120; updateChoices() } }
        choices.addView(hz90, LinearLayout.LayoutParams(0, dp(50), 1f))
        choices.addView(hz120, LinearLayout.LayoutParams(0, dp(50), 1f))
        root.addView(choices, match())
        modeButton = Button(this).apply {
            text = "LIBERAR ACESSO"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { applyRefreshRate() }
        }
        root.addView(modeButton, LinearLayout.LayoutParams(-1, dp(64)))
        root.addView(space(12))
        root.addView(label("TERMINAL DE EXECUÇÃO", 14f, 0xFF55C9FF.toInt(), true), match())
        logView = TextView(this).apply {
            text = "Aguardando pareamento. Nenhum comando foi executado ainda."
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
        val host = hostInput.text.toString().trim()
        val pairPort = pairPortInput.text.toString().toIntOrNull()
        val code = codeInput.text.toString().trim()
        val connectPort = connectPortInput.text.toString().toIntOrNull()
        if (host.isBlank() || pairPort !in 1..65535 || connectPort !in 1..65535 || !code.matches(Regex("\\d{6}"))) {
            appendLog("ERRO: informe IP, porta de pareamento, código de 6 dígitos e porta de conexão.")
            return
        }
        setBusy(true)
        appendLog("Iniciando pareamento TLS ADB…")
        thread {
            try {
                runBlocking { Kadb.pair(host, pairPort!!, code) }
                runOnUiThread { appendLog("Pareamento concluído. Abrindo conexão ADB…") }
                val connection = Kadb.create(host, connectPort!!)
                val check = connection.shell("echo ATOMIC_ADB_OK")
                if (check.exitCode != 0 || !check.allOutput.contains("ATOMIC_ADB_OK")) {
                    connection.close()
                    throw IllegalStateException("A conexão ADB não confirmou o teste de shell: ${check.allOutput}")
                }
                adb?.close()
                adb = connection
                adbReady = true
                runOnUiThread {
                    appendLog("SUCESSO REAL: conexão ADB autorizada e comando de teste confirmado. UID shell ativo.")
                    modeButton.isEnabled = true
                    pairButton.text = "ADB CONECTADO"
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    adbReady = false
                    modeButton.isEnabled = false
                    appendLog("FALHA REAL NO PAREAMENTO/CONEXÃO: ${e.message ?: e.javaClass.simpleName}")
                    setBusy(false)
                }
            }
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
                runOnUiThread { appendLog("Aplicando solicitação de ${hz} Hz pelo ADB autorizado…") }
                val result = connection.shell("settings put system peak_refresh_rate $hz && settings put system min_refresh_rate $hz")
                if (result.exitCode != 0) throw IllegalStateException(result.allOutput)
                val peakAfter = connection.shell("settings get system peak_refresh_rate").allOutput.trim()
                val minAfter = connection.shell("settings get system min_refresh_rate").allOutput.trim()
                if (peakAfter != hz.toString() || minAfter != hz.toString()) {
                    throw IllegalStateException("O sistema não confirmou os valores. peak=$peakAfter; min=$minAfter")
                }
                runOnUiThread {
                    appendLog("COMANDO CONFIRMADO: peak_refresh_rate=$peakAfter; min_refresh_rate=$minAfter. Isso confirma a gravação, não garante FPS do jogo.")
                    modeButton.text = "RESTAURAR CONFIGURAÇÕES"
                    modeButton.setOnClickListener { restoreRefreshRate() }
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendLog("SISTEMA RECUSOU OU NÃO CONFIRMOU: ${e.message ?: e.javaClass.simpleName}")
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
                    appendLog("Configurações anteriores restauradas: peak=$oldPeak; min=$oldMin.")
                    modeButton.text = "LIBERAR ACESSO"
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

    private fun field(hintText: String, value: String, type: Int) = EditText(this).apply {
        hint = hintText
        setText(value)
        inputType = type
        textSize = 14f
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF7894AD.toInt())
        setPadding(dp(12), dp(8), dp(12), dp(8))
        backgroundTintList = ColorStateList.valueOf(0xFF55C9FF.toInt())
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
