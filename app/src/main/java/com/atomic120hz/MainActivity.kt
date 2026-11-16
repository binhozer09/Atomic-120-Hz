package com.atomic120hz

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.res.ColorStateList
import android.graphics.Color
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private var selectedHz = 90f
    private var enabled = false
    private var adbConnected = false
    private lateinit var status: TextView
    private lateinit var detected: TextView
    private lateinit var toggle: Button
    private lateinit var hz90: Button
    private lateinit var hz120: Button
    private lateinit var pairingPort: EditText
    private lateinit var pairingCode: EditText
    private lateinit var accessButton: Button
    private lateinit var disconnectButton: Button
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 15, 29)
        window.navigationBarColor = Color.rgb(5, 15, 29)
        buildUi()
        refreshDisplayInfo()
    }

    override fun onDestroy() {
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
            text = "⚛  ATOMIC"
            textSize = 29f
            setTextColor(0xFF55C9FF.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, match())
        root.addView(TextView(this).apply {
            text = "120 Hz  •  GAME PERFORMANCE"
            textSize = 12f
            letterSpacing = .16f
            setTextColor(0xFF9ABBD5.toInt())
            gravity = Gravity.CENTER
        }, match())
        root.addView(space(26))

        detected = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(18), dp(12), dp(18))
            setBackgroundColor(0xFF102943.toInt())
        }
        root.addView(detected, match())
        root.addView(space(22))

        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        hz90 = choiceButton("90 Hz") { choose(90f) }
        hz120 = choiceButton("120 Hz") { choose(120f) }
        choices.addView(hz90, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(7) })
        choices.addView(hz120, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(7) })
        root.addView(choices, match())
        root.addView(space(16))

        toggle = Button(this).apply {
            text = "DESLIGADO"
            textSize = 17f
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(0xFF174568.toInt())
            setOnClickListener { if (enabled) disableMode() else enableMode() }
        }
        root.addView(toggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(70)))
        root.addView(space(12))

        status = TextView(this).apply {
            text = "Ative a Depuração sem fio para conectar o Atomic Access."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }
        root.addView(status, match())
        root.addView(space(26))

        root.addView(TextView(this).apply {
            text = "ATOMIC ACCESS"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xFF55C9FF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, match())
        root.addView(space(8))
        root.addView(TextView(this).apply {
            text = "Abra Opções do desenvolvedor → Depuração sem fio → Parear dispositivo com código. Digite abaixo a porta e o código exibidos nessa janela."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }, match())
        root.addView(space(14))

        pairingPort = EditText(this).apply {
            hint = "Porta de pareamento"
            inputType = InputType.TYPE_CLASS_NUMBER
            singleLine = true
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF7894AD.toInt())
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setBackgroundColor(0xFF102943.toInt())
        }
        root.addView(pairingPort, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)))
        root.addView(space(9))

        pairingCode = EditText(this).apply {
            hint = "Código de pareamento (6 dígitos)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            singleLine = true
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF7894AD.toInt())
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setBackgroundColor(0xFF102943.toInt())
        }
        root.addView(pairingCode, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)))
        root.addView(space(12))

        accessButton = Button(this).apply {
            text = "PAREAR E CONECTAR ADB"
            isAllCaps = false
            setOnClickListener { pairAndConnect() }
        }
        root.addView(accessButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))
        root.addView(space(7))

        disconnectButton = Button(this).apply {
            text = "DESCONECTAR"
            isAllCaps = false
            visibility = View.GONE
            setOnClickListener {
                AtomicAdbClient.disconnect(this@MainActivity)
                adbConnected = false
                visibility = View.GONE
                accessButton.isEnabled = true
                accessButton.text = "PAREAR E CONECTAR ADB"
                status.text = "ADB desconectado."
            }
        }
        root.addView(disconnectButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
        root.addView(space(14))
        root.addView(TextView(this).apply {
            text = "O Atomic só informa CONECTADO depois de executar um comando real e confirmar UID shell (2000). O pareamento usa o protocolo ADB sem fio do Android 11 ou superior."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF7894AD.toInt())
        }, match())

        setContentView(ScrollView(this).apply { addView(root) })
        updateButtons()
    }

    private fun pairAndConnect() {
        val port = pairingPort.text.toString().trim().toIntOrNull()
        val code = pairingCode.text.toString().trim()
        if (port == null || port !in 1..65535) {
            status.text = "Informe uma porta de pareamento válida."
            return
        }
        if (!code.matches(Regex("\\d{6}"))) {
            status.text = "O código precisa ter exatamente 6 dígitos."
            return
        }
        if (android.os.Build.VERSION.SDK_INT < 30) {
            status.text = "O pareamento ADB sem fio exige Android 11 ou superior."
            return
        }

        accessButton.isEnabled = false
        accessButton.text = "PAREANDO..."
        status.text = "Etapa 1/3: realizando o pareamento ADB real..."
        worker.execute {
            try {
                val result = AtomicAdbClient.pairConnectAndVerify(this, port, code)
                runOnUiThread {
                    adbConnected = true
                    accessButton.isEnabled = true
                    accessButton.text = "CONECTADO"
                    disconnectButton.visibility = View.VISIBLE
                    pairingCode.text.clear()
                    status.text = "CONECTADO • ADB confirmou UID shell (2000).\n$result"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    adbConnected = false
                    accessButton.isEnabled = true
                    accessButton.text = "TENTAR NOVAMENTE"
                    disconnectButton.visibility = View.GONE
                    status.text = "FALHA NO ADB: ${e.message ?: "erro desconhecido"}"
                }
            }
        }
    }

    private fun enableMode() {
        if (!adbConnected) {
            status.text = "Atomic Access ainda não está conectado. Faça o pareamento ADB primeiro."
            return
        }
        val mode = window.decorView.display?.supportedModes
            ?.filter { kotlin.math.abs(it.refreshRate - selectedHz) < 2f }
            ?.maxByOrNull { it.refreshRate }
        if (mode == null) {
            status.text = "Este aparelho não informou um modo de ${selectedHz.toInt()} Hz compatível."
            return
        }

        toggle.isEnabled = false
        status.text = "Solicitando ${selectedHz.toInt()} Hz pelo ADB..."
        worker.execute {
            try {
                AtomicAdbClient.execute(this, "settings put system peak_refresh_rate ${selectedHz.toInt()}")
                AtomicAdbClient.execute(this, "settings put system min_refresh_rate ${selectedHz.toInt()}")
                val peak = AtomicAdbClient.execute(this, "settings get system peak_refresh_rate")
                val min = AtomicAdbClient.execute(this, "settings get system min_refresh_rate")
                runOnUiThread {
                    val params = window.attributes
                    params.preferredDisplayModeId = mode.modeId
                    window.attributes = params
                    enabled = true
                    toggle.isEnabled = true
                    toggle.text = "LIGADO"
                    toggle.backgroundTintList = ColorStateList.valueOf(0xFF087A58.toInt())
                    status.text = "Comandos ADB executados. Limites lidos: mínimo $min Hz / máximo $peak Hz. Isso não garante FPS do jogo."
                    refreshDisplayInfo()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    toggle.isEnabled = true
                    status.text = "Falha ao aplicar pelo ADB: ${e.message}"
                }
            }
        }
    }

    private fun disableMode() {
        toggle.isEnabled = false
        worker.execute {
            try {
                if (adbConnected) {
                    AtomicAdbClient.execute(this, "settings delete system min_refresh_rate")
                    AtomicAdbClient.execute(this, "settings delete system peak_refresh_rate")
                }
                runOnUiThread {
                    val params = window.attributes
                    params.preferredDisplayModeId = 0
                    window.attributes = params
                    enabled = false
                    toggle.isEnabled = true
                    toggle.text = "DESLIGADO"
                    toggle.backgroundTintList = ColorStateList.valueOf(0xFF174568.toInt())
                    status.text = "Preferências de frequência removidas."
                    refreshDisplayInfo()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    toggle.isEnabled = true
                    status.text = "Falha ao restaurar as preferências: ${e.message}"
                }
            }
        }
    }

    private fun choose(hz: Float) {
        selectedHz = hz
        updateButtons()
        status.text = "Selecionado: ${hz.toInt()} Hz. Atomic Access precisa estar conectado para aplicar."
    }

    private fun refreshDisplayInfo() {
        val display = window.decorView.display
        val modes = display?.supportedModes?.map { it.refreshRate.toInt().toString() }?.distinct()
        val current = display?.refreshRate
        detected.text = "FREQUÊNCIA REPORTADA\n" +
            (current?.let { "${it.toInt()} Hz" } ?: "Indisponível") +
            "\n\nModos informados: " + (modes?.joinToString(" / ")?.plus(" Hz") ?: "não disponíveis")
    }

    private fun updateButtons() {
        hz90.alpha = if (selectedHz == 90f) 1f else .55f
        hz120.alpha = if (selectedHz == 120f) 1f else .55f
    }

    private fun choiceButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun match() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
