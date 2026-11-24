package com.pushupbird.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadSignalTest {

    @Test
    fun noFaceLeavesTheBirdUnset() {
        assertNull(HeadSignal().screenY)
    }

    @Test
    fun theFollowedValueIsTheHeadLine() {
        val signal = HeadSignal(alpha = 1f)
        signal.push(0.35f)
        signal.push(0.35f)
        signal.push(0.35f)
        assertEquals(0.35f, signal.screenY!!, 0.0001f)
        signal.push(0.75f)
        signal.push(0.75f)
        signal.push(0.75f)
        assertEquals(0.75f, signal.screenY!!, 0.0001f)
    }

    @Test
    fun holdingStillDoesNotDrift() {
        val signal = HeadSignal()
        repeat(3) { signal.push(0.42f) }
        val settled = signal.screenY!!
        repeat(30) { signal.push(0.42f) }
        assertEquals(settled, signal.screenY!!, 0.0001f)
    }

    @Test
    fun notPushingHoldsTheLastLine() {
        val signal = HeadSignal(alpha = 1f)
        signal.push(0.4f)
        signal.push(0.4f)
        signal.push(0.4f)
        val held = signal.screenY
        assertEquals(0.4f, held!!, 0.0001f)
        assertEquals(held, signal.screenY)
    }

    @Test
    fun aSingleSpikeBetweenSteadySamplesIsIgnored() {
        val signal = HeadSignal(alpha = 1f)
        signal.push(0.40f)
        signal.push(0.40f)
        signal.push(0.40f)
        signal.push(0.95f)
        assertEquals(0.40f, signal.screenY!!, 0.0001f)
        signal.push(0.40f)
        assertEquals(0.40f, signal.screenY!!, 0.0001f)
    }

    @Test
    fun aRealMoveIsFollowedWithoutPassingTheHead() {
        val signal = HeadSignal(alpha = 0.5f)
        repeat(3) { signal.push(0.30f) }
        signal.push(0.50f)
        signal.push(0.70f)
        val followed = signal.screenY!!
        assertEquals(0.40f, followed, 0.0001f)
        assertTrue(followed < 0.70f)
        signal.push(0.70f)
        assertEquals(0.55f, signal.screenY!!, 0.0001f)
        assertTrue(signal.screenY!! < 0.70f)
    }

    @Test
    fun samplesOutsideTheScreenAreClamped() {
        val signal = HeadSignal(alpha = 1f)
        signal.push(-1f)
        signal.push(-1f)
        signal.push(-1f)
        assertEquals(0f, signal.screenY!!, 0.0001f)
        signal.push(3f)
        signal.push(3f)
        signal.push(3f)
        assertEquals(1f, signal.screenY!!, 0.0001f)
    }

    @Test
    fun sameAspectPreviewKeepsTheImageFraction() {
        assertEquals(0.35f, fillCenterNormY(70f, 100f, 200f, 100f, 200f), 0.0001f)
    }

    @Test
    fun fillCenterCropMapsTheVisibleLineToTheView() {
        // Image is twice as tall as the view, so the view shows the middle half.
        // imageY 85 is 35% of the way down that visible band.
        assertEquals(0.35f, fillCenterNormY(85f, 100f, 200f, 100f, 100f), 0.0001f)
        assertEquals(0.5f, fillCenterNormY(100f, 100f, 200f, 100f, 100f), 0.0001f)
    }

    @Test
    fun theTrackedFaceStaysWithTheHeadNotANewcomer() {
        val faces = listOf(
            FaceCandidate(screenY = 0.20f, area = 4_000),
            FaceCandidate(screenY = 0.72f, area = 9_000),
        )
        assertEquals(0.72f, pickFaceScreenY(faces, lastScreenY = null)!!, 0.0001f)
        assertEquals(0.20f, pickFaceScreenY(faces, lastScreenY = 0.22f)!!, 0.0001f)
    }
}
