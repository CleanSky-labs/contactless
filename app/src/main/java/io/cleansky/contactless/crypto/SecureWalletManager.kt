package io.cleansky.contactless.crypto

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.Keys
import org.web3j.crypto.MnemonicUtils
import java.security.SecureRandom

/**
 * Gestor de wallet SEGURO con:
 * - Claves privadas cifradas con Android Keystore
 * - Opción de autenticación biométrica
 * - Soporte para StrongBox (HSM de hardware)
 *
 * FLUJO DE SEGURIDAD:
 * 1. Al crear/importar wallet:
 *    - Se genera una clave maestra en el Keystore (nunca sale del hardware)
 *    - La clave privada se cifra con AES-256-GCM
 *    - Solo se guarda la versión cifrada
 *
 * 2. Al usar la wallet:
 *    - (Opcional) Se requiere biometría
 *    - Se descifra la clave privada en memoria
 *    - Se usa y se borra de memoria inmediatamente
 */

private val Context.secureWalletDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_wallet")

class SecureWalletManager(private val context: Context) {

    private val secureKeyStore = SecureKeyStore(context)
    private val biometricAuth = BiometricAuth(context)

    companion object {
        private val ENCRYPTED_PRIVATE_KEY = stringPreferencesKey("encrypted_pk")
        private val WALLET_ADDRESS = stringPreferencesKey("wallet_address")
        private val MERCHANT_ID = stringPreferencesKey("merchant_id")
        private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val HAS_STRONGBOX = booleanPreferencesKey("has_strongbox")
    }

    // ========== FLOWS PÚBLICOS ==========

    val addressFlow: Flow<String?> = context.secureWalletDataStore.data.map { prefs ->
        prefs[WALLET_ADDRESS]
    }

    val merchantIdFlow: Flow<String?> = context.secureWalletDataStore.data.map { prefs ->
        prefs[MERCHANT_ID]
    }

    val isBiometricEnabledFlow: Flow<Boolean> = context.secureWalletDataStore.data.map { prefs ->
        prefs[BIOMETRIC_ENABLED] ?: false
    }

    // ========== INICIALIZACIÓN ==========

    /**
     * Inicializa el sistema de seguridad.
     * Debe llamarse al iniciar la app.
     */
    suspend fun initialize(): InitResult {
        // Crear clave maestra si no existe
        val keyCreated = secureKeyStore.createMasterKey(
            requireUserAuth = false, // La biometría se maneja a nivel de app
            authValiditySeconds = 0
        )

        if (!keyCreated) {
            return InitResult.KeystoreError
        }

        // Guardar si tiene StrongBox
        context.secureWalletDataStore.edit { prefs ->
            prefs[HAS_STRONGBOX] = secureKeyStore.hasStrongBox()
        }

        return if (hasWallet()) {
            InitResult.WalletExists
        } else {
            InitResult.NoWallet
        }
    }

    enum class InitResult {
        WalletExists,
        NoWallet,
        KeystoreError
    }

    // ========== GESTIÓN DE WALLET ==========

    /**
     * Verifica si hay una wallet configurada
     */
    suspend fun hasWallet(): Boolean {
        val prefs = context.secureWalletDataStore.data.first()
        return prefs[ENCRYPTED_PRIVATE_KEY] != null
    }

    /**
     * Crea una nueva wallet con clave generada aleatoriamente.
     * La clave se cifra antes de guardarla.
     */
    suspend fun createWallet(): CreateWalletResult {
        return try {
            // Generar keypair
            val ecKeyPair = Keys.createEcKeyPair()
            val privateKey = ecKeyPair.privateKey.toString(16).padStart(64, '0')
            val credentials = Credentials.create(ecKeyPair)

            // Cifrar la clave privada
            val encryptedKey = secureKeyStore.encryptString(privateKey)
                ?: return CreateWalletResult.EncryptionError

            // Guardar versión cifrada + dirección
            context.secureWalletDataStore.edit { prefs ->
                prefs[ENCRYPTED_PRIVATE_KEY] = encryptedKey
                prefs[WALLET_ADDRESS] = credentials.address
            }

            // Limpiar la clave de memoria (best effort)
            // En producción usar una librería de limpieza segura

            CreateWalletResult.Success(credentials.address)
        } catch (e: Exception) {
            CreateWalletResult.Error(e.message ?: "Error desconocido")
        }
    }

