# YataGami Development Gap Analysis & Priority Checklist
## "What Hasn't Been Covered Yet" — Complete Roadmap
### Tecno Pova 7 4G (LJ6) | Helio G100 Ultimate | Personal Build

---

## Legend

| Priority | Meaning | Timeline |
|----------|---------|----------|
| **P0** | Must have — app gak bisa dipake tanpa ini | Minggu 1-2 |
| **P1** | Important — UX significantly better | Minggu 3-4 |
| **P2** | Nice to have — polish & delight | Minggu 5-6 |
| **P3** | Future / experimental | Bulan 2+ |

---

## ✅ ALREADY COVERED (Don't Revisit Unless Bug)

| # | Area | Status |
|---|------|--------|
| 1 | C++ Native memory & GC optimization | ✅ Pool, arena, zero-copy, JNI batch |
| 2 | C++ Cache locality & compiler optimization | ✅ Tiling, SoA, prefetch, LTO, PGO |
| 3 | Camera2 configuration & 108MP strategy | ✅ 12MP binned, ISP hardware NR |
| 4 | HiOS background kill mitigation | ✅ ForegroundService, wake lock, onboarding |
| 5 | PDF generation (PdfBox) | ✅ Streaming write, MediaStore |
| 6 | Build system & OpenCV static linking | ✅ Strip, visibility hidden, minimal modules |
| 7 | Device-specific thermal & thread tuning | ✅ A76 affinity, 3 threads, aggressive threshold |

---

## 🔴 P0: MUST HAVE — Belum Dibahas / Butuh Design

### 1. Document Detection Algorithm (The Core!)
**Status: ✅ Selesai Diimplementasikan (Hierarchical Fallback, Confidence Scoring, EMA Smoothing & Interactive Crop UI)**

Ini JANTUNG aplikasi. Semua optimasi C++ sia-sia kalau deteksi dokumen-nya gagal.

- [x] **Quad detection pipeline** — Dari preview frame → 4 corner points dokumen
  - Grayscale → Gaussian blur → Canny edge → findContours → approximatePolyDP → filter by aspect ratio & area → select best quad
  - Hierarchical Fallback: Convex Hull (approxPolyDP 0.04) → minAreaRect → Full Frame Quad (bebas false trigger)
- [x] **Real-time vs capture-time detection** — Preview pakai lightweight detection (0.5x pyramid), capture baru full resolution warp
- [x] **Confidence scoring (6 Faktor)** — Score 0.0-1.0 (Area, Parallelism, Convexity, Orthogonality, Aspect Ratio, Stability). Auto-capture aktif hanya jika confidence >= 0.70.
- [x] **Corner refinement** — Sub-pixel corner detection (cornerSubPix window 5x5, maxIter=10, eps=0.01) untuk akurasi warp
- [x] **EMA Temporal Smoothing & Multi-frame validation** — Exponential Moving Average (alpha=0.35) dan stabil 3 frame berturut-turut untuk auto-capture
- [x] **Interactive Manual corner adjustment UI (CropScreen)** — 4-Corner Draggable, 2x Magnifier Loupe bubble, Magnetic Snap, dan Real-time Quad Validation (Convexity & non-intersecting)

**Why P0:** Tanpa ini, app cuma jadi "kamera biasa yang save PDF".

#### 🟡 Nice to Have / Future UX Polish:
- [ ] **Double-tap handle untuk fine-tune** — Nudge 1px untuk presisi micro-adjustment.
- [ ] **Show dimensions badge saat crop** — Indikator deteksi tipe (A4, KTP, SIM, Receipt) langsung di canvas crop.
- [ ] **Perspective grid overlay** — 3x3 grid rule-of-thirds untuk mempermudah alignment pengguna.
- [ ] **Haptic feedback saat snap** — Mikro-getaran haptic saat sudut menempel ke garis tepi magnetik.
- [ ] **Animate corner transitions** — Animasi perpindahan sudut halus saat auto-detect update.
- [ ] **Auto-suggest filename dari aspect ratio** — Otomatis menamai file berdasarkan rasio dokumen (`Scan_KTP_YYYYMMDD.pdf`).
- [ ] **Save crop state per page** — Menyimpan status sudut manual agar pengguna bisa kembali mengedit crop kapan saja.

