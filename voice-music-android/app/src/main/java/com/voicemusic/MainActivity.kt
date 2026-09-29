package com.voicemusic

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var microphoneButton: Button
    private lateinit var modelButton: Button
    private lateinit var modelProgress: ProgressBar
    private lateinit var notificationAccessButton: Button
    private lateinit var overlayButton: Button
    private lateinit var notificationsButton: Button
    private lateinit var wakePhrase: EditText
    private var downloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        toggle = findViewById(R.id.toggle_listening)
        microphoneButton = findViewById(R.id.setup_microphone)
        modelButton = findViewById(R.id.setup_model)
        modelProgress = findViewById(R.id.model_progress)
        notificationAccessButton = findViewById(R.id.setup_notification_access)
        overlayButton = findViewById(R.id.setup_overlay)
        notificationsButton = findViewById(R.id.setup_notifications)
        wakePhrase = findViewById(R.id.wake_phrase)

        wakePhrase.setText(Prefs.wakePhrase(this))

        toggle.setOnClickListener { toggleListening() }
        microphoneButton.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
        }
        modelButton.setOnClickListener { downloadModel() }
        notificationAccessButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        overlayButton.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        notificationsButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
        }
        findViewById<Button>(R.id.save_wake_phrase).setOnClickListener { saveWakePhrase() }

        val testInput = findViewById<EditText>(R.id.test_command)
        val testResult = findViewById<TextView>(R.id.test_result)
        findViewById<Button>(R.id.run_test_command).setOnClickListener {
            val command = CommandParser.parseBest(listOf(testInput.text.toString()))
            testResult.text = "$command\n→ ${YouTubeMusicController(this).execute(command)}"
        }
    }

    override fun onResume() {
        super.onResume()
        VoiceControlService.statusListener = { refresh() }
        refresh()
    }

    override fun onPause() {
        VoiceControlService.statusListener = null
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun refresh() {
        val micOk = hasMicrophone()
        val modelOk = ModelManager.isInstalled(this)
        val accessOk = MediaNotificationListener.isEnabled(this)
        val overlayOk = Settings.canDrawOverlays(this)
        val notificationsOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        setStep(microphoneButton, micOk, "Allow microphone", "Microphone allowed")
        if (!downloading) {
            setStep(modelButton, modelOk, "Download voice model (~40 MB)", "Voice model ready")
        }
        setStep(notificationAccessButton, accessOk, "Allow notification access (to control playback)", "Notification access allowed")
        setStep(overlayButton, overlayOk, "Allow display over other apps (to open YouTube Music)", "Can open YouTube Music")
        setStep(notificationsButton, notificationsOk, "Allow notifications (optional)", "Notifications allowed")

        val running = VoiceControlService.isRunning
        status.text = if (running) VoiceControlService.status else "Not listening"
        toggle.text = getString(if (running) R.string.stop_listening else R.string.start_listening)
        toggle.isEnabled = running || (micOk && modelOk)
    }

    private fun setStep(button: Button, done: Boolean, todo: String, doneText: String) {
        button.text = if (done) "✓ $doneText" else todo
        button.isEnabled = !done
    }

    private fun hasMicrophone() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun toggleListening() {
        if (VoiceControlService.isRunning) {
            VoiceControlService.stop(this)
        } else {
            VoiceControlService.start(this)
        }
        status.postDelayed({ refresh() }, 300)
    }

    private fun saveWakePhrase() {
        val phrase = Prefs.normalizePhrase(wakePhrase.text.toString())
        if (phrase.split(' ').size < 2) {
            Toast.makeText(this, "Use at least two words, like \"hey music\"", Toast.LENGTH_LONG).show()
            return
        }
        Prefs.setWakePhrase(this, phrase)
        wakePhrase.setText(phrase)
        Toast.makeText(this, "Wake phrase saved", Toast.LENGTH_SHORT).show()
        if (VoiceControlService.isRunning) {
            // Restart so the detector picks up the new phrase.
            VoiceControlService.stop(this)
            status.postDelayed({ VoiceControlService.start(this) }, 500)
        }
    }

    private fun downloadModel() {
        downloading = true
        modelButton.isEnabled = false
        modelButton.text = "Downloading voice model…"
        modelProgress.visibility = View.VISIBLE
        modelProgress.progress = 0
        Thread {
            val result = runCatching {
                ModelManager.download(applicationContext) { percent -> runOnUiThread { modelProgress.progress = percent } }
            }
            runOnUiThread {
                downloading = false
                modelProgress.visibility = View.GONE
                result.exceptionOrNull()?.let {
                    Toast.makeText(this, "Download failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
                refresh()
            }
        }.start()
    }

    private companion object {
        const val REQUEST_MICROPHONE = 1
        const val REQUEST_NOTIFICATIONS = 2
    }
}
