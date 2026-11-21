package com.apollo.overload.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type

/** Screen side gutter used by every tab. */
val Gutter = 20.dp
val CtlShape = RoundedCornerShape(14.dp)
val SheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

/** Uppercase Archivo label in mute colour (mockup .eyebrow). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.mute) =
    Text(text.uppercase(), modifier, style = Type.eyebrow, color = color)

/** Eyebrow + wide title (mockup .eyebrow + .title). */
@Composable
fun ScreenHeader(eyebrow: String, title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Eyebrow(eyebrow)
            Text(title, style = Type.title, color = LocalPalette.current.ink, modifier = Modifier.padding(top = 4.dp))
        }
        trailing?.invoke()
    }
}

/** Primary action: ink-filled, never coloured. */
@Composable
fun InkButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val p = LocalPalette.current
    Button(
        onClick, modifier.heightIn(min = 52.dp), enabled = enabled, shape = CtlShape,
        colors = ButtonDefaults.buttonColors(containerColor = p.ink, contentColor = p.onInk, disabledContainerColor = p.line2, disabledContentColor = p.mute),
        contentPadding = PaddingValues(horizontal = 18.dp), content = content,
    )
}

/** Secondary action: hairline outline. */
@Composable
fun GhostButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val p = LocalPalette.current
    OutlinedButton(
        onClick, modifier.heightIn(min = 40.dp), shape = CtlShape, border = BorderStroke(1.dp, p.line2),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = p.ink),
        contentPadding = PaddingValues(horizontal = 14.dp), content = content,
    )
}

/** Small rounded tag, e.g. "plan #2", "PUSH", "↓1". */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, fg: Color = LocalPalette.current.ink2, bg: Color = LocalPalette.current.sunk) =
    Text(
        text, modifier.clip(RoundedCornerShape(5.dp)).background(bg).padding(horizontal = 6.dp, vertical = 3.dp),
        style = Type.display(11.sp, 800), color = fg,
    )

/** Segmented control (mockup Week/Day toggle). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier.fillMaxWidth().clip(CtlShape).background(p.sunk).border(1.dp, p.line, CtlShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (i == selected) p.raise else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, style = Type.small.copy(fontSize = 14.sp), color = if (i == selected) p.ink else p.mute) }
        }
    }
}

/** Hairline used instead of cards between list rows. */
@Composable
fun Hairline(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = LocalPalette.current.line)

/** Format a load: 35.0 -> "35", 17.5 -> "17.5". */
fun kg(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')

/** Parse a typed number, accepting a comma decimal separator. */
fun parseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

/** One decimal at most: 46.0 -> "46", 46.25 -> "46.3", 1.5 -> "1.5". */
fun fmt1(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
}