---

### 2. Perspective Warp & Deskew Pipeline
**Status: ✅ Selesai Diimplementasikan (Tiered 150 DPI, Orientation-Aware Inference, BORDER_CONSTANT 20px padding, Conditional Deskew & Smart Content Trim)**

- [x] **Perspective transform** — Dari 4 corner detected → warp ke rectangle A4/Letter/ID card aspect ratio dengan 20px pre-padding dan BORDER_CONSTANT white
- [x] **Aspect ratio & Orientation inference** — Deteksi otomatis tipe dokumen dan orientasi (Portrait/Landscape):
  - A4 = 1:1.414 (1240×1754 @ 150 DPI)
  - KTP (Indonesia) = 1:1.588 (505×799 @ 150 DPI)
  - F4 / Folio (Asia) = 1:1.535 (1270×1949 @ 150 DPI)
  - Receipt = Dynamic clamped 2.0-5.0 (472×height max 5906px)
  - Square (foto) = 1:1 (1200×1200)
- [x] **Content-aware boundary** — Smart boundary trimming dengan minimum 10px safety margin dan 80% content area guard
- [x] **Skew correction post-warp** — Conditional text deskew (-15° s/d +15°) via horizontal projection profile (skip KTP/Photo)
- [x] **Curvature detection hint** — Deteksi kelengkungan teks (FLAT/MAYBE_CURVED/LIKELY_CURVED) sebagai warning geometris non-destruktif
- [x] **Homography validation** — Memvalidasi determinan matriks homography sebelum warp untuk mencegah deformasi gambar ekstrem

#### 🟡 Nice to Have / Future Improvements (Perspective Warp & Deskew):
- [ ] **Precompute homography di native & cache** — Re-warp dengan corner manual jadi < 50ms.
- [ ] **Multi-page consistent DPI** — Semua halaman dalam satu PDF memakai DPI konsisten.
- [ ] **Smart background color detection** — Auto-white balance target berdasarkan warna background meja/karpet.
- [ ] **Shadow removal sebelum warp** — Menghilangkan bayangan di pojok kertas sebelum deteksi.
- [ ] **Save warp matrix ke metadata** — Menyimpan homography matrix agar bisa di-re-warp kapan saja.

---

### 3. Enhancement Pipeline (Tiered Engine)
**Status: ✅ Selesai Diimplementasikan (Tier 1 Fast, Tier 2 Standard, Tier 3 Quality, LAB L-Channel CLAHE, & O(1) Fast Filter Modes)**

- [x] **Tier 1 — Fast (Preview & Quick Export)**
  - CLAHE eksklusif pada LAB L-Channel (clipLimit=2.0, tileSize=8x8, tanpa distorsi warna A & B)
  - Auto White Point percentile dynamic range stretch (1% & 99%) via 256-byte L1 Cache LUT
  - Target: < 100ms di native Helio G100
- [x] **Tier 2 — Standard (Default)**
  - Fast edge-preserving surface blur (~15-20ms, bukan OpenCV bilateral lambat)
  - CLAHE L-Channel (clipLimit=2.5, tileSize=8x8)
  - Modified White Patch / Grayworld AWB color cast compensation
  - Glare clamp & suppression pada L-Channel sebelum CLAHE
  - Target: < 400ms
- [x] **Tier 3 — Quality (Manual/Special Documents)**
  - Urutan pipeline kritis: Color Constancy → Background Illumination Flattening (Gaussian blur 101×101 + power factor 0.8) → Bilateral denoise (d=9, sigma=75) → Aggressive CLAHE (clipLimit=4.0) → Adaptive text edge unsharp masking (r=1.0, k=1.2)
  - Target: < 1.5 detik dengan hasil terbaik
