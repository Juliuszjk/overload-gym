package com.apollo.overload.ui.plan

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.apollo.overload.data.Exercise
import com.apollo.overload.data.PlanDay
import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.Repository
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.Gutter
import com.apollo.overload.ui.common.kg
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun DayEditor(repo: Repository, dayId: Long, exercises: List<Exercise>, exMap: Map<String, Exercise>, onBack: () -> Unit) {
    val dao = repo.dao
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var day by remember(dayId) { mutableStateOf<PlanDay?>(null) }
    LaunchedEffect(dayId) { day = dao.day(dayId) ?: run { onBack(); null } }
    val split by remember { dao.activeSplit() }.collectAsStateWithLifecycle(null)
    val dbRows by remember(dayId) { dao.dayExercises(dayId) }.collectAsStateWithLifecycle(emptyList())
    // Order written on drop, shown until the DB emits it, so the list never flickers back.
    var localRows by remember { mutableStateOf<List<PlanExercise>?>(null) }
    LaunchedEffect(dbRows) { localRows = null }
    val rows = localRows ?: dbRows

    var editId by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    var dayOptions by remember { mutableStateOf(false) }

    val d = day ?: return
    val sets = rows.sumOf { it.sets }

    Column(Modifier.fillMaxSize().background(p.bg).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        AppBar("${split?.name ?: "Split"} · Plan", onBack) {
            IconBtn(Icons.Outlined.MoreHoriz, "Day options", { dayOptions = true })
        }
        Column(Modifier.padding(horizontal = Gutter).padding(top = 16.dp)) {
            Eyebrow("${KindLabel[d.kind] ?: d.kind} day")
            Text(d.name, Modifier.padding(top = 4.dp), style = Type.title, color = p.ink)
            Sub(
                if (rows.isEmpty()) "No exercises yet" else "${rows.size} exercises · $sets sets · about ${dayMinutes(sets)} min",
                Modifier.padding(top = 6.dp),
            )
            Sub(
                "Drag ≡ to reorder. A bracket joins a push/pull pair: when you go off plan, the pairs swap together. Tap an exercise to edit it.",
                Modifier.padding(top = 14.dp, bottom = 16.dp),
            )
        }
        PlanList(
            rows, exMap,
            onEdit = { editId = it.id },
            onMove = { from, to ->
                val list = rows.toMutableList()
                list.add(to, list.removeAt(from))
                val norm = normalizePlan(list)
                localRows = norm
                scope.launch { dao.updatePlanExercises(norm) }
            },
        )
        AddRow("Add exercise", Icons.Outlined.Add, { picking = true }, Modifier.padding(start = Gutter + 14.dp, end = Gutter, top = 12.dp))
        Foot(
            "Weights are the total load. Changes apply right away, also to a workout in progress.",
            Modifier.padding(horizontal = Gutter).padding(top = 20.dp),
        )
    }

    if (picking) {
        PickerSheet(
            d, rows, exercises,
            onAdd = { e ->
                scope.launch {
                    val id = repo.db.withTransaction {
                        // The split shares one load per exercise: take it from another day, else from last session's top set.
                        val kgV = dao.splitExercisesNow(d.id).firstOrNull { it.exerciseId == e.id }?.kg
                            ?: dao.lastSessionSets(e.id, -1).maxOfOrNull { it.kg } ?: 0.0
                        val id = dao.insertPlanExercise(PlanExercise(dayId = d.id, exerciseId = e.id, position = rows.size, kg = kgV, incrementPct = PlanExercise.defaultPct(e.type)))
                        // A workout of this day in progress takes the new exercise too, at the end like in the plan.
                        dao.syncOpenWorkout(d.id, id, add = true)
                        id
                    }
                    // Open the edit sheet once the picker has slid away, so the user sets kg right after adding.
                    snapshotFlow { picking }.first { !it }
                    editId = id
                }
            },
            onCreate = { e -> scope.launch { dao.upsertExercise(e) } },
            onClose = { picking = false },
        )
    }
    editId?.let { id ->
        EditSheet(repo, scope, d, rows, id, exMap, onClose = { editId = null })
    }
    if (dayOptions) {
        DayOptionsSheet(repo, scope, d, onSaved = { day = it }, onDeleted = onBack, onClose = { dayOptions = false })
    }
}

