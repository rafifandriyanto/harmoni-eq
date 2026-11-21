## Unduh Aplikasi (Tanpa Clone)

Pengguna dapat langsung mengunduh file APK terbaru melalui halaman Releases:

- [Unduh Harmoni EQ APK Terbaru](https://github.com/rafifandriyanto/harmoni-eq/releases/latest)

---

## Design System — Restrained Glass UI

Harmoni EQ menggunakan sistem tampilan **restrained glass UI** yang memprioritaskan keterbacaan, kontras WCAG AA, dan performa 60fps saat drag fader.

### Token Utama (`ui/theme/GlassTokens.kt`)

| Token | Nilai | Penggunaan |
|---|---|---|
| Canvas Dark / Light | `#0E1013` / `#F5F2EE` | Latar belakang utama + 1 radial spot statis |
| Glass Fill Dark / Light | White `0.08f` / White `0.62f` | Hanya UI chrome pada API 31+ |
| Near-Solid Surface | `0.90f` opacity | Permukaan pembawa teks (judul dialog, isi sheet, bar) |
| Reduce Transparency | `0.95f` solid + `1.dp` border | Aktif saat toggle Reduce Transparency menyala |
| Border & Inner Highlight | `1.dp` white `0.18f` / inset top `0.10f` | Batas permukaan glass |
| Corner Radius | `20.dp` | Konsisten di semua panel & chrome |
| Accent Default (Amber) | `#D48B57` (dark) / `#9A5728` (light) | Fader aktif, tombol utama, border slot A/B |
| Accent Alternatif (Sage) | `#7FA385` (dark) / `#436649` (light) | Dapat dipilih atau dikustom lewat hex di Pengaturan |
| Dialog Scrim | Solid `40%` hitam | Tanpa blur pada scrim |
| Skala Spasi | `4 / 8 / 12 / 16 / 24 / 32.dp` | Seluruh padding & jarak antar elemen |

### Verifikasi Kontras (Computed WCAG AA)

| Pasangan | Rasio | Status |
|---|---|---|
| Dark Primary Text (`#F2EFEA`) di atas Canvas (`#0E1013`) | `16.61:1` | Lulus AA |
| Dark Primary Text (`#F2EFEA`) di atas Glass terterang | `10.67:1` | Lulus AA |
| Dark Muted Text (`#96928B`) di atas Near-Solid | `5.62:1` | Lulus AA |
| Dark Accent Amber (`#D48B57`) di atas Near-Solid | `6.32:1` | Lulus AA |
| Light Primary Text (`#1A1917`) di atas Canvas (`#F5F2EE`) | `15.74:1` | Lulus AA |
| Light Primary Text (`#1A1917`) di atas Glass tergelap | `16.05:1` | Lulus AA |
| Light Muted Text (`#66625C`) di atas Near-Solid | `5.66:1` | Lulus AA |
| Light Accent Amber (`#9A5728`) di atas Near-Solid | `5.21:1` | Lulus AA |

### Aturan Pakai
1. **Glass khusus UI chrome:** hanya dipakai pada `GlassTopBar`, `GlassBottomSheet`, `GlassDialog`, `GlassPrimaryPillButton`, dan `GlassAbToolbar`.
2. **Dilarang untuk konten:** track fader, kartu konten (`ConsolePanel`), daftar preset, dan form pengaturan wajib menggunakan permukaan solid/near-solid.
3. **Maksimal 2 permukaan blur per layar:** saat sheet/dialog terbuka, top bar otomatis turun menjadi near-solid.
4. **Aksesibilitas:** menghormati system dark mode, mendukung kustomisasi warna aksen dengan pembatasan saturasi otomatis, serta menyediakan toggle **Reduce transparency** dan **Reduce motion** (tersimpan di DataStore terpisah `harmoni_theme_prefs_v1`).

### Batasan Performa & Benchmark
- Blur hanya memakai `RenderEffect.createBlurEffect` pada Android 12+ (API 31+) di layer backdrop panel (dilarang `Modifier.blur()`).
- API < 31 otomatis memakai fallback translucent `0.85f` tanpa real-time blur.
- Latar belakang radial spot di-pre-render sekali menjadi `ImageBitmap` statis (`StaticRadialBackdrop.kt`).
- Selama drag fader atau scroll daftar, `RenderEffect` dimatikan sementara (`LocalGlassBlurSuppressed`) dan aktif kembali setelah idle `150ms`.

#### Catatan Benchmark FPS Drag Fader
Jalankan perintah berikut sebelum dan sesudah patch saat menggeser fader selama 10 detik:

```bash
adb shell dumpsys gfxinfo com.aistudio.harmonieq.vqrzkm reset
# Geser fader EQ naik-turun selama 10 detik
adb shell dumpsys gfxinfo com.aistudio.harmonieq.vqrzkm framestats
```

| Kondisi | Janky Frames (%) | 90th Percentile (ms) | Target |
|---|---|---|---|
| Sebelum (baseline) | _isi hasil perangkat_ | _isi hasil perangkat_ | <= 16.6 ms |
| Sesudah (Restrained Glass UI) | _isi hasil perangkat_ | _isi hasil perangkat_ | 60 fps stabil |