- [x] **Auto-tier selection** — Multi-metric scene analysis: meanL, stdDevL, darkRatio, brightRatio, glareRatio, blurScore, noiseEstimate
- [x] **LUT-based fast path & Master Transform** — Master enhanced image digenerate sekali, pergantian filter mode (Magic Color, B&W, Gray, Sharpen) merupakan transformasi O(1) < 20ms tanpa alokasi memori ulang.

#### 🟡 Nice to Have / Future Improvements (Enhancement Pipeline):
- [ ] **Progressive enhancement preview** — Tampilkan hasil Tier 1 dulu (<100ms), lalu background upgrade ke Tier 2/3 (instant UX).
- [ ] **Per-region tier** — Area bayangan menggunakan Tier 3 lokal, area terang menggunakan Tier 1 (kualitas merata).
- [ ] **Save "Master" processed image** — Simpan cache hasil Tier 2/3 tanpa filter mode agar user bisa beralih filter tanpa re-processing.
- [ ] **Histogram equalization fallback** — Jika terjadi artifact CLAHE pada region datar, fallback otomatis ke gentle histogram stretch.
- [ ] **Adaptive denoise strength** — Estimasi noise tinggi → meningkatkan sigmaColor bilateral secara adaptif.

---

### 4. Scanning UX Flow (The Capture Experience)
**Status: ✅ Selesai Diimplementasikan (Decoupled 3 Streams 720p Analysis, Adaptive EMA, 5-Frame Cancelable Auto-Capture, Document Frame Guide, Background Queue)**

- [x] **Decoupled Camera Streams** — Preview 30 FPS (720p), ImageAnalysis 10-15 FPS (720p), ImageCapture 12MP (MINIMIZE_LATENCY).
- [x] **Adaptive EMA Smoothing** — Velocity-aware alpha (0.25 - 0.55) untuk corner tracking yang responsif namun halus.
- [x] **5-Frame Multi-Factor Stability & Cancelable Countdown** — 5 frame stabil berturut-turut + hitung mundur 500ms yang dapat dibatalkan jika ada pergerakan.
- [x] **Document Alignment Frame & Level Indicator** — Panduan bingkai dokumen 85% dan indikator kerataan orientasi accelerometer.
- [x] **Document Type Presets** — A4, KTP, F4, Struk, Foto yang langsung mempengaruhi aspect ratio prioritas dan target resolusi warp.
- [x] **Torch Tri-State (Off / Auto / On)** — Kontrol pencahayaan LED ganda untuk kondisi cahaya rendah.
- [x] **Background Processing Queue** — Pemrosesan warp & enhance berjalan di background coroutine queue tanpa memblokir viewfinder kamera (true batch scan).
- [x] **Subtle Shutter Flash (30% opacity, 50ms) + Haptic Feedback** — Umpan balik visual yang nyaman tanpa menyilaukan mata.

#### 🟡 Nice to Have / Future Improvements (Scanning UX Flow):
- [ ] **"Scanning guide" voice/text hint** — "Dekatkan dokumen", "Terlalu gelap", "Goyang".
- [ ] **Smart zoom (pinch) untuk dokumen jauh** — Digital zoom ringan dengan detektor tetap aktif.
- [ ] **Double-tap shutter = burst 3 frame** — Memilih frame tertajam untuk kondisi low-light.
- [ ] **Long-press shutter = hold for continuous** — Pindai batch super cepat (receipts, tumpukan kartu).
- [ ] **"Last page" undo gesture** — Shake HP atau 3-finger tap untuk retake halaman terakhir tanpa navigasi.
- [ ] **Auto document type dari first frame** — Mendeteksi rasio sebelum user memilih chip.
- [ ] **Preview quality indicator** — Indikator status lingkungan: 🌙 (low light), ⚡ (glare), ✓ (optimal).
- [ ] **Screen wake lock selama scan** — Mencegah layar mati otomatis saat user merapikan dokumen fisik.

---

