package com.atomic120hz

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.res.ColorStateList
import android.graphics.Color

class MainActivity : Activity() {
    private var selectedHz = 90f
    private var enabled = false
    private lateinit var status: TextView
    private lateinit var detected: TextView
    private lateinit var toggle: Button
    private lateinit var hz90: Button
    private lateinit var hz120: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 15, 29)
        window.navigationBarColor = Color.rgb(5, 15, 29)
        buildUi()
        refreshDisplayInfo()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(28), dp(22), dp(20))
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
        root.addView(space(28))
        detected = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(18), dp(12), dp(18))
            setBackgroundColor(0xFF102943.toInt())
        }
        root.addView(detected, match())
        root.addView(space(24))
        val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        hz90 = choiceButton("90 Hz") { choose(90f) }
        hz120 = choiceButton("120 Hz") { choose(120f) }
        choices.addView(hz90, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(7) })
        choices.addView(hz120, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(7) })
        root.addView(choices, match())
        root.addView(space(18))
        toggle = Button(this).apply {
            text = "DESLIGADO"
            textSize = 17f
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(0xFF174568.toInt())
            setOnClickListener { if (enabled) disableMode() else enableMode() }
        }
        root.addView(toggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(76)))
        root.addView(space(14))
        status = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }
        root.addView(status, match())
        root.addView(space(22))
        root.addView(TextView(this).apply {
            text = "ATOMIC ACCESS"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(0xFF55C9FF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, match())
        root.addView(space(8))
        root.addView(TextView(this).apply {
            text = "O Android bloqueia aplicativos comuns de alterar globalmente os limites de Hz em alguns aparelhos. O Atomic aplica o modo compatível somente à própria tela; não precisa pedir uma autorização que não resolveria esse bloqueio."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }, match())
        root.addView(space(18))
        root.addView(TextView(this).apply {
            text = "Importante: a frequência da tela não desbloqueia o limite interno de FPS de jogos como Free Fire."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF7894AD.toInt())
        }, match())
        setContentView(ScrollView(this).apply { addView(root) })
        updateButtons()
    }

    private fun enableMode() {
        val mode = window.decorView.display?.supportedModes
            ?.filter { kotlin.math.abs(it.refreshRate - selectedHz) < 2f }
            ?.maxByOrNull { it.refreshRate }

        if (mode == null) {
            enabled = false
            toggle.text = "NÃO DISPONÍVEL"
            status.text = "Este aparelho não informou um modo de ${selectedHz.toInt()} Hz compatível."
            return
        }

        val params = window.attributes
        params.preferredDisplayModeId = mode.modeId
        window.attributes = params
        enabled = true
        toggle.text = "LIGADO"
        toggle.backgroundTintList = ColorStateList.valueOf(0xFF087A58.toInt())
        status.text = "Solicitado ${mode.refreshRate.toInt()} Hz para a tela do Atomic. Isso não altera a frequência de outros aplicativos."
        refreshDisplayInfo()
    }

    private fun disableMode() {
        val params = window.attributes
        params.preferredDisplayModeId = 0
        window.attributes = params
        enabled = false
        toggle.text = "DESLIGADO"
        toggle.backgroundTintList = ColorStateList.valueOf(0xFF174568.toInt())
        status.text = "Preferência de frequência removida da tela do Atomic."
        refreshDisplayInfo()
    }

    private fun choose(hz: Float) {
        selectedHz = hz
        updateButtons()
        status.text = "Selecionado: ${hz.toInt()} Hz. Toque em LIGAR para solicitar esse modo à tela do Atomic."
    }

    private fun refreshDisplayInfo() {
        val d = window.decorView.display
        val modes = d?.supportedModes?.map { it.refreshRate.toInt().toString() }?.distinct()
        val current = d?.refreshRate
        detected.text = "FREQUÊNCIA REPORTADA\n" +
            (current?.let { it.toInt().toString() + " Hz" } ?: "Indisponível") +
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
