package io.cleansky.contactless.model

import org.junit.Assert.*
import org.junit.Test

class ContactTest {
    @Test
    fun `Contact default note is empty string`() {
        val contact =
            Contact(
                address = "0x742d35Cc6634C0532925a3b844Bc9e7595f5bA5E",
                name = "Default Note Test",
            )

        assertEquals("", contact.note)
    }

    @Test
    fun `Contact createdAt and lastUsedAt have reasonable defaults`() {
        val before = System.currentTimeMillis()
        val contact =
            Contact(
                address = "0x742d35Cc6634C0532925a3b844Bc9e7595f5bA5E",
                name = "Timestamp Test",
            )
        val after = System.currentTimeMillis()

        assertTrue(contact.createdAt >= before)
        assertTrue(contact.createdAt <= after)
        assertTrue(contact.lastUsedAt >= before)
        assertTrue(contact.lastUsedAt <= after)
    }

    @Test
    fun `Contact handles unicode in name and note`() {
        val contact =
            Contact(
                address = "0x742d35Cc6634C0532925a3b844Bc9e7595f5bA5E",
                name = "Alice ",
                note = "Proveedor de tokio",
            )

        assertEquals("Alice ", contact.name)
        assertEquals("Proveedor de tokio", contact.note)
    }

    @Test
    fun `Contact data class equality works correctly`() {
        val contact1 =
            Contact(
                address = "0x742d35Cc6634C0532925a3b844Bc9e7595f5bA5E",
                name = "Alice",
                note = "Note",
                createdAt = 1000L,
                lastUsedAt = 2000L,
            )
        val contact2 =
            Contact(
                address = "0x742d35Cc6634C0532925a3b844Bc9e7595f5bA5E",
                name = "Alice",
                note = "Note",
                createdAt = 1000L,
                lastUsedAt = 2000L,
            )

        assertEquals(contact1, contact2)
        assertEquals(contact1.hashCode(), contact2.hashCode())
    }

    @Test
    fun `Contact copy works correctly`() {
        val original =
            Contact(
                address = "0x742d35Cc6634C0532925a3b844Bc9e7595f5bA5E",
                name = "Original",
                note = "Original note",
            )

        val updated = original.copy(name = "Updated", note = "Updated note")

        assertEquals("0x742d35Cc6634C0532925a3b844Bc9e7595f5bA5E", updated.address)
        assertEquals("Updated", updated.name)
        assertEquals("Updated note", updated.note)
        assertEquals(original.createdAt, updated.createdAt)
    }

    @Test
    fun `Contact inequality works for different addresses`() {
        val contact1 =
            Contact(
                address = "0x742d35Cc6634C0532925a3b844Bc9e7595f5bA5E",
                name = "Alice",
            )
        val contact2 =
            Contact(
                address = "0x123456789abcdef0123456789abcdef012345678",
                name = "Alice",
            )

        assertNotEquals(contact1, contact2)
    }

    @Test
    fun `Contact inequality works for different names`() {
        val contact1 =
            Contact(
                address = "0x742d35Cc6634C0532925a3b844Bc9e7595f5bA5E",
                name = "Alice",
            )
        val contact2 =
            Contact(
                address = "0x742d35Cc6634C0532925a3b844Bc9e7595f5bA5E",
                name = "Bob",
            )

        assertNotEquals(contact1, contact2)
    }

    @Test
    fun `Contact toString contains address and name`() {
        val contact =
            Contact(
                address = "0x742d35Cc6634C0532925a3b844Bc9e7595f5bA5E",
                name = "TestContact",
            )

        val str = contact.toString()
        assertTrue(str.contains("0x742d35Cc6634C0532925a3b844Bc9e7595f5bA5E"))
        assertTrue(str.contains("TestContact"))
    }
}
