package mx.kompara.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AccessibilityServiceConfigTest {
    @Test
    fun `service declares screenshot reading without action capabilities`() {
        val xml = File("src/main/res/xml/kompara_accessibility_service.xml")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val service = factory.newDocumentBuilder().parse(xml).documentElement
        val android = "http://schemas.android.com/apk/res/android"

        assertEquals("accessibility-service", service.tagName)
        assertEquals("true", service.getAttributeNS(android, "canTakeScreenshot"))
        assertEquals("false", service.getAttributeNS(android, "isAccessibilityTool"))
        assertFalse(service.hasAttributeNS(android, "canPerformGestures"))
    }
}
