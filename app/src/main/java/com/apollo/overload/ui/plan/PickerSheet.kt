package com.apollo.overload.ui.plan

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollo.overload.data.Exercise
import com.apollo.overload.data.Muscles
import com.apollo.overload.data.PlanDay
import com.apollo.overload.data.PlanExercise
import com.apollo.overload.ui.common.Hairline
import com.apollo.overload.ui.common.InkButton
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type

private val EquipmentRank = mapOf("machine" to 0, "cable" to 1, "smith" to 2, "barbell" to 3, "dumbbell" to 4)
private val EquipmentChoices = listOf("machine", "cable", "smith", "barbell", "dumbbell", "bodyweight", "kettlebell", "band", "other")
private const val SuggestedLimit = 60

/** Lets a child ignore the sheet's 20dp side padding (mockup `margin-inline:-20px`). */
private fun Modifier.bleed(): Modifier = layout { m, c ->
    val extra = 40.dp.roundToPx()
    val pl = m.measure(c.copy(maxWidth = c.maxWidth + extra, minWidth = c.minWidth + extra))
    layout(pl.width - extra, pl.height) { pl.place(-extra / 2, 0) }
}

/** "Add exercise" sheet: search, filters, the exercise DB with muscle tags, and custom exercise creation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickerSheet(
    day: PlanDay, rows: List<PlanExercise>, exercises: List<Exercise>,
    onAdd: (Exercise) -> Unit, onCreate: (Exercise) -> Unit, onClose: () -> Unit,
) {
    val p = LocalPalette.current
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("suggested") }
    var creating by remember { mutableStateOf(false) }
    val have = remember(rows) { rows.map { it.exerciseId }.toSet() }
    val kindLabel = KindLabel[day.kind] ?: day.kind

    val list = remember(exercises, q, filter, have, day.kind) {
        val types = when (filter) { "suggested" -> suggestedTypes(day.kind); "all" -> ExerciseTypes; else -> listOf(filter) }
        val query = q.trim().lowercase()
        var l = exercises.filter { e ->
            e.type in types && (query.isEmpty() || e.name.lowercase().contains(query) ||
                e.muscles.keys.any { (Muscles.label[it] ?: it).lowercase().contains(query) })
        }
        if (filter == "suggested") {
            l = l.sortedWith(compareBy<Exercise> { EquipmentRank[it.equipment] ?: 9 }.thenBy { it.name })
            if (query.isEmpty()) l = l.take(SuggestedLimit)
        }
        l.sortedBy { it.id in have }
    }

    PlanSheet(onDismiss = onClose, fullHeight = true) { close ->
        if (creating) {
            CreateExercise(day, onCancel = { creating = false }, onSave = { e -> onCreate(e); onAdd(e); close() })
            return@PlanSheet
        }
        SheetHead("Add to ${day.name}", close)
        FieldInput(q, { q = it }, big = false, placeholder = "Search exercise or muscle")
        Row(
            Modifier.bleed().fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("suggested" to "Suggested for $kindLabel", "all" to "All", "push" to "Push", "pull" to "Pull", "legs" to "Legs", "core" to "Core")
                .forEach { (k, l) -> Chip(l, filter == k, { filter = k }) }
        }
        LazyColumn(Modifier.bleed().fillMaxWidth().weight(1f)) {
            item {
                AddRow("Create custom exercise", Icons.Outlined.Add, { creating = true }, Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp))
            }
            if (list.isEmpty()) item { Sub("No match. Try another word or filter.", Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
            items(list, key = { it.id }) { e ->
                Hairline()
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(e.name, style = Type.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold), color = p.ink)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TypeTag(e.type)
                            Text(e.musclesText(), style = Type.small.copy(fontSize = 12.sp), color = p.mute, maxLines = 2)
                        }
                    }
                    if (e.id in have) Sub("Added", Modifier.padding(start = 10.dp))
                    else SmallGhost("Add", { onAdd(e); close() }, Modifier.padding(start = 10.dp), icon = Icons.Outlined.Add)
                }
            }
        }
    }
}

/** Name, type, equipment and muscles of a new custom exercise. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateExercise(day: PlanDay, onCancel: () -> Unit, onSave: (Exercise) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(suggestedTypes(day.kind).first()) }
    var equipment by remember { mutableStateOf("machine") }
    var muscles by remember { mutableStateOf(emptyMap<String, Float>()) }
    SheetHead("New exercise · ${day.name}", onCancel)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Field("Name") { FieldInput(name, { name = it }, big = false, placeholder = "e.g. Hack squat") }
        Field("Type") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ExerciseTypes.forEach { t -> Chip(t.replaceFirstChar { it.uppercase() }, type == t, { type = t }) }
            }
        }
        Field("Equipment") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EquipmentChoices.forEach { q -> Chip(q.replaceFirstChar { it.uppercase() }, equipment == q, { equipment = q }) }
            }
        }
        Field("Muscles") { MuscleEditor(muscles, { muscles = it }) }
        InkButton({
            val slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "exercise" }
            val id = "custom-$slug-${System.currentTimeMillis().toString(36)}"
            onSave(Exercise(id = id, name = name.trim(), type = type, equipment = equipment, muscles = muscles, custom = true))
        }, Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
            Text("Save and add", style = Type.label)
        }
    }
}
