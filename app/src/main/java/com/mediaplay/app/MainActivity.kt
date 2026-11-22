package com.mediaplay.app

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media as AudioCol
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }
}

// ---------- Dados ----------
data class Track(val id: Long, val title: String, val artist: String, val album: String, val dur: Long, val uri: Uri)

object Repo {
    fun load(c: Context): List<Track> {
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val out = mutableListOf<Track>()
        val proj = arrayOf(AudioCol._ID, AudioCol.TITLE, AudioCol.ARTIST, AudioCol.ALBUM, AudioCol.DURATION)
        val sel = AudioCol.IS_MUSIC + " != 0 AND " + AudioCol.DURATION + " > 30000"
        c.contentResolver.query(base, proj, sel, null, AudioCol.TITLE + " COLLATE NOCASE")?.use { q ->
            while (q.moveToNext()) {
                val id = q.getLong(0)
                out += Track(
                    id, q.getString(1) ?: "Sem título",
                    q.getString(2)?.takeIf { it != "<unknown>" } ?: "Artista desconhecido",
                    q.getString(3) ?: "", q.getLong(4), ContentUris.withAppendedId(base, id)
                )
            }
        }
        return out
    }
}

/** Contagem de "ouvidas" guardada só no aparelho. */
object Plays {
    private fun sp(c: Context) = c.getSharedPreferences("plays", Context.MODE_PRIVATE)
    fun all(c: Context): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        for ((k, v) in sp(c).all) {
            val id = k.toLongOrNull()
            if (id != null && v is Int) out[id] = v
        }
        return out
    }
    fun inc(c: Context, id: Long) {
        val s = sp(c)
        s.edit().putInt(id.toString(), s.getInt(id.toString(), 0) + 1).apply()
    }
}

fun item(t: Track) = MediaItem.Builder().setMediaId(t.id.toString()).setUri(t.uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(t.uri).build())
    .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build())
    .build()

fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)

class Now(private val p: Player) : Player.Listener {
    var playing by mutableStateOf(p.isPlaying)
    var id by mutableStateOf(p.currentMediaItem?.mediaId)
    var shuffle by mutableStateOf(p.shuffleModeEnabled)
    init { p.addListener(this) }
    override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { id = mediaItem?.mediaId }
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { shuffle = shuffleModeEnabled }
    fun release() = p.removeListener(this)
}

/** Duas cores da capa (cantos opostos), usadas no degradê do fundo. */
suspend fun coverColors(ctx: Context, uri: Uri): Pair<Color, Color>? = withContext(Dispatchers.IO) {
    runCatching {
        val b = ctx.contentResolver.loadThumbnail(uri, Size(32, 32), null)
        val s = Bitmap.createScaledBitmap(b, 2, 2, true)
        Color(s.getPixel(0, 0)) to Color(s.getPixel(1, 1))
    }.getOrNull()
}

// ---------- Visual "liquid glass" ----------
val Ink = Color(0xFF0B0D1A)
val Accent = Color(0xFFFF7A3D)
val Rainbow = Brush.linearGradient(listOf(Color(0xFF6AA8FF), Color(0xFFA56BFF), Color(0xFFFF6BD5)))

fun Modifier.glass(r: Int = 24): Modifier {
    val s = RoundedCornerShape(r.dp)
    return clip(s)
        .background(Brush.verticalGradient(listOf(Color.White.copy(.18f), Color.White.copy(.06f))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(.5f), Color.White.copy(.08f))), s)
}

/** Vidro mais firme, usado na barra de baixo para o texto da lista não aparecer por trás. */
fun Modifier.dock(r: Int): Modifier {
    val s = RoundedCornerShape(r.dp)
    return clip(s)
        .background(Brush.verticalGradient(listOf(Color(0xE0322F5E), Color(0xE0191736))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(.45f), Color.White.copy(.08f))), s)
}

/** Fundo em degradê com as cores do álbum. */
fun Modifier.aurora(c1: Color, c2: Color): Modifier = background(Ink).drawBehind {
    drawRect(Brush.radialGradient(listOf(c1.copy(alpha = .75f), Color.Transparent), Offset(size.width * .15f, 0f), size.width * 1.1f))
    drawRect(Brush.radialGradient(listOf(c2.copy(alpha = .6f), Color.Transparent), Offset(size.width, size.height * .2f), size.width))
}

/** Cache de capas na memória (até ~24 MB) para a rolagem ficar fluida. */
object Thumbs {
    val cache = object : android.util.LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
}

