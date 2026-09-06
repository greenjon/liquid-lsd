package llm.slop.liquidlsd.ui

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppIconResourceTest {
    @Test
    fun testAppIconResourcesExistAndAreValid() {
        val expectedSizes = listOf(16, 32, 48, 64, 128, 256, 512)
        for (size in expectedSizes) {
            val resourcePath = "/icons/icon-$size.png"
            val stream = javaClass.getResourceAsStream(resourcePath)
            assertNotNull(stream, "Missing icon resource: $resourcePath")
            val img = stream.use { ImageIO.read(it) }
            assertNotNull(img, "Failed to decode image: $resourcePath")
            assertEquals(size, img.width, "Width mismatch for $resourcePath")
            assertEquals(size, img.height, "Height mismatch for $resourcePath")
        }

        // Check master icon.png
        val masterStream = javaClass.getResourceAsStream("/icons/icon.png")
        assertNotNull(masterStream, "Missing master icon.png")
        val masterImg = masterStream.use { ImageIO.read(it) }
        assertNotNull(masterImg, "Failed to decode master icon.png")
        assertEquals(1024, masterImg.width)
        assertEquals(1024, masterImg.height)

        // Verify corner transparency on master icon
        val alphaCorner = (masterImg.getRGB(0, 0) ushr 24) and 0xFF
        assertEquals(0, alphaCorner, "Corner pixel (0,0) must have alpha 0")
    }
}
