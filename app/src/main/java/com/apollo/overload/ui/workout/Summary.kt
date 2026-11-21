package com.apollo.overload.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.SetEntry
import com.apollo.overload.data.Workout
import com.apollo.overload.domain.Progression
import com.apollo.overload.domain.Session
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.GhostButton
import com.apollo.overload.ui.common.Gutter
import com.apollo.overload.ui.common.Hairline
import com.apollo.overload.ui.common.InkButton
import com.apollo.overload.ui.common.SheetShape
import com.apollo.overload.ui.common.kg
import com.apollo.overload.ui.common.parseNumber
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private fun duration(ms: Long): String {
    val min = (ms / 60_000).coerceAtLeast(0)
    return if (min < 60) "$min min" else "${min / 60} h ${"%02d".format(min % 60)} min"
}

private fun volume(sets: Collection<SetEntry>): String =
    String.format(Locale.ENGLISH, "%,d", sets.sumOf { it.kg * it.reps }.roundToLong()).replace(',', ' ') + " kg"

/** Session summary with per-exercise results; "Finish workout" closes the session and writes the loads used (plus earned increments) into the plan. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.Summary(
    s: Session, dayPlan: List<PlanExercise>, workout: Workout, name: (Long) -> String,
    header: @Composable () -> Unit, complete: Boolean,
    onBack: () -> Unit, onEdit: (SetEntry) -> Unit, onFinish: () -> Unit, onDiscard: () -> Unit,
) {
    val p = LocalPalette.current
    val now = remember { System.currentTimeMillis() }
    val all = s.logged.values.flatten()
    val skipped = dayPlan.filter { it.id !in s.order && s.today(it.id).isEmpty() }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
        header()
        Eyebrow(workout.dayName)
        Text(
            if (complete) "Workout complete" else "Summary", style = Type.display(36.sp, 800, 100f, -0.025f, 34.sp), color = p.ink,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )
        if (!complete) TextAction("Back to workout", onBack)
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp).drawBehind {
                drawLine(p.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                drawLine(p.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
        ) {
            Stat("Duration", duration(now - workout.startedAt), Modifier.weight(1f))
            Stat("Sets", "${all.size}", Modifier.weight(1f))
            Stat("Volume", volume(all), Modifier.weight(1.3f))
        }
        Spacer(Modifier.height(4.dp))
        // Session exercises, then any with sets logged here that were removed from the plan since.
        (s.order + s.extra).forEachIndexed { i, id ->
            val pe = s.planRow(id)
            val today = s.today(id)
            if (i > 0) Hairline()
            Column(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name(id), Modifier.weight(1f), style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = p.ink)
                    if (pe != null && Progression.earned(pe, today)) PlusBadge("Next time +${kg(Progression.jump(pe, today.maxOf { it.kg }))} kg")
                }
                if (today.isEmpty()) Text("Not started", style = Type.small, color = p.mute)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    today.forEach { e ->
                        Text(
                            setText(e.kg, e.reps), Modifier.clickable { onEdit(e) }.padding(horizontal = 4.dp, vertical = 4.dp),
                            style = Type.display(13.sp, 600), color = p.mute,
                        )
                    }
                }
            }
        }
        skipped.forEach { pe ->
            Hairline()
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name(pe.id).ifEmpty { pe.exerciseId }, Modifier.weight(1f), style = Type.body, color = p.mute)
                Text("Skipped", style = Type.small, color = p.mute)
            }
        }
        if (all.isNotEmpty()) Text(
            "Tap a set to change or delete it.", Modifier.padding(top = 8.dp, bottom = 16.dp),
            style = Type.small.copy(fontSize = 12.sp), color = p.mute,
        )
    }
    Dock {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton(onDiscard, Modifier.height(54.dp)) { Text("Discard", style = Type.label.copy(fontSize = 15.sp)) }
            InkButton(onFinish, Modifier.weight(1f).height(54.dp), enabled = all.isNotEmpty()) {
                Text("Finish workout", style = Type.label.copy(fontSize = 16.sp))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(label)
        Text(value, style = Type.display(24.sp, 800, 90f), color = p.ink, maxLines = 1)
    }
}

/** Bottom sheet to change or delete a logged set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditSetSheet(title: String, set: SetEntry, onSave: (Double, Int) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var kgV by remember(set.id) { mutableStateOf(set.kg) }
    var reps by remember(set.id) { mutableStateOf(set.reps) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape, containerColor = p.raise,
        dragHandle = { BottomSheetDefaults.DragHandle(color = p.line2) },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Eyebrow(title)
                Text("Set ${set.setIndex + 1}", style = Type.display(24.sp, 800), color = p.ink, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Eyebrow("kg")
                    NumberInput(
                        kg(kgV), { t -> parseNumber(t)?.takeIf { it >= 0 }?.let { kgV = Progression.round2(it) } }, ::parseNumber,
                        KeyboardType.Decimal, Type.display(48.sp, 850, 80f), Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Eyebrow("Reps")
                    NumberInput(
                        "$reps", { t -> parseNumber(t)?.roundToInt()?.takeIf { it >= 1 }?.let { reps = it } }, ::parseNumber,
                        KeyboardType.Number, Type.display(48.sp, 850, 80f), Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostButton(onDelete, Modifier.height(52.dp)) {
                    Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete", style = Type.label.copy(fontSize = 15.sp))
                }
                InkButton({ onSave(kgV, reps) }, Modifier.weight(1f)) { Text("Save", style = Type.label.copy(fontSize = 16.sp)) }
            }
        }
    }
}
