package com.example.leanangletracker.sensor

import com.example.leanangletracker.data.Vec3
import kotlin.math.PI
import kotlin.math.exp

class LowPassFilter(private val cutoffHz: Float) {
    private var initialized = false
    private var state = Vec3(0f, 0f, 0f)

    fun reset() {
        initialized = false
        state = Vec3(0f, 0f, 0f)
    }

    fun update(input: Vec3, dtSec: Float): Vec3 {
        if (!initialized || dtSec <= 0f) {
            state = input
            initialized = true
            return state
        }
        val tau = 1f / (2f * PI.toFloat() * cutoffHz)
        val alpha = exp((-dtSec / tau).toDouble()).toFloat().coerceIn(0f, 1f)
        state = state * alpha + input * (1f - alpha)
        return state
    }
}
