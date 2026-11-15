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
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private var selectedHz = 90f
    private var enabled = false
    private lateinit var status: TextView
    private lateinit var detected: TextView
    private lateinit var toggle: Button
    private lateinit var hz90: Button
    private lateinit var hz120: Button
    private lateinit var shizukuButton: Button
    private val shizukuRequestCode = 12090
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == shizukuRequestCode) {
            runOnUiThread {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    status.text = "Atomic Access autorizado pelo Shizuku. Agora você pode tentar ativar o modo selecionado."
                    shizukuButton.text = "SHIZUKU AUTORIZADO"
                } else {
                    status.text = "Autorização do Shizuku negada. Você pode solicitar novamente."
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 15, 29)
        window.navigationBarColor = Color.rgb(5, 15, 29)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        buildUi()
        refreshDisplayInfo()
        updateShizukuStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
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
            text = "Conecte o Shizuku para autorizar operações avançadas do Android. O serviço precisa estar iniciado no próprio aparelho (por depuração sem fio ou root)."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }, match())
        shizukuButton = Button(this).apply {
            text = "CONECTAR SHIZUKU"
            isAllCaps = false
            setOnClickListener { connectShizuku() }
        }
        root.addView(shizukuButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)))
        root.addView(space(18))
        root.addView(TextView(this).apply {
            text = "O Shizuku não remove limites internos de FPS do jogo. Ele permite apenas operações que o Android autoriza ao serviço ADB/root."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF7894AD.toInt())
        }, match())
        setContentView(ScrollView(this).apply { addView(root) })
        updateButtons()
    }

    private fun connectShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (launch != null) startActivity(launch)
                else startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://shizuku.rikka.app/download/")))
                status.text = "Abra o Shizuku, inicie o serviço por Depuração sem fio e volte ao Atomic."
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                updateShizukuStatus()
            } else {
                Shizuku.requestPermission(shizukuRequestCode)
            }
        } catch (e: Exception) {
            status.text = "Não foi possível conectar ao Shizuku: ${e.message}"
        }
    }

    private fun updateShizukuStatus() {
        try {
            val active = Shizuku.pingBinder()
            val granted = active && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            shizukuButton.text = when {
                granted -> "SHIZUKU AUTORIZADO"
                active -> "AUTORIZAR ATOMIC NO SHIZUKU"
                else -> "CONECTAR SHIZUKU"
            }
            if (granted) status.text = "Shizuku conectado e autorizado."
            else if (!active) status.text = "Shizuku não está iniciado. Inicie-o por Depuração sem fio e volte aqui."
        } catch (_: Exception) {
            shizukuButton.text = "CONECTAR SHIZUKU"
        }
    }

    private fun runShizukuCommand(command: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val error = process.errorStream.bufferedReader().use { it.readText() }
                val exit = process.waitFor()
                runOnUiThread { onResult(exit == 0, (output + "\\n" + error).trim()) }
            } catch (e: Exception) {
                runOnUiThread { onResult(false, e.message ?: "Falha ao executar comando Shizuku") }
            }
        }.start()
    }

    private fun enableMode() {
        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                status.text = "Conecte e autorize o Shizuku antes de ativar o modo global."
                connectShizuku()
                return
            }
        } catch (_: Exception) {
            status.text = "Shizuku indisponível. Inicie o serviço e autorize o Atomic."
            return
        }

        val mode = window.decorView.display?.supportedModes
            ?.filter { kotlin.math.abs(it.refreshRate - selectedHz) < 2f }
            ?.maxByOrNull { it.refreshRate }

        if (mode == null) {
            enabled = false
            toggle.text = "NÃO DISPONÍVEL"
            status.text = "Este aparelho não informou um modo de ${selectedHz.toInt()} Hz compatível."
            return
        }

        val command = "settings put system peak_refresh_rate ${selectedHz.toInt()} && settings put system min_refresh_rate ${selectedHz.toInt()} && settings get system peak_refresh_rate && settings get system min_refresh_rate"
        status.text = "Aplicando ${selectedHz.toInt()} Hz pelo Shizuku..."
        runShizukuCommand(command) { ok, output ->
            if (ok) {
                val params = window.attributes
                params.preferredDisplayModeId = mode.modeId
                window.attributes = params
                enabled = true
                toggle.text = "LIGADO"
                toggle.backgroundTintList = ColorStateList.valueOf(0xFF087A58.toInt())
                status.text = "Comando aceito pelo Shizuku. Retorno: ${output.ifBlank { "sem saída" }}. Confirme a frequência real no aparelho."
                refreshDisplayInfo()
            } else {
                enabled = false
                status.text = "O Shizuku executou a tentativa, mas o sistema recusou a alteração: ${output}"
            }
        }
    }

    private fun disableMode() {
        val params = window.attributes
        params.preferredDisplayModeId = 0
        window.attributes = params
        enabled = false
        toggle.text = "DESLIGADO"
        toggle.backgroundTintList = ColorStateList.valueOf(0xFF174568.toInt())
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                runShizukuCommand("settings delete system min_refresh_rate; settings delete system peak_refresh_rate") { _, output ->
                    status.text = "Preferências globais removidas. ${output}"
                    refreshDisplayInfo()
                }
            } else {
                status.text = "Preferência local removida. Shizuku não está autorizado para restaurar os limites globais."
                refreshDisplayInfo()
            }
        } catch (_: Exception) {
            status.text = "Preferência local removida."
            refreshDisplayInfo()
        }
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
        detected.text = "FREQUÊNCIA REPORTADA\\n" +
            (current?.let { it.toInt().toString() + " Hz" } ?: "Indisponível") +
            "\\n\\nModos informados: " + (modes?.joinToString(" / ")?.plus(" Hz") ?: "não disponíveis")
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
