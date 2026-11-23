package com.pushupbird.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.pushupbird.R
import com.pushupbird.logic.GameWorld
import com.pushupbird.logic.RunState
import kotlin.math.sin

/** What the playfield needs to draw one frame. The world stays free of Android types. */
data class DrawFrame(
    val world: GameWorld,
    val seconds: Float,
    val tiltDegrees: Float,
    val deathFlash: Float,
    val scoreScale: Float,
    val topInset: Float,
    val density: Float,
    val banner: String?,
    val centerLabel: String,
    val wordmark: String,
)

/**
 * Original shapes for Pushup Bird: a sweatbanded bird and brass-collared bars.
 * Drawn in code so the game does not ship anyone else's sprites.
 */
class GameRenderer(context: Context) {
    private val terracotta = ContextCompat.getColor(context, R.color.terracotta)
    private val terracottaDark = ContextCompat.getColor(context, R.color.terracotta_dark)
    private val cream = ContextCompat.getColor(context, R.color.cream)
    private val belly = ContextCompat.getColor(context, R.color.belly)
    private val blush = ContextCompat.getColor(context, R.color.blush)
    private val beak = ContextCompat.getColor(context, R.color.beak)
    private val pupil = ContextCompat.getColor(context, R.color.pupil)
    private val stripe = ContextCompat.getColor(context, R.color.stripe)
    private val bar = ContextCompat.getColor(context, R.color.bar)
    private val barEdge = ContextCompat.getColor(context, R.color.bar_edge)
    private val brass = ContextCompat.getColor(context, R.color.brass)
    private val brassLight = ContextCompat.getColor(context, R.color.brass_light)
    private val mat = ContextCompat.getColor(context, R.color.mat)
    private val matStripe = ContextCompat.getColor(context, R.color.mat_stripe)
    private val ceiling = ContextCompat.getColor(context, R.color.ceiling)
    private val danger = ContextCompat.getColor(context, R.color.danger)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cream
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        setShadowLayer(18f, 0f, 4f, 0xAA000000.toInt())
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cream
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cream
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val bannerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE614181F.toInt() }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCE0A24A.toInt()
        style = Paint.Style.STROKE
    }
    private val scrim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private var shadeW = -1f
    private var shadeH = -1f
    private var scoreBaseline = 0f

    fun draw(canvas: Canvas, frame: DrawFrame) {
        val world = frame.world
        val width = world.width
        val height = world.height
        if (width < 2f || height < 2f) return

        ensureScrim(width, height)
        canvas.drawRect(0f, 0f, width, height, scrim)

        for (pipe in world.pipes) {
            drawBar(canvas, pipe.x, pipe.width, pipe.gapCenterY, pipe.gapHeight)
        }
        drawCeiling(canvas, width, world.ceilingThickness)
        drawMat(canvas, width, height, world.floorThickness, frame.wordmark, frame.density)
        if (world.state == RunState.READY || world.state == RunState.PAUSED) {
            drawGuide(canvas, world, frame.centerLabel, frame.density)
        }
        drawBird(canvas, world, frame.tiltDegrees, frame.seconds)
        if (world.state != RunState.READY) {
            drawScore(canvas, world, frame)
        }
        frame.banner?.let { drawBanner(canvas, it, width, frame) }
        if (frame.deathFlash > 0.02f) {
            fill.shader = null
            fill.color = danger
            fill.alpha = (frame.deathFlash * 120f).toInt().coerceIn(0, 150)
            canvas.drawRect(0f, 0f, width, height, fill)
            fill.alpha = 255
        }
    }

    private fun ensureScrim(width: Float, height: Float) {
        if (shadeW == width && shadeH == height) return
        shadeW = width
        shadeH = height
        scrim.shader = LinearGradient(
            0f,
            0f,
            0f,
            height,
            intArrayOf(0xC010141C.toInt(), 0x22000000, 0x00000000, 0x00000000, 0xE010141C.toInt()),
            floatArrayOf(0f, 0.18f, 0.34f, 0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private fun drawBar(
        canvas: Canvas,
        x: Float,
        pipeWidth: Float,
        gapCenter: Float,
        gapHeight: Float,
    ) {
        val gapTop = gapCenter - gapHeight / 2f
        val gapBottom = gapCenter + gapHeight / 2f
        val left = x
        val right = x + pipeWidth
        val radius = pipeWidth * 0.18f
        val extra = pipeWidth * GameWorld.CAP_EXTRA_SCALE
        val capH = pipeWidth * GameWorld.CAP_HEIGHT_SCALE

        fill.shader = null
        fill.color = bar
        round(canvas, left, -40f, right, gapTop, radius)
        round(canvas, left, gapBottom, right, canvas.height + 40f, radius)

        stroke.color = barEdge
        stroke.strokeWidth = pipeWidth * 0.045f
        val inset = pipeWidth * 0.16f
        canvas.drawLine(left + inset, 0f, left + inset, gapTop, stroke)
        canvas.drawLine(left + inset, gapBottom, left + inset, canvas.height.toFloat(), stroke)

        fill.color = brass
        round(canvas, left - extra, gapTop - capH, right + extra, gapTop, radius)
        round(canvas, left - extra, gapBottom, right + extra, gapBottom + capH, radius)
        fill.color = brassLight
        val highlight = capH * 0.28f
        round(canvas, left - extra, gapTop - capH, right + extra, gapTop - capH + highlight, radius * 0.6f)
        round(canvas, left - extra, gapBottom, right + extra, gapBottom + highlight, radius * 0.6f)
    }

    private fun drawCeiling(canvas: Canvas, width: Float, thickness: Float) {
        fill.shader = null
        fill.color = ceiling
        canvas.drawRect(0f, 0f, width, thickness, fill)
        fill.color = brass
        val lip = (thickness * 0.08f).coerceAtLeast(4f)
        canvas.drawRect(0f, thickness - lip, width, thickness, fill)
    }

    private fun drawMat(
        canvas: Canvas,
        width: Float,
        height: Float,
        thickness: Float,
        wordmark: String,
        density: Float,
    ) {
        val top = height - thickness
        fill.shader = null
        fill.color = mat
        canvas.drawRect(0f, top, width, height, fill)
        fill.color = matStripe
        canvas.drawRect(0f, top, width, top + thickness * 0.14f, fill)
        labelPaint.color = 0xFFFFF6E8.toInt()
        labelPaint.alpha = 210
        labelPaint.textSize = (thickness * 0.28f).coerceAtMost(14f * density)
        labelPaint.letterSpacing = 0.18f
        canvas.drawText(wordmark, width / 2f, height - thickness * 0.38f, labelPaint)
        labelPaint.letterSpacing = 0f
        labelPaint.alpha = 255
    }

    private fun drawGuide(canvas: Canvas, world: GameWorld, label: String, density: Float) {
        val y = world.centerY()
        guidePaint.strokeWidth = 2f * density
        guidePaint.pathEffect = DashPathEffect(floatArrayOf(10f * density, 8f * density), 0f)
        val inset = 28f * density
        canvas.drawLine(inset, y, world.width - inset, y, guidePaint)
        guidePaint.pathEffect = null
        labelPaint.color = brass
        labelPaint.textSize = 11f * density
        labelPaint.letterSpacing = 0.14f
        canvas.drawText(label, world.width / 2f, y - 10f * density, labelPaint)
        labelPaint.letterSpacing = 0f
    }

    private fun drawBird(canvas: Canvas, world: GameWorld, tilt: Float, seconds: Float) {
        val r = world.birdRadius
        canvas.save()
        canvas.translate(world.birdX, world.birdY)
        canvas.rotate(tilt)
        canvas.scale(r, r)

        fill.shader = null
        fill.color = 0x66000000
        canvas.drawOval(-0.35f, 0.72f, 1.15f, 1.35f, fill)

        val flap = sin(seconds * 9.5f) * 26f
        canvas.save()
        canvas.rotate(flap, -0.2f, 0.1f)
        fill.color = terracottaDark
        canvas.drawOval(-1.15f, -0.05f, -0.05f, 0.72f, fill)
        canvas.restore()

        path.rewind()
        path.moveTo(-0.45f, -0.05f)
        path.lineTo(-1.35f, -0.42f)
        path.lineTo(-1.05f, 0.18f)
        path.lineTo(-1.32f, 0.48f)
        path.lineTo(-0.42f, 0.32f)
        path.close()
        fill.color = terracottaDark
        canvas.drawPath(path, fill)

        fill.color = terracotta
        canvas.drawCircle(0f, 0f, 1f, fill)
        fill.color = belly
        canvas.drawCircle(0.12f, 0.22f, 0.62f, fill)

        fill.color = cream
        rect.set(-1.05f, -0.62f, 1.05f, -0.18f)
        canvas.drawRoundRect(rect, 0.18f, 0.18f, fill)
        fill.color = stripe
        rect.set(-1.05f, -0.46f, 1.05f, -0.32f)
        canvas.drawRoundRect(rect, 0.08f, 0.08f, fill)

        fill.color = blush
        canvas.drawCircle(0.18f, 0.38f, 0.16f, fill)

        fill.color = cream
        canvas.drawCircle(0.42f, -0.02f, 0.30f, fill)
        fill.color = pupil
        canvas.drawCircle(0.50f, -0.01f, 0.15f, fill)
        fill.color = cream
        canvas.drawCircle(0.55f, -0.07f, 0.05f, fill)

        path.rewind()
        path.moveTo(0.62f, 0.02f)
        path.lineTo(1.48f, 0.28f)
        path.lineTo(0.58f, 0.42f)
        path.close()
        fill.color = beak
        canvas.drawPath(path, fill)

        stroke.color = cream
        stroke.strokeWidth = 0.10f
        canvas.drawCircle(0f, 0f, 1f, stroke)
        canvas.restore()
    }

    private fun drawScore(canvas: Canvas, world: GameWorld, frame: DrawFrame) {
        scorePaint.textSize = minOf(world.width, world.height) * 0.13f
        val baseline = maxOf(
            world.ceilingThickness + scorePaint.textSize * 0.15f,
            frame.topInset + scorePaint.textSize * 0.85f,
        )
        canvas.save()
        canvas.translate(world.width / 2f, baseline)
        canvas.scale(frame.scoreScale, frame.scoreScale)
        canvas.drawText(world.score.toString(), 0f, 0f, scorePaint)
        canvas.restore()
        scoreBaseline = baseline
    }

    private fun drawBanner(canvas: Canvas, text: String, width: Float, frame: DrawFrame) {
        val density = frame.density
        var size = 14f * density
        bannerPaint.textSize = size
        val maxWidth = width - 48f * density
        while (bannerPaint.measureText(text) > maxWidth && size > 11f * density) {
            size -= density
            bannerPaint.textSize = size
        }
        val textWidth = bannerPaint.measureText(text)
        val padX = 14f * density
        val padY = 8f * density
        val metrics = bannerPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val top = scoreBaseline + 16f * density
        val cx = width / 2f
        rect.set(
            cx - textWidth / 2f - padX,
            top,
            cx + textWidth / 2f + padX,
            top + textHeight + padY * 2f,
        )
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, bannerBg)
        val baseline = rect.top + padY - metrics.ascent
        canvas.drawText(text, cx, baseline, bannerPaint)
    }

    private fun round(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, radius, radius, fill)
    }
}
