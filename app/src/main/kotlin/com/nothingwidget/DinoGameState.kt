package com.nothingwidget

import kotlin.math.max

/**
 * Holds and advances the Chrome-Dino animation state.
 * Call [update] once per frame (~15 fps target).
 */
class DinoGameState {

    // ── Jump state
    var djY  = 0f       // current y-offset (negative = upward)
    var djV  = 0f       // vertical velocity
    var djGnd = true    // on ground?

    // ── Leg animation
    var dlFr = 0        // frame index (0 or 1)
    var dlTk = 0        // tick counter

    // ── Scrolling elements
    data class Cactus(var x: Float)
    data class Cloud(var x: Float, val y: Float)

    val cacti  = mutableListOf(Cactus(195f))
    val clouds = mutableListOf(
        Cloud(155f, 7f),
        Cloud(88f,  13f),
        Cloud(235f, 5f),
    )

    var cSpawn = 72     // frames until next cactus
    var gTick  = 0f     // ground-texture scroll position

    fun update() {
        gTick += CSPD

        // Scroll clouds (slower than cacti)
        clouds.forEach { c ->
            c.x -= CSPD * 0.42f
            if (c.x < -28f) c.x = 200f + (0..60).random().toFloat()
        }

        // Scroll cacti
        cacti.forEach { it.x -= CSPD }
        cacti.removeAll { it.x < -18f }
        if (--cSpawn <= 0) {
            cacti += Cactus(205f)
            cSpawn = 78 + (0..52).random()
        }

        // Jump AI: jump when a cactus is within trigger distance
        if (djGnd) {
            for (c in cacti) {
                val d = c.x - DINO_RX
                if (d in 0f..52f) {
                    djV  = JV
                    djGnd = false
                    break
                }
            }
        }

        // Jump physics
        if (!djGnd) {
            djY += djV
            djV += GRAV
            if (djY >= 0f) { djY = 0f; djV = 0f; djGnd = true }
        }

        // Leg cycle (only while running on ground)
        if (djGnd && ++dlTk > 9) { dlTk = 0; dlFr = 1 - dlFr }
    }

    companion object {
        const val DINO_RX = 22f   // dino's fixed x in barW space
        const val JV      = -5.0f // initial jump velocity (upward)
        const val GRAV    =  0.33f
        const val CSPD    =  1.8f // horizontal scroll speed
    }
}
