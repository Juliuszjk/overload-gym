package com.apollo.overload.ui.workout

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollo.overload.data.Exercise
import com.apollo.overload.data.Muscles
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.GhostButton
import com.apollo.overload.ui.common.Gutter
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One entry of assets/howto.json: the free-exercise-db source, whether it is only a similar movement, short steps. */
@Serializable
internal data class HowTo(val src: String, val similar: Boolean = false, val steps: List<String> = emptyList())

internal object HowTos {
    @Volatile private var cache: Map<String, HowTo>? = null

    /** App exercise id -> how-to. Read once from assets; call off the main thread. */
    fun all(context: Context): Map<String, HowTo> = cache ?: synchronized(this) {
        cache ?: runCatching {
            val text = context.assets.open("howto.json").use { it.readBytes().decodeToString() }
            Json { ignoreUnknownKeys = true }.decodeFromString<Map<String, HowTo>>(text)
        }.getOrDefault(emptyMap()).also { cache = it }
    }

    /**
     * The two demo frames: bundled in assets/howto/<src>/, else cached in filesDir, else downloaded from
     * free-exercise-db on GitHub and cached. Null when offline and not cached.
     */
    suspend fun frames(context: Context, src: String): List<ImageBitmap>? = withContext(Dispatchers.IO) {
        runCatching { (0..1).map { i -> context.assets.open("howto/$src/$i.jpg").use { it.readBytes() } } }.getOrNull()?.let { return@withContext decode(it) }
        val dir = File(context.filesDir, "howto/$src")
        val files = (0..1).map { File(dir, "$it.jpg") }
        if (files.all { it.isFile }) decode(files.map { it.readBytes() })?.let { return@withContext it }
        val bytes = runCatching { (0..1).map { download("$RAW/$src/$it.jpg") } }.getOrNull() ?: return@withContext null
        val frames = decode(bytes) ?: return@withContext null
        runCatching {
            dir.mkdirs()
            files.zip(bytes).forEach { (f, b) -> File(dir, "${f.name}.tmp").apply { writeBytes(b); renameTo(f) } }
        }
        frames
    }

    private fun decode(bytes: List<ByteArray>): List<ImageBitmap>? =
        bytes.map { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() ?: return null }

