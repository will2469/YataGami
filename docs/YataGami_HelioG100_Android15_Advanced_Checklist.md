# YataGami Advanced Feature Checklist
## Optimized for: MediaTek Helio G100 + Android 15 (Personal Use)

---

## 📱 Device Profile Analysis

| Spec | Helio G100 | Implication for YataGami |
|------|-----------|-------------------------|
| CPU | 2x Cortex-A75 @ 2.2GHz + 6x Cortex-A55 @ 2.0GHz | Big.LITTLE → offload heavy CV to A75, UI ke A55. Jangan pakai semua core untuk processing! |
| GPU | Mali-G57 MC2 (2 core) | Terbatas. GPU compute untuk CV tidak direkomendasikan. Fokus CPU NEON + thread pinning. |
| RAM | Typically 4-8GB LPDDR4X | Memory cukup, tapi jangan greedy. Target < 300MB peak. |
| ISP | MediaTek Imagiq | Bisa dimanfaatkan kalau akses Camera2 directly (noise reduction hardware). |
| NPU | Tidak ada dedicated NPU | Gak bisa ML inference berat. Skip ML-based dewarping/detection. |
| Android | 15 (API 35) | Bisa pakai Photo Picker, Edge-to-Edge, Predictive Back, partial screen share, etc. |

---

## 🔴 TIER 1: Helio G100 Specific Optimizations (Wajib!)

### 1. CPU Thread Pinning & Scheduling
- [ ] **Pin heavy CV thread ke Cortex-A75 (big core)** — Gunakan `sched_setaffinity()` di native untuk thread processing dokumen. A75 = 2 core, jadi maksimal 2 thread heavy parallel.
- [ ] **Pin UI thread ke Cortex-A55 (LITTLE core)** — Cukup, UI gak butuh compute berat.
- [ ] **Set thread priority**: CV thread = `THREAD_PRIORITY_URGENT_AUDIO` (di Android) / `SCHED_FIFO` (di native). UI thread = normal.
- [ ] **Jangan spawn lebih dari 2 thread untuk OpenCV parallel** — `cv::setNumThreads(2)` karena hanya 2 big core. Lebih dari itu malah context switch mahal di A55.
- [ ] **Gunakan `setpriority(PRIO_PROCESS, 0, -10)` untuk native processing thread** — Biar OS tidak preempt thread processing saat sedang warp/enhance.

### 2. MediaTek ISP & Camera2 Leverage
- [ ] **Bypass CameraX untuk capture quality tertinggi** — CameraX abstraction ada overhead. Pakai **Camera2 API langsung** untuk:
  - Manual exposure/ISO control
  - Raw (DNG) capture kalau ISP support (Helio G100 ISP support 48MP raw processing)
  - Hardware noise reduction (MNRF / ANRF) di ISP level
- [ ] **Enable Zero Shutter Lag (ZSL)** — Helio G100 ISP support ZSL. Capture dari ring buffer frame terakhir = gak ada delay shutter → dokumen gak blur karena goyang saat tap.
- [ ] **Request `NOISE_REDUCTION_MODE_HIGH_QUALITY`** — Pakai hardware NR ISP MediaTek, bukan software OpenCV.
- [ ] **Request `EDGE_MODE_HIGH_QUALITY`** — ISP sharpening hardware lebih baik & lebih hemat baterai dari unsharp mask software.
- [ ] **Control AE/AF manually untuk dokumen**:
  - Lock exposure saat dokumen terdeteksi stabil (biar gak flicker)
  - Manual focus distance ke **infinity / hyperfocal** (dokumen biasanya >20cm, fokus di sini paling tajam untuk flat object)
  - Disable continuous AF (boros baterai & ada hunting focus)

### 3. Memory & Thermal Awareness (Helio G100 cepat panas!)
- [ ] **Thermal throttling detection** — Register `PowerManager.addThermalStatusListener()`. Kalau status = `THERMAL_STATUS_MODERATE` atau lebih, turunkan:
  - Processing resolution (dari full ke half)
  - Nonaktifkan auto-capture, pindah ke manual shutter
  - Skip enhancement filter yang berat (CLAHE, NLM denoise)
- [ ] **Peak memory cap 250MB** — Helio G100 device biasanya 4GB RAM, system pakai ~2.5GB. Sisa untuk app lain. Monitor dengan `Debug.MemoryInfo`.
- [ ] **Avoid memory spike saat PDF generation** — Stream write per halaman, jangan load semua bitmap ke RAM. Kalau >10 halaman, flush ke disk dulu baru gabung.
- [ ] **Use `android:largeHeap="true"` di manifest** — Hanya untuk safety, tapi tetap target < 250MB actual usage.

