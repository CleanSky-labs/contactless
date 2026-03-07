package io.cleansky.contactless.crypto

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manejo de autenticación biométrica para proteger operaciones sensibles.
 */
class BiometricAuth(private val context: Context) {

    enum class BiometricStatus {
        AVAILABLE,
        NO_HARDWARE,
        HARDWARE_UNAVAILABLE,
        NONE_ENROLLED,
        SECURITY_UPDATE_REQUIRED,
        UNSUPPORTED
    }

    /**
     * Verifica si la autenticación biométrica está disponible
     */
    fun checkBiometricStatus(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)

        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricStatus.UNSUPPORTED
            else -> BiometricStatus.UNSUPPORTED
        }
    }

    /**
     * Verifica si hay biometría disponible (huella, cara, etc.)
     */
    fun isBiometricAvailable(): Boolean {
        return checkBiometricStatus() == BiometricStatus.AVAILABLE
    }

    /**
     * Solicita autenticación biométrica.
     * Suspende hasta que el usuario se autentique o cancele.
     *
     * @param activity La actividad desde donde se muestra el prompt
     * @param title Título del diálogo
     * @param subtitle Subtítulo opcional
     * @param description Descripción de por qué se necesita autenticación
     * @param negativeButtonText Texto del botón cancelar
     * @param allowDeviceCredential Si permite PIN/patrón como alternativa
     *
     * @return true si autenticación exitosa, false si falló o canceló
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Autenticacion requerida",
        subtitle: String? = null,
        description: String = "Confirma tu identidad para continuar",
        negativeButtonText: String = "Cancelar",
        allowDeviceCredential: Boolean = true
    ): AuthResult {
        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(context)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) {
                        continuation.resume(AuthResult.Success)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) {
                        val result = when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_CANCELED -> AuthResult.Cancelled

                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> AuthResult.Lockout(errString.toString())

                            else -> AuthResult.Error(errorCode, errString.toString())
                        }
                        continuation.resume(result)
                    }
                }

                override fun onAuthenticationFailed() {
                    // No hacer nada aquí - el usuario puede reintentar
                    // onAuthenticationError se llama si hay demasiados intentos
                }
            }

            val biometricPrompt = BiometricPrompt(activity, executor, callback)

            val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setDescription(description)

            subtitle?.let { promptInfoBuilder.setSubtitle(it) }

            if (allowDeviceCredential) {
                // Permitir PIN/patrón/contraseña como alternativa
                promptInfoBuilder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                promptInfoBuilder
                    .setNegativeButtonText(negativeButtonText)
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            }

            val promptInfo = promptInfoBuilder.build()

            continuation.invokeOnCancellation {
                // Cancelar el prompt si la coroutine se cancela
            }

            biometricPrompt.authenticate(promptInfo)
        }
    }

    sealed class AuthResult {
        object Success : AuthResult()
        object Cancelled : AuthResult()
        data class Error(val code: Int, val message: String) : AuthResult()
        data class Lockout(val message: String) : AuthResult()
    }
}
