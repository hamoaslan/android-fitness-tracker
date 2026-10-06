package de.pbma.moa.fitnessapp

import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.ConcurrentHashMap
import android.view.animation.AnimationUtils



class LiveViewerActivity : AppCompatActivity() {

    private lateinit var outputView: TextView
    private lateinit var countdownText: TextView

    private val playerStats = ConcurrentHashMap<String, String>()
    private val lobbyParticipants = mutableSetOf<String>()

    private var serviceBound = false
    private var mqttService: MQTTService? = null

    private var countdownStarted = false
    private val countdownHandler = Handler()
    private var countdownTime = 60
    private var workoutCountdownTime = 60
    private val workoutDuration = 60000L
    private var workoutRunning = false
    private lateinit var playerStatsContainer: LinearLayout


    private val pressListener = object : PressListener {
        override fun onPress(topic: String?, msg: String?) {
            Log.d("LiveViewer", "onPress: $topic → $msg")
            runOnUiThread {
                handleIncomingMessage(topic, msg)
            }
        }

        override fun onLogMessage(msg: String?) {
            Log.d("LiveViewer", "Log: $msg")
        }

        override fun onMQTTStatus(connected: Boolean) {
            runOnUiThread {
                outputView.text = if (connected) {
                    if (playerStats.isEmpty()) "✅ Verbunden – warte auf Spieler…" else updateViewText()
                } else {
                    "❌ MQTT-Verbindung getrennt"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_viewer)

        outputView = findViewById(R.id.textLiveData)
        countdownText = findViewById(R.id.countdownText)

        outputView.text = "🔌 Verbinde mit MQTT…"

        // MQTT-Service starten
        val startIntent = Intent(this, MQTTService::class.java).apply {
            action = MQTTService.ACTION_START
        }
        startService(startIntent)

        // MQTT-Service binden
        val bindIntent = Intent(this, MQTTService::class.java).apply {
            action = MQTTService.ACTION_PRESS
        }
        bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        playerStatsContainer = findViewById<LinearLayout>(R.id.playerStatsContainer)

    }

    private fun handleIncomingMessage(topic: String?, msg: String?) {
        if (msg == null) return

        when {
            msg == "START" -> {
                workoutRunning = true
                //SpielerNamenNichtLöschen
              //  playerStats.clear()
                outputView.text = "🏋️ Workout läuft…"
                workoutCountdownTime = 8

                //Wer hat gewonnen?
                val winner = playerStats.maxByOrNull { (_, stats) ->
                    stats.split(",").sumOf {
                        it.split(":")[1].trim().toIntOrNull() ?: 0
                    }
                }

                // Countdown anzeigen
                countdownHandler.post(object : Runnable {
                    override fun run() {
                        if (workoutCountdownTime <= 0) {
                            workoutRunning = false

                            // JETZT Gewinner bestimmen
                            val winner = playerStats.maxByOrNull { (_, stats) ->
                                stats.split(",").sumOf {
                                    it.split(":")[1].trim().toIntOrNull() ?: 0
                                }
                            }

                            winner?.let { showWinnerAnimation(it.key) }
                            countdownText.text = ""
                        } else {
                            countdownText.text = "Workout: ${workoutCountdownTime--}s"
                            countdownHandler.postDelayed(this, 1000)
                        }
                    }
                })
            }
            msg.endsWith("|JOIN") -> {
                val player = msg.removeSuffix("|JOIN")
                lobbyParticipants.add(player)
                updateLobbyView()
                startCountdownIfNeeded()
            }

            topic?.startsWith("25moagd/game/") == true -> {
                if (workoutRunning) parseAndDisplayMessage(msg)
            }

            else -> {
                Log.d("LiveViewer", "Unerwartete Nachricht: $msg")
            }
        }
    }

    private fun updateLobbyView() {
        outputView.text = "👥 Spieler in Lobby:\n" + lobbyParticipants.joinToString("\n")
    }

    private fun parseAndDisplayMessage(msg: String) {
        val parts = msg.split("|")
        if (parts.size == 4) {
            val name = parts[0]
            val pushups = parts[1]
            val squats = parts[2]
            val pullups = parts[3]
            val result = "Pushups: $pushups, Squats: $squats, Pullups: $pullups"
            playerStats[name] = result
            updateStatsView()
        } else {
            Log.w("LiveViewer", "⚠️ Ungültiges Format: $msg")
        }
    }

    private fun updateViewText(): String {
        val names = playerStats.keys.joinToString(" | ") { it }
        val pushups = playerStats.map { it.value.split(",")[0].split(":")[1].trim() }.joinToString(" | ")
        val squats = playerStats.map { it.value.split(",")[1].split(":")[1].trim() }.joinToString(" | ")
        val pullups = playerStats.map { it.value.split(",")[2].split(":")[1].trim() }.joinToString(" | ")

        return """
        👤 $names

        Pushups: $pushups
        Squats:  $squats
        Pullups: $pullups
    """.trimIndent()
    }

    private fun startCountdownIfNeeded() {
        if (!countdownStarted && lobbyParticipants.size >= 2) {
            countdownStarted = true
            countdownTime = 10
            countdownText.text = "Start in: 10s"

            countdownHandler.post(object : Runnable {
                override fun run() {
                    if (countdownTime <= 0) {
                        countdownText.text = "📲 Bitte Handys in die Tasche"
                        countdownHandler.postDelayed({
                            countdownText.text = "⏳ Warte…"
                            countdownHandler.postDelayed({
                                countdownText.text = "Auf die…"
                                countdownHandler.postDelayed({
                                    countdownText.text = "Plätze…"
                                    countdownHandler.postDelayed({
                                        countdownText.text = "Fertig…"
                                        countdownHandler.postDelayed({
                                            countdownText.text = "Los! 💥"
                                            countdownHandler.postDelayed({
                                            mqttService?.sendMessage("25moagd/lobby", "START")
                                            }, 800)
                                        }, 800) // Zeit nach „Fertig…“ bis „Los!“
                                    }, 800)     // Zeit nach „Plätze…“ bis „Fertig…“
                                }, 800)         // Zeit nach „Auf die…“ bis „Plätze…“
                            }, 2000)            // Zeit nach „Warte…“ bis „Auf die…“
                        }, 2000)                // Zeit nach „Handys in Tasche“ bis „Warte…“
                    } else {
                        countdownText.text = "Start in: ${countdownTime--}s"
                        countdownHandler.postDelayed(this, 1000)
                    }
                }
            })
        }
    }

    private fun updateStatsView() {
        playerStatsContainer.removeAllViews()

        for ((name, statsString) in playerStats) {
            val stats = statsString.split(",")
            val pushups = stats[0].split(":")[1].trim()
            val squats = stats[1].split(":")[1].trim()
            val pullups = stats[2].split(":")[1].trim()

            val playerBox = TextView(this).apply {
                text = """
                👤 $name
                Liegestütze: $pushups
                Kniebeugen: $squats
                Klimmzüge: $pullups
            """.trimIndent()
                setPadding(32, 32, 32, 32)
                setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                textSize = 14f
            }

            playerStatsContainer.addView(playerBox)
        }
    }


    private fun showWinnerAnimation(winnerName: String) {
        // 1. Zeige oben "Victory" + Gewinnername
        outputView.text = "🏆 Victory 🏆\n$winnerName"

        // 2. Gewinner im Container hervorheben
        val container = findViewById<LinearLayout>(R.id.playerStatsContainer)
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is TextView && child.text.contains(winnerName)) {
                child.setBackgroundResource(R.drawable.winner_highlight)
                child.text = "🏆 $winnerName\n" + child.text.lines().drop(1).joinToString("\n")

                val animation = AnimationUtils.loadAnimation(this, R.anim.pulse)
                child.startAnimation(animation)
            }
        }
    }


    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mqttService = (service as? MQTTService.LocalBinder)?.getMQTTService()
            mqttService?.registerPressListener(pressListener)
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mqttService?.deregisterPressListener(pressListener)
            mqttService = null
            serviceBound = false
        }
    }

    override fun onDestroy() {
        if (serviceBound) {
            mqttService?.deregisterPressListener(pressListener)
            unbindService(serviceConnection)
        }
        super.onDestroy()
    }
}
