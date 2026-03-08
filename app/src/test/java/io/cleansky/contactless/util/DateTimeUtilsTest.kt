package io.cleansky.contactless.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class DateTimeUtilsTest {
    @Test
    fun `formatLocalDateTime formats timestamp in local timezone`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val formatted = DateTimeUtils.formatLocalDateTime(0L)
            assertEquals("01/01/1970 00:00", formatted)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
