# YataGami Advanced Feature Checklist
## Optimized for: MediaTek Helio G100 + Android 15 (Personal Use)

---

## 📱 Device Profile Analysis (Tecno Pova 7 4G - LJ6)

| Spec | Real Device Spec (Tecno Pova 7) | Implication for YataGami |
|------|---------------------------------|-------------------------|
| **Chipset** | MediaTek Helio G100 Ultimate (6nm TSMC) | Efisien daya & termal dingin. Clock CPU/GPU stabil. |
| **CPU** | 2x Cortex-A76 @ 2.2GHz + 6x Cortex-A55 @ 2.0GHz | Big.LITTLE → offload CV berat ke 2 big core Cortex-A76, UI ke A55. |
| **GPU** | ARM Mali-G57 MC2 (2 core @ 1000MHz) | Lemah untuk compute. Murni untuk Compose UI hardware rendering, komputasi CV di CPU. |
| **RAM** | 8GB LPDDR4X | Kapasitas besar. Headroom memory > 500MB, buffer pool 16 Mats, in-memory page caching. |
| **Baterai** | 7000 mAh | Sangat besar! Headroom daya tinggi, Smart Auto-Enhance dapat berjalan default. |
| **Kamera Utama** | 108MP (9-in-1 pixel binning) | Request sweet spot 12MP binned (4000x3000) via Camera2 + ISP hardware MFNR & Edge Sharpening. |
| **OS** | Android 15 + HiOS 15 (Tecno) | HiOS agresif kill background → Dilindungi ForegroundService + WakeLock + Onboarding Setup. |

---

## 🔴 TIER 1: Helio G100 Ultimate & Tecno Pova 7 Specific (Wajib!)

### 1. CPU Thread Pinning & Scheduling
- [x] **Pin heavy CV thread ke Cortex-A76 (big core)** — `sched_setaffinity()` di native mendeteksi core CPU berfrekuensi tertinggi (A76) dan mengikat thread CV ke 2 big core (`pinThreadToBigCores`).
- [x] **Pin UI thread ke Cortex-A55 (LITTLE core)** — Android Main UI thread berjalan pada core A55 tanpa terganggu komputasi berat.
- [x] **Set thread priority**: CV thread native diprioritaskan dengan `setpriority(PRIO_PROCESS, 0, -10)` saat pemrosesan dokumen.
- [x] **Jangan spawn lebih dari 2 thread untuk OpenCV parallel** — `cv::setNumThreads(2)` dikonfigurasikan saat `JNI_OnLoad` untuk mencegah context switching berlebih ke A55.
- [x] **Gunakan `setpriority(PRIO_PROCESS, 0, -10)` untuk native processing thread** — Mencegah OS preemption saat pemrosesan warp/deskew/enhance.

### 2. MediaTek ISP & Camera2 Leverage (108MP Sensor Strategy)
- [x] **Request 12MP 9-in-1 Binned Sweet Spot (4000x3000)** — `ResolutionSelector` meminta resolusi 12MP binned 4:3 untuk noise minimal dan dynamic range optimal.
- [x] **Enable Zero Shutter Lag (ZSL)** — `CAPTURE_MODE_ZERO_SHUTTER_LAG` pada `ImageCapture` untuk capture instan dari frame buffer ISP tanpa lag shutter dan bebas blur goyang.
- [x] **Request `NOISE_REDUCTION_MODE_HIGH_QUALITY`** — Menggunakan hardware Multi-Frame Noise Reduction (MFNR) tingkat ISP MediaTek Helio G100 Ultimate.
- [x] **Request `EDGE_MODE_HIGH_QUALITY`** — Hardware ISP edge sharpening untuk ketajaman teks dokumen tanpa beban CPU software.
- [x] **Control AE/AF & Steady Scene Mode**:
  - Deteksi stabilitas kontur dokumen (3 frame berturut-turut $\Delta < 18\text{px}$) sebelum auto-shutter (`checkCornerStability`).
  - Request `CONTROL_SCENE_MODE_STEADYPHOTO`, `SHADING_MODE_HIGH_QUALITY` & `HOT_PIXEL_MODE_HIGH_QUALITY` pada ISP level.

### 3. Memory & Thermal Awareness (7000 mAh + 6nm TSMC Headroom)
- [x] **Thermal throttling detection (Threshold SEVERE)** — `DevicePerformanceMonitor` mendaftarkan `PowerManager.addThermalStatusListener()`. Karena termal 6nm dingin, resolusi hanya turun ke 200 DPI saat status mencapai `THERMAL_STATUS_SEVERE`.
- [x] **Headroom Memory Cap 500MB** — `DevicePerformanceMonitor.getUsedMemoryMB()` memonitor alokasi heap runtime dari total 8GB RAM LPDDR4X.
- [x] **Avoid memory spike saat PDF generation** — Incremental page stream drawing & periodic garbage collection saat memory pressure terdeteksi.
- [x] **Use `android:largeHeap="true"` di manifest** — Diaktifkan pada `<application>` tag di `AndroidManifest.xml` untuk keamanan alokasi buffer grafis.
- [x] **HiOS 15 Background Protection** — `PdfProcessingService` (Foreground Service) + CPU Partial WakeLock untuk menjamin proses kompilasi tidak dihentikan oleh HiOS.

