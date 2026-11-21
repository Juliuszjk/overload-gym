package com.apollo.overload.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.GhostButton
import com.apollo.overload.ui.common.kg
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Palette
import com.apollo.overload.ui.theme.Type
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ---------- formatting ---------- */

private val filler = setOf("machine", "straight", "bar", "dumbbell", "barbell", "cable", "seated", "standing", "lying", "supported", "v")

/** Short label for the exercise track and reasons, e.g. "Incline Smith Press" -> "Incline Smith". */
internal fun shortName(name: String): String {
    val words = name.split(' ').filter { it.isNotBlank() }
    return words.filter { it.lowercase() !in filler }.ifEmpty { words }.take(2).joinToString(" ")
}

/** "35 kg", or "BW" for bodyweight. */
internal fun load(v: Double): String = if (v == 0.0) "BW" else "${kg(v)} kg"

internal fun setText(kgV: Double, reps: Int): String = "${if (kgV == 0.0) "BW" else kg(kgV)}×$reps"

internal fun dayLabel(millis: Long): String = SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(Date(millis))
internal fun clock(millis: Long): String = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(millis))

/* ---------- state savers ---------- */

internal val LongIntMapSaver = Saver<Map<Long, Int>, LongArray>(
    save = { m -> m.flatMap { listOf(it.key, it.value.toLong()) }.toLongArray() },
    restore = { a -> a.toList().chunked(2).associate { it[0] to it[1].toInt() } },
)

internal val LongDoubleMapSaver = Saver<Map<Long, Double>, DoubleArray>(
    save = { m -> m.flatMap { listOf(it.key.toDouble(), it.value) }.toDoubleArray() },
    restore = { a -> a.toList().chunked(2).associate { it[0].toLong() to it[1] } },
)

/* ---------- layout helpers ---------- */

/** Mix [c] at [alpha] over the page background (CSS color-mix). */
internal fun Palette.wash(c: Color, alpha: Float): Color = c.copy(alpha = alpha).compositeOver(bg)

/**
 * Bottom padding that lifts this element above the keyboard, counting only the part of the keyboard that overlaps
 * it (the bottom nav and system bar below this screen are already covered by the keyboard).
 */
@Composable
internal fun Modifier.aboveKeyboard(): Modifier {
    val density = LocalDensity.current
    val rootHeight = LocalView.current.rootView.height
    val ime = WindowInsets.ime.getBottom(density)
    var below by remember { mutableIntStateOf(0) }
    return this
        .onGloballyPositioned { below = (rootHeight - (it.positionInWindow().y + it.size.height)).toInt().coerceAtLeast(0) }
        .padding(bottom = with(density) { (ime - below).coerceAtLeast(0).toDp() })
}

@Composable
internal fun keyboardOpen(): Boolean = WindowInsets.ime.getBottom(LocalDensity.current) > 0

/* ---------- small pieces ---------- */

/** Eyebrow line (+ optional chip under it) + Plan button (+ optional overflow menu), mockup .wk-top. */
@Composable
internal fun WorkoutTop(eyebrow: String, onOpenPlan: () -> Unit, menu: (@Composable () -> Unit)? = null, chip: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f).padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Eyebrow(eyebrow)
            chip?.invoke()
        }
        GhostButton(onOpenPlan) {
            Icon(Icons.AutoMirrored.Outlined.List, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Plan", style = Type.label.copy(fontSize = 14.sp))
        }
        menu?.invoke()
    }
}

/** ↑n / ↓n badge: moved against the plan position. */
@Composable
internal fun MovedBadge(moved: Int) {
    if (moved == 0) return
    val p = LocalPalette.current
    Text(
        if (moved > 0) "↑$moved" else "↓${-moved}",
        Modifier.clip(RoundedCornerShape(5.dp)).background(p.sunk).padding(horizontal = 5.dp, vertical = 3.dp),
        style = Type.display(11.sp, 800), color = if (moved > 0) p.good else p.warn,
    )
}

/** Green "+2.5 kg" style badge (mockup .plus). */
@Composable
internal fun PlusBadge(text: String) {
    val p = LocalPalette.current
    Text(
        text, Modifier.clip(RoundedCornerShape(5.dp)).background(p.wash(p.good, 0.16f)).padding(horizontal = 6.dp, vertical = 4.dp),
        style = Type.display(10.5.sp, 800), color = p.good, maxLines = 1,
    )
}

