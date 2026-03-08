package io.cleansky.contactless.crypto

import org.junit.Assert.*
import org.junit.Test
import org.web3j.crypto.Credentials
import java.math.BigInteger

class EphemeralAccountTest {
    private val mainCredentials =
        Credentials.create(
            "0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce036f0e6c58f6e8f7a66f5",
        )

    @Test
    fun `deriveEphemeralAccount is deterministic for same index`() {
        val account1 = EphemeralAccount.deriveEphemeralAccount(mainCredentials, paymentIndex = 123L)
        val account2 = EphemeralAccount.deriveEphemeralAccount(mainCredentials, paymentIndex = 123L)

        assertEquals(account1.address, account2.address)
        assertEquals(account1.ownerCredentials.address, account2.ownerCredentials.address)
        assertEquals(account1.salt, account2.salt)
        assertEquals(account1.initCode, account2.initCode)
    }

    @Test
    fun `deriveEphemeralAccount changes with different index`() {
        val account1 = EphemeralAccount.deriveEphemeralAccount(mainCredentials, paymentIndex = 1L)
        val account2 = EphemeralAccount.deriveEphemeralAccount(mainCredentials, paymentIndex = 2L)

        assertNotEquals(account1.address, account2.address)
        assertNotEquals(account1.ownerCredentials.address, account2.ownerCredentials.address)
    }

    @Test
    fun `derived initCode includes factory and create selector`() {
        val account = EphemeralAccount.deriveEphemeralAccount(mainCredentials, paymentIndex = 99L)

        assertTrue(account.initCode.startsWith(EphemeralAccount.SIMPLE_ACCOUNT_FACTORY))
        assertTrue(account.initCode.contains("5fbfb9cf"))
    }

    @Test
    fun `createPaymentUserOp native transfer sets deploy gas profile when not deployed`() {
        val account = EphemeralAccount.deriveEphemeralAccount(mainCredentials, paymentIndex = 7L)
        val userOp =
            EphemeralAccount.createPaymentUserOp(
                ephemeralAccount = account,
                targetAddress = "0x1111111111111111111111111111111111111111",
                tokenAddress = "0x0000000000000000000000000000000000000000",
                amount = BigInteger("1000000"),
                nonce = BigInteger.valueOf(5),
                isDeployed = false,
            )

        assertEquals(account.address, userOp.sender)
        assertEquals("0x5", userOp.nonce)
        assertEquals(account.initCode, userOp.initCode)
        assertTrue(userOp.callData.startsWith("0xb61d27f6"))
        assertEquals("0x60000", userOp.verificationGasLimit)
    }

    @Test
    fun `createPaymentUserOp erc20 transfer embeds transfer selector`() {
        val account = EphemeralAccount.deriveEphemeralAccount(mainCredentials, paymentIndex = 8L)
        val userOp =
            EphemeralAccount.createPaymentUserOp(
                ephemeralAccount = account,
                targetAddress = "0x2222222222222222222222222222222222222222",
                tokenAddress = "0x3333333333333333333333333333333333333333",
                amount = BigInteger("42"),
                isDeployed = true,
            )

        assertEquals("0x", userOp.initCode)
        assertEquals("0x20000", userOp.verificationGasLimit)
        assertTrue(userOp.callData.startsWith("0xb61d27f6"))
        assertTrue(userOp.callData.contains("a9059cbb"))
    }

    @Test
    fun `signUserOperation creates 65-byte signature in hex`() {
        val account = EphemeralAccount.deriveEphemeralAccount(mainCredentials, paymentIndex = 77L)
        val userOp =
            EphemeralAccount.createPaymentUserOp(
                ephemeralAccount = account,
                targetAddress = "0x4444444444444444444444444444444444444444",
                tokenAddress = "native",
                amount = BigInteger("1"),
            )

        val signed = EphemeralAccount.signUserOperation(userOp, account.ownerCredentials, chainId = 84532L)

        assertTrue(signed.signature.startsWith("0x"))
        assertEquals(132, signed.signature.length)
        assertNotEquals("0x", signed.signature)
    }

    @Test
    fun `signUserOperation changes signature with different chainId`() {
        val account = EphemeralAccount.deriveEphemeralAccount(mainCredentials, paymentIndex = 88L)
        val userOp =
            EphemeralAccount.createPaymentUserOp(
                ephemeralAccount = account,
                targetAddress = "0x5555555555555555555555555555555555555555",
                tokenAddress = "native",
                amount = BigInteger("10"),
            )

        val signedOnBase = EphemeralAccount.signUserOperation(userOp, account.ownerCredentials, chainId = 84532L)
        val signedOnMainnet = EphemeralAccount.signUserOperation(userOp, account.ownerCredentials, chainId = 1L)

        assertNotEquals(signedOnBase.signature, signedOnMainnet.signature)
    }
}