/** Ordered plan rows with grip drag-reorder, pair brackets and "Pair N" labels. */
@Composable
private fun PlanList(rows: List<PlanExercise>, exMap: Map<String, Exercise>, onEdit: (PlanExercise) -> Unit, onMove: (Int, Int) -> Unit) {
    val p = LocalPalette.current
    if (rows.isEmpty()) return
    val gapPx = with(LocalDensity.current) { 6.dp.toPx() }
    val heights = remember { mutableStateMapOf<Long, Int>() }
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val currentRows by rememberUpdatedState(rows)
    val currentMove by rememberUpdatedState(onMove)

    fun target(list: List<PlanExercise>, from: Int, dy: Float): Int {
        var t = from
        var acc = 0f
        if (dy > 0) for (j in from + 1 until list.size) {
            val h = (heights[list[j].id] ?: 0) + gapPx
            if (dy > acc + h / 2) { t = j; acc += h } else break
        } else for (j in from - 1 downTo 0) {
            val h = (heights[list[j].id] ?: 0) + gapPx
            if (-dy > acc + h / 2) { t = j; acc += h } else break
        }
        return t
    }

    fun finish() {
        val list = currentRows
        val from = list.indexOfFirst { it.id == dragId }
        if (from >= 0) {
            val to = target(list, from, dragDy)
            if (to != from) currentMove(from, to)
        }
        dragId = null
        dragDy = 0f
    }

    val from = rows.indexOfFirst { it.id == dragId }
    val to = if (from >= 0) target(rows, from, dragDy) else -1
    val lift = if (from >= 0) (heights[rows[from].id] ?: 0) + gapPx else 0f
    val pairNo = pairNumbers(rows)

    Column(Modifier.padding(start = Gutter + 14.dp, end = Gutter), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { i, x ->
            key(x.id) {
                val dragging = i == from
                val shift = when {
                    from < 0 || dragging -> 0f
                    i in (from + 1)..to -> -lift
                    i in to until from -> lift
                    else -> 0f
                }
                val anim by animateFloatAsState(shift, if (from >= 0) tween(180) else snap(), label = "shift")
                val pa = x.pairId != null && rows.getOrNull(i + 1)?.pairId == x.pairId
                val pb = x.pairId != null && rows.getOrNull(i - 1)?.pairId == x.pairId
                PlanRow(
                    x, i, exMap[x.exerciseId], pairNo[x.id], pa, pb, dragging,
                    modifier = Modifier.onSizeChanged { heights[x.id] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragDy else anim },
                    grip = Modifier.pointerInput(x.id) {
                        detectDragGestures(
                            onDragStart = { dragId = x.id; dragDy = 0f },
                            onDragEnd = { finish() },
                            onDragCancel = { finish() },
                        ) { change, amount ->
                            change.consume()
                            dragDy += amount.y
                        }
                    },
                    onClick = { onEdit(x) },
                )
            }
        }
    }
}

