package mx.kompara.parsers

import mx.kompara.data.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoOpOfferParserTest {

    private val parser = NoOpOfferParser()

    @Test
    fun `no-op parser recognizes nothing`() {
        assertNull(parser.parse(listOf("Uber", "\$120", "8.5 km")))
        assertNull(parser.parse(emptyList()))
    }

    @Test
    fun `no-op parser reports unknown platform`() {
        assertEquals(Platform.UNKNOWN, parser.platform)
    }
}
