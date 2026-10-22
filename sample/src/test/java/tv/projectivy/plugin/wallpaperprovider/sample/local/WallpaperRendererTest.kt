package tv.projectivy.plugin.wallpaperprovider.sample.local

import android.app.Application
import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperRendererTest {
    @Test fun createsValid4kJpegWithMetadataAndTitleFallback() {
        val backdrop = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(backdrop)
        canvas.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 1920f, 1080f,
            intArrayOf(Color.rgb(12, 30, 60), Color.rgb(30, 120, 145), Color.rgb(220, 130, 65)), null, Shader.TileMode.CLAMP) })
        canvas.drawCircle(1500f, 280f, 150f, Paint().apply { color = Color.rgb(230, 190, 100) })
        val file = File("build/reports/renderer-preview.jpg")
        try {
            val details = TmdbDetails(1, "My Trakt Wallpapers", 2026, "https://example.org/art.jpg", null,
                "This is a local render test. Real wallpapers use artwork and details from your selected movies and TV series.",
                8.4, 125, listOf("Adventure", "Science Fiction"), "4K on your Android TV")
            WallpaperRenderer().render(backdrop, null, file, details)
            val decoded = BitmapFactory.decodeFile(file.absolutePath)
            assertNotNull(decoded)
            assertEquals(3840, decoded.width)
            assertEquals(2160, decoded.height)
            assertEquals(Color.BLACK, decoded.getPixel(10, 2100))
            assertNotEquals(Color.BLACK, decoded.getPixel(3000, 500))
            // The title fallback must paint visible light text in the original logo box.
            var bright = 0
            for (y in 275 until 600 step 3) for (x in 380 until 1420 step 3) {
                val pixel = decoded.getPixel(x, y)
                if (Color.red(pixel) > 180 && Color.green(pixel) > 180 && Color.blue(pixel) > 180) bright++
            }
            assertTrue("Missing title fallback", bright > 100)
            decoded.recycle()
        } finally { backdrop.recycle() }
    }
}
