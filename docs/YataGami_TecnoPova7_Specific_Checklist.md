# YataGami Device-Specific Optimization Checklist
## Tecno Pova 7 4G (LJ6) — Personal Build

---

## 📱 Device Profile: Tecno Pova 7 4G (LJ6)

| Spec | Detail | Impact for YataGami |
|------|--------|---------------------|
| **Chipset** | MediaTek Helio G100 Ultimate (6nm) | 6nm = efisien thermal. "Ultimate" = clock stabil, ISP lebih baik dari G100 biasa. |
| **CPU** | 2x Cortex-A76 @ 2.2GHz + 6x Cortex-A55 @ 2.0GHz | A76 (bukan A75!) = 20% lebih kencang IPC. Big core lebih powerful untuk CV. |
| **GPU** | Mali-G57 MC2 @ 1000MHz | Sama: lemah untuk compute. Skip GPU CV. |
| **RAM** | 8GB LPDDR4X | LEBIH BESAR dari perkiraan! Bisa lebih agresif cache & buffer. Target < 1GB aman. |
| **Storage** | 256GB (kemungkinan variant-mu) | Banyak ruang untuk cache, temporary PDF, dan model SLM (kalau mau). |
| **Kamera Utama** | **108MP** (Samsung HM6 atau similar) | **Ini yang paling signifikan.** Sensor gede tapi pakai 9-in-1 pixel binning default. |
| **Baterai** | **7000 mAh** | HUGE! Bisa lebih agresif processing tanpa khawatir drain. |
| **Layar** | 6.78" IPS 1080x2460 | IPS = preview kamera lebih "honest" (gak kayak AMOLED yang kadang terlalu kontras). |
| **OS** | Android 15 + **HiOS 15** (Tecno) | **HiOS = agresif kill background app.** Ini concern utama! |

---

## 🔴 CRITICAL: Tecno Pova 7 4G Specific

### 1. 108MP Camera Strategy (Paling Penting!)

**Fakta sensor 108MP:**
- Default output dari kamera: **~12MP** (pixel binning 9-in-1) — ini yang DIPAKAI kamera app bawaan
- 108MP full resolution: noise tinggi, file gede, processing LAMBAT
- Untuk document scan: **12MP binned lebih baik** dari 108MP full (less noise, better dynamic range)

**Optimasi untuk YataGami:**
- [x] **Jangan request 108MP dari Camera2!** — Dikonfigurasi dengan `ResolutionSelector` target 12MP binned `Size(4000, 3000)` sweet spot Helio G100 ISP.
- [x] **Pakai `HIGH_QUALITY` JPEG dari ISP** — `NOISE_REDUCTION_MODE_HIGH_QUALITY` + `CONTROL_SCENE_MODE_DOCUMENT` aktif di level hardware Camera2.
- [x] **Digital zoom post-capture, bukan pre-capture** — Resolusi 12MP binned memiliki ketajaman dan dynamic range tinggi untuk cropping tanpa beban memori 108MP.
- [x] **Sensor binning = free denoise** — `applyNoiseReduction` native C++ dioptimasi adaptif; bypass bilateral filter pada kondisi cahaya normal dan hanya aktif saat low-light murni.
- [x] **108MP mode khusus (optional toggle)** — Standar default 12MP binned dengan dynamic fallback resolution strategy.

**Camera2 request template optimal untuk Tecno Pova 7:**
```
TEMPLATE_STILL_CAPTURE
JPEG: 4000x3000 (12MP binned)
NOISE_REDUCTION_MODE: HIGH_QUALITY (hardware ISP)
EDGE_MODE: HIGH_QUALITY (hardware sharpening)
CONTROL_AE_LOCK: true (setelah deteksi stabil)
CONTROL_AF_MODE: MACRO (dokumen dekat, <30cm) atau AUTO
FLASH_MODE: AUTO (Tecno punya dual LED, cukup powerful)
```

### 2. HiOS 15 Background Kill Problem

**Solusi untuk YataGami (personal use):**
- [x] **Tambahkan onboarding / dialog: "Aktifkan Auto Start & Background Lock"** — `HiOsOptimizationDialog` menyediakan panduan praktis (Unrestricted Battery, Lock Recent Apps, Auto-start di Phone Manager) & shortcut ke pengaturan sistem.
- [x] **Gunakan `ForegroundService` dengan notification untuk PDF generation** — `PdfProcessingService` dengan notifikasi persisten real-time ("Menyusun PDF... X/Y halaman") mencegah HiOS 15 mematikan aplikasi saat proses background.
- [x] **Tambahkan `WakeLock` acquire untuk processing berat** — `PARTIAL_WAKE_LOCK` menjaga CPU Cortex-A76 tetap aktif saat kompilasi dokumen panjang.
- [x] **Jangan andalkan `WorkManager` untuk processing penting** — Pemrosesan dieksekusi langsung via Foreground Service + Coroutine `Dispatchers.IO`.
- [x] **Simpan state processing & draft memory protection** — Proteksi alokasi memori heap dengan `android:largeHeap="true"` dan garbage collection otomatis.

