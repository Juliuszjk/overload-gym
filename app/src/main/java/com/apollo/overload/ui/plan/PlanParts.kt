package com.apollo.overload.ui.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollo.overload.data.Exercise
import com.apollo.overload.data.Muscles
import com.apollo.overload.data.PlanExercise
import com.apollo.overload.ui.common.CtlShape
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.SheetShape
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import kotlinx.coroutines.launch

val Kinds = listOf("upper", "lower", "push", "pull", "legs", "full", "custom")
val KindLabel = mapOf(
    "upper" to "Upper", "lower" to "Lower", "push" to "Push", "pull" to "Pull",
    "legs" to "Legs", "full" to "Full body", "custom" to "Custom",
)
val ExerciseTypes = listOf("push", "pull", "legs", "core")

/** Exercise types suggested for a day kind in the Add exercise sheet. */
fun suggestedTypes(kind: String): List<String> = when (kind) {
    "upper" -> listOf("push", "pull")
    "lower", "legs" -> listOf("legs", "core")
    "push" -> listOf("push")
    "pull" -> listOf("pull")
    else -> ExerciseTypes
}

/** Muscle labels, strongest activation first. */
fun Exercise.musclesText(): String =
    muscles.entries.filter { it.value > 0f }.sortedByDescending { it.value }.joinToString(" · ") { Muscles.label[it.key] ?: it.key }

fun Exercise.primaryMuscle(): String? = muscles.maxByOrNull { it.value }?.key

/** Keeps a pair only while both rows sit next to each other, and renumbers positions. */
fun normalizePlan(list: List<PlanExercise>): List<PlanExercise> = list.mapIndexed { i, r ->
    val keep = r.pairId != null &&
        (list.getOrNull(i - 1)?.pairId == r.pairId || list.getOrNull(i + 1)?.pairId == r.pairId)
    r.copy(position = i, pairId = if (keep) r.pairId else null)
}

/** "Pair N" numbers in plan order, for rows that are actually paired. */
fun pairNumbers(rows: List<PlanExercise>): Map<Long, Int> {
    val ids = mutableListOf<Int>()
    val out = mutableMapOf<Long, Int>()
    rows.forEachIndexed { i, r ->
        val id = r.pairId ?: return@forEachIndexed
        val paired = rows.getOrNull(i - 1)?.pairId == id || rows.getOrNull(i + 1)?.pairId == id
        if (!paired) return@forEachIndexed
        if (id !in ids) ids += id
        out[r.id] = ids.indexOf(id) + 1
    }
    return out
}

fun dayMinutes(sets: Int): Int = Math.round(sets * 2.6 + 5).toInt()

/** Back arrow + eyebrow label (mockup .appbar). */
@Composable
fun AppBar(label: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        Eyebrow(label, Modifier.padding(start = 6.dp).weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun IconBtn(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = LocalPalette.current.ink) {
    Box(modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** PUSH / PULL / LEGS / CORE tag (mockup .tt). */
@Composable
fun TypeTag(type: String) {
    val p = LocalPalette.current
    val (fg, border) = when (type) {
        "push" -> p.h2 to p.h2.copy(alpha = 0.45f)
        "pull" -> p.ink2 to p.ink2
        else -> p.mute to p.line2
    }
    Text(
        type.uppercase(),
        Modifier.border(1.dp, border, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 3.dp),
        style = Type.display(9.5.sp, 700, tracking = 0.1f, lineHeight = 10.sp), color = fg, maxLines = 1,
    )
}

/** Filter / option chip (mockup .chip). */
@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(999.dp)
    Text(
        text,
        Modifier.clip(shape).background(if (on) p.ink else Color.Transparent).border(1.dp, if (on) p.ink else p.line2, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp),
        style = Type.small.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
        color = when { on -> p.onInk; enabled -> p.ink2; else -> p.ink2.copy(alpha = 0.4f) },
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** Uppercase field label (mockup .field > span). */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, style = Type.display(10.5.sp, 600, tracking = 0.12f), color = LocalPalette.current.mute)

/** Bordered input (mockup .field input). [big] uses the 22sp numeric display style. */
@Composable
fun FieldInput(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    big: Boolean = true,
    placeholder: String = "",
    singleLine: Boolean = true,
) {
    val p = LocalPalette.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val style: TextStyle = if (big) Type.display(22.sp, 800) else Type.body.copy(fontSize = 15.sp)
    BasicTextField(
        value, onChange,
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.bg)
            .border(1.5.dp, if (focused) p.h2 else p.line2, RoundedCornerShape(12.dp)),
        textStyle = style.copy(color = p.ink), singleLine = singleLine, cursorBrush = SolidColor(p.h2),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), interactionSource = source,
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 12.dp, vertical = if (big) 10.dp else 12.dp)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = style, color = p.mute)
                inner()
            }
        },
    )
}

