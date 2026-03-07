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

/**
 * Feedback de confirmación tipo TPV/Terminal de pago
 * - Vibración
 * - Sonido de confirmación
 */
class PaymentFeedback(private val context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()

        try {
            toneGenerator = ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            // Ignorar si no se puede crear
        }
    }

    /**
     * Feedback de pago exitoso - tipo TPV
     * Doble vibración corta + sonido agudo
     */
    suspend fun onPaymentSuccess() {
        // Vibración de éxito: dos pulsos cortos
        val pattern = longArrayOf(0, 100, 100, 100)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))

        // Sonido de éxito (dos tonos ascendentes)
        playSuccessTone()
    }

    /**
     * Feedback de error
     * Vibración larga + sonido grave
     */
    suspend fun onPaymentError() {
        // Vibración de error: pulso largo
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))

        // Sonido de error
        playErrorTone()
    }

    /**
     * Feedback cuando se recibe una solicitud de pago
     * Vibración suave para alertar
     */
    fun onPaymentRequestReceived() {
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /**
     * Feedback cuando se detecta NFC
     */
    fun onNfcDetected() {
        vibrator.vibrate(VibrationEffect.createOneShot(30, 100))
    }

    private suspend fun playSuccessTone() {
        try {
            // Tono 1: nota alta
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
            delay(200)
            // Tono 2: nota más alta
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        } catch (e: Exception) {
            // Fallback: usar beep simple
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 300)
            } catch (_: Exception) {}
        }
    }

    private suspend fun playErrorTone() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 500)
        } catch (e: Exception) {
            // Ignorar
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        toneGenerator?.release()
        toneGenerator = null
    }
}