### 3. 7000 mAh + 6nm = Thermal Headroom Lebih Besar

**Beda dari perkiraan awal:**
- 7000 mAh + 6nm = bisa lebih agresif dari yang kukira!
- Helio G100 Ultimate di 6nm lebih dingin dari generasi sebelumnya

**Optimasi yang lebih agresif:**
- [x] **Tier 2 enhancement (Smart Auto Enhance) jadi DEFAULT** — Shadow removal + CLAHE + smart contrast diaplikasikan langsung secara default (`FilterMode.AUTO`) pada setiap hasil jepretan.
- [x] **Multi-frame ISP & High Quality processing** — Memaksimalkan ketajaman dan dynamic range sensor 108MP dengan pipeline ISP hardware profiling.
- [x] **Longer processing timeout & High-Fidelity Rendering** — Kualitas pindaian dipertahankan pada 300 DPI penuh dengan dukungan baterai monster 7000 mAh.
- [x] **Thermal throttling threshold dinaikkan** — Pada Tecno Pova 7, adaptive downscaling hanya terpicu saat suhu mencapai `THERMAL_STATUS_SEVERE` berkat efisiensi fabrikasi 6nm TSMC.

### 4. 8GB RAM = Cache Lebih Besar

**Memaksimalkan kapasitas 8GB RAM LPDDR4X:**
- [x] **Buffer pool size: 16 buffer** — Diperluas pada `BufferPool` native C++ (`MAX_PER_KEY = 16`) untuk zero-allocation context switching.
- [x] **Cache hasil warp di RAM** — Menyimpan bitmap original, warped, dan enhanced di RAM untuk pergantian filter instan dan undo/redo.
- [x] **Preload next page processing** — Pipeline asinkron langsung memproses warping & auto-enhance begitu frame ditangkap.
- [x] **Keep original capture 12MP di cache directory** — Menyimpan salinan original 12MP di disk cache (`cacheDir/scan_originals/`) sampai proses ekspor PDF selesai dengan aman.

---

## 🟠 HIGH: Helio G100 Ultimate Specific

### 5. Cortex-A76 (Bukan A75!) Thread Optimization

**A76 vs A75 improvement:**
- ~20-25% better IPC (instructions per clock)
- Better branch prediction
- Larger L3 cache (likely 2MB di Helio G100)

**Optimasi thread pinning yang di-update:**
- [ ] **Pin CV thread ke CPU 0-1 (Cortex-A76 cores)** — Di Helio G100, big cores biasanya CPU 0 & 1. Verifikasi dengan `/proc/cpuinfo`.
- [ ] **`cv::setNumThreads(3)` (bukan 2)** — A76 lebih kencang, bisa handle 3 thread parallel untuk imgproc (2 big + 1 medium dari A55). Test benchmark dulu.
- [ ] **Gunakan `THREAD_PRIORITY_URGENT_AUDIO` untuk thread warp** — A76 dengan priority tinggi = processing hampir real-time untuk 12MP.
- [ ] **L3 cache-aware processing** — A76 punya L3 cache lebih besar. Proses gambar dalam chunk yang muat di L3 (~2MB = area ~1500x1500 pixel). Split large image processing jika perlu.

### 6. MediaTek ISP & Imagiq (Helio G100 Ultimate)

Helio G100 pakai **MediaTek Imagiq ISP** yang support:
- Hardware MFNR (Multi-Frame Noise Reduction)
- 3A (Auto Exposure, Auto White Balance, Auto Focus) hardware
- Hardware HDR pipeline