/** Section header row (mockup .sechd). */
@Composable
fun SectionHead(title: String, sub: String?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, Modifier.weight(1f), style = Type.display(17.sp, 800, tracking = -0.01f), color = LocalPalette.current.ink)
        if (sub != null) Sub(sub)
    }
}

@Composable
fun Sub(text: String, modifier: Modifier = Modifier) = Text(text, modifier, style = Type.small, color = LocalPalette.current.mute)

@Composable
fun Foot(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = Type.small.copy(fontSize = 11.sp, lineHeight = 16.sp), color = LocalPalette.current.mute)

/** Dashed "+ Add …" row (mockup .addrow). */
@Composable
fun AddRow(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier.fillMaxWidth().clip(CtlShape)
            .drawBehind {
                drawRoundRect(
                    p.line2, cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clickable(onClick = onClick).padding(13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.ink2, modifier = Modifier.size(18.dp))
        Text(text, Modifier.padding(start = 8.dp), style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = p.ink2)
    }
}

/** Small ghost button with an optional leading icon (mockup .btn.ghost.sm). */
@Composable
fun SmallGhost(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val p = LocalPalette.current
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).border(BorderStroke(1.dp, p.line2), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (enabled) p.ink else p.mute
        if (icon != null) Icon(icon, null, tint = c, modifier = Modifier.size(18.dp).padding(end = 0.dp))
        if (text.isNotEmpty()) Text(text, Modifier.padding(start = if (icon != null) 6.dp else 0.dp), style = Type.label, color = c)
    }
}

/** Small ink-filled button (mockup .btn.sm). */
@Composable
fun SmallInk(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = LocalPalette.current
    Text(
        text,
        modifier.clip(RoundedCornerShape(12.dp)).background(if (enabled) p.ink else p.line2)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        style = Type.label, color = if (enabled) p.onInk else p.mute,
    )
}

/**
 * Bottom sheet styled like the mockup .sheet. [content] gets a `close` lambda that animates the sheet away
 * and then calls [onDismiss]. With [fullHeight] the content is not scrolled here (the caller scrolls a list).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSheet(onDismiss: () -> Unit, fullHeight: Boolean = false, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val p = LocalPalette.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, shape = SheetShape, containerColor = p.raise, contentColor = p.ink,
        scrimColor = Color.Black.copy(alpha = 0.5f), tonalElevation = 0.dp,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.line2)) },
    ) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (fullHeight) Modifier.fillMaxHeight(0.92f) else Modifier.verticalScroll(rememberScrollState()))
                .imePadding().padding(horizontal = 20.dp).padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content(close) }
    }
}

/** Sheet header: eyebrow + close button. */
@Composable
fun SheetHead(eyebrow: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(eyebrow, Modifier.weight(1f))
        SmallGhost("", onClose, icon = Icons.Outlined.Close)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = p.raise, titleContentColor = p.ink, textContentColor = p.ink2,
        title = { Text(title, style = Type.display(20.sp, 800)) },
        text = { Text(text, style = Type.body) },
        confirmButton = { TextButton({ onDismiss(); onConfirm() }) { Text(confirm, style = Type.label, color = p.ink) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", style = Type.label, color = p.mute) } },
    )
}

@Composable
fun TextDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = p.raise, titleContentColor = p.ink,
        title = { Text(title, style = Type.display(20.sp, 800)) },
        text = { FieldInput(text, { text = it }, big = false) },
        confirmButton = {
            TextButton({ if (text.isNotBlank()) { onDismiss(); onSave(text.trim()) } }) { Text("Save", style = Type.label, color = p.ink) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", style = Type.label, color = p.mute) } },
    )
}

/** Labelled field block (mockup .field). */
@Composable
fun Field(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        content()
    }
}

/** Letter-spaced title used in sheets (24sp display). */
val SheetTitle: TextStyle get() = Type.display(24.sp, 800, lineHeight = 25.sp)