### 5. Review & Edit Screen (Post-Capture)
**Status: ✅ Selesai Diimplementasikan (Tiered Memory Pager, Before/After Split, Master+FilterState, Soft Delete+Undo, Background Rotation & Reorder Sync)**

- [x] **Tiered Resolution Pager** — Pager menggunakan Preview Res (1200×1600) dengan `beyondViewportPageCount = 1` untuk mencegah OOM/killer HiOS.
- [x] **Before/After Split Slider** — Membandingkan Warped Master (Before) vs Enhanced (After) pada resolusi preview (~15MB total).
- [x] **Master + FilterState O(1)** — Master Enhanced disimpan sebagai referensi, perpindahan filter adalah transformasi O(1) cepat.
- [x] **Background 90° Rotation** — Rotasi cepat pada preview diikuti rotasi native full-res di background.
- [x] **Reorder with Pager State Sync** — Reordering halaman menjaga sinkronisasi posisi carousel secara mulus.
- [x] **Soft Delete + Snackbar Undo** — Penghapusan halaman non-destruktif dengan opsi "Batalkan" sebelum file disk dihapus permanen.
- [x] **Page Status & Progress Indicator** — Indikator status per halaman (Processing / Processed / Error).

#### 🟡 Nice to Have / Future Improvements (Review & Edit Screen):
- [ ] **Undo/Redo stack per page** — Corner edit, rotasi, filter change bisa di-undo (Ctrl+Z style).
- [ ] **Bulk filter apply** — Terapkan filter dari satu halaman ke seluruh halaman dalam satu ketukan.
- [ ] **Page duplicate** — Duplikasi halaman untuk variasi filter/crop berbeda.
- [ ] **Zoom & pan di review pager** — Pinch zoom untuk inspeksi detail teks halus.
- [ ] **Page note/label** — Menambahkan label atau nama kustom per halaman.
- [ ] **Auto-save edit state** — State restorasi otomatis jika proses terhenti oleh OS.
- [ ] **Share single page** — Ekspor langsung 1 halaman JPG tanpa menyusun PDF penuh.
- [ ] **Drag-to-reorder thumbnail** — Long press & drag thumbnail untuk mengatur ulang urutan.
- [ ] **Page count badge di export button** — "Simpan PDF (N Halaman)".
- [ ] **Empty state reset** — Navigasi kembali ke kamera dengan CTA jika seluruh halaman dihapus.

---

### 6. App Architecture & State Management
**Status: ❌ Belum dibahas**

- [ ] **Single-Activity + Compose Navigation** — `ScanScreen` → `ReviewScreen` → `ExportScreen`
- [ ] **ViewModel per screen** — `ScanViewModel`, `ReviewViewModel`, `ExportViewModel`
- [ ] **Shared DocumentSession** — Singleton holder untuk session scan sekarang (list of NativePage, config, state). Clear saat "New Document".
- [ ] **State preservation** — Kalau app di-kill HiOS di tengah scan, bisa resume:
  - Simpan `DocumentSession` ke DataStore/JSON file saat app background
  - Restore saat app reopen → "Lanjutkan scan sebelumnya?"
- [ ] **Configuration change survival** — Rotation saat scan gak boleh reset preview/camera.
- [ ] **Permission handling** — Camera, storage, notification (untuk foreground service). Flow yang graceful.

**Why P0:** HiOS kill background app + config change = data loss kalau architecture gak solid.

---

## 🟠 P1: IMPORTANT — Significant UX Impact

### 7. Document Library / Home Screen
**Status: ❌ Belum dibahas**

- [ ] **Document list** — RecyclerView/Compose LazyColumn dengan thumbnail, nama, tanggal, jumlah halaman.
- [ ] **Folder/grouping** — By date (Hari ini, Kemarin, Minggu ini, Bulan ini) atau by tag (KTP, Invoice, Kuliah, dll).
- [ ] **Search** — By filename, tag, atau date range. Personal use = simple prefix search cukup.
- [ ] **Thumbnail cache** — Thumbnail 200x200 di disk (Glide/Coil). Jangan regenerate dari PDF tiap kali.
- [ ] **Quick actions per document** — Share, Rename, Delete, Export as images, Duplicate.
- [ ] **Empty state** — Illustrasi + CTA "Scan Dokumen Pertama".
- [ ] **Import existing** — Dari file manager, import PDF/gambar yang sudah ada ke library YataGami.

