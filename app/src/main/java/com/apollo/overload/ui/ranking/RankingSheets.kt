package com.apollo.overload.ui.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollo.overload.data.Exercise
import com.apollo.overload.data.Repository
import com.apollo.overload.ui.common.BodyFigure
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.Gutter
import com.apollo.overload.ui.common.SheetShape
import com.apollo.overload.ui.common.Side
import com.apollo.overload.ui.common.Tag
import com.apollo.overload.ui.common.kg
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape, containerColor = p.raise, contentColor = p.ink, tonalElevation = 0.dp,
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(p.line2)) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Gutter).padding(top = 6.dp, bottom = 26.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content,
        )
    }
}

@Composable
private fun SheetHeader(eyebrow: String, onClose: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(eyebrow, Modifier.weight(1f))
        IconButton(onClose, Modifier.size(36.dp)) { Icon(Icons.Outlined.Close, "Close", tint = p.ink2) }
    }
}

private fun verdict(a: RankEntry, b: RankEntry): String {
    val aw = Axis.entries.filter { a.score(it) > b.score(it) }.map { it.label.lowercase() }
    val bw = Axis.entries.filter { b.score(it) > a.score(it) }.map { it.label.lowercase() }
    val lead = if (a.overall == b.overall) "They tie overall." else "${if (a.overall > b.overall) a.name else b.name} ranks higher overall."
    return listOfNotNull(
        lead,
        aw.takeIf { it.isNotEmpty() }?.let { "${a.name} wins on ${it.joinToString(", ")}." },
        bw.takeIf { it.isNotEmpty() }?.let { "${b.name} wins on ${it.joinToString(", ")}." },
        "Doing both in one week covers more of the muscle's range than either alone.",
    ).joinToString(" ")
}

@Composable
fun CompareSheet(muscleLabel: String, a: RankEntry, b: RankEntry, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    RankSheet(onDismiss) {
        SheetHeader("$muscleLabel · compare", onDismiss)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(a.name, style = Type.small.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp), color = p.ink)
                Text("${a.overall}", style = Type.display(44.sp, 850, 85f, lineHeight = 44.sp), color = p.ink)
            }
            Text("VS", Modifier.padding(bottom = 8.dp), style = Type.display(12.sp, 700), color = p.mute)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(b.name, style = Type.small.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp), color = p.ink, textAlign = TextAlign.End)
                Text("${b.overall}", style = Type.display(44.sp, 850, 85f, lineHeight = 44.sp), color = p.ink)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Axis.entries.forEach { ax -> FlyRow(ax.label, a.score(ax), b.score(ax)) }
        }
        Text(verdict(a, b), style = Type.small.copy(fontSize = 14.sp, lineHeight = 20.sp), color = p.ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(a.why, Modifier.weight(1f), style = Type.small.copy(fontSize = 12.5.sp, lineHeight = 17.sp), color = p.mute)
            Text(b.why, Modifier.weight(1f), style = Type.small.copy(fontSize = 12.5.sp, lineHeight = 17.sp), color = p.mute, textAlign = TextAlign.End)
        }
        Text(ESTIMATE_NOTE, style = Type.small.copy(fontSize = 11.sp, lineHeight = 16.sp), color = p.mute)
    }
}

/** Butterfly row: label on top, a's bar grows left from the centre, b's grows right; the higher one is h2. */
@Composable
private fun FlyRow(label: String, a: Int, b: Int) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), Modifier.fillMaxWidth(), style = Type.eyebrow.copy(fontSize = 10.sp), color = p.mute, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$a", Modifier.width(24.dp), style = Type.display(14.sp, 800), color = p.ink)
            FlyBar(a, win = a > b, alignEnd = true, Modifier.weight(1f))
            FlyBar(b, win = b > a, alignEnd = false, Modifier.weight(1f))
            Text("$b", Modifier.width(24.dp), style = Type.display(14.sp, 800), color = p.ink, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun FlyBar(value: Int, win: Boolean, alignEnd: Boolean, modifier: Modifier) {
    val p = LocalPalette.current
    Box(modifier.height(10.dp).clip(RoundedCornerShape(3.dp)).background(p.sunk), contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth(value / 10f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(if (win) p.h2 else p.line2))
    }
}

@Composable
fun DetailSheet(repo: Repository, muscleLabel: String, e: RankEntry, inPlan: Boolean, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val exercise by produceState<Exercise?>(null, e.id) { value = repo.dao.exercise(e.id) }
    val sets by remember { repo.dao.allSets() }.collectAsStateWithLifecycle(emptyList())
    val best = remember(sets, e.id) {
        sets.filter { it.exerciseId == e.id }.maxWithOrNull(compareBy({ it.kg }, { it.reps }))
    }
    RankSheet(onDismiss) {
        SheetHeader("$muscleLabel · exercise", onDismiss)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(e.name, style = Type.display(26.sp, 800, 100f, -0.02f, 28.sp), color = p.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag("Overall ${e.overall}")
                if (inPlan) Tag("In your plan", fg = p.onInk, bg = p.ink2)
            }
        }
        val muscles = exercise?.muscles.orEmpty()
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BodyFigure(Side.Front, fill = { k -> muscles[k]?.let { p.heat(it) } }, modifier = Modifier.weight(1f))
            BodyFigure(Side.Back, fill = { k -> muscles[k]?.let { p.heat(it) } }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(p.heat(1f), "Primary")
            Legend(p.heat(0.4f), "Secondary")
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Axis.entries.forEach { ax -> ScoreLine(ax.label, e.score(ax)) }
        }
        Text(e.why, style = Type.small.copy(fontSize = 14.sp, lineHeight = 20.sp), color = p.ink2)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow("Your best set")
            if (best == null) Text("Not logged yet.", style = Type.small, color = p.mute)
            else Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${kg(best.kg)} kg × ${best.reps}", style = Type.display(22.sp, 800), color = p.ink)
                Text(
                    Instant.ofEpochMilli(best.loggedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)),
                    Modifier.padding(bottom = 3.dp), style = Type.small, color = p.mute,
                )
            }
        }
        Text(ESTIMATE_NOTE, style = Type.small.copy(fontSize = 11.sp, lineHeight = 16.sp), color = p.mute)
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Dot(color)
        Text(label, style = Type.small.copy(fontSize = 12.sp), color = p.mute)
    }
}

/** Axis label, 0..10 bar and the number. */
@Composable
private fun ScoreLine(label: String, value: Int) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label.uppercase(), Modifier.width(92.dp), style = Type.eyebrow.copy(fontSize = 10.sp, letterSpacing = 0.1.em), color = p.mute)
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.line)) {
            Box(Modifier.fillMaxWidth(value / 10f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(p.ink2))
        }
        Text("$value", Modifier.width(22.dp), style = Type.display(14.sp, 800), color = p.ink, textAlign = TextAlign.End)
    }
}
