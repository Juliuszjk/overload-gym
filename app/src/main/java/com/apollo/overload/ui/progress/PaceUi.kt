package com.apollo.overload.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollo.overload.domain.ExpectedProgress
import com.apollo.overload.domain.PaceStatus
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.Gutter
import com.apollo.overload.ui.common.Hairline
import com.apollo.overload.ui.common.InkButton
import com.apollo.overload.ui.common.SheetShape
import com.apollo.overload.ui.common.fmt1
import com.apollo.overload.ui.common.kg
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import com.apollo.overload.ui.theme.archivo
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** Ahead = good green, behind = warn amber, on track = plain ink. */
@Composable
fun statusColor(s: PaceStatus): Color {
    val p = LocalPalette.current
    return when (s) { PaceStatus.Ahead -> p.good; PaceStatus.Behind -> p.warn; PaceStatus.OnTrack -> p.ink }
}

private fun paceText(perWeek: Double) = (if (perWeek >= 0) "+" else "−") + "%.1f".format(Locale.ENGLISH, abs(perWeek)) + " kg/week"
private val sinceFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val wdFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private fun sessions(n: Int) = "$n session${if (n == 1) "" else "s"}"
fun loggedSince(): String = sinceFmt.format(ExpectedProgress.LOGGED_FROM)

private fun nextText(pace: LiftPace): String? {
    val n = pace.next ?: return null
    return when (n.sessions) {
        null -> "reps flat at ${kg(n.kg)} kg for ${sessions(n.atWeight)}"
        1 -> "next +${kg(n.increment)} kg next session"
        else -> "next +${kg(n.increment)} kg in ~${n.sessions} sessions"
    }
}

private fun nextShort(pace: LiftPace): String? {
    val n = pace.next ?: return null
    return when {
        n.sessions == null -> "reps flat ${sessions(n.atWeight)}"
        pace.nextDate != null -> "next +${kg(n.increment)} kg ~${sinceFmt.format(pace.nextDate)}"
        else -> "next +${kg(n.increment)} kg in ~${n.sessions}"
    }
}

/** Filled dot for ahead / behind, a ring for on track ([onInk] when drawn on a selected chip). */
@Composable
fun StatusDot(s: PaceStatus, size: Int = 7, onInk: Boolean = false) {
    val p = LocalPalette.current
    val m = Modifier.size(size.dp).clip(CircleShape)
    if (s == PaceStatus.OnTrack) Box(m.border(1.5.dp, if (onInk) p.onInk else p.ink2, CircleShape))
    else Box(m.background(statusColor(s)))
}

@Composable
private fun Pill(s: PaceStatus) {
    val p = LocalPalette.current
    val (fg, bg) = when (s) { PaceStatus.Ahead -> p.good to p.goodBg; PaceStatus.Behind -> p.warn to p.warnBg; PaceStatus.OnTrack -> p.ink2 to p.sunk }
    Text(
        s.label, Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 5.dp),
        style = Type.display(11.sp, 700, lineHeight = 11.sp, tracking = 0.04f), color = fg, maxLines = 1,
    )
}

/** "Behind · +1.3 kg/week · next +2.5 kg in ~2 sessions" above the chart; tap opens the explanation. */
@Composable
fun PaceLine(pace: LiftPace, onClick: () -> Unit) {
    val p = LocalPalette.current
    val m = pace.model ?: return
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(m.status, 8)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = statusColor(m.status), fontWeight = FontWeight.Bold)) { append(m.status.label) }
                append(" · ${paceText(m.pacePerWeek)}")
                nextText(pace)?.let { append(" · $it") }
            },
            Modifier.weight(1f), style = Type.small.copy(fontSize = 13.5.sp), color = p.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Outlined.Info, "How it is worked out", Modifier.size(16.dp), tint = p.mute)
    }
}

/**
 * Day summary at the top of Progress: "2 ahead, 4 on track, 1 behind (Shoulder press)" and one bar per lift.
 * Tapping a bar picks that lift; tapping elsewhere opens the day sheet.
 */
@Composable
fun DaySummary(day: String, lifts: List<Pair<Lift, PaceStatus?>>, selected: String?, onLift: (String) -> Unit, onOpen: () -> Unit) {
    val p = LocalPalette.current
    val count = PaceStatus.entries.associateWith { s -> lifts.count { it.second == s } }
    val behind = lifts.filter { it.second == PaceStatus.Behind }.map { it.first.name }
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow("$day · pace since ${loggedSince()}")
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = p.ink, fontWeight = FontWeight.SemiBold)) {
                    append("${count[PaceStatus.Ahead]} ahead, ${count[PaceStatus.OnTrack]} on track")
                }
                if (behind.isNotEmpty()) {
                    append(", ")
                    withStyle(SpanStyle(color = p.warn, fontWeight = FontWeight.SemiBold)) { append("${behind.size} behind") }
                    append(" (${behind.joinToString(", ")})")
                }
            },
            style = Type.body.copy(fontSize = 14.5.sp), color = p.ink2,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            lifts.forEach { (l, s) ->
                val c = when (s) { PaceStatus.Ahead -> p.good; PaceStatus.Behind -> p.warn; else -> p.ink.copy(alpha = 0.22f) }
                val on = l.id == selected
                Box(Modifier.weight(1f).height(18.dp).clickable { onLift(l.id) }, contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxWidth().height(6.dp).drawBehind {
                            drawRoundRect(c, cornerRadius = CornerRadius(2.dp.toPx()))
                            if (on) {
                                val o = 2.dp.toPx() + 0.75.dp.toPx()
                                drawRoundRect(
                                    p.ink, Offset(-o, -o), Size(size.width + 2 * o, size.height + 2 * o),
                                    CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()),
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/** Off by default; shows the typical range on the chart. */
@Composable
fun TypicalToggle(on: Boolean, onToggle: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(34.dp, 20.dp).clip(RoundedCornerShape(10.dp)).background(if (on) p.ink else p.line2)) {
            Box(Modifier.padding(start = if (on) 17.dp else 3.dp, top = 3.dp).size(14.dp).clip(CircleShape).background(p.bg))
        }
        Text("Typical range for beginners", style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = p.ink2)
    }
    if (on) Text(
        "Hatched: where lifters starting out usually are after the same time. A rough reference, not a goal.",
        style = Type.small.copy(fontSize = 11.5.sp, lineHeight = 16.sp), color = p.mute,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaceSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
        )
        InkButton(onDismiss, Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(bottom = 26.dp)) {
            Text("Done", style = Type.body.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/** Mockup .facts row: label + right value, mute note below. */
@Composable
private fun Fact(label: String, value: @Composable () -> Unit, note: String?) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), style = Type.small.copy(fontSize = 13.5.sp), color = p.ink2)
            value()
        }
        if (note != null) Text(note, style = Type.small.copy(fontSize = 12.sp, lineHeight = 16.sp), color = p.mute)
    }
}