**Why P1:** Setelah scan, user perlu manage dokumen. Ini jadi "home base" app.

---

### 8. Export & Share Options
**Status: ⚠️ PDF sudah, format lain belum**

- [ ] **Export as single images** — JPG/PNG per halaman. Quality selector (80%, 90%, 100%).
- [ ] **Export as ZIP** — Multiple images dalam satu ZIP.
- [ ] **Share sheet integration** — Android Sharesheet untuk kirim ke WhatsApp, Gmail, Drive, dll.
- [ ] **Print support** — Android Print framework untuk print langsung ke printer (WiFi/BT).
- [ ] **Cloud upload (optional)** — Google Drive, Telegram Saved Messages, SMB ke NAS. Personal use = manual share cukup, tapi quick upload ke satu cloud favoritmu bisa dibikin.
- [ ] **PDF metadata** — Title, Author, CreationDate, Subject. Auto-set title dari filename.
- [ ] **PDF password protection** — Optional encryption untuk dokumen sensitif (KTP, kontrak).
- [ ] **PDF compression level** — Fast vs Standard vs Minimum size.

**Why P1:** Export adalah end goal dari scanning. Semakin fleksibel, semakin berguna.

---

### 9. Edge Cases & Error Handling
**Status: ❌ Belum dibahas**

- [ ] **Glare detection** — Specular highlight di dokumen glossy (KTP laminating, foto). Deteksi via threshold brightness → warning "Glikter terdeteksi, geser dokumen sedikit".
- [ ] **Low light handling** — Kalau lux < 50:
  - Auto torch on
  - Longer exposure (kalau Camera2 support)
  - Burst 5 frame untuk NR
  - Warning "Cahaya kurang, hasil mungkin berisik"
- [ ] **Finger in frame detection** — Hand/finger accidentally cover corner dokumen. Edge contour analysis → warning "Jari terdeteksi di frame".
- [ ] **Blur detection & prevention** — Laplacian variance real-time. Kalau blur → jangan auto-capture. Show "Dokumen blur, stabilkan HP".
- [ ] **Multiple document detection** — Kalau ada 2 dokumen di frame, detect keduanya → let user pilih yang mana. Atau default ke yang terbesar.
- [ ] **Text orientation detection** — Auto-rotate kalau dokumen terbalik/samping. Horizontal projection profile + minimize entropy.
- [ ] **Processing failure recovery** — Kalau warp gagal (quad invalid, perspective singular), fallback ke:
  - Crop ke bounding box (no perspective correction)
  - Atau minta user manual corner adjustment
- [ ] **Storage full handling** — Kalau disk < 500MB, warning & pause scan.
- [ ] **Thermal throttling UX** — Kalau CPU hot, turun ke Tier 1 + show "HP panas, mode hemat aktif".

**Why P1:** Real-world scanning penuh edge case. Handle dengan graceful = app terasa "pintar".

---

### 10. Settings & Personalization
**Status: ❌ Belum dibahas**

- [ ] **Default document type** — Apa yang paling sering kamu scan? A4? KTP? Invoice?
- [ ] **Default enhancement tier** — Tier 2 untukmu? Atau Tier 1 karena sering buru-buru?
- [ ] **Auto-capture toggle** — On/off. Some people prefer manual shutter.
- [ ] **Sound & haptic** — Shutter sound on/off, haptic feedback on capture.
- [ ] **Default filename pattern** — Hardcode sesuai kebutuhanmu (KTP_Nama_Tanggal, INV_Vendor_Bulan, dll).
- [ ] **Default export location** — `/Documents/YataGami/` atau sync folder.
- [ ] **Theme** — Light/Dark/System. AMOLED dark mode = hemat baterai Pova 7.
- [ ] **Image quality default** — JPEG quality (85% sweet spot untuk dokumen).
- [ ] **Max pages per PDF** — Warning kalau > 50 halaman (file terlalu besar).
- [ ] **Auto-delete raw captures** — Setelah PDF berhasil dibuat, hapus original capture untuk hemat storage. Atau keep 7 hari.

