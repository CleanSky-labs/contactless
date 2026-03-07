package io.cleansky.contactless.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.cleansky.contactless.model.Contact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.addressBookDataStore: DataStore<Preferences> by preferencesDataStore(name = "address_book")

class AddressBookRepository(private val context: Context) {

    private val CONTACTS_KEY = stringPreferencesKey("contacts")

    val contactsFlow: Flow<List<Contact>> = context.addressBookDataStore.data.map { prefs ->
        val json = prefs[CONTACTS_KEY] ?: "[]"
        parseContacts(json)
    }

    private fun parseContacts(json: String): List<Contact> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                Contact.fromJson(array.getJSONObject(i))
            }.sortedByDescending { it.lastUsedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun contactsToJson(contacts: List<Contact>): String {
        val array = JSONArray()
        contacts.forEach { array.put(it.toJson()) }
        return array.toString()
    }

    suspend fun addContact(contact: Contact) {
        context.addressBookDataStore.edit { prefs ->
            val current = parseContacts(prefs[CONTACTS_KEY] ?: "[]")
            // Remove existing contact with same address (case insensitive)
            val filtered = current.filter { !it.address.equals(contact.address, ignoreCase = true) }
            val updated = filtered + contact
            prefs[CONTACTS_KEY] = contactsToJson(updated)
        }
    }

    suspend fun updateContact(address: String, name: String, note: String) {
        context.addressBookDataStore.edit { prefs ->
            val current = parseContacts(prefs[CONTACTS_KEY] ?: "[]")
            val updated = current.map { contact ->
                if (contact.address.equals(address, ignoreCase = true)) {
                    contact.copy(name = name, note = note)
                } else {
                    contact
                }
            }
            prefs[CONTACTS_KEY] = contactsToJson(updated)
        }
    }

    suspend fun updateLastUsed(address: String) {
        context.addressBookDataStore.edit { prefs ->
            val current = parseContacts(prefs[CONTACTS_KEY] ?: "[]")
            val updated = current.map { contact ->
                if (contact.address.equals(address, ignoreCase = true)) {
                    contact.copy(lastUsedAt = System.currentTimeMillis())
                } else {
                    contact
                }
            }
            prefs[CONTACTS_KEY] = contactsToJson(updated)
        }
    }

    suspend fun removeContact(address: String) {
        context.addressBookDataStore.edit { prefs ->
            val current = parseContacts(prefs[CONTACTS_KEY] ?: "[]")
            val filtered = current.filter { !it.address.equals(address, ignoreCase = true) }
            prefs[CONTACTS_KEY] = contactsToJson(filtered)
        }
    }

    suspend fun getContact(address: String): Contact? {
        var result: Contact? = null
        context.addressBookDataStore.data.collect { prefs ->
            val contacts = parseContacts(prefs[CONTACTS_KEY] ?: "[]")
            result = contacts.find { it.address.equals(address, ignoreCase = true) }
        }
        return result
    }

    fun getContactFlow(address: String): Flow<Contact?> {
        return contactsFlow.map { contacts ->
            contacts.find { it.address.equals(address, ignoreCase = true) }
        }
    }
}
