package com.apollo.overload.ui.common

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.apollo.overload.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Anatomical figure from react-native-body-highlighter 3.2.0 (MIT, Hicham El Abbassi), assets/body.json.
 * Coordinates: front view box (0, 40, 724, 1380), back view box (724, 40, 724, 1380).
 */
enum class Side { Front, Back }

class BodyPaths(val outline: Map<Side, Path>, val parts: Map<Side, List<Pair<String?, Path>>>)

private val FRONT_KEYS = mapOf(
    "chest" to "chest", "obliques" to "obliques", "abs" to "abs", "biceps" to "biceps", "triceps" to "triceps",
    "trapezius" to "traps", "deltoids" to "delts", "adductors" to "adductors", "quadriceps" to "quads",
    "calves" to "calves", "forearm" to "forearms",
)
private val BACK_KEYS = mapOf(
    "trapezius" to "traps", "deltoids" to "reardelts", "upper-back" to "back", "triceps" to "triceps",
    "lower-back" to "lowerback", "forearm" to "forearms", "gluteal" to "glutes", "adductors" to "adductors",
    "hamstring" to "hamstrings", "calves" to "calves",
)

private var cached: BodyPaths? = null

fun loadBodyPaths(context: Context): BodyPaths {
    cached?.let { return it }
    val root = Json.parseToJsonElement(context.assets.open("body.json").use { it.readBytes().decodeToString() }).jsonObject
    fun parse(d: String) = PathParser().parsePathString(d).toPath()
    val outline = (root["outline"] as JsonObject).let {
        mapOf(Side.Front to parse(it["front"]!!.jsonPrimitive.content), Side.Back to parse(it["back"]!!.jsonPrimitive.content))
    }
    val parts = mapOf(Side.Front to "front", Side.Back to "back").mapValues { (side, key) ->
        val keys = if (side == Side.Front) FRONT_KEYS else BACK_KEYS
        root[key]!!.jsonObject.flatMap { (slug, arr) ->
            arr.jsonArray.map { keys[slug] to parse(it.jsonPrimitive.content) }
        }
    }
    return BodyPaths(outline, parts).also { cached = it }
}

@Composable
fun rememberBodyPaths(): BodyPaths? {
    val context = LocalContext.current
    val paths by produceState(cached, context) { value = withContext(Dispatchers.Default) { loadBodyPaths(context) } }
    return paths
}

/**
 * One side of the figure. [fill] gives each muscle key its colour (null = untrained body colour).
 * [selected] gets an ink outline. [onTap] reports the tapped muscle key.
 */
@Composable
fun BodyFigure(
    side: Side,
    fill: (String) -> Color?,
    modifier: Modifier = Modifier,
    selected: String? = null,
    onTap: ((String) -> Unit)? = null,
) {
    val paths = rememberBodyPaths() ?: return
    val p = LocalPalette.current
    val originX = if (side == Side.Front) 0f else 724f
    val vbW = 724f; val vbH = 1380f; val originY = 40f
    val tapMod = if (onTap != null) Modifier.pointerInput(side, paths) {
        detectTapGestures { pos ->
            val s = size.width / vbW
            val x = pos.x / s + originX; val y = pos.y / s + originY
            val region = android.graphics.Region()
            val clip = android.graphics.Region(-10000, -10000, 10000, 10000)
            val hit = paths.parts[side]!!.lastOrNull { (k, path) ->
                k != null && run { region.setPath(path.asAndroidPath(), clip); region.contains(x.toInt(), y.toInt()) }
            }
            hit?.first?.let(onTap)
        }
    } else Modifier
    Canvas(modifier.aspectRatio(vbW / vbH).then(tapMod)) {
        val s = size.width / vbW
        scale(s, s, pivot = Offset.Zero) {
            translate(-originX, -originY) {
                val outline = paths.outline[side]!!
                drawPath(outline, p.sil)
                drawPath(outline, p.line2, style = Stroke(width = 2f / s))
                for ((key, path) in paths.parts[side]!!) {
                    val color = if (key == null) p.bodyInert else fill(key) ?: p.body
                    drawPath(path, color)
                    drawPath(path, p.bg, style = Stroke(width = 2.5f))
                }
                if (selected != null) for ((key, path) in paths.parts[side]!!) {
                    if (key == selected) drawPath(path, p.ink, style = Stroke(width = 6f))
                }
            }
        }
    }
}