**Why P1:** Personal use = app harus kerasa "milikku", bukan generic.

---

## 🟡 P2: NICE TO HAVE — Polish & Delight

### 11. OCR Layer (On-Device)
**Status: ❌ Belum dibahas**

- [ ] **Google ML Kit Text Recognition v2** — On-device, gratis, support Latin + some others. Gak perlu cloud.
- [ ] **OCR di native (optional)** — Tesseract via C++ (heavier, tapi 100% offline). ML Kit lebih praktis.
- [ ] **Text overlay di review screen** — Lihat teks yang terdeteksi untuk verify.
- [ ] **Copy text from scan** — Select & copy text dari dokumen.
- [ ] **Search text dalam document library** — "Cari dokumen yang ada kata 'Invoice PT X'".
- [ ] **Smart filename dari OCR** — Deteksi nomor invoice, nama, tanggal dari teks → auto-suggest filename.

**Why P2:** OCR = game changer, tapi bukan core scanning. Bisa ditambahkan nanti.

---

### 12. Smart Features
**Status: ❌ Belum dibahas**

- [ ] **Document classification** — Auto-detect jenis dokumen dari visual + OCR:
  - KTP → aspect ratio + text "REPUBLIK INDONESIA"
  - Invoice → table structure + total amount
  - Receipt (struk) → narrow aspect + item list
  - A4 letter → standard aspect + paragraph text
- [ ] **Auto-filename suggestion** — Dari classification + OCR:
  - KTP: "KTP_[Nama]_[Tanggal].pdf"
  - Invoice: "INV_[Vendor]_[NoInvoice]_[Bulan].pdf"
  - Kuliah: "Kuliah_[Matkul]_[Pertemuan].pdf"
- [ ] **Duplicate detection** — Warning kalau scan dokumen yang sama (compare perceptual hash).
- [ ] **Document quality score** — Rate hasil scan: Sharpness, contrast, glare, skew. Show "⭐⭐⭐ Dokumen bagus" atau "⚠️ Dokumen blur, scan ulang?"
- [ ] **Batch enhancement** — Apply enhancement setting dari halaman 1 ke semua halaman (kalau kondisi sama).

**Why P2:** "Wow factor". Bikin app terasa pintar & personal.

---

### 13. Widgets & Shortcuts
**Status: ⚠️ Konsep ada, detail belum**

- [ ] **Home screen widget 1x1** — "Scan KTP", "Scan Invoice", "Scan A4" — langsung buka camera dengan preset.
- [ ] **Quick Settings Tile** — Sama kayak widget tapi di notification shade.
- [ ] **App shortcut (long-press launcher)** — "New Scan", "Last Document", "Scan KTP".
- [ ] **Volume button shutter** — Hardware shutter lebih cepat.
- [ ] **Shake to retake** — Goyang HP untuk retake halaman terakhir.

**Why P2:** Speed of access. Untuk personal use, semakin cepat = semakin sering dipakai.

---

### 14. Data Backup & Sync (Personal)
**Status: ❌ Belum dibahas**

- [ ] **Auto-backup ke folder sync** — Syncthing, FolderSync, atau SMB. YataGami simpan PDF ke folder yang di-sync.
- [ ] **Export library index** — JSON file berisi metadata semua dokumen (nama, tag, tanggal, path). Backup ini ke cloud.
- [ ] **Import/restore** — Dari JSON backup, reconstruct library.
- [ ] **Local-only mode** — Default, gak perlu cloud. Tapi kalau mau sync, tinggal point ke folder.

