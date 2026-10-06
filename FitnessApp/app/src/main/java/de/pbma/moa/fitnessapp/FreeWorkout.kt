package de.pbma.moa.fitnessapp

import android.app.Activity
import de.pbma.moa.fitnessapp.R
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt
import android.widget.LinearLayout
import android.widget.Toast
import java.io.File
import android.os.Environment
import android.os.IBinder
import android.widget.EditText
import java.lang.Math.*
import java.text.*
import java.util.*
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity


import androidx.fragment.app.FragmentActivity
import database.AppDataBase
import database.ProfileEntity



import androidx.lifecycle.lifecycleScope
import de.pbma.moa.fitnessapp.mqtt.MqttMessaging
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//public class WorkoutActivity extends AppCompatActivity {

class FreeWorkout : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var vibrator: android.os.Vibrator
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var rotationSensor: Sensor? = null
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private var isInPocket = false
    private var pullUpCount = 0
    private var pushUpCount = 0
    private var squatCount = 0

    private lateinit var statusText: TextView
    private lateinit var counterPushup: TextView
    private lateinit var counterPullup: TextView
    private lateinit var counterSquat: TextView
    private lateinit var angleXText: TextView
    private lateinit var angleYText: TextView
    private lateinit var angleZText: TextView
    private lateinit var angleText: TextView
    private lateinit var timerText: TextView
    private lateinit var startButton: Button

    private var currentPitch = 0.0
    private var currentRoll = 0.0
    private var currentAzimuth = 0.0
    private var lastPitch = 0.0
    private var lastRoll = 0.0

    private var goingDown = false
    private var lastZ = 0f
    private var lastMotionTime = 0L

    private var repInProgress = false

    //Neuer Schwellwert
    private var ACC_THRESHOLD_DOWN = -0.8f  // z sinkt bei Bewegung nach unten
    private var ACC_THRESHOLD_UP = 0.8f     // z steigt bei Bewegung nach oben

    private var smoothedZ = 0f
    private val alpha = 0.8f // je näher an 1, desto glatter


    //Alter Schwellwert
    private val DOWN_THRESHOLD = -2.0f
    private val UP_THRESHOLD = 2.0f
    private val MOTION_TIMEOUT = 4000L
    private val ANGLE_CHANGE_THRESHOLD = 15.0

    private var thresholdPushup = 1.0
    private var thresholdSquat = 1.0  // wird später befüllt
    private var thresholdPullup = 1.1

    private var positionStartTime = 0L
    private var hasConfirmedReady = false

    private var workoutStartTime = 0L
    private var workoutPausedTime = 0L
    private var isTimerRunning = false
    private var workoutStarted = false
    private var isWaitingForPocket = false

    // Nur zählen, wenn Bewegung stark genug UND seit letzter Bewegung mind. 300 ms vergangen
    private val REP_MIN_DELAY_MS = 400
    private var lastRepTime = 0L
    private val MOTION_DEADZONE = 0.2f  // kleinere Bewegungen ignorieren


    //Debug
    private lateinit var togglePocketButton: Button
    private var debugPocketOverride = false
    private var debugMode = false  // Debug-Modus aktivieren
    private lateinit var debugExerciseText: TextView
    private lateinit var debugButtons: LinearLayout
    private var isLogging = false
    private var loggingExercise: ExerciseType = ExerciseType.NONE
    private val loggedSensorData = mutableListOf<String>()
    private var calibrationStep = 0
    private var calibrationStartTime = 0L
    private var calibrationLogged = false

    // Kalibrierbare Schwellen pro Übung
    private var accDownPushup = -0.8f
    private var accUpPushup = 0.8f

    private var accDownSquat = -0.8f
    private var accUpSquat = 0.8f

    private var accDownPullup = -0.8f
    private var accUpPullup = 0.8f

    private lateinit var db: AppDataBase
    private var stateChangeTime = 0L  // Zeitstempel für READY -> COUNTING

    private var currentProfileName: String = ""

    private var multiplayerMode = false

    //MQTT-Service
    private var mqttService: MQTTService? = null
    private var mqttServiceBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MQTTService.LocalBinder
            mqttService = binder.getMQTTService()
            mqttServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mqttServiceBound = false
            mqttService = null
        }
    }



    private val timerHandler = Handler()
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTimerRunning) {
                val elapsedMillis = System.currentTimeMillis() - workoutStartTime
                val seconds = (elapsedMillis / 1000) % 60
                val minutes = (elapsedMillis / (1000 * 60)) % 60
                val hours = (elapsedMillis / (1000 * 60 * 60))
                timerText.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                timerHandler.postDelayed(this, 1000)
            }
        }
    }


    enum class State {
        WAITING, READY, COUNTING, PAUSED
    }

    enum class ExerciseType {
        NONE, PUSHUP, SQUAT, PULLUP
    }

    private var state = State.WAITING
    private var currentExercise = ExerciseType.NONE

    override fun onCreate(savedInstanceState: Bundle?) {

        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1001
            )
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.free_workout_layout)
        super.onCreate(savedInstanceState)
        counterPushup = findViewById(R.id.counterPushup)
        db = AppDataBase.getInstance(applicationContext)

        // MQTT-Service starten und binden
        val intent = Intent(this, MQTTService::class.java)
        intent.action = MQTTService.ACTION_START
        startService(intent)
        bindService(Intent(this, MQTTService::class.java).apply {
            action = MQTTService.ACTION_PRESS
        }, connection, Context.BIND_AUTO_CREATE)

        //Automatisch nach Profil Fragen
        lifecycleScope.launch {
            val profiles = withContext(Dispatchers.IO) { db.profileDAO().getAll() }
            val names = profiles.map { it.name }.toMutableList()
            names.add("Neues Profil erstellen")

            AlertDialog.Builder(this@FreeWorkout)
                .setTitle("Profil auswählen")
                .setItems(names.toTypedArray()) { _, which ->
                    if (which == names.size - 1) {
                        // Neues Profil erstellen
                        promptNewProfileName()
                    } else {
                        val selected = profiles[which]
                        loadProfile(selected)
                        Toast.makeText(this@FreeWorkout, "Profil '${selected.name}' geladen", Toast.LENGTH_SHORT).show()
                    }
                }
                .setCancelable(false)
                .show()
        }
        // Multiplayer-Button
        val multiplayerButton = findViewById<Button>(R.id.btn_multiplayer)
