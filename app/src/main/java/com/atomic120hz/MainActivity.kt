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
import android.net.Uri
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
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
    private val shizukuRequestCode = 12090
    private val binderReceivedListener = Runnable { runOnUiThread { refreshAtomicAccess() } }
    private val binderDeadListener = Runnable { runOnUiThread { refreshAtomicAccess() } }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == shizukuRequestCode) runOnUiThread {
            accessStatus.text = if (grantResult == PackageManager.PERMISSION_GRANTED) "Atomic Access autorizado. Ponte privilegiada conectada." else "Autorização negada. Você pode autorizar novamente."
            refreshAtomicAccess()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 15, 29)
        window.navigationBarColor = Color.rgb(5, 15, 29)
        buildUi()
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
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
        if (!Shizuku.pingBinder()) {
            accessStatus.text = "Serviço privilegiado não iniciado. Ative a Depuração sem fio e inicie o serviço Shizuku/Sui. O Atomic não consegue obter esse privilégio sozinho."
            try {
                startActivity(Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"))
            } catch (_: Exception) {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            }
            return
        }
        if (Shizuku.isPreV11()) {
            accessStatus.text = "Versão do serviço incompatível. Atualize o Shizuku/Sui."
            return
        }
        when {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                accessStatus.text = "Atomic Access autorizado. UID de serviço: " + Shizuku.getUid() + "."
            }
            Shizuku.shouldShowRequestPermissionRationale() -> {
                accessStatus.text = "Permissão necessária. Autorize o Atomic na solicitação do sistema."
                Shizuku.requestPermission(shizukuRequestCode)
            }
            else -> {
                accessStatus.text = "Solicitando autorização segura…"
                Shizuku.requestPermission(shizukuRequestCode)
            }
        }
    }

    private fun refreshAtomicAccess() {
        if (!::accessStatus.isInitialized) return
        if (!Shizuku.pingBinder()) {
            accessStatus.text = "Desconectado: serviço privilegiado não está iniciado."
            accessButton.text = "CONFIGURAR ATOMIC ACCESS"
            return
        }
        val granted = try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false }
        accessStatus.text = if (granted) {
            "Conectado e autorizado • UID " + Shizuku.getUid()
        } else {
            "Serviço encontrado, aguardando autorização do usuário."
        }
        accessButton.text = if (granted) "ATOMIC ACCESS AUTORIZADO" else "AUTORIZAR ATOMIC ACCESS"
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        super.onDestroy()
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
