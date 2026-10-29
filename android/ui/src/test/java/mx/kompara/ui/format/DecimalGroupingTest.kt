package mx.kompara.ui.format

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.*
import org.junit.Test

class DecimalGroupingTest {
    @Test fun `grouped editable money retains every decimal and cursor boundary`() {
        for ((raw, display) in listOf("5340" to "5,340", "5340.25" to "5,340.25", "1234567." to "1,234,567.", "0.00" to "0.00")) {
            val result = DecimalGrouping.filter(AnnotatedString(raw))
            assertEquals(display, result.text.text)
            for (offset in 0..raw.length) assertEquals(offset,
                result.offsetMapping.transformedToOriginal(result.offsetMapping.originalToTransformed(offset)))
            for (offset in 0..display.length) assertTrue(result.offsetMapping.transformedToOriginal(offset) in 0..raw.length)
        }
    }
    @Test fun `localized signed incomplete or pasted entries stay editable without coercion`() {
        for (raw in listOf("", "−25.50", "-5340", "6,50", "$5,340.25", ".")) {
            val result = DecimalGrouping.filter(AnnotatedString(raw))
            assertEquals(raw, result.text.text)
            for (offset in 0..raw.length) assertEquals(offset, result.offsetMapping.originalToTransformed(offset))
        }
    }
}