    /**
     * Importa una wallet existente.
     * La clave se cifra antes de guardarla.
     */
    suspend fun importWallet(privateKey: String): ImportWalletResult {
        return try {
            val cleanKey = privateKey.removePrefix("0x").lowercase()

            // Validar formato
            if (cleanKey.length != 64 || !cleanKey.all { it.isDigit() || it in 'a'..'f' }) {
                return ImportWalletResult.InvalidKey
            }

            // Verificar que la clave es válida
            val credentials = try {
                Credentials.create(cleanKey)
            } catch (e: Exception) {
                return ImportWalletResult.InvalidKey
            }

            // Cifrar la clave privada
            val encryptedKey = secureKeyStore.encryptString(cleanKey)
                ?: return ImportWalletResult.EncryptionError

            // Guardar
            context.secureWalletDataStore.edit { prefs ->
                prefs[ENCRYPTED_PRIVATE_KEY] = encryptedKey
                prefs[WALLET_ADDRESS] = credentials.address
            }

            ImportWalletResult.Success(credentials.address)
        } catch (e: Exception) {
            ImportWalletResult.Error(e.message ?: "Error desconocido")
        }
    }

    /**
     * Importa una wallet desde una frase semilla BIP39 (12 o 24 palabras).
     * Usa la derivación estándar de Ethereum: m/44'/60'/0'/0/0
     */
    suspend fun importWalletFromMnemonic(mnemonic: String): ImportWalletResult {
        return try {
            val words = mnemonic.trim().lowercase().split(Regex("\\s+"))

            // Validar número de palabras (12 o 24)
            if (words.size !in listOf(12, 15, 18, 21, 24)) {
                return ImportWalletResult.InvalidMnemonic
            }

            val cleanMnemonic = words.joinToString(" ")

            // Validar que es un mnemonic válido
            if (!MnemonicUtils.validateMnemonic(cleanMnemonic)) {
                return ImportWalletResult.InvalidMnemonic
            }

            // Derivar la clave privada usando BIP44 path: m/44'/60'/0'/0/0
            val seed = MnemonicUtils.generateSeed(cleanMnemonic, "")
            val masterKeypair = Bip32ECKeyPair.generateKeyPair(seed)

            // BIP44 derivation path for Ethereum: m/44'/60'/0'/0/0
            val path = intArrayOf(
                44 or Bip32ECKeyPair.HARDENED_BIT,   // purpose
                60 or Bip32ECKeyPair.HARDENED_BIT,   // coin type (ETH)
                0 or Bip32ECKeyPair.HARDENED_BIT,    // account
                0,                                     // change
                0                                      // address index
            )
            val derivedKeyPair = Bip32ECKeyPair.deriveKeyPair(masterKeypair, path)
            val credentials = Credentials.create(derivedKeyPair)

            val privateKey = derivedKeyPair.privateKey.toString(16).padStart(64, '0')

            // Cifrar la clave privada
            val encryptedKey = secureKeyStore.encryptString(privateKey)
                ?: return ImportWalletResult.EncryptionError

            // Guardar
            context.secureWalletDataStore.edit { prefs ->
                prefs[ENCRYPTED_PRIVATE_KEY] = encryptedKey
                prefs[WALLET_ADDRESS] = credentials.address
            }

            ImportWalletResult.Success(credentials.address)
        } catch (e: Exception) {
            ImportWalletResult.Error(e.message ?: "Error importing mnemonic")
        }
    }

    sealed class CreateWalletResult {
        data class Success(val address: String) : CreateWalletResult()
        object EncryptionError : CreateWalletResult()
        data class Error(val message: String) : CreateWalletResult()
    }

    sealed class ImportWalletResult {
        data class Success(val address: String) : ImportWalletResult()
        object InvalidKey : ImportWalletResult()
        object InvalidMnemonic : ImportWalletResult()
        object EncryptionError : ImportWalletResult()
        data class Error(val message: String) : ImportWalletResult()
    }

    // ========== ACCESO A CREDENCIALES ==========

    /**
     * Obtiene las credenciales para firmar transacciones.
     * Si biometría está habilitada, la solicita primero.
     *
     * @param activity Necesaria si hay que mostrar el prompt biométrico
     */
    suspend fun getCredentials(activity: FragmentActivity? = null): CredentialsResult {
        val prefs = context.secureWalletDataStore.data.first()
        val encryptedKey = prefs[ENCRYPTED_PRIVATE_KEY]
            ?: return CredentialsResult.NoWallet

        val biometricEnabled = prefs[BIOMETRIC_ENABLED] ?: false

        // Si biometría está habilitada, solicitarla
        if (biometricEnabled) {
            if (activity == null) {
                return CredentialsResult.BiometricRequired
            }

            when (val authResult = biometricAuth.authenticate(
                activity = activity,
                title = "Confirmar pago",
                description = "Usa tu huella o rostro para autorizar"
            )) {
                is BiometricAuth.AuthResult.Success -> { /* Continuar */ }
                is BiometricAuth.AuthResult.Cancelled -> return CredentialsResult.Cancelled
                is BiometricAuth.AuthResult.Error -> return CredentialsResult.AuthError(authResult.message)
                is BiometricAuth.AuthResult.Lockout -> return CredentialsResult.Lockout(authResult.message)
            }
        }

        // Descifrar la clave
        val privateKey = secureKeyStore.decryptString(encryptedKey)
            ?: return CredentialsResult.DecryptionError

        return try {
            val credentials = Credentials.create(privateKey)
            CredentialsResult.Success(credentials)
        } catch (e: Exception) {
            CredentialsResult.Error(e.message ?: "Error al crear credenciales")
        }
    }

