package io.cleansky.contactless.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.DecimalFormatSymbols
import java.util.Locale

class AmountUtilsTest {
    private val decSep = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator

    @Test
    fun `parseToUnitsOrZero parses decimal input`() {
        val parsed = AmountUtils.parseToUnitsOrZero("1.5", 6)
        assertEquals("1500000", parsed.toString())
    }

    @Test
    fun `parseToUnitsOrZero returns zero for invalid input`() {
        val parsed = AmountUtils.parseToUnitsOrZero("invalid", 6)
        assertEquals("0", parsed.toString())
    }

    @Test
    fun `parseToUnitsOrZero returns zero for blank input`() {
        val parsed = AmountUtils.parseToUnitsOrZero("   ", 6)
        assertEquals("0", parsed.toString())
    }

    @Test
    fun `parseToUnitsOrZero returns zero for negative decimals`() {
        val parsed = AmountUtils.parseToUnitsOrZero("1.5", -1)
        assertEquals("0", parsed.toString())
    }

    @Test
    fun `parseToUnitsOrZero returns zero for negative amount`() {
        val parsed = AmountUtils.parseToUnitsOrZero("-1.5", 6)
        assertEquals("0", parsed.toString())
    }

    @Test
    fun `formatRawAmountOrPlaceholder formats integer raw amount`() {
        val formatted = AmountUtils.formatRawAmountOrPlaceholder("1500000", 6)
        assertEquals("1${decSep}50", formatted)
    }

    @Test
    fun `formatRawAmountOrPlaceholder returns placeholder for null amount`() {
        val formatted = AmountUtils.formatRawAmountOrPlaceholder(null, 6)
        assertEquals("...", formatted)
    }

    @Test
    fun `formatRawAmountOrPlaceholder returns placeholder for invalid amount`() {
        val formatted = AmountUtils.formatRawAmountOrPlaceholder("invalid", 6)
        assertEquals("...", formatted)
    }
}