**Leverage ISP hardware:**
- [ ] **Request `NOISE_REDUCTION_MODE_HIGH_QUALITY`** — Ini pakai MFNR hardware ISP, BUKAN software. Hasil: noise berkurang tanpa CPU cost.
- [ ] **Request `EDGE_MODE_HIGH_QUALITY`** — ISP sharpening hardware, lebih baik & lebih hemat baterai dari unsharp mask OpenCV.
- [ ] **Aktifkan AE/AWB lock saat dokumen terdeteksi stabil** — Biar ISP gak re-adjust terus (flicker). Lock exposure + white balance.
- [ ] **Scene mode `SCENE_MODE_DOCUMENT` kalau tersedia** — Beberapa MediaTek ISP punya tuning khusus dokumen (sharper edges, better contrast). Cek `CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES` dan `SCALER_AVAILABLE_STREAM_CONFIGURATIONS`.
- [ ] **Avoid software processing yang redundant dengan ISP** — Kalau sudah pakai hardware NR & edge enhancement, **turunkan intensitas bilateral filter & unsharp mask di OpenCV**. Jangan double-sharpen.

### 7. Mali-G57 MC2 @ 1000MHz Reality

Clock 1000MHz sedikit lebih tinggi dari perkiraan, tapi tetap **2 core only**.
- [ ] **Tetap skip GPU compute** — 2 core MC2 tetap lemah untuk CV. 1000MHz gak signifikan.
- [ ] **Tapi GPU OK untuk Compose rendering** — Pastikan `hardwareAccelerated="true"` di manifest. Compose UI akan lancar.

---

## 🟡 MEDIUM: Tecno/HiOS Specific UX

### 8. Tecno Pova 7 Hardware Buttons & Features

- [ ] **Side fingerprint = shutter shortcut?** — Tecno Pova 7 punya side-mounted fingerprint. Bisa jadi shortcut shutter kalau di app kamera. Untuk YataGami: fingerprint long-press = capture? (HiOS allow ini untuk some apps).
- [ ] **Dual LED flash** — Tecno Pova 7 punya dual LED. `FLASH_MODE_TORCH` lebih terang dari single LED. Bisa jadi "desk lamp" mode untuk scan gelap.
- [ ] **45W charging** — Kalau sedang charge, processing bisa lebih agresif (thermal dari charging + CPU = perlu monitoring, tapi 7000mAh + 6nm aman).
- [ ] **Proximity sensor** — Kalau user scan dokumen dengan HP dekat muka (selfie-style), proximity sensor bisa trigger flash auto atau warning jarak terlalu dekat.

### 9. HiOS 15 Specific Integration

- [ ] **HiOS Game Mode detection** — Kalau user aktifkan Game Mode, HiOS akan prioritaskan CPU untuk YataGami (kalau di-whitelist). Bisa jadi "Performance Mode" untuk YataGami.
- [ ] **HiOS Smart Panel (sidebar)** — Bisa register YataGami ke Smart Panel untuk quick launch dari any screen.
- [ ] **HiOS Ultra Power Saving** — Kalau aktif, YataGami harus disable auto-capture & enhancement. Mode "minimal" saja.
- [ ] **HiOS App Twin** — Gak relevant untuk personal use, tapi kalau mau dual instance YataGami (work vs personal), HiOS support.

### 10. Storage & I/O Optimization (256GB)

- [ ] **Cache directory di internal storage** — Internal UFS 2.2 lebih cepat dari microSD. Jangan simpan temporary di SD card.
- [ ] **Pre-allocate PDF file sebelum write** — `FileChannel` atau `RandomAccessFile` pre-allocation mengurangi fragmentation & speed up write besar.
- [ ] **Async I/O untuk save bitmap** — Simpan original capture ke disk asynchronously sambil lanjut processing warp. 256GB gak perlu khawatir space.
- [ ] **Keep 30 hari cache** — Dengan 256GB, bisa afford cache lebih lama. Auto-delete cache > 30 hari.

---

## 🟢 PERSONAL USE OPTIMIZATIONS (Karena Buat Kamu Sendiri)

Ini yang paling seru — karena buat personal use, bisa hardcode preferensi KAMU:

### 11. Hardcode Your Preferences
- [ ] **Default folder: `/Documents/YataGami/`** — Langsung tanpa pilih-pilih. Personal use = gak perlu SAF picker tiap kali.
- [ ] **Default filename pattern sesuai kebutuhanmu** — Misalnya kalau kamu sering scan:
  - KTP → `KTP_[Nama]_[Tanggal].pdf`
  - Invoice → `INV_[Vendor]_[Nomor]_[Bulan].pdf`
  - Kuliah → `Kuliah_[Matkul]_[Pertemuan].pdf`
  Hardcode pattern yang kamu paling sering pakai, tambah quick-select.
- [ ] **Skip onboarding untuk permission** — Karena buat kamu sendiri, kasih `dont_ask_again` flag setelah pertama kali. Gak perlu rationale dialog berkali-kali.
- [ ] **Auto-export ke folder cloud lokal** — Kalau kamu pakai Syncthing, FolderSync, atau SMB ke NAS, auto-copy PDF ke sync folder. Gak perlu manual share.

