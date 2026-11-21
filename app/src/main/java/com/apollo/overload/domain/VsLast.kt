package com.apollo.overload.domain

import com.apollo.overload.data.SetEntry

/** Today's dialed numbers against last time, for the "vs last" cue on the hero dials. */
object VsLast {
    /** Dialed kg minus last time's top kg; null when there is no history. */
    fun kg(kg: Double, last: List<SetEntry>): Double? = last.maxOfOrNull { it.kg }?.let { Progression.round2(kg - it) }

    /** Dialed reps minus last time's reps on the same set; null when last time had no such set. */
    fun reps(reps: Int, last: List<SetEntry>, setIndex: Int): Int? = last.getOrNull(setIndex)?.let { reps - it.reps }
}