    private fun download(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000; c.readTimeout = 8000
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private const val RAW = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises"
}

private sealed interface Demo {
    data object Loading : Demo
    data object Missing : Demo
    class Ready(val frames: List<ImageBitmap>) : Demo
}

private val Scrim = Color(0x9E141311)
private val OnScrim = Color(0xFFF3EFE7)

/** Full-screen how-to subpage: demo, name, your setup, steps, muscles, your video link, credit. */
@Composable
internal fun HowToPage(name: String, howTo: HowTo, ex: Exercise?, note: String, url: String, onBack: () -> Unit, onVideo: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val demo by produceState<Demo>(Demo.Loading, howTo.src) {
        value = HowTos.frames(context, howTo.src)?.let { Demo.Ready(it) } ?: Demo.Missing
    }
    Column(
        Modifier.fillMaxSize().background(p.bg)
            // Take every touch so nothing under the page reacts.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
    ) {
        Row(
            Modifier.fillMaxWidth().drawBehind {
                drawLine(p.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = p.ink) }
            Text("How to", style = Type.display(17.sp, 800, tracking = -0.01f), color = p.ink)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter).padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (val d = demo) {
                is Demo.Ready -> DemoBox(d.frames, howTo.similar, name)
                Demo.Loading -> Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(18.dp)).background(p.sunk))
                Demo.Missing -> {}
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, style = Type.display(30.sp, 800, tracking = -0.025f, lineHeight = 30.sp), color = p.ink)
                if (note.isNotBlank()) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.sunk).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp), tint = p.ink2)
                    Text(
                        buildAnnotatedString {
                            append("Your setup: ")
                            withStyle(SpanStyle(color = p.ink, fontWeight = FontWeight.SemiBold)) { append(note) }
                        },
                        style = Type.body.copy(fontSize = 14.sp), color = p.ink2,
                    )
                }
            }
            if (howTo.steps.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow("Steps")
                Column {
                    howTo.steps.take(4).forEachIndexed { k, t ->
                        Row(
                            Modifier.fillMaxWidth().drawBehind {
                                if (k > 0) drawLine(p.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                            }.padding(top = if (k == 0) 2.dp else 10.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("${k + 1}", Modifier.width(26.dp), style = Type.display(17.sp, 800, lineHeight = 20.sp), color = p.mute)
                            Text(t, style = Type.body.copy(fontSize = 14.5.sp, lineHeight = 20.sp), color = p.ink2)
                        }
                    }
                }
            }
            val muscles = ex?.muscles.orEmpty().entries.sortedByDescending { it.value }
            val primary = muscles.filter { it.value >= 1f }.mapNotNull { Muscles.label[it.key] }
            val secondary = muscles.filter { it.value < 1f }.mapNotNull { Muscles.label[it.key] }
            if (primary.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow("Muscles")
                Column(Modifier.drawBehind {
                    drawLine(p.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                    drawLine(p.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                }) {
                    MuscleRow("Primary", primary.joinToString(" · "), first = true)
                    MuscleRow("Secondary", secondary.joinToString(" · ").ifEmpty { "None listed" }, first = false)
                }
            }
            if (url.isNotBlank()) GhostButton(onVideo, Modifier.fillMaxWidth()) {
                Text("Watch video", Modifier.weight(1f), style = Type.label.copy(fontSize = 15.sp))
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
            }
            Text("Photos: free-exercise-db (Unlicense)", style = Type.small.copy(fontSize = 11.sp, lineHeight = 16.sp), color = p.mute)
        }
    }
}

@Composable
private fun MuscleRow(label: String, value: String, first: Boolean) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().drawBehind {
            if (!first) drawLine(p.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(96.dp), style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = p.ink)
        Text(value, style = Type.body.copy(fontSize = 14.sp), color = p.ink2)
    }
}

/** 3:2 two-frame demo, frames swap every 0.8 s with a short crossfade; tap pauses / resumes. */
@Composable
private fun DemoBox(frames: List<ImageBitmap>, similar: Boolean, name: String) {
    val p = LocalPalette.current
    var paused by rememberSaveable { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(paused) {
        while (!paused) {
            delay(800)
            frame = 1 - frame
        }
    }
    val bAlpha by animateFloatAsState(if (frame == 1) 1f else 0f, tween(220, easing = LinearEasing), label = "demo")
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(18.dp)).background(p.sunk)
            .clickable { paused = !paused },
    ) {
        Image(frames[0], "$name, start position", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Image(frames[1], "$name, end position", Modifier.fillMaxSize().alpha(bAlpha), contentScale = ContentScale.Crop)
        if (similar) Pill(Modifier.align(Alignment.TopStart).padding(10.dp)) {
            Text("Similar movement", style = Type.display(11.sp, 700, tracking = 0.06f), color = OnScrim)
        }
        if (paused) Box(
            Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape).background(Scrim),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.PlayArrow, null, Modifier.size(26.dp), tint = OnScrim) }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(Modifier) {
                repeat(2) { k ->
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (k == frame) OnScrim else OnScrim.copy(alpha = 0.4f)))
                    if (k == 0) Spacer(Modifier.width(4.dp))
                }
            }
            Pill(Modifier) {
                Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, Modifier.size(12.dp), tint = OnScrim)
                Spacer(Modifier.width(5.dp))
                Text(if (paused) "PAUSED" else "TAP TO PAUSE", style = Type.display(11.sp, 700, tracking = 0.06f), color = OnScrim)
            }
        }
    }
}

@Composable
private fun Pill(modifier: Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(999.dp)).background(Scrim).padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
