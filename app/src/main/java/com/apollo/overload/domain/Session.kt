package com.apollo.overload.domain

import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.SetEntry

/**
 * Derived state of an open workout.
 *
 * @param dayPlan the day's plan rows in position order (the session uses those listed in [rawOrder]).
 * @param rawOrder Workout.order.
 * @param sets this workout's logged sets.
 * @param setCounts per plan-exercise set-count overrides for today (extra sets, or an early end).
 */
class Session(
    dayPlan: List<PlanExercise>,
    rawOrder: List<Long>,
    sets: List<SetEntry>,
    private val types: Map<String, String>,
    private val setCounts: Map<Long, Int> = emptyMap(),
) {
    private val inOrder = rawOrder.toSet()

    /** Session exercises in plan order. */
    val plan: List<PlanExercise> = dayPlan.filter { it.id in inOrder }
    private val byId = plan.associateBy { it.id }

    /** Session exercises in the order they are being done. */
    val order: List<Long> = rawOrder.filter { it in byId }

    val logged: Map<Long, List<SetEntry>> = sets.filter { it.planExerciseId != null }
        .groupBy { it.planExerciseId!! }.mapValues { (_, v) -> v.sortedWith(compareBy({ it.setIndex }, { it.loggedAt })) }

    /** Exercises with sets logged in this workout that are no longer in the session's plan, in logging order. */
    val extra: List<Long> = logged.keys.filter { it !in byId }

    val items: List<OrderItem> = plan.map { OrderItem(it.id, types[it.exerciseId] ?: "", it.pairId) }

    fun planRow(id: Long): PlanExercise? = byId[id]
    fun today(id: Long): List<SetEntry> = logged[id].orEmpty()

    /** Sets to do today: the override, else the plan's count; never fewer than already logged. */
    fun target(id: Long): Int = maxOf(setCounts[id] ?: byId[id]?.sets ?: 0, today(id).size)

    fun isStarted(id: Long) = today(id).isNotEmpty()
    fun isDone(id: Long) = today(id).size >= target(id)

    val started: Set<Long> get() = order.filter { isStarted(it) }.toSet()
    val done: Set<Long> get() = order.filter { isDone(it) }.toSet()

    /** The exercise being done now: the first unfinished one in session order. */
    val active: Long? get() = order.firstOrNull { !isDone(it) }

    /** 1-based position in the session. */
    fun rank(id: Long): Int = order.indexOf(id) + 1
    /** 1-based position in the plan. */
    fun planPos(id: Long): Int = plan.indexOfFirst { it.id == id } + 1
    fun moved(id: Long): Int = PairOrder.moved(items, order, id)

    /** True when today's order differs from the plan's. */
    val orderChanged: Boolean get() = order != plan.map { it.id }

    /** The order "Back to plan order" gives: exercises with logged sets keep their place, the rest follow the plan. */
    fun planOrder(): List<Long> = rebuild(false, activeId = null)

    fun start(id: Long, pullFirst: Boolean): StartResult = PairOrder.start(items, order, pullFirst, started, done, id)
    fun rebuild(pullFirst: Boolean, activeId: Long? = active): List<Long> =
        PairOrder.rebuild(items, order, pullFirst, started, done, activeId)
}
