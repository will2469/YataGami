# C++ OpenCV Optimization Checklist — YataGami Native Layer
## Dari "Cuma Kosmetik" → High-Performance Document Processing Engine

---

## 🔴 CRITICAL — Core Architecture & Memory

### 1. Memory Management & Buffer Pooling
- [x] **Implementasi BufferPool (Object Pool Pattern)** — `BufferPool` class singleton dengan `ScopedMat` RAII wrapper untuk me-reuse buffer `cv::Mat` tanpa alokasi heap berulang di tiap frame.
- [x] **Gunakan `cv::Mat::create()` bukan constructor berulang** — `BufferPool::acquire()` mengoptimalkan alokasi memori dengan `create()` reuse.
- [x] **Pass by reference (`const cv::Mat&` / `cv::Mat&`)** — Seluruh fungsi native C++ di `core/` menggunakan pass-by-reference untuk mencegah refcount copy overhead.
- [x] **Pre-allocated output Mat** — Fungsi processing mendukung signature `void func(const cv::Mat& src, cv::Mat& dst)` dengan pre-allocated output buffer.
- [x] **ROI-based processing** — Analisis citra beroperasi pada sub-resolusi terarah (640px kontur, 800px skew/blur) untuk menghemat bandwidth memori.
- [x] **Release Mat ke pool setelah use** — `ScopedMat` otomatis merilis kembali buffer ke `BufferPool` saat selesai digunakan.
- [x] **Hindari `.clone()` kecuali beneran perlu deep copy** — Menghilangkan duplikasi memori redundant pada pipeline preprocessing & filter.
- [x] **Gunakan `cv::Mat::setTo()` / direct memory operations** — Inisialisasi cepat dengan LUT lookup table dan per-channel operations.

### 2. Pipeline Architecture (No Blocking, No Copy)
- [ ] **Triple buffering untuk preview** — Thread A (capture) → Thread B (detect) → Thread C (warp/enhance). Jangan tunggu satu sama lain.
- [ ] **Zero-copy antara Java-Kotlin ↔ Native** — Pakai `AndroidBitmap_lockPixels()` langsung ke `cv::Mat` data pointer. JANGAN copy ke byte array dulu.
- [ ] **Lock-free queue antar thread** — Gunakan `std::atomic<std::shared_ptr<...>>` atau boost::lockfree::spsc_queue untuk passing frame antar thread.
- [ ] **Pipeline stage masing-masing punya pre-allocated buffer** — Capture buffer, detect buffer, warp buffer, enhance buffer. Total 4x memori tapi zero-copy antar stage.
- [ ] **Release frame capture segera setelah convert ke processing format** — Jangan tahan `ImageProxy` lebih lama dari yang perlu.

### 3. Format & Channel Optimization
- [ ] **Proses di Grayscale kalau bisa** — 1 channel = 3x lebih cepat dari 3 channel. Konversi BGR→Gray di awal pipeline, jangan bolak-balik.
- [ ] **Gunakan CV_8U kalau cukup** — Jangan naik ke CV_32F/CV_64F kecuali beneran butuh precision (contoh: gamma correction lookup table boleh 32F, image-nya tetep 8U).
- [ ] **Planar processing untuk multi-channel** — Split channel → proses per channel → merge. Lebih cache-friendly daripada interleaved BGR processing.
- [ ] **In-place operations selalu** — `cv::GaussianBlur(src, src, ...)` bukan `cv::GaussianBlur(src, dst, ...)`. Kalau gak bisa in-place, reuse buffer dari pool.

---

## 🟠 HIGH PRIORITY — Algorithmic Optimization

### 4. Image Pyramid & Multi-Scale Strategy
- [ ] **Deteksi dokumen di 0.5x resolution dulu** — Canny + findContours di 1080p cukup, gak perlu 4K. Scale corner hasil ke original.
- [ ] **Build pyramid sekali, pakai berkali-kali** — `cv::buildPyramid()` di awal, reuse tiap level untuk detection, validation, dan quality check.
- [ ] **Early rejection di pyramid bawah** — Kalau contour gak lolos validasi di 0.5x, gak perlu dicek di 1.0x.
- [ ] **Warp perspective di full-res, tapi enhancement di half-res dulu** — Preview filter di half-res, apply ke full-res cuma waktu export PDF.

