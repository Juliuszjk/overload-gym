package com.apollo.overload.ui.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollo.overload.data.Repository
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.GhostButton
import com.apollo.overload.ui.common.Gutter
import com.apollo.overload.ui.common.Hairline
import com.apollo.overload.ui.common.InkButton
import com.apollo.overload.ui.common.ScreenHeader
import com.apollo.overload.ui.common.Tag
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val ESTIMATE_NOTE = "Scores are estimates from published hypertrophy research, not exact measurements."

@Composable
fun RankingScreen(repo: Repository) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val ranking by produceState<List<RankMuscle>?>(null) { value = withContext(Dispatchers.IO) { loadRanking(context) } }
    val planIds by remember { planExerciseIds(repo.dao) }.collectAsStateWithLifecycle(emptySet())
    val muscles = ranking ?: return

    var muscleKey by rememberSaveable { mutableStateOf(muscles.first().key) }
    var sortName by rememberSaveable { mutableStateOf<String?>(null) } // null = Overall
    var cmp by rememberSaveable { mutableStateOf(listOf<String>()) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var compareOpen by rememberSaveable { mutableStateOf(false) }

    val muscle = muscles.firstOrNull { it.key == muscleKey } ?: muscles.first()
    val sort = sortName?.let { Axis.valueOf(it) }
    val list = remember(muscle, sort) {
        muscle.exercises.sortedWith(compareByDescending<RankEntry> { it.score(sort) }.thenByDescending { it.overall })
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (cmp.isEmpty()) 24.dp else 96.dp)) {
            item { ScreenHeader("Hypertrophy · research estimates", "Exercise ranking") }
            item {
                Text(ESTIMATE_NOTE, Modifier.padding(horizontal = Gutter).padding(bottom = 12.dp), style = Type.small.copy(fontSize = 11.sp, lineHeight = 16.sp), color = p.mute)
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(muscles, key = { it.key }) { m ->
                        Chip(m.label, m.key == muscle.key) {
                            if (m.key != muscleKey) { muscleKey = m.key; cmp = emptyList() }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(top = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow("Sort by", Modifier.padding(horizontal = Gutter))
                    LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { Chip("Overall", sort == null, small = true) { sortName = null } }
                        items(Axis.entries) { a -> Chip(a.label, sort == a, small = true) { sortName = a.name } }
                    }
                }
            }
            itemsIndexed(list, key = { _, r -> r.id }) { i, r ->
                RankRow(
                    rank = i + 1, entry = r, sort = sort, inPlan = r.id in planIds, selected = r.id in cmp,
                    onClick = { detailId = r.id },
                    onCompare = {
                        cmp = when {
                            r.id in cmp -> cmp - r.id
                            cmp.size >= 2 -> listOf(cmp[1], r.id)
                            else -> cmp + r.id
                        }
                    },
                )
            }
            item {
                Hairline()
                Text(
                    "Scores from 1 to 10 rate stretch under load, how easy it is to add weight over time, how little systemic and joint " +
                        "fatigue it costs, and how directly research supports it for this muscle. Overall weights: stretch 30%, " +
                        "overload 30%, low fatigue 15%, evidence 25%. Tap the compare button on two exercises to compare them, " +
                        "or tap a row for details.",
                    Modifier.padding(horizontal = Gutter, vertical = 14.dp), style = Type.small.copy(fontSize = 11.sp, lineHeight = 16.sp), color = p.mute,
                )
            }
        }

        if (cmp.isNotEmpty()) {
            val a = muscle.exercises.firstOrNull { it.id == cmp[0] }
            val b = cmp.getOrNull(1)?.let { id -> muscle.exercises.firstOrNull { it.id == id } }
            CompareBar(a, b, Modifier.align(Alignment.BottomCenter), onClear = { cmp = emptyList() }, onOpen = { compareOpen = true })
        }
    }

    if (compareOpen && cmp.size == 2) {
        val a = muscle.exercises.firstOrNull { it.id == cmp[0] }
        val b = muscle.exercises.firstOrNull { it.id == cmp[1] }
        if (a != null && b != null) CompareSheet(muscle.label, a, b, onDismiss = { compareOpen = false })
    }
    detailId?.let { id ->
        muscle.exercises.firstOrNull { it.id == id }?.let { e ->
            DetailSheet(repo, muscle.label, e, inPlan = e.id in planIds, onDismiss = { detailId = null })
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(999.dp)
    Text(
        text,
        Modifier.clip(shape).background(if (on) p.ink else Color.Transparent).border(1.dp, if (on) p.ink else p.line2, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 11.dp else 13.dp, vertical = if (small) 6.dp else 8.dp),
        style = Type.small.copy(fontSize = if (small) 12.sp else 13.sp, fontWeight = FontWeight.SemiBold),
        color = if (on) p.onInk else p.ink2,
    )
}

@Composable
private fun RankRow(
    rank: Int, entry: RankEntry, sort: Axis?, inPlan: Boolean, selected: Boolean,
    onClick: () -> Unit, onCompare: () -> Unit,
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().background(if (selected) lerp(p.bg, p.h2, 0.08f) else Color.Transparent)
                .clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 14.dp),
        ) {
            Text(
                "$rank", Modifier.width(34.dp).padding(top = 2.dp),
                style = Type.display(24.sp, 850, 85f, lineHeight = 24.sp), color = if (rank <= 3) p.ink else p.mute,
            )
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Row {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(entry.name, style = Type.body.copy(fontWeight = FontWeight.Bold, lineHeight = 19.sp), color = p.ink)
                        Text(entry.why, Modifier.padding(top = 2.dp), style = Type.small.copy(fontSize = 12.5.sp, lineHeight = 17.sp), color = p.mute)
                        if (inPlan) Tag("In your plan", Modifier.padding(top = 6.dp), fg = p.onInk, bg = p.ink2)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${entry.overall}", style = Type.display(26.sp, 850, lineHeight = 26.sp), color = p.ink)
                        CompareButton(selected, entry.name, onCompare)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Axis.entries.forEach { a -> AxisBar(a.label, entry.score(a), hot = sort == a, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CompareButton(on: Boolean, name: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier.size(30.dp).clip(shape).background(if (on) p.h2 else Color.Transparent)
            .border(1.5.dp, if (on) p.h2 else p.line2, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (on) Icons.Outlined.Check else Icons.Outlined.SwapHoriz, "Compare $name",
            Modifier.size(17.dp), tint = if (on) Color(0xFF1B1305) else p.mute,
        )
    }
}

/** Small labelled 0..10 bar (mockup .ax). [hot] marks the current sort axis in h2. */
@Composable
fun AxisBar(label: String, value: Int, hot: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = Type.eyebrow.copy(fontSize = 9.5.sp, letterSpacing = 0.08.em), color = if (hot) p.h2 else p.mute, maxLines = 1, softWrap = false)
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.line)) {
            Box(Modifier.fillMaxWidth(value / 10f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (hot) p.h2 else p.ink2))
        }
    }
}

@Composable
private fun CompareBar(a: RankEntry?, b: RankEntry?, modifier: Modifier, onClear: () -> Unit, onOpen: () -> Unit) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().background(p.raise)) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val bold = SpanStyle(fontWeight = FontWeight.Bold, color = p.ink)
            Text(
                buildAnnotatedString {
                    if (b == null) {
                        withStyle(bold) { append(a?.name.orEmpty()) }; append(" selected. Pick one more to compare.")
                    } else {
                        withStyle(bold) { append(a?.name.orEmpty()) }; append(" vs "); withStyle(bold) { append(b.name) }
                    }
                },
                Modifier.weight(1f), style = Type.small, color = p.ink2,
            )
            GhostButton(onClear) { Text("Clear", style = Type.small.copy(fontWeight = FontWeight.SemiBold)) }
            if (b != null) InkButton(onOpen, Modifier.heightIn(max = 40.dp)) { Text("Compare", style = Type.small.copy(fontWeight = FontWeight.SemiBold)) }
        }
    }
}

/** Small filled circle for the detail sheet legend. */
@Composable
internal fun Dot(color: Color) = Box(Modifier.size(8.dp).clip(CircleShape).background(color))
