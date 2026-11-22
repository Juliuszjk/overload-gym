package com.apollo.overload.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollo.overload.data.Exercise
import com.apollo.overload.data.Muscles
import com.apollo.overload.data.SetEntry
import com.apollo.overload.domain.Similar
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.Hairline
import com.apollo.overload.ui.plan.FieldInput
import com.apollo.overload.ui.plan.PlanSheet
import com.apollo.overload.ui.plan.SheetHead
import com.apollo.overload.ui.plan.SmallGhost
import com.apollo.overload.ui.plan.Sub
import com.apollo.overload.ui.plan.TypeTag
import com.apollo.overload.ui.plan.musclesText
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type

private const val OthersLimit = 40

/**
 * "Replace" for today only: exercises like the planned one, those already done before (with their last sets) first,
 * then the rest that work the same main muscle. Search looks through every exercise.
 *
 * @param planned the plan row's exercise; similarity is measured against it, also after a replacement.
 * @param current the exercise the row does today (the planned one, or an earlier replacement).
 * @param exclude exercises already in today's workout.
 * @param history per exercise id: sets of its last finished session; null while loading.
 */
@Composable
internal fun ReplaceSheet(
    planned: Exercise, current: Exercise, exclude: Set<String>, exercises: List<Exercise>,
    history: Map<String, List<SetEntry>>?, onPick: (String) -> Unit, onClose: () -> Unit,
) {
    val p = LocalPalette.current
    var q by remember { mutableStateOf("") }
    val done = history.orEmpty()
    val (mine, others) = remember(exercises, q, done, planned, current, exclude) {
        val query = q.trim().lowercase()
        val pool = exercises.filter { it.id != planned.id && it.id != current.id && it.id !in exclude }
            .filter { e ->
                // Without muscle data (a custom exercise), suggest the same type.
                if (query.isEmpty()) if (planned.muscles.isEmpty()) e.type == planned.type else Similar.fits(planned.muscles, e.muscles)
                else e.name.lowercase().contains(query) || e.muscles.keys.any { (Muscles.label[it] ?: it).lowercase().contains(query) }
            }
            .sortedByDescending { Similar.score(planned.muscles, it.muscles) }
        val (m, o) = pool.partition { it.id in done }
        m to (if (query.isEmpty()) o.take(OthersLimit) else o)
    }

    PlanSheet(onDismiss = onClose, fullHeight = true) { close ->
        SheetHead("Replace ${planned.name} · today only", close)
        FieldInput(q, { q = it }, big = false, placeholder = "Search exercise or muscle")
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            if (current.id != planned.id) item(key = "back") {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Back to plan", style = Type.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold), color = p.ink)
                        Sub(planned.name)
                    }
                    SmallGhost("Plan", { onPick(planned.id); close() }, icon = Icons.AutoMirrored.Outlined.Undo)
                }
            }
            if (mine.isNotEmpty()) {
                item(key = "mine") { Eyebrow("Done before", Modifier.padding(top = 4.dp, bottom = 8.dp), color = p.h2) }
                items(mine, key = { "m-" + it.id }) { e -> Candidate(e, done[e.id]) { onPick(e.id); close() } }
            }
            if (others.isNotEmpty()) {
                item(key = "others") {
                    Eyebrow(if (q.isBlank()) "Also works ${planned.mainMuscle()}" else "Other matches", Modifier.padding(top = 18.dp, bottom = 8.dp))
                }
                items(others, key = { "o-" + it.id }) { e -> Candidate(e, null) { onPick(e.id); close() } }
            }
            if (history != null && mine.isEmpty() && others.isEmpty()) item(key = "none") {
                Sub("No match. Try another word.", Modifier.padding(vertical = 12.dp))
            }
        }
    }
}

private fun Exercise.mainMuscle(): String = muscles.maxByOrNull { it.value }?.key?.let { Muscles.label[it] ?: it }?.lowercase() ?: "the same muscles"

@Composable
private fun Candidate(e: Exercise, last: List<SetEntry>?, onUse: () -> Unit) {
    val p = LocalPalette.current
    Hairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(e.name, style = Type.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold), color = p.ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeTag(e.type)
                Text(e.musclesText(), style = Type.small.copy(fontSize = 12.sp), color = p.mute, maxLines = 2)
            }
            if (!last.isNullOrEmpty()) Text("Last time ${lastTimeText(last)}", style = Type.small.copy(fontSize = 12.sp), color = p.ink2)
        }
        SmallGhost("Use", onUse, Modifier.padding(start = 10.dp))
    }
}