/** Compact ink-filled button used inside tips. */
@Composable
internal fun SmallInkButton(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        text, Modifier.clip(RoundedCornerShape(10.dp)).background(p.ink).clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        style = Type.label, color = p.onInk,
    )
}

/** Small text action, e.g. "+ Set", "Skip". */
@Composable
internal fun TextAction(text: String, onClick: () -> Unit, color: Color = LocalPalette.current.ink2) {
    val p = LocalPalette.current
    Text(
        text, Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, p.line2, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 7.dp),
        style = Type.label, color = color,
    )
}

/** Amber off-plan tip with an action (mockup .tip). */
@Composable
internal fun Tip(icon: @Composable () -> Unit, lead: String, rest: String, action: String, onAction: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.warnBg).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = p.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)) { append(lead) }
                append(" "); append(rest)
            },
            Modifier.weight(1f), style = Type.small.copy(fontSize = 13.5.sp), color = p.ink2,
        )
        SmallInkButton(action, onAction)
    }
}

/** The segmented exercise track (mockup .fx-track). */
@Composable
internal fun Track(segments: List<TrackSegment>, onTap: (Long) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.forEach { s ->
            Column(
                Modifier.weight(1f).clickable(enabled = !s.done) { onTap(s.id) }.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(
                            when {
                                // Amber = moved against the plan; orange stays "current".
                                s.moved != 0 -> p.warn.copy(alpha = 0.45f).compositeOver(p.line2)
                                s.now -> p.h2.copy(alpha = 0.35f).compositeOver(p.line2)
                                else -> p.line2
                            }
                        )
                ) {
                    Box(
                        Modifier.fillMaxWidth(s.fraction.coerceIn(0f, 1f)).height(4.dp)
                            .background(if (s.now) p.h2 else p.ink)
                    )
                }
                Text(
                    buildAnnotatedString {
                        if (s.moved != 0) withStyle(SpanStyle(color = p.warn, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)) {
                            append(if (s.moved > 0) "↑${s.moved} " else "↓${-s.moved} ")
                        }
                        append(s.label)
                    },
                    style = Type.display(10.5.sp, 600, tracking = 0.04f), color = if (s.now) p.ink else p.mute,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** [moved]: plan position minus session position, 0 when in its plan place. */
internal data class TrackSegment(val id: Long, val label: String, val fraction: Float, val now: Boolean, val done: Boolean, val moved: Int = 0)

/**
 * A big number that is also a text input: decimal keyboard, comma accepted, select-all on focus, dashed underline
 * (solid orange while editing). [onText] receives every edit; the caller parses it and updates [value].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NumberInput(
    value: String,
    onText: (String) -> Unit,
    parse: (String) -> Double?,
    keyboardType: KeyboardType,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.ink,
    lineTint: Color? = null,
) {
    val p = LocalPalette.current
    val focusManager = LocalFocusManager.current
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    var focused by remember { mutableStateOf(false) }
    var selectAll by remember { mutableStateOf(false) }
    // Hiding the keyboard with Back keeps focus; drop it so the next tap selects all again instead of appending.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) { if (!imeVisible && focused) focusManager.clearFocus() }
    LaunchedEffect(value, focused) {
        if (tfv.text != value && (!focused || parse(tfv.text) != parse(value))) {
            tfv = TextFieldValue(value, TextRange(value.length))
        }
    }
    val lineColor = if (focused) p.h2 else lineTint ?: p.line2
    BasicTextField(
        value = tfv,
        onValueChange = { n ->
            // The tap that focuses the field also moves the cursor; keep the select-all from focusing instead.
            if (selectAll && n.text == tfv.text) { selectAll = false; return@BasicTextField }
            selectAll = false
            val changed = n.text != tfv.text
            tfv = n
            if (changed) onText(n.text)
        },
        modifier = modifier
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    tfv = tfv.copy(selection = TextRange(0, tfv.text.length))
                    selectAll = true
                }
                focused = it.isFocused
            }
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(),
                    pathEffect = if (focused) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                )
            }
            .padding(bottom = 8.dp),
        textStyle = style.copy(color = color, textAlign = TextAlign.Center),
        singleLine = true,
        cursorBrush = SolidColor(p.h2),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
}
