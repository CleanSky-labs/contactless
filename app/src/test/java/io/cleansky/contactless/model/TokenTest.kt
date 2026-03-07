package io.cleansky.contactless.model

import org.junit.Assert.*
import org.junit.Test

class TokenTest {

    @Test
    fun `Token fromJson parses valid JSON`() {
        val json = """{"address":"0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913","symbol":"USDC","name":"USD Coin","decimals":6,"chainId":8453,"isNative":false}"""

        val token = Token.fromJson(json)

        assertNotNull(token)
        assertEquals("0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913", token?.address)
        assertEquals("USDC", token?.symbol)
        assertEquals("USD Coin", token?.name)
        assertEquals(6, token?.decimals)
        assertEquals(8453L, token?.chainId)
        assertEquals(false, token?.isNative)
    }

    @Test
    fun `Token fromJson returns null for invalid JSON`() {
        val invalidJson = "not valid json"

        val token = Token.fromJson(invalidJson)

        assertNull(token)
    }

    @Test
    fun `Token fromJson returns null for empty JSON`() {
        val emptyJson = ""

        val token = Token.fromJson(emptyJson)

        assertNull(token)
    }

    @Test
    fun `Token toJson produces valid JSON that can be parsed back`() {
        val original = Token(
            address = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
            symbol = "USDC",
            name = "USD Coin",
            decimals = 6,
            chainId = 1L,
            isNative = false
        )

        val json = original.toJson()
        val parsed = Token.fromJson(json)

        assertNotNull(parsed)
        assertEquals(original.address, parsed?.address)
        assertEquals(original.symbol, parsed?.symbol)
        assertEquals(original.name, parsed?.name)
        assertEquals(original.decimals, parsed?.decimals)
        assertEquals(original.chainId, parsed?.chainId)
        assertEquals(original.isNative, parsed?.isNative)
    }

    @Test
    fun `Token listFromJson parses valid JSON array`() {
        val json = """[
            {"address":"0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913","symbol":"USDC","name":"USD Coin","decimals":6,"chainId":8453,"isNative":false},
            {"address":"0x0000000000000000000000000000000000000000","symbol":"ETH","name":"Ethereum","decimals":18,"chainId":8453,"isNative":true}
        ]"""

        val tokens = Token.listFromJson(json)

        assertEquals(2, tokens.size)
        assertEquals("USDC", tokens[0].symbol)
        assertEquals("ETH", tokens[1].symbol)
        assertTrue(tokens[1].isNative)
    }

    @Test
    fun `Token listFromJson returns empty list for invalid JSON`() {
        val invalidJson = "not valid json"

        val tokens = Token.listFromJson(invalidJson)

        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `Token listToJson and listFromJson roundtrip`() {
        val original = listOf(
            Token("0xabc", "ABC", "Abc Token", 18, 1L, false),
            Token("0xdef", "DEF", "Def Token", 8, 1L, false)
        )

        val json = Token.listToJson(original)
        val parsed = Token.listFromJson(json)

        assertEquals(original.size, parsed.size)
        assertEquals(original[0].symbol, parsed[0].symbol)
        assertEquals(original[1].symbol, parsed[1].symbol)
    }

    @Test
    fun `Token matches returns true for matching address and chainId`() {
        val token = Token(
            address = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
            symbol = "USDC",
            name = "USD Coin",
            decimals = 6,
            chainId = 8453L
        )

        assertTrue(token.matches("0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913", 8453L))
    }

    @Test
    fun `Token matches is case insensitive for address`() {
        val token = Token(
            address = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
            symbol = "USDC",
            name = "USD Coin",
            decimals = 6,
            chainId = 8453L
        )

        assertTrue(token.matches("0x833589FCD6EDB6E08F4C7C32D4F71B54BDA02913", 8453L))
        assertTrue(token.matches("0x833589fcd6edb6e08f4c7c32d4f71b54bda02913", 8453L))
    }

    @Test
    fun `Token matches returns false for different chainId`() {
        val token = Token(
            address = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
            symbol = "USDC",
            name = "USD Coin",
            decimals = 6,
            chainId = 8453L
        )

        assertFalse(token.matches("0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913", 1L))
    }

    @Test
    fun `Token matches returns false for different address`() {
        val token = Token(
            address = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
            symbol = "USDC",
            name = "USD Coin",
            decimals = 6,
            chainId = 8453L
        )

        assertFalse(token.matches("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", 8453L))
    }

    @Test
    fun `Token NATIVE_ADDRESS constant is correct`() {
        assertEquals("0x0000000000000000000000000000000000000000", Token.NATIVE_ADDRESS)
        assertEquals(42, Token.NATIVE_ADDRESS.length)
    }

    @Test
    fun `Token isNative defaults to false`() {
        val token = Token(
            address = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
            symbol = "USDC",
            name = "USD Coin",
            decimals = 6,
            chainId = 8453L
        )

        assertFalse(token.isNative)
    }
}
