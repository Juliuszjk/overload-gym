package com.apollo.overload.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.withTransaction
import com.apollo.overload.data.Exercise
import com.apollo.overload.data.Muscles
import com.apollo.overload.data.PlanDay
import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.Repository
import com.apollo.overload.domain.Progression
import com.apollo.overload.ui.common.BodyFigure
import com.apollo.overload.ui.common.Side
import com.apollo.overload.ui.common.kg
import com.apollo.overload.ui.common.parseNumber
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Applies a pair choice for row [i] ("none", "prev", "next") and drops pairs it breaks. */
private fun applyPair(rows: List<PlanExercise>, i: Int, mode: String): List<PlanExercise> {
    val l = rows.toMutableList()
    val newId = (rows.mapNotNull { it.pairId }.maxOrNull() ?: 0) + 1
    when (mode) {
        "none" -> l[i] = l[i].copy(pairId = null)
        "prev" -> if (i > 0) { l[i - 1] = l[i - 1].copy(pairId = newId); l[i] = l[i].copy(pairId = newId) }
        "next" -> if (i < l.size - 1) { l[i + 1] = l[i + 1].copy(pairId = newId); l[i] = l[i].copy(pairId = newId) }
    }
    return normalizePlan(l)
}

/** Edit sheet for one plan row (mockup editSheet), with a muscle map editor for its exercise. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSheet(
    repo: Repository, scope: CoroutineScope, day: PlanDay, rows: List<PlanExercise>, rowId: Long,
    exMap: Map<String, Exercise>, onClose: () -> Unit,
) {
    val dao = repo.dao
    val p = LocalPalette.current
    val i = rows.indexOfFirst { it.id == rowId }
    // A freshly added row may not have arrived from the DB yet.
    if (i < 0) return
    val x = rows[i]
    val e = exMap[x.exerciseId]

    var kgText by remember(rowId) { mutableStateOf(kg(x.kg)) }
    var sets by remember(rowId) { mutableIntStateOf(x.sets) }
    var lo by remember(rowId) { mutableStateOf(x.repMin.toString()) }
    var hi by remember(rowId) { mutableStateOf(x.repMax.toString()) }
    var inc by remember(rowId) { mutableStateOf(kg(x.increment)) }
    var pct by remember(rowId) { mutableStateOf(kg(x.incrementPct)) }
    var note by remember(rowId) { mutableStateOf(x.note) }
    var url by remember(rowId) { mutableStateOf(x.url) }
    var removed by remember(rowId) { mutableStateOf(false) }
    var muscleMode by remember(rowId) { mutableStateOf(false) }
    var muscles by remember(rowId) { mutableStateOf(e?.muscles ?: emptyMap()) }

    fun draft(r: PlanExercise): PlanExercise {
        val a = lo.toIntOrNull() ?: r.repMin
        val b = hi.toIntOrNull() ?: r.repMax
        return r.copy(
            kg = parseNumber(kgText)?.coerceAtLeast(0.0) ?: r.kg, sets = sets,
            repMin = minOf(a, b).coerceAtLeast(1), repMax = maxOf(a, b).coerceAtLeast(1),
            increment = parseNumber(inc)?.coerceAtLeast(0.0) ?: r.increment,
            incrementPct = parseNumber(pct)?.coerceAtLeast(0.0) ?: r.incrementPct, note = note.trim(), url = url.trim(),
        )
    }

    fun save() {
        if (removed) return
        val updated = draft(x)
        if (updated != x) scope.launch {
            // The load is shared by every row of this exercise in the split.
            val same = if (updated.kg == x.kg) emptyList()
            else dao.splitExercisesNow(x.dayId).filter { it.id != x.id && it.exerciseId == x.exerciseId && it.kg != updated.kg }
            dao.updatePlanExercises(listOf(updated) + same.map { it.copy(kg = updated.kg) })
        }
    }

    PlanSheet(onDismiss = { save(); onClose() }) { close ->
        if (muscleMode && e != null) {
            SheetHead("${e.name} · Muscles", { muscleMode = false })
            Sub("Primary counts as 1 set for weekly volume, secondary as half a set. Tap the figure to cycle a muscle.")
            MuscleEditor(muscles, { muscles = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallGhost("Cancel", { muscles = e.muscles; muscleMode = false })
                SmallInk("Save muscles", {
                    scope.launch { dao.upsertExercise(e.copy(muscles = muscles, custom = true)) }
                    muscleMode = false
                })
            }
            return@PlanSheet
        }

        SheetHead("${day.name} · #${i + 1}", close)
        Column {
            Text(e?.name ?: x.exerciseId, style = SheetTitle, color = p.ink)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (e != null) TypeTag(e.type)
                Sub(e?.musclesText() ?: "")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Weight, total kg", Modifier.weight(1f)) { FieldInput(kgText, { kgText = it }, keyboard = KeyboardType.Decimal) }
            Field("Sets", Modifier.weight(1f)) { Stepper(sets, { sets = it.coerceIn(1, 10) }) }
        }
        Field("Rep range · double progression") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldInput(lo, { lo = it.filter(Char::isDigit) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                Text("–", style = Type.label, color = p.mute)
                FieldInput(hi, { hi = it.filter(Char::isDigit) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
            }
            val d = draft(x)
            val step = if (d.increment > 0) ", step ${kg(d.increment)} kg" else ""
            Text(
                "When every set reaches ${d.repMax} reps, the next workout suggests +${kg(Progression.jump(d, d.kg))} kg " +
                    "(${kg(d.incrementPct)} %$step).",
                style = Type.small.copy(fontSize = 12.5.sp), color = p.mute,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Load step, kg", Modifier.weight(1f)) { FieldInput(inc, { inc = it }, keyboard = KeyboardType.Decimal) }
            Field("Increase, %", Modifier.weight(1f)) { FieldInput(pct, { pct = it }, keyboard = KeyboardType.Decimal) }
        }
        Field("Note") { FieldInput(note, { note = it }, big = false, placeholder = "Seat 5, setting 2…") }
        Field("Video URL") { FieldInput(url, { url = it }, big = false, keyboard = KeyboardType.Uri, placeholder = "https://…") }
        Field("Push/pull pair") {
            val prev = rows.getOrNull(i - 1)
            val next = rows.getOrNull(i + 1)
            val cur = when {
                x.pairId == null -> "none"
                prev?.pairId == x.pairId -> "prev"
                next?.pairId == x.pairId -> "next"
                else -> "none"
            }
            fun pick(mode: String) {
                val list = applyPair(rows.map { if (it.id == x.id) draft(it) else it }, i, mode)
                scope.launch { dao.updatePlanExercises(list) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("No pair", cur == "none", { pick("none") })
                Chip(prev?.let { "With ${exMap[it.exerciseId]?.name ?: it.exerciseId} (above)" } ?: "With the one above", cur == "prev", { pick("prev") }, enabled = prev != null)
                Chip(next?.let { "With ${exMap[it.exerciseId]?.name ?: it.exerciseId} (below)" } ?: "With the one below", cur == "next", { pick("next") }, enabled = next != null)
            }
        }
        if (e != null) SmallGhost("Edit muscles", { muscles = e.muscles; muscleMode = true }, icon = Icons.Outlined.Accessibility)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SmallGhost("Remove", {
                removed = true
                scope.launch {
                    repo.db.withTransaction {
                        dao.deletePlanExercise(x)
                        dao.syncOpenWorkout(x.dayId, x.id, add = false)
                        dao.updatePlanExercises(normalizePlan(rows.filter { it.id != x.id }))
                    }
                }
                close()
            }, icon = Icons.Outlined.Delete)
            SmallInk("Done", close)
        }
    }
}

/** Sets stepper (mockup .stepper). */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().border(1.5.dp, p.line2, RoundedCornerShape(12.dp)).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepBtn(Icons.Outlined.Remove, "Fewer") { onChange(value - 1) }
        Text("$value", Modifier.weight(1f), style = Type.display(22.sp, 800), color = p.ink, textAlign = TextAlign.Center)
        StepBtn(Icons.Outlined.Add, "More") { onChange(value + 1) }
    }
}