@Composable
private fun PlanRow(
    x: PlanExercise, index: Int, e: Exercise?, pairNo: Int?, pa: Boolean, pb: Boolean, dragging: Boolean,
    modifier: Modifier, grip: Modifier, onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.fillMaxWidth()
            .drawBehind {
                // Pair bracket in the left gutter (mockup .pl-row.pa / .pb::before).
                val sw = 2.dp.toPx(); val left = -11.dp.toPx(); val right = -4.dp.toPx(); val r = 7.dp.toPx()
                val mid = size.height / 2; val gap = 3.dp.toPx()
                val path = Path()
                if (pa) {
                    path.moveTo(right, mid); path.lineTo(left + r, mid)
                    path.quadraticTo(left, mid, left, mid + r); path.lineTo(left, size.height + gap)
                }
                if (pb) {
                    path.moveTo(left, -gap); path.lineTo(left, mid - r)
                    path.quadraticTo(left, mid, left + r, mid); path.lineTo(right, mid)
                }
                if (pa || pb) drawPath(path, p.ink2, style = Stroke(sw))
            }
            .then(if (dragging) Modifier.shadow(16.dp, shape) else Modifier)
            .background(p.raise, shape)
            .then(if (dragging) Modifier.border(1.5.dp, p.h2, shape) else Modifier)
            .drawBehind { if (!dragging) drawLine(p.line, Offset(14.dp.toPx(), size.height), Offset(size.width - 14.dp.toPx(), size.height), 1.dp.toPx()) }
            .clickable(onClick = onClick)
            .padding(end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(grip.size(width = 36.dp, height = 44.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.DragHandle, "Drag to reorder", tint = p.mute, modifier = Modifier.size(18.dp))
        }
        Text("${index + 1}", Modifier.width(20.dp), style = Type.display(14.sp, 800), color = p.mute)
        Column(Modifier.weight(1f).padding(start = 6.dp, top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                e?.name ?: x.exerciseId, style = Type.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${kg(x.kg)} kg · ${x.sets} × ${x.repMin}–${x.repMax}", style = Type.display(12.5.sp, 600), color = p.ink2, maxLines = 1)
                if (e != null) TypeTag(e.type)
                if (pairNo != null) Text("Pair $pairNo", style = Type.display(10.sp, 700), color = p.ink2, maxLines = 1)
            }
            if (x.note.isNotBlank()) Text(x.note, style = Type.small.copy(fontSize = 12.sp), color = p.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = p.mute, modifier = Modifier.size(20.dp))
    }
}

/** Rename, change kind, move within the rotation, or delete a day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayOptionsSheet(repo: Repository, scope: CoroutineScope, day: PlanDay, onSaved: (PlanDay) -> Unit, onDeleted: () -> Unit, onClose: () -> Unit) {
    val dao = repo.dao
    val siblings by remember(day.splitId) { dao.days(day.splitId) }.collectAsStateWithLifecycle(emptyList())
    var name by remember { mutableStateOf(day.name) }
    var kind by remember { mutableStateOf(day.kind) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val index = siblings.indexOfFirst { it.id == day.id }

    fun save() {
        if (deleted) return
        val updated = day.copy(name = name.trim().ifEmpty { day.name }, kind = kind)
        if (updated != day) {
            onSaved(updated)
            scope.launch { dao.updateDay(updated) }
        }
    }

    fun move(by: Int) {
        val list = siblings.toMutableList()
        val i = list.indexOfFirst { it.id == day.id }
        val j = i + by
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        scope.launch { repo.db.withTransaction { list.forEachIndexed { k, s -> if (s.position != k) dao.updateDay(s.copy(position = k)) } } }
    }

    PlanSheet(onDismiss = { save(); onClose() }) { close ->
        SheetHead("Day ${index + 1} of ${siblings.size}", close)
        Field("Name") { FieldInput(name, { name = it }, big = false) }
        Field("Kind · drives suggestions") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Kinds.forEach { k -> Chip(KindLabel[k] ?: k, kind == k, { kind = k }) }
            }
        }
        Field("Order in rotation") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallGhost("Earlier", { move(-1) }, icon = Icons.Outlined.KeyboardArrowUp, enabled = index > 0)
                SmallGhost("Later", { move(1) }, icon = Icons.Outlined.KeyboardArrowDown, enabled = index in 0 until siblings.size - 1)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SmallGhost("Delete day", { confirmDelete = true }, icon = Icons.Outlined.Delete)
            SmallInk("Done", close)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "Delete ${day.name}?", "Its exercises are removed from the plan. Past workouts stay in your history.", "Delete",
            onConfirm = {
                deleted = true
                scope.launch {
                    val done = repo.db.withTransaction {
                        if (dao.openWorkoutNow()?.dayId == day.id) return@withTransaction false
                        deleteDayDeep(dao, day)
                        siblings.filter { it.id != day.id }.forEachIndexed { k, s -> if (s.position != k) dao.updateDay(s.copy(position = k)) }
                        true
                    }
                    if (!done) {
                        deleted = false
                        Toast.makeText(context, WorkoutInProgressMsg, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    onClose()
                    onDeleted()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