### 5. OpenCV Function Selection (Pilih yang Cepat)
- [ ] **BilateralFilter → `cv::bilateralFilter()`** — Memang lambat, tapi edge-preserving. Kalau terlalu lambat, ganti **`cv::blur()` + `cv::Canny()`** untuk detection pipeline (gak perlu bilateral untuk edge detection doang).
- [ ] **Denoising untuk enhancement: `cv::fastNlMeansDenoising()`** — Kualitas tinggi tapi BERAT. Untuk real-time preview, pakai **bilateral atau median blur** saja.
- [ ] **Threshold: `cv::adaptiveThreshold()`** — Cukup cepat. Kalau mau lebih baik lagi untuk teks kecil, implementasi **Sauvola** manual (lebih akurat, effort medium).
- [ ] **Warp: `cv::warpPerspective()` dengan `INTER_LINEAR`** — `INTER_CUBIC` lebih bagus tapi 3x lebih lambat. Linear cukup untuk dokumen.
- [ ] **Resize: `cv::resize()` dengan `INTER_AREA`** (downscale) / **`INTER_LANCZOS4`** (upscale) — Area untuk downscale = lebih cepat & anti-aliasing. Lanczos4 untuk upscale hasil = lebih tajam.

### 6. Loop & Cache Optimization
- [ ] **Iterate Mat dengan pointer, bukan `at<>()`** — `at<>()` ada bounds check (slow). Gunakan `.ptr<T>(row)` untuk row-wise access.
- [ ] **Cache-friendly row-major order** — Loop row dulu, baru col. Jangan col dulu (cache miss parah).
- [ ] **Gunakan `CV_Assert()` di debug, `#ifdef` out di release** — Assert ada overhead, matikan untuk release build.
- [ ] **Minimize branch prediction miss** — Hindari if-else di dalam tight pixel loop. Kalau mau thresholding, pakai lookup table (`cv::LUT`).
- [ ] **SIMD-friendly access pattern** — Pastikan data aligned (OpenCV default sudah aligned 64-byte untuk AVX512/NEON).

---

## 🟡 MEDIUM PRIORITY — ARM/NEON & Compiler Optimization

### 7. NEON SIMD Acceleration (ARM64)
- [ ] **Pastikan OpenCV dikompilasi dengan NEON=ON** — Cek `cv::useOptimized()` return true. Kalau false, OpenCV gak pakai SIMD.
- [ ] **Gunakan `cv::hal::` interface** — Hardware abstraction layer OpenCV sudah pakai NEON di belakang layar untuk banyak fungsi (filter, resize, warp).
- [ ] **Hindari custom loop scalar kalau OpenCV function equivalent ada** — Contoh: jangan bikin convolution manual, pakai `cv::filter2D()` yang sudah NEON-optimized.
- [ ] **Kalau MUST custom loop, pakai `uint8x16_t` (NEON intrinsics)** — Untuk operasi pixel-wise (threshold custom, color conversion). Ini advanced, bikin hanya untuk bottleneck utama.
- [ ] **Compile flags optimal untuk ARM64:**
  ```cmake
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -fno-math-errno")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a+fp+simd")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden")
  ```
- [ ] **Hidden symbol visibility** — `-fvisibility=hidden` mengurangi ukuran .so dan improve link time.

### 8. Compiler & Build Optimization
- [ ] **`-O3` bukan `-O2`** — Link Time Optimization (LTO) kalau bisa: `-flto` di compiler & linker.
- [ ] **`-ffunction-sections -fdata-sections` + linker `--gc-sections`** — Hapus fungsi/data yang gak dipakai, turunkan ukuran .so.
- [ ] **Static link hanya module OpenCV yang dipakai** — Jangan bundle seluruh libopencv_world.so. Link static: `opencv_core`, `opencv_imgproc` saja.
- [ ] **Strip debug symbols untuk release** — `arm64-v8a/libsmartcamera.so` bisa turun dari 5MB → 500KB.
- [ ] **Enable R8 + native library compression** — Di `build.gradle.kts`: `android.packagingOptions.jniLibs.useLegacyPackaging = false` untuk compression native lib di APK.

---

