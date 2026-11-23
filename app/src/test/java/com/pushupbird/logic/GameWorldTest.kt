package com.pushupbird.logic

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameWorldTest {

    @Test
    fun holdingTheMiddleDoesNotDieImmediately() {
        val world = world()
        assertTrue(world.start())
        val tick = world.update(0.05f, 0f)
        assertEquals(RunState.PLAYING, world.state)
        assertFalse(tick.died)
        assertEquals(0, world.score)
    }

    @Test
    fun fullDownHitsTheMatAndFullUpHitsTheCeiling() {
        val down = world()
        down.start()
        down.nextSpawnIn = 10_000f
        val diedDown = down.update(0.05f, 1f)
        assertTrue(diedDown.died)
        assertEquals(RunState.DEAD, down.state)

        val up = world()
        up.start()
        up.nextSpawnIn = 10_000f
        val diedUp = up.update(0.05f, -1f)
        assertTrue(diedUp.died)
        assertEquals(RunState.DEAD, up.state)
    }

    @Test
    fun clearingTheOpeningScoresOnce() {
        val world = world()
        world.start()
        world.nextSpawnIn = 10_000f
        world.update(0.016f, 0f)
        world.pipes.clear()
        world.pipes.add(
            Pipe(
                x = world.birdX + 40f,
                width = world.pipeWidth,
                gapCenterY = world.birdY,
                gapHeight = world.gapHeight,
            ),
        )

        var steps = 0
        while (world.score == 0 && world.state == RunState.PLAYING && steps < 400) {
            world.update(0.05f, 0f)
            steps++
        }
        assertEquals(RunState.PLAYING, world.state)
        assertEquals(1, world.score)

        repeat(12) { world.update(0.05f, 0f) }
        assertEquals(1, world.score)
        assertEquals(RunState.PLAYING, world.state)
    }

    @Test
    fun hittingABarDoesNotScore() {
        val world = world()
        world.start()
        world.nextSpawnIn = 10_000f
        world.update(0.016f, 0f)
        world.pipes.clear()
        world.pipes.add(
            Pipe(
                x = world.birdX - 20f,
                width = world.pipeWidth,
                gapCenterY = world.birdY + world.gapHeight,
                gapHeight = world.gapHeight,
            ),
        )
        val tick = world.update(0.016f, 0f)
        assertTrue(tick.died)
        assertEquals(0, world.score)
    }

    @Test
    fun aHugeTimeStepCannotSkipTheScreen() {
        val world = world()
        world.start()
        world.nextSpawnIn = 10_000f
        world.update(0.016f, 0f)
        world.pipes.clear()
        val startX = world.birdX + 400f
        world.pipes.add(
            Pipe(
                x = startX,
                width = world.pipeWidth,
                gapCenterY = world.birdY,
                gapHeight = world.gapHeight,
            ),
        )
        world.update(10f, 0f)
        assertEquals(RunState.PLAYING, world.state)
        val moved = startX - world.pipes.first().x
        assertTrue(moved < 40f)
    }

    @Test
    fun afterDeathTheBirdStillTracksAndBarsFreeze() {
        val world = world()
        world.start()
        world.nextSpawnIn = 10_000f
        world.pipes.clear()
        world.pipes.add(Pipe(x = 80f, width = 90f, gapCenterY = 1000f, gapHeight = 200f))
        world.update(0.05f, 1f)
        assertEquals(RunState.DEAD, world.state)
        val frozenX = world.pipes.first().x
        val lowY = world.birdY
        world.update(0.05f, 0f)
        assertEquals(frozenX, world.pipes.first().x, 0.01f)
        assertTrue(world.birdY < lowY)
    }

    @Test
    fun gapsStayBetweenTheCeilingAndTheMat() {
        val world = world()
        repeat(40) {
            val center = world.sampleGapCenter()
            val radius = world.birdRadius * GameWorld.HIT_SCALE
            assertTrue(center - world.gapHeight / 2f > world.ceilingThickness)
            assertTrue(center + world.gapHeight / 2f < world.matTop())
            assertTrue(center - radius > world.ceilingThickness)
            assertTrue(center + radius < world.matTop())
        }
    }

    @Test
    fun offscreenBarsAreDiscarded() {
        val world = GameWorld(Random(3))
        world.resize(1080f, 1920f)
        world.start()
        repeat(500) { world.update(0.05f, 0f) }
        assertTrue(world.pipes.size < 8)
    }

    @Test
    fun circleMissesAGapAndHitsAPost() {
        assertFalse(circleHits(50f, 50f, 10f, 0f, 0f, 20f, 20f))
        assertTrue(circleHits(24f, 10f, 10f, 0f, 0f, 20f, 20f))
        assertFalse(circleHits(10f, 10f, 4f, 30f, 30f, 20f, 40f))
    }

    private fun world(): GameWorld {
        return GameWorld(Random(1)).also { it.resize(1000f, 2000f) }
    }
}