### 4. Mali-G57 MC2 GPU (Use with Caution)
- [x] **Jangan pakai GPU untuk CV compute** — Seluruh komputasi CV (Canny, Warp, Deskew, Filter) berjalan pada CPU Cortex-A76 native C++ tanpa overhead OpenCL/Vulkan compute.
- [x] **GPU hanya untuk UI rendering** — Jetpack Compose UI dirender via GPU hardware acceleration standar tanpa membebani bus memori grafis.
- [x] **Skip RenderEffect/AGSL untuk filter real-time** — Filter dokumen diaplikasikan secara efisien post-capture pada native layer, menjaga viewfinder 60 FPS tetap mulus dan bebas panas.

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

## 🟡 TIER 3: Advanced Document Intelligence & Capture

### 8. Smart Capture Pipeline
- [ ] **Lux-based capture strategy**:
  - Lux > 200 (terang): Fast capture, standard pipeline
  - Lux 50-200 (normal): Standard pipeline + Smart Auto-Enhance
  - Lux < 50 (gelap): Longer exposure via ISP, bilateral denoise, auto-flash suggestion
- [ ] **Multi-frame burst untuk low light** — Capture frame burst via Camera2 ring buffer untuk noise reduction murni saat gelap.
- [ ] **Focus control untuk dokumen dekat** — Hyperfocal & macro focus distance locking saat mendeteksi dokumen < 30cm agar teks tetap tajam.
- [ ] **Auto flash trigger** — Deteksi lux < 30 + blur score rendah → sarankan flash (hindari jika terdeteksi glare).

### 9. Document Intelligence (Rule-Based, Non-ML)
- [ ] **Document type auto-classification** (rule-based aspect ratio):
  - KTP: Aspect ratio ~1.58:1 (85.6mm × 53.98mm)
  - SIM: Aspect ratio ~1.59:1
  - A4: Aspect ratio 1:1.414
  - Struk/Receipt: Aspect ratio memanjang
  - Kartu nama: Aspect ratio ~1.75:1
- [ ] **Content-aware margin cleanup** — Setelah warp, analisis projection profile untuk membersihkan margin hitam/bayangan luar tanpa memotong teks.
- [ ] **Blank page detection** — Deteksi halaman kosong (>95% putih/polos) sebelum di-save ke PDF.

### 10. PDF Generation & Advanced Archival
- [ ] **Automatic filename intelligence**:
  - Format: `Scan_YYYYMMDD_HHMMSS_[DocType].pdf` (contoh: `Scan_20260820_143022_KTP.pdf`)
- [ ] **PDF/A archival metadata** — Menyertakan info judul, tanggal, dan metadata standar untuk keperluan legal/resmi.
- [ ] **Page Reordering & Deletion UX** — Geser untuk mengatur urutan halaman sebelum diekspor.

---

## 🟢 TIER 4: UX & Productivity (Personal Use)

### 11. Workflow Optimization
- [ ] **Quick rescan** — Tombol "Scan Lagi" setelah save untuk langsung membuka kamera tanpa reload activity.
- [ ] **Haptic feedback** — Getaran halus saat dokumen terdeteksi stabil & auto-shutter terpicu.
- [ ] **Share target integration** — Terima share gambar dari galeri/aplikasi lain langsung ke YataGami untuk auto-crop & ekspor PDF.

---

## 🗺️ Implementation Priority (Tecno Pova 7 4G)

### Phase 1: Core Engine (SELESAI)
1. Thread pinning ke 2x Cortex-A76 big cores (`pinThreadToBigCores`)
2. Camera2 Zero Shutter Lag + 12MP 9-in-1 Binned (`4000x3000`)
3. HiOS 15 Foreground Service + WakeLock protection
4. BufferPool (16 mats) + In-Memory Caching (8GB RAM)

### Phase 2: Android 15 & Document Intelligence (IN PROGRESS)
5. Photo Picker Modern (`PickVisualMedia`) & Edge-to-Edge display
6. Rule-based Document Type Classification (KTP, A4, Struk)
7. Content-aware margin trimming
8. Quick rescan & Haptic feedback

---

## ⚠️ What to AVOID on Tecno Pova 7 4G

| Jangan Implementasi | Kenapa |
|--------------------|--------|
| GPU Compute (OpenCL/Vulkan) | Mali-G57 MC2 terlalu lemah untuk CV compute; biarkan GPU murni untuk render UI Compose. |
| ML/TFLite berlebih | Tidak ada dedicated NPU. Rule-based geometry & morphology di CPU A76 jauh lebih cepat, hemat baterai, dan ringan. |
| Request 108MP unbinned | 108MP mentah menghasilkan noise tinggi & file membengkak tanpa manfaat tambahan untuk dokumen; 12MP binned adalah sweet spot. |
| Fitur Bloat (Voice command, SMB/FTP, E-Ink) | Tidak relevan untuk scanner dokumen pribadi; jaga aplikasi tetap ramping, cepat, dan fokus. |

---

*Checklist ini dioptimalkan khusus untuk Tecno Pova 7 4G (MediaTek Helio G100 Ultimate + Android 15 / HiOS 15).*
*Fokus: Performa maksimal, visual tajam, zero-bloat.*
