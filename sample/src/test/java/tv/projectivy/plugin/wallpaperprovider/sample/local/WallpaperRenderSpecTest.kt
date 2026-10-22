// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample.local

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class WallpaperRenderSpecTest {
    @Test
    fun logoBoxMatchesRequestedTopLeftComposition() {
        val spec = WallpaperRenderSpec.default()
        assertRectEquals(RectF(380f, 250f, 1420f, 620f), spec.logoBox)
    }

    @Test
    fun coverRectBiasesBackdropToUpperRight() {
        val spec = WallpaperRenderSpec.default()
        val rect = spec.coverCropRect(sourceWidth = 1920, sourceHeight = 1080)
        // The original 0.72 right bias crops 118.4 pixels from the left of the scaled image.
        assertRectEquals(RectF(401.60f, 0f, 3886.04f, 1960f), rect)
    }

    @Test
    fun fullCanvasUnderlayStartsAtScreenEdge() {
        val spec = WallpaperRenderSpec.default()
        val rect = spec.fullCanvasCoverRect(sourceWidth = 1920, sourceHeight = 1080)
        assertRectEquals(RectF(0f, 0f, 3840f, 2160f), rect)
    }

    @Test
    fun bottomGradientCoversLauncherRows() {
        val spec = WallpaperRenderSpec.default()
        assertEquals(1320f, spec.bottomGradientStartY, 0.01f)
        assertEquals(1740f, spec.bottomGradientStrongUntilY, 0.01f)
    }

    @Test
    fun leftGradientReleasesBackdropEarlier() {
        val spec = WallpaperRenderSpec.default()
        assertEquals(640f, spec.leftGradientOpaqueUntilX, 0.01f)
        assertEquals(1600f, spec.leftGradientEndX, 0.01f)
    }

    @Test
    fun edgeMasksAnchorToVisibleBackdropEdges() {
        val spec = WallpaperRenderSpec.default()
        assertEquals(spec.backdropBox.left, spec.leftEdgeMaskStartX, 0.01f)
        assertEquals(spec.backdropBox.bottom, spec.bottomEdgeMaskEndY, 0.01f)
    }

    @Test
    fun containRectCentersLogoInsideBox() {
        val spec = WallpaperRenderSpec.default()
        val rect = spec.containRect(sourceWidth = 1000, sourceHeight = 300, box = spec.logoBox)
        assertRectEquals(RectF(380f, 279f, 1420f, 591f), rect)
    }

    private fun assertRectEquals(expected: RectF, actual: RectF) {
        assertEquals(expected.left, actual.left, 0.01f)
        assertEquals(expected.top, actual.top, 0.01f)
        assertEquals(expected.right, actual.right, 0.01f)
        assertEquals(expected.bottom, actual.bottom, 0.01f)
    }
}