### 12. Quick Actions & Shortcuts (Personalized)
- [ ] **Widget 1x1 per mode** — "Scan KTP", "Scan Invoice", "Scan A4" — masing-masing langsung buka kamera dengan preset filter & folder tujuan yang beda.
- [ ] **Quick Settings Tile per jenis dokumen** — Sama kayak widget tapi di notification shade.
- [ ] **Volume button as shutter** — Hardware shutter lebih cepat dari tap screen. HiOS biasanya allow ini.
- [ ] **Shake to re-capture** — Goyang HP untuk retake halaman terakhir (kalau blur). Cepat tanpa navigasi UI.

### 13. No Ads / No Subscription = No Compromise
- [ ] **Unlimited page count** — Gak perlu limit logic. Scan sebanyak-banyaknya.
- [ ] **No watermark ever** — Gak perlu watermark overlay canvas. Langsung pure document.
- [ ] **No quality tier lock** — Semua filter (Tier 1/2/3) gratis & available. Gak perlu "Pro version" logic.
- [ ] **No analytics / telemetry** — Gak perlu Firebase Analytics, Crashlytics (kecuali kamu mau). App 100% offline & private.
- [ ] **No cloud dependency** — Semua lokal. Gak ada "sync ke cloud" yang dipaksa.

---

## 🔵 BENCHMARK TARGETS (Tecno Pova 7 4G Realistic)

Dengan optimalisasi di atas, target di device-mu:

| Skenario | Target | Notes |
|----------|--------|-------|
| Preview + auto-detect | **30fps stabil** | 12MP binned + ISP hardware NR |
| Capture → Warp (Tier 1) | **< 500ms** | A76 + skip bilateral (ISP sudah NR) |
| Capture → Warp (Tier 2) | **< 1 detik** | Bilateral + CLAHE + shadow |
| 10 halaman → PDF | **< 4 detik** | 8GB RAM + streaming write |
| 20 halaman scan baterai | **< 5% drain** | 7000mAh = monster |
| Thermal 20 scan berturut | **< 40°C** | 6nm + 7000mAh body besar = adem |
| APK size (minimal OpenCV) | **< 15MB** | Strip + static link |

---

## ⚠️ What to AVOID on Tecno Pova 7 4G

| Jangan | Kenapa |
|--------|--------|
| Request 108MP sebagai default | Processing lambat, noise tinggi, overkill untuk dokumen |
| Andalkan WorkManager untuk PDF | HiOS akan delay/kill. Pakai foreground service. |
| Skip battery optimization onboarding | HiOS akan kill app di tengah proses |
| Double NR (ISP + OpenCV bilateral) | Hasil over-smoothed, teks jadi kabur |
| GPU compute / RenderEffect real-time | Mali-G57 MC2 tetap lemah |
| Background processing tanpa wake lock | CPU bisa sleep di tengah warp 12MP |
| Simpan cache di microSD | UFS 2.2 internal 2-3x lebih cepat |

---

## 🗺️ Updated Roadmap untuk Tecno Pova 7

### Phase 1: Device-Specific Foundation
1. Camera2 request 12MP binned (bukan 108MP)
2. ISP hardware NR + edge enhancement (skip software bilateral default)
3. HiOS battery optimization onboarding (auto-start + background lock)
4. ForegroundService + WakeLock untuk PDF generation

### Phase 2: Leverage Hardware
5. Thread pinning ke A76 (CPU 0-1) + `cv::setNumThreads(3)`
6. AE/AWB lock saat dokumen stabil
7. Multi-frame burst 5 frame untuk low-light
8. Thermal threshold agresif (baru turun di SEVERE)

### Phase 3: Personal UX Polish
9. Hardcode folder & filename pattern sesuai kebutuhanmu
10. Widget & Quick Settings Tile per dokumen type
11. Auto-export ke sync folder (Syncthing/SMB)
12. Volume button shutter + shake to retake

---

*Checklist ini disusun khusus untuk Tecno Pova 7 4G (LJ6) dengan Helio G100 Ultimate, 8GB RAM, 108MP kamera, dan HiOS 15.*
*Fokus: maksimalkan 108MP sensor via 12MP binning + ISP hardware, handle HiOS agresif background kill, dan leverage 7000mAh + 8GB RAM untuk processing tanpa kompromi.*
*Buat personal use — no ads, no watermark, no subscription, no compromise.*
