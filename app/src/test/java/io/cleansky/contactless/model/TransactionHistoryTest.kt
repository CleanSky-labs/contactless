package io.cleansky.contactless.model

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class TransactionHistoryTest {

    private fun createTransaction(
        amount: String = "1000000",
        refundedAmount: String = "0",
        type: TransactionType = TransactionType.PAYMENT_RECEIVED,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
        refundTxHashes: List<String> = emptyList()
    ) = Transaction(
        id = "test-id",
        txHash = "0xabc123",
        type = type,
        status = status,
        amount = amount,
        refundedAmount = refundedAmount,
        asset = "0xtoken",
        chainId = 8453L,
        counterparty = "0xcounterparty",
        merchantId = "merchant-1",
        invoiceId = "inv-1",
        timestamp = 1700000000L,
        refundTxHashes = refundTxHashes
    )

    // --- getRemainingRefundable() ---

    @Test
    fun `getRemainingRefundable returns full amount when no refunds`() {
        val tx = createTransaction(amount = "1000000", refundedAmount = "0")
        assertEquals(BigInteger("1000000"), tx.getRemainingRefundable())
    }

    @Test
    fun `getRemainingRefundable returns difference after partial refund`() {
        val tx = createTransaction(amount = "1000000", refundedAmount = "400000")
        assertEquals(BigInteger("600000"), tx.getRemainingRefundable())
    }

    @Test
    fun `getRemainingRefundable returns zero when fully refunded`() {
        val tx = createTransaction(amount = "1000000", refundedAmount = "1000000")
        assertEquals(BigInteger.ZERO, tx.getRemainingRefundable())
    }

    @Test
    fun `getRemainingRefundable never returns negative value`() {
        // Given
        val tx = createTransaction(amount = "1000000", refundedAmount = "1500000")

        // When
        val remaining = tx.getRemainingRefundable()

        // Then
        assertEquals(BigInteger.ZERO, remaining)
    }

    // --- canRefund() ---

    @Test
    fun `canRefund returns true for PAYMENT_RECEIVED with CONFIRMED status`() {
        val tx = createTransaction(type = TransactionType.PAYMENT_RECEIVED, status = TransactionStatus.CONFIRMED)
        assertTrue(tx.canRefund())
    }

    @Test
    fun `canRefund returns true for PAYMENT_SENT with CONFIRMED status`() {
        val tx = createTransaction(type = TransactionType.PAYMENT_SENT, status = TransactionStatus.CONFIRMED)
        assertTrue(tx.canRefund())
    }

    @Test
    fun `canRefund returns true for PARTIALLY_REFUNDED status`() {
        val tx = createTransaction(
            status = TransactionStatus.PARTIALLY_REFUNDED,
            refundedAmount = "500000"
        )
        assertTrue(tx.canRefund())
    }

    @Test
    fun `canRefund returns false for REFUNDED status`() {
        val tx = createTransaction(
            status = TransactionStatus.REFUNDED,
            amount = "1000000",
            refundedAmount = "1000000"
        )
        assertFalse(tx.canRefund())
    }

    @Test
    fun `canRefund returns false for REFUND_SENT type`() {
        val tx = createTransaction(type = TransactionType.REFUND_SENT)
        assertFalse(tx.canRefund())
    }

    @Test
    fun `canRefund returns false for REFUND_RECEIVED type`() {
        val tx = createTransaction(type = TransactionType.REFUND_RECEIVED)
        assertFalse(tx.canRefund())
    }

    @Test
    fun `canRefund returns true for FAILED status with remaining amount`() {
        // canRefund only checks type and REFUNDED status, not FAILED
        val tx = createTransaction(status = TransactionStatus.FAILED)
        assertTrue(tx.canRefund())
    }

    // --- withRefund() ---

    @Test
    fun `withRefund partial refund sets PARTIALLY_REFUNDED`() {
        val tx = createTransaction(amount = "1000000")
        val refunded = tx.withRefund(BigInteger("400000"), "0xrefund1")

        assertEquals(TransactionStatus.PARTIALLY_REFUNDED, refunded.status)
        assertEquals("400000", refunded.refundedAmount)
    }

    @Test
    fun `withRefund full refund sets REFUNDED`() {
        val tx = createTransaction(amount = "1000000")
        val refunded = tx.withRefund(BigInteger("1000000"), "0xrefund1")

        assertEquals(TransactionStatus.REFUNDED, refunded.status)
        assertEquals("1000000", refunded.refundedAmount)
    }

    @Test
    fun `withRefund accumulates refund amounts`() {
        val tx = createTransaction(amount = "1000000")
        val first = tx.withRefund(BigInteger("300000"), "0xrefund1")
        val second = first.withRefund(BigInteger("700000"), "0xrefund2")

        assertEquals(TransactionStatus.REFUNDED, second.status)
        assertEquals("1000000", second.refundedAmount)
    }

    @Test
    fun `withRefund caps refunded amount at original amount`() {
        // Given
        val tx = createTransaction(amount = "1000000", refundedAmount = "900000")

        // When
        val refunded = tx.withRefund(BigInteger("500000"), "0xrefund-over")

        // Then
        assertEquals(TransactionStatus.REFUNDED, refunded.status)
        assertEquals("1000000", refunded.refundedAmount)
    }

    @Test
    fun `withRefund accumulates txHashes`() {
        val tx = createTransaction(amount = "1000000")
        val first = tx.withRefund(BigInteger("300000"), "0xrefund1")
        val second = first.withRefund(BigInteger("200000"), "0xrefund2")

        assertEquals(listOf("0xrefund1", "0xrefund2"), second.refundTxHashes)
    }

    @Test
    fun `withRefund preserves other fields`() {
        val tx = createTransaction(amount = "1000000")
        val refunded = tx.withRefund(BigInteger("100000"), "0xrefund1")

        assertEquals(tx.id, refunded.id)
        assertEquals(tx.txHash, refunded.txHash)
        assertEquals(tx.type, refunded.type)
        assertEquals(tx.asset, refunded.asset)
        assertEquals(tx.chainId, refunded.chainId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `withRefund rejects non-positive amounts`() {
        // Given
        val tx = createTransaction(amount = "1000000")

        // When
        tx.withRefund(BigInteger.ZERO, "0xrefund1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `withRefund rejects blank tx hash`() {
        // Given
        val tx = createTransaction(amount = "1000000")

        // When
        tx.withRefund(BigInteger("1"), "   ")
    }

    // --- JSON serialization ---

    @Test
    fun `toJson and fromJson round-trip preserves data`() {
        val tx = createTransaction(refundTxHashes = listOf("0xrefund1"))
        val json = tx.toJson()
        val parsed = Transaction.fromJson(json)

        assertNotNull(parsed)
        assertEquals(tx.id, parsed!!.id)
        assertEquals(tx.txHash, parsed.txHash)
        assertEquals(tx.amount, parsed.amount)
        assertEquals(tx.refundedAmount, parsed.refundedAmount)
        assertEquals(tx.type, parsed.type)
        assertEquals(tx.status, parsed.status)
        assertEquals(tx.refundTxHashes, parsed.refundTxHashes)
    }

    @Test
    fun `fromJson returns null for invalid JSON`() {
        assertNull(Transaction.fromJson("not json"))
    }

    @Test
    fun `listFromJson parses array of transactions`() {
        val txs = listOf(
            createTransaction(amount = "100"),
            createTransaction(amount = "200")
        )
        val json = Transaction.listToJson(txs)
        val parsed = Transaction.listFromJson(json)

        assertEquals(2, parsed.size)
        assertEquals("100", parsed[0].amount)
        assertEquals("200", parsed[1].amount)
    }

    @Test
    fun `listFromJson returns empty list for invalid JSON`() {
        val result = Transaction.listFromJson("invalid")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listToJson and listFromJson round-trip`() {
        val txs = listOf(createTransaction(), createTransaction(amount = "999"))
        val json = Transaction.listToJson(txs)
        val parsed = Transaction.listFromJson(json)

        assertEquals(txs.size, parsed.size)
        assertEquals(txs[0].amount, parsed[0].amount)
        assertEquals(txs[1].amount, parsed[1].amount)
    }
}
