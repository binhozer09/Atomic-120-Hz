package com.atomic120hz

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var analyzeButton: Button
    private val worker = Executors.newSingleThreadExecutor()

    // Endpoint seguro: a chave da OpenAI fica somente no servidor, nunca no APK.
    private val apiUrl = "https://atomic-120-hz-ai.vercel.app/api/analyze"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 15, 29)
        window.navigationBarColor = Color.rgb(5, 15, 29)
        buildUi()
        showDeviceInfo()
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
            text = "120 Hz  •  AI GAME DIAGNOSTIC"
            textSize = 12f
            letterSpacing = .14f
            setTextColor(0xFF9ABBD5.toInt())
            gravity = Gravity.CENTER
        }, match())

        root.addView(space(22))
        root.addView(TextView(this).apply {
            text = "O Atomic envia os dados do aparelho para a IA analisar o limite de FPS e sugerir comandos e ajustes. Nenhum comando ADB é executado pelo aplicativo."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFD5E7F5.toInt())
            setPadding(dp(14), dp(16), dp(14), dp(16))
            setBackgroundColor(0xFF102943.toInt())
        }, match())

        root.addView(space(18))
        analyzeButton = Button(this).apply {
            text = "ANALISAR COM IA"
            textSize = 17f
            isAllCaps = false
            setOnClickListener { analyzeDevice() }
        }
        root.addView(analyzeButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(62)))

        root.addView(space(14))
        status = TextView(this).apply {
            text = "Pronto para coletar os dados do aparelho."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }
        root.addView(status, match())

        root.addView(space(18))
        result = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFE4EEF7.toInt())
            setPadding(dp(14), dp(16), dp(14), dp(16))
            setBackgroundColor(0xFF0C2034.toInt())
            text = "O diagnóstico da IA aparecerá aqui."
        }
        root.addView(result, match())

        root.addView(space(16))
        root.addView(TextView(this).apply {
            text = "Importante: a IA pode recomendar ajustes, mas não consegue garantir 90 FPS. O resultado depende do painel, do Android/HyperOS e do suporte do próprio Free Fire."
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFF7894AD.toInt())
        }, match())

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showDeviceInfo() {
        val display = window.decorView.display
        val modes = display?.supportedModes?.map { "${it.refreshRate.toInt()} Hz" }?.distinct()
        status.text = "Aparelho: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nModos de tela: ${modes?.joinToString(" / ") ?: "não disponíveis"}"
    }

    private fun analyzeDevice() {
        analyzeButton.isEnabled = false
        status.text = "Coletando dados e consultando a IA..."
        result.text = "Aguardando análise..."
        val payload = collectDeviceData()

        worker.execute {
            try {
                val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 45000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (code !in 200..299) {
                    val message = try { JSONObject(body).optString("error", "HTTP $code") } catch (_: Exception) { "HTTP $code" }
                    error(message)
                }
                val response = JSONObject(body)
                val analysis = response.getJSONObject("analysis")
                val formatted = formatAnalysis(analysis)
                runOnUiThread {
                    status.text = "Análise concluída."
                    result.text = formatted
                    analyzeButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Não foi possível concluir a análise."
                    result.text = e.message ?: "Erro de conexão. Tente novamente."
                    analyzeButton.isEnabled = true
                }
            }
        }
    }

    private fun collectDeviceData(): JSONObject {
        val display = window.decorView.display
        val modes = JSONArray()
        display?.supportedModes?.forEach { mode ->
            modes.put(JSONObject()
                .put("width", mode.physicalWidth)
                .put("height", mode.physicalHeight)
                .put("refresh_rate_hz", mode.refreshRate.toDouble()))
        }
        val freeFire = try {
            val info = packageManager.getPackageInfo("com.dts.freefireth", 0)
            JSONObject()
                .put("installed", true)
                .put("version_name", info.versionName ?: "unknown")
                .put("version_code", if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
        } catch (_: PackageManager.NameNotFoundException) {
            JSONObject().put("installed", false)
        }

        return JSONObject()
            .put("app", "Atomic 120 Hz")
            .put("device", JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("brand", Build.BRAND)
                .put("android_release", Build.VERSION.RELEASE)
                .put("sdk_int", Build.VERSION.SDK_INT)
                .put("supported_display_modes", modes)
                .put("current_app_window_refresh_rate_hz", display?.refreshRate?.toDouble() ?: 0.0))
            .put("game", JSONObject()
                .put("name", "Free Fire")
                .put("package_name", "com.dts.freefireth")
                .put("details", freeFire))
            .put("user_goal", "Diagnosticar por que o Free Fire fica limitado a 60 FPS e sugerir formas seguras e realistas de tentar usar 90 FPS.")
            .put("execution_capability", "Este aplicativo NÃO possui ADB, Shizuku, root nem executor de comandos shell. Apenas coleta dados, envia para análise e exibe recomendações.")
    }

    private fun formatAnalysis(a: JSONObject): String {
        val out = StringBuilder()
        out.append(a.optString("summary")).append("\n\n")
        out.append("FREQUÊNCIA\n")
        out.append("Atual: ").append(a.optString("current_refresh_rate_hz")).append(" Hz\n")
        out.append("Máxima detectada: ").append(a.optString("max_supported_refresh_rate_hz")).append(" Hz\n\n")
        out.append("POSSÍVEIS LIMITADORES\n")
        appendArray(out, a.optJSONArray("likely_limiters"))
        out.append("\nORIENTAÇÕES\n")
        val steps = a.optJSONArray("steps")
        if (steps != null) for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            out.append("\n").append(i + 1).append(". ").append(step.optString("title")).append("\n")
            out.append(step.optString("instructions")).append("\n")
        }
        out.append("\nCOMANDOS SUGERIDOS (NÃO EXECUTADOS)\n")
        val commands = a.optJSONArray("commands")
        if (commands != null) for (i in 0 until commands.length()) {
            val c = commands.getJSONObject(i)
            out.append("\n").append(i + 1).append(". ").append(c.optString("purpose")).append("\n")
            out.append("$ ").append(c.optString("command")).append("\n")
            out.append("Requer ADB: ").append(if (c.optBoolean("requires_adb")) "sim" else "não")
                .append(" • Risco: ").append(c.optString("risk")).append("\n")
        }
        out.append("\nLIMITAÇÃO\n").append(a.optString("limitation"))
        return out.toString()
    }

    private fun appendArray(out: StringBuilder, values: JSONArray?) {
        if (values == null || values.length() == 0) {
            out.append("Nenhum identificado com os dados disponíveis.\n")
            return
        }
        for (i in 0 until values.length()) out.append("• ").append(values.optString(i)).append("\n")
    }

    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun match() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