**Why P2:** HP bisa hilang/rusak. Dokumen penting harus bisa recover.

---

## 🟢 P3: FUTURE / EXPERIMENTAL

### 15. Advanced CV Features
- [ ] **Signature detection & extraction** — Auto-crop area tanda tangan
- [ ] **Table detection & extraction** — Detect tabel dalam invoice → export sebagai structured data
- [ ] **Handwriting recognition** — ML Kit / custom model untuk catatan tangan
- [ ] **Document de-noising (ML-based)** — Pakai TFLite model lightweight untuk de-noise (bukan generative, tapi restoration)
- [ ] **Barcode/QR detection** — Kalau dokumen ada QR (invoice, e-ticket), auto-detect & extract

### 16. In-App Assistant (SLM Lightweight)
- [ ] **Qwen2.5 0.5B atau TinyLlama 1.1B Q4** — Via llama.cpp
- [ ] **Smart Filename Generator** — Dari OCR text, generate filename yang deskriptif
- [ ] **In-App Help Assistant** — "Bang, gimana caranya scan KTP?" → jawaban dari SLM lokal
- [ ] **Document Summarization** — Summarize isi dokumen panjang (syarat: OCR dulu)

### 17. Multi-Device / Ecosystem
- [ ] **WiFi Direct transfer** — Kirim PDF dari HP ke laptop tanpa internet
- [ ] **Web interface (local)** — Buka `http://192.168.1.x:8080` di laptop → download dokumen dari HP

---

## 🗺️ RECOMMENDED DEVELOPMENT ORDER

### Sprint 1: "Core Scanning" (P0)
```
Week 1-2:
├── Document Detection Algorithm (quad detection)
├── Perspective Warp & Deskew
├── Basic Enhancement (Tier 1 & 2)
├── Scanning UX Flow (camera + overlay + auto-capture)
└── Review & Edit Screen (basic)
```

### Sprint 2: "Solid Foundation" (P0 + P1)
```
Week 3-4:
├── App Architecture & State Management
├── Document Library / Home Screen
├── Export options (images, ZIP, share, print)
├── Edge Cases & Error Handling
└── Settings & Personalization
```

### Sprint 3: "Polish" (P1 + P2)
```
Week 5-6:
├── OCR (ML Kit on-device)
├── Smart filename & classification
├── Widgets & shortcuts
├── Backup & sync
└── Performance benchmarking & tuning
```

### Sprint 4: "Future" (P3)
```
Month 2+:
├── Advanced CV (table, signature, barcode)
├── SLM assistant (optional)
└── Multi-device features
```

---

## ⚠️ CRITICAL AWARENESS: Yang Sering Dilupakan

| # | Yang Sering Ketinggalan | Impact |
|---|------------------------|--------|
| 1 | **Configuration change (rotation)** | App restart, data scan hilang |
| 2 | **HiOS killing app during PDF generation** | PDF corrupt, halaman ilang |
| 3 | **Document curved / glossy / wrinkled** | Warp gagal, hasil jelek |
| 4 | **Finger accidentally in frame** | Corner detection gagal |
| 5 | **Text orientation (terbalik/samping)** | PDF terbalik, user bingung |
| 6 | **Storage penuh saat scan batch** | Crash di tengah proses |
| 7 | **Permission denied & tidak bisa retry** | App stuck, user uninstall |
| 8 | **Thermal throttle saat scan banyak** | Processing lambat, user frustrated |
| 9 | **No manual fallback kalau auto gagal** | User gak bisa apa-apa, app useless |
| 10 | **AMOLED preview vs real PDF difference** | User kaget hasil beda dari preview |

---

*Gap analysis ini menunjukkan: C++ optimization & camera/PDF infra sudah solid, tapi "business logic" inti (deteksi, warp, enhancement, UX scanning) masih 90% belum didesain. Fokus Sprint 1 adalah membuat "end-to-end scanning flow" yang functional, baru polish di Sprint 2-3.*