//        multiplayerButton.setOnClickListener {
//            multiplayerMode = !multiplayerMode
//            val status = if (multiplayerMode) "aktiviert" else "deaktiviert"
//            Toast.makeText(this, "Multiplayer $status", Toast.LENGTH_SHORT).show()
//        }
        multiplayerButton.setOnClickListener {
            multiplayerMode = true
            Toast.makeText(this, "Multiplayer aktiviert", Toast.LENGTH_SHORT).show()

            // In Lobby eintragen
            val joinMsg = "$currentProfileName|JOIN"
            mqttService?.sendMessage("25moagd/lobby", joinMsg)
            // Reset
            workoutStarted = false
            isTimerRunning = false
            timerHandler.removeCallbacks(timerRunnable)
            timerText.text = "00:00:00"
            pushUpCount = 0
            squatCount = 0
            pullUpCount = 0
            counterPushup.text = "Liegestütze: 0"
            counterSquat.text = "Kniebeugen: 0"
            counterPullup.text = "Klimmzüge: 0"
        }



        //Zurück
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }


        //ProfilWächseln
        val btnSwitchProfile = findViewById<ImageButton>(R.id.btnSwitchProfile)
        btnSwitchProfile.setOnClickListener {
            lifecycleScope.launch {
                val profiles = withContext(Dispatchers.IO) { db.profileDAO().getAll() }

                val names = profiles.map { it.name }.toMutableList()
                names.add("Neues Profil erstellen")

                AlertDialog.Builder(this@FreeWorkout)
                    .setTitle("Profil wechseln")
                    .setItems(names.toTypedArray()) { _, which ->
                        if (which == names.size - 1) {
                            // neues Profil erstellen
                            promptNewProfileName()
                        } else {
                            val selected = profiles[which]
                            loadProfile(selected)

                            // Reset
                            workoutStarted = false
                            isTimerRunning = false
                            timerHandler.removeCallbacks(timerRunnable)
                            timerText.text = "00:00:00"
                            pushUpCount = 0
                            squatCount = 0
                            pullUpCount = 0
                            counterPushup.text = "Liegestütze: 0"
                            counterSquat.text = "Kniebeugen: 0"
                            counterPullup.text = "Klimmzüge: 0"

                            Toast.makeText(this@FreeWorkout, "Profil gewechselt", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .show()
            }
        }



        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator

        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        statusText = findViewById(R.id.statusText)
        counterPushup = findViewById(R.id.counterPushup)
        counterSquat = findViewById(R.id.counterSquat)
        counterPullup = findViewById(R.id.counterPullup)
        angleXText = findViewById(R.id.angleXText)
        angleYText = findViewById(R.id.angleYText)
        angleZText = findViewById(R.id.angleZText)
        angleText = findViewById(R.id.angleText)
        timerText = findViewById(R.id.timerText)
        startButton = findViewById(R.id.startButton)

        togglePocketButton = findViewById(R.id.togglePocketButton)

        //DEBUG
        val btnSaveLog: Button = findViewById(R.id.btnSaveLog)
        btnSaveLog.setOnClickListener {
            stopAndSaveLogging()
        }
        debugExerciseText = findViewById(R.id.debugExerciseText)
        debugButtons = findViewById(R.id.debugExerciseButtons)

        val btnDebugPushup: Button = findViewById(R.id.btnDebugPushup)
        val btnDebugSquat: Button = findViewById(R.id.btnDebugSquat)
        val btnDebugPullup: Button = findViewById(R.id.btnDebugPullup)

        if (debugMode) {
            debugExerciseText.visibility = View.VISIBLE
            debugButtons.visibility = View.VISIBLE
        }


        //DEBUG
        btnDebugPushup.setOnClickListener {
            loggingExercise = ExerciseType.PUSHUP
            calibrationStep = 0
            Toast.makeText(this, "Kalibrierung gestartet: Liegestütze", Toast.LENGTH_SHORT)
                .show()
            pushUpCount++
            sendMultiplayerProgress()
        }


        btnDebugSquat.setOnClickListener {
            currentExercise = ExerciseType.SQUAT
            state = State.READY
            debugExerciseText.text = "Debug: Aktuelle Übung: SQUAT"
            statusText.text = "Debug: Bereit für Kniebeugen"
            startLogging(ExerciseType.SQUAT)
            squatCount++
            sendMultiplayerProgress()
        }

        btnDebugPullup.setOnClickListener {
            currentExercise = ExerciseType.PULLUP
            state = State.READY
            debugExerciseText.text = "Debug: Aktuelle Übung: PULLUP"
            statusText.text = "Debug: Bereit für Klimmzüge"
            startLogging(ExerciseType.PULLUP)
            pullUpCount++
            sendMultiplayerProgress()
        }


        //DEBUG
        togglePocketButton.setOnClickListener {
            debugPocketOverride = !debugPocketOverride
            isInPocket = debugPocketOverride
            statusText.text =
                if (isInPocket) "DEBUG: In Tasche (manuell)" else "DEBUG: Nicht in Tasche (manuell)"
            Log.d("DEBUG", "Manueller Taschenstatus: $isInPocket")

            if (workoutStarted) {
                if (isInPocket) {
                    resumeTimer()
                } else {
                    pauseTimer()
                    showPauseDialog()
                }
            }
        }
        // Push-up Schwellenbuttons
        val btnPushupUp: Button = findViewById(R.id.btnPushupUp)
        val btnPushupDown: Button = findViewById(R.id.btnPushupDown)
        val thresholdPushupLabel: TextView = findViewById(R.id.thresholdPushupLabel)
        btnPushupUp.setOnClickListener {
            accUpPushup += 0.1f; accDownPushup -= 0.1f
            thresholdPushupLabel.text = "Push-up Δz: ${"%.2f".format(accUpPushup)}"
        }
        btnPushupDown.setOnClickListener {
            accUpPushup -= 0.1f; accDownPushup += 0.1f
            thresholdPushupLabel.text = "Push-up Δz: ${"%.2f".format(accUpPushup)}"
        }

// Squat Schwellenbuttons
        val btnSquatUp: Button = findViewById(R.id.btnSquatUp)
        val btnSquatDown: Button = findViewById(R.id.btnSquatDown)
        val thresholdSquatLabel: TextView = findViewById(R.id.thresholdSquatLabel)
        btnSquatUp.setOnClickListener {
            accUpSquat += 0.1f; accDownSquat -= 0.1f
            thresholdSquatLabel.text = "Squat Δz: ${"%.2f".format(accUpSquat)}"
        }
        btnSquatDown.setOnClickListener {
            accUpSquat -= 0.1f; accDownSquat += 0.1f
            thresholdSquatLabel.text = "Squat Δz: ${"%.2f".format(accUpSquat)}"
        }

// Pullup Schwellenbuttons
        val btnPullupUp: Button = findViewById(R.id.btnPullupUp)
        val btnPullupDown: Button = findViewById(R.id.btnPullupDown)
        val thresholdPullupLabel: TextView = findViewById(R.id.thresholdPullupLabel)
        btnPullupUp.setOnClickListener {
            accUpPullup += 0.1f; accDownPullup -= 0.1f
            thresholdPullupLabel.text = "Pull-up Δz: ${"%.2f".format(accUpPullup)}"
        }
        btnPullupDown.setOnClickListener {
            accUpPullup -= 0.1f; accDownPullup += 0.1f
            thresholdPullupLabel.text = "Pull-up Δz: ${"%.2f".format(accUpPullup)}"
        }


        startButton.setOnClickListener {
            workoutStarted = true
            startButton.visibility = View.GONE
            statusText.text = "Warte auf Tasche..."

            // Zähler zurücksetzen
            pushUpCount = 0
            squatCount = 0
            pullUpCount = 0

            counterPushup.text = "Liegestütze: 0"
            counterSquat.text = "Kniebeugen: 0"
            counterPullup.text = "Klimmzüge: 0"

            // Timer zurücksetzen
            workoutStartTime = 0L
            workoutPausedTime = 0L
            isTimerRunning = false
            timerText.text = "00:00:00"
        }
        val btnLowerThreshold: Button = findViewById(R.id.btnLowerThreshold)
        val btnHigherThreshold: Button = findViewById(R.id.btnHigherThreshold)
        val thresholdLabel: TextView = findViewById(R.id.thresholdLabel)

        btnLowerThreshold.setOnClickListener {
            ACC_THRESHOLD_UP -= 0.1f
            ACC_THRESHOLD_DOWN += 0.1f
            thresholdLabel.text = "Δz-Schwelle: ${"%.2f".format(ACC_THRESHOLD_UP)}"
        }

        btnHigherThreshold.setOnClickListener {
            ACC_THRESHOLD_UP += 0.1f
            ACC_THRESHOLD_DOWN -= 0.1f
            thresholdLabel.text = "Δz-Schwelle: ${"%.2f".format(ACC_THRESHOLD_UP)}"
        }
        val pushupLabel = findViewById<TextView>(R.id.thresholdPushupLabel)
        val squatLabel = findViewById<TextView>(R.id.thresholdSquatLabel)
        val pullupLabel = findViewById<TextView>(R.id.thresholdPullupLabel)

        fun updateThresholdLabels() {
            pushupLabel.text = "Push-up Δz: %.2f".format(thresholdPushup)
            squatLabel.text = "Squat Δz: %.2f".format(thresholdSquat)
            pullupLabel.text = "Pull-up Δz: %.2f".format(thresholdPullup)
        }
        updateThresholdLabels()
        findViewById<Button>(R.id.btnPushupUp).setOnClickListener {
            thresholdPushup += 0.1
            updateThresholdLabels()
        }
        findViewById<Button>(R.id.btnPushupDown).setOnClickListener {
            thresholdPushup = maxOf(0.1, thresholdPushup - 0.1)
            updateThresholdLabels()
        }

        findViewById<Button>(R.id.btnSquatUp).setOnClickListener {
            thresholdSquat += 0.1
            updateThresholdLabels()
        }
        findViewById<Button>(R.id.btnSquatDown).setOnClickListener {
            thresholdSquat = maxOf(0.1, thresholdSquat - 0.1)
            updateThresholdLabels()
        }

        findViewById<Button>(R.id.btnPullupUp).setOnClickListener {
            thresholdPullup += 0.1
            updateThresholdLabels()
        }
        findViewById<Button>(R.id.btnPullupDown).setOnClickListener {
            thresholdPullup = maxOf(0.1, thresholdPullup - 0.1)
            updateThresholdLabels()
        }
        // Klickzähler für timerText zum Aktivieren des Debug-Modus
        var debugClickCount = 0
        var lastClickTime = 0L

        timerText.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime > 1000) {
                debugClickCount = 0
            }
            debugClickCount++
            lastClickTime = currentTime

            val btnExitDebug: Button = findViewById(R.id.btnExitDebug)
            btnExitDebug.setOnClickListener {
                debugMode = false
                Toast.makeText(this, "Debug-Modus beendet", Toast.LENGTH_SHORT).show()

                togglePocketButton.visibility = View.GONE
                angleXText.visibility = View.GONE
                angleYText.visibility = View.GONE
                angleZText.visibility = View.GONE
                angleText.visibility = View.GONE
                debugExerciseText.visibility = View.GONE
                debugButtons.visibility = View.GONE

                findViewById<View>(R.id.thresholdPushupLabel).visibility = View.GONE
                findViewById<View>(R.id.thresholdSquatLabel).visibility = View.GONE
                findViewById<View>(R.id.thresholdPullupLabel).visibility = View.GONE
                findViewById<View>(R.id.btnPushupUp).visibility = View.GONE
                findViewById<View>(R.id.btnPushupDown).visibility = View.GONE
                findViewById<View>(R.id.btnSquatUp).visibility = View.GONE
                findViewById<View>(R.id.btnSquatDown).visibility = View.GONE
                findViewById<View>(R.id.btnPullupUp).visibility = View.GONE
                findViewById<View>(R.id.btnPullupDown).visibility = View.GONE
                findViewById<View>(R.id.thresholdLabel).visibility = View.GONE
                findViewById<View>(R.id.btnLowerThreshold).visibility = View.GONE
                findViewById<View>(R.id.btnHigherThreshold).visibility = View.GONE
                findViewById<View>(R.id.btnSaveLog).visibility = View.GONE
                btnExitDebug.visibility = View.GONE
            }

            if (debugClickCount >= 5 && !debugMode) {
                debugMode = true
                Toast.makeText(this, "Debug-Modus aktiviert!", Toast.LENGTH_SHORT).show()

                // Alle Debug-Elemente sichtbar machen
                togglePocketButton.visibility = View.VISIBLE
                angleXText.visibility = View.VISIBLE
                angleYText.visibility = View.VISIBLE
                angleZText.visibility = View.VISIBLE
                angleText.visibility = View.VISIBLE
                debugExerciseText.visibility = View.VISIBLE
                debugButtons.visibility = View.VISIBLE
                btnExitDebug.visibility = View.VISIBLE

                findViewById<View>(R.id.thresholdPushupLabel).visibility = View.VISIBLE
                findViewById<View>(R.id.thresholdSquatLabel).visibility = View.VISIBLE
                findViewById<View>(R.id.thresholdPullupLabel).visibility = View.VISIBLE
                findViewById<View>(R.id.btnPushupUp).visibility = View.VISIBLE
                findViewById<View>(R.id.btnPushupDown).visibility = View.VISIBLE
                findViewById<View>(R.id.btnSquatUp).visibility = View.VISIBLE
                findViewById<View>(R.id.btnSquatDown).visibility = View.VISIBLE
                findViewById<View>(R.id.btnPullupUp).visibility = View.VISIBLE
                findViewById<View>(R.id.btnPullupDown).visibility = View.VISIBLE
                findViewById<View>(R.id.thresholdLabel).visibility = View.VISIBLE
                findViewById<View>(R.id.btnLowerThreshold).visibility = View.VISIBLE
                findViewById<View>(R.id.btnHigherThreshold).visibility = View.VISIBLE
                findViewById<View>(R.id.btnSaveLog).visibility = View.VISIBLE


            }
        }



    }

    private fun promptNewProfileName() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Neues Profil")
            .setMessage("Gib einen Namen für das neue Profil ein:")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    startCalibrationMode(name)
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            //Toast.makeText(this, "Speicherzugriff erlaubt", Toast.LENGTH_SHORT).show()
        } else {
            //Toast.makeText(this, "Speicherzugriff verweigert", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
        proximitySensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        rotationSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        timerHandler.removeCallbacks(timerRunnable)
    }
    override fun onDestroy() {
        super.onDestroy()
        if (mqttServiceBound) {
            unbindService(connection)
            mqttServiceBound = false
        }
    }


    private fun startTimer() {
        workoutStartTime = System.currentTimeMillis()
        isTimerRunning = true
        timerHandler.post(timerRunnable)
    }

    private fun pauseTimer() {
        workoutPausedTime = System.currentTimeMillis()
        isTimerRunning = false
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun resumeTimer() {
        if (!isTimerRunning) {
            if (workoutStartTime == 0L) {
                startTimer()
            } else {
                val pauseDuration = System.currentTimeMillis() - workoutPausedTime
                workoutStartTime += pauseDuration
                isTimerRunning = true
                timerHandler.post(timerRunnable)
            }
        }
    }

    private fun showProfileManagementDialog(profiles: List<ProfileEntity>) {
        val names = profiles.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Profil verwalten")
            .setItems(names) { _, which ->
                val selectedProfile = profiles[which]
                AlertDialog.Builder(this)
                    .setTitle("Profil '${selectedProfile.name}' bearbeiten")
                    .setItems(arrayOf("Umbenennen", "Löschen")) { _, action ->
                        when (action) {
                            0 -> promptRenameProfile(selectedProfile)
                            1 -> confirmDeleteProfile(selectedProfile)
                        }
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
    private fun promptRenameProfile(profile: ProfileEntity) {
        val input = EditText(this)
        input.setText(profile.name)

        AlertDialog.Builder(this)
            .setTitle("Profil umbenennen")
            .setView(input)
            .setPositiveButton("Speichern") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val updatedProfile = ProfileEntity(
                            newName,
                            profile.accDownPushup,
                            profile.accUpPushup,
                            profile.accDownSquat,
                            profile.accUpSquat,
                            profile.accDownPullup,
                            profile.accUpPullup
                        )
                        updatedProfile.workoutLog = profile.workoutLog
                        db.profileDAO().update(updatedProfile)
                    }
                    Toast.makeText(this, "Profil umbenannt", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
    private fun confirmDeleteProfile(profile: ProfileEntity) {
        AlertDialog.Builder(this)
            .setTitle("Profil löschen")
            .setMessage("Möchten Sie das Profil '${profile.name}' wirklich löschen?")
            .setPositiveButton("Löschen") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.profileDAO().delete(profile)
                }
                Toast.makeText(this, "Profil gelöscht", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }




    private fun startCalibrationMode(name: String) {
        Toast.makeText(this, "Kalibrierungsmodus gestartet", Toast.LENGTH_SHORT).show()

        AlertDialog.Builder(this)
            .setTitle("Kalibrierung – Liegestütze")
            .setMessage("Handy bitte in die Tasche tun.\nBei jeder Vibration ein Liegestütz ausführen.\nSobald das Handy anfängt lange zu vibrieren \nHand bitte aus der Tasche holen, dann ist die Kalibrierung beendet ")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                calibrateExerciseWithPocket(name, ExerciseType.PUSHUP) {
                    AlertDialog.Builder(this)
                        .setTitle("Kalibrierung – Kniebeugen")
                        .setMessage("Bitte das Handy wieder in die Tasche legen.\nBei jeder Vibration ein Kniebeugen ausführen.\n\n" +
                                "Sobald das Handy anfängt lange zu vibrieren \n" +
                                "Hand bitte aus der Tasche holen, dann ist die Kalibrierung beendet")
                        .setCancelable(false)
                        .setPositiveButton("OK") { _, _ ->
                            waitForOutAndBackInPocket {
                                calibrateExerciseWithPocket(name, ExerciseType.SQUAT) {
                                    saveCalibratedProfile(name)
                                }
                            }
                        }
                        .show()
                }
            }
            .show()
    }

    private fun calibrateExerciseWithPocket(name: String, type: ExerciseType, onComplete: () -> Unit) {
        Thread {
            while (!isInPocket) {
                Thread.sleep(500)
            }

            runOnUiThread {
                Toast.makeText(this, "Tasche erkannt – Kalibrierung für ${type.name}", Toast.LENGTH_SHORT).show()
            }

            repeat(3) {
                Thread.sleep(6000) //6sekunden bis startposition
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                Thread.sleep(3000) //3sec nach jedem intervall
                vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            }

            repeat(3) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                Thread.sleep(300)
            }

            runOnUiThread {
                Toast.makeText(this, "${type.name} Kalibrierung abgeschlossen", Toast.LENGTH_SHORT).show()
                onComplete()
            }

        }.start()
    }

    private fun waitForOutAndBackInPocket(onBackIn: () -> Unit) {
        Thread {
            while (isInPocket) {
                Thread.sleep(500)
            }

            while (!isInPocket) {
                Thread.sleep(500)
            }

            runOnUiThread {
                onBackIn()
            }
        }.start()
    }

    private fun saveCalibratedProfile(name: String) {
        val profile = ProfileEntity(
            name,
            accDownPushup, accUpPushup,
            accDownSquat, accUpSquat,
            accDownPullup, accUpPullup
        )

        lifecycleScope.launch {
            // Hintergrund-Thread
            withContext(Dispatchers.IO) {
                db.profileDAO().insert(profile)
            }

            // Haupt-Thread – Profil laden
            val inserted = withContext(Dispatchers.IO) {
                db.profileDAO().getByName(name)
            }

            loadProfile(inserted)
            Toast.makeText(this@FreeWorkout, "Profil '$name' gespeichert und geladen", Toast.LENGTH_LONG).show()
        }
    }

    private fun waitForPocketThenCalibrate(name: String) {
        Thread {
            // Warte bis Handy in der Tasche erkannt wurde
            while (!isInPocket) {
                Thread.sleep(500)
            }

            runOnUiThread {
                Toast.makeText(this, "Tasche erkannt – Kalibrierung startet", Toast.LENGTH_SHORT).show()
            }

            // 3 Wiederholungen
            repeat(3) { i ->
                Thread.sleep(8000)
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))

                Thread.sleep(5000)
                vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            }

            // Abschluss: 3 kurze Vibrationen
            repeat(3) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                Thread.sleep(300)
            }

            // Profil speichern
            val profile = ProfileEntity(
                name,
                accDownPushup, accUpPushup,
                accDownSquat, accUpSquat,
                accDownPullup, accUpPullup
            )
            lifecycleScope.launch(Dispatchers.IO) {
                db.profileDAO().insert(profile)
            }

            runOnUiThread {
                Toast.makeText(this, "Kalibrierung abgeschlossen & Profil '$name' gespeichert.", Toast.LENGTH_LONG).show()
            }

        }.start()
    }


    private fun showPauseDialog() {

        AlertDialog.Builder(this)
            .setTitle("Workout pausiert")
            .setMessage("Möchten Sie das Workout beenden?")
            .setPositiveButton("Ja") { _, _ ->
                pauseTimer()
                workoutStarted = false
                isWaitingForPocket = false
                startButton.visibility = View.VISIBLE
                statusText.text = "Workout beendet"

                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val entry = "$timestamp | Pushups: $pushUpCount, Squats: $squatCount, Pullups: $pullUpCount"

                lifecycleScope.launch(Dispatchers.IO) {
                    val profile = db.profileDAO().getByName(currentProfileName)
                    val updatedLog = profile.workoutLog.toMutableList()
                    updatedLog.add(entry)
                    profile.workoutLog = updatedLog
                    db.profileDAO().update(profile)
                }
                Toast.makeText(this@FreeWorkout, "Workout wurde im Profil gespeichert", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Nein") { dialog, _ ->
                dialog.dismiss()
                isWaitingForPocket = true
                statusText.text = "Warte auf Tasche..."
                resumeTimer()
                state = State.WAITING
                currentExercise = ExerciseType.NONE
                repInProgress = false
                goingDown = false
                lastRepTime = System.currentTimeMillis() + 2000 // gibt Pufferzeit

            }
            .setCancelable(false)
            .show()
    }


    //DEBUG
    private fun startLogging(exercise: ExerciseType) {
        isLogging = true
        loggingExercise = exercise
        loggedSensorData.clear()
        Toast.makeText(this, "Logging gestartet für $exercise", Toast.LENGTH_SHORT).show()
    }

    private fun stopAndSaveLogging() {
        isLogging = false
        Log.d("LOGGING", "Anzahl geloggter Einträge: ${loggedSensorData.size}")
        if (loggedSensorData.isEmpty()) {
            Toast.makeText(this, "Keine Sensordaten gesammelt!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val filename = "${loggingExercise.name}_log_${System.currentTimeMillis()}.csv"
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, filename)

            file.printWriter().use { out ->
                out.println("Exercise,Timestamp,X,Y,Z")
                loggedSensorData.forEach { out.println(it) }
            }

            Toast.makeText(
                this,
                "Gespeichert in Downloads:\n${file.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
            Log.d("SPEICHERORT", "Datei geschrieben: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("LOGGING", "Fehler beim Speichern: ${e.message}")
            Toast.makeText(this, "Fehler beim Speichern: ${e.message}", Toast.LENGTH_LONG)
                .show()
        }
    }

    private var inCalibration = false
    private var calibrationStarted = false

    private fun checkCalibration(z: Float) {
        val startThreshold = -2.5f
        val endThreshold = 2.5f

        if (!calibrationStarted && z < startThreshold) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    150,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
            calibrationStarted = true
            inCalibration = true
            loggedSensorData.clear()
            Toast.makeText(this, "Kalibrierung gestartet", Toast.LENGTH_SHORT).show()
        }

        if (calibrationStarted && inCalibration && z > endThreshold) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    300,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
            inCalibration = false
            saveCalibrationLog()
            Toast.makeText(this, "Kalibrierung beendet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logIfCalibrating(x: Float, y: Float, z: Float) {
        if (inCalibration) {
            val timestamp = System.currentTimeMillis()
            loggedSensorData.add("CALIB,$timestamp,$x,$y,$z")
        }
    }

    private fun saveCalibrationLog() {
        try {
            val filename = "calibration_${System.currentTimeMillis()}.csv"
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, filename)
            file.printWriter().use { out ->
                out.println("Type,Timestamp,X,Y,Z")
                loggedSensorData.forEach { out.println(it) }
            }
            Log.d("CALIB", "Gespeichert: ${file.absolutePath}")
            Toast.makeText(
                this,
                "Kalibrierung gespeichert:\n${file.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e("CALIB", "Fehler beim Speichern: ${e.message}")
        }
    }


    private fun handleCalibrationLogging(x: Float, y: Float, z: Float) {
        val now = System.currentTimeMillis()

        when (calibrationStep) {
            0 -> {
                calibrationStartTime = now
                calibrationLogged = false
                calibrationStep = 1
                statusText.text = "Halte Startposition für 2 Sekunden..."
            }

            1 -> {
                if (!calibrationLogged && now - calibrationStartTime > 2000) {
                    val timestamp = System.currentTimeMillis()
                    loggedSensorData.clear()
                    loggedSensorData.add("START,$timestamp,$x,$y,$z")
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            150,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                    statusText.text =
                        "Startposition erfasst. Jetzt Endposition einnehmen..."
                    calibrationStartTime = now
                    calibrationLogged = true
                    calibrationStep = 2
                }
            }

            2 -> {
                if (now - calibrationStartTime > 2000) {
                    val timestamp = System.currentTimeMillis()
                    loggedSensorData.add("END,$timestamp,$x,$y,$z")
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            300,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                    saveCalibrationLog()
                    statusText.text = "Kalibrierung abgeschlossen. Log gespeichert."
                    calibrationStep = 0
                    loggingExercise = ExerciseType.NONE
                }
            }

        }


    }

    private fun applyThresholdsFor(exercise: ExerciseType) {
        when (exercise) {
            ExerciseType.PUSHUP -> {
                ACC_THRESHOLD_UP = accUpPushup
                ACC_THRESHOLD_DOWN = accDownPushup
            }
            ExerciseType.SQUAT -> {
                ACC_THRESHOLD_UP = accUpSquat
                ACC_THRESHOLD_DOWN = accDownSquat
            }
            ExerciseType.PULLUP -> {
                ACC_THRESHOLD_UP = accUpPullup
                ACC_THRESHOLD_DOWN = accDownPullup
            }
            else -> {}
        }
    }


    private fun getProfileList(): List<String> {
        val dir = getExternalFilesDir(null) ?: return emptyList()
        return dir.listFiles()?.filter { it.extension == "json" }?.map { it.nameWithoutExtension } ?: emptyList()
    }

    private fun loadProfile(profile: ProfileEntity) {
        accDownPushup = profile.accDownPushup
        accUpPushup = profile.accUpPushup
        accDownSquat = profile.accDownSquat
        accUpSquat = profile.accUpSquat
        accDownPullup = profile.accDownPullup
        accUpPullup = profile.accUpPullup
        currentProfileName = profile.name
        applyThresholdsFor(ExerciseType.PUSHUP) // Standardwerte anwenden
        Toast.makeText(this, "Profil '${profile.name}' geladen", Toast.LENGTH_SHORT).show()
    }

    private fun sendMultiplayerProgress() {
        if (multiplayerMode && currentProfileName != null) {
            val message = "$currentProfileName|$pushUpCount|$squatCount|$pullUpCount"
            val topic = "25moagd/game/$currentProfileName"
            Log.d("MQTT-SENDER", "→ $topic: $message")
            mqttService?.sendMessage(topic, message)
            Log.d("MQTT", "Sende: $message auf $topic")
            Log.e("MQTT-SENDER", "Sende an Topic: $topic → $message")


        }
    }



    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY && !debugPocketOverride) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            val wasInPocket = isInPocket
            isInPocket = distance < maxRange
            statusText.text = if (isInPocket) "Tasche erkannt – bereit zur Übung" else "Nicht in der Tasche"
            if (workoutStarted) {
                if (isInPocket && !wasInPocket) resumeTimer()
                else if (!isInPocket && wasInPocket) {
                    pauseTimer()
                    showPauseDialog()
                }
            }
            return
        }

        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)

            currentAzimuth = Math.toDegrees(orientation[0].toDouble())
            currentPitch = Math.toDegrees(orientation[1].toDouble())
            currentRoll = Math.toDegrees(orientation[2].toDouble())

            angleXText.text = "X (Pitch): ${"%.1f".format(currentPitch)}°"
            angleYText.text = "Y (Roll):  ${"%.1f".format(currentRoll)}°"
            angleZText.text = "Z (Azimut): ${"%.1f".format(currentAzimuth)}°"
        }

        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val now = System.currentTimeMillis()

            if (debugMode && loggingExercise != ExerciseType.NONE) handleCalibrationLogging(x, y, z)
            if (isLogging) {
                val logLine = "${loggingExercise.name},$now,$x,$y,$z"
                loggedSensorData.add(logLine)
            }

            val totalForce = sqrt(x * x + y * y + z * z)
            val angle = Math.toDegrees(acos(z / totalForce.toDouble()))
            angleText.text = "Winkel: ${"%.1f".format(angle)}°"

            when (state) {
                State.WAITING -> {
                    if (currentPitch in 70.0..90.0 || currentPitch in 30.0..60.0) {
                        if (positionStartTime == 0L) positionStartTime = now
                        if (!hasConfirmedReady && now - positionStartTime > 1000) {
                            currentExercise = if (currentPitch in 30.0..60.0) ExerciseType.PUSHUP else ExerciseType.SQUAT
                            state = State.READY
                            stateChangeTime = System.currentTimeMillis() // Warten nicht so schnell
                            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                            statusText.text = "Bereit: ${currentExercise.name}"
                            hasConfirmedReady = true
                        }
                    } else {
                        positionStartTime = 0L
                        hasConfirmedReady = false
                    }
                }

                State.READY -> {
                    if (currentPitch in 30.0..60.0 && z < DOWN_THRESHOLD) {
                        currentExercise = ExerciseType.PUSHUP
                    } else if (currentPitch in 70.0..90.0 && z > UP_THRESHOLD) {
                        currentExercise = ExerciseType.PULLUP
                    } else if (currentPitch in 70.0..90.0 && z < DOWN_THRESHOLD) {
                        currentExercise = ExerciseType.SQUAT
                    } else {
                        return
                    }
                    goingDown = true
                    state = State.COUNTING
                    lastMotionTime = now
                    repInProgress = false
                    statusText.text = "Zählt: ${currentExercise.name}"
                }

                State.COUNTING -> {
                    if (inCalibration || isLogging || calibrationStep != 0) return
                    if (System.currentTimeMillis() - stateChangeTime < 1500) return

                    val movementDelta = z - lastZ
                    val now = System.currentTimeMillis()

                    // Nur wenn Bewegung signifikant (außerhalb der Deadzone)
                    if (abs(movementDelta) > MOTION_DEADZONE && now - lastRepTime > REP_MIN_DELAY_MS) {
                        when (currentExercise) {
                            ExerciseType.PUSHUP -> {
                                if (!goingDown && movementDelta < accDownPushup) goingDown = true
                                if (goingDown && movementDelta > accUpPushup) {
                                    goingDown = false
                                    lastRepTime = now
                                    pushUpCount++
                                    sendMultiplayerProgress() // NEU
                                    counterPushup.text = "Liegestütze: $pushUpCount"
                                    vibrator.vibrate(150)

                                }
                            }
                            ExerciseType.SQUAT -> {
                                if (!goingDown && movementDelta < accDownSquat) goingDown = true
                                if (goingDown && movementDelta > accUpSquat) {
                                    goingDown = false
                                    lastRepTime = now
                                    squatCount++
                                    sendMultiplayerProgress() // NEU

                                    counterSquat.text = "Kniebeugen: $squatCount"
                                    vibrator.vibrate(150)

                                }
                            }
                            ExerciseType.PULLUP -> {
                                if (!goingDown && movementDelta > accUpPullup) goingDown = true
                                if (goingDown && movementDelta < accDownPullup) {
                                    goingDown = false
                                    lastRepTime = now
                                    pullUpCount++
                                    sendMultiplayerProgress() // NEU
                                    counterPullup.text = "Klimmzüge: $pullUpCount"
                                    vibrator.vibrate(150)
                                }
                            }
                            else -> {}
                        }
                    }

                    if (now - lastMotionTime > MOTION_TIMEOUT) {
                        if (abs(currentPitch - lastPitch) > ANGLE_CHANGE_THRESHOLD ||
                            abs(currentRoll - lastRoll) > ANGLE_CHANGE_THRESHOLD) {
                            state = State.PAUSED
                            goingDown = false
                            repInProgress = false
                            statusText.text = "Pausiert: neue Position erwartet"
                        }
                    }
                }

                State.PAUSED -> {
                    if (angle !in 30.0..100.0) {
                        state = State.WAITING
                        currentExercise = ExerciseType.NONE
                        statusText.text = "Warte auf Position"
                    }
                    positionStartTime = 0L
                    hasConfirmedReady = false
                }
            }

            lastZ = z
            lastPitch = currentPitch
            lastRoll = currentRoll
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}