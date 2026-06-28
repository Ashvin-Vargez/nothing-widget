package com.nothingwidget

import android.graphics.*
import java.util.Calendar
import kotlin.math.*

/**
 * Renders one frame of the Nothing-OS style widget onto a Bitmap.
 *
 * All internal coordinates are in a 280 × 280 virtual space;
 * the canvas is pre-scaled to fill whatever pixel size is requested.
 */
class WidgetRenderer {

    // ── Colours
    private val C_BG   = Color.parseColor("#1c1c1e")
    private val C_LIT  = Color.parseColor("#d0d0d0")
    private val C_DIM_WEEK  = Color.parseColor("#4a4a4e")   // week bar only — much more visible
    private val C_DIM_OTHER = Color.parseColor("#383838")    // JUN / 2026 / year — slightly brighter than before
    private val C_RED  = Color.parseColor("#ff3b30")
    private val C_DRED = Color.parseColor("#520e0a")
    private val C_ENV  = Color.parseColor("#3a3a3c")
    private val C_CACT = Color.parseColor("#585858")

    private val paint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
    }
    private val clipPath = Path()

    // ── Animation clocks (updated once per render call)
    private var slowEdge = C_RED
    private var fastEdge = C_RED

    private lateinit var canvas: Canvas

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry point
    // ─────────────────────────────────────────────────────────────────────────

    fun render(px: Int, settings: WidgetSettings, game: DinoGameState): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bmp)

        // Scale virtual 280 px space → actual pixel size
        canvas.scale(px / 280f, px / 280f)

        // Compute animation colours
        val now = System.currentTimeMillis()
        val bAmt = (sin(now.toDouble() / 1130.0) + 1.0) / 2.0
        slowEdge = Color.rgb(
            (80 + 175 * bAmt).toInt(),
            (8  +  51 * bAmt).toInt(),
            (6  +  42 * bAmt).toInt()
        )
        fastEdge = if ((now / 500) % 2 == 0L) C_RED else C_DRED

        // Background (squircle)
        paint.color = C_BG
        canvas.drawRoundRect(0f, 0f, 280f, 280f, 34f, 34f, paint)

        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(RectF(0f, 0f, 280f, 280f), 34f, 34f, Path.Direction.CW)
        canvas.clipPath(clipPath)

        drawContent(settings, game)

        canvas.restore()
        return bmp
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main layout
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawContent(s: WidgetSettings, game: DinoGameState) {
        val cal = Calendar.getInstance()
        val hour   = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)
        val dow    = cal.get(Calendar.DAY_OF_WEEK)        // 1=Sun
        val dom    = cal.get(Calendar.DAY_OF_MONTH)
        val month  = cal.get(Calendar.MONTH)              // 0-based
        val year   = cal.get(Calendar.YEAR)
        val doy    = cal.get(Calendar.DAY_OF_YEAR)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysInYear  = if (isLeapYear(year)) 366 else 365

        val curMins  = hour * 60 + minute
        val startM   = s.startHour * 60
        val endM     = s.endHour   * 60
        val totalM   = (endM - startM).coerceAtLeast(1)
        val dayPct   = ((endM - curMins.coerceAtLeast(startM)).toFloat() / totalM).coerceIn(0f, 1f)

        val remSecs  = (s.endHour * 3600 - (hour * 3600 + minute * 60 + second)
                        .coerceAtLeast(s.startHour * 3600)).coerceAtLeast(0)

        val dowIdx   = (dow + 5) % 7                         // 0=Mon
        val weekPct  = (1f - (dowIdx * 1440 + curMins).toFloat() / (7 * 1440)).coerceAtLeast(0f)
        val monthPct = (1f - ((dom - 1) * 1440 + curMins).toFloat() / (daysInMonth * 1440)).coerceAtLeast(0f)
        val yearPct  = (1f - ((doy - 1) * 1440 + curMins).toFloat() / (daysInYear  * 1440)).coerceAtLeast(0f)
        val wn       = isoWeek(cal)

        val DAY_NAMES = arrayOf("SUNDAY","MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY")
        val MON_NAMES = arrayOf("JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC")
        val dayName  = DAY_NAMES[dow - 1]
        val monStr   = MON_NAMES[month]
        val yrStr    = year.toString()
        val dateStr  = dom.toString()

        // ── Sizing constants (280 px virtual space)
        val LP  = 11f
        val CG  = 1
        val WK  = "MONTUEWEDTHUFRISATSUN"

        // Day column: reversed so first letter sits at bottom
        val dayRev   = dayName.reversed()
        val daySlots = dayRev.length * 5 + (dayRev.length - 1) * CG
        val dStep    = (280f - LP - LP - 20f) / daySlots
        val dDs = dStep * 0.76f;  val dDg = dStep * 0.24f
        val dayColW  = 7f * dStep
        val cX  = ceil(LP + dayColW + 5.0).toFloat()
        val PCT_W = 16f
        val barW = 280f - cX - LP - PCT_W
        val pctRX = 280f - LP - 1f

        val wkStep = barW / (WK.length * 5 + (WK.length - 1) * CG)
        val wkDs = wkStep * 0.76f;  val wkDg = wkStep * 0.24f;  val wkH = 7f * wkStep;  val wkY = LP

        // JUN bar + date number share the same step
        // JUN  = 17 col-slots, gap = 1, date "26" = 11 col-slots → total 29 = barW
        val mnStep = barW / 29f
        val mnDs = mnStep * 0.76f;  val mnDg = mnStep * 0.24f;  val mnH = 7f * mnStep

        val yrStep = barW / (yrStr.length * 5 + (yrStr.length - 1) * CG)
        val yrDs = yrStep * 0.76f;  val yrDg = yrStep * 0.24f;  val yrH = 7f * yrStep

        val w25Y = wkY + wkH + 3f
        val mnY  = w25Y + 10f + 6f
        val yrY  = mnY + mnH + 8f
        val yrBot = yrY + yrH

        // ── Draw progress bars
        vBar(dayRev,  dayPct,  LP,  LP,  dDs,  dDg,  CG, fastEdge)
        hBar(WK,      weekPct, cX,  wkY, wkDs, wkDg, CG, slowEdge, dimColor = C_DIM_WEEK)
        hBar(monStr,  monthPct,cX,  mnY, mnDs, mnDg, CG, slowEdge)
        dotText(dateStr, cX + 18f * mnStep, mnY, mnStep, C_RED)    // date "26" beside JUN
        hBar(yrStr,   yearPct, cX,  yrY, yrDs, yrDg, CG, slowEdge)

        // ── W## label (W in red, digits in white)
        val wnStr = wn.toString().padStart(2, '0')
        drawLabel("W",   cX,       w25Y, 13f, C_RED)
        drawLabel(wnStr, cX + 10f, w25Y, 13f, C_LIT)

        // ── Percentage labels  (number in red, % in white)
        drawPct(weekPct,  pctRX, wkY + wkH / 2f,  12f)
        drawPct(monthPct, pctRX, mnY + mnH / 2f, 15f)
        drawPct(yearPct,  pctRX, yrY + yrH / 2f, 15f)

        // ── Countdown timer
        val timerStr = formatTimer(remSecs)
        val SS   = 0.74f
        val tmNS = barW / (35f + 11f * SS)
        val tmSS = tmNS * SS
        val tmH  = 7f * tmNS
        val tmY  = yrBot + 8f
        val tmX  = cX + (barW - timerWidth(timerStr, tmNS, tmSS)) / 2f
        renderTimer(timerStr, tmX, tmY, tmNS, tmSS, slowEdge)

        // ── Chrome Dino scene
        val dzTop = tmY + tmH + 4f
        val dzBot = 280f - LP - 2f
        if (dzBot - dzTop > 18f) drawDinoScene(cX, dzTop, dzBot, barW, game, hour)

        // ── Day % at bottom of day column
        val dpY    = LP + daySlots * dStep + 4f
        val dpStr  = (dayPct * 100).toInt().toString()
        drawLabel(dpStr, LP + 1f, dpY, 14f, C_RED)
        textPaint.apply { color = C_LIT; textSize = 11f; textAlign = Paint.Align.LEFT }
        val dpW = textPaint.measureText(dpStr)
        canvas.drawText("%", LP + 1f + dpW + 1f, dpY + 14f, textPaint)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dot-matrix bars
    // ─────────────────────────────────────────────────────────────────────────

    /** Horizontal bar: DIM=left(elapsed)  LIT=right(remaining). */
    private fun hBar(str: String, pct: Float, x: Float, y: Float,
                     ds: Float, dg: Float, cg: Int, edgeColor: Int,
                     dimColor: Int = C_DIM_OTHER) {
        val step = ds + dg
        val flat = buildList {
            str.forEachIndexed { i, ch ->
                val m = Glyphs[ch]
                for (c in 0..4) add(m[c])
                if (i < str.length - 1) repeat(cg) { add(0) }
            }
        }
        val tot = flat.size
        val dk  = ((1f - pct) * tot + .5f).toInt()
        flat.forEachIndexed { i, mask ->
            if (mask == 0) return@forEachIndexed
            val edge = i == dk && dk > 0 && dk < tot
            val lit  = i > dk
            for (r in 0..6) {
                if (mask ushr (6 - r) and 1 == 0) continue
                fr(x + i * step, y + r * step, ds, ds, if (edge) edgeColor else if (lit) C_LIT else dimColor)
            }
        }
    }

    /**
     * Vertical day bar — 90 °CCW rotation, string already reversed.
     * origCol 4 → slot 0 (TOP, darkens first).
     * origCol 0 → slot 4 (BOTTOM, stays lit longest).
     * scrX = x + origRow*step,  scrY = y + slot*step
     */
    private fun vBar(str: String, pct: Float, x: Float, y: Float,
                     ds: Float, dg: Float, cg: Int, edgeColor: Int) {
        val step = ds + dg
        val tot  = str.length * 5 + (str.length - 1) * cg
        val dk   = ((1f - pct) * tot + .5f).toInt()
        str.forEachIndexed { ci, ch ->
            val m    = Glyphs[ch]
            val base = ci * (5 + cg)
            for (oc in 0..4) {
                val mask = m[oc]
                val slot = base + (4 - oc)
                val edge = slot == dk && dk > 0 && dk < tot
                val lit  = slot > dk
                for (oR in 0..6) {
                    if (mask ushr (6 - oR) and 1 == 0) continue
                    fr(x + oR * step, y + slot * step, ds, ds,
                       if (edge) edgeColor else if (lit) C_LIT else C_DIM_OTHER)
                }
            }
        }
    }

    /** Plain dot-matrix text in a fixed colour (used for date "26"). */
    private fun dotText(str: String, x: Float, y: Float, step: Float, color: Int) {
        val ds = step * 0.76f
        var cx = x
        str.forEachIndexed { ci, ch ->
            val m = Glyphs[ch]
            for (c in 0..4) {
                val mask = m[c]
                for (r in 0..6) {
                    if (mask ushr (6 - r) and 1 == 0) continue
                    fr(cx + c * step, y + r * step, ds, ds, color)
                }
            }
            cx += 5f * step + if (ci < str.length - 1) step else 0f
        }
    }

    /** Timer: H:MM in C_LIT, colons in colonColor, :SS in C_RED + 74 % height. */
    private fun renderTimer(str: String, x: Float, y: Float,
                            nSt: Float, sSt: Float, colonColor: Int) {
        val nDs  = nSt * 0.76f;  val sDs = sSt * 0.76f
        val tH   = 7f * nSt;     val sYoff = (tH - 7f * sSt) / 2f
        val lastC = str.lastIndexOf(':')
        var cx = x
        str.forEachIndexed { ci, ch ->
            val m    = Glyphs[ch]
            val isSec = ci > lastC;  val isCo = ch == ':'
            val st = if (isSec) sSt else nSt
            val ds = if (isSec) sDs else nDs
            val yO = if (isSec) sYoff else 0f
            val col = when { isCo -> colonColor; isSec -> C_RED; else -> C_LIT }
            for (c in 0..4) {
                val mask = m[c]
                for (r in 0..6) {
                    if (mask ushr (6 - r) and 1 == 0) continue
                    fr(cx + c * st, y + yO + r * st, ds, ds, col)
                }
            }
            cx += 5f * st + if (ci < str.length - 1) st else 0f
        }
    }

    private fun timerWidth(str: String, nSt: Float, sSt: Float): Float {
        val lc = str.lastIndexOf(':')
        var w = 0f
        str.forEachIndexed { i, _ ->
            val st = if (i > lc) sSt else nSt
            w += 5f * st + if (i < str.length - 1) st else 0f
        }
        return w
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chrome Dino scene
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawDinoScene(cX: Float, zTop: Float, zBot: Float,
                               barW: Float, g: DinoGameState, hour: Int) {
        val gY = zBot - 12f
        canvas.save()
        canvas.clipRect(cX, zTop, cX + barW, zBot)

        // Ground line
        fr(cX, gY, barW, 2f, C_ENV)
        // Scrolling ground dots
        val dt = (g.gTick.toInt()) % 11
        var gx = 0
        while (gx < barW.toInt()) {
            fr(cX + ((gx + dt) % barW.toInt()), gY + 3f, 4f, 1f, C_ENV)
            gx += 11
        }

        // Sun (day 6-20) or crescent Moon
        val isDay = hour in 6..19
        val sx = cX + barW - 14f;  val sy = zTop + 9f
        if (isDay) {
            paint.color = Color.parseColor("#b0b090")
            canvas.drawCircle(sx, sy, 5f, paint)
            listOf(0f to -8f, 0f to 6f, -8f to 0f, 5f to 0f,
                   -6f to -6f, 4f to -6f, -6f to 4f, 4f to 4f)
                .forEach { (rx, ry) -> fr(sx + rx, sy + ry, 2f, 2f, Color.parseColor("#b0b090")) }
        } else {
            paint.color = Color.parseColor("#9898b8")
            canvas.drawCircle(sx, sy, 6f, paint)
            paint.color = C_BG
            canvas.drawCircle(sx + 3f, sy - 1f, 4.5f, paint)
        }

        // Clouds (8-bit rectangles)
        g.clouds.forEach { c ->
            val cx = cX + c.x;  val cy = zTop + c.y
            fr(cx + 3f, cy,       14f, 3f, C_ENV)
            fr(cx,      cy + 3f,  20f, 4f, C_ENV)
            fr(cx + 2f, cy + 7f,  16f, 3f, C_ENV)
        }

        // Cacti
        g.cacti.forEach { c ->
            val cx = cX + c.x;  val f = gY - 1f
            fr(cx - 3f, f - 24f,  7f, 25f, C_CACT)   // trunk
            fr(cx - 10f, f - 16f, 8f,  4f, C_CACT)   // left arm H
            fr(cx - 10f, f - 24f, 4f,  9f, C_CACT)   // left arm V
            fr(cx + 3f,  f - 20f, 8f,  4f, C_CACT)   // right arm H
            fr(cx + 7f,  f - 28f, 4f,  9f, C_CACT)   // right arm V
        }

        // Dino
        drawDino(cX + DinoGameState.DINO_RX, gY - 2f, g)

        canvas.restore()
    }

    private fun drawDino(ax: Float, groundY: Float, g: DinoGameState) {
        val dx = ax
        val fy = groundY + g.djY.toInt()           // foot Y

        fr(dx - 7f, fy - 14f,  5f,  3f, C_LIT)   // tail tip
        fr(dx - 5f, fy - 17f,  3f,  4f, C_LIT)   // tail base
        fr(dx,      fy - 20f, 12f, 13f, C_LIT)   // body
        fr(dx + 5f, fy - 29f, 13f, 10f, C_LIT)   // head
        fr(dx + 5f, fy - 21f,  9f,  2f, C_LIT)   // jaw
        fr(dx + 14f, fy - 28f, 2f,  2f, C_BG)    // eye

        if (g.djGnd) {
            if (g.dlFr == 0) {
                fr(dx + 2f, fy - 6f, 4f, 7f, C_LIT)
                fr(dx + 2f, fy - 1f, 6f, 2f, C_LIT)
                fr(dx + 7f, fy - 10f, 4f, 6f, C_LIT)
            } else {
                fr(dx + 2f, fy - 10f, 4f, 6f, C_LIT)
                fr(dx + 7f, fy - 6f,  4f, 7f, C_LIT)
                fr(dx + 7f, fy - 1f,  6f, 2f, C_LIT)
            }
        } else {
            // Jump: legs tucked
            fr(dx + 2f, fy - 10f, 4f, 6f, C_LIT)
            fr(dx + 7f, fy - 10f, 4f, 6f, C_LIT)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Text helpers (regular system font for small labels)
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawLabel(str: String, x: Float, y: Float, size: Float, color: Int) {
        textPaint.apply {
            this.color = color
            textSize   = size
            textAlign  = Paint.Align.LEFT
        }
        canvas.drawText(str, x, y + size, textPaint)
    }

    private fun drawPct(pct: Float, rx: Float, cy: Float, size: Float) {
        val numStr = (pct * 100).toInt().toString()
        textPaint.apply { color = C_RED; textSize = size; textAlign = Paint.Align.RIGHT }
        canvas.drawText(numStr, rx, cy + size / 2f, textPaint)
        textPaint.apply { color = C_LIT; textSize = size - 1f; textAlign = Paint.Align.LEFT }
        canvas.drawText("%", rx + 1f, cy + size / 2f, textPaint)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    /** Fill a rectangle — shorthand to avoid verbose Paint setup every call. */
    private fun fr(x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color
        canvas.drawRect(x, y, x + w, y + h, paint)
    }

    private fun formatTimer(secs: Int): String {
        val h = secs / 3600
        val m = (secs % 3600) / 60
        val s = secs % 60
        return "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }

    private fun isLeapYear(y: Int) = y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)

    private fun isoWeek(cal: Calendar): Int {
        val c = cal.clone() as Calendar
        c.minimalDaysInFirstWeek = 4
        c.firstDayOfWeek = Calendar.MONDAY
        return c.get(Calendar.WEEK_OF_YEAR)
    }
}