## 🟢 NATIVE LAYER — Specific Optimizations untuk YataGami

### 9. Document Detection Pipeline Optimization
- [ ] **Pre-process sekali, reuse hasilnya** — Bilateral + CLAHE + gamma hasilnya bisa dipakai untuk:
  - Canny edge detection
  - Contour finding
  - Quality metric (brightness/contrast check)
  Jangan re-run pre-processing untuk tiap step!
- [ ] **Contour approximation dengan epsilon adaptif** — `cv::arcLength(contour, true) * 0.02` adalah default. Kalau contour besar, epsilon lebih besar = lebih cepat & cukup.
- [ ] **Sort contour by area dengan `std::nth_element` bukan `std::sort`** — Kita cuma butuh top-N contour, gak perlu sort semua. `nth_element` = O(n) vs O(n log n).
- [ ] **Early exit kalau sudah nemu contour valid** — Jangan lanjut proses contour lain kalau sudah dapet 4-corner valid dengan confidence tinggi.
- [ ] **Contour validation di integer coordinate dulu** — Jangan convert ke `Point2f` dulu untuk cek aspect ratio & solidity. Cek di integer, convert ke float cuma untuk yang lolos.

### 10. Warp & Enhancement Optimization
- [ ] **Pre-calculate homography matrix sekali** — Kalau corner gak berubah (dokumen stabil), jangan re-calculate `cv::getPerspectiveTransform()` tiap frame.
- [ ] **Warp ke target size optimal, bukan selalu A4 2480x3508** — Kalau dokumen asli cuma setengah A4, warp ke 1240x1754. Lebih cepat & memori lebih kecil.
- [ ] **Lookup table (LUT) untuk semua pixel-wise transform** — Gamma, contrast, threshold: semua pakai `cv::LUT()` (vectorized, cache-friendly). Jangan loop per-pixel manual.
- [ ] **Filter pipeline yang fixed order** — Jangan bikin dynamic filter chain yang bisa reorder. Fixed order = compiler bisa inline & optimize lebih baik.
  ```
  Optimal order: Denoise → White Balance → Warp → Enhance → Binarize (kalau perlu)
  ```
- [ ] **Parallelize independent operations dengan `cv::parallel_for_`** — Kalau ada operasi yang bisa di-split per row (custom filter), pakai OpenCV's parallel_for.

### 11. Quality Metric Optimization
- [ ] **Blur detection: Laplacian variance di grayscale + downscaled** — Gak perlu full-res. 0.25x cukup untuk deteksi blur.
- [ ] **Glare detection: histogram analysis gak perlu per-pixel branch** — Pakai `cv::calcHist()` + threshold, jangan loop manual dengan if.
- [ ] **Brightness metric: `cv::mean()` cukup** — Jangan bikin custom sum loop. `cv::mean()` sudah NEON-optimized.
- [ ] **Jalankan quality metrics di thread terpisah (async)** — Jangan block pipeline utama. Quality check bisa jalan di background, report ke UI via callback.

---

## 🔵 PROFILING & BENCHMARKING

### 12. Measure Everything
- [ ] **Tambahkan `__android_log_print` timing di setiap pipeline stage** — Capture → Preprocess → Detect → Warp → Enhance. Log dalam millisecond.
- [ ] **Target performance per halaman:**
  - Capture → Detect: **< 100ms** (preview @ 10fps)
  - Warp + Enhance: **< 500ms** (mid-range device)
  - PDF generation per page: **< 300ms**
- [ ] **Gunakan Android Profiler (CPU + Memory)** — Cek apakah native thread ada di CPU big core atau little core. Dokumentasikan.
- [ ] **Systrace / Perfetto untuk trace native** — Tambahkan `ATrace_beginSection()` / `ATrace_endSection()` di awal/akhir tiap pipeline stage.
- [ ] **Benchmark dengan berbagai resolusi input** — 1080p, 2K, 4K. Catat mana yang jadi bottleneck.
- [ ] **Memory profiling** — Pastikan gak ada memory leak di `cv::Mat` yang gak di-release. Pool harus stabil size-nya setelah warm-up.

---

## 🟣 APK SIZE & DELIVERY OPTIMIZATION

