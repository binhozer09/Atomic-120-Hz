package com.atomic120hz

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.content.res.ColorStateList
import android.graphics.Color
import kotlin.math.abs

class MainActivity : Activity() {
    private var selectedHz = 90f
    private var enabled = false
    private lateinit var status: TextView
    private lateinit var detected: TextView
    private lateinit var toggle: Button
    private lateinit var hz90: Button
    private lateinit var hz120: Button
    private lateinit var accessStatus: TextView
    private lateinit var accessButton: Button
    private val prefs by lazy { getSharedPreferences("atomic_access", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 15, 29)
        window.navigationBarColor = Color.rgb(5, 15, 29)
        buildUi()
        refreshDisplayInfo()
        refreshAtomicAccess()
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
        accessStatus = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
            text = "Verificando serviço de autorização…"
        }
        root.addView(accessStatus, match())
        root.addView(space(10))
        accessButton = Button(this).apply {
            text = "ATIVAR ATOMIC ACCESS"
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(0xFF155A88.toInt())
            setOnClickListener { activateAtomicAccess() }
        }
        root.addView(accessButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)))
        root.addView(space(18))
        root.addView(TextView(this).apply {
            text = "Hz da tela não garante FPS igual dentro do jogo. O Android e o fabricante podem limitar a frequência solicitada."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF7894AD.toInt())
        }, match())
        setContentView(ScrollView(this).apply { addView(root) })
        updateButtons()
    }

    private fun activateAtomicAccess() {
        if (Settings.System.canWrite(this)) {
            refreshAtomicAccess()
            return
        }
        accessStatus.text = "Autorize o Atomic a modificar configurações do sistema na próxima tela."
        startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun refreshAtomicAccess() {
        if (!::accessStatus.isInitialized) return
        val granted = Settings.System.canWrite(this)
        accessStatus.text = if (granted) "Acesso especial concedido • controle de taxa de atualização disponível." else "Aguardando autorização especial do Android."
        accessButton.text = if (granted) "ATOMIC ACCESS LIBERADO" else "LIBERAR ATOMIC ACCESS"
    }

    override fun onResume() {
        super.onResume()
        if (::accessStatus.isInitialized) refreshAtomicAccess()
    }

    private fun enableMode() {
        if (!Settings.System.canWrite(this)) {
            status.text = "Primeiro libere o Atomic Access abaixo."
            activateAtomicAccess()
            return
        }
        val resolver = contentResolver
        if (!prefs.getBoolean("saved_original", false)) {
            prefs.edit()
                .putString("old_peak", Settings.System.getString(resolver, "peak_refresh_rate"))
                .putString("old_min", Settings.System.getString(resolver, "min_refresh_rate"))
                .putBoolean("saved_original", true).apply()
        }
        val target = selectedHz.toString()
        Settings.System.putString(resolver, "peak_refresh_rate", target)
        Settings.System.putString(resolver, "min_refresh_rate", target)
        val actualPeak = Settings.System.getString(resolver, "peak_refresh_rate")
        val actualMin = Settings.System.getString(resolver, "min_refresh_rate")
        val verified = actualPeak == target && actualMin == target
        enabled = verified
        toggle.text = if (verified) "LIGADO" else "NÃO CONFIRMADO"
        toggle.backgroundTintList = ColorStateList.valueOf(if (verified) 0xFF087A58.toInt() else 0xFF8A4A20.toInt())
        status.text = if (verified) "Sistema aceitou $target Hz. Isso não comprova FPS do Free Fire." else "O Android/fabricante não confirmou: peak=$actualPeak • min=$actualMin."
        refreshDisplayInfo()
    }

    private fun disableMode() {
        if (!Settings.System.canWrite(this)) {
            status.text = "Sem Atomic Access: não foi possível restaurar as configurações."
            activateAtomicAccess()
            return
        }
        val oldP = prefs.getString("old_peak", null)
        val oldM = prefs.getString("old_min", null)
        Settings.System.putString(contentResolver, "peak_refresh_rate", oldP)
        Settings.System.putString(contentResolver, "min_refresh_rate", oldM)
        prefs.edit().remove("old_peak").remove("old_min").putBoolean("saved_original", false).apply()
        enabled = false
        toggle.text = "DESLIGADO"
        toggle.backgroundTintList = ColorStateList.valueOf(0xFF174568.toInt())
        status.text = "Configurações anteriores restauradas quando suportadas pelo fabricante."
        refreshDisplayInfo()
    }

    private fun choose(hz: Float) {
        selectedHz = hz
        updateButtons()
        status.text = "Selecionado: " + hz.toInt() + " Hz. Toque em LIGAR para solicitar ao Android."
    }

    private fun enableMode() {
        val mode = findMode(selectedHz)
        if (mode == null) {
            status.text = "Este aparelho não informou um modo de " + selectedHz.toInt() + " Hz. Nada foi alterado."
            return
        }
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = mode.modeId
            preferredRefreshRate = selectedHz
        }
        enabled = true
        toggle.text = "LIGADO"
        toggle.backgroundTintList = ColorStateList.valueOf(0xFF087A58.toInt())
        status.text = "Solicitação enviada. Verificando a resposta do sistema…"
        window.decorView.postDelayed({ verifyApplied() }, 1200)
    }

    private fun verifyApplied() {
        val actual = window.decorView.display?.refreshRate
        if (actual != null && abs(actual - selectedHz) < 2f) {
            status.text = "Confirmado: o Android reporta " + actual.toInt() + " Hz nesta tela."
        } else {
            status.text = "Não confirmado. O sistema reporta " + (actual?.toInt()?.toString() ?: "valor indisponível") + " Hz."
        }
        refreshDisplayInfo()
    }

    private fun disableMode() {
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = 0
            preferredRefreshRate = 0f
        }
        enabled = false
        toggle.text = "DESLIGADO"
        toggle.backgroundTintList = ColorStateList.valueOf(0xFF174568.toInt())
        status.text = "Solicitação removida. O Android voltou a escolher a frequência desta janela."
        refreshDisplayInfo()
    }

    private fun findMode(hz: Float): android.view.Display.Mode? {
        return window.decorView.display?.supportedModes
            ?.filter { abs(it.refreshRate - hz) < 2f }
            ?.maxByOrNull { it.refreshRate }
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