@Composable
fun Cover(uri: Uri?, mod: Modifier, px: Int = 512) {
    val ctx = LocalContext.current
    val key = "$uri@$px"
    val bmp by produceState<ImageBitmap?>(Thumbs.cache.get(key), key) {
        if (value == null && uri != null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.loadThumbnail(uri, Size(px, px), null).asImageBitmap() }.getOrNull()
            }
            if (loaded != null) Thumbs.cache.put(key, loaded)
            value = loaded
        }
    }
    Box(mod.background(Brush.linearGradient(listOf(Color(0xFF3A2A8F), Color(0xFFD04FD0))))) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("♪", Modifier.align(Alignment.Center), color = Color.White.copy(.4f), fontSize = if (px >= 512) 56.sp else 20.sp)
    }
}

/** Capa quadrada com o disco de vinil saindo pelo lado direito. */
@Composable
fun Sleeve(uri: Uri?, spinning: Boolean, modifier: Modifier = Modifier) {
    val rot: Float = if (spinning) {
        val tr = rememberInfiniteTransition(label = "disc")
        val r by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "r")
        r
    } else 0f
    BoxWithConstraints(modifier) {
        val sv = maxWidth / 1.28f
        Box(
            Modifier.offset(x = sv * 0.35f, y = sv * 0.04f).size(sv * 0.92f)
                .graphicsLayer { rotationZ = rot }
                .clip(CircleShape).background(Color(0xFF0A0A10))
                .drawBehind {
                    val rad = size.minDimension / 2
                    var k = 0.5f
                    while (k < 0.98f) {
                        drawCircle(Color.White.copy(alpha = .07f), radius = rad * k, style = Stroke(width = 1.2f))
                        k += 0.06f
                    }
                },
            Alignment.Center
        ) {
            Cover(uri, Modifier.size(sv * 0.42f).clip(CircleShape), 256)
            Box(Modifier.size(8.dp).clip(CircleShape).background(Ink))
        }
        Cover(uri, Modifier.size(sv).clip(RoundedCornerShape(10.dp)), 512)
    }
}

@Composable
fun Round(t: String, s: Int, active: Boolean = false, ring: Boolean = false, onClick: () -> Unit) {
    val br: Brush = if (ring) Rainbow else SolidColor(Color.White.copy(if (active) .9f else .35f))
    Box(
        Modifier.size(s.dp).clip(CircleShape).border(if (ring) 2.dp else 1.5.dp, br, CircleShape).clickable(onClick = onClick),
        Alignment.Center
    ) { if (t == "▶" || t == "⏸" || t == "⏮" || t == "⏭") Glyph(t, (s * .4f).dp) else Text(t, color = Color.White, fontSize = (s * .38f).sp) }
}

/** Botão play/pause estilo "Siri": esfera de vidro com cores girando por dentro. */
@Composable
fun Orb(playing: Boolean, size: Dp, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (playing) 3000 else 9000, easing = LinearEasing)), label = "spin")
    val pulse by t.animateFloat(1f, if (playing) 1.08f else 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Box(
        Modifier.size(size).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(CircleShape).clickable(onClick = onClick),
        Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }.background(
                Brush.sweepGradient(
                    listOf(Color(0xFF6AA8FF), Color(0xFFA56BFF), Color(0xFFFF6BD5), Color(0xFFFF7A3D), Color(0xFF5CF2D6), Color(0xFF6AA8FF))
                )
            )
        )
        Box(
            Modifier.fillMaxSize().padding(size * 0.06f).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White.copy(.5f), Color.White.copy(.08f))))
        )
        Glyph(if (playing) "⏸" else "▶", size * 0.4f)
    }
}

@Composable
fun Glyph(k: String, dp: Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(dp)) {
        val w = size.width
        val h = size.height
        val c = Color.White
        fun tri(l: Float, r: Float, right: Boolean) = Path().apply {
            if (right) { moveTo(l, h * .1f); lineTo(r, h / 2); lineTo(l, h * .9f) }
            else { moveTo(r, h * .1f); lineTo(l, h / 2); lineTo(r, h * .9f) }
            close()
        }
        val bar = androidx.compose.ui.geometry.Size(w * .14f, h * .8f)
        when (k) {
            "▶" -> drawPath(tri(w * .2f, w * .95f, true), c)
            "⏸" -> {
                val b = androidx.compose.ui.geometry.Size(w * .26f, h * .84f)
                drawRoundRect(c, Offset(w * .15f, h * .08f), b, CornerRadius(w * .06f))
                drawRoundRect(c, Offset(w * .59f, h * .08f), b, CornerRadius(w * .06f))
            }
            "⏭" -> { drawPath(tri(w * .05f, w * .7f, true), c); drawRect(c, Offset(w * .78f, h * .1f), bar) }
            "⏮" -> { drawPath(tri(w * .3f, w * .95f, false), c); drawRect(c, Offset(w * .08f, h * .1f), bar) }
        }
    }
}

