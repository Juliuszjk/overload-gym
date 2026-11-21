package com.apollo.overload.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.Gutter
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type

/** Pinned bottom dock (mockup .upnext): the main action, then an optional collapsible "Next" bar. */
@Composable
internal fun Dock(next: (@Composable () -> Unit)? = null, action: @Composable () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(p.raise).border(1.dp, p.line, shape)
            .padding(horizontal = Gutter).padding(top = 12.dp, bottom = if (next == null) 12.dp else 0.dp)
    ) {
        action()
        next?.invoke()
    }
}

/** One exercise row of the "Next" timeline. */
internal data class TimelineRow(
    val id: Long, val name: String, val load: String, val moved: Int, val sub: String, val rank: Int,
)

@Composable
internal fun NextBar(
    next: TimelineRow?,
    open: Boolean,
    onToggle: () -> Unit,
    done: List<Pair<String, String?>>,
    now: TimelineRow?,
    queue: List<TimelineRow>,
    foot: String,
    onStart: (Long) -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow("Next")
        Text(
            next?.name ?: "Last exercise", Modifier.weight(1f, fill = false),
            style = Type.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold), color = p.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (next != null) {
            Text(next.load, style = Type.display(13.sp, 800), color = p.ink)
            MovedBadge(next.moved)
        }
        Spacer(Modifier.weight(1f))
        if (queue.size > 1) Text("+${queue.size - 1}", style = Type.display(12.sp, 700), color = p.mute)
        Icon(
            Icons.Outlined.KeyboardArrowUp, if (open) "Collapse" else "Expand",
            Modifier.size(20.dp).rotate(if (open) 180f else 0f), tint = p.mute,
        )
    }
    if (!open) return
    val shown = queue.take(3)
    val total = (if (done.isNotEmpty()) 1 else 0) + (if (now != null) 1 else 0) + shown.size
    var idx = 0
    Column(Modifier.padding(bottom = 8.dp)) {
        if (done.isNotEmpty()) {
            TimelineItem(idx++, total, node = { Node(filled = p.ink) { Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = p.onInk) } }) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        done.joinToString(" · ") { it.first }, Modifier.weight(1f, fill = false),
                        style = Type.small.copy(fontSize = 13.5.sp), color = p.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    done.mapNotNull { it.second }.distinct().firstOrNull()?.let { PlusBadge(it) }
                }
                Text("done", style = Type.small.copy(fontSize = 12.sp), color = p.mute)
            }
        }
        if (now != null) {
            TimelineItem(idx++, total, node = { Node(filled = p.h2) {} }) { RowBody(now) }
        }
        shown.forEach { r ->
            TimelineItem(idx++, total, onClick = { onStart(r.id) }, node = { Node(filled = null) { Text("${r.rank}", style = Type.display(11.sp, 800), color = p.mute) } }) {
                RowBody(r)
            }
        }
        if (queue.size > 3) {
            Text("+${queue.size - 3} more", Modifier.padding(start = 34.dp, top = 2.dp), style = Type.small.copy(fontSize = 12.sp), color = p.mute)
        }
        Text(foot, Modifier.padding(top = 8.dp, bottom = 4.dp), style = Type.small.copy(fontSize = 12.sp, lineHeight = 16.sp), color = p.mute)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.RowBody(r: TimelineRow) {
    val p = LocalPalette.current
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                r.name, Modifier.weight(1f, fill = false), style = Type.small.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            MovedBadge(r.moved)
        }
        Text(r.sub, style = Type.small.copy(fontSize = 12.sp, lineHeight = 16.sp), color = p.mute)
    }
    Text(r.load, style = Type.display(13.sp, 800), color = p.ink)
}

@Composable
private fun Node(filled: androidx.compose.ui.graphics.Color?, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(filled ?: p.bg).border(1.5.dp, filled ?: p.line2, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Timeline row with a connector line between the nodes. */
@Composable
private fun TimelineItem(
    index: Int, total: Int,
    onClick: (() -> Unit)? = null,
    node: @Composable () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val line = LocalPalette.current.line2
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind {
                val x = 12.dp.toPx()
                val c = size.height / 2
                val r = 12.dp.toPx()
                if (index > 0) drawLine(line, Offset(x, 0f), Offset(x, c - r), strokeWidth = 2.dp.toPx())
                if (index < total - 1) drawLine(line, Offset(x, c + r), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        node()
        content()
    }
}
