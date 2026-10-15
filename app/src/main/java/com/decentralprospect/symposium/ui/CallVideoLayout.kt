package com.decentralprospect.symposium

internal data class FittedFrameSize(
    val width: Float,
    val height: Float
)

internal fun fitFrameWithin(
    maximumWidth: Float,
    maximumHeight: Float,
    frameAspectRatio: Float
): FittedFrameSize {
    val width = maximumWidth.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val height = maximumHeight.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val aspectRatio = frameAspectRatio
        .takeIf { it.isFinite() && it > 0f }
        ?.coerceIn(0.01f, 100f)
        ?: (16f / 9f)

    if (width == 0f || height == 0f) return FittedFrameSize(0f, 0f)

    return if (aspectRatio >= width / height) {
        FittedFrameSize(width = width, height = width / aspectRatio)
    } else {
        FittedFrameSize(width = height * aspectRatio, height = height)
    }
}

internal fun networkSignalBars(pingMs: Long?): Int = when {
    pingMs == null -> 0
    pingMs <= 80L -> 5
    pingMs <= 150L -> 4
    pingMs <= 250L -> 3
    pingMs <= 450L -> 2
    else -> 1
}