### 4. Mali-G57 MC2 GPU (Use with Caution)
- [ ] **Jangan pakai GPU untuk CV compute** — Mali-G57 MC2 terlalu lemah untuk OpenCL/Vulkan compute. Overhead dispatch lebih mahal dari hasilnya.
- [ ] **GPU hanya untuk UI rendering** — Compose UI pakai GPU rendering (default). Itu sudah cukup.
- [ ] **Skip RenderEffect/AGSL untuk filter real-time** — Helio G100 + Mali-G57 MC2 akan lag kalau preview di-overlay dengan shader kompleks. Filter cukup di apply post-capture.

---

## 🟠 TIER 2: Android 15 Specific Features (API 35)

### 5. Photo Picker & Storage (Android 15 Enhancements)
- [ ] **Use new Photo Picker (Android 14+) with `MediaStore` integration** — Gak perlu izin storage untuk import gambar dari galeri. Lebih privat & aman.
- [ ] **Partial screen content sharing** — Android 15 support partial screen share. Bisa implementasi "share hanya halaman tertentu dari PDF" via `MediaProjection`.
- [ ] **Predictive Back Gesture** — Tambahkan animation predictive back saat user swipe back dari screen crop/filter. UX lebih halus.
- [ ] **Edge-to-Edge enforcement** — Android 15 enforce edge-to-edge untuk app targetSdk 35. Pastikan CameraPreview & overlay handle insets dengan benar (system bar gak nutup tombol shutter).
- [ ] **TextView justify + hyphenation** — Untuk metadata/label di UI, pakai `JUSTIFICATION_MODE_INTER_WORD` biar rapi.
- [ ] **Improve font rendering** — Android 15 punya `LINEAR_METRICS_FLAG` dan better glyph caching. Pastikan Text di Compose pakai `includeFontPadding = false` untuk tampilan lebih compact.

### 6. Camera & Media (Android 15)
- [ ] **Low Light Boost (if device supports)** — Android 15 introduce `LOW_LIGHT_BOOST` mode di Camera2. Helio G100 ISP mungkin support. Auto-aktifkan saat lux < 50.
- [ ] **Ultra HDR image capture** — Kalau kamera support (unlikely di Helio G100 tier), bisa capture HDR + simpan gainmap. Skip kalau tidak support.
- [ ] **In-app camera controls in edge-to-edge** — Pastikan tombol shutter, flash, dan gallery gak ketutup navigation bar/gesture area di Android 15 edge-to-edge.

### 7. Security & Privacy (Android 15)
- [ ] **Screen recording detection** — Android 15 bisa deteksi `MediaProjection` aktif. Warning user kalau screen recording aktif saat scan dokumen sensitif (KTP, NPWP, etc).
- [ ] **Private Space awareness** — Kalau user punya Private Space (Android 15 feature), PDF yang disimpan di Documents tetap visible. Pertimbangkan opsi simpan ke app-private directory dengan enkripsi.
- [ ] **Safer Intents** — Android 15 enforce `Intent` dengan package name yang lebih ketat. Pastikan share PDF via `Intent.createChooser()` pakai `FLAG_GRANT_READ_URI_PERMISSION` yang benar.

---

## 🟡 TIER 3: Advanced Image Processing (Feasible di Helio G100)

### 8. Smart Capture Pipeline
- [ ] **Lux-based capture strategy**:
  - Lux > 200 (terang): Fast capture, minimal processing, skip denoise
  - Lux 50-200 (normal): Standard pipeline
  - Lux < 50 (gelap): Enable Low Light Boost (kalau support), longer exposure, stronger denoise, flash suggestion
- [ ] **Multi-frame capture untuk low light** — Capture 3-5 frame burst, align dengan `cv::findTransformECC()`, average untuk noise reduction. Helio G100 bisa handle 3 frame @ 1080p dalam < 1 detik.
- [ ] **Focus bracketing untuk dokumen dekat** — Capture 3 frame dengan focus distance berbeda (near, mid, far), pilih yang paling tajam berdasarkan Laplacian variance. Berguna untuk dokumen yang gak 100% flat.
- [ ] **Auto flash trigger** — Deteksi lux < 30 + blur metric tinggi → suggest flash. Tapi hindari flash untuk dokumen glossy (glare detection dulu).

### 9. Document Intelligence (Non-ML, rule-based)
- [ ] **Document type auto-classification** (rule-based, gak perlu ML):
  - KTP: Aspect ratio ~1.58:1 (85.6mm × 53.98mm), ada foto wajah di kiri
  - SIM: Aspect ratio ~1.59:1, layout mirip KTP tapi tanpa foto
  - A4: Aspect ratio 1:1.414
  - Struk: Aspect ratio variable, thermal paper (biasanya off-white/pinkish)
  - Kartu nama: Aspect ratio ~1.75:1
