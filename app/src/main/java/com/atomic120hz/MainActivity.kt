package com.atomic120hz

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val permissionRequest = 12090
    private val apiUrl = "https://atomic-120-hz-ai.vercel.app/api/analyze"
    private lateinit var status: TextView
    private lateinit var diagnosis: TextView
    private lateinit var shizukuButton: Button
    private lateinit var analyzeButton: Button
    private lateinit var executeButton: Button
    private var commands = listOf<String>()
    private var busy = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, grant ->
        if (code == permissionRequest) runOnUiThread {
            status.text = if (grant == PackageManager.PERMISSION_GRANTED) "Shizuku autorizado. Atomic já pode enviar comandos." else "Permissão recusada no Shizuku."
            refreshShizuku()
        }
    }
    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { refreshShizuku() } }
    private val deadListener = Shizuku.OnBinderDeadListener { runOnUiThread { refreshShizuku() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 15, 29)
        window.navigationBarColor = Color.rgb(5, 15, 29)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        buildScreen()
        refreshDevice()
        refreshShizuku()
    }
    override fun onResume() { super.onResume(); if (::status.isInitialized) refreshShizuku() }
    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        worker.shutdown()
        super.onDestroy()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(26), dp(20), dp(24)); setBackgroundColor(Color.rgb(5, 15, 29))
        }
        root.addView(label("⚛  ATOMIC", 29, 0xFF55C9FF.toInt(), true), match())
        root.addView(label("120 Hz  •  CHATGPT + SHIZUKU", 12, 0xFF9ABBD5.toInt()), match())
        root.addView(space(18))
        root.addView(panel("O ChatGPT analisa o celular e escolhe os comandos. O Atomic envia os comandos ao Shizuku, que executa pelo ADB autorizado."), match())
        root.addView(space(14))
        shizukuButton = button("1. CONECTAR / AUTORIZAR SHIZUKU") { connectShizuku() }
        root.addView(shizukuButton, buttonParams())
        root.addView(space(7))
        root.addView(button("ABRIR OU BAIXAR SHIZUKU") { openShizuku() }, buttonParams())
        root.addView(space(14))
        analyzeButton = button("2. ANALISAR COM CHATGPT") { analyzeDevice() }
        root.addView(analyzeButton, buttonParams(58))
        root.addView(space(7))
        executeButton = button("3. ENVIAR COMANDOS AO SHIZUKU") { executeCommands() }
        executeButton.isEnabled = false
        root.addView(executeButton, buttonParams(58))
        root.addView(space(12))
        status = label("Verificando conexão...", 13, 0xFF9ABBD5.toInt())
        root.addView(status, match())
        root.addView(space(10))
        diagnosis = panel("O diagnóstico e os comandos escolhidos pela IA aparecerão aqui.")
        root.addView(diagnosis, match())
        root.addView(space(12))
        root.addView(label("Primeiro: instale o Shizuku, inicie o serviço pela Depuração sem fio e autorize o Atomic. Após reiniciar o celular, será preciso iniciar o Shizuku novamente. Confira os comandos antes de executar. O método não garante 90 FPS se o jogo ou o HyperOS bloquear a taxa.", 11, 0xFF7894AD.toInt()), match())
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun connectShizuku() {
        if (!Shizuku.pingBinder()) {
            status.text = "Shizuku não está iniciado. Abra o app Shizuku e inicie o serviço pela Depuração sem fio."
            openShizuku()
            return
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) refreshShizuku()
            else { status.text = "Aceite a permissão do Atomic na janela do Shizuku."; Shizuku.requestPermission(permissionRequest) }
        } catch (e: Exception) { status.text = "Erro ao verificar Shizuku: ${e.message}" }
    }
    private fun openShizuku() {
        val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (launch != null) startActivity(launch)
        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
    }
    private fun hasPermission() = try { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false }
    private fun refreshShizuku() {
        if (!::status.isInitialized) return
        val running = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        val granted = running && hasPermission()
        shizukuButton.text = if (granted) "SHIZUKU CONECTADO E AUTORIZADO" else if (running) "1. AUTORIZAR ATOMIC NO SHIZUKU" else "1. CONECTAR / INICIAR SHIZUKU"
        shizukuButton.isEnabled = !busy
        executeButton.isEnabled = granted && commands.isNotEmpty() && !busy
        if (!running) status.text = "Shizuku não iniciado. Abra o Shizuku e inicie o serviço."
        else if (!granted) status.text = "Shizuku ativo. Falta autorizar o Atomic."
        else status.text = "Shizuku conectado e autorizado."
    }

    private fun analyzeDevice() {
        if (busy) return
        busy = true; analyzeButton.isEnabled = false; executeButton.isEnabled = false
        status.text = "Enviando informações ao ChatGPT..."
        worker.execute {
            try {
                val payload = collectData()
                val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 20000; readTimeout = 60000; doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()
                if (code !in 200..299) error(try { JSONObject(body).optString("error", "HTTP $code") } catch (_: Exception) { "HTTP $code" })
                val a = JSONObject(body).getJSONObject("analysis")
                val safe = mutableListOf<String>()
                val arr = a.optJSONArray("commands") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val cmd = arr.getJSONObject(i).optString("command").trim().removePrefix("adb shell ").trim()
                    if (isSafeCommand(cmd)) safe.add(cmd)
                }
                runOnUiThread {
                    commands = safe.distinct()
                    diagnosis.text = formatAnalysis(a, commands)
                    status.text = "IA respondeu. ${commands.size} comando(s) passaram pela validação de segurança."
                    busy = false; analyzeButton.isEnabled = true; refreshShizuku()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Falha na análise: ${e.message ?: "erro de conexão"}"
                    diagnosis.text = "Confira a internet e a configuração da API no servidor."
                    busy = false; analyzeButton.isEnabled = true; refreshShizuku()
                }
            }
        }
    }

    private fun executeCommands() {
        if (busy || commands.isEmpty()) return
        if (!hasPermission()) { connectShizuku(); return }
        busy = true; executeButton.isEnabled = false
        status.text = "Executando os comandos recebidos da IA pelo Shizuku..."
        worker.execute {
            val output = StringBuilder()
            try {
                commands.forEach { cmd ->
                    check(isSafeCommand(cmd)) { "Comando bloqueado pela validação local." }
                    output.append("$ ").append(cmd).append("\n").append(runShell(cmd)).append("\n\n")
                }
                runOnUiThread {
                    diagnosis.append("\n\nRESULTADO DA EXECUÇÃO\n").append(output.toString())
                    status.text = "Comandos enviados ao Shizuku. Confira as respostas do Android."
                    busy = false; refreshShizuku()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    diagnosis.append("\n\nERRO AO EXECUTAR\n").append(e.message ?: "Falha")
                    status.text = "Falha ao executar comandos pelo Shizuku."
                    busy = false; refreshShizuku()
                }
            }
        }
    }

    private fun runShell(cmd: String): String {
        check(hasPermission()) { "Shizuku não está conectado ou autorizado." }
        val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        method.isAccessible = true
        val p = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process ?: error("Não iniciou shell.")
        val out = p.inputStream.bufferedReader().use { it.readText() }
        val err = p.errorStream.bufferedReader().use { it.readText() }
        val exit = p.waitFor()
        return (out + if (err.isBlank()) "" else "\n" + err).trim() + "\n[exit=$exit]"
    }

    private fun isSafeCommand(c: String): Boolean {
        if (c.isBlank() || c.length > 300) return false
        if (c.any { it == ';' || it == '|' || it == '&' || it == '\n' || it == '\r' || it == 96.toChar() }) return false
        if (Regex("\\$\\(|\\bsu\\b|\\brm\\b|\\breboot\\b|\\bpm\\s+(disable|uninstall)\\b", RegexOption.IGNORE_CASE).containsMatchIn(c)) return false
        val patterns = listOf(
            Regex("^settings get (system|secure) [a-z0-9_]+$"),
            Regex("^settings put (system|secure) (peak_refresh_rate|min_refresh_rate|user_refresh_rate|miui_refresh_rate) (60|90|120)(\\.0)?$"),
            Regex("^cmd display get-user-preferred-display-mode(?: 0)?$"),
            Regex("^cmd display set-user-preferred-display-mode \\d+ \\d+ (60|90|120)(\\.0)? 0$"),
            Regex("^device_config get game_overlay com\\.dts\\.freefireth$"),
            Regex("^device_config put game_overlay com\\.dts\\.freefireth mode=(2|3),fps=(60|90|120)(:mode=(2|3),fps=(60|90|120))?$"),
            Regex("^cmd game mode (performance|standard) com\\.dts\\.freefireth$")
        )
        return patterns.any { it.matches(c) }
    }

    private fun collectData(): JSONObject {
        val d = window.decorView.display
        val modes = JSONArray()
        d?.supportedModes?.forEach { m -> modes.put(JSONObject().put("width", m.physicalWidth).put("height", m.physicalHeight).put("refresh_rate_hz", m.refreshRate.toDouble())) }
        val ff = try {
            val p = packageManager.getPackageInfo("com.dts.freefireth", 0)
            JSONObject().put("installed", true).put("version", p.versionName ?: "unknown")
        } catch (_: Exception) { JSONObject().put("installed", false) }
        return JSONObject().put("app", "Atomic 120 Hz")
            .put("device", JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL).put("brand", Build.BRAND)
                .put("android_release", Build.VERSION.RELEASE).put("sdk_int", Build.VERSION.SDK_INT)
                .put("supported_display_modes", modes).put("current_app_window_refresh_rate_hz", d?.refreshRate?.toDouble() ?: 0.0))
            .put("game", JSONObject().put("name", "Free Fire").put("package_name", "com.dts.freefireth").put("details", ff))
            .put("execution_capability", "Shizuku API instalado. Comandos seguros podem ser executados após iniciar o serviço e autorizar o app.")
            .put("user_goal", "Investigar limite de 60 FPS e selecionar comandos seguros para tentar 90 FPS no Free Fire.")
    }

    private fun formatAnalysis(a: JSONObject, cmds: List<String>): String {
        val b = StringBuilder(a.optString("summary")).append("\n\nFREQUÊNCIA\nAtual: ")
            .append(a.optString("current_refresh_rate_hz")).append(" Hz\nMáxima: ").append(a.optString("max_supported_refresh_rate_hz"))
            .append(" Hz\n\nLIMITADORES\n")
        val limits = a.optJSONArray("likely_limiters") ?: JSONArray()
        for (i in 0 until limits.length()) b.append("• ").append(limits.optString(i)).append("\n")
        b.append("\nCOMANDOS PREPARADOS PELA IA\n")
        if (cmds.isEmpty()) b.append("Nenhum comando aprovado pela validação.\n")
        cmds.forEachIndexed { i, c -> b.append(i + 1).append(". ").append(c).append("\n") }
        b.append("\nORIENTAÇÕES\n")
        val steps = a.optJSONArray("steps") ?: JSONArray()
        for (i in 0 until steps.length()) {
            val s = steps.getJSONObject(i)
            b.append("\n").append(i + 1).append(". ").append(s.optString("title")).append("\n").append(s.optString("instructions")).append("\n")
        }
        b.append("\nLIMITAÇÃO\n").append(a.optString("limitation"))
        return b.toString()
    }

    private fun refreshDevice() {
        val d = window.decorView.display
        status.text = "Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}\nTaxas disponíveis: " +
            (d?.supportedModes?.map { "${it.refreshRate.toInt()} Hz" }?.distinct()?.joinToString(" / ") ?: "indisponível")
    }
    private fun label(t: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size.toFloat(); setTextColor(color); gravity = Gravity.CENTER
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(dp(4), dp(5), dp(4), dp(5))
    }
    private fun panel(t: String) = label(t, 14, 0xFFD5E7F5.toInt()).apply {
        setPadding(dp(14), dp(16), dp(14), dp(16)); setBackgroundColor(0xFF102943.toInt())
    }
    private fun button(t: String, action: () -> Unit) = Button(this).apply { text = t; isAllCaps = false; textSize = 14f; setOnClickListener { action() } }
    private fun buttonParams(h: Int = 52) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(h))
    private fun match() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
