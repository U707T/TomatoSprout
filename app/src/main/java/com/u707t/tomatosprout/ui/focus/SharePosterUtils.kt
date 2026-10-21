package com.u707t.tomatosprout.ui.focus

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 用原生 Canvas 生成分享海报，不申请存储权限（FileProvider + cache）。 */
object SharePosterUtils {
    fun share(context: Context, title: String, timeText: String, subtitle: String, accent: Color) {
        val bitmap = render(title, timeText, subtitle, accent)
        val dir = File(context.cacheDir, "posters").apply { mkdirs() }
        val file = File(dir, "tomato_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "番茄芽 · $title · $timeText")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享专注海报"))
    }

    private fun render(title: String, timeText: String, subtitle: String, accent: Color): Bitmap {
        val w = 1080
        val h = 1440
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val base = accent.toArgb()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(lighten(base, 0.18f), darken(base, 0.55f)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        paint.color = android.graphics.Color.argb(40, 0, 0, 0)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        paint.color = android.graphics.Color.WHITE
        paint.textSize = 54f
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        canvas.drawText("🍅 番茄芽", 90f, 150f, paint)

        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = 34f
        paint.alpha = 210
        canvas.drawText("专注记录 · " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")), 90f, 210f, paint)

        paint.alpha = 255
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = 68f
        wrapText(title, 900f, paint).take(3).forEachIndexed { index, line ->
            canvas.drawText(line, 90f, 370f + index * 92f, paint)
        }

        paint.textSize = 210f
        canvas.drawText(timeText, 90f, 820f, paint)

        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = 38f
        paint.alpha = 220
        canvas.drawText(subtitle, 95f, 890f, paint)

        paint.alpha = 255
        paint.textSize = 40f
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.ITALIC)
        val quote = "把注意力放在当下，时间会替你积累答案。"
        wrapText(quote, 900f, paint).forEachIndexed { index, line ->
            canvas.drawText(line, 90f, 1080f + index * 58f, paint)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.alpha = 110
        canvas.drawRoundRect(RectF(90f, 1280f, 990f, 1360f), 40f, 40f, paint)
        paint.style = Paint.Style.FILL
        paint.alpha = 210
        paint.textSize = 30f
        canvas.drawText("不索权 · 不打扰 · 不空洞", 130f, 1334f, paint)
        return bmp
    }

    private fun darken(color: Int, amount: Float): Int {
        val f = 1f - amount.coerceIn(0f, 1f)
        return android.graphics.Color.argb(
            255,
            (android.graphics.Color.red(color) * f).toInt(),
            (android.graphics.Color.green(color) * f).toInt(),
            (android.graphics.Color.blue(color) * f).toInt(),
        )
    }

    private fun lighten(color: Int, amount: Float): Int {
        val f = amount.coerceIn(0f, 1f)
        return android.graphics.Color.argb(
            255,
            (android.graphics.Color.red(color) + (255 - android.graphics.Color.red(color)) * f).toInt(),
            (android.graphics.Color.green(color) + (255 - android.graphics.Color.green(color)) * f).toInt(),
            (android.graphics.Color.blue(color) + (255 - android.graphics.Color.blue(color)) * f).toInt(),
        )
    }

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val result = mutableListOf<String>()
        val line = StringBuilder()
        text.forEach { ch ->
            if (paint.measureText(line.toString() + ch) > maxWidth) {
                result += line.toString()
                line.clear()
            }
            line.append(ch)
        }
        if (line.isNotEmpty()) result += line.toString()
        return result
    }
}
