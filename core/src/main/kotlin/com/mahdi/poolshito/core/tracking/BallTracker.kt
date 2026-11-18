package com.mahdi.poolshito.core.tracking

import com.mahdi.poolshito.core.analysis.BallDetection
import com.mahdi.poolshito.core.analysis.BallKind
import com.mahdi.poolshito.core.analysis.BallTrack
import com.mahdi.poolshito.geometry.Point2
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Constant-velocity 2D Kalman filters with gated, globally sorted nearest-neighbor assignment. */
class BallTracker(private val maxMissedFrames: Int = 8, private val gateDistancePx: Double = 80.0) {
    private data class State(
        val id: Int, var x: Double, var y: Double, var vx: Double = 0.0, var vy: Double = 0.0,
        var p: Array<DoubleArray> = Array(4) { r -> DoubleArray(4) { c -> if (r == c) 25.0 else 0.0 } },
        var radius: Double, var kind: BallKind, var confidence: Double,
        var age: Int = 1, var missed: Int = 0,
    )
    private data class Pairing(val track: State, val detectionIndex: Int, val distance: Double)
    private val states = mutableListOf<State>()
    private var nextId = 1
    private var lastTimestampNs: Long? = null

    @Synchronized fun update(detections: List<BallDetection>, timestampNs: Long): List<BallTrack> {
        val prev = lastTimestampNs
        val dt = if (prev == null) 1.0 / 30.0 else ((timestampNs - prev) / 1e9).coerceIn(1.0 / 120.0, 0.5)
        lastTimestampNs = timestampNs
        states.forEach { predict(it, dt) }
        val pairs = states.flatMap { track -> detections.indices.map { i ->
            val d = detections[i]
            val distance = hypot(d.center.x - track.x, d.center.y - track.y)
            Pairing(track, i, distance)
        } }.filter { pair ->
            val predictedTravel = hypot(pair.track.vx, pair.track.vy) * dt
            pair.distance <= (gateDistancePx + predictedTravel * 1.5).coerceAtMost(320.0)
        }.sortedBy { it.distance }
        val usedTracks = mutableSetOf<Int>(); val usedDetections = mutableSetOf<Int>()
        pairs.forEach { p -> if (p.track.id !in usedTracks && p.detectionIndex !in usedDetections) {
            correct(p.track, detections[p.detectionIndex], dt)
            usedTracks += p.track.id; usedDetections += p.detectionIndex
        } }
        states.filter { it.id !in usedTracks }.forEach { it.missed++ }
        detections.forEachIndexed { i, d -> if (i !in usedDetections) states += State(nextId++, d.center.x, d.center.y, radius = d.radiusPx, kind = d.kind, confidence = d.confidence) }
        states.removeAll { it.missed > maxMissedFrames }
        return states.map { it.asTrack() }
    }

    @Synchronized fun reset() { states.clear(); nextId = 1; lastTimestampNs = null }

    private fun predict(s: State, dt: Double) {
        s.x += s.vx * dt; s.y += s.vy * dt
        val f = arrayOf(doubleArrayOf(1.0,0.0,dt,0.0), doubleArrayOf(0.0,1.0,0.0,dt), doubleArrayOf(0.0,0.0,1.0,0.0), doubleArrayOf(0.0,0.0,0.0,1.0))
        val fp = multiply(f, s.p); s.p = multiply(fp, transpose(f))
        val q = 4.0; val qp = q * dt * dt * dt * dt / 4.0; val qc = q * dt * dt * dt / 2.0; val qv = q * dt * dt
        s.p[0][0] += qp; s.p[1][1] += qp; s.p[0][2] += qc; s.p[2][0] += qc
        s.p[1][3] += qc; s.p[3][1] += qc; s.p[2][2] += qv; s.p[3][3] += qv
    }

    private fun correct(s: State, d: BallDetection, dt: Double) {
        val r = max(4.0, d.radiusPx * 0.18).let { it * it }
        val s00 = s.p[0][0] + r; val s01 = s.p[0][1]; val s10 = s.p[1][0]; val s11 = s.p[1][1] + r
        val det = s00 * s11 - s01 * s10
        if (abs(det) < 1e-9) return
        val inv = arrayOf(doubleArrayOf(s11 / det, -s01 / det), doubleArrayOf(-s10 / det, s00 / det))
        val k = Array(4) { row -> DoubleArray(2) { col -> s.p[row][0] * inv[0][col] + s.p[row][1] * inv[1][col] } }
        val rx = d.center.x - s.x; val ry = d.center.y - s.y
        val correction = DoubleArray(4) { k[it][0] * rx + k[it][1] * ry }
        s.x += correction[0]; s.y += correction[1]; s.vx += correction[2]; s.vy += correction[3]
        val ikh = Array(4) { row -> DoubleArray(4) { col -> (if (row == col) 1.0 else 0.0) - if (col < 2) k[row][col] else 0.0 } }
        s.p = multiply(ikh, s.p)
        if (s.age == 1 && dt > 0.0) { s.vx = correction[0] / dt * 0.35; s.vy = correction[1] / dt * 0.35 }
        s.radius = d.radiusPx; if (d.kind != BallKind.UNKNOWN) s.kind = d.kind
        s.confidence = d.confidence; s.age++; s.missed = 0
    }

    private fun State.asTrack() = BallTrack(id, Point2(x,y), Point2(vx,vy), radius, kind, confidence, age, missed)
    private fun transpose(a: Array<DoubleArray>) = Array(a[0].size) { r -> DoubleArray(a.size) { c -> a[c][r] } }
    private fun multiply(a: Array<DoubleArray>, b: Array<DoubleArray>) = Array(a.size) { r -> DoubleArray(b[0].size) { c -> a[r].indices.sumOf { k -> a[r][k] * b[k][c] } } }
}
