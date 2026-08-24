package com.example.ui.reader

/**
 * Small, platform-independent rules used by the immersive reader.
 *
 * The lyric viewport stays in a fixed full-window box. When chrome changes its
 * content padding, the scroll position moves by the same amount so the line the
 * reader was looking at stays anchored on screen whenever the scroll range allows it.
 */
internal object ReaderImmersivePolicy {
    const val MAX_SINGLE_TAP_DURATION_MS = 300L

    fun scrollCompensationPx(fromTopChromePx: Int, toTopChromePx: Int): Float =
        (toTopChromePx - fromTopChromePx).toFloat()

    fun isIntentionalSingleTap(
        travelPx: Float,
        touchSlopPx: Float,
        pointerCount: Int,
        durationMs: Long
    ): Boolean =
        pointerCount == 1 &&
            travelPx <= touchSlopPx &&
            durationMs in 0..MAX_SINGLE_TAP_DURATION_MS
}