// ---------- App ----------
@Composable
fun App() {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun has() = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    var ok by remember { mutableStateOf(has()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ok = has() }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    LaunchedEffect(ok) { if (ok) tracks = withContext(Dispatchers.IO) { Repo.load(ctx) } }

    var ctl by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val f = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()
        f.addListener({ runCatching { ctl = f.get() } }, ContextCompat.getMainExecutor(ctx))
        onDispose { MediaController.releaseFuture(f); ctl = null }
    }
    val now = remember(ctl) { ctl?.let { Now(it) } }
    DisposableEffect(now) { onDispose { now?.release() } }

    var tab by remember { mutableIntStateOf(0) }
    var full by remember { mutableStateOf(false) }
    var plays by remember { mutableStateOf(Plays.all(ctx)) }
    val cur = tracks.firstOrNull { it.id.toString() == now?.id }

    // Degradê do fundo com as cores da capa que está tocando
    var bg1 by remember { mutableStateOf(Color(0xFF3A2A8F)) }
    var bg2 by remember { mutableStateOf(Color(0xFFD04FD0)) }
    val a1 by animateColorAsState(bg1, tween(900), label = "a1")
    val a2 by animateColorAsState(bg2, tween(900), label = "a2")
    LaunchedEffect(cur?.id) {
        cur?.let { t -> coverColors(ctx, t.uri)?.let { (x, y) -> bg1 = x; bg2 = y } }
    }

    // Uma música só conta como "ouvida" depois de 30 s tocando
    LaunchedEffect(now?.id) {
        val id = now?.id?.toLongOrNull()
        if (id != null) {
            var secs = 0
            while (secs < 30) { delay(1000); if (now?.playing == true) secs++ }
            Plays.inc(ctx, id)
            plays = Plays.all(ctx)
        }
    }

    BackHandler(full) { full = false }

    fun playList(list: List<Track>, i: Int) {
        val s0 = maxOf(0, i - 100)
        val sub = list.subList(s0, minOf(list.size, s0 + 400))
        ctl?.run { setMediaItems(sub.map(::item), i - s0, 0L); prepare(); play() }
    }

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(Modifier.fillMaxSize().aurora(a1, a2)) {
            if (!ok) {
                Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                    Text("Media Play", style = TextStyle(brush = Rainbow, fontSize = 34.sp, fontWeight = FontWeight.Bold))
                    Text("Permita o acesso às músicas do seu aparelho para começar.", Modifier.padding(vertical = 16.dp))
                    Button(onClick = { ask.launch(arrayOf(perm, Manifest.permission.POST_NOTIFICATIONS)) }) { Text("Permitir") }
                }
            } else {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Text(
                        "Media Play", Modifier.padding(20.dp, 14.dp, 20.dp, 8.dp),
                        style = TextStyle(brush = Rainbow, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            0 -> HomeScreen(tracks, plays, ::playList)
                            1 -> MusicScreen(tracks, cur?.id, ::playList)
                            2 -> ConnectedScreen()
                            else -> SettingsScreen()
                        }
                    }
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .92f))))
                )
                // Mini player + barra de navegação flutuantes
                Column(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (cur != null && now != null && ctl != null) {
                        Row(
                            Modifier.fillMaxWidth().dock(26).clickable { full = true }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Cover(cur.uri, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)), 128)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(cur.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(cur.artist, fontSize = 12.sp, color = Color.White.copy(.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(Modifier.clickable { ctl?.seekToPreviousMediaItem() }.padding(10.dp)) { Glyph("⏮", 18.dp) }
                            Box(Modifier.clickable { if (now.playing) ctl?.pause() else ctl?.play() }.padding(10.dp)) {
                                Glyph(if (now.playing) "⏸" else "▶", 22.dp)
                            }
                            Box(Modifier.clickable { ctl?.seekToNextMediaItem() }.padding(10.dp)) { Glyph("⏭", 18.dp) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().dock(30).padding(6.dp)) {
                        listOf("Início", "Música", "Conectado", "Ajustes").forEachIndexed { i, l ->
                            Box(
                                Modifier.weight(1f).then(if (tab == i) Modifier.glass(24) else Modifier)
                                    .clickable { tab = i }.padding(vertical = 12.dp),
                                Alignment.Center
                            ) { Text(l, color = if (tab == i) Color.White else Color.White.copy(.6f), fontSize = 13.sp, maxLines = 1) }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = full && cur != null && now != null && ctl != null,
                    enter = slideInVertically(tween(350)) { it } + fadeIn(tween(350)),
                    exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(300))
                ) {
                    if (cur != null && now != null && ctl != null) NowPlaying(cur, now, ctl!!, a1, a2) { full = false }
                }
            }
        }
    }
}
