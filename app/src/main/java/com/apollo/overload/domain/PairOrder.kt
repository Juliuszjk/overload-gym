package com.apollo.overload.domain

/** One session exercise as the pair logic sees it. type: push | pull | legs | core. */
data class OrderItem(val id: Long, val type: String, val pairId: Int?)

enum class OrderSwitch { PullFirst, PlanOrder }

data class StartResult(val order: List<Long>, val pullFirst: Boolean, val switched: OrderSwitch?)

/**
 * Push/pull pair re-ordering. Adjacent plan items with the same non-null pairId form a pair whose plan order is
 * "plan-first". Starting the pull of a not-yet-started pair when it is not plan-first switches the rest of the
 * session to pull-first; starting the plan-first push of a not-yet-started pair switches back to plan order.
 *
 * All functions take the session plan: the session's exercises in plan position order.
 */
object PairOrder {

    /** Plan items split into groups; a group of two is a pair. */
    fun groups(plan: List<OrderItem>): List<List<OrderItem>> {
        val out = mutableListOf<MutableList<OrderItem>>()
        for (e in plan) {
            val last = out.lastOrNull()
            if (e.pairId != null && last != null && last.size == 1 && last[0].pairId == e.pairId) last.add(e)
            else out.add(mutableListOf(e))
        }
        return out
    }

    private fun pairOf(plan: List<OrderItem>, id: Long): List<OrderItem>? =
        groups(plan).firstOrNull { g -> g.size == 2 && g.any { it.id == id } }

    /** Plan order, with each pair's pull first when [pullFirst] (pairs without exactly one pull keep plan order). */
    fun sequence(plan: List<OrderItem>, pullFirst: Boolean): List<Long> = groups(plan).flatMap { g ->
        val pull = g.singleOrNull { it.type == "pull" }
        if (g.size == 2 && pullFirst && pull != null) listOf(pull, g.first { it !== pull }) else g
    }.map { it.id }

    /**
     * New session order: done exercises (in their current order), then [activeId], then started but unfinished
     * ones, then every unstarted exercise in [sequence] order. Ids not in [plan] are dropped.
     */
    fun rebuild(
        plan: List<OrderItem>, order: List<Long>, pullFirst: Boolean,
        started: Set<Long>, done: Set<Long>, activeId: Long?,
    ): List<Long> {
        val known = plan.map { it.id }.toSet()
        val current = order.filter { it in known }
        val inSession = current.toSet()
        val active = activeId?.takeIf { it in inSession && it !in done }
        val finished = current.filter { it in done }
        val open = current.filter { it in started && it !in done && it != active }
        val rest = sequence(plan.filter { it.id in inSession }, pullFirst)
            .filter { it !in started && it !in done && it != active }
        return finished + listOfNotNull(active) + open + rest
    }

    /** Start [id] now: it becomes the next exercise, and the pair rule may flip the order of the rest. */
    fun start(
        plan: List<OrderItem>, order: List<Long>, pullFirst: Boolean,
        started: Set<Long>, done: Set<Long>, id: Long,
    ): StartResult {
        var pf = pullFirst
        var switched: OrderSwitch? = null
        val pair = pairOf(plan.filter { it.id in order }, id)
        if (pair != null && pair.none { it.id in started }) {
            val me = pair.first { it.id == id }
            val planFirst = pair[0].id == id
            if (me.type == "pull" && !planFirst && !pf) { pf = true; switched = OrderSwitch.PullFirst }
            else if (me.type == "push" && planFirst && pf) { pf = false; switched = OrderSwitch.PlanOrder }
        }
        return StartResult(rebuild(plan, order, pf, started, done, id), pf, switched)
    }

    /** 1-based number of the pair [id] belongs to (counting pairs only), or 0 when it has no partner. */
    fun pairNumber(plan: List<OrderItem>, id: Long): Int {
        val pairs = groups(plan).filter { it.size == 2 }
        return pairs.indexOfFirst { g -> g.any { it.id == id } } + 1
    }

    fun partner(plan: List<OrderItem>, id: Long): OrderItem? = pairOf(plan, id)?.firstOrNull { it.id != id }

    /**
     * The plan-first exercise of the first pair currently done out of plan order (the one that was "busy"), or null
     * when every pair is in plan order.
     */
    fun busyExercise(plan: List<OrderItem>, order: List<Long>): OrderItem? = groups(plan)
        .filter { it.size == 2 }
        .firstOrNull { g ->
            val a = order.indexOf(g[0].id)
            val b = order.indexOf(g[1].id)
            a >= 0 && b >= 0 && b < a
        }?.get(0)

    /** Plan position minus session position: > 0 = moved earlier than planned, < 0 = later. */
    fun moved(plan: List<OrderItem>, order: List<Long>, id: Long): Int {
        val planPos = plan.indexOfFirst { it.id == id }
        val pos = order.indexOf(id)
        return if (planPos < 0 || pos < 0) 0 else planPos - pos
    }
}