- [ ] **Auto DPI estimation** — Dari document type + detected physical size (dari focal length & object distance estimation). KTP = 300 DPI minimum, A4 = 150-300 DPI tergantung tujuan.
- [ ] **Content-aware crop** — Setelah warp, analisis projection profile horizontal/vertical untuk deteksi margin konten sebenarnya. Auto-crop margin putih berlebih tanpa motong teks.
- [ ] **Blank page detection** — Kalau halaman > 95% putih setelah binarization, flag sebagai "blank" dan kasih opsi hapus otomatis.

### 10. Enhancement Pipeline (Tiered by Performance)
- [ ] **Tier 1 - Fast (< 200ms di Helio G100)**:
  - White balance (Grayworld)
  - Gamma correction
  - Mild unsharp mask (kernel 3x3)
  - Auto contrast stretch
- [ ] **Tier 2 - Standard (< 500ms)**:
  - Tier 1 + Bilateral filter
  - CLAHE (tile 8x8)
  - Shadow removal (morphological)
- [ ] **Tier 3 - Quality (< 1s, manual only)**:
  - Tier 2 + Fast NLM denoise
  - Sauvola binarization
  - Deconvolution untuk motion blur (Wiener, ringan saja)
- [ ] **Auto-tier selection** — Berdasarkan thermal status + document type + user preference (speed vs quality).

### 11. PDF Generation Advanced Features
- [ ] **Mixed compression per page otomatis**:
  - Halaman teks polos (variance rendah, edge banyak) → PNG + Flate (lossless)
  - Halaman foto/berwarna (variance tinggi) → JPEG @ 85%
  - Halaman B&W hasil threshold → CCITT G4 (fax compression) via PDFBox — ukuran super kecil untuk teks
- [ ] **PDF/A-1b compliance** — Embed font, sRGB profile, dan metadata yang sesuai standard archival. Dokumen legal/official jadi lebih trusted.
- [ ] **Linearized PDF (Fast Web View)** — Meskipun offline, PDF yang linearized bisa dibuka & render halaman pertama lebih cepat di reader apapun.
- [ ] **PDF thumbnail embedding** — Generate thumbnail 128x128 per halaman, embed ke PDF. File manager bisa preview tanpa buka app.
- [ ] **Automatic filename intelligence**:
  - "Scan_YYYYMMDD_HHMMSS_[DocType]_[NPages].pdf"
  - DocType auto-detect: KTP, A4, Struk, KartuNama
  - Contoh: "Scan_20250820_143022_KTP_1.pdf"

---

## 🟢 TIER 4: UX & Personal Productivity Features

### 12. Batch & Workflow Optimization
- [ ] **Quick rescan** — Tombol "Scan Lagi" di bottom sheet setelah save. Langsung balik ke kamera tanap reload activity (cold start < 300ms).
- [ ] **Batch mode dengan auto-increment filename** — "Invoice_Agustus_001.pdf", "Invoice_Agustus_002.pdf", etc. Detect existing files untuk auto-numbering.
- [ ] **Favorite folder shortcuts** — Simpan 3 folder favorit (contoh: "KTP", "Invoice", "Kuliah") untuk save langsung ke situ via SAF (Storage Access Framework) tree.
- [ ] **One-tap widget** — Android widget di home screen: tap langsung buka kamera mode "KTP" atau mode "Dokumen" (preset filter + folder tujuan).

### 13. Smart Organization (Local, No Cloud)
- [ ] **Auto-tagging berdasarkan document type** — Folder otomatis: `/Documents/YataGami/KTP/`, `/Documents/YataGami/Invoice/`, etc.
- [ ] **Search by date range & doc type** — Local database (Room) index filename, path, doc type, page count, dan creation date. Search offline instan.
- [ ] **Duplicate detection** — Hash (perceptual hash / dHash) untuk deteksi dokumen yang sudah pernah di-scan. Warning sebelum save duplikat.
- [ ] **PDF merge/split tool** — Merge beberapa PDF jadi satu, atau split PDF multi-halaman. Native processing, gak perlu app lain.

### 14. Backup & Export Strategy
- [ ] **Auto-backup ke USB OTG** — Deteksi USB drive terpasang, auto-copy PDF baru ke folder backup (opsional, user setting).
- [ ] **Export ke SMB/FTP local server** — Untuk backup ke NAS / PC di jaringan lokal (WiFi). Gak perlu cloud.
- [ ] **Encrypted ZIP export** — Password-protected ZIP untuk dokumen sensitif sebelum di-share via WhatsApp/Email.

### 15. Accessibility & Convenience
- [ ] **Voice command** — "Scan", "Save", "Next", "Retake" via `SpeechRecognizer` (offline, gak perlu internet).
- [ ] **Haptic feedback** — Vibration ringan saat dokumen terdeteksi stabil & auto-capture trigger. Konfirmasi tactile.
- [ ] **Large shutter button mode** — Mode untuk user yang butuh tombol besar (aksesibilitas). Tombol shutter 2/3 layar.
- [ ] **Grayscale preview mode** — Preview viewfinder dalam grayscale untuk fokus ke komposisi & edge (tidak terganggu warna). Toggle di setting.

