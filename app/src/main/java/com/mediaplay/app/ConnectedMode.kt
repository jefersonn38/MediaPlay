package com.mediaplay.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController as SysController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay

private val APPS = mapOf(
    "com.google.android.apps.youtube.music" to "YouTube Music",
    "deezer.android.app" to "Deezer",
    "com.amazon.mp3" to "Amazon Music",
    "com.spotify.music" to "Spotify",
    "com.apple.android.music" to "Apple Music",
    "com.aspiro.tidal" to "Tidal"
)

class Snap(
    val app: String, val title: String, val artist: String, val album: String,
    val art: Bitmap?, val playing: Boolean, val ctl: SysController
)

/** Lê as sessões de mídia ativas (outros apps tocando). Só funciona com o acesso às notificações ativado. */
fun readSessions(c: Context): List<Snap> = runCatching {
    val m = c.getSystemService(MediaSessionManager::class.java)
    m.getActiveSessions(ComponentName(c, MediaListener::class.java))
        .filter { it.packageName != c.packageName }
        .map { s ->
            val md = s.metadata
            Snap(
                APPS[s.packageName] ?: s.packageName,
                md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Sem título",
                md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
                md?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
                s.playbackState?.state == PlaybackState.STATE_PLAYING,
                s
            )
        }
}.getOrDefault(emptyList())

@Composable
fun ConnectedScreen() {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf(emptyList<Snap>()) }
    LaunchedEffect(Unit) {
        while (true) {
            enabled = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
            list = if (enabled) readSessions(ctx) else emptyList()
            delay(1000)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp, 0.dp, 20.dp, 190.dp)) {
        if (!enabled) {
            Column(Modifier.fillMaxWidth().glass(24).padding(18.dp)) {
                Text("Modo conectado", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Para mostrar e controlar o que toca em outros apps de música (YouTube Music, Deezer, Amazon Music, Spotify), " +
                        "o Android pede acesso às notificações. O Media Play só lê título, artista e capa, e nada sai do seu aparelho. " +
                        "Se o Android bloquear a opção (app instalado fora da Play Store), abra Configurações > Apps > Media Play > menu ⋮ > Permitir configurações restritas.",
                    Modifier.padding(vertical = 12.dp), color = Color.White.copy(.75f)
                )
                Button(onClick = { openSafely(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), Intent(Settings.ACTION_SETTINGS)) }) { Text("Ativar acesso") }
            }
        } else if (list.isEmpty()) {
            Text(
                "Nenhum app tocando agora. Abra o YouTube Music, Deezer, Amazon Music ou Spotify e dê play.",
                color = Color.White.copy(.75f)
            )
        } else list.forEach { s ->
            Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth(.72f).aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF3A2A8F), Color(0xFFD04FD0))))
                ) { s.art?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                Column(Modifier.padding(top = 14.dp).fillMaxWidth().glass(28).padding(18.dp)) {
                    Text("Tocando em ${s.app}", fontSize = 12.sp, color = Color.White.copy(.6f))
                    Text(s.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (s.artist.isNotBlank()) Text(s.artist, color = Color.White.copy(.8f))
                    if (s.album.isNotBlank()) Text(s.album, fontSize = 13.sp, color = Color.White.copy(.6f))
                }
                Row(
                    Modifier.padding(top = 14.dp).fillMaxWidth().glass(40).padding(10.dp),
                    Arrangement.SpaceEvenly, Alignment.CenterVertically
                ) {
                    Round("⏮", 52) { s.ctl.transportControls.skipToPrevious() }
                    Round(if (s.playing) "⏸" else "▶", 72, ring = true) {
                        if (s.playing) s.ctl.transportControls.pause() else s.ctl.transportControls.play()
                    }
                    Round("⏭", 52) { s.ctl.transportControls.skipToNext() }
                }
            }
        }
    }
}