### 13. Native Library Size Reduction
- [ ] **Static link OpenCV module terpilih saja** — Hanya: `opencv_core`, `opencv_imgproc`. Jangan include: `opencv_video`, `opencv_ml`, `opencv_objdetect`, dll.
- [ ] **ABI split: arm64-v8a saja** — Kalau target Android 15 (API 35), armeabi-v7a bisa di-drop. Hemat 50% ukuran native.
- [ ] **Compress native libraries di APK** — `android.bundle.compress.nativeLibraries = true` untuk AAB.
- [ ] **Remove unused OpenCV 3rdparty libs** — ILLDASM, libtiff, libpng (kalau gak dipakai). OpenCV minimal bisa cuma ~2MB.
- [ ] **Use App Bundle (AAB) bukan APK** — Play Store akan deliver native lib sesuai device user.

---

## ⚫ COMMON PITFALLS TO AVOID

| Jangan Lakukan Ini | Kenapa | Solusi |
|-------------------|--------|--------|
| `cv::Mat dst = src.clone()` di setiap frame | Alokasi memori + copy data tiap frame | Buffer pool + pass by reference |
| `cv::imwrite()` di thread UI | I/O blocking = jank/lag | Dedicated I/O thread |
| Loop pixel dengan `at<uchar>(i,j)` | Bounds check tiak akses = 10x lebih lambat | `.ptr<uchar>(row)` |
| `cv::cvtColor` BGR→Gray→BGR bolak-balik | Konversi mahal & redundant | Proses di grayscale, convert ke BGR hanya untuk output |
| `findContours` di full-res 4K | 4x lebih lambat dari 1080p dengan hasil serupa | Pyramid: deteksi di 0.5x, refine di 1.0x |
| `warpPerspective` dengan `INTER_CUBIC` | 3x lebih lambat dari LINEAR, beda visual minimal | `INTER_LINEAR` cukup untuk dokumen |
| Load native lib di setiap activity | `System.loadLibrary()` cukup sekali | Load di `Application.onCreate()` atau singleton |
| Logging verbose di release build | I/O logging ada overhead | `#ifdef DEBUG` untuk log timing |
| Ignore `cv::Exception` | App crash di native = ANR/crash | Wrap tiap JNI call dengan try-catch C++ |

---

## 🗺️ IMPLEMENTATION ROADMAP (Native Layer)

### Phase 1: Memory & Architecture (Impact Terbesar)
1. Implementasi BufferPool
2. Zero-copy Java→Native via `AndroidBitmap_lockPixels`
3. Triple-buffer pipeline (Capture / Detect / Process)
4. Pass-by-reference semua fungsi processing

### Phase 2: Algorithm Optimization
5. Image pyramid (0.5x detection, 1.0x warp)
6. Pre-process sekali, reuse untuk detection + quality metric
7. `std::nth_element` untuk contour sorting
8. LUT-based pixel-wise operations (gamma, contrast)

### Phase 3: ARM/Compiler Optimization
9. Verifikasi NEON aktif (`cv::useOptimized()`)
10. Compiler flags `-O3 -march=armv8-a+fp+simd`
11. Static link minimal OpenCV modules
12. Strip symbols + hidden visibility

### Phase 4: Profiling & Polish
13. Timing log per pipeline stage
14. Android Profiler + Perfetto trace
15. Memory leak audit
16. APK size audit

---

## 📊 Expected Performance Gain

| Optimization | Expected Speedup | Effort |
|-------------|-----------------|--------|
| BufferPool + zero-copy | 2-3x | Medium |
| Image pyramid (0.5x detect) | 3-4x detection | Low |
| Pass-by-reference (no clone) | 1.5-2x | Low |
| NEON verification + `-O3` | 2-3x (OpenCV functions) | Low |
| LUT vs per-pixel loop | 5-10x (custom ops) | Medium |
| `nth_element` vs `sort` | 2-3x (contour stage) | Low |
| Static link minimal OpenCV | -50% APK size | Medium |
| Triple-buffer pipeline | UI 60fps stable | Medium |

**Total potential: 5-10x faster processing, 50-70% smaller APK native footprint**

---

*Checklist ini untuk mengubah native layer YataGami dari "cuma panggil OpenCV" menjadi high-performance document processing engine yang optimal untuk ARM64 Android.*