---

## 🔵 TIER 5: Experimental / Future-Proofing

### 16. E-Ink / Low Power Mode
- [ ] **E-Ink optimized preview** — Kalau device support secondary display atau e-ink case, render preview dalam black & white dithered. Sangat hemat baterai.
- [ ] **Battery-aware processing** — Kalau baterai < 20%, otomatis:
  - Skip enhancement
  - Lower resolution warp
  - Disable auto-capture (manual only, gak perlu analysis terus-menerus)

### 17. Advanced Camera Features (Kalau Hardware Support)
- [ ] **Macro mode detection** — Helio G100 + sensor support macro? Auto-switch ke macro kalau dokumen < 10cm.
- [ ] **Document flatness detection** — Dari variance blur di berbagai region — deteksi halaman buku yang melengkung. Suggest user untuk tekan buku agar rata.
- [ ] **Chromatic aberration correction** — Kalau lensa kamera murah (biasanya di Helio G100 device), edge teks ada fringing warna. Correct dengan channel shift di native.

### 18. Integration dengan Android 15 System
- [ ] **App Shortcuts (long-press icon)** — "Scan KTP", "Scan Invoice", "Buka Gallery PDF"
- [ ] **Share target API** — Register sebagai share target untuk gambar. User bisa share foto dari app kamera bawaan → langsung masuk YataGami untuk crop & PDF-kan.
- [ ] **Quick Settings Tile** — Tile di notification shade untuk "Quick Scan" (buka kamera langsung).
- [ ] **Digital Wellbeing integration** — Timer penggunaan / reminder istirahat (opsional, untuk user yang aware digital wellbeing).

---

## 🗺️ Implementation Priority untuk Helio G100

### Sprint 1: Foundation (Paling Besar Impact)
1. Thread pinning ke Cortex-A75 (big core)
2. Camera2 manual control (AE lock, focus hyperfocal, ZSL)
3. Thermal throttling detection + adaptive quality
4. Memory cap & streaming PDF generation

### Sprint 2: Smart Capture
5. Lux-based capture strategy
6. Multi-frame burst untuk low light
7. Focus bracketing
8. Document type auto-classification (rule-based)

### Sprint 3: Advanced Processing
9. Tiered enhancement pipeline (Fast/Standard/Quality)
10. Content-aware crop
11. Mixed compression per page (PNG/JPEG/CCITT G4)
12. PDF/A-1b compliance

### Sprint 4: UX & Productivity
13. Quick rescan + batch mode
14. Auto-tagging & local search (Room database)
15. One-tap widget + App Shortcuts
16. Share target integration

---

## ⚠️ What to AVOID on Helio G100

| Jangan Implementasi | Kenapa |
|--------------------|--------|
| Real-time filter preview dengan shader AGSL/RenderEffect | Mali-G57 MC2 akan lag parah, thermal naik cepat |
| ML inference (TFLite) untuk document detection | Gak ada NPU, CPU akan lambat & panas. Rule-based CV cukup |
| 4K processing real-time | Helio G100 ISP bisa capture 4K tapi processing 4K di CPU = lambat. Max 1080p untuk pipeline, upscale hanya untuk final export kalau perlu |
| OpenCL / Vulkan compute | Mali-G57 MC2 overhead dispatch lebih mahal dari CPU NEON. Skip. |
| Multi-page OCR (even on-device) | Terlalu berat untuk chipset ini. YataGami memang tanpa OCR, tetap fokus visual quality. |
| Continuous auto-capture | Boros baterai & thermal. Trigger hanya saat dokumen stabil + cooldown 2 detik. |
| Loading full PDF ke memory untuk preview | Stream & render per page. Helio G100 RAM terbatas. |

---

## 📊 Expected Experience on Helio G100

| Scenario | Target Performance |
|----------|-------------------|
| Kamera preview + auto-detect | 30fps stabil, gak panas |
| Capture → Warp → Tier 1 Enhance | < 1 detik |
| Capture → Warp → Tier 2 Enhance | < 2 detik |
| 10 halaman → PDF (mixed compression) | < 5 detik total |
| Baterai: 20 halaman scan | < 8% drain |
| Thermal: 10 scan berturut-turut | Tetap di bawah 42°C (tidak throttling) |
| APK size (minimal OpenCV + strip) | < 15MB |

---

*Checklist ini disusun khusus untuk MediaTek Helio G100 + Android 15, penggunaan pribadi.*
*Fokus: maksimalkan hardware yang ada tanpa over-engineering. Skip fitur yang membutuhkan NPU/GPU compute berat.*
*YataGami = cermin suci yang optimal untuk device-mu.*
