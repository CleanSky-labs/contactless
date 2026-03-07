package io.cleansky.contactless.model

import org.junit.Assert.*
import org.junit.Test

class PaymentStateTest {

    // --- isTerminal() tests ---

    @Test
    fun `REQUEST_CREATED is not terminal`() {
        assertFalse(PaymentState.REQUEST_CREATED.isTerminal())
    }

    @Test
    fun `REQUEST_SENT is not terminal`() {
        assertFalse(PaymentState.REQUEST_SENT.isTerminal())
    }

    @Test
    fun `AUTH_RECEIVED is not terminal`() {
        assertFalse(PaymentState.AUTH_RECEIVED.isTerminal())
    }

    @Test
    fun `TX_BROADCAST is not terminal`() {
        assertFalse(PaymentState.TX_BROADCAST.isTerminal())
    }

    @Test
    fun `TX_CONFIRMED is terminal`() {
        assertTrue(PaymentState.TX_CONFIRMED.isTerminal())
    }

    @Test
    fun `TX_FAILED is terminal`() {
        assertTrue(PaymentState.TX_FAILED.isTerminal())
    }

    // --- canTransitionTo() valid transitions ---

    @Test
    fun `REQUEST_CREATED can transition to REQUEST_SENT`() {
        assertTrue(PaymentState.REQUEST_CREATED.canTransitionTo(PaymentState.REQUEST_SENT))
    }

    @Test
    fun `REQUEST_CREATED can transition to TX_FAILED`() {
        assertTrue(PaymentState.REQUEST_CREATED.canTransitionTo(PaymentState.TX_FAILED))
    }

    @Test
    fun `REQUEST_SENT can transition to AUTH_RECEIVED`() {
        assertTrue(PaymentState.REQUEST_SENT.canTransitionTo(PaymentState.AUTH_RECEIVED))
    }

    @Test
    fun `REQUEST_SENT can transition to TX_FAILED`() {
        assertTrue(PaymentState.REQUEST_SENT.canTransitionTo(PaymentState.TX_FAILED))
    }

    @Test
    fun `AUTH_RECEIVED can transition to TX_BROADCAST`() {
        assertTrue(PaymentState.AUTH_RECEIVED.canTransitionTo(PaymentState.TX_BROADCAST))
    }

    @Test
    fun `AUTH_RECEIVED can transition to TX_FAILED`() {
        assertTrue(PaymentState.AUTH_RECEIVED.canTransitionTo(PaymentState.TX_FAILED))
    }

    @Test
    fun `TX_BROADCAST can transition to TX_CONFIRMED`() {
        assertTrue(PaymentState.TX_BROADCAST.canTransitionTo(PaymentState.TX_CONFIRMED))
    }

    @Test
    fun `TX_BROADCAST can transition to TX_FAILED`() {
        assertTrue(PaymentState.TX_BROADCAST.canTransitionTo(PaymentState.TX_FAILED))
    }

    // --- canTransitionTo() invalid transitions ---

    @Test
    fun `REQUEST_CREATED cannot transition to TX_CONFIRMED`() {
        assertFalse(PaymentState.REQUEST_CREATED.canTransitionTo(PaymentState.TX_CONFIRMED))
    }

    @Test
    fun `REQUEST_CREATED cannot transition to AUTH_RECEIVED`() {
        assertFalse(PaymentState.REQUEST_CREATED.canTransitionTo(PaymentState.AUTH_RECEIVED))
    }

    @Test
    fun `TX_CONFIRMED cannot transition to any state`() {
        PaymentState.values().forEach { state ->
            assertFalse(PaymentState.TX_CONFIRMED.canTransitionTo(state))
        }
    }

    @Test
    fun `TX_FAILED cannot transition to any state`() {
        PaymentState.values().forEach { state ->
            assertFalse(PaymentState.TX_FAILED.canTransitionTo(state))
        }
    }

    // --- PaymentStateHolder.transition() ---

    @Test
    fun `transition succeeds for valid state change`() {
        val holder = PaymentStateHolder(
            invoiceId = "inv-1",
            state = PaymentState.REQUEST_CREATED
        )
        val next = holder.transition(PaymentState.REQUEST_SENT)
        assertEquals(PaymentState.REQUEST_SENT, next.state)
        assertEquals("inv-1", next.invoiceId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `transition throws for invalid state change`() {
        val holder = PaymentStateHolder(
            invoiceId = "inv-1",
            state = PaymentState.REQUEST_CREATED
        )
        holder.transition(PaymentState.TX_CONFIRMED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `transition from terminal state throws`() {
        val holder = PaymentStateHolder(
            invoiceId = "inv-1",
            state = PaymentState.TX_CONFIRMED
        )
        holder.transition(PaymentState.TX_FAILED)
    }

    // --- PaymentStateHolder.isTimedOut() ---

    @Test
    fun `AUTH_RECEIVED is timed out after 30 seconds`() {
        val holder = PaymentStateHolder(
            invoiceId = "inv-1",
            state = PaymentState.AUTH_RECEIVED,
            enteredAt = System.currentTimeMillis() - 31_000L
        )
        assertTrue(holder.isTimedOut())
    }

    @Test
    fun `AUTH_RECEIVED is not timed out within 30 seconds`() {
        val holder = PaymentStateHolder(
            invoiceId = "inv-1",
            state = PaymentState.AUTH_RECEIVED,
            enteredAt = System.currentTimeMillis() - 10_000L
        )
        assertFalse(holder.isTimedOut())
    }

    @Test
    fun `TX_BROADCAST is timed out after 5 minutes`() {
        val holder = PaymentStateHolder(
            invoiceId = "inv-1",
            state = PaymentState.TX_BROADCAST,
            enteredAt = System.currentTimeMillis() - 301_000L
        )
        assertTrue(holder.isTimedOut())
    }

    @Test
    fun `TX_BROADCAST is not timed out within 5 minutes`() {
        val holder = PaymentStateHolder(
            invoiceId = "inv-1",
            state = PaymentState.TX_BROADCAST,
            enteredAt = System.currentTimeMillis() - 60_000L
        )
        assertFalse(holder.isTimedOut())
    }

    @Test
    fun `REQUEST_CREATED never times out`() {
        val holder = PaymentStateHolder(
            invoiceId = "inv-1",
            state = PaymentState.REQUEST_CREATED,
            enteredAt = 0L // Very old timestamp
        )
        assertFalse(holder.isTimedOut())
    }

    @Test
    fun `TX_CONFIRMED never times out`() {
        val holder = PaymentStateHolder(
            invoiceId = "inv-1",
            state = PaymentState.TX_CONFIRMED,
            enteredAt = 0L
        )
        assertFalse(holder.isTimedOut())
    }

    // --- Constants ---

    @Test
    fun `AUTH_RECEIVED timeout is 30 seconds`() {
        assertEquals(30_000L, PaymentState.AUTH_RECEIVED_TIMEOUT_MS)
    }

    @Test
    fun `TX_BROADCAST timeout is 5 minutes`() {
        assertEquals(300_000L, PaymentState.TX_BROADCAST_TIMEOUT_MS)
    }
}
