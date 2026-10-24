# GoogleUnlimited — Android

Port Android (Native Kotlin + Jetpack Compose) dari watcher desktop
`GoogleUnlimited`. Ringan & cepat: tanpa bundel binary di APK, tanpa library
gambar/jaringan tambahan, database SQLite lokal yang skemanya sama dengan
versi desktop (`uploads` / `retry_queue` / `logs`).

## Yang dilakukan aplikasi ini

- **Pindai** foto & video lewat MediaStore (Camera, Pictures, Downloads,
  Movies) — scoped storage, tanpa akses semua file.
- **Filter** sama seperti desktop: 41 format, minimal 10 KB, file yang baru
  diubah < 60 detik dilewati dulu (masih ditulis).
- **Dedup** nama + ukuran + SHA-256 (streaming, jadi video besar tidak
  memenuhi RAM).
- **Upload** lewat `gotohp-cli` (mesin dari `xob0t/gotohp`), perintah yang
  sama seperti desktop: `gotohp-cli --config gotohp.config upload --no-tui <file>`.
- **Retry** maksimal 3x, lalu dicatat gagal (sama seperti desktop).
- **Sync latar** tiap 15 menit (WorkManager) bila diaktifkan, dengan opsi
  hanya Wi-Fi.

## Cara build (GitHub Actions)

Push ke `main` → workflow `.github/workflows/android.yml` otomatis build
`app-debug.apk` dan menyimpannya sebagai artifact
`googleunlimited-android-debug`. Unduh dari tab Actions → run terbaru →
Artifacts. APK debug ini bisa langsung diinstal (sideload); tidak ditandatangani
rilis.

Lokal (butuh Android SDK + JDK 17):

```sh
cd android
gradle assembleDebug
```

## Cara pakai di HP

1. Instal APK, izinkan akses foto/video.
2. Tab **Akun & Log** → **Unduh / Perbarui Engine**. Aplikasi mengunduh
   `gotohp-cli_arm64` v0.11.0 dari rilis resmi `xob0t/gotohp` dan menampilkan
   SHA-256-nya. Status engine harus `OK`, kalau tidak, binary Linux itu tidak
   bisa jalan di HP tersebut dan upload tidak akan berjalan — aplikasi akan
   mengatakannya apa adanya, bukan pura-pura sync.
3. Ambil `oauth_token` dari browser komputer (akun Google yang sama):
   buka halaman Embedded Setup Google → login → **I agree** (halaman akan
   loading terus, itu normal) → DevTools → Cookies `accounts.google.com` →
   salin `oauth_token` → tempel di aplikasi → **Simpan**.
4. Kembali ke **Beranda** → **Sinkronkan Sekarang**.

## Yang TIDAK dijanjikan aplikasi ini

- **Bukan produk Google**, bukan aplikasi resmi Google Photos.
- **"Unlimited" bukan jaminan.** Mesinnya meniru perangkat Pixel; Google bisa
  mengubah kebijakan, endpoint, atau kriteria perangkat kapan saja, dan akun
  bisa kena pembatasan. Pakai akun yang risikonya dipahami, dan tetap simpan
  backup foto penting di tempat lain.
- **Token tidak pernah** ditulis ke log, prefs, atau dikirim ke server pihak
  ketiga — hanya diteruskan ke `gotohp.config` di penyimpanan privat aplikasi.
- Binary arm64 tidak punya checksum resmi dari upstream, jadi aplikasi tidak
  mengklaim "terverifikasi" — SHA-256 ditampilkan agar bisa dibandingkan
  manual dengan halaman rilis.

## Kredit

- Proyek asal: `shishir0x/GoogleUnlimited`
- Mesin upload: `xob0t/gotohp` (GPLv3)
