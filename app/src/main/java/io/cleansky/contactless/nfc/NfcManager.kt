package io.cleansky.contactless.nfc

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Parcelable
import io.cleansky.contactless.model.Contact
import io.cleansky.contactless.model.PaymentRequest
import io.cleansky.contactless.model.SignedTransaction

/**
 * NFC Manager v0.6 - CBOR encoding with contact exchange
 */
class NfcManager(private val activity: Activity) {

    private var nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val isNfcAvailable: Boolean
        get() = nfcAdapter != null

    val isNfcEnabled: Boolean
        get() = nfcAdapter?.isEnabled == true

    private var onPaymentRequestReceived: ((PaymentRequest) -> Unit)? = null
    private var onSignedTransactionReceived: ((SignedTransaction) -> Unit)? = null
    private var onContactReceived: ((Contact) -> Unit)? = null
    private var pendingMessage: NdefMessage? = null

    companion object {
        // MIME types (CBOR only)
        const val MIME_REQUEST = "application/vnd.cleansky.contactless.request.cbor"
        const val MIME_SIGNED = "application/vnd.cleansky.contactless.signed.cbor"
        const val MIME_CONTACT = "application/vnd.cleansky.contactless.contact.cbor"
    }

    fun setOnPaymentRequestReceived(callback: (PaymentRequest) -> Unit) {
        onPaymentRequestReceived = callback
    }

    fun setOnSignedTransactionReceived(callback: (SignedTransaction) -> Unit) {
        onSignedTransactionReceived = callback
    }

    fun setOnContactReceived(callback: (Contact) -> Unit) {
        onContactReceived = callback
    }

    fun clearOnContactReceived() {
        onContactReceived = null
    }

    fun enableForegroundDispatch() {
        nfcAdapter?.let { adapter ->
            val intent = Intent(activity, activity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                activity, 0, intent,
                android.app.PendingIntent.FLAG_MUTABLE
            )
            adapter.enableForegroundDispatch(activity, pendingIntent, null, null)
        }
    }

    fun disableForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(activity)
    }

    /**
     * Prepare PaymentRequest for NFC transmission
     */
    fun preparePaymentRequest(request: PaymentRequest) {
        val record = NdefRecord.createMime(MIME_REQUEST, request.toCbor())
        pendingMessage = NdefMessage(arrayOf(record))

        nfcAdapter?.enableReaderMode(
            activity,
            { tag -> writeToTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V,
            null
        )
    }

    /**
     * Prepare SignedTransaction for NFC transmission
     */
    fun prepareSignedTransaction(signedTx: SignedTransaction) {
        val record = NdefRecord.createMime(MIME_SIGNED, signedTx.toCbor())
        pendingMessage = NdefMessage(arrayOf(record))

        nfcAdapter?.enableReaderMode(
            activity,
            { tag -> writeToTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V,
            null
        )
    }

    /**
     * Prepare Contact for NFC transmission (contact exchange)
     */
    fun prepareContact(contact: Contact) {
        val record = NdefRecord.createMime(MIME_CONTACT, contact.toCbor())
        pendingMessage = NdefMessage(arrayOf(record))

        nfcAdapter?.enableReaderMode(
            activity,
            { tag -> writeToTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V,
            null
        )
    }

    fun stopBroadcast() {
        pendingMessage = null
        nfcAdapter?.disableReaderMode(activity)
    }

    private fun writeToTag(tag: Tag) {
        pendingMessage?.let { message ->
            try {
                val ndef = Ndef.get(tag)
                if (ndef != null) {
                    ndef.connect()
                    if (ndef.isWritable) {
                        ndef.writeNdefMessage(message)
                    }
                    ndef.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun handleIntent(intent: Intent) {
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action
        ) {
            val rawMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, Parcelable::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            }
            rawMessages?.let { messages ->
                for (rawMessage in messages) {
                    val ndefMessage = rawMessage as NdefMessage
                    for (record in ndefMessage.records) {
                        processNdefRecord(record)
                    }
                }
            }

            // Also try to read directly from tag
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            }
            tag?.let { readFromTag(it) }
        }
    }

    private fun readFromTag(tag: Tag) {
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                val ndefMessage = ndef.cachedNdefMessage
                ndefMessage?.records?.forEach { record ->
                    processNdefRecord(record)
                }
                ndef.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processNdefRecord(record: NdefRecord) {
        val mimeType = String(record.type, Charsets.UTF_8)
        val payload = record.payload

        when {
            // Contact exchange
            mimeType == MIME_CONTACT || mimeType.contains("contact") -> {
                Contact.fromCbor(payload)?.let { contact ->
                    onContactReceived?.invoke(contact)
                }
            }
            // PaymentRequest
            mimeType == MIME_REQUEST || mimeType.contains("request") -> {
                PaymentRequest.parse(payload)?.let { request ->
                    onPaymentRequestReceived?.invoke(request)
                }
            }
            // SignedTransaction
            mimeType == MIME_SIGNED || mimeType.contains("signed") -> {
                SignedTransaction.parse(payload)?.let { signedTx ->
                    onSignedTransactionReceived?.invoke(signedTx)
                }
            }
            // Unknown - try auto-detection
            else -> {
                // Try contact first (simplest format)
                Contact.fromCbor(payload)?.let { contact ->
                    onContactReceived?.invoke(contact)
                    return
                }
                PaymentRequest.parse(payload)?.let { request ->
                    onPaymentRequestReceived?.invoke(request)
                    return
                }
                SignedTransaction.parse(payload)?.let { signedTx ->
                    onSignedTransactionReceived?.invoke(signedTx)
                }
            }
        }
    }
}