@Composable
private fun StepBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(p.sunk).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = p.ink, modifier = Modifier.size(20.dp))
    }
}

private val Levels = listOf(0f, 0.4f, 1f)
private val LevelLabels = listOf("None", "Secondary", "Primary")
private fun level(v: Float?): Int = when { v == null || v <= 0f -> 0; v >= 1f -> 2; else -> 1 }

/** Muscle map editor: front/back heat preview plus a none/secondary/primary selector per muscle. */
@Composable
fun MuscleEditor(muscles: Map<String, Float>, onChange: (Map<String, Float>) -> Unit) {
    val p = LocalPalette.current
    fun set(key: String, lv: Int) {
        val m = muscles.toMutableMap()
        if (lv == 0) m.remove(key) else m[key] = Levels[lv]
        onChange(m)
    }
    val fill: (String) -> androidx.compose.ui.graphics.Color? = { k -> muscles[k]?.takeIf { it > 0f }?.let { p.heat(it) } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
        BodyFigure(Side.Front, fill, Modifier.height(190.dp), onTap = { k -> set(k, (level(muscles[k]) + 1) % 3) })
        BodyFigure(Side.Back, fill, Modifier.height(190.dp), onTap = { k -> set(k, (level(muscles[k]) + 1) % 3) })
    }
    Column {
        Muscles.all.forEach { k ->
            val lv = level(muscles[k])
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Muscles.label[k] ?: k, Modifier.weight(1f), style = Type.small.copy(fontSize = 14.sp), color = if (lv > 0) p.ink else p.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LevelLabels.forEachIndexed { li, l -> Chip(l, lv == li, { set(k, li) }) }
                }
            }
        }
    }
}
