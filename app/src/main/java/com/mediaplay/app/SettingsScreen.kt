package com.mediaplay.app

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Abre uma tela do sistema; se o aparelho não tiver, abre a alternativa. */
fun openSafely(c: Context, main: Intent, fallback: Intent) {
    try { c.startActivity(main) } catch (_: Exception) { runCatching { c.startActivity(fallback) } }
}

/** Mostra onde o som está saindo agora: Bluetooth, fone com fio ou alto-falante. */
fun currentOutput(c: Context): String {
    val am = c.getSystemService(AudioManager::class.java)
    val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        ?.let { return it.productName?.toString()?.takeIf { n -> n.isNotBlank() } ?: "Dispositivo Bluetooth" }
    if (outs.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_USB_HEADSET })
        return "Fone com fio"
    return "Alto-falante do celular"
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var out by remember { mutableStateOf(currentOutput(ctx)) }
    LaunchedEffect(Unit) { while (true) { out = currentOutput(ctx); delay(1500) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp, 0.dp, 20.dp, 190.dp)) {
        Column(Modifier.fillMaxWidth().glass(24).padding(18.dp)) {
            Text("Caixa de som e fone", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Saída atual: $out", Modifier.padding(top = 8.dp, bottom = 14.dp), color = Color.White.copy(.8f))
            Button(onClick = { openSafely(ctx, Intent(Settings.Panel.ACTION_VOLUME), Intent(Settings.ACTION_SOUND_SETTINGS)) }) { Text("Escolher saída de áudio") }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { openSafely(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), Intent(Settings.ACTION_SETTINGS)) }) { Text("Parear caixa ou fone Bluetooth") }
            Text(
                "O codec Bluetooth (AAC, aptX, LDAC) é escolhido pelo Android e pelo fone. O Media Play entrega o áudio no melhor formato que o aparelho aceitar.",
                Modifier.padding(top = 14.dp), fontSize = 12.sp, color = Color.White.copy(.6f)
            )
        }
    }
}
