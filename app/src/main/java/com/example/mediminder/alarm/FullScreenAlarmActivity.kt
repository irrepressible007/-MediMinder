package com.example.mediminder.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mediminder.ui.theme.MediMinderTheme

class FullScreenAlarmActivity : ComponentActivity() {
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Wake up screen and bypass lockguard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        startNudgeSequence()

        val medicationId = intent.getLongExtra("MEDICATION_ID", -1L)

        setContent {
            MediMinderTheme {
                Column(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Time to take your medication!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(onClick = { 
                        stopNudgeSequence()
                        finish() 
                    }) {
                        Text("Log as Taken")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(onClick = { 
                        stopNudgeSequence()
                        finish() 
                    }) {
                        Text("Snooze (10 min)")
                    }
                }
            }
        }
    }

    private fun startNudgeSequence() {
        // Nudge sequence (Chime -> Haptic -> Alarm)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 500, 500, 500, 500)
            val amplitudes = intArrayOf(0, 255, 0, 255, 0)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 500, 500, 500, 500), 0)
        }
    }

    private fun stopNudgeSequence() {
        vibrator?.cancel()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopNudgeSequence()
    }
}
