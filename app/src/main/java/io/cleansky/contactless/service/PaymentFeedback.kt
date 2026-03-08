package io.cleansky.contactless.service

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.delay

class PaymentFeedback(private val context: Context) {
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private var soundPool: SoundPool? = null
    private var successSoundId: Int = 0
    private var errorSoundId: Int = 0
    private var toneGenerator: ToneGenerator? = null

    init {
        initSoundPool()
    }

    private fun initSoundPool() {
        val audioAttributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

        soundPool =
            SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(audioAttributes)
                .build()

        try {
            toneGenerator = ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
        }
    }

    suspend fun onPaymentSuccess() {
        val pattern = longArrayOf(0, 100, 100, 100)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))

        playSuccessTone()
    }

    suspend fun onPaymentError() {
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))

        playErrorTone()
    }

    fun onPaymentRequestReceived() {
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun onNfcDetected() {
        vibrator.vibrate(VibrationEffect.createOneShot(30, 100))
    }

    private suspend fun playSuccessTone() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
            delay(200)
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        } catch (e: Exception) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 300)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun playErrorTone() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 500)
        } catch (e: Exception) {
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        toneGenerator?.release()
        toneGenerator = null
    }
}