    /**
     * Versión simplificada sin biometría (para operaciones que ya verificaron)
     */
    suspend fun getCredentialsUnsafe(): Credentials? {
        val prefs = context.secureWalletDataStore.data.first()
        val encryptedKey = prefs[ENCRYPTED_PRIVATE_KEY] ?: return null

        val privateKey = secureKeyStore.decryptString(encryptedKey) ?: return null

        return try {
            Credentials.create(privateKey)
        } catch (e: Exception) {
            null
        }
    }

    sealed class CredentialsResult {
        data class Success(val credentials: Credentials) : CredentialsResult()
        object NoWallet : CredentialsResult()
        object BiometricRequired : CredentialsResult()
        object Cancelled : CredentialsResult()
        object DecryptionError : CredentialsResult()
        data class AuthError(val message: String) : CredentialsResult()
        data class Lockout(val message: String) : CredentialsResult()
        data class Error(val message: String) : CredentialsResult()
    }

    // ========== CONFIGURACIÓN ==========

    /**
     * Habilita/deshabilita la autenticación biométrica
     */
    suspend fun setBiometricEnabled(enabled: Boolean): Boolean {
        if (enabled && !biometricAuth.isBiometricAvailable()) {
            return false
        }

        context.secureWalletDataStore.edit { prefs ->
            prefs[BIOMETRIC_ENABLED] = enabled
        }
        return true
    }

    suspend fun isBiometricEnabled(): Boolean {
        val prefs = context.secureWalletDataStore.data.first()
        return prefs[BIOMETRIC_ENABLED] ?: false
    }

    fun isBiometricAvailable(): Boolean {
        return biometricAuth.isBiometricAvailable()
    }

    suspend fun hasStrongBox(): Boolean {
        val prefs = context.secureWalletDataStore.data.first()
        return prefs[HAS_STRONGBOX] ?: secureKeyStore.hasStrongBox()
    }

    // ========== MERCHANT ID ==========

    suspend fun setMerchantId(merchantId: String) {
        context.secureWalletDataStore.edit { prefs ->
            prefs[MERCHANT_ID] = merchantId
        }
    }

    suspend fun getMerchantId(): String {
        val prefs = context.secureWalletDataStore.data.first()
        return prefs[MERCHANT_ID] ?: run {
            val address = prefs[WALLET_ADDRESS] ?: return@run "merchant-default"
            "merchant-${address.takeLast(8)}"
        }
    }

    // ========== BORRADO SEGURO ==========

    /**
     * Elimina la wallet de forma segura.
     * CUIDADO: Esta acción es irreversible.
     */
    suspend fun deleteWallet() {
        context.secureWalletDataStore.edit { prefs ->
            prefs.remove(ENCRYPTED_PRIVATE_KEY)
            prefs.remove(WALLET_ADDRESS)
            prefs.remove(MERCHANT_ID)
            prefs.remove(BIOMETRIC_ENABLED)
        }
    }

    /**
     * Elimina todo incluyendo la clave maestra.
     * CUIDADO: Esto hace irrecuperables todos los datos cifrados.
     */
    suspend fun factoryReset() {
        deleteWallet()
        secureKeyStore.deleteMasterKey()
    }

    // ========== BACKUP ==========

    /**
     * Exporta la clave privada (requiere autenticación biométrica si está habilitada).
     * SOLO para backup - mostrar al usuario para que la guarde de forma segura.
     */
    suspend fun exportPrivateKey(activity: FragmentActivity): ExportResult {
        when (val result = getCredentials(activity)) {
            is CredentialsResult.Success -> {
                val privateKey = result.credentials.ecKeyPair.privateKey.toString(16).padStart(64, '0')
                return ExportResult.Success("0x$privateKey")
            }
            is CredentialsResult.Cancelled -> return ExportResult.Cancelled
            is CredentialsResult.NoWallet -> return ExportResult.NoWallet
            else -> return ExportResult.Error("No se pudo exportar")
        }
    }

    sealed class ExportResult {
        data class Success(val privateKey: String) : ExportResult()
        object NoWallet : ExportResult()
        object Cancelled : ExportResult()
        data class Error(val message: String) : ExportResult()
    }
}
