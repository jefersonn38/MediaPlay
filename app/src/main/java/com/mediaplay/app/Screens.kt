package com.mediaplay.app

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay

// ---------- Início: Mais tocadas + álbuns sanfonados ----------
@Composable
fun Tile(t: Track, rank: Int, mod: Modifier, onClick: () -> Unit) {
    Box(mod.aspectRatio(1f).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Cover(t.uri, Modifier.fillMaxSize(), 256)
        Text("$rank", Modifier.padding(6.dp).glass(11).padding(8.dp, 2.dp), fontSize = 11.sp)
        Text(
            t.title,
            Modifier.align(Alignment.BottomCenter).padding(6.dp).fillMaxWidth().glass(12).padding(8.dp, 5.dp),
            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HomeScreen(tracks: List<Track>, plays: Map<Long, Int>, onPlay: (List<Track>, Int) -> Unit) {
    if (tracks.isEmpty()) {
        Text("Nenhuma música encontrada neste aparelho.", Modifier.padding(20.dp), color = Color.White.copy(.7f))
        return
    }
    var n by remember { mutableIntStateOf(10) }
    var sel by remember { mutableIntStateOf(0) }
    val top = remember(tracks, plays, n) {
        tracks.filter { (plays[it.id] ?: 0) > 0 }.sortedByDescending { plays[it.id] ?: 0 }.take(n)
    }
    val albums = remember(tracks) { tracks.groupBy { it.album }.toList().filter { it.first.isNotBlank() }.take(6) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 200.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp, 8.dp, 20.dp, 10.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Text("Mais tocadas", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10, 20).forEach { v ->
                    Text(
                        "$v",
                        Modifier.clip(RoundedCornerShape(12.dp)).then(if (n == v) Modifier.glass(12) else Modifier)
                            .clickable { n = v }.padding(12.dp, 4.dp),
                        fontSize = 12.sp, color = if (n == v) Color.White else Color.White.copy(.6f)
                    )
                }
            }
        }
        if (top.isEmpty()) {
            Text(
                "Ouça algumas músicas (mais de 30 segundos) e as mais tocadas aparecem aqui.",
                Modifier.padding(horizontal = 20.dp), color = Color.White.copy(.6f)
            )
        } else {
            val pages = top.chunked(9)
            val pager = rememberPagerState(pageCount = { pages.size })
            HorizontalPager(state = pager, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp) { p ->
                val page = pages.getOrNull(p) ?: emptyList()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    page.chunked(3).forEachIndexed { r, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEachIndexed { c, t ->
                                val idx = p * 9 + r * 3 + c
                                Tile(t, idx + 1, Modifier.weight(1f)) { onPlay(top, idx) }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), Arrangement.Center) {
                repeat(pages.size) { i ->
                    val w by animateDpAsState(if (pager.currentPage == i) 20.dp else 8.dp, label = "dot")
                    Box(
                        Modifier.padding(horizontal = 4.dp).height(8.dp).width(w).clip(CircleShape)
                            .background(if (pager.currentPage == i) Color.White else Color.White.copy(.3f))
                    )
                }
            }
        }
        if (albums.isNotEmpty()) {
            Text("Seus álbuns", Modifier.padding(20.dp, 24.dp, 20.dp, 10.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(270.dp), Arrangement.spacedBy(8.dp)) {
                albums.forEachIndexed { i, (name, list) ->
                    val w by animateFloatAsState(if (i == sel) 5f else 1f, tween(450), label = "w")
                    Box(
                        Modifier.weight(w).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                            .clickable { if (sel == i) onPlay(list, 0) else sel = i }
                    ) {
                        Cover(list.first().uri, Modifier.fillMaxSize(), 512)
                        if (i == sel) {
                            Column(Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth().glass(20).padding(12.dp, 10.dp)) {
                                Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(list.first().artist, fontSize = 12.sp, color = Color.White.copy(.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Música: pesquisa + Faixas / Álbuns (discos) / Artistas ----------
@Composable
fun TrackRow(t: Track, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White.copy(.10f) else Color.Transparent)
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(t.uri, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), 128)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, fontSize = 12.sp, color = Color.White.copy(.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(fmt(t.dur), fontSize = 12.sp, color = Color.White.copy(.6f))
    }
}

@Composable
fun MusicScreen(tracks: List<Track>, curId: Long?, onPlay: (List<Track>, Int) -> Unit) {
    var q by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(1) }
    val list = remember(tracks, q) {
        if (q.isBlank()) tracks else tracks.filter { (it.title + " " + it.artist + " " + it.album).contains(q, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("Pesquisar músicas, artistas, álbuns…") },
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass(16),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
            )
        )
        Row(Modifier.padding(20.dp, 12.dp, 20.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            listOf("Faixas", "Álbuns", "Artistas").forEachIndexed { i, l ->
                Text(
                    l, Modifier.clickable { mode = i }.padding(vertical = 4.dp),
                    color = if (mode == i) Color.White else Color.White.copy(.55f),
                    fontWeight = if (mode == i) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
        if (list.isEmpty()) {
            Text("Nada encontrado.", Modifier.padding(20.dp), color = Color.White.copy(.6f))
        } else when (mode) {
            0 -> LazyColumn(contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 200.dp)) {
                items(list.size, key = { list[it].id }) { i ->
                    TrackRow(list[i], list[i].id == curId) { onPlay(list, i) }
                }
            }
            1 -> {
                val groups = remember(list) { list.groupBy { it.album }.toList() }
                LazyVerticalGrid(
                    GridCells.Fixed(2), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 200.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(groups) { (name, tr) ->
                        Column(Modifier.clickable { onPlay(tr, 0) }) {
                            Sleeve(tr.first().uri, false, Modifier.fillMaxWidth().aspectRatio(1.28f))
                            Text(name.ifBlank { "Sem álbum" }, Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tr.first().artist, fontSize = 12.sp, color = Color.White.copy(.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            else -> {
                val artists = remember(list) { list.groupBy { it.artist }.toList() }
                LazyColumn(contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 200.dp)) {
                    items(artists.size) { i ->
                        val (name, tr) = artists[i]
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPlay(tr, 0) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Cover(tr.first().uri, Modifier.size(48.dp).clip(CircleShape), 128)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${tr.size} faixas", fontSize = 12.sp, color = Color.White.copy(.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Tela de reprodução ----------
@Composable
fun NowPlaying(t: Track, n: Now, p: Player, c1: Color, c2: Color, close: () -> Unit) {
    var pos by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var fav by remember(t.id) { mutableStateOf(false) }
    LaunchedEffect(t.id) { while (true) { pos = p.currentPosition; delay(400) } }
    Column(
        Modifier.fillMaxSize().aurora(c1, c2).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) { Round("▾", 44, onClick = close) }
        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
            Sleeve(t.uri, n.playing, Modifier.fillMaxWidth(.9f).aspectRatio(1.28f))
        }
        Column(Modifier.fillMaxWidth().glass(28).padding(18.dp)) {
            Text(t.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(t.artist, color = Color.White.copy(.8f))
            if (t.album.isNotBlank()) Text(t.album, fontSize = 13.sp, color = Color.White.copy(.6f))
        }
        Slider(
            value = drag ?: pos.toFloat(), onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { p.seekTo(it.toLong()) }; drag = null },
            valueRange = 0f..t.dur.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Color.White.copy(.2f))
        )
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(fmt(pos), fontSize = 12.sp, color = Color.White.copy(.6f))
            Text(fmt(t.dur), fontSize = 12.sp, color = Color.White.copy(.6f))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp).glass(40).padding(10.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Round(if (fav) "★" else "☆", 48) { fav = !fav }
            Round("⏮", 48) { p.seekToPreviousMediaItem() }
            Orb(n.playing, 76.dp) { if (n.playing) p.pause() else p.play() }
            Round("⏭", 48) { p.seekToNextMediaItem() }
            Round("⇄", 48, active = n.shuffle) { p.shuffleModeEnabled = !n.shuffle }
        }
        Spacer(Modifier.height(12.dp))
    }
}
