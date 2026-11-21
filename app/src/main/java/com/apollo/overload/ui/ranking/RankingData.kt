package com.apollo.overload.ui.ranking

import android.content.Context
import com.apollo.overload.data.GymDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** One muscle group of assets/ranking.json. Keys follow the mockup groups, not the body-map keys. */
@Serializable
data class RankMuscle(val key: String, val label: String, val exercises: List<RankEntry>)

/** Axis scores are 1..10, higher is better (lowFatigue = low systemic and joint cost). [id] is an exercise id. */
@Serializable
data class RankEntry(
    val id: String,
    val name: String,
    val stretch: Int,
    val overload: Int,
    val lowFatigue: Int,
    val evidence: Int,
    val why: String,
) {
    /** Weighted overall score 0..100: stretch 30%, overload 30%, low fatigue 15%, evidence 25%. */
    val overall: Int get() = ((stretch * .3 + overload * .3 + lowFatigue * .15 + evidence * .25) * 10).roundToInt()

    fun score(axis: Axis?): Int = when (axis) {
        null -> overall
        Axis.Stretch -> stretch
        Axis.Overload -> overload
        Axis.LowFatigue -> lowFatigue
        Axis.Evidence -> evidence
    }
}

enum class Axis(val label: String) { Stretch("Stretch"), Overload("Overload"), LowFatigue("Low fatigue"), Evidence("Evidence") }

@Serializable
private data class RankFile(val muscles: List<RankMuscle>)

private val json = Json { ignoreUnknownKeys = true }

fun loadRanking(context: Context): List<RankMuscle> =
    json.decodeFromString(RankFile.serializer(), context.assets.open("ranking.json").use { it.readBytes().decodeToString() }).muscles

/** Exercise ids used anywhere in the active split's plan. */
@OptIn(ExperimentalCoroutinesApi::class)
fun planExerciseIds(dao: GymDao): Flow<Set<String>> = dao.activeSplit().flatMapLatest { split ->
    if (split == null) flowOf(emptySet())
    else dao.days(split.id).flatMapLatest { days ->
        if (days.isEmpty()) flowOf(emptySet())
        else combine(days.map { dao.dayExercises(it.id) }) { lists -> lists.flatMap { l -> l.map { it.exerciseId } }.toSet() }
    }
}
