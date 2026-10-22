// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample.local

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.util.Locale
import java.io.File
import java.io.FileOutputStream

class WallpaperRenderer(private val spec: WallpaperRenderSpec = WallpaperRenderSpec.default()) {
    fun render(
        backdrop: Bitmap,
        logo: Bitmap?,
        outputFile: File,
        details: TmdbDetails? = null
    ) {
        val output = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
        try {
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.drawColor(Color.BLACK)
        drawDarkBackdropUnderlay(canvas, backdrop, paint)
        val clippedBackdrop = canvas.save()
        canvas.clipRect(spec.backdropBox)
        canvas.drawBitmap(backdrop, Rect(0, 0, backdrop.width, backdrop.height), spec.coverCropRect(backdrop.width, backdrop.height), paint)
        canvas.restoreToCount(clippedBackdrop)
        drawLeftGradient(canvas)
        drawBottomGradient(canvas)

        if (logo != null) {
            canvas.drawBitmap(logo, Rect(0, 0, logo.width, logo.height), spec.containRect(logo.width, logo.height, spec.logoBox), paint)
        }

        if (details != null) drawDetails(canvas, details, logo != null)

        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { stream ->
            check(output.compress(Bitmap.CompressFormat.JPEG, 92, stream)) { "Unable to encode wallpaper." }
        }
        } finally {
            output.recycle()
        }
    }

    private fun drawDetails(canvas: Canvas, details: TmdbDetails, hasLogo: Boolean) {
        if (!hasLogo) drawText(canvas, details.title, spec.logoBox.left, spec.logoBox.top + 25f, 1040, 92f, 3, true)
        val metadata = listOfNotNull(
            details.year?.toString(),
            if (details.isMovie) "Movie" else "TV series",
            details.runtime?.takeIf { it > 0 }?.let { "$it min" },
            details.rating?.let { "TMDB ${String.format(Locale.US, "%.1f", it)}/10" }
        ).joinToString("  ·  ")
        drawText(canvas, metadata, 380f, 675f, 1140, 36f, 1)
        drawText(canvas, details.genres.joinToString("  ·  "), 380f, 730f, 1040, 34f, 1)
        var top = 805f
        if (details.tagline.isNotBlank()) {
            drawText(canvas, details.tagline, 380f, top, 1040, 38f, 2, true)
            top += 105f
        }
        drawText(canvas, details.overview, 380f, top, 1040, 37f, 6)
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float, width: Int, size: Float, lines: Int, bold: Boolean = false) {
        if (text.isBlank()) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(236, 237, 240)
            textSize = size
            typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .setLineSpacing(8f, 1f).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
        val saved = canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restoreToCount(saved)
    }

    private fun drawDarkBackdropUnderlay(canvas: Canvas, backdrop: Bitmap, paint: Paint) {
        paint.alpha = 90
        canvas.drawBitmap(backdrop, Rect(0, 0, backdrop.width, backdrop.height), spec.fullCanvasCoverRect(backdrop.width, backdrop.height), paint)
        paint.alpha = 255
        canvas.drawColor(Color.argb(135, 0, 0, 0))
    }

    private fun drawLeftGradient(canvas: Canvas) {
        canvas.drawRect(0f, 0f, spec.leftEdgeMaskStartX, spec.height.toFloat(), Paint().apply {
            color = Color.BLACK
        })

        val opaqueStop = ((spec.leftGradientOpaqueUntilX - spec.leftEdgeMaskStartX) /
            (spec.leftGradientEndX - spec.leftEdgeMaskStartX)).coerceIn(0f, 1f)
        val paint = Paint()
        paint.shader = LinearGradient(
            spec.leftEdgeMaskStartX, 0f, spec.leftGradientEndX, 0f,
            intArrayOf(Color.BLACK, Color.BLACK, Color.argb(210, 0, 0, 0), Color.TRANSPARENT),
            floatArrayOf(0f, opaqueStop, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(spec.leftEdgeMaskStartX, 0f, spec.leftGradientEndX, spec.height.toFloat(), paint)
    }

    private fun drawBottomGradient(canvas: Canvas) {
        val strongStop = ((spec.bottomGradientStrongUntilY - spec.bottomGradientStartY) /
            (spec.bottomEdgeMaskEndY - spec.bottomGradientStartY)).coerceIn(0f, 1f)
        val paint = Paint()
        paint.shader = LinearGradient(
            0f, spec.bottomGradientStartY, 0f, spec.bottomEdgeMaskEndY,
            intArrayOf(Color.TRANSPARENT, Color.argb(220, 0, 0, 0), Color.BLACK),
            floatArrayOf(0f, strongStop, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, spec.bottomGradientStartY, spec.width.toFloat(), spec.bottomEdgeMaskEndY, paint)
        canvas.drawRect(0f, spec.bottomEdgeMaskEndY, spec.width.toFloat(), spec.height.toFloat(), Paint().apply {
            color = Color.BLACK
        })
    }
}
