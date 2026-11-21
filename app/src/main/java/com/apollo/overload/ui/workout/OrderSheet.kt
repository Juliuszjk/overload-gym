package com.apollo.overload.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.GhostButton
import com.apollo.overload.ui.common.Gutter
import com.apollo.overload.ui.common.SheetShape
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type

/** Amber "Order changed" chip under the workout eyebrow (mockup .ochip). */
@Composable
internal fun OrderChip(onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(p.warnBg).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Outlined.Shuffle, null, Modifier.size(12.dp), tint = p.warn)
        Text("ORDER CHANGED", style = Type.display(10.5.sp, 800, tracking = 0.08f), color = p.warn, maxLines = 1)
    }
}

/**
 * Plan vs Today order, with "Back to plan order". Exercises with logged sets keep their place, so when nothing else can
 * move ([canRestore] false) the button is replaced by a one-line reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrderSheet(
    plan: List<String>, today: List<String>, todayMoved: List<Boolean>, canRestore: Boolean,
    onRestore: () -> Unit, onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape, containerColor = p.raise,
        dragHandle = { BottomSheetDefaults.DragHandle(color = p.line2) },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Order changed", style = Type.display(24.sp, 800), color = p.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrderColumn("Plan", plan, List(plan.size) { false }, Modifier.weight(1f))
                OrderColumn("Today", today, todayMoved, Modifier.weight(1f))
            }
            if (canRestore) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Logged sets stay.", Modifier.weight(1f), style = Type.small.copy(fontSize = 12.sp), color = p.mute)
                    GhostButton(onRestore) { Text("Back to plan order", style = Type.label.copy(fontSize = 14.sp)) }
                }
            } else {
                Text(
                    "Exercises with logged sets keep their place, so the rest is already in plan order.",
                    style = Type.small, color = p.mute,
                )
            }
        }
    }
}

@Composable
private fun OrderColumn(title: String, names: List<String>, moved: List<Boolean>, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Eyebrow(title)
        names.forEachIndexed { k, n ->
            val mv = moved.getOrElse(k) { false }
            Row {
                Text("${k + 1}", Modifier.width(14.dp), style = Type.display(12.sp, 800), color = p.mute)
                Spacer(Modifier.width(6.dp))
                Text(
                    n, style = Type.small.copy(fontWeight = if (mv) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (mv) p.warn else p.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
