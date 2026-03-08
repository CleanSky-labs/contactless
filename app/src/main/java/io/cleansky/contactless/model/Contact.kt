package io.cleansky.contactless.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.json.JSONObject

/**
 * A contact in the address book.
 */
data class Contact(
    val address: String,
    val name: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("address", address)
            put("name", name)
            put("note", note)
            put("createdAt", createdAt)
            put("lastUsedAt", lastUsedAt)
        }
    }

    /**
     * Serialize contact info for NFC exchange (CBOR format).
     */
    fun toCbor(): ByteArray = cborMapper.writeValueAsBytes(ContactNfc(address, name))

    companion object {
        private val cborMapper = ObjectMapper(CBORFactory()).registerModule(KotlinModule.Builder().build())

        fun fromJson(json: JSONObject): Contact {
            return Contact(
                address = json.getString("address"),
                name = json.getString("name"),
                note = json.optString("note", ""),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                lastUsedAt = json.optLong("lastUsedAt", System.currentTimeMillis()),
            )
        }

        /**
         * Parse contact from NFC CBOR payload.
         */
        fun fromCbor(data: ByteArray): Contact? {
            return try {
                val nfc = cborMapper.readValue<ContactNfc>(data)
                if (nfc.address.isNotBlank()) {
                    Contact(
                        address = nfc.address,
                        name = nfc.name,
                        note = "",
                        createdAt = System.currentTimeMillis(),
                        lastUsedAt = System.currentTimeMillis(),
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Minimal contact data for NFC exchange (only address and name).
 */
data class ContactNfc(
    val address: String,
    val name: String,
)
