package io.cleansky.contactless.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Almacenamiento seguro usando Android Keystore.
 *
 * Características:
 * - Claves almacenadas en hardware (TEE/StrongBox si disponible)
 * - Cifrado AES-256-GCM
 * - Claves no exportables
 * - Opcionalmente requiere autenticación del usuario
 */
class SecureKeyStore(private val context: Context) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "contactless_pay_master_key"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12

        // Prefijo para datos cifrados: IV (12 bytes) + datos cifrados
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    /**
     * Verifica si el dispositivo soporta StrongBox (hardware security module)
     */
    fun hasStrongBox(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
        } else {
            false
        }
    }

    /**
     * Verifica si hay una clave maestra creada
     */
    fun hasMasterKey(): Boolean {
        return keyStore.containsAlias(KEY_ALIAS)
    }

    /**
     * Crea la clave maestra en el Keystore.
     * Esta clave NUNCA sale del hardware seguro.
     *
     * @param requireUserAuth Si true, requiere biometría para usar la clave
     * @param authValiditySeconds Segundos que la autenticación es válida (0 = cada uso)
     */
    fun createMasterKey(
        requireUserAuth: Boolean = false,
        authValiditySeconds: Int = 0
    ): Boolean {
        if (hasMasterKey()) return true

        return try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val strongBoxSpec = buildKeyGenSpec(requireUserAuth, authValiditySeconds, useStrongBox = true)
            val fallbackSpec = buildKeyGenSpec(requireUserAuth, authValiditySeconds, useStrongBox = false)
            generateKeyWithFallback(keyGenerator, strongBoxSpec, fallbackSpec)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun buildKeyGenSpec(
        requireUserAuth: Boolean,
        authValiditySeconds: Int,
        useStrongBox: Boolean
    ): KeyGenParameterSpec {
        return KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(256)
            setRandomizedEncryptionRequired(true)
            applyAuthentication(requireUserAuth, authValiditySeconds)
            setInvalidatedByBiometricEnrollment(true)
            if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasStrongBox()) {
                setIsStrongBoxBacked(true)
            }
        }.build()
    }

    private fun KeyGenParameterSpec.Builder.applyAuthentication(
        requireUserAuth: Boolean,
        authValiditySeconds: Int
    ) {
        if (!requireUserAuth) return
        setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setUserAuthenticationParameters(
                authValiditySeconds,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            setUserAuthenticationValidityDurationSeconds(authValiditySeconds)
        }
    }

    private fun generateKeyWithFallback(
        keyGenerator: KeyGenerator,
        preferred: KeyGenParameterSpec,
        fallback: KeyGenParameterSpec
    ) {
        try {
            keyGenerator.init(preferred)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            val isStrongBoxUnavailable =
                e.javaClass.name == "android.security.keystore.StrongBoxUnavailableException"
            if (!isStrongBoxUnavailable) throw e
            keyGenerator.init(fallback)
            keyGenerator.generateKey()
        }
    }

    /**
     * Cifra datos usando la clave maestra del Keystore.
     * Retorna: IV (12 bytes) + datos cifrados, todo en Base64
     */
    fun encrypt(plaintext: ByteArray): String? {
        return try {
            val secretKey = getSecretKey() ?: return null
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv // IV generado automáticamente (12 bytes para GCM)
            val encryptedData = cipher.doFinal(plaintext)

            // Concatenar IV + datos cifrados
            val combined = ByteArray(iv.size + encryptedData.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Descifra datos usando la clave maestra del Keystore.
     * Espera: IV (12 bytes) + datos cifrados, todo en Base64
     */
    fun decrypt(encryptedBase64: String): ByteArray? {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)

            if (combined.size < GCM_IV_LENGTH) {
                return null
            }

            // Extraer IV y datos cifrados
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedData = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val secretKey = getSecretKey() ?: return null
            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Cifra un string (útil para claves privadas)
     */
    fun encryptString(plaintext: String): String? {
        return encrypt(plaintext.toByteArray(Charsets.UTF_8))
    }

    /**
     * Descifra a string
     */
    fun decryptString(encryptedBase64: String): String? {
        return decrypt(encryptedBase64)?.toString(Charsets.UTF_8)
    }

    /**
     * Elimina la clave maestra (CUIDADO: los datos cifrados serán irrecuperables)
     */
    fun deleteMasterKey() {
        try {
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