@Composable
private fun FactValue(text: String) =
    Text(text, style = Type.display(14.sp, 700), color = LocalPalette.current.ink, textAlign = TextAlign.End, maxLines = 1)

private fun weekdays(days: Set<DayOfWeek>) = days.sorted().joinToString(" · ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }

/** How the status, pace and next jump of one lift are worked out. */
@Composable
fun LiftPaceSheet(name: String, day: String, pace: LiftPace, onDismiss: () -> Unit) {
    val m = pace.model ?: return
    val n = pace.next
    PaceSheet(onDismiss) {
        Column {
            Eyebrow(name)
            Text(m.status.label, style = Type.display(22.sp, 800, lineHeight = 23.sp, tracking = -0.015f), color = statusColor(m.status))
        }
        Column {
            Fact("Last ${ExpectedProgress.RECENT} sessions", { FactValue(paceText(m.recentPerWeek)) },
                "Your recent pace, a straight line through them. Within ±${fmt1(m.tolerance)} kg/week of your usual pace " +
                    "counts as on track, so a session at the same weight while reps climb is fine.")
            Hairline()
            Fact("Usual pace", { FactValue(paceText(m.pacePerWeek)) },
                "Average slope over the same sessions of a curve through all your logged sessions, flattening as you " +
                    "train longer. It flattens with each session, not with each week, so a break does not lower it.")
            Hairline()
            Fact("Est. max now · curve", { FactValue("${fmt1(m.sessions.last().estMax)} · ${fmt1(m.curve(m.n - 1))} kg") },
                "Epley, averaged over the sets at the working weight; sets with typo-like reps are left out. " +
                    "The chart shows the curve as kg for ${kg(m.midReps)} reps.")
            Hairline()
            val nextValue = n?.sessions?.let { "+${kg(n.increment)} kg" } ?: "–"
            val note = when {
                n == null -> "Needs two logged sessions."
                n.sessions == null ->
                    "Not expected yet. ${fmt1(n.avgReps)} reps per set at ${kg(n.kg)} kg and no gain over the last ${sessions(n.atWeight)}, so there is no date to show."
                else -> {
                    val s = n.sessions
                    val head = "+${kg(n.increment)} kg in ~$s session${if (s > 1) "s" else ""}" +
                        (pace.nextDate?.let { " · ${wdFmt.format(it)}" } ?: "") + ". "
                    val why = if (n.need <= 0) "All sets reached ${pace.repMax} reps at ${kg(n.kg)} kg."
                    else "Now ${fmt1(n.avgReps)} reps per set at ${kg(n.kg)} kg, gaining ${fmt1(n.gain ?: 0.0)} a session; ${pace.repMax} on every set earns the jump."
                    val sched = if (pace.weekdays.isEmpty() || pace.nextDate == null) "" else {
                        val on = pace.nextDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                        " Date from your ${weekdays(pace.weekdays)} schedule ($day on ${on}s)."
                    }
                    head + why + sched
                }
            }
            Fact("Next weight jump", { FactValue(nextValue) }, note)
            Hairline()
            Fact("Data used", { FactValue("${m.n} sessions") },
                "Logged since ${loggedSince()}." + if (m.n0 > 0) " Earlier sessions are estimates and are left out." else "")
        }
    }
}

/** Every lift of the day with its status, pace and next jump. */
@Composable
fun DayPaceSheet(day: String, lifts: List<Pair<Lift, LiftPace>>, onDismiss: () -> Unit) {
    PaceSheet(onDismiss) {
        Column {
            Eyebrow("Pace since ${loggedSince()}")
            Text(day, style = Type.display(22.sp, 800, lineHeight = 23.sp, tracking = -0.015f), color = LocalPalette.current.ink)
        }
        Column {
            lifts.forEachIndexed { i, (l, pace) ->
                if (i > 0) Hairline()
                val m = pace.model
                Fact(
                    l.name, { if (m != null) Pill(m.status) else FactValue("–") },
                    if (m == null) "Not enough logged sessions yet." else listOfNotNull(paceText(m.pacePerWeek), nextShort(pace)).joinToString(" · "),
                )
            }
        }
    }
}
