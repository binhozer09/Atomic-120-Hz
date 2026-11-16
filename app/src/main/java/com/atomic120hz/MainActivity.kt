package com.atomic120hz

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import java.io.IOException
import java.util.concurrent.Executors
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private var selectedHz = 90
    private var enabled = false
    private var shizukuReady = false
    private lateinit var status: TextView
    private lateinit var detected: TextView
    private lateinit var connectButton: Button
    private lateinit var executeButton: Button
    private lateinit var toggle: Button
    private lateinit var hz90: Button
    private lateinit var hz120: Button
    private lateinit var diagnosticStartButton: Button
    private lateinit var diagnosticStopButton: Button
    private lateinit var diagnosticReport: TextView
    private lateinit var diagnosticCopyButton: Button
    private lateinit var exploreFreeFireButton: Button
    private lateinit var inspectApkButton: Button
    private lateinit var autoFixButton: Button
    private val worker = Executors.newSingleThreadExecutor()
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_CODE) {
            runOnUiThread {
                shizukuReady = grantResult == PackageManager.PERMISSION_GRANTED && Shizuku.pingBinder()
                updateConnectionUi()
                if (shizukuReady) status.text = "Shizuku autorizado. Toque em EXECUTAR COMANDOS."
                else status.text = "Permissão do Shizuku não concedida."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 15, 29)
        window.navigationBarColor = Color.rgb(5, 15, 29)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        buildUi()
        refreshDisplayInfo()
        checkShizuku()
    }

    override fun onResume() {
        super.onResume()
        checkShizuku()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
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
            text = "⚛  ATOMIC 120 Hz"
            textSize = 27f
            setTextColor(0xFF55C9FF.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, match())
        root.addView(TextView(this).apply {
            text = "AJUSTE DE FREQUÊNCIA • SHIZUKU"
            textSize = 12f
            letterSpacing = .12f
            setTextColor(0xFF9ABBD5.toInt())
            gravity = Gravity.CENTER
        }, match())
        root.addView(space(22))
        detected = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(18), dp(12), dp(18))
            setBackgroundColor(0xFF102943.toInt())
        }
        root.addView(detected, match())
        root.addView(space(18))

        root.addView(stepTitle("1. BAIXAR O SHIZUKU"))
        root.addView(TextView(this).apply {
            text = "Instale o Shizuku separadamente. Depois abra o Shizuku e inicie o serviço usando Depuração sem fio."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }, match())
        root.addView(space(8))
        root.addView(actionButton("BAIXAR SHIZUKU") { openShizukuDownload() }, buttonParams(52))
        root.addView(space(18))

        root.addView(stepTitle("2. CONECTAR E AUTORIZAR"))
        root.addView(TextView(this).apply {
            text = "No Shizuku, siga as instruções de Depuração sem fio do Android. Quando o serviço estiver iniciado, volte aqui e autorize o Atomic."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }, match())
        root.addView(space(8))
        connectButton = actionButton("CONECTAR AO SHIZUKU / AUTORIZAR") { connectShizuku() }
        root.addView(connectButton, buttonParams(56))
        root.addView(space(10))
        status = TextView(this).apply {
            text = "Aguardando o Shizuku."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }
        root.addView(status, match())
        root.addView(space(18))

        root.addView(stepTitle("3. EXECUTAR OS COMANDOS"))
        executeButton = actionButton("EXECUTAR COMANDOS PARA 90 FPS") { applySelectedMode() }
        root.addView(executeButton, buttonParams(60))
        root.addView(space(8))
        root.addView(actionButton("VERIFICAR LIMITAÇÃO DO FREE FIRE") { diagnoseFreeFireLimit() }, buttonParams(52))
        exploreFreeFireButton = actionButton("EXPLORAR PASTAS DO FREE FIRE (LEITURA)") { exploreFreeFireStorage() }
        root.addView(exploreFreeFireButton, buttonParams(56))
        root.addView(space(8))
        root.addView(actionButton("LOCALIZAR ARQUIVOS DE CONFIGURAÇÃO DE FPS") { locateFreeFireFpsFiles() }, buttonParams(58))
        root.addView(space(8))
        inspectApkButton = actionButton("ANALISAR APK DO FREE FIRE (SOMENTE LEITURA)") { inspectFreeFireApk() }
        root.addView(inspectApkButton, buttonParams(58))
        root.addView(space(8))
        autoFixButton = actionButton("ANALISAR E CORRIGIR AUTOMATICAMENTE") { runAutomaticDiagnosisAndRepair() }
        root.addView(autoFixButton, buttonParams(58))
        root.addView(space(8))
        root.addView(space(8))
        root.addView(actionButton("TENTAR 90 FPS NO FREE FIRE") { tryFreeFire90Fps() }, buttonParams(58))
        root.addView(space(8))
        root.addView(actionButton("INVESTIGAR POLÍTICAS DO ANDROID / HYPEROS") { investigateAndroidFpsPolicy() }, buttonParams(56))
        root.addView(space(8))
        root.addView(actionButton("DIAGNÓSTICO COMPLETO — TODOS OS SETORES") { runCompleteFreeFireAudit() }, buttonParams(62))
        root.addView(space(8))
        root.addView(actionButton("SOLICITAR 90 HZ (PRESERVA LIMITE TÉRMICO)") { force90Hz() }, buttonParams(58))
        root.addView(space(8))
        root.addView(actionButton("SOLICITAR MODO NATIVO DE DISPLAY 90 HZ") { requestNative90Hz() }, buttonParams(58))
        root.addView(space(18))

        root.addView(stepTitle("4. DIAGNÓSTICO REAL DE FPS"))
        root.addView(TextView(this).apply {
            text = "Toque em INICIAR, volte ao Free Fire e jogue por cerca de 30 segundos. Depois volte ao Atomic e toque em FINALIZAR para gerar o relatório."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF9ABBD5.toInt())
        }, match())
        root.addView(space(8))
        diagnosticStartButton = actionButton("INICIAR MEDIÇÃO") { startFpsDiagnostic() }
        root.addView(diagnosticStartButton, buttonParams(52))
        root.addView(space(8))
        diagnosticStopButton = actionButton("FINALIZAR E MOSTRAR RELATÓRIO") { finishFpsDiagnostic() }
        root.addView(diagnosticStopButton, buttonParams(52))
        root.addView(space(8))
        diagnosticReport = TextView(this).apply {
            text = "O relatório aparecerá aqui."
            textSize = 12f
            setTextColor(0xFFE1EDF7.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFF102943.toInt())
        }
        diagnosticCopyButton = actionButton("COPIAR RELATÓRIO") { copyDiagnosticLog() }
        root.addView(diagnosticCopyButton, buttonParams(52))
        root.addView(space(8))
        root.addView(diagnosticReport, match())
        root.addView(space(18))

        root.addView(stepTitle("FREQUÊNCIA DESEJADA"))
        val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        hz90 = actionButton("90 Hz") { choose(90) }
        hz120 = actionButton("120 Hz") { choose(120) }
        choices.addView(hz90, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(6) })
        choices.addView(hz120, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(6) })
        root.addView(choices, match())
        root.addView(space(10))
        toggle = actionButton("RESTAURAR CONFIGURAÇÕES") { restoreSettings() }
        root.addView(toggle, buttonParams(50))
        root.addView(space(16))
        root.addView(TextView(this).apply {
            text = "Importante: o Atomic executa e verifica os comandos permitidos pelo Android. Isso não força todo jogo a renderizar em 90 FPS; o próprio jogo e o sistema podem limitar a taxa."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF7894AD.toInt())
        }, match())
        setContentView(ScrollView(this).apply { addView(root) })
        updateConnectionUi()
        updateButtons()
    }

    private fun checkShizuku() {
        val installed = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: Exception) { false }
        val binderAlive = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        shizukuReady = installed && binderAlive &&
            try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false }
        if (!installed) status.text = "Shizuku não instalado. Use o botão BAIXAR SHIZUKU."
        else if (!binderAlive) status.text = "Shizuku instalado, mas o serviço não está iniciado. Inicie-o no Shizuku."
        else if (!shizukuReady) status.text = "Serviço ativo. Toque em CONECTAR / AUTORIZAR."
        else status.text = "Shizuku conectado e autorizado."
        updateConnectionUi()
    }

    private fun connectShizuku() {
        val installed = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: Exception) { false }
        if (!installed) {
            status.text = "Instale o Shizuku primeiro."
            openShizukuDownload()
            return
        }
        val alive = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        if (!alive) {
            status.text = "Abra o Shizuku e inicie o serviço por Depuração sem fio. Depois volte aqui."
            return
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                shizukuReady = true
                status.text = "Shizuku conectado e autorizado."
                updateConnectionUi()
            } else {
                status.text = "Aguardando você autorizar o Atomic na janela do Shizuku..."
                Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE)
            }
        } catch (e: Exception) {
            status.text = "Não foi possível solicitar a autorização: ${e.message}"
        }
        updateConnectionUi()
    }

    private fun applySelectedMode() {
        if (!hasShizukuPermission()) return
        executeButton.isEnabled = false
        status.text = "Salvando preferências e solicitando $selectedHz Hz nas camadas compatíveis..."
        worker.execute {
            try {
                val systemKeys = listOf("min_refresh_rate", "peak_refresh_rate", "user_refresh_rate", "user_peak_refresh_rate", "miui_refresh_rate")
                val prefs = getSharedPreferences("atomic_refresh_backup", MODE_PRIVATE)
                systemKeys.forEach { key ->
                    val old = AtomicShizukuClient.run("settings get system $key 2>&1 || true").trim()
                    if (!prefs.contains(key)) prefs.edit().putString(key, old).apply()
                }
                val oldGlobal = AtomicShizukuClient.run("settings get global user_preferred_refresh_rate 2>&1 || true").trim()
                if (!prefs.contains("global_user_preferred_refresh_rate")) prefs.edit().putString("global_user_preferred_refresh_rate", oldGlobal).apply()
                val writes = systemKeys.joinToString(" ; ") { "settings put system $it $selectedHz 2>&1" } +
                    " ; settings put global user_preferred_refresh_rate $selectedHz 2>&1"
                val verify = systemKeys.joinToString(" ; ") { "echo $it=\$(settings get system $it)" } +
                    " ; echo global_user_preferred_refresh_rate=\$(settings get global user_preferred_refresh_rate)"
                val result = AtomicShizukuClient.run("$writes ; echo '--- PREFERÊNCIAS GRAVADAS ---' ; $verify ; echo '--- DISPLAY ATIVO ---' ; dumpsys display | grep -iE 'mActiveSfDisplayMode|mActiveRenderFrameRate|mDesiredDisplayModeSpecs|mDisplayModeSpecs|renderFrameRate' | head -n 35")
                runOnUiThread {
                    executeButton.isEnabled = true
                    enabled = true
                    diagnosticReport.text = "PREFERÊNCIAS DE $selectedHz HZ SOLICITADAS\n" +
                        "Foram alteradas preferências conhecidas de taxa de atualização. Limites térmicos e políticas internas do jogo não foram forçados.\n\n$result"
                    status.text = "Preferências de $selectedHz Hz gravadas. A taxa ativa ainda depende das políticas do HyperOS e do jogo."
                    refreshDisplayInfo()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    executeButton.isEnabled = true
                    status.text = "Falha ao solicitar $selectedHz Hz: ${e.message}"
                }
            }
        }
    }

    private fun tryFreeFire90Fps() {
        if (!hasShizukuPermission()) return
        status.text = "Solicitando 90 Hz e verificando o perfil do Free Fire..."
        worker.execute {
            // Cada comando é executado separadamente: evita erros de parsing do shell
            // causados por encadeamento, pipes e substituição de comandos.
            fun check(command: String): String = try {
                AtomicShizukuClient.run(command).ifBlank { "(sem saída)" }
            } catch (e: Exception) {
                "ERRO: ${e.message ?: "falha sem detalhes"}"
            }

            val keys = listOf("min_refresh_rate", "peak_refresh_rate", "user_refresh_rate", "user_peak_refresh_rate", "miui_refresh_rate")
            val report = StringBuilder()
            report.appendLine("=== SOLICITAÇÃO DE 90 HZ ===")
            for (key in keys) {
                report.appendLine("$key: " + check("settings put system $key 90"))
            }
            report.appendLine("user_preferred_refresh_rate: " + check("settings put global user_preferred_refresh_rate 90"))

            report.appendLine("\\n=== VALORES GRAVADOS ===")
            for (key in keys) {
                report.appendLine("$key=" + check("settings get system $key"))
            }
            report.appendLine("global_user_preferred_refresh_rate=" + check("settings get global user_preferred_refresh_rate"))

            report.appendLine("\\n=== PERFIL ANDROID DO FREE FIRE ===")
            report.appendLine(check("device_config get game_overlay com.dts.freefireth"))

            report.appendLine("\\n=== GAME MANAGER (LINHAS RELEVANTES) ===")
            val gameDump = check("dumpsys game")
            report.appendLine(gameDump.lineSequence().filter { it.contains("com.dts.freefireth", true) || it.contains("fps", true) || it.contains("mode", true) }.take(50).joinToString("\\n").ifBlank { "(nenhuma linha relevante)" })

            report.appendLine("\\n=== CAMADA DO FREE FIRE ===")
            val layers = check("dumpsys SurfaceFlinger --list")
            report.appendLine(layers.lineSequence().filter { it.contains("com.dts.freefireth", true) || it.contains("FFMainActivity", true) }.take(30).joinToString("\\n").ifBlank { "(camada não localizada)" })

            report.appendLine("\\n=== DISPLAY ATIVO ===")
            val display = check("dumpsys display")
            report.appendLine(display.lineSequence().filter { it.contains("mActiveSfDisplayMode", true) || it.contains("mActiveRenderFrameRate", true) || it.contains("renderFrameRate", true) }.take(25).joinToString("\\n").ifBlank { "(dados de taxa não encontrados)" })

            runOnUiThread {
                diagnosticReport.text = "TENTATIVA DE 90 FPS — FREE FIRE\\n" +
                    "Comandos executados individualmente para evitar erro de sintaxe. A proteção térmica foi preservada.\\n\\n" +
                    report.toString().take(10000)
                status.text = "Diagnóstico concluído. Confira os erros individuais no relatório."
                refreshDisplayInfo()
            }
        }
    }

    /**
     * Inspeciona a estrutura do APK instalado sem modificar, extrair para disco,
     * reempacotar ou alterar assinatura/anti-cheat.
     */
    private fun inspectFreeFireApk() {
        inspectApkButton.isEnabled = false
        status.text = "Analisando APK principal, splits e recursos candidatos (somente leitura)..."
        worker.execute {
            try {
                val appInfo = packageManager.getApplicationInfo("com.dts.freefireth", 0)
                val apkPaths = (listOfNotNull(appInfo.sourceDir) + (appInfo.splitSourceDirs?.toList() ?: emptyList())).distinct()
                val terms = Regex("(?i)fps|frame.?rate|refresh.?rate|target.?frame|target.?fps|high.?frame|frame.?limit|render.?rate")
                val numeric = Regex("(?i)(?:fps|frame.?rate|refresh.?rate|target.?frame|target.?fps|frame.?limit|render.?rate)[^\\r\\n]{0,48}(?:30|45|60|72|90|120|144|165|180|200)|(?:30|45|60|72|90|120|144|165|180|200)[^\\r\\n]{0,48}(?:fps|frame.?rate|refresh.?rate|target.?frame|target.?fps|frame.?limit|render.?rate)")
                val report = StringBuilder()
                report.appendLine("=== ATOMIC 120 HZ — ANÁLISE AVANÇADA DO APK ===")
                report.appendLine("MODO: SOMENTE LEITURA. Nenhum arquivo será alterado.")
                report.appendLine("APKs encontrados: ${apkPaths.size} (base + splits instalados)")
                var totalEntries = 0
                var totalBytes = 0L
                var totalCandidates = 0
                val findings = mutableListOf<String>()
                val fileCandidates = mutableListOf<String>()
                val maxTotalBytes = 48L * 1024 * 1024
                for ((apkIndex, apkPath) in apkPaths.withIndex()) {
                    val apkFile = java.io.File(apkPath)
                    report.appendLine()
                    report.appendLine("APK ${apkIndex + 1}: $apkPath")
                    report.appendLine("Tamanho: ${apkFile.length()} bytes")
                    java.util.zip.ZipFile(apkPath).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            totalEntries++
                            if (entry.isDirectory) continue
                            val name = entry.name
                            val lower = name.lowercase()
                            val configLike = lower.contains("config") || lower.contains("resconf") ||
                                lower.contains("setting") || lower.contains("quality") ||
                                lower.contains("frame") || lower.contains("fps") ||
                                lower.endsWith(".json") || lower.endsWith(".xml") ||
                                lower.endsWith(".ini") || lower.endsWith(".cfg") ||
                                lower.endsWith(".conf") || lower.endsWith(".properties")
                            if (configLike && fileCandidates.size < 100) {
                                fileCandidates.add("APK ${apkIndex + 1}: $name (${entry.size} bytes)")
                            }
                            val inspectable = configLike || lower.endsWith(".dex")
                            if (!inspectable || entry.size !in 1..(4L * 1024 * 1024) ||
                                totalBytes >= maxTotalBytes) continue
                            val remaining = (maxTotalBytes - totalBytes).coerceAtLeast(0L)
                            val limit = minOf(entry.size, remaining, 4L * 1024 * 1024).toInt()
                            if (limit <= 0) continue
                            val bytes = zip.getInputStream(entry).use { input ->
                                val out = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                var readTotal = 0
                                while (readTotal < limit) {
                                    val n = input.read(buffer, 0, minOf(buffer.size, limit - readTotal))
                                    if (n <= 0) break
                                    out.write(buffer, 0, n)
                                    readTotal += n
                                }
                                out.toByteArray()
                            }
                            totalBytes += bytes.size
                            val content = String(bytes, Charsets.ISO_8859_1)
                            val hits = terms.findAll(content).take(24).toList()
                            if (hits.isNotEmpty()) {
                                totalCandidates++
                                val numericHits = numeric.findAll(content).take(8).map { it.value.replace(Regex("[\\x00-\\x1F]+"), " ").trim().take(140) }.distinct().toList()
                                val contexts = hits.take(6).map { hit ->
                                    val from = (hit.range.first - 90).coerceAtLeast(0)
                                    val to = (hit.range.last + 110).coerceAtMost(content.length)
                                    content.substring(from, to).replace(Regex("[\\x00-\\x1F]+"), " ").replace(Regex("\\s+"), " ").trim().take(240)
                                }.distinct()
                                val category = when {
                                    lower.contains("resconf") -> "RECURSO RESCONF"
                                    lower.contains("config") || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".ini") || lower.endsWith(".cfg") -> "POSSÍVEL CONFIGURAÇÃO"
                                    lower.endsWith(".dex") -> "CÓDIGO DEX (REFERÊNCIA TEXTUAL)"
                                    else -> "RECURSO / ASSET"
                                }
                                findings.add("[$category] APK ${apkIndex + 1}: $name")
                                if (numericHits.isNotEmpty()) findings.add("  VALORES PRÓXIMOS A TERMOS DE FPS: ${numericHits.joinToString(" | ")}")
                                contexts.forEach { findings.add("  CONTEXTO: $it") }
                            }
                        }
                    }
                }
                report.appendLine()
                report.appendLine("=== RESUMO DA VARREDURA ===")
                report.appendLine("Entradas ZIP examinadas: $totalEntries")
                report.appendLine("Conteúdo examinado: $totalBytes bytes (limite total: 48 MiB)")
                report.appendLine("Arquivos candidatos de configuração listados: ${fileCandidates.size}")
                report.appendLine("Arquivos com referências textuais relacionadas: $totalCandidates")
                report.appendLine()
                report.appendLine("=== ARQUIVOS / RECURSOS CANDIDATOS ===")
                if (fileCandidates.isEmpty()) report.appendLine("Nenhum nome de arquivo candidato encontrado.")
                else fileCandidates.forEach { report.appendLine(it) }
                report.appendLine()
                report.appendLine("=== REFERÊNCIAS COM CONTEXTO ===")
                if (findings.isEmpty()) report.appendLine("Nenhuma referência textual relacionada a FPS foi localizada nos conteúdos examinados.")
                else findings.take(160).forEach { report.appendLine(it) }
                report.appendLine()
                report.appendLine("=== LIMITES E INTERPRETAÇÃO ===")
                report.appendLine("A análise inclui base.apk e todos os split APKs informados pelo Android.")
                report.appendLine("Recursos binários podem conter dados compactados, codificados ou estruturados que uma busca textual não interpreta.")
                report.appendLine("Números próximos de FPS são apenas pistas; não comprovam que sejam limites ativos ou modificáveis.")
                report.appendLine("Nenhum arquivo foi extraído para alteração, reempacotado, assinado ou modificado.")
                report.appendLine("Nenhuma proteção, mecanismo anti-cheat ou dado privado do jogo foi acessado.")
                runOnUiThread {
                    inspectApkButton.isEnabled = true
                    diagnosticReport.text = report.toString().take(22000)
                    status.text = "Análise avançada concluída. Confira o relatório; nenhum arquivo foi modificado."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    inspectApkButton.isEnabled = true
                    diagnosticReport.text = "Não foi possível analisar os APKs instalados: ${e.javaClass.simpleName}: ${e.message}\n\nA análise não modificou nenhum arquivo."
                    status.text = "Falha ao analisar os APKs do Free Fire."
                }
            }
        }
    }

    private fun locateFreeFireFpsFiles() {
        if (!hasShizukuPermission()) return
        status.text = "Procurando configurações de FPS nos arquivos acessíveis do Free Fire..."
        worker.execute {
            try {
                val command = """
                    echo '=== ATOMIC 120 HZ — BUSCA DE CONFIGURAÇÕES DE FPS ==='
                    echo 'MODO: SOMENTE LEITURA. Nenhum arquivo será alterado.'
                    echo '=== PACOTE PRINCIPAL E SPLITS INSTALADOS ==='
                    pm path com.dts.freefireth 2>&1
                    echo '=== DIRETÓRIOS EXTERNOS ACESSÍVEIS ==='
                    for d in /sdcard/Android/data/com.dts.freefireth/files /sdcard/Android/data/com.dts.freefireth; do
                      if [ -d "${'$'}d" ]; then echo "ACESSÍVEL: ${'$'}d"; else echo "INDISPONÍVEL: ${'$'}d"; fi
                    done
                    echo '=== ARQUIVOS DE CONFIGURAÇÃO CANDIDATOS (CACHE EXCLUÍDO) ==='
                    find /sdcard/Android/data/com.dts.freefireth -type f -size -2M \
                      ! -path '*/cache/*' \
                      \\( -iname '*.ini' -o -iname '*.cfg' -o -iname '*.conf' -o -iname '*.json' -o -iname '*.xml' -o -iname '*.txt' -o -iname '*.config' -o -iname '*.properties' -o -iname '*.dat' \\) \
                      -print 2>&1 | head -n 160
                    echo '=== REFERÊNCIAS A FPS / FRAME RATE NOS ARQUIVOS TEXTUAIS ==='
                    find /sdcard/Android/data/com.dts.freefireth -type f -size -2M \
                      ! -path '*/cache/*' \
                      \\( -iname '*.ini' -o -iname '*.cfg' -o -iname '*.conf' -o -iname '*.json' -o -iname '*.xml' -o -iname '*.txt' -o -iname '*.config' -o -iname '*.properties' -o -iname '*.dat' \\) \
                      -exec grep -IinE 'fps|frame.?rate|refresh.?rate|target.?frame|60[[:space:]]*fps|90[[:space:]]*fps' {} \\; 2>/dev/null | head -n 180
                    echo '=== ARQUIVOS PRINCIPAIS DO APK (SOMENTE CAMINHOS) ==='
                    pm path com.dts.freefireth 2>&1
                    echo 'NOTA: o APK principal é assinado; esta busca não o modifica nem tenta reempacotá-lo.'
                    echo '=== FIM DA BUSCA ==='
                """.trimIndent()
                val output = AtomicShizukuClient.run(command).take(18000)
                val found = output.contains("MATCH:")
                runOnUiThread {
                    diagnosticReport.text = "BUSCA DE ARQUIVOS DE CONFIGURAÇÃO — FREE FIRE\n" +
                        "A busca examinou somente arquivos externos acessíveis, ignorando o cache. Nenhum arquivo foi alterado.\n\n" +
                        output.ifBlank { "O Android não retornou conteúdo para a busca." } +
                        if (found) "\n\nATENÇÃO: referências encontradas precisam ser verificadas; uma ocorrência de texto não prova que controla o limite de FPS." else
                            "\n\nNenhuma referência textual clara a FPS foi localizada nos arquivos acessíveis. Isso não prova que exista um limitador em arquivo; ele pode estar no código do APK, no servidor ou nas políticas do sistema."
                    status.text = "Busca concluída. Confira os arquivos e as referências encontradas."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    diagnosticReport.text = "Falha na busca de configurações: " + e.message
                    status.text = "Não foi possível concluir a busca."
                }
            }
        }
    }

    private fun exploreFreeFireStorage() {
        if (!hasShizukuPermission()) return
        exploreFreeFireButton.isEnabled = false
        status.text = "Inspecionando caminhos do Free Fire. Nenhum arquivo será alterado..."
        worker.execute {
            fun read(command: String): String = try {
                AtomicShizukuClient.run(command).ifBlank { "(sem saída)" }
            } catch (e: Exception) {
                "BLOQUEADO/ERRO: ${e.message ?: "sem detalhes"}"
            }

            val report = StringBuilder()
            report.appendLine("=== ATOMIC 120 HZ — EXPLORADOR FREE FIRE ===")
            report.appendLine("Modo: SOMENTE LEITURA. Nenhum arquivo foi criado, apagado ou modificado.")
            report.appendLine()
            report.appendLine("=== IDENTIDADE E NÍVEL DE ACESSO DO SHIZUKU ===")
            report.appendLine(read("id; echo \\\"SELINUX:\\\"; getenforce 2>&1; echo \\\"MONTAGENS:\\\"; mount | grep -E \\\"/storage|/data/user|/mnt/user\\\" | head -n 50"))
            report.appendLine()
            report.appendLine("=== PACOTE INSTALADO ===")
            report.appendLine(read("dumpsys package com.dts.freefireth | grep -iE 'versionName|versionCode|codePath|dataDir|credentialProtectedDataDir|deviceProtectedDataDir|userId|targetSdk' | head -n 60"))
            report.appendLine()
            report.appendLine("=== CAMINHO DO APK ===")
            report.appendLine(read("pm path com.dts.freefireth 2>&1"))
            report.appendLine()
            report.appendLine("=== PASTA PRIVADA DO APLICATIVO ===")
            report.appendLine(read("ls -ld /data/user/0/com.dts.freefireth /data/data/com.dts.freefireth /data/user_de/0/com.dts.freefireth 2>&1"))
            report.appendLine("Nota: 'Permission denied' é o isolamento normal do Android para dados privados.")
            report.appendLine()
            report.appendLine("=== TESTE DE ACESSO A ANDROID/DATA (VÁRIOS CAMINHOS) ===")
            report.appendLine(read("for p in /sdcard/Android/data/com.dts.freefireth /storage/emulated/0/Android/data/com.dts.freefireth /mnt/user/0/primary/Android/data/com.dts.freefireth; do echo \\\"--- \$p ---\\\"; ls -la \\\"\$p\\\" 2>&1; done"))
            report.appendLine()
            report.appendLine("=== DADOS EXTERNOS DO JOGO ===")
            report.appendLine(read("ls -la /sdcard/Android/data/com.dts.freefireth 2>&1"))
            report.appendLine()
            report.appendLine("=== OBB / ARQUIVOS DE EXPANSÃO ===")
            report.appendLine(read("ls -la /sdcard/Android/obb/com.dts.freefireth 2>&1"))
            report.appendLine()
            report.appendLine("=== ARQUIVOS VISÍVEIS (APENAS NOMES, ATÉ 3 NÍVEIS) ===")
            report.appendLine(read("find /sdcard/Android/data/com.dts.freefireth -maxdepth 3 -type f 2>&1 | head -n 100"))
            report.appendLine()
            report.appendLine("=== PERMISSÕES DO PACOTE ===")
            report.appendLine(read("cmd appops get com.dts.freefireth 2>&1 | head -n 80"))
            report.appendLine()
            report.appendLine("=== PROCESSO DO JOGO (SE ESTIVER ABERTO) ===")
            report.appendLine(read("pidof com.dts.freefireth 2>&1"))
            report.appendLine()
            report.appendLine("=== RESULTADO ===")
            report.appendLine("Este relatório mostra o que o Android permite ao Shizuku/UID shell enxergar. Não tenta obter root, invadir sandbox, contornar anti-cheat ou alterar dados protegidos.")
            runOnUiThread {
                exploreFreeFireButton.isEnabled = true
                diagnosticReport.text = report.toString().take(14000)
                status.text = "Exploração concluída. Confira quais caminhos estão acessíveis e quais foram bloqueados."
            }
        }
    }

    /**
     * Diagnóstico local por regras, sem chave de API ou conexão externa.
     * Corrige somente preferências públicas de taxa de atualização; nunca altera
     * arquivos do Free Fire, limites térmicos, SELinux, permissões ou anti-cheat.
     */
    private fun runAutomaticDiagnosisAndRepair() {
        if (!hasShizukuPermission()) return
        autoFixButton.isEnabled = false
        status.text = "Analisando o sistema e aplicando apenas correções seguras..."
        worker.execute {
            fun read(command: String): String = try {
                AtomicShizukuClient.run(command).ifBlank { "(sem saída)" }
            } catch (e: Exception) {
                "ERRO: ${e.message ?: "sem detalhes"}"
            }

            val report = StringBuilder()
            report.appendLine("=== ATOMIC 120 HZ — DIAGNÓSTICO AUTOMÁTICO ===")
            report.appendLine("Motor: análise local por regras; não exige chave de API nem envia dados para a internet.")
            report.appendLine()

            val packagePath = read("pm path com.dts.freefireth 2>&1")
            report.appendLine("1) FREE FIRE INSTALADO")
            report.appendLine(packagePath)
            val gameInstalled = packagePath.contains("package:", true)

            val identity = read("id; getenforce 2>&1")
            report.appendLine()
            report.appendLine("2) IDENTIDADE SHIZUKU / SELINUX")
            report.appendLine(identity)

            val external = read("ls -ld /sdcard/Android/data/com.dts.freefireth /sdcard/Android/obb/com.dts.freefireth 2>&1")
            report.appendLine()
            report.appendLine("3) PASTAS EXTERNAS")
            report.appendLine(external)

            val thermal = read("dumpsys thermalservice 2>&1 | head -n 70")
            report.appendLine()
            report.appendLine("4) ESTADO TÉRMICO (SOMENTE LEITURA)")
            report.appendLine(thermal)

            val gameInfo = read("dumpsys game 2>&1 | grep -i -A 8 -B 3 'com.dts.freefireth' | head -n 70")
            val overlay = read("device_config get game_overlay com.dts.freefireth 2>&1")
            report.appendLine()
            report.appendLine("5) PERFIL DE JOGO / GAME MANAGER")
            report.appendLine("Game Manager:\\n$gameInfo")
            report.appendLine("Game overlay: $overlay")

            report.appendLine()
            report.appendLine("6) CORREÇÕES AUTOMÁTICAS PERMITIDAS")
            if (!gameInstalled) {
                report.appendLine("Nenhuma correção aplicada: o pacote com.dts.freefireth não foi localizado.")
            } else {
                val keys = listOf("peak_refresh_rate", "user_refresh_rate", "user_peak_refresh_rate", "miui_refresh_rate")
                val changes = mutableListOf<String>()
                for (key in keys) {
                    val before = read("settings get system $key").trim()
                    val write = read("settings put system $key 90 2>&1")
                    val after = read("settings get system $key").trim()
                    changes.add("$key: antes=$before; depois=$after; retorno=$write")
                }
                val globalBefore = read("settings get global user_preferred_refresh_rate").trim()
                val globalWrite = read("settings put global user_preferred_refresh_rate 90 2>&1")
                val globalAfter = read("settings get global user_preferred_refresh_rate").trim()
                changes.add("global user_preferred_refresh_rate: antes=$globalBefore; depois=$globalAfter; retorno=$globalWrite")
                report.appendLine(changes.joinToString("\\n"))
                report.appendLine("A preferência min_refresh_rate não foi forçada para evitar manter o painel em frequência alta continuamente.")
            }

            val display = read("dumpsys display 2>&1 | grep -iE 'mActiveSfDisplayMode|mActiveRenderFrameRate|mDesiredDisplayModeSpecs|mDisplayModeSpecs|renderFrameRate|mRefreshRateThrottling|defaultPeakRefreshRate' | head -n 65")
            report.appendLine()
            report.appendLine("7) VERIFICAÇÃO DO DISPLAY APÓS A CORREÇÃO")
            report.appendLine(display)

            report.appendLine()
            report.appendLine("=== CONCLUSÃO AUTOMÁTICA ===")
            if (!gameInstalled) {
                report.appendLine("O Free Fire não foi encontrado. Nenhuma preferência foi modificada.")
            } else if (display.contains("60.0") || display.contains("60 Hz")) {
                report.appendLine("O sistema ainda mostra referências a 60 Hz. Pode haver política do HyperOS, modo de tela ou limitação do jogo.")
            } else {
                report.appendLine("Preferências de 90 Hz solicitadas. Isso não comprova 90 FPS dentro do jogo; é necessário medir a camada do Free Fire durante uma partida.")
            }
            report.appendLine("Proteções térmicas, SELinux, arquivos privados do jogo e mecanismos anti-cheat permaneceram intactos.")
            report.appendLine("Este motor local identifica e corrige apenas situações conhecidas e seguras; não é uma cópia do ChatGPT e não pode garantir desbloqueio de FPS.")

            runOnUiThread {
                autoFixButton.isEnabled = true
                diagnosticReport.text = report.toString().take(18000)
                status.text = "Diagnóstico automático concluído. Veja o relatório e a verificação final."
                refreshDisplayInfo()
            }
        }
    }

    private fun diagnoseFreeFireLimit() {
        if (!hasShizukuPermission()) return
        status.text = "Verificando configurações do sistema e perfil do Free Fire..."
        worker.execute {
            try {
                val command = """
                    echo '=== PACOTE FREE FIRE ==='
                    dumpsys package com.dts.freefireth | grep -iE 'versionName|versionCode|targetSdk' || true
                    echo '=== TAXAS CONFIGURADAS ==='
                    echo -n 'min_refresh_rate='; settings get system min_refresh_rate
                    echo -n 'peak_refresh_rate='; settings get system peak_refresh_rate
                    echo -n 'user_refresh_rate='; settings get system user_refresh_rate
                    echo -n 'user_peak_refresh_rate='; settings get system user_peak_refresh_rate
                    echo '=== PERFIL GAME OVERLAY ==='
                    device_config get game_overlay com.dts.freefireth 2>&1 || true
                    echo '=== JOGOS RECONHECIDOS PELO ANDROID ==='
                    cmd game list 2>&1 || true
                    echo '=== ESTADO DO DISPLAY ==='
                    dumpsys display | grep -iE 'mActiveDisplayMode|mActiveSfDisplayMode|defaultModeId|defaultPeakRefreshRate|renderFrameRate|refreshRate' | head -n 45 || true
                    echo '=== SERVICO DE JOGOS ==='
                    dumpsys game 2>&1 | grep -i -A 5 -B 3 'com.dts.freefireth' | head -n 60 || true
                """.trimIndent()
                val output = AtomicShizukuClient.run(command).take(10000)
                runOnUiThread {
                    diagnosticReport.text = "VERIFICAÇÃO DO LIMITE DO FREE FIRE\n" +
                        "Coleta somente leitura; nenhum arquivo ou configuração do jogo foi alterado.\n\n" +
                        output.ifBlank { "O sistema não retornou dados para esses itens." }
                    status.text = "Verificação concluída. Copie o relatório e envie aqui."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    diagnosticReport.text = "Falha na verificação: " + e.message
                    status.text = "Não foi possível concluir o diagnóstico."
                }
            }
        }
    }

    private fun investigateAndroidFpsPolicy() {
        if (!hasShizukuPermission()) return
        status.text = "Identificando o perfil de compatibilidade do Free Fire e do dispositivo (somente leitura)..."
        worker.execute {
            fun read(command: String): String = try {
                AtomicShizukuClient.run(command).ifBlank { "(sem saída)" }
            } catch (e: Exception) {
                "BLOQUEADO/ERRO: " + (e.message ?: "sem detalhes")
            }
            val report = StringBuilder()
            report.appendLine("===== ATOMIC 120 HZ — IDENTIFICAÇÃO DO PERFIL DO FREE FIRE =====")
            report.appendLine("Coleta somente leitura. Nenhum arquivo, configuração, serviço ou proteção foi alterado.")
            report.appendLine()
            report.appendLine("===== 1. IDENTIDADE / VERSÃO / COMPATIBILIDADE DO PACOTE =====")
            report.appendLine(read("dumpsys package com.dts.freefireth 2>&1 | grep -iE 'versionName|versionCode|targetSdk|isGame|appCategory|category|supportsPerformanceGameMode|supportsBatteryGameMode|game_mode_config|primaryCpuAbi|secondaryCpuAbi|codePath|splitNames|flags=' | head -n 140"))
            report.appendLine()
            report.appendLine("===== 2. DECLARAÇÃO DO JOGO NO ANDROID =====")
            report.appendLine(read("cmd game list 2>&1; echo '--- GAME SERVICE ---'; dumpsys game 2>&1 | grep -i -A 14 -B 5 -E 'com.dts.freefireth|game.?mode|fps|frame.?rate|intervention' | head -n 180"))
            report.appendLine()
            report.appendLine("===== 3. PERFIL / INTERVENÇÕES CONFIGURADAS PELO SISTEMA =====")
            report.appendLine("game_overlay do pacote: " + read("device_config get game_overlay com.dts.freefireth 2>&1"))
            report.appendLine(read("device_config list game_overlay 2>&1 | grep -i -A 4 -B 3 'com.dts.freefireth' | head -n 100"))
            report.appendLine(read("dumpsys game_manager 2>&1 | grep -i -A 14 -B 5 -E 'com.dts.freefireth|game.?mode|intervention|fps|frame.?rate|downscale' | head -n 180"))
            report.appendLine()
            report.appendLine("===== 4. IDENTIFICAÇÃO DO DISPOSITIVO / SOC / KERNEL =====")
            report.appendLine(read("getprop ro.product.manufacturer; getprop ro.product.brand; getprop ro.product.model; getprop ro.product.device; getprop ro.product.board; getprop ro.board.platform; getprop ro.hardware; getprop ro.soc.manufacturer; getprop ro.soc.model; getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.opengles.version; uname -a"))
            report.appendLine()
            report.appendLine("===== 5. GPU / RENDERIZADOR =====")
            report.appendLine(read("dumpsys SurfaceFlinger 2>&1 | grep -iE 'GLES:|GL_VENDOR|GL_RENDERER|GPU|renderer|RenderEngine' | head -n 90"))
            report.appendLine()
            report.appendLine("===== 6. ARQUITETURA E BIBLIOTECAS NATIVAS INSTALADAS =====")
            report.appendLine(read("dumpsys package com.dts.freefireth 2>&1 | grep -iE 'primaryCpuAbi|secondaryCpuAbi|nativeLibraryDir|splitNames|versionName|versionCode' | head -n 80; echo '--- APK SPLITS ---'; pm path com.dts.freefireth 2>&1"))
            report.appendLine()
            report.appendLine("===== 7. TAXAS SUPORTADAS E MODO ATIVO DO DISPLAY =====")
            report.appendLine(read("dumpsys display 2>&1 | grep -iE -A 3 -B 3 'mActiveSfDisplayMode|mActiveRenderFrameRate|mActiveModeId|defaultModeId|defaultPeakRefreshRate|mDesiredDisplayModeSpecs|mDisplayModeSpecs|mRefreshRateLimitations|mRefreshRateThrottling|renderFrameRate|supportedModes' | head -n 200"))
            report.appendLine()
            report.appendLine("===== 8. POLÍTICAS DO FABRICANTE RELACIONADAS A JOGOS / FPS =====")
            report.appendLine(read("getprop 2>/dev/null | grep -iE 'game|fps|frame.?rate|refresh|thermal|performance|power.?mode|gpu' | head -n 180"))
            report.appendLine()
            report.appendLine("===== 9. ESTADO TÉRMICO E ECONOMIA DE ENERGIA =====")
            report.appendLine(read("dumpsys thermalservice 2>&1 | head -n 100; echo '--- POWER ---'; dumpsys power 2>&1 | grep -iE 'low.?power|power.?save|thermal|mode|interactive' | head -n 100"))
            report.appendLine()
            report.appendLine("===== 10. PROCESSO / CAMADAS DO FREE FIRE (SE ABERTO) =====")
            report.appendLine(read("pidof com.dts.freefireth 2>&1; echo '--- SURFACES ---'; dumpsys SurfaceFlinger --list 2>&1 | grep -iE 'freefire|FFMainActivity|SurfaceView' | head -n 100"))
            report.appendLine()
            report.appendLine("===== COMO INTERPRETAR =====")
            report.appendLine("A seção 1 verifica se o pacote se declara como jogo e se anuncia suporte aos modos de desempenho do Android.")
            report.appendLine("A seção 3 mostra intervenções do Game Manager, distintas do perfil interno de compatibilidade da Garena.")
            report.appendLine("As seções 4 a 6 identificam modelo, SoC, GPU e ABI, sinais que podem participar da compatibilidade.")
            report.appendLine("Se o perfil interno da Garena vier de tabela remota ou código binário, esta coleta não prova sua regra exata.")
            report.appendLine("Não tenta ler dados privados do jogo, contornar sandbox, alterar APK, assinatura, anti-cheat ou limites térmicos.")
            runOnUiThread {
                diagnosticReport.text = report.toString().take(24000)
                status.text = "Identificação concluída. Copie o relatório completo e envie aqui para cruzarmos os sinais do perfil."
            }
        }
    }

    /** Varredura unificada e somente leitura. */
    private fun runCompleteFreeFireAudit() {
        if (!hasShizukuPermission()) return
        status.text = "Executando diagnóstico completo. Deixe o Free Fire aberto..."
        worker.execute {
            fun read(command: String): String = try {
                AtomicShizukuClient.run(command).ifBlank { "(sem saída)" }
            } catch (e: Exception) {
                "INDISPONÍVEL/BLOQUEADO: " + (e.message ?: e.javaClass.simpleName)
            }
            val out = StringBuilder()
            fun section(title: String, command: String) {
                out.appendLine()
                out.appendLine("========== " + title + " ==========")
                out.appendLine(read(command))
            }
            out.appendLine("ATOMIC 120 HZ — DIAGNÓSTICO UNIFICADO DO FREE FIRE")
            out.appendLine("Somente leitura. Não altera o jogo, APK, assinatura, anti-cheat, SELinux ou proteções térmicas.")
            out.appendLine("Coleta pontual: mantenha o Free Fire aberto e, de preferência, em uma partida.")
            section("01 — APARELHO / ANDROID / SOC",
                "getprop ro.product.manufacturer; getprop ro.product.brand; getprop ro.product.model; getprop ro.product.device; getprop ro.product.board; getprop ro.board.platform; getprop ro.hardware; getprop ro.soc.manufacturer; getprop ro.soc.model; getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.build.fingerprint; getprop ro.opengles.version; uname -a")
            section("02 — PACOTE / VERSÃO / MANIFESTO / SPLITS",
                "dumpsys package com.dts.freefireth 2>&1 | grep -iE 'versionName|versionCode|targetSdk|isGame|appCategory|category|supportsPerformanceGameMode|supportsBatteryGameMode|game_mode_config|primaryCpuAbi|secondaryCpuAbi|nativeLibraryDir|codePath|splitNames|dataDir|flags=' | head -n 180; echo '--- APK PATHS ---'; pm path com.dts.freefireth 2>&1")
            section("03 — GAME MANAGER / MODOS / INTERVENÇÕES",
                "cmd game list 2>&1; echo '--- GAME OVERLAY ---'; device_config get game_overlay com.dts.freefireth 2>&1; echo '--- GAME DUMP ---'; dumpsys game 2>&1 | grep -i -A 16 -B 5 -E 'com.dts.freefireth|game.?mode|fps|frame.?rate|intervention' | head -n 220; echo '--- GAME MANAGER ---'; dumpsys game_manager 2>&1 | grep -i -A 16 -B 5 -E 'com.dts.freefireth|game.?mode|intervention|fps|frame.?rate|downscale' | head -n 220; echo '--- DEVICE CONFIG ---'; device_config list game_overlay 2>&1 | grep -i -A 4 -B 3 'com.dts.freefireth' | head -n 100")
            section("04 — SERVIÇOS / PACOTES DE JOGOS E FABRICANTE",
                "echo '--- SERVICES ---'; service list 2>&1 | grep -iE 'game|booster|perf|power|thermal|display|refresh|miui|xiaomi|mediatek|mtk' | head -n 180; echo '--- DUMPSYS SERVICES ---'; dumpsys -l 2>&1 | grep -iE 'game|booster|perf|power|thermal|display|refresh|miui|xiaomi|mediatek|mtk' | head -n 180; echo '--- SYSTEM PACKAGES ---'; pm list packages -s 2>&1 | grep -iE 'game|booster|perf|power|thermal|display|refresh|miui|xiaomi|mediatek|mtk' | head -n 180")
            section("04A — INVESTIGAÇÃO DIRETA DO MIUIBOOSTERSERVICE",
                "echo '--- SERVICE CHECK ---'; service check miuiboosterservice 2>&1; echo '--- DUMPSYS DIRETO ---'; dumpsys miuiboosterservice 2>&1 | head -n 260; echo '--- SERVIÇOS COM BOOSTER ---'; service list 2>&1 | grep -i -A 2 -B 2 'miuibooster'; dumpsys -l 2>&1 | grep -i -A 2 -B 2 'miuibooster'; echo '--- PACOTES XIAOMI RELACIONADOS ---'; pm list packages -s 2>&1 | grep -iE 'booster|powerkeeper|securitycenter|game' | head -n 120; echo '--- PROCESSOS RELACIONADOS ---'; ps -A 2>&1 | grep -iE 'booster|powerkeeper|gamebooster|com.dts.freefireth|freefire' | head -n 140")
            section("04B — REGISTROS RECENTES DE INTERVENÇÃO (LEITURA)",
                "echo '--- LOGCAT: MIUI BOOSTER / FREE FIRE / FPS ---'; logcat -d -t 3000 2>&1 | grep -iE 'miuibooster|miui.?booster|game.?booster|powerkeeper|com.dts.freefireth|freefire|frame.?rate|fps' | tail -n 260; echo '--- GAME MANAGER ASSOCIADO AO PACOTE ---'; dumpsys game 2>&1 | grep -i -A 18 -B 6 'com.dts.freefireth' | head -n 100; echo '--- POWER HINTS ---'; dumpsys power 2>&1 | grep -iE -A 3 -B 3 'hint|boost|game|performance|interactive' | head -n 100")
            section("05 — PROPRIEDADES HYPEROS / POLÍTICAS FPS",
                "getprop 2>/dev/null | grep -iE 'game|fps|frame.?rate|refresh|thermal|performance|power.?mode|gpu|miui|xiaomi|mediatek|mtk' | head -n 240; echo '--- GAME OVERLAY CONFIG ---'; device_config list game_overlay 2>&1 | head -n 180")
            section("06 — DISPLAY / MODOS / LIMITES ATIVOS",
                "dumpsys display 2>&1 | grep -iE -A 4 -B 4 'mActiveSfDisplayMode|mActiveRenderFrameRate|mActiveModeId|defaultModeId|defaultPeakRefreshRate|mDesiredDisplayModeSpecs|mDisplayModeSpecs|mRefreshRateLimitations|mRefreshRateThrottling|renderFrameRate|supportedModes|thermal' | head -n 260; echo '--- SYSTEM SETTINGS ---'; settings list system 2>&1 | grep -iE 'refresh|fps|frame|miui|game|power' | head -n 120; echo '--- GLOBAL SETTINGS ---'; settings list global 2>&1 | grep -iE 'refresh|fps|frame|game|power' | head -n 120")
            section("07 — GPU / OPENGL / SURFACEFLINGER / CAMADAS",
                "dumpsys SurfaceFlinger 2>&1 | grep -iE 'GLES:|GL_VENDOR|GL_RENDERER|GPU|renderer|RenderEngine|refresh|frame.?rate|composition' | head -n 180; echo '--- LAYERS ---'; dumpsys SurfaceFlinger --list 2>&1 | grep -iE 'freefire|com.dts.freefireth|SurfaceView|Unity|Game' | head -n 160; echo '--- DISPLAY ID ---'; dumpsys SurfaceFlinger --display-id 2>&1 | head -n 80")
            section("08 — PROCESSOS / PID / THREADS / CPU / MEMÓRIA",
                "echo '--- PID ---'; pidof com.dts.freefireth 2>&1; echo '--- PROCESS LIST ---'; ps -A 2>&1 | grep -iE 'com.dts.freefireth|freefire|surfaceflinger|game|booster|perf|thermal' | head -n 180; echo '--- THREADS ---'; ps -T -A 2>&1 | grep -iE 'com.dts.freefireth|freefire' | head -n 160; echo '--- CPUINFO ---'; dumpsys cpuinfo 2>&1 | grep -i -A 3 -B 2 -E 'freefire|surfaceflinger|game' | head -n 100; echo '--- MEMINFO ---'; dumpsys meminfo com.dts.freefireth 2>&1 | head -n 100")
            section("09 — MÉTRICAS DE FRAMES / GFXINFO",                "dumpsys gfxinfo com.dts.freefireth 2>&1 | head -n 260; echo '--- SURFACEFLINGER TIMESTATS ---'; dumpsys SurfaceFlinger --timestats -dump 2>&1 | head -n 220; echo '--- SUPORTE LATENCY ---'; dumpsys SurfaceFlinger --help 2>&1 | grep -iE 'latency|timestats|frame' | head -n 80")
            section("10 — TEMPERATURA / ENERGIA / THROTTLING",
                "dumpsys thermalservice 2>&1 | head -n 180; echo '--- POWER ---'; dumpsys power 2>&1 | grep -iE 'low.?power|power.?save|thermal|mode|interactive|battery' | head -n 160; echo '--- BATTERY ---'; dumpsys battery 2>&1 | head -n 100; echo '--- CPUFREQ CPU0 CURRENT ---'; cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>&1; echo '--- CPUFREQ CPU0 MAX ---'; cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq 2>&1; echo '--- KGSL GPU CURRENT ---'; cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq 2>&1; echo '--- KGSL GPU MAX ---'; cat /sys/class/kgsl/kgsl-3d0/devfreq/max_freq 2>&1")
            section("11 — CONFIGURAÇÕES EXTERNAS DO JOGO",
                "echo '--- EXTERNAL GAME FILES ---'; ls -la /sdcard/Android/data/com.dts.freefireth 2>&1 | head -n 100; echo '--- OBB FILES ---'; ls -la /sdcard/Android/obb/com.dts.freefireth 2>&1 | head -n 100; echo '--- CONFIG FILES ---'; find /sdcard/Android/data/com.dts.freefireth -type f -size -2M ! -path '*/cache/*' \\( -iname '*.ini' -o -iname '*.cfg' -o -iname '*.conf' -o -iname '*.json' -o -iname '*.xml' -o -iname '*.txt' -o -iname '*.config' -o -iname '*.properties' -o -iname '*.dat' \\) -print 2>&1 | head -n 180; echo '--- FPS TEXT HITS ---'; find /sdcard/Android/data/com.dts.freefireth -type f -size -2M ! -path '*/cache/*' -iname '*.json' -exec grep -IinE 'fps|frame.?rate|refresh.?rate|target.?frame|60[[:space:]]*fps|90[[:space:]]*fps' {} \\; 2>/dev/null | head -n 160")
            out.appendLine()
            out.appendLine("========== 12 — VARREDURA ESTÁTICA DOS APKs ==========")
            try {
                val info = packageManager.getApplicationInfo("com.dts.freefireth", 0)
                val paths = (listOfNotNull(info.sourceDir) + (info.splitSourceDirs?.toList() ?: emptyList())).distinct()
                val terms = Regex("(?i)fps|frame.?rate|refresh.?rate|target.?frame|target.?fps|high.?frame|frame.?limit|render.?rate|quality.?level")
                val candidates = mutableListOf<String>()
                val hits = mutableListOf<String>()
                var entries = 0
                var bytesScanned = 0L
                val budget = 128L * 1024 * 1024
                for ((index, path) in paths.withIndex()) {
                    out.appendLine("APK " + (index + 1) + ": " + path + " (" + java.io.File(path).length() + " bytes)")
                    java.util.zip.ZipFile(path).use { zip ->
                        val all = zip.entries()
                        while (all.hasMoreElements()) {
                            val entry = all.nextElement()
                            entries++
                            if (entry.isDirectory) continue
                            val name = entry.name
                            val low = name.lowercase()
                            val config = low.contains("config") || low.contains("resconf") || low.contains("setting") ||
                                low.contains("quality") || low.contains("frame") || low.contains("fps") ||
                                low.endsWith(".json") || low.endsWith(".xml") || low.endsWith(".ini") ||
                                low.endsWith(".cfg") || low.endsWith(".conf") || low.endsWith(".properties")
                            if (config && candidates.size < 160) candidates.add("APK " + (index + 1) + ": " + name + " (" + entry.size + " bytes)")
                            val dex = low.endsWith(".dex")
                            if ((!config && !dex) || entry.size !in 1..(8L * 1024 * 1024) || bytesScanned >= budget) continue
                            val maxRead = minOf(entry.size, budget - bytesScanned, 8L * 1024 * 1024).toInt()
                            if (maxRead <= 0) continue
                            val data = zip.getInputStream(entry).use { input ->
                                val buffer = java.io.ByteArrayOutputStream()
                                val chunk = ByteArray(8192)
                                var count = 0
                                while (count < maxRead) {
                                    val got = input.read(chunk, 0, minOf(chunk.size, maxRead - count))
                                    if (got <= 0) break
                                    buffer.write(chunk, 0, got)
                                    count += got
                                }
                                buffer.toByteArray()
                            }
                            bytesScanned += data.size
                            val content = String(data, Charsets.ISO_8859_1)
                            val matches = terms.findAll(content).take(12).toList()
                            if (matches.isNotEmpty()) {
                                val contexts = matches.take(5).map { m ->
                                    val from = (m.range.first - 90).coerceAtLeast(0)
                                    val to = (m.range.last + 110).coerceAtMost(content.length)
                                    content.substring(from, to).replace(Regex("[\\x00-\\x1F]+"), " ").replace(Regex("\\s+"), " ").trim().take(220)
                                }.distinct()
                                hits.add("APK " + (index + 1) + " [" + name + "]: " + contexts.joinToString(" || "))
                            }
                        }
                    }
                }
                out.appendLine("APKs: " + paths.size + "; entradas ZIP: " + entries + "; bytes lidos: " + bytesScanned + " / orçamento " + budget + ".")
                out.appendLine("--- RECURSOS CANDIDATOS ---")
                candidates.forEach { out.appendLine(it) }
                out.appendLine("--- REFERÊNCIAS TEXTUAIS ---")
                if (hits.isEmpty()) out.appendLine("Nenhuma referência textual encontrada no conteúdo efetivamente lido.")
                else hits.take(100).forEach { out.appendLine(it) }
                out.appendLine("Dados binários/compactados e conteúdo além do orçamento podem exigir análise especializada; strings não provam limite ativo.")
            } catch (e: Exception) {
                out.appendLine("Falha na análise estática: " + e.javaClass.simpleName + ": " + e.message)
            }
            out.appendLine()
            out.appendLine("========== LIMITES DA COLETA ==========")
            out.appendLine("Resultados bloqueados/ausentes refletem permissões ou diferenças da ROM. O limitador pode estar no motor Unity, biblioteca nativa, configuração remota Garena, Game Manager, compositor ou driver; pode não existir um processo separado.")
            out.appendLine("Não injeta código, não lê memória privada, não contorna sandbox e não modifica APK, anti-cheat, assinatura, SELinux ou proteção térmica.")
            runOnUiThread {
                diagnosticReport.text = out.toString().take(48000)
                status.text = "Diagnóstico completo concluído. Toque em COPIAR RELATÓRIO e envie o texto aqui."
            }
        }
    }

    private fun force90Hz() {
        if (!hasShizukuPermission()) return
        status.text = "Salvando valores atuais e solicitando 90 Hz em todas as preferências compatíveis..."
        worker.execute {
            try {
                val systemKeys = listOf("min_refresh_rate", "peak_refresh_rate", "user_refresh_rate", "user_peak_refresh_rate", "miui_refresh_rate")
                val prefs = getSharedPreferences("atomic_refresh_backup", MODE_PRIVATE)
                systemKeys.forEach { key ->
                    val old = AtomicShizukuClient.run("settings get system $key 2>&1 || true").trim()
                    if (!prefs.contains(key)) prefs.edit().putString(key, old).apply()
                }
                val oldGlobal = AtomicShizukuClient.run("settings get global user_preferred_refresh_rate 2>&1 || true").trim()
                if (!prefs.contains("global_user_preferred_refresh_rate")) prefs.edit().putString("global_user_preferred_refresh_rate", oldGlobal).apply()
                val writes = systemKeys.joinToString(" ; ") { "settings put system $it 90 2>&1" } +
                    " ; settings put global user_preferred_refresh_rate 90 2>&1"
                val verify = systemKeys.joinToString(" ; ") { "echo $it=\$(settings get system $it)" } +
                    " ; echo global_user_preferred_refresh_rate=\$(settings get global user_preferred_refresh_rate)"
                val result = AtomicShizukuClient.run(
                    "$writes ; echo '--- PREFERÊNCIAS GRAVADAS ---' ; $verify" +
                    " ; echo '--- ESTADO REAL DO DISPLAY ---' ; dumpsys display | grep -iE 'mActiveSfDisplayMode|mActiveRenderFrameRate|mActiveModeId|mDesiredDisplayModeSpecs|mDisplayModeSpecs|renderFrameRate|mRefreshRateThrottling' | head -n 55" +
                    " ; echo '--- PERFIL DE FPS DO FREE FIRE (LEITURA) ---' ; dumpsys game 2>&1 | grep -i -A 8 -B 3 'com.dts.freefireth' | head -n 45"
                )
                runOnUiThread {
                    status.text = "Preferências compatíveis de 90 Hz gravadas. Limites de jogo, hardware e temperatura continuam sob controle do sistema."
                    diagnosticReport.text = "ATOMIC 120 HZ — APLICAÇÃO AMPLIADA DE 90 HZ\n" +
                        "Valores anteriores salvos para restauração. Nenhum limite térmico foi alterado; políticas internas do jogo/HyperOS não são sobrescritas.\n\n" + result
                    refreshDisplayInfo()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Falha ao solicitar 90 Hz: ${e.message}" }
            }
        }
    }

    private fun requestNative90Hz() {
        if (!hasShizukuPermission()) return
        status.text = "Verificando a resolução física e aplicando as preferências de 90 Hz..."
        worker.execute {
            try {
                // Apenas lê a resolução física; nunca altera wm size.
                val sizeOutput = AtomicShizukuClient.run("wm size 2>&1 || true")
                val physicalMatch = Regex("(?im)^Physical size:\\s*(\\d+)x(\\d+)").find(sizeOutput)
                    ?: Regex("(?m)^(\\d+)x(\\d+)$").find(sizeOutput)
                    ?: throw IOException("Não foi possível ler a resolução física. Saída: $sizeOutput")
                val width = physicalMatch.groupValues[1]
                val height = physicalMatch.groupValues[2]
                val prefs = getSharedPreferences("atomic_refresh_backup", MODE_PRIVATE)
                val systemKeys = listOf(
                    "min_refresh_rate", "peak_refresh_rate", "user_refresh_rate",
                    "user_peak_refresh_rate", "miui_refresh_rate"
                )
                systemKeys.forEach { key ->
                    if (!prefs.contains(key)) {
                        val old = AtomicShizukuClient.run("settings get system $key 2>&1 || true").trim()
                        prefs.edit().putString(key, old).apply()
                    }
                }
                if (!prefs.contains("global_user_preferred_refresh_rate")) {
                    val old = AtomicShizukuClient.run("settings get global user_preferred_refresh_rate 2>&1 || true").trim()
                    prefs.edit().putString("global_user_preferred_refresh_rate", old).apply()
                }

                val before = AtomicShizukuClient.run("cmd display get-user-preferred-display-mode 2>&1 || true")
                val nativeAttempt = try {
                    AtomicShizukuClient.run("cmd display set-user-preferred-display-mode $width $height 90 2>&1")
                } catch (e: Exception) {
                    "O HyperOS recusou o comando nativo: ${e.message}"
                }

                // Fallback: preferências de taxa já identificadas no Redmi/HyperOS.
                // Não altera resolução, não desativa serviços e não remove proteção térmica.
                val writes = systemKeys.joinToString(" ; ") {
                    "settings put system $it 90 2>&1 || true"
                } + " ; settings put global user_preferred_refresh_rate 90 2>&1 || true"
                val verify = systemKeys.joinToString(" ; ") {
                    "echo $it=\$(settings get system $it 2>&1)"
                } + " ; echo global_user_preferred_refresh_rate=\$(settings get global user_preferred_refresh_rate 2>&1)"
                val fallbackResult = AtomicShizukuClient.run(
                    "$writes ; echo '--- CONFERENCIA DAS PREFERENCIAS ---' ; $verify"
                )
                val after = AtomicShizukuClient.run("cmd display get-user-preferred-display-mode 2>&1 || true")
                val state = AtomicShizukuClient.run(
                    "dumpsys display 2>&1 | grep -iE 'mActiveSfDisplayMode|mActiveRenderFrameRate|mActiveModeId|mDesiredDisplayModeSpecs|mDisplayModeSpecs|mRefreshRateThrottling|renderFrameRate|supportedModes' | head -n 100"
                )
                runOnUiThread {
                    diagnosticReport.text = "RESOLUÇÃO NATIVA + SOLICITAÇÃO DE 90 HZ\n" +
                        "Resolução física lida: $width x $height (nenhuma alteração de resolução foi executada).\n\n" +
                        "TENTATIVA DO DISPLAYMANAGER:\n$nativeAttempt\n\n" +
                        "PREFERÊNCIA NATIVA ANTES:\n$before\n\n" +
                        "PREFERÊNCIA NATIVA DEPOIS:\n$after\n\n" +
                        "$fallbackResult\n\nESTADO REAL DO DISPLAY:\n$state"
                    status.text = "Preferências de 90 Hz reaplicadas. Confira no relatório se o HyperOS ativou 90 Hz; a resolução foi preservada."
                    refreshDisplayInfo()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    diagnosticReport.text = "Falha ao solicitar 90 Hz: ${e.message}"
                    status.text = "Não foi possível concluir a solicitação."
                }
            }
        }
    }

    private fun startFpsDiagnostic() {
        if (!hasShizukuPermission()) return
        diagnosticStartButton.isEnabled = false
        diagnosticReport.text = "Iniciando captura do SurfaceFlinger..."
        worker.execute {
            try {
                val output = AtomicShizukuClient.run("dumpsys SurfaceFlinger --timestats -clear && dumpsys SurfaceFlinger --timestats -enable")
                runOnUiThread {
                    diagnosticStartButton.isEnabled = true
                    diagnosticReport.text = "MEDIÇÃO INICIADA. Agora volte ao Free Fire, jogue por aproximadamente 30 segundos e retorne ao Atomic. Depois toque em FINALIZAR.\\n\\n$output"
                    status.text = "Captura ativa. Jogue por 30 segundos e volte para finalizar."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    diagnosticStartButton.isEnabled = true
                    diagnosticReport.text = "Não foi possível iniciar a captura: ${e.message}\\nSeu Android pode não oferecer essa opção do SurfaceFlinger."
                }
            }
        }
    }

    private fun finishFpsDiagnostic() {
        if (!hasShizukuPermission()) return
        diagnosticStopButton.isEnabled = false
        diagnosticReport.text = "Lendo os quadros capturados..."
        worker.execute {
            try {
                val dump = AtomicShizukuClient.run("dumpsys SurfaceFlinger --timestats -dump")
                try {
                    AtomicShizukuClient.run("dumpsys SurfaceFlinger --timestats -disable")
                } catch (_: Exception) { }
                val report = dump.take(14000)
                val gameMentioned = dump.contains("com.dts.freefireth", ignoreCase = true) ||
                    dump.contains("freefire", ignoreCase = true)
                runOnUiThread {
                    diagnosticStopButton.isEnabled = true
                    diagnosticReport.text = (if (gameMentioned) {
                        "O relatório menciona o Free Fire. Confira as camadas e contagens abaixo.\\n\\n"
                    } else {
                        "O nome do Free Fire não apareceu claramente no relatório. Isso não prova ausência de captura; confira as camadas listadas.\\n\\n"
                    }) + report
                    status.text = "Diagnóstico finalizado. Envie uma captura deste relatório para análise."
                }
            } catch (e: Exception) {
                try { AtomicShizukuClient.run("dumpsys SurfaceFlinger --timestats -disable") } catch (_: Exception) { }
                runOnUiThread {
                    diagnosticStopButton.isEnabled = true
                    diagnosticReport.text = "Falha ao ler o relatório: ${e.message}"
                }
            }
        }
    }

    private fun copyDiagnosticLog() {
        val report = diagnosticReport.text?.toString().orEmpty()
        if (report.isBlank() || report == "O relatório aparecerá aqui.") {
            status.text = "Gere o relatório primeiro para copiar o log."
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Atomic 120 Hz - Log FPS", report))
        status.text = "Log copiado! Agora é só colar aqui na conversa."
    }

    private fun restoreSettings() {
        if (!hasShizukuPermission()) return
        toggle.isEnabled = false
        worker.execute {
            try {
                val prefs = getSharedPreferences("atomic_refresh_backup", MODE_PRIVATE)
                val keys = listOf("min_refresh_rate", "peak_refresh_rate", "user_refresh_rate", "miui_refresh_rate", "thermal_limit_refresh_rate")
                val restoreCommands = keys.mapNotNull { key ->
                    if (!prefs.contains(key)) null
                    else {
                        val old = prefs.getString(key, "null") ?: "null"
                        if (old == "null") "settings delete system $key" else "settings put system $key $old"
                    }
                }.toMutableList()
                if (prefs.contains("global_user_preferred_refresh_rate")) {
                    val oldGlobal = prefs.getString("global_user_preferred_refresh_rate", "null") ?: "null"
                    restoreCommands.add(if (oldGlobal == "null") "settings delete global user_preferred_refresh_rate" else "settings put global user_preferred_refresh_rate $oldGlobal")
                }
                if (restoreCommands.isNotEmpty()) {
                    AtomicShizukuClient.run(restoreCommands.joinToString(" && ") + " && cmd display clear-user-preferred-display-mode 2>/dev/null || true")
                    prefs.edit().clear().apply()
                } else {
                    AtomicShizukuClient.run("settings delete system min_refresh_rate && settings delete system peak_refresh_rate")
                }
                runOnUiThread {
                    enabled = false
                    toggle.isEnabled = true
                    status.text = "Preferências removidas. O sistema voltou a controlar a frequência."
                    refreshDisplayInfo()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    toggle.isEnabled = true
                    status.text = "Falha ao restaurar: ${e.message}"
                }
            }
        }
    }

    private fun hasShizukuPermission(): Boolean {
        val alive = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        val granted = alive && try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false }
        shizukuReady = granted
        updateConnectionUi()
        if (!granted) {
            status.text = if (alive) {
                "Solicitando autorização do Shizuku. Aceite a janela de permissão e toque novamente na função."
            } else {
                "Inicie o serviço Shizuku por Depuração sem fio antes de continuar."
            }
            if (alive) {
                try { Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE) } catch (_: Exception) { }
            }
        }
        return granted
    }

    private fun openShizukuDownload() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE")))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
        }
    }

    private fun choose(hz: Int) {
        selectedHz = hz
        executeButton.text = "EXECUTAR COMANDOS PARA $hz FPS"
        updateButtons()
        status.text = "Selecionado: $hz Hz. Conecte o Shizuku para aplicar."
    }

    private fun refreshDisplayInfo() {
        val display = window.decorView.display
        val modes = display?.supportedModes?.map { it.refreshRate.toInt().toString() }?.distinct()
        val current = display?.refreshRate
        detected.text = "FREQUÊNCIA REPORTADA\n" +
            (current?.let { "${it.toInt()} Hz" } ?: "Indisponível") +
            "\n\nModos informados: " + (modes?.joinToString(" / ")?.plus(" Hz") ?: "não disponíveis")
    }

    private fun updateConnectionUi() {
        if (!::connectButton.isInitialized || !::executeButton.isInitialized) return
        connectButton.text = if (shizukuReady) "SHIZUKU CONECTADO E AUTORIZADO" else "CONECTAR AO SHIZUKU / AUTORIZAR"
        executeButton.isEnabled = shizukuReady
        executeButton.alpha = if (shizukuReady) 1f else .55f
    }

    private fun updateButtons() {
        if (!::hz90.isInitialized) return
        hz90.alpha = if (selectedHz == 90) 1f else .55f
        hz120.alpha = if (selectedHz == 120) 1f else .55f
    }

    private fun stepTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 17f
        gravity = Gravity.CENTER
        setTextColor(0xFF55C9FF.toInt())
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(0xFF174568.toInt())
        setOnClickListener { action() }
    }

    private fun buttonParams(height: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(height)
    )

    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun match() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SHIZUKU_PERMISSION_CODE = 120
    }
}