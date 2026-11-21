package com.apollo.overload.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairOrderTest {
    // Upper A: (Incline smith, Lat pulldown) (Shoulder press, CS row) Pec deck (Curl, Pushdown)
    private val upperA = listOf(
        OrderItem(1, "push", 1), OrderItem(2, "pull", 1),
        OrderItem(3, "push", 2), OrderItem(4, "pull", 2),
        OrderItem(5, "push", null),
        OrderItem(6, "pull", 3), OrderItem(7, "push", 3),
    )
    private val planOrder = listOf(1L, 2, 3, 4, 5, 6, 7)

    @Test fun groupsJoinAdjacentPairs() {
        assertEquals(listOf(listOf(1L, 2), listOf(3L, 4), listOf(5L), listOf(6L, 7)), PairOrder.groups(upperA).map { g -> g.map { it.id } })
    }

    @Test fun sameIdNotAdjacentIsNoPair() {
        val plan = listOf(OrderItem(1, "push", 1), OrderItem(2, "legs", null), OrderItem(3, "pull", 1))
        assertEquals(3, PairOrder.groups(plan).size)
        assertEquals(0, PairOrder.pairNumber(plan, 1))
    }

    @Test fun sequencePullFirstSwapsOnlyPushFirstPairs() {
        assertEquals(planOrder, PairOrder.sequence(upperA, false))
        assertEquals(listOf(2L, 1, 4, 3, 5, 6, 7), PairOrder.sequence(upperA, true))
    }

    @Test fun startingSecondPullSwitchesRestToPullFirst() {
        val r = PairOrder.start(upperA, planOrder, false, emptySet(), emptySet(), 2)
        assertEquals(OrderSwitch.PullFirst, r.switched)
        assertEquals(true, r.pullFirst)
        assertEquals(listOf(2L, 1, 4, 3, 5, 6, 7), r.order)
        assertEquals(1L, PairOrder.busyExercise(upperA, r.order)?.id)
        assertEquals(1, PairOrder.moved(upperA, r.order, 2))
        assertEquals(-1, PairOrder.moved(upperA, r.order, 1))
        assertEquals(0, PairOrder.moved(upperA, r.order, 5))
    }

    @Test fun startingPlanFirstPushSwitchesBack() {
        val order = listOf(2L, 1, 4, 3, 5, 6, 7)
        // Lat pulldown done, Incline smith not started; user starts Shoulder press (plan-first push of pair 2).
        val r = PairOrder.start(upperA, order, true, setOf(2), setOf(2), 3)
        assertEquals(OrderSwitch.PlanOrder, r.switched)
        assertEquals(false, r.pullFirst)
        assertEquals(listOf(2L, 3, 1, 4, 5, 6, 7), r.order)
    }

    @Test fun startedPartnerMeansNoSwitch() {
        val order = listOf(2L, 1, 4, 3, 5, 6, 7)
        // Incline smith's partner (Lat pulldown) is started, so starting it keeps pull-first.
        val r = PairOrder.start(upperA, order, true, setOf(2), setOf(2), 1)
        assertNull(r.switched)
        assertEquals(listOf(2L, 1, 4, 3, 5, 6, 7), r.order)
    }

    @Test fun startingPlanFirstPullOrUnpairedKeepsMode() {
        val r = PairOrder.start(upperA, planOrder, false, emptySet(), emptySet(), 6)
        assertNull(r.switched)
        assertEquals(listOf(6L, 1, 2, 3, 4, 5, 7), r.order)
        val r2 = PairOrder.start(upperA, listOf(2L, 1, 4, 3, 5, 6, 7), true, setOf(2), setOf(2), 5)
        assertNull(r2.switched)
        assertEquals(listOf(2L, 5, 1, 4, 3, 6, 7), r2.order)
    }

    @Test fun startingSecondPushDoesNotSwitch() {
        val r = PairOrder.start(upperA, planOrder, false, emptySet(), emptySet(), 7)
        assertNull(r.switched)
        assertEquals(false, r.pullFirst)
    }

    @Test fun undoRestoresPlanOrderKeepingActive() {
        val order = listOf(2L, 1, 4, 3, 5, 6, 7)
        assertEquals(listOf(2L, 1, 3, 4, 5, 6, 7), PairOrder.rebuild(upperA, order, false, setOf(2), emptySet(), 2))
    }

    @Test fun rebuildKeepsDoneThenActiveThenOpenAndDropsUnknown() {
        // 1 done, 2 started but unfinished, user starts 5 now; 99 no longer in the plan.
        val order = listOf(1L, 2, 99, 3, 4, 5, 6, 7)
        assertEquals(listOf(1L, 5, 2, 3, 4, 6, 7), PairOrder.rebuild(upperA, order, false, setOf(1, 2), setOf(1), 5))
    }

    @Test fun rebuildRespectsSkippedExercises() {
        // Pec deck skipped = removed from the order.
        val order = listOf(1L, 2, 3, 4, 6, 7)
        assertEquals(listOf(2L, 1, 4, 3, 6, 7), PairOrder.rebuild(upperA, order, true, emptySet(), emptySet(), null))
    }

    @Test fun pairNumbersAndPartners() {
        assertEquals(1, PairOrder.pairNumber(upperA, 2))
        assertEquals(3, PairOrder.pairNumber(upperA, 7))
        assertEquals(0, PairOrder.pairNumber(upperA, 5))
        assertEquals(3L, PairOrder.partner(upperA, 4)?.id)
        assertNull(PairOrder.partner(upperA, 5))
    }
}
