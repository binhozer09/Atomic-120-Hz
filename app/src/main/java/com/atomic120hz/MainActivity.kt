package com.atomic120hz

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.res.ColorStateList
import java.util.concurrent.Executors
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private var selectedHz = 90
    private var enabled = false
    private lateinit var status: TextView
    private lateinit var detected: TextView
    private lateinit var applyButton: Button
    private lateinit var hz90: Button
    private lateinit var hz120: Button
    private lateinit var shizukuButton: Button
    private val worker = Executors.newSingleThreadExecutor()
    private val requestCode = 12090

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == requestCode) runOnUiThread {
            if (result == PackageManager.PERMISSION_GRANTED) {
                status.text = "Shizuku autorizado. Agora é só aplicar a frequência."
                shizukuButton.text = "SHIZUKU AUTORIZADO"
                applyButton.isEnabled = true
            } else {
                status.text = "A autorização do Atomic foi recusada no Shizuku."
                shizukuButton.text = "AUTORIZAR SHIZUKU"
            }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { refreshShizukuState() }
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread {
            applyButton.isEnabled = false
            status.text = "Shizuku parou. Abra o Shizuku e inicie o serviço novamente."
            shizukuButton.text = "ABRIR SHIZUKU"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 15, 29)
        window.navigationBarColor = Color.rgb(5, 15, 29)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        buildUi()
        refreshDisplayInfo()
        refreshShizukuState()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshShizukuState()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
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
        root.addView(space(20))

        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        hz90 = choiceButton("90 Hz") { choose(90) }
        hz120 = choiceButton("120 Hz") { choose(120) }
        choices.addView(hz90, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(7) })
        choices.addView(hz120, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(7) })
        root.addView(choices, match())
        root.addView(space(16))

        applyButton = Button(this).apply {
            text = "APLICAR " + selectedHz + " Hz"
            textSize = 17f
            isAllCaps = false
            isEnabled = false
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(0xFF174568.toInt())
            setOnClickListener { if (enabled) disableMode() else enableMode() }
        }
        root.addView(applyButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(68)))
        root.addView(space(12))

        status = TextView(this).apply {
            text = "Conecte e autorize o Shizuku para liberar a aplicação."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }
        root.addView(status, match())
        root.addView(space(22))

        shizukuButton = Button(this).apply {
            text = "VERIFICAR SHIZUKU"
            isAllCaps = false
            setOnClickListener { connectShizuku() }
        }
        root.addView(shizukuButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)))
        root.addView(space(8))
        val installButton = Button(this).apply {
            text = "BAIXAR SHIZUKU OFICIAL"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            }
        }
        root.addView(installButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)))
        root.addView(space(14))
        root.addView(TextView(this).apply {
            text = "Primeira utilização: instale o Shizuku, inicie o serviço pela Depuração sem fio e autorize o Atomic. Depois, basta tocar em APLICAR. Após reiniciar o celular, inicie o Shizuku novamente."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF7894AD.toInt())
        }, match())
        setContentView(ScrollView(this).apply { addView(root) })
        updateButtons()
    }

    private fun connectShizuku() {
        if (!Shizuku.pingBinder()) {
            status.text = "Shizuku não está iniciado. Abra o Shizuku e toque em Iniciar."
            try {
                val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (launch != null) startActivity(launch)
                else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            } catch (_: Exception) { }
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            refreshShizukuState()
            return
        }
        status.text = "Confirme a autorização do Atomic na janela do Shizuku."
        Shizuku.requestPermission(requestCode)
    }

    private fun refreshShizukuState() {
        val running = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        val granted = running && try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false }
        shizukuButton.text = when {
            granted -> "SHIZUKU AUTORIZADO"
            running -> "AUTORIZAR SHIZUKU"
            else -> "ABRIR / INICIAR SHIZUKU"
        }
        applyButton.isEnabled = granted
        if (!running) status.text = "Shizuku não está iniciado. Abra o Shizuku e inicie o serviço."
        else if (!granted) status.text = "Shizuku está ativo. Toque em AUTORIZAR SHIZUKU."
        else status.text = "Shizuku conectado e Atomic autorizado. Pronto para aplicar."
    }

    private fun shell(command: String): String {
        check(Shizuku.pingBinder()) { "O serviço Shizuku não está ativo." }
        check(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { "O Atomic ainda não foi autorizado no Shizuku." }
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as? Process
            ?: error("Shizuku não conseguiu iniciar o processo shell.")
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val errorOutput = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return (output + if (errorOutput.isBlank()) "" else "\n" + errorOutput).trim() +
            "\n[exit=$exitCode]"
    }

    private fun enableMode() {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            refreshShizukuState()
            return
        }
        val mode = window.decorView.display?.supportedModes
            ?.filter { kotlin.math.abs(it.refreshRate - selectedHz) < 1f }
            ?.maxByOrNull { it.physicalWidth.toLong() * it.physicalHeight.toLong() }
        if (mode == null) {
            status.text = "Este aparelho não informou um modo de $selectedHz Hz."
            return
        }
        applyButton.isEnabled = false
        status.text = "Aplicando comandos pelo Shizuku..."
        worker.execute {
            try {
                val prefs = getSharedPreferences("atomic_display_mode_backup", MODE_PRIVATE)
                if (!prefs.getBoolean("saved", false)) {
                    val keys = listOf("secure:user_refresh_rate", "secure:miui_refresh_rate",
                        "system:peak_refresh_rate", "system:min_refresh_rate")
                    val edit = prefs.edit()
                    keys.forEach { entry ->
                        val parts = entry.split(":")
                        val old = shell("settings get ${parts[0]} ${parts[1]}").substringBefore("\n[exit=").trim()
                        edit.putString(entry, if (old.isBlank() || old == "null") "__NULL__" else old)
                    }
                    val previous = shell("cmd display get-user-preferred-display-mode 0").substringBefore("\n[exit=").trim()
                    edit.putString("previous_mode", previous.substringAfter(":", "").trim().ifBlank { "__NULL__" })
                    val oldOverlay = shell("device_config get game_overlay com.dts.freefireth").substringBefore("\n[exit=").trim()
                    edit.putString("previous_game_overlay", if (oldOverlay.isBlank() || oldOverlay == "null") "__NULL__" else oldOverlay)
                    edit.putBoolean("saved", true).apply()
                }

                val commands = listOf(
                    "settings put secure user_refresh_rate $selectedHz",
                    "settings put secure miui_refresh_rate $selectedHz",
                    "settings put system peak_refresh_rate ${selectedHz.toFloat()}",
                    "settings put system min_refresh_rate ${selectedHz.toFloat()}",
                    "cmd display set-user-preferred-display-mode ${mode.physicalWidth} ${mode.physicalHeight} ${mode.refreshRate} 0",
                    "device_config put game_overlay com.dts.freefireth mode=2,fps=90:mode=3,fps=90",
                    "cmd game mode performance com.dts.freefireth"
                )
                val results = commands.map { cmd -> "$cmd => ${shell(cmd)}" }
                val userRefresh = shell("settings get secure user_refresh_rate").substringBefore("\n[exit=").trim()
                val miuiRefresh = shell("settings get secure miui_refresh_rate").substringBefore("\n[exit=").trim()
                val preferred = shell("cmd display get-user-preferred-display-mode 0")
                val displayDump = shell("dumpsys display | grep -i -E 'mActiveSfDisplayMode|mActiveRenderFrameRate|mDesiredDisplayModeSpecs|PRIORITY_MIUI_REFRESH_RATE'; dumpsys game")
                val ok = userRefresh.toFloatOrNull()?.let { kotlin.math.abs(it - selectedHz) < 1f } == true &&
                    miuiRefresh.toFloatOrNull()?.let { kotlin.math.abs(it - selectedHz) < 1f } == true &&
                    !preferred.contains("Exception", true) && !preferred.contains("error", true)
                runOnUiThread {
                    applyButton.isEnabled = true
                    enabled = ok
                    applyButton.text = if (ok) "RESTAURAR MODO ANTERIOR" else "TENTAR APLICAR $selectedHz Hz"
                    applyButton.backgroundTintList = ColorStateList.valueOf(if (ok) 0xFF087A58.toInt() else 0xFF174568.toInt())
                    status.text = (if (ok) "COMANDOS EXECUTADOS E CONFIGURAÇÕES CONFIRMADAS." else "Os comandos foram enviados, mas o Android não confirmou todas as configurações.") +
                        "\n\n" + results.joinToString("\n") +
                        "\n\nLeitura secure user=$userRefresh / miui=$miuiRefresh\nModo preferido: $preferred\nDiagnóstico: $displayDump" +
                        "\n\nA frequência real dentro do jogo depende também do suporte e da política do próprio Free Fire/HyperOS."
                    refreshDisplayInfo()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    applyButton.isEnabled = true
                    status.text = "FALHA NO SHIZUKU: ${e.message ?: "erro desconhecido"}"
                }
            }
        }
    }

    private fun disableMode() {
        applyButton.isEnabled = false
        status.text = "Restaurando as configurações anteriores..."
        worker.execute {
            try {
                val prefs = getSharedPreferences("atomic_display_mode_backup", MODE_PRIVATE)
                if (!prefs.getBoolean("saved", false)) error("Não há configurações anteriores salvas.")
                val restored = mutableListOf<String>()
                listOf("secure:user_refresh_rate", "secure:miui_refresh_rate",
                    "system:peak_refresh_rate", "system:min_refresh_rate").forEach { entry ->
                    val parts = entry.split(":")
                    val old = prefs.getString(entry, "__NULL__") ?: "__NULL__"
                    val cmd = if (old == "__NULL__") "settings delete ${parts[0]} ${parts[1]}"
                        else "settings put ${parts[0]} ${parts[1]} $old"
                    restored.add("$cmd => ${shell(cmd)}")
                }
                val oldMode = prefs.getString("previous_mode", "__NULL__") ?: "__NULL__"
                val modeCmd = if (oldMode == "__NULL__") "cmd display clear-user-preferred-display-mode 0"
                    else "cmd display set-user-preferred-display-mode $oldMode"
                restored.add("$modeCmd => ${shell(modeCmd)}")
                val oldOverlay = prefs.getString("previous_game_overlay", "__NULL__") ?: "__NULL__"
                val overlayCmd = if (oldOverlay == "__NULL__") "device_config delete game_overlay com.dts.freefireth"
                    else "device_config put game_overlay com.dts.freefireth $oldOverlay"
                restored.add("$overlayCmd => ${shell(overlayCmd)}")
                restored.add(shell("cmd game mode standard com.dts.freefireth"))
                prefs.edit().clear().apply()
                runOnUiThread {
                    enabled = false
                    applyButton.isEnabled = true
                    applyButton.text = "APLICAR $selectedHz Hz"
                    applyButton.backgroundTintList = ColorStateList.valueOf(0xFF174568.toInt())
                    status.text = "Configurações anteriores restauradas.\n" + restored.joinToString("\n")
                    refreshDisplayInfo()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    applyButton.isEnabled = true
                    status.text = "FALHA AO RESTAURAR: ${e.message ?: "erro desconhecido"}"
                }
            }
        }
    }

    private fun choose(hz: Int) {
        if (enabled) {
            status.text = "Restaure o modo atual antes de escolher outra frequência."
            return
        }
        selectedHz = hz
        applyButton.text = "APLICAR $selectedHz Hz"
        updateButtons()
    }

    private fun refreshDisplayInfo() {
        val display = window.decorView.display
        val modes = display?.supportedModes?.map { it.refreshRate.toInt().toString() }?.distinct()
        detected.text = "FREQUÊNCIA REPORTADA\n${display?.refreshRate?.toInt()?.let { "$it Hz" } ?: "Indisponível"}" +
            "\n\nModos informados: " + (modes?.joinToString(" / ")?.plus(" Hz") ?: "não disponíveis")
    }

    private fun updateButtons() {
        hz90.alpha = if (selectedHz == 90) 1f else .55f
        hz120.alpha = if (selectedHz == 120) 1f else .55f
    }

    private fun choiceButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun match() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
