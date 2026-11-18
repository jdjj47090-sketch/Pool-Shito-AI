package com.mahdi.poolshito.core.physics

import com.mahdi.poolshito.core.analysis.BallKind
import com.mahdi.poolshito.core.analysis.TrajectoryKind
import com.mahdi.poolshito.core.analysis.TrajectorySegment
import com.mahdi.poolshito.geometry.Point2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Rigid equal-mass sphere approximation with rolling ignored and restitution in [0,1]. */
class TrajectoryAnalyzer(private val restitution: Double = 0.90, private val ballRadius: Double = 0.0225) {
    init { require(restitution in 0.0..1.0); require(ballRadius > 0.0 && ballRadius < 0.5) }

    data class MotionBall(val id: Int, val position: Point2, val velocity: Point2, val kind: BallKind)
    data class Analysis(val segments: List<TrajectorySegment>, val description: String)

    fun analyze(balls: List<MotionBall>): Analysis? {
        val cue = balls.firstOrNull { it.kind == BallKind.CUE } ?: return null
        if (cue.velocity.length() < 0.035) return suggestShot(cue, balls)
        val direction = cue.velocity.normalized()
        val candidates = balls.filter { it.id != cue.id }.mapNotNull { ball ->
            val relative = ball.position - cue.position
            val along = dot(relative, direction)
            if (along <= 0.0) return@mapNotNull null
            val lateral = abs(cross(relative, direction))
            if (lateral > 2.0 * ballRadius) return@mapNotNull null
            val contactDistance = max(0.0, along - kotlin.math.sqrt(max(0.0, 4 * ballRadius * ballRadius - lateral * lateral)))
            Triple(ball, contactDistance, relative.normalized())
        }.minByOrNull { it.second }

        val segments = mutableListOf<TrajectorySegment>()
        val cueEnd = candidates?.let { cue.position + direction * it.second } ?: firstBoundary(cue.position, direction)
        val cuePath = trace(cue.position, cueEnd, direction, bounceLimit = 1)
        segments += TrajectorySegment(cuePath, TrajectoryKind.CUE_BALL, cue.id,
            if (candidates == null) "Cue path estimate; no ball contact detected" else "Estimated cue path to ball ${candidates.first.id}")
        var description = if (candidates == null) "Estimated free path; no ball contact is currently aligned" else "Estimated collision with ball ${candidates.first.id}"
        if (candidates != null) {
            val (target, _, normal) = candidates
            val normalSpeed = dot(cue.velocity, normal)
            val targetVelocity = normal * (max(0.0, normalSpeed) * (1.0 + restitution) / 2.0)
            if (targetVelocity.length() > 0.005) {
                val targetDirection = targetVelocity.normalized()
                val targetEnd = firstBoundary(target.position, targetDirection)
                segments += TrajectorySegment(
                    trace(target.position, targetEnd, targetDirection, bounceLimit = 1),
                    TrajectoryKind.OBJECT_BALL, target.id, "Estimated target-ball path after equal-mass impact",
                )
                description += "; target path assumes equal ball masses and rolling contact"
            }
        }
        return Analysis(segments, description)
    }


    /** Generates one clear geometric candidate for a stationary layout; it does not infer the player's aim. */
    private fun suggestShot(cue: MotionBall, balls: List<MotionBall>): Analysis? {
        val pockets = listOf(
            Point2(ballRadius * 1.5, ballRadius * 1.5), Point2(1.0, ballRadius * 1.2), Point2(2.0 - ballRadius * 1.5, ballRadius * 1.5),
            Point2(ballRadius * 1.5, 1.0 - ballRadius * 1.5), Point2(1.0, 1.0 - ballRadius * 1.2), Point2(2.0 - ballRadius * 1.5, 1.0 - ballRadius * 1.5),
        )
        data class Candidate(val target: MotionBall, val pocket: Point2, val contact: Point2, val score: Double)
        val candidate = balls.asSequence().filter { it.id != cue.id }.flatMap { target -> pockets.asSequence().map { pocket ->
            val direction = (pocket - target.position).normalized()
            val contact = target.position - direction * (2.0 * ballRadius)
            val inside = contact.x in ballRadius..(2.0 - ballRadius) && contact.y in ballRadius..(1.0 - ballRadius)
            val routeClear = balls.filter { it.id != cue.id && it.id != target.id }.all { other ->
                distanceToSegment(other.position, cue.position, contact) > 2.2 * ballRadius &&
                    distanceToSegment(other.position, target.position, pocket) > 2.2 * ballRadius
            }
            Candidate(target, pocket, contact, if (inside && routeClear) (contact - cue.position).length() + (pocket - target.position).length() else Double.POSITIVE_INFINITY)
        } }.filter { it.score.isFinite() }.minByOrNull { it.score } ?: return null
        return Analysis(
            listOf(
                TrajectorySegment(listOf(cue.position, candidate.contact), TrajectoryKind.CUE_BALL, cue.id, "Candidate cue-to-object-ball line; actual cue aim is not measured"),
                TrajectorySegment(listOf(candidate.target.position, candidate.pocket), TrajectoryKind.OBJECT_BALL, candidate.target.id, "Candidate pocket route; outcome depends on speed, spin, and collision"),
            ),
            "Candidate route to a pocket; this is geometric guidance, not a shot prediction",
        )
    }

    private fun distanceToSegment(point: Point2, start: Point2, end: Point2): Double {
        val segment = end - start
        val lengthSquared = dot(segment, segment)
        if (lengthSquared < 1e-12) return (point - start).length()
        val t = (dot(point - start, segment) / lengthSquared).coerceIn(0.0, 1.0)
        return (point - (start + segment * t)).length()
    }

    private fun firstBoundary(p: Point2, d: Point2): Point2 {
        val tx = when { d.x > 1e-9 -> (2.0 - ballRadius - p.x) / d.x; d.x < -1e-9 -> (ballRadius - p.x) / d.x; else -> Double.POSITIVE_INFINITY }
        val ty = when { d.y > 1e-9 -> (1.0 - ballRadius - p.y) / d.y; d.y < -1e-9 -> (ballRadius - p.y) / d.y; else -> Double.POSITIVE_INFINITY }
        val t = min(tx, ty).coerceAtLeast(0.0)
        return p + d * t
    }

    private fun trace(start: Point2, firstEnd: Point2, initial: Point2, bounceLimit: Int): List<Point2> {
        val points = mutableListOf(start, firstEnd)
        var direction = initial
        var end = firstEnd
        repeat(bounceLimit) {
            val xWall = abs(end.x - ballRadius) < 1e-5 || abs(end.x - (2.0 - ballRadius)) < 1e-5
            val yWall = abs(end.y - ballRadius) < 1e-5 || abs(end.y - (1.0 - ballRadius)) < 1e-5
            if (!xWall && !yWall) return points
            direction = Point2(if (xWall) -direction.x else direction.x, if (yWall) -direction.y else direction.y).normalized()
            end = firstBoundary(end, direction)
            points += end
        }
        return points
    }

    private fun dot(a: Point2, b: Point2) = a.x * b.x + a.y * b.y
    private fun cross(a: Point2, b: Point2) = a.x * b.y - a.y * b.x
}
