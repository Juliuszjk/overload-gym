package com.apollo.overload.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.apollo.overload.data.Exercise
import com.apollo.overload.data.Muscles
import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.Repository
import com.apollo.overload.data.SetEntry
import com.apollo.overload.data.Workout
import com.apollo.overload.data.logSet
import com.apollo.overload.data.replaceExercise
import com.apollo.overload.domain.ExpectedProgress
import com.apollo.overload.domain.OrderSwitch
import com.apollo.overload.domain.PairOrder
import com.apollo.overload.domain.Progression
import com.apollo.overload.domain.Session
import com.apollo.overload.domain.VsLast
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.Gutter
import com.apollo.overload.ui.common.InkButton
import com.apollo.overload.ui.common.Tag
import com.apollo.overload.ui.common.kg
import com.apollo.overload.ui.common.parseNumber
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import com.apollo.overload.widget.WidgetSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicInteger

/** An open workout: Focus+ (one exercise at a time), "Next up" between exercises, and the summary. */
@Composable
internal fun SessionScreen(repo: Repository, w: Workout, onOpenPlan: () -> Unit) {
    val p = LocalPalette.current
    val dao = repo.dao
    val scope = rememberCoroutineScope()

    // Per-session UI state; survives configuration changes and process death.
    var draftKg by rememberSaveable(stateSaver = LongDoubleMapSaver) { mutableStateOf(emptyMap<Long, Double>()) }
    var draftReps by rememberSaveable(stateSaver = LongIntMapSaver) { mutableStateOf(emptyMap<Long, Int>()) }
    // Logged set count per exercise when the drafts were last checked against the sets.
    var loggedCounts by rememberSaveable(stateSaver = LongIntMapSaver) { mutableStateOf(emptyMap<Long, Int>()) }
    // Kept in the workout row so "Next up" survives leaving the screen and an app kill.
    var justDone by remember { mutableStateOf(w.nextUpFrom) }
    // Follows the workout row too, so a set logged or an exercise started from the widget shows here.
    LaunchedEffect(w.nextUpFrom) { justDone = w.nextUpFrom }
    // Kept in the workout row too, so the widget and an app restart see them; set here at once so taps show right away.
    var setCounts by remember { mutableStateOf(w.setCounts) }
    LaunchedEffect(w.setCounts) { setCounts = w.setCounts }
    var skippedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var skipPrev by rememberSaveable { mutableStateOf<LongArray?>(null) }
    var unOpen by rememberSaveable { mutableStateOf(false) }
    var summaryOpen by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmReps by rememberSaveable(stateSaver = repsCheckSaver) { mutableStateOf<RepsCheck?>(null) }
    var switchPrev by rememberSaveable { mutableStateOf<LongArray?>(null) }
    var howToId by rememberSaveable { mutableStateOf<Long?>(null) }
    var orderOpen by rememberSaveable { mutableStateOf(false) }
    var replaceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var logging by remember { mutableStateOf(false) }
    val writeLock = remember { Mutex() }
    // Bumped by every move away from "Next up", so a set still being saved can't bring it back afterwards.
    val moveGen = remember { AtomicInteger() }

    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val howTos by produceState(emptyMap<String, HowTo>()) { value = withContext(Dispatchers.IO) { HowTos.all(context) } }
    val dayPlan by remember(w.dayId) { dao.dayExercises(w.dayId) }.collectAsStateWithLifecycle(null)
    val setsState by remember(w.id) { dao.sets(w.id) }.collectAsStateWithLifecycle(null)
    val exList by remember { dao.exercises() }.collectAsStateWithLifecycle(null)
    val exercises = remember(exList) { exList?.associateBy { it.id } }
    val types = remember(exercises) { exercises?.mapValues { it.value.type } }
    val sessionExIds = remember(dayPlan, w.order, w.swaps) { dayPlan?.let(w::todayPlan)?.filter { it.id in w.order }?.map { it.exerciseId }?.distinct() }
    val last by produceState<Map<String, List<SetEntry>>?>(null, w.id, sessionExIds) {
        val ids = sessionExIds ?: return@produceState
        value = ids.associateWith { dao.lastSessionSets(it, w.id) }
    }
    // For "Replace": the last finished session of every exercise done before, loaded when the sheet opens.
    val history by produceState<Map<String, List<SetEntry>>?>(null, replaceId != null) {
        if (replaceId == null) return@produceState
        val finished = dao.allWorkouts().filter { it.finishedAt != null }.map { it.id }.toSet()
        val sets = dao.allSetsNow()
        value = withContext(Dispatchers.Default) {
            sets.filter { it.workoutId in finished }.groupBy { it.exerciseId }.mapValues { (_, l) ->
                val lastWorkout = l.maxBy { it.loggedAt }.workoutId
                l.filter { it.workoutId == lastWorkout }.sortedBy { it.setIndex }
            }
        }
    }

    val rawPlan = dayPlan ?: return
    val plan = remember(rawPlan, w.swaps) { w.todayPlan(rawPlan) }
    val sets = setsState ?: return
    val exMap = exercises ?: return
    val lastMap = last ?: return
    LaunchedEffect(sets) { logging = false }
    // A set logged elsewhere (the widget) also ends this screen's draft for that exercise.
    LaunchedEffect(sets) {
        val now = sets.mapNotNull { it.planExerciseId }.groupingBy { it }.eachCount()
        val grown = now.filter { (id, n) -> n > (loggedCounts[id] ?: 0) }.keys
        if (grown.isNotEmpty()) { draftKg = draftKg - grown; draftReps = draftReps - grown }
        loggedCounts = now
    }
    val s = Session(plan, w.order, sets, types.orEmpty(), setCounts)
    val rx = s.plan.associate { it.id to Progression.prescribe(it, lastMap[it.exerciseId].orEmpty()) }

    fun ex(id: Long): Exercise? = s.planRow(id)?.let { exMap[it.exerciseId] }
    /** Also names exercises removed from the plan mid-workout, from their logged sets. */
    fun name(id: Long): String = (s.planRow(id)?.exerciseId ?: s.today(id).firstOrNull()?.exerciseId)?.let { exMap[it]?.name ?: it }.orEmpty()
    fun short(id: Long) = shortName(name(id))
    fun lastSets(id: Long) = s.planRow(id)?.let { lastMap[it.exerciseId] }.orEmpty()
    fun target(id: Long, k: Int): Pair<Double, Int> {
        val today = s.today(id)
        return Progression.setTarget(s.planRow(id)!!, rx.getValue(id), today, k, draftKg[id], if (k == today.size) draftReps[id] else null)
    }
    fun why(id: Long): String {
        val n = PairOrder.pairNumber(s.items, id)
        val partner = PairOrder.partner(s.items, id)
        if (n == 0 || partner == null) return "No pair, stays in its place"
        val type = s.items.first { it.id == id }.type
        return if (s.order.indexOf(id) < s.order.indexOf(partner.id)) "Pair $n: $type first" else "Pair $n: $type after ${short(partner.id)}"
    }
    /** Bundled how-to for this plan row; custom exercises have none. */
    fun howTo(id: Long): HowTo? = s.planRow(id)?.let { pe -> howTos[pe.exerciseId].takeIf { exMap[pe.exerciseId]?.custom != true } }
    fun earnedText(id: Long): String? {
        val pe = s.planRow(id) ?: return null
        val today = s.today(id)
        return if (Progression.earned(pe, today)) "+${kg(Progression.jump(pe, today.maxOf { it.kg }))} kg" else null
    }

    // ---- actions ----
    /**
     * Serialised order change: [change] gets the workout as it is in the DB now and its session, and returns the new
     * order and pull-first flag (null = no change). Quick taps queue up instead of overwriting each other.
     */
    fun save(change: (Workout, Session) -> Pair<List<Long>, Boolean>?) {
        scope.launch {
            writeLock.withLock {
                repo.db.withTransaction {
                    val cur = dao.workout(w.id) ?: return@withTransaction
                    val cs = Session(cur.todayPlan(dao.dayExercisesNow(cur.dayId)), cur.order, sets, types.orEmpty(), cur.setCounts)
                    val (order, pullFirst) = change(cur, cs) ?: return@withTransaction
                    if (order != cur.order || pullFirst != cur.pullFirst) dao.setOrder(w.id, order, pullFirst)
                }
            }
            WidgetSync.update(context)
        }
    }
    /** Leaves "Next up" (also in the DB), and keeps a set still being saved from showing it again. */
    fun leaveNextUp() {
        moveGen.incrementAndGet()
        justDone = null
        scope.launch { writeLock.withLock { dao.setNextUpFrom(w.id, null) }; WidgetSync.update(context) }
    }
    fun clearSkip() { skippedId = null; skipPrev = null }
    fun putCounts(counts: Map<Long, Int>) {
        setCounts = counts
        scope.launch { writeLock.withLock { dao.setSetCounts(w.id, counts) }; WidgetSync.update(context) }
    }
    fun startEx(id: Long) {
        if (s.isDone(id)) return
        save { cur, cs ->
            if (cs.isDone(id)) return@save null
            val r = cs.start(id, cur.pullFirst)
            switchPrev = if (r.switched == OrderSwitch.PullFirst) cur.order.toLongArray() else null
            r.order to r.pullFirst
        }
        leaveNextUp(); unOpen = false; summaryOpen = false; clearSkip()
    }
    /** Logs the dialed set, or exactly [confirmed] after "Log anyway". */
    fun log(id: Long, confirmed: RepsCheck? = null) {
        val pe = s.planRow(id) ?: return
        if (logging) return
        val today = s.today(id)
        val (kgV, reps) = confirmed?.let { it.kg to it.reps } ?: target(id, today.size)
        if (confirmed == null && !ExpectedProgress.plausibleReps(reps, pe.repMax)) { confirmReps = RepsCheck(id, kgV, reps, null); return }
        logging = true
        val finishes = today.size + 1 >= s.target(id)
        val gen = moveGen.get()
        scope.launch {
            // Same transaction as the set, so "Next up" is saved even if the screen goes away right after.
            val nextUp = writeLock.withLock {
                // The row as it is now: a Replace or Undo tapped just before may not have reached this screen yet.
                val row = dao.workout(w.id)?.let { cur -> cur.todayPlan(dao.dayExercisesNow(cur.dayId)).firstOrNull { it.id == id } } ?: pe
                repo.logSet(w.id, row, today.size, kgV, reps, finishes) { moveGen.get() == gen }
            }
            WidgetSync.update(context)
            draftKg = draftKg - id; draftReps = draftReps - id
            if (nextUp && moveGen.get() == gen) justDone = id
        }
        clearSkip(); switchPrev = null
    }
    /** Back to plan order. Right after the switch (nothing logged since) this also undoes the start that caused it. */
    fun undoPullFirst() {
        val prev = switchPrev?.toList()
        save { _, cs -> (prev ?: cs.rebuild(false)) to false }
        switchPrev = null
    }
    /** "Back to plan order" from the order sheet: exercises with logged sets keep their place, the rest follow the plan. */
    fun backToPlanOrder() {
        save { _, cs -> cs.planOrder() to false }
        switchPrev = null
    }
    fun addSet(id: Long) { putCounts(setCounts + (id to s.target(id) + 1)); leaveNextUp(); summaryOpen = false; clearSkip() }
    fun skip(id: Long) {
        if (s.isStarted(id)) putCounts(setCounts + (id to s.today(id).size))
        else {
            skipPrev = w.order.toLongArray()
            save { cur, _ ->
                if (skippedId == id) skipPrev = cur.order.toLongArray()
                (cur.order - id) to cur.pullFirst
            }
        }
        skippedId = id; leaveNextUp(); switchPrev = null
    }
    fun undoSkip() {
        val id = skippedId ?: return
        val prev = skipPrev?.toList()
        if (prev != null) save { cur, _ ->
            if (id in cur.order) null
            else cur.order.toMutableList().apply { add(prev.indexOf(id).coerceIn(0, size), id) } to cur.pullFirst
        }
        else putCounts(setCounts - id)
        clearSkip()
    }
    /** Does plan row [id] as [exerciseId] today only (the planned exercise = back to plan). */
    fun replace(id: Long, exerciseId: String) {
        draftKg = draftKg - id; draftReps = draftReps - id
        scope.launch { writeLock.withLock { repo.replaceExercise(w.id, id, exerciseId) }; WidgetSync.update(context) }
    }
    fun deleteSet(e: SetEntry) = scope.launch {
        repo.db.withTransaction {
            dao.deleteSet(e)
            s.today(e.planExerciseId ?: -1).filter { it.setIndex > e.setIndex }.forEach { dao.updateSet(it.copy(setIndex = it.setIndex - 1)) }
        }
        WidgetSync.update(context)
    }
    fun finish() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                writeLock.withLock {
                    repo.db.withTransaction {
                        val cur = dao.workout(w.id) ?: return@withTransaction
                        // The plan's kg follows the load actually used (plus the jump when earned), on every day of the split.
                        // A replacement's load goes to its own rows in the split; the replaced row keeps its kg.
                        val updates = Progression.planUpdates(cur.todayPlan(dao.splitExercisesNow(cur.dayId)), s.logged)
                            .filter { it.id !in cur.swaps }
                        if (updates.isNotEmpty()) dao.updatePlanExercises(updates)
                        dao.updateWorkout(cur.copy(finishedAt = System.currentTimeMillis()))
                    }
                }
                WidgetSync.update(context)
            } finally {
                busy = false
            }
        }
    }
    fun discard() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                repo.db.withTransaction { sets.forEach { dao.deleteSet(it) }; dao.deleteWorkout(w) }
                WidgetSync.update(context)
            } finally {
                busy = false
            }
        }
    }

    // ---- derived view state ----
    val active = s.active
    val showSummary = summaryOpen || active == null
    val prevDone = justDone?.takeIf { it in s.order && s.isDone(it) && active != null && !showSummary }
    val track = s.order.map { id ->
        TrackSegment(
            id, short(id), s.today(id).size.toFloat() / s.target(id).coerceAtLeast(1), id == active && !showSummary, s.isDone(id),
            if (s.orderChanged) s.moved(id) else 0,
        )
    }
    val orderChip: (@Composable () -> Unit)? = if (s.orderChanged) { { OrderChip { orderOpen = true } } } else null
    val eyebrow = "${dayLabel(w.startedAt)} · ${w.dayName} · ${clock(w.startedAt)}"
    val menu: @Composable () -> Unit = {
        var open by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "More", tint = p.ink) }
            DropdownMenu(open, { open = false }, containerColor = p.raise) {
                if (!showSummary) DropdownMenuItem(text = { Text("Finish workout", style = Type.body) }, onClick = { open = false; summaryOpen = true })
                DropdownMenuItem(text = { Text("Discard workout", style = Type.body) }, onClick = { open = false; confirmDiscard = true })
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().aboveKeyboard()) {
            if (showSummary) {
                Summary(
                    s = s, dayPlan = plan, workout = w, name = ::name, header = {
                        WorkoutTop(eyebrow, onOpenPlan, menu, orderChip)
                        Track(track, ::startEx)
                    },
                    complete = active == null, onBack = { summaryOpen = false }, onEdit = { editId = it.id },
                    onFinish = ::finish, onDiscard = { confirmDiscard = true },
                )
            } else {
                val id = active!!
                val pe = s.planRow(id)!!
                val today = s.today(id)
                val n = s.target(id)
                val i = today.size
                val (curKg, curReps) = target(id, i)
                val queue = s.order.filter { !s.isDone(it) && it != id }
                fun row(q: Long, sub: String) = TimelineRow(q, name(q), load(target(q, s.today(q).size).first), s.moved(q), sub, s.rank(q))

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
                    WorkoutTop(eyebrow, onOpenPlan, menu, orderChip)
                    Track(track, ::startEx)
                    val busyEx = PairOrder.busyExercise(s.items, s.order)
                    if (w.pullFirst && busyEx != null && queue.isNotEmpty()) {
                        Tip(
                            icon = { Icon(Icons.Outlined.Shuffle, null, Modifier.size(18.dp), tint = p.warn) },
                            lead = "${short(busyEx.id)} busy?", rest = "Pull-first order for the rest: each pull now comes before its push.",
                            action = "Undo", onAction = ::undoPullFirst,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    skippedId?.let { sk ->
                        Tip(
                            icon = { Icon(Icons.Outlined.SkipNext, null, Modifier.size(18.dp), tint = p.warn) },
                            lead = "${short(sk)} skipped.", rest = if (skipPrev != null) "It's out of today's order." else "No more sets today.",
                            action = "Undo", onAction = ::undoSkip,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    val planned = rawPlan.firstOrNull { it.id == id }?.exerciseId
                    if (prevDone == null && id in w.swaps && planned != null && !s.isStarted(id)) {
                        Tip(
                            icon = { Icon(Icons.Outlined.SwapHoriz, null, Modifier.size(18.dp), tint = p.warn) },
                            lead = "Instead of ${shortName(exMap[planned]?.name ?: planned)}.", rest = "Today only, your plan stays the same.",
                            action = "Undo", onAction = { replace(id, planned) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (prevDone != null) NextUp(
                        prev = prevDone, id = id, s = s, name = ::name, ex = ::ex, why = ::why, loadKg = curKg,
                        lastSets = lastSets(id), onAddSet = { addSet(prevDone) },
                    )
                    else Hero(
                        id = id, pe = pe, ex = ex(id), name = name(id), s = s, today = today, n = n, i = i,
                        target = ::target, lastSets = lastSets(id),
                        curKg = curKg, curReps = curReps,
                        onHowTo = when {
                            howTo(id) != null -> { { howToId = id } }
                            pe.url.isNotBlank() -> { { runCatching { uri.openUri(pe.url) } } }
                            else -> null
                        },
                        onKg = { v -> draftKg = draftKg + (id to Progression.round2(v.coerceAtLeast(0.0))) },
                        onReps = { v -> draftReps = draftReps + (id to v.coerceAtLeast(1)) },
                        onEditSet = { editId = it.id }, onAddSet = { addSet(id) }, onSkip = { skip(id) },
                        onReplace = if (today.isEmpty() && planned != null && exMap[planned] != null) { { replaceId = id } } else null,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                val nextRow = queue.firstOrNull()?.let { row(it, why(it)) }
                val nextBar: (@Composable () -> Unit)? = if (keyboardOpen()) null else {
                    {
                        val doneIds = s.order.filter { s.isDone(it) }
                        NextBar(
                            next = nextRow,
                            open = unOpen, onToggle = { unOpen = !unOpen },
                            done = doneIds.map { short(it) to earnedText(it) },
                            now = TimelineRow(
                                id, name(id), load(curKg), s.moved(id),
                                "${if (prevDone != null) "Up next" else "Now · set ${i + 1} of $n"} · ${why(id)}", s.rank(id),
                            ),
                            queue = queue.map { row(it, why(it)) },
                            foot = (if (w.pullFirst) "Pull-first order: in every push/pull pair the pull now comes first. " else "") +
                                "↑/↓ = moved against your plan. Tap an exercise to do it now.",
                            onStart = ::startEx,
                        )
                    }
                }
                Dock(next = nextBar) {
                    if (prevDone != null) {
                        InkButton({ startEx(id) }, Modifier.fillMaxWidth().height(54.dp)) {
                            Text("Start ${name(id)}", style = Type.label.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        InkButton({ log(id) }, Modifier.fillMaxWidth().height(54.dp)) {
                            Icon(Icons.Outlined.Check, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Log set ${i + 1} of $n · ${load(curKg)} × $curReps", style = Type.label.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        val howToPe = howToId?.let { s.planRow(it) }
        val howToEntry = howToId?.let { howTo(it) }
        BackHandler(enabled = howToPe != null && howToEntry != null) { howToId = null }
        AnimatedVisibility(
            visible = howToPe != null && howToEntry != null,
            enter = slideInHorizontally(tween(320)) { it }, exit = slideOutHorizontally(tween(320)) { it },
        ) {
            // Keep showing the last page while it slides out; follow a new one if reopened meanwhile.
            var shown by remember { mutableStateOf(howToPe!! to howToEntry!!) }
            if (howToPe != null && howToEntry != null) shown = howToPe to howToEntry
            val (pe, entry) = shown
            HowToPage(
                name = exMap[pe.exerciseId]?.name ?: pe.exerciseId, howTo = entry, ex = exMap[pe.exerciseId],
                note = pe.note, url = pe.url, onBack = { howToId = null }, onVideo = { runCatching { uri.openUri(pe.url) } },
            )
        }
    }

    replaceId?.let { rid ->
        val planned = rawPlan.firstOrNull { it.id == rid }?.let { exMap[it.exerciseId] }
        val current = s.planRow(rid)?.let { exMap[it.exerciseId] }
        val open = planned != null && current != null && !s.isStarted(rid)
        // Closed from outside (e.g. a set logged on the widget): forget it, so it doesn't reopen later.
        LaunchedEffect(open) { if (!open) replaceId = null }
        // Planned and today's exercises both, so no exercise ends up in two rows (one shared load per exercise).
        val exclude = rawPlan.filter { it.id in s.order }.map { it.exerciseId }.toSet() + s.plan.map { it.exerciseId }
        if (open) ReplaceSheet(
            planned = planned!!, current = current!!, exclude = exclude, exercises = exList.orEmpty(),
            history = history, onPick = { replace(rid, it) }, onClose = { replaceId = null },
        )
    }

    LaunchedEffect(s.orderChanged) { if (!s.orderChanged) orderOpen = false }
    if (orderOpen && s.orderChanged) {
        OrderSheet(
            plan = s.plan.map { name(it.id) }, today = s.order.map { name(it) },
            todayMoved = s.order.mapIndexed { k, id -> s.plan.getOrNull(k)?.id != id },
            canRestore = s.planOrder() != s.order,
            onRestore = { backToPlanOrder(); orderOpen = false },
            onDismiss = { orderOpen = false },
        )
    }

    editId?.let { eid -> sets.firstOrNull { it.id == eid } }?.let { e ->
        EditSetSheet(
            title = name(e.planExerciseId ?: -1).ifEmpty { e.exerciseId }, set = e,
            onSave = { k, r ->
                if (!ExpectedProgress.plausibleReps(r, s.planRow(e.planExerciseId ?: -1)?.repMax)) {
                    confirmReps = RepsCheck(e.planExerciseId ?: -1, k, r, e.id)
                } else scope.launch { dao.updateSet(e.copy(kg = k, reps = r)); WidgetSync.update(context) }
                editId = null
            },
            onDelete = { deleteSet(e); editId = null },
            onDismiss = { editId = null },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = p.raise,
            title = { Text("Discard this workout?", style = Type.display(20.sp, 800), color = p.ink) },
            text = {
                Text(
                    if (sets.isEmpty()) "Nothing is logged yet." else "All ${sets.size} logged sets will be deleted. This can't be undone.",
                    style = Type.body, color = p.ink2,
                )
            },
            confirmButton = { TextButton({ confirmDiscard = false; discard() }) { Text("Discard", style = Type.label, color = p.warn) } },
            dismissButton = { TextButton({ confirmDiscard = false }) { Text("Cancel", style = Type.label, color = p.ink) } },
        )
    }
    confirmReps?.let { c ->
        val pe = s.planRow(c.planId)
        val edited = c.setId?.let { sid -> sets.firstOrNull { it.id == sid } }
        fun accept() {
            confirmReps = null
            if (c.setId == null) log(c.planId, confirmed = c)
            else edited?.let { e -> scope.launch { dao.updateSet(e.copy(kg = c.kg, reps = c.reps)); WidgetSync.update(context) } }
        }
        AlertDialog(
            onDismissRequest = { confirmReps = null },
            containerColor = p.raise,
            title = { Text("${c.reps} reps?", style = Type.display(20.sp, 800), color = p.ink) },
            text = {
                Text(
                    "Higher than usual for this exercise${pe?.let { " (range ${it.repMin}–${it.repMax})" } ?: ""}. Check it isn't a typo.",
                    style = Type.body, color = p.ink2,
                )
            },
            confirmButton = { TextButton(::accept) { Text("Log anyway", style = Type.label, color = p.warn) } },
            dismissButton = { TextButton({ confirmReps = null; editId = c.setId }) { Text("Fix", style = Type.label, color = p.ink) } },
        )
    }
}

/** Typed reps that look like a typo, waiting for "Log anyway": plan row, kg, reps and the edited set (null: a new set). */
private class RepsCheck(val planId: Long, val kg: Double, val reps: Int, val setId: Long?)

private val repsCheckSaver = Saver<RepsCheck?, List<Any>>(
    save = { c -> c?.let { listOf(it.planId, it.kg, it.reps, it.setId ?: -1L) } },
    restore = { l -> RepsCheck(l[0] as Long, l[1] as Double, l[2] as Int, (l[3] as Long).takeIf { it >= 0 }) },
)

private fun musclesLine(ex: Exercise?): String =
    ex?.muscles.orEmpty().entries.sortedByDescending { it.value }.take(3).mapNotNull { Muscles.label[it.key] }.joinToString(" · ")

internal fun lastTimeText(last: List<SetEntry>): String = when {
    last.isEmpty() -> "first time"
    last.all { it.kg == last[0].kg } -> "${load(last[0].kg)} × ${last.joinToString(", ") { "${it.reps}" }}"
    else -> last.joinToString(", ") { setText(it.kg, it.reps) }
}

@Composable
private fun RefLine(left: String, leftValue: String, right: String) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            buildAnnotatedString {
                append("$left ")
                withStyle(SpanStyle(color = p.ink2, fontWeight = FontWeight.Bold)) { append(leftValue) }
            },
            Modifier.weight(1f, fill = false), style = Type.small, color = p.mute,
        )
        Text(right, Modifier.padding(start = 10.dp), style = Type.small, color = p.mute)
    }
}

/** The current exercise: meta, name, set ladder, progress pips and the kg / reps dials. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(
    id: Long, pe: PlanExercise, ex: Exercise?, name: String, s: Session, today: List<SetEntry>, n: Int, i: Int,
    target: (Long, Int) -> Pair<Double, Int>, lastSets: List<SetEntry>,
    curKg: Double, curReps: Int, onKg: (Double) -> Unit, onReps: (Int) -> Unit, onHowTo: (() -> Unit)?,
    onEditSet: (SetEntry) -> Unit, onAddSet: () -> Unit, onSkip: () -> Unit, onReplace: (() -> Unit)?,
) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text("Exercise ${s.rank(id)} of ${s.order.size}", style = Type.small, color = p.mute)
                if (s.planPos(id) != s.rank(id)) Tag("plan #${s.planPos(id)}")
                musclesLine(ex).takeIf { it.isNotEmpty() }?.let { Text(it, style = Type.small, color = p.mute) }
                if (pe.note.isNotBlank()) Text(pe.note, style = Type.small, color = p.ink2)
                if (onHowTo != null) Text(
                    "How to", Modifier.clickable(onClick = onHowTo),
                    style = Type.small.copy(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold), color = p.ink2,
                )
            }
            Text(name, style = Type.exerciseName, color = p.ink, modifier = Modifier.padding(top = 6.dp))
        }
        Ladder(n) { k, m ->
            val logged = today.getOrNull(k)
            val lastReps = lastSets.getOrNull(k)?.reps
            if (logged != null) {
                LadderCell(
                    m, "Set ${k + 1}", logged.kg, logged.reps, LadderState.Done,
                    if (logged.reps >= pe.repMax) "Top of range" else "${pe.repMax - logged.reps} below top",
                    onClick = { onEditSet(logged) },
                )
            } else {
                val (tk, tr) = if (k == i) curKg to curReps else target(id, k)
                val sub = when {
                    k == i -> if (lastReps != null) "Now · last $lastReps" else "Now"
                    lastReps != null -> "Last $lastReps"
                    else -> "Target $tr"
                }
                LadderCell(m, "Set ${k + 1}", tk, tr, if (k == i) LadderState.Now else LadderState.Upcoming, sub)
            }
        }
        // Pip line: how many sets reached the top of the range.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(n) { k ->
                    val on = (today.getOrNull(k)?.reps ?: 0) >= pe.repMax
                    Box(Modifier.size(14.dp, 6.dp).clip(RoundedCornerShape(2.dp)).background(if (on) p.good else p.line2))
                }
            }
            Text(
                buildAnnotatedString {
                    append("Range ${pe.repMin}–${pe.repMax}. ${Progression.topCount(pe, today)}/$n sets at ${pe.repMax}. All $n at ${pe.repMax} → ")
                    withStyle(SpanStyle(color = p.ink2, fontWeight = FontWeight.Bold)) { append("+${kg(Progression.jump(pe, today.maxOfOrNull { it.kg } ?: pe.kg))} kg next time") }
                    append(".")
                },
                style = Type.small.copy(fontSize = 12.5.sp, lineHeight = 17.sp), color = p.mute,
            )
        }
        Dials(
            kgText = kg(curKg), repsText = "$curReps",
            kgDelta = VsLast.kg(curKg, lastSets)?.takeIf { it != 0.0 }?.let { d -> (d > 0) to "${kg(abs(d))} kg vs last" },
            repsDelta = VsLast.reps(curReps, lastSets, i)?.takeIf { it != 0 }?.let { d -> (d > 0) to "${abs(d)} vs last" },
            onKgText = { t -> parseNumber(t)?.takeIf { it >= 0 }?.let(onKg) },
            onRepsText = { t -> parseNumber(t)?.roundToInt()?.takeIf { it >= 1 }?.let(onReps) },
            onKgStep = { d -> onKg(curKg + d * pe.increment) },
            onRepsStep = { d -> onReps(curReps + d) },
        )
        RefLine("Last time", lastTimeText(lastSets), "Target ${pe.sets} × ${pe.repMin}–${pe.repMax}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextAction("+ Set", onAddSet)
            TextAction("Skip exercise", onSkip)
            if (onReplace != null) TextAction("Replace", onReplace)
        }
    }
}

internal enum class LadderState { Done, Now, Upcoming }

/** One cell per set; more than four sets scroll sideways. */
@Composable
private fun Ladder(n: Int, cell: @Composable (k: Int, m: Modifier) -> Unit) {
    if (n > 4) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(n) { cell(it, Modifier.width(84.dp)) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(n) { cell(it, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun LadderCell(modifier: Modifier, label: String, kgV: Double, reps: Int, state: LadderState, sub: String, onClick: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    val (bg, border) = when (state) {
        LadderState.Done -> p.sunk to p.sunk
        LadderState.Now -> p.wash(p.h2, 0.09f) to p.h2
        LadderState.Upcoming -> p.bg to p.line
    }
    Column(
        modifier.clip(shape).background(bg).border(1.5.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow(label, color = if (state == LadderState.Done) p.ink2 else p.mute)
            if (state == LadderState.Done) Icon(Icons.Outlined.Check, null, Modifier.size(12.dp), tint = p.ink2)
        }
        Text(
            buildAnnotatedString {
                append(if (kgV == 0.0) "BW" else kg(kgV))
                withStyle(SpanStyle(fontSize = 13.sp, color = p.mute, fontWeight = FontWeight.SemiBold)) { append(" × ") }
                append("$reps")
            },
            style = Type.display(18.sp, 800), color = p.ink, maxLines = 1,
        )
        Text(
            sub, style = Type.small.copy(fontSize = 11.5.sp, fontWeight = if (state == LadderState.Now) FontWeight.Bold else FontWeight.Normal),
            color = when {
                state == LadderState.Now -> p.h2
                state == LadderState.Done && sub == "Top of range" -> p.good
                else -> p.mute
            },
            maxLines = 1,
        )
    }
}

/** Big kg and reps numbers: tap to type, or step with − / +. */
@Composable
private fun Dials(
    kgText: String, repsText: String,
    /** Raised (true) or lowered (false) against last time, with the pill text; null when equal or no history. */
    kgDelta: Pair<Boolean, String>?, repsDelta: Pair<Boolean, String>?,
    onKgText: (String) -> Unit, onRepsText: (String) -> Unit,
    onKgStep: (Int) -> Unit, onRepsStep: (Int) -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().drawBehind {
            drawLine(p.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            drawLine(p.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            drawLine(p.line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
        }
    ) {
        Dial("kg · tap to type", kgText, onKgText, KeyboardType.Decimal, onKgStep, "Less weight", "More weight", kgDelta, Modifier.weight(1f))
        Dial("Reps · tap to type", repsText, onRepsText, KeyboardType.Number, onRepsStep, "Fewer reps", "More reps", repsDelta, Modifier.weight(1f))
    }
}

@Composable
private fun Dial(
    label: String, value: String, onText: (String) -> Unit, keyboard: KeyboardType, onStep: (Int) -> Unit,
    less: String, more: String, delta: Pair<Boolean, String>?, modifier: Modifier,
) {
    val p = LocalPalette.current
    val up = delta?.first
    Column(modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(label)
        NumberInput(
            value, onText, ::parseNumber, keyboard, Type.bigNumber, Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            color = when (up) { true -> p.h2; false -> p.mute; null -> p.ink },
            lineTint = if (up == true) p.h2.copy(alpha = 0.55f) else null,
        )
        // Row kept even when empty, so the dial doesn't jump while typing.
        Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) { if (delta != null) VsLastPill(delta.first, delta.second) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            StepButton(Icons.Outlined.Remove, less) { onStep(-1) }
            StepButton(Icons.Outlined.Add, more) { onStep(1) }
        }
    }
}

/** "↑ +5 kg vs last" (orange) or "↓ −5 kg vs last" (neutral). */
@Composable
private fun VsLastPill(up: Boolean, text: String) {
    val p = LocalPalette.current
    val fg = if (up) p.h2 else p.mute
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(if (up) p.h2.copy(alpha = 0.14f) else p.sunk).padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(if (up) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null, Modifier.size(11.dp), tint = fg)
        Text("${if (up) "+" else "−"}$text", style = Type.display(11.sp, 800, tracking = 0.02f), color = fg, maxLines = 1)
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(46.dp, 36.dp).clip(shape).border(1.dp, p.line2, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(20.dp), tint = p.ink) }
}

/** Between exercises: what was just finished, then the next exercise with its load. No rest timer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NextUp(
    prev: Long, id: Long, s: Session, name: (Long) -> String, ex: (Long) -> Exercise?, why: (Long) -> String,
    loadKg: Double, lastSets: List<SetEntry>, onAddSet: () -> Unit,
) {
    val p = LocalPalette.current
    val pe = s.planRow(id)!!
    val prevPe = s.planRow(prev)!!
    val prevSets = s.today(prev)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.sunk).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = p.good)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = p.ink, fontWeight = FontWeight.SemiBold)) { append(name(prev)) }
                    append(" done · ")
                    append(prevSets.joinToString("  ") { setText(it.kg, it.reps) })
                },
                Modifier.weight(1f).padding(vertical = 3.dp), style = Type.small, color = p.ink2,
            )
            TextAction("+ Set", onAddSet)
        }
        if (Progression.earned(prevPe, prevSets)) {
            val next = Progression.nextKg(prevPe, prevSets)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.wash(p.good, 0.13f)).padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.ArrowUpward, null, Modifier.size(18.dp), tint = p.good)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = p.ink, fontWeight = FontWeight.Bold)) { append("Next time: ${load(next)}") }
                        append(" (+${kg(Progression.round2(next - prevSets.maxOf { it.kg }))} kg). All ${prevSets.size} sets hit ${prevPe.repMax} reps.")
                    },
                    style = Type.small.copy(fontSize = 13.5.sp), color = p.ink2,
                )
            }
        }
        Column {
            Eyebrow("Next up · exercise ${s.rank(id)} of ${s.order.size}", color = p.h2)
            Text(name(id), style = Type.exerciseName, color = p.ink, modifier = Modifier.padding(top = 6.dp))
            FlowRow(
                Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(why(id), style = Type.small, color = p.mute)
                MovedBadge(s.moved(id))
                if (pe.note.isNotBlank()) Text(pe.note, style = Type.small, color = p.ink2)
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 90.dp).drawBehind {
                drawLine(p.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                drawLine(p.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                drawLine(p.line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
            }
        ) {
            val small = SpanStyle(fontSize = 26.sp, color = p.mute, letterSpacing = 0.sp)
            BigFigure("Load", buildAnnotatedString {
                append(if (loadKg == 0.0) "BW" else kg(loadKg))
                if (loadKg != 0.0) withStyle(small) { append("kg") }
            }, Modifier.weight(1f))
            BigFigure("Sets × reps", buildAnnotatedString {
                append("${s.target(id)}")
                withStyle(small) { append(" × ") }
                append("${pe.repMin}–${pe.repMax}")
            }, Modifier.weight(1f).padding(start = 16.dp))
        }
        RefLine("Last time", lastTimeText(lastSets), musclesLine(ex(id)))
    }
}

@Composable
private fun BigFigure(label: String, value: androidx.compose.ui.text.AnnotatedString, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(label)
        Text(value, style = Type.display(60.sp, 800, 80f, -0.03f, 56.sp), color = p.ink, maxLines = 1, softWrap = false)
    }
}
