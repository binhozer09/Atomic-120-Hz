package com.atomic120hz

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import android.text.InputType
import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var selectedHz = 90
    private var adbReady = false
    private var busy = false
    private var adb: Kadb? = null
    private lateinit var logView: TextView
    private lateinit var pairButton: Button
    private lateinit var modeButton: Button
    private lateinit var hz90: Button
    private lateinit var hz120: Button
    private lateinit var terminalInput: EditText
    private lateinit var terminalButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 15, 29)
        window.navigationBarColor = Color.rgb(5, 15, 29)
        buildUi()
        handleNotificationCode(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationCode(intent)
    }

    private fun handleNotificationCode(source: Intent?) {
        val code = source?.getStringExtra(PairCodeReceiver.EXTRA_CODE) ?: return
        val pairingPort = source.getIntExtra(PairCodeReceiver.EXTRA_PORT, -1)
        val pairingHost = source.getStringExtra(PairCodeReceiver.EXTRA_HOST)
        source.removeExtra(PairCodeReceiver.EXTRA_CODE)
        source.removeExtra(PairCodeReceiver.EXTRA_PORT)
        source.removeExtra(PairCodeReceiver.EXTRA_HOST)
        appendLog("Código recebido pela notificação. Endpoint descoberto: $pairingHost:$pairingPort.")
        pairAndConnect(code, pairingHost ?: "", pairingPort)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestPairingCodeNotification()
                appendLog("Permissão concedida. Iniciando descoberta mDNS antes de solicitar o código.")
            } else {
                appendLog("Permissão de notificações negada. Ative as notificações do Atomic para enviar o código pelo painel.")
            }
        }
    }

    private fun requestPairingCodeNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        appendLog("Iniciando descoberta mDNS do Shizuku: procurando primeiro a porta de pareamento…")
        pairButton.isEnabled = false
        thread {
            try {
                val endpoint = discoverAdbEndpoint("_adb-tls-pairing._tcp.")
                val pairingHost = endpoint.first
                val pairingPort = endpoint.second
                appendFromWorker("Endpoint de pareamento identificado: $pairingHost:$pairingPort. Preparando notificação.")
                runOnUiThread {
                    showPairingNotification(pairingHost, pairingPort)
                    appendLog("Serviço ADB encontrado. Digite os 6 dígitos na notificação e toque em ENVIAR CÓDIGO.")
                    pairButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendLog("NÃO FOI POSSÍVEL DESCOBRIR A PORTA DE PAREAMENTO: ${e.message}")
                    pairButton.isEnabled = true
                }
            }
        }
    }

    private fun showPairingNotification(pairingHost: String, pairingPort: Int) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(PAIR_CHANNEL, "Pareamento Atomic Access", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Solicitação do código de pareamento da Depuração sem fio"
                    setShowBadge(true)
                }
            )
        }
        val remoteInput = RemoteInput.Builder(PairCodeReceiver.KEY_CODE)
            .setLabel("Digite o código de 6 dígitos")
            .build()
        val receiverIntent = Intent(this, PairCodeReceiver::class.java).setPackage(packageName)
            .putExtra(PairCodeReceiver.EXTRA_HOST, pairingHost)
            .putExtra(PairCodeReceiver.EXTRA_PORT, pairingPort)
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val replyPendingIntent = PendingIntent.getBroadcast(this, 120, receiverIntent, pendingFlags)
        val action = Notification.Action.Builder(
            android.R.drawable.ic_menu_send, "ENVIAR CÓDIGO", replyPendingIntent
        ).addRemoteInput(remoteInput).build()
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this, 121, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, PAIR_CHANNEL)
            else Notification.Builder(this)
        val notification = builder
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Atomic Access • código de pareamento")
            .setContentText("Abra “Parear dispositivo com código” e responda aqui com os 6 dígitos.")
            .setStyle(Notification.BigTextStyle().bigText("No Android, entre em Opções do desenvolvedor > Depuração sem fio > Parear dispositivo com código. Digite os 6 números nesta notificação e toque em ENVIAR CÓDIGO."))
            .addAction(action)
            .setContentIntent(contentPending)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(PAIR_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1201
        private const val PAIR_CHANNEL = "atomic_pairing"
        private const val PAIR_NOTIFICATION_ID = 1202
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
        root.addView(label("PAREAMENTO AUTOMÁTICO", 16f, Color.WHITE, true), match())
        root.addView(label("1. Ative Opções do desenvolvedor e Depuração sem fio.\n2. Toque em LIBERAR ACESSO.\n3. Abra “Parear dispositivo com código” no Android.\n4. Digite os 6 números na notificação do Atomic e toque em ENVIAR CÓDIGO. O ADB será pareado e conectado automaticamente.", 13f, 0xFF9ABBD5.toInt()), match())
        root.addView(space(8))
        pairButton = Button(this).apply {
            text = "LIBERAR ACESSO"
            isAllCaps = false
            setOnClickListener { requestPairingCodeNotification() }
        }
        root.addView(pairButton, LinearLayout.LayoutParams(-1, dp(58)))
        root.addView(space(18))
        root.addView(label("FREQUÊNCIA DESEJADA", 14f, 0xFF55C9FF.toInt(), true), match())
        val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hz90 = Button(this).apply { text = "90 Hz"; isAllCaps = false; setOnClickListener { selectedHz = 90; updateChoices() } }
        hz120 = Button(this).apply { text = "120 Hz"; isAllCaps = false; setOnClickListener { selectedHz = 120; updateChoices() } }
        choices.addView(hz90, LinearLayout.LayoutParams(0, dp(50), 1f))
        choices.addView(hz120, LinearLayout.LayoutParams(0, dp(50), 1f))
        root.addView(choices, match())
        modeButton = Button(this).apply {
            text = "ATIVAR MODO"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { applyRefreshRate() }
        }
        root.addView(modeButton, LinearLayout.LayoutParams(-1, dp(62)))
        root.addView(space(12))
        root.addView(label("TERMINAL DE EXECUÇÃO", 14f, 0xFF55C9FF.toInt(), true), match())
        logView = TextView(this).apply {
            text = "Aguardando autorização ADB. Nenhum comando foi executado."
            textSize = 12f
            setTextColor(0xFFD2E8F8.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFF102943.toInt())
        }
        root.addView(logView, LinearLayout.LayoutParams(-1, dp(260)))
        terminalInput = EditText(this).apply {
            hint = "Digite um comando ADB shell"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF7894AD.toInt())
            textSize = 14f
            setSingleLine(true)
            backgroundTintList = ColorStateList.valueOf(0xFF55C9FF.toInt())
        }
        root.addView(terminalInput, match())
        terminalButton = Button(this).apply {
            text = "EXECUTAR COMANDO"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { executeTerminalCommand() }
        }
        root.addView(terminalButton, LinearLayout.LayoutParams(-1, dp(52)))
        setContentView(ScrollView(this).apply { addView(root) })
        updateChoices()
    }

    private fun pairAndConnect(code: String, pairingHost: String, pairingPort: Int) {
        if (busy) return
        if (!code.matches(Regex("\\d{6}")) || pairingHost.isBlank() || pairingPort !in 1..65535) {
            appendLog("Código ou endpoint de pareamento inválidos. Toque em LIBERAR ACESSO para redescobrir o serviço.")
            return
        }
        setBusy(true)
        appendLog("Iniciando descoberta mDNS do serviço ADB de pareamento…")
        thread {
            try {
                // Igual ao fluxo do Shizuku: o mDNS serve para validar o serviço e obter a porta,
                // mas o cliente de pareamento conecta ao adbd local pelo loopback.
                // Em HyperOS, usar o IP Wi-Fi anunciado pelo mDNS pode deixar o TLS aguardando.
                appendFromWorker("Pareamento ADB via loopback 127.0.0.1:$pairingPort (serviço mDNS validado em $pairingHost)…")
                runBlocking {
                    withTimeout(25_000) {
                        Kadb.pair("127.0.0.1", pairingPort, code)
                    }
                }
                appendFromWorker("Pareamento aceito. Procurando a porta segura de conexão…")
                val connectEndpoint = discoverAdbEndpoint("_adb-tls-connect._tcp.")
                appendFromWorker("Serviço de conexão identificado em ${connectEndpoint.first}:${connectEndpoint.second}. Abrindo sessão TLS…")
                val connection = Kadb.create(connectEndpoint.first, connectEndpoint.second)
                val check = connection.shell("echo ATOMIC_ADB_OK")
                if (check.exitCode != 0 || !check.allOutput.contains("ATOMIC_ADB_OK")) {
                    connection.close()
                    throw IllegalStateException("A conexão não confirmou o teste de shell: ${check.allOutput}")
                }
                adb?.close()
                adb = connection
                adbReady = true
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(PAIR_NOTIFICATION_ID)
                runOnUiThread {
                    appendLog("CONEXÃO CONFIRMADA: pareamento TLS concluído e teste de shell retornou ATOMIC_ADB_OK.")
                    pairButton.text = "ADB CONECTADO"
                    appendLog("Iniciando diagnóstico e configuração automática para 90 Hz…")
                }
                runAutomaticSetup(connection)
                runOnUiThread {
                    modeButton.isEnabled = true
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



    private fun appendFromWorker(message: String) {
        runOnUiThread { appendLog(message) }
    }

    /**
     * Descobre serviços ADB Wireless Debugging usando o NSD nativo do Android.
     * Assim o app não depende de um artefato mDNS externo.
     */

    @Suppress("DEPRECATION")
    private fun discoverAdbEndpoint(serviceType: String): Pair<String, Int> {
        val nsd = getSystemService(NSD_SERVICE) as NsdManager
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val multicastLock = wifi.createMulticastLock("Atomic120Hz:adb-mdns").apply {
            setReferenceCounted(false)
            acquire()
        }
        val latch = CountDownLatch(1)
        val endpoint = AtomicReference<Pair<String, Int>?>(null)
        val failure = AtomicReference<String?>(null)
        val resolving = java.util.concurrent.atomic.AtomicBoolean(false)

        fun normalizedType(value: String): String =
            value.trim().lowercase(java.util.Locale.ROOT)
                .removeSuffix(".local.")
                .removeSuffix(".local")
                .trimEnd('.')

        val expectedType = normalizedType(serviceType)
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                failure.set("Falha ao resolver serviço " + serviceInfo.serviceName + " (código " + errorCode + ").")
                latch.countDown()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val resolvedAddress = serviceInfo.host
                // Compare address bytes rather than InetAddress objects: IPv6 scope IDs can
                // differ even when mDNS resolved the phone's own interface address.
                val localHost = try {
                    if (resolvedAddress == null) null
                    else java.net.NetworkInterface.getNetworkInterfaces()
                        .asSequence()
                        .filter { it.isUp }
                        .flatMap { networkInterface -> networkInterface.inetAddresses.asSequence() }
                        .firstOrNull { it.address.contentEquals(resolvedAddress.address) }
                        ?.hostAddress
                } catch (_: Exception) { null }
                // Shizuku follows this same ADB-over-Wi-Fi behavior: mDNS is used
                // to discover the dynamic port, but the local adbd endpoint is reached
                // through loopback. Passing the WLAN address to Kadb can leave the TLS
                // handshake waiting forever on some HyperOS/Android builds.
                if (!localHost.isNullOrBlank() && serviceInfo.port in 1..65535) {
                    endpoint.set("127.0.0.1" to serviceInfo.port)
                } else {
                    failure.set("mDNS resolveu um endereço que não foi associado às interfaces locais ativas (resolvido=${resolvedAddress?.hostAddress}, porta=${serviceInfo.port}).")
                }
                latch.countDown()
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                appendFromWorker("mDNS iniciado para " + serviceType + ".")
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val foundType = normalizedType(serviceInfo.serviceType ?: "")
                appendFromWorker("Serviço de rede encontrado: " + serviceInfo.serviceName + " (" + serviceInfo.serviceType + ").")
                if (foundType == expectedType && resolving.compareAndSet(false, true)) {
                    appendFromWorker("Serviço ADB correspondente localizado. Resolvendo endereço IP e porta…")
                    try {
                        nsd.resolveService(serviceInfo, resolveListener)
                    } catch (e: Exception) {
                        failure.set("Não foi possível resolver o serviço mDNS: " + e.message)
                        latch.countDown()
                    }
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                appendFromWorker("Serviço mDNS saiu da rede: " + serviceInfo.serviceName + ".")
            }
            override fun onDiscoveryStopped(regType: String) {}
            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {
                failure.set("Falha ao iniciar descoberta mDNS (código " + errorCode + "). Verifique a Depuração sem fio e a rede Wi-Fi.")
                latch.countDown()
                try { nsd.stopServiceDiscovery(this) } catch (_: Exception) {}
            }
            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {
                failure.compareAndSet(null, "Falha ao encerrar descoberta mDNS (código " + errorCode + ").")
            }
        }

        try {
            appendFromWorker("Ativando recepção multicast para localizar a porta ADB automaticamente…")
            nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            if (!latch.await(25, TimeUnit.SECONDS)) {
                throw IllegalStateException("Tempo esgotado procurando " + serviceType + ". Nenhum endpoint foi resolvido. Confirme que a tela 'Parear dispositivo com código' continua aberta e que o Wi-Fi está ativo.")
            }
            val result = endpoint.get()
                ?: throw IllegalStateException(failure.get() ?: "Nenhum serviço ADB compatível foi encontrado por mDNS.")
            appendFromWorker("Endpoint ADB detectado: " + result.first + ":" + result.second + ".")
            return result
        } finally {
            try { nsd.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
            try { if (multicastLock.isHeld) multicastLock.release() } catch (_: Exception) {}
        }
    }

    /**
     * Rotina automática conservadora: coleta evidências reais do aparelho e só solicita
     * 90 Hz se o próprio dumpsys informar que há um modo de 90 Hz disponível.
     * Não tenta alterar limites internos do jogo nem propriedades protegidas do fabricante.
     */
    private fun runAutomaticSetup(connection: Kadb) {
        fun command(title: String, cmd: String): String {
            appendFromWorker(title)
            appendFromWorker("$ " + cmd)
            val result = connection.shell(cmd)
            appendFromWorker("SAÍDA:\n" + result.allOutput.ifBlank { "(sem saída)" } + "\nCÓDIGO DE SAÍDA: " + result.exitCode)
            if (result.exitCode != 0) {
                appendFromWorker("COMANDO RECUSADO/FALHOU (código ${result.exitCode}).")
                return ""
            }
            return result.allOutput.trim()
        }

        try {
            val androidVersion = command("Identificando versão do Android…", "getprop ro.build.version.release")
            val manufacturer = command("Identificando fabricante…", "getprop ro.product.manufacturer")
            val model = command("Identificando modelo…", "getprop ro.product.model")
            val hyperOs = command("Verificando versão HyperOS/MIUI…", "getprop ro.miui.ui.version.name")
            val peakBefore = command("Lendo limite máximo atual…", "settings get system peak_refresh_rate")
            val minBefore = command("Lendo limite mínimo atual…", "settings get system min_refresh_rate")
            val power = command("Verificando modo de economia de energia…", "settings get global low_power")
            val gamePath = command("Verificando instalação do Free Fire…", "pm path com.dts.freefireth")
            val displayDump = command("Lendo modos de exibição informados pelo Android…", "dumpsys display")
            val has90 = Regex("""(?i)(fps|refreshRate|refresh rate)[^\n]{0,35}90(?:\\.0+)?""").containsMatchIn(displayDump) ||
                Regex("""(?i)90(?:\\.0+)?[^\n]{0,35}(fps|refreshRate|refresh rate)""").containsMatchIn(displayDump)
            val has120 = Regex("""(?i)(fps|refreshRate|refresh rate)[^\n]{0,35}120(?:\\.0+)?""").containsMatchIn(displayDump) ||
                Regex("""(?i)120(?:\\.0+)?[^\n]{0,35}(fps|refreshRate|refresh rate)""").containsMatchIn(displayDump)

            runOnUiThread {
                appendLog("DIAGNÓSTICO: fabricante=$manufacturer; modelo=$model; Android=$androidVersion; HyperOS/MIUI=$hyperOs.")
                appendLog("ESTADO ANTERIOR: peak=$peakBefore; min=$minBefore; economia=$power.")
                appendLog(if (gamePath.contains("package:", true)) "Free Fire instalado." else "Free Fire não encontrado (com.dts.freefireth).")
                appendLog("MODOS DETECTADOS NO DUMPSYS: 90 Hz=$has90; 120 Hz=$has120.")
            }

            if (!has90) {
                runOnUiThread { appendLog("AUTOMAÇÃO PAUSADA: o relatório do sistema não confirmou modo de 90 Hz. Nenhuma alteração foi aplicada.") }
                return
            }

            val prefs = getSharedPreferences("atomic_refresh_backup", MODE_PRIVATE)
            if (!prefs.contains("peak")) {
                prefs.edit().putString("peak", peakBefore.ifBlank { "null" })
                    .putString("min", minBefore.ifBlank { "null" }).apply()
            }

            command("Solicitando 90 Hz por ADB autorizado…",
                "settings put system peak_refresh_rate 90 && settings put system min_refresh_rate 90")
            val peakAfter = command("Conferindo limite máximo aplicado…", "settings get system peak_refresh_rate")
            val minAfter = command("Conferindo limite mínimo aplicado…", "settings get system min_refresh_rate")
            val actual = command("Consultando taxa de atualização reportada pelo display…", "dumpsys display")
            val actual90 = Regex("""(?i)(renderFrameRate|active.*fps|refreshRate)[^\n]{0,35}90(?:\\.0+)?""").containsMatchIn(actual)
            runOnUiThread {
                if (peakAfter == "90" && minAfter == "90") {
                    appendLog("CONFIGURAÇÕES ACEITAS: peak=$peakAfter; min=$minAfter.")
                } else {
                    appendLog("O fabricante não confirmou os dois limites: peak=$peakAfter; min=$minAfter.")
                }
                appendLog(if (actual90) "O relatório atual mostra referência a 90 Hz." else "A taxa ativa de 90 Hz ainda não foi comprovada. O sistema pode manter outro modo.")
                appendLog("IMPORTANTE: isso ajusta a tela. Não altera nem garante o limite de FPS interno do Free Fire.")
            }
        } catch (e: Exception) {
            runOnUiThread { appendLog("FALHA NO PROCESSO AUTOMÁTICO: ${e.message ?: e.javaClass.simpleName}") }
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
                runOnUiThread { appendLog("Executando comandos ADB autorizados para solicitar ${hz} Hz…") }
                val result = connection.shell("settings put system peak_refresh_rate $hz && settings put system min_refresh_rate $hz")
                if (result.exitCode != 0) throw IllegalStateException(result.allOutput)
                val peakAfter = connection.shell("settings get system peak_refresh_rate").allOutput.trim()
                val minAfter = connection.shell("settings get system min_refresh_rate").allOutput.trim()
                if (peakAfter != hz.toString() || minAfter != hz.toString()) {
                    throw IllegalStateException("O sistema não confirmou os valores. peak=$peakAfter; min=$minAfter")
                }
                runOnUiThread {
                    appendLog("VALORES CONFIRMADOS PELO ANDROID: peak=$peakAfter; min=$minAfter. Isso não garante 90 FPS dentro de um jogo.")
                    modeButton.text = "RESTAURAR CONFIGURAÇÕES"
                    modeButton.setOnClickListener { restoreRefreshRate() }
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendLog("O SISTEMA RECUSOU OU NÃO CONFIRMOU A ALTERAÇÃO: ${e.message ?: e.javaClass.simpleName}")
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
                    appendLog("CONFIGURAÇÕES ANTERIORES RESTAURADAS: peak=$oldPeak; min=$oldMin.")
                    modeButton.text = "ATIVAR MODO"
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

    private fun executeTerminalCommand() {
        val connection = adb
        val command = terminalInput.text.toString().trim()
        if (!adbReady || connection == null) {
            appendLog("ADB desconectado. Autorize e conecte primeiro.")
            return
        }
        if (command.isBlank()) return
        terminalInput.setText("")
        terminalButton.isEnabled = false
        thread {
            try {
                appendFromWorker("$ " + command)
                val result = connection.shell(command)
                appendFromWorker("SAÍDA:\n" + result.allOutput.ifBlank { "(sem saída)" } + "\nCÓDIGO DE SAÍDA: " + result.exitCode)
            } catch (e: Exception) {
                appendFromWorker("ERRO DE EXECUÇÃO: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                runOnUiThread { terminalButton.isEnabled = adbReady && !busy }
            }
        }
    }

    private fun appendLog(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logView.text = logView.text.toString() + "\n[" + time + "] > " + message
    }

    private fun setBusy(value: Boolean) {
        busy = value
        pairButton.isEnabled = !value
        modeButton.isEnabled = !value && adbReady
        if (::terminalButton.isInitialized) terminalButton.isEnabled = !value && adbReady
    }

    private fun updateChoices() {
        hz90.alpha = if (selectedHz == 90) 1f else .55f
        hz120.alpha = if (selectedHz == 120) 1f else .55f
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


/** Recebe o código digitado diretamente na notificação do Android. */
class PairCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val code = results.getCharSequence(KEY_CODE)?.toString()?.trim() ?: return
        if (!code.matches(Regex("\\d{6}"))) return
        val launch = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_CODE, code)
            putExtra(EXTRA_PORT, intent.getIntExtra(EXTRA_PORT, -1))
            putExtra(EXTRA_HOST, intent.getStringExtra(EXTRA_HOST))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(launch)
    }

    companion object {
        const val KEY_CODE = "atomic_pairing_code"
        const val EXTRA_CODE = "atomic_pairing_code_extra"
        const val EXTRA_PORT = "atomic_pairing_port_extra"
        const val EXTRA_HOST = "atomic_pairing_host_extra"
    }
}
