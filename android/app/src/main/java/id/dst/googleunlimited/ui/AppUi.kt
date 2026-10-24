package id.dst.googleunlimited.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dst.googleunlimited.GuViewModel
import id.dst.googleunlimited.data.formatSize

private val Green = Color(0xFF1A7F37)
private val SoftGreen = Color(0xFFE6F4EA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUi(vm: GuViewModel, onRequestPermissions: () -> Unit) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GoogleUnlimited", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Green, titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Beranda") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Antrian") })
                FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Akun & Log") })
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (tab == 0) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("Terupload", state.uploadedCount.toString(), Modifier.weight(1f))
                            StatCard("Antrian", state.queue.size.toString(), Modifier.weight(1f))
                            StatCard("Retry", state.retryCount.toString(), Modifier.weight(1f))
                        }
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = SoftGreen)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Sinkronisasi", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Memindai Camera, Pictures, Downloads & Movies lewat MediaStore. " +
                                        "File yang baru diubah < 60 detik dilewati dulu supaya tidak terupload setengah-setengah. " +
                                        "Duplikat dicek pakai nama + ukuran + SHA-256, persis seperti versi desktop.",
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                if (state.isSyncing) {
                                    Text(state.progressText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "${state.syncedThisRun} berhasil • ${state.failedThisRun} gagal di sesi ini",
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(onClick = { vm.stopSync() }) { Text("Hentikan") }
                                } else {
                                    Button(
                                        onClick = { onRequestPermissions(); vm.startSync() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("▶  Sinkronkan Sekarang") }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { onRequestPermissions(); vm.scanOnly() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Pindai Saja (tanpa upload)") }
                                }
                            }
                        }
                    }
                    item {
                        Card {
                            Column(Modifier.padding(16.dp)) {
                                Text("Otomatis", fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Hanya lewat Wi-Fi", Modifier.weight(1f), fontSize = 14.sp)
                                    Switch(state.wifiOnly, onCheckedChange = { vm.setWifiOnly(it) })
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Sync latar tiap 15 menit", Modifier.weight(1f), fontSize = 14.sp)
                                    Switch(state.autoSync, onCheckedChange = { vm.setAutoSync(it) })
                                }
                                Text(
                                    "Sync latar butuh jaringan & engine terpasang. Android bisa menunda job saat hemat baterai — itu normal.",
                                    fontSize = 12.sp, color = Color.Gray
                                )
                            }
                        }
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5))) {
                            Column(Modifier.padding(16.dp)) {
                                Text("⚠️ Baca dulu, ayah", fontWeight = FontWeight.Bold)
                                Text(
                                    "Ini utilitas TIDAK RESMI, bukan dari Google. Mesinnya meniru perangkat Pixel " +
                                        "lewat gotohp — Google bisa mengubah kebijakan / mencabut kuota kapan saja, dan akun " +
                                        "bisa kena pembatasan. Jangan jadikan ini satu-satunya backup foto penting. " +
                                        "Token hanya disimpan privat di HP ini, tidak dikirim ke server siapa pun.",
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
                if (tab == 1) {
                    item {
                        Text(
                            "Antrian (${state.queue.size}) — yang sudah terupload tidak tampil di sini.",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (state.queue.isEmpty()) {
                        item { Text("Antrean kosong. Tekan “Pindai Saja” di Beranda.", fontSize = 14.sp) }
                    } else {
                        items(state.queue.take(200)) { item ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.name.firstOrNull()?.uppercase() ?: "?",
                                    modifier = Modifier.size(36.dp),
                                    fontWeight = FontWeight.Bold, color = Green
                                )
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(item.name, fontSize = 14.sp, maxLines = 1)
                                    Text(formatSize(item.size), fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                    item { Text("Terakhir terupload", fontWeight = FontWeight.Bold) }
                    items(state.recent) { row ->
                        Column {
                            Text(row.fileName, fontSize = 14.sp, maxLines = 1)
                            Text("${row.uploadedAt} • ${formatSize(row.fileSize)}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
                if (tab == 2) {
                    item {
                        Card {
                            Column(Modifier.padding(16.dp)) {
                                Text("Engine gotohp-cli", fontWeight = FontWeight.Bold)
                                Text("Status: ${state.engineStatus}", fontSize = 13.sp)
                                state.engineSha?.let {
                                    Text("SHA-256: $it", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Engine diunduh dari rilis resmi xob0t/gotohp v0.11.0 (arm64). " +
                                        "Upstream tidak mempublikasikan checksum arm64, jadi aplikasi TIDAK mengklaim " +
                                        "“terverifikasi” — bandingkan SHA di atas dengan halaman rilis bila ragu.",
                                    fontSize = 12.sp, color = Color.Gray
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { vm.downloadEngine() }, enabled = !state.isBusy) {
                                    Text("Unduh / Perbarui Engine")
                                }
                            }
                        }
                    }
                    item {
                        Card {
                            Column(Modifier.padding(16.dp)) {
                                Text("Akun Google (oauth_token)", fontWeight = FontWeight.Bold)
                                Text(
                                    "Cara dapat token: di browser komputer, buka halaman Embedded Setup Google, " +
                                        "login → I agree (halaman akan loading terus, itu normal) → DevTools → Cookies " +
                                        "accounts.google.com → salin oauth_token → tempel di sini. Di HP, cara termudah " +
                                        "adalah ambil token dari browser komputer dengan akun yang sama.",
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = token,
                                    onValueChange = { token = it },
                                    label = { Text("oauth_token") },
                                    singleLine = true,
                                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row {
                                    TextButton(onClick = { showToken = !showToken }) {
                                        Text(if (showToken) "Sembunyikan" else "Tampilkan")
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Button(
                                        onClick = { vm.saveToken(token); token = "" },
                                        enabled = token.isNotBlank() && !state.isBusy
                                    ) { Text("Simpan") }
                                }
                                OutlinedButton(onClick = { vm.listAccounts() }) { Text("Lihat Akun Tersimpan") }
                                if (state.accountText.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(state.accountText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                    item { Text("Log", fontWeight = FontWeight.Bold) }
                    items(state.logs) { log ->
                        Text(
                            "[${log.timestamp}] ${log.level}: ${log.message}",
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                }
                item {
                    if (state.message.isNotBlank()) {
                        Text(state.message, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    if (state.isBusy) Text("Bekerja…", fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Fork dari shishir0x/GoogleUnlimited • Engine: xob0t/gotohp (GPLv3) • " +
                            "Dibuat untuk pemakaian pribadi, bukan produk Google.",
                        fontSize = 11.sp, color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Green)
            Text(title, fontSize = 12.sp, color = Color.Gray)
        }
    }
}
