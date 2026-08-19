# YataGami C++ Native Optimization Checklist
## GC Pressure & Cache Efficiency — Deep Dive
### For Tecno Pova 7 4G (LJ6) | Helio G100 Ultimate | 8GB RAM

---

## 🎯 Filosofi: "Jangan Biarkan GC Melihat Apapun yang Besar"

Di Android, masalah utama bukan Java heap-nya — tapi **GC pause** dan **bitmap OOM di native heap**. 

**Prinsip YataGami:**
- Semua data piksel > 1MB → **Native Heap (C++)**
- Semua object sementara → **Pool (C++)**
- Java layer cuma pegang **handle/thin wrapper**
- JNI crossing → **minimal & batch**

---

## 🔴 CRITICAL: Native Memory Architecture

### 1. Zero-Copy Bitmap ↔ Native Pipeline

**Masalah default Android:**
```
Camera → Java Bitmap (native heap) → JNI copy → cv::Mat (native heap) → process → JNI copy → Java Bitmap
```
Ini **2x copy** piksel 12MP (~48MB) = 96MB memory bandwidth percuma + GC stress.

**Solusi YataGami — True Zero-Copy:**
- [x] **Camera2 → HardwareBuffer / ImageProxy Direct** — Frame kamera langsung diakses dari buffer native tanpa alokasi Java heap.
- [x] **Direct access via `AndroidBitmap_lockPixels()`** — `bitmapToMatWrap` dan `matToBitmapDirect` membungkus memory buffer langsung menjadi `cv::Mat` tanpa copy ganda.
- [x] **Process langsung di native** — Komputasi C++ mengeksekusi filter langsung ke pointer buffer dengan `ScopedMat`.
- [x] **Unlock & return direct in-place** — `enhanceImageDirect()` menulis hasil pemrosesan langsung ke memory bitmap yang sudah ada, tanpa membuat object Bitmap baru di Java heap.

**Keuntungan:**
- Memory bandwidth hemat 50%
- Nol alokasi Bitmap baru di Java heap saat cycling filter → GC tenang tanpa jank
- Latency turun 30-50%

---

### 2. Native Object Pool — Mat, Buffer, dan Struct

**Masalah:** `cv::Mat::create()`, `malloc()`, `new` di loop processing = **heap fragmentation** + **cache miss** + **allocator contention**.

**Solusi — Custom Native Pool Allocator:**
- [x] **MatPool & BufferPool** — Singleton `BufferPool` native C++ dengan kapasitas 16 Mats per key untuk frame camera dan dokumen resolusi tinggi.
- [x] **Small Object & Struct Scoping** — RAII wrapper `ScopedMat` mengelola siklus hidup buffer sementara tanpa overhead heap allocation.
- [x] **Pre-warm saat startup** — `BufferPool::getInstance().preallocate()` berjalan pada `JNI_OnLoad` untuk memanaskan buffer contour 640x480 dan A4 3508x2480.
- [x] **Borrow/Return Pattern** — `ScopedMat` otomatis mengambil buffer dari pool saat masuk scope dan mengembalikannya ke pool saat destruct, tanpa `malloc`/`free` di hot path.

**Cache benefit:**
- Memory selalu contiguous & reused → **cache hit rate tinggi**
- Gak ada heap fragmentation → allocator gak jalan di hot path
- Working set predictable → OS bisa optimize page mapping

---

### 3. Arena Allocator untuk Processing Satu Halaman

**Konsep:** Satu "arena" = satu block memory besar untuk SEMUA allocation selama processing satu halaman.

```
[Arena 50MB] → Mat temp1 | Mat temp2 | Mat edges | vector<Point> | LUT | dst
                ↑_________________________________________________↓
                         Semua dalam satu contiguous block!
```

- [x] **Arena per thread/halaman** — `MemoryArena` mengalokasikan blok memori kontigu 48MB per thread pemrosesan (`getThreadLocalArena`).
- [x] **Bump pointer allocation** — `arena.allocate()` melakukan alokasi dengan pointer bumping dalam ~1ns dengan cache-line alignment 64-byte (Cortex-A76).
- [x] **Arena reset** — `ScopedArenaReset` otomatis me-reset offset ke 0 saat keluar dari scope, mereklamasi seluruh buffer secara instan dalam O(1).
- [x] **Cache locality maksimal** — Seluruh data pemrosesan tersimpan dalam satu blok kontigu memori berurutan (spatial locality sempurna pada L2/L3 cache).

**Keuntungan vs individual malloc/free:**
- Allocation: ~1ns vs ~100ns
- Cache hit: jauh lebih tinggi (data berdekatan)
- Gak ada fragmentation
- Gak ada lock contention (thread-local arena)

---

## 🔴 CRITICAL: Cache Locality Optimization

### 4. Row-Major Access & Loop Tiling

**Masalah umum OpenCV:** Access pattern gak optimal = **cache miss** berjamaah.

**cv::Mat adalah row-major.** Access pattern yang benar:
```cpp
// ✅ BENAR — Row-major, cache friendly
for (int y = 0; y < rows; y++) {
    uchar* row = mat.ptr(y);
    for (int x = 0; x < cols; x++) {
        row[x] = ...;  // Sequential access = cache prefetch happy
    }
}

// ❌ SALAH — Column-major, cache thrashing
for (int x = 0; x < cols; x++) {
    for (int y = 0; y < rows; y++) {
        mat.at<uchar>(y, x) = ...;  // Lompat-lompat per row = cache miss tiap access
    }
}
```

- [x] **Audit SEMUA loop manual** — Seluruh pipeline native C++ mengakses memori piksel secara strictly row-major (`ptr<uchar>(y)` atau SIMD OpenCV).
- [x] **Gunakan `ptr<T>(y)` / `cv::LUT`** — Mengeliminasi `at<T>(y,x)` dari hot loop sehingga tidak ada bounds check overhead di jalur eksekusi.
- [x] **L1 Cache LUT Optimization** — Glare suppression dan dynamic gamma diimplementasikan menggunakan 256-byte LUT yang pas dalam L1 data cache Cortex-A76 (~1ns access).

**Tiling example untuk 4000x3000 image:**
```cpp
const int TILE_SIZE = 128;
for (int tileY = 0; tileY < height; tileY += TILE_SIZE) {
    for (int tileX = 0; tileX < width; tileX += TILE_SIZE) {
        // Process tile [tileX, tileY] → [tileX+TILE_SIZE, tileY+TILE_SIZE]
        // Semua data dalam tile ini muat di L2 cache A76 (~256KB-1MB)
    }
}
```

---

### 5. Data Structure Layout untuk Cache Line

**Cache line ARM Cortex-A76 = 64 bytes.** Satu cache line bisa hold 16 int32 atau 64 uint8.

**Struct of Arrays (SoA) vs Array of Structs (AoS):**

```cpp
// ❌ AoS — Cache line isi campur-campur, prefetch bingung
struct Pixel { uint8_t r, g, b, a; };  // 4 bytes
vector<Pixel> pixels;  // 4000x3000 = 12M elements

// ✅ SoA — Cache line isi data homogen, prefetch senang
struct ImageData {
    vector<uint8_t> r;  // 12MB contiguous
    vector<uint8_t> g;  // 12MB contiguous
    vector<uint8_t> b;  // 12MB contiguous
};
```

- [x] **Untuk LUT & lookup tables** — Pakai struktur contiguous independen per channel (Gamma LUT, Glare LUT).
- [x] **Untuk point/vertex data** — `orderQuadCorners` menggunakan stack-allocated memory terpisah (`sums` & `diffs`) tanpa alokasi heap vector.
- [x] **Align data ke 64-byte boundary** — `alignas(64)` diaplikasikan ke semua array LUT dan scratch buffer agar sejajar persis dengan cache line ARM Cortex-A76.

---

### 6. Prefetch Hint untuk Cortex-A76

ARMv8 punya `PRFM` (prefetch memory) instruction. GCC/Clang support via `__builtin_prefetch()`.

```cpp
for (int y = 0; y < rows; y++) {
    uchar* row = mat.ptr(y);
    uchar* nextRow = mat.ptr(y + 1);
    __builtin_prefetch(nextRow, 0, 3);  // Prefetch next row ke L1 cache
    for (int x = 0; x < cols; x++) {
        // Process row[x]
    }
}
```

- [x] **Prefetch row N+1 saat process row N** — Menggunakan macro `YATAGAMI_PREFETCH_READ` (`__builtin_prefetch(ptr, 0, 3)`) yang mengaktifkan instruksi hardware ARMv8 `PRFM` ke L1 data cache.
- [x] **Prefetch target buffer write** — `YATAGAMI_PREFETCH_WRITE` (`__builtin_prefetch(ptr, 1, 3)`) menyiapkan cache line memori sebelum proses penulisan piksel bitmap.
- [x] **Controlled prefetch stride** — Jarak prefetch dibatasi 1 baris ke depan untuk mencegah cache pollution pada cache Cortex-A76.

---

## 🔴 CRITICAL: JNI Boundary Optimization

### 7. Batch JNI Calls — Jangan Cross Boundary Tiap Frame

**Masalah:** JNI boundary crossing cost ~100-300ns. Kelihatan kecil, tapi kalau tiap piksel diproses via JNI = bencana.

**Solusi:**
- [x] **Satu JNI call = satu halaman penuh** — `nativeProcessPageFull(...)` mengeksekusi deteksi sudut subpixel, perspective warping, deskewing, dan auto-enhancement dalam satu kali crossing JNI.
- [x] **Jangan panggil JNI per operasi individual** — Mengeliminasi *JNI hopping overhead* di pipeline pemrosesan halaman dokumen.
- [x] **Return data terstruktur & direct write** — Sudut dokumen dan piksel tertulis langsung ke memory target tanpa alokasi Java berulang.
- [x] **Zero Reverse-JNI** — Pemrosesan berjalan murni di native C++ tanpa callback balik yang memperlambat execution loop.

**JNI signature optimal untuk YataGami:**
```cpp
// ✅ Satu call, semua processing
JNIEXPORT void JNICALL
Java_com_yatagami_engine_DocumentProcessor_nativeProcessPage(
    JNIEnv* env, jobject thiz,
    jobject inBitmap, jobject outBitmap,
    jint tier, jboolean detectSkew,
    jobject outResult  // DetectionResult struct
);

// ✅ Batch process multiple pages
JNIEXPORT void JNICALL
Java_com_yatagami_engine_DocumentProcessor_nativeProcessBatch(
    JNIEnv* env, jobject thiz,
    jobjectArray inBitmaps, jobjectArray outBitmaps,
    jint count, jint tier
);
```

---

### 8. DirectBuffer untuk Data Transfer Besar

**Masalah:** Passing large arrays (LUT, kernel, config) via JNI = copy.

**Solusi:**
- [x] **Gunakan `ByteBuffer.allocateDirect()` di Java** — `DocumentDetector` mengalokasikan direct native buffer 32-byte satu kali saat inisialisasi.
- [x] **Pass `ByteBuffer` ke C++ via JNI** — Pointer diakses langsung di C++ via `env->GetDirectBufferAddress()` tanpa duplikasi memori.
- [x] **Zero JNI Array Allocation di Camera Loop** — Mengeliminasi alokasi `NewFloatArray` di setiap frame deteksi dokumen real-time.

---

### 9. Critical Native & @FastNative

**Penggunaan untuk YataGami:**
- [x] **Static Native Registration via `RegisterNatives()`** — Di-register secara eksplisit pada `JNI_OnLoad` untuk `DocumentDetector` dan `ImageProcessor`.
- [x] **Eliminasi Dynamic Runtime Lookup (`dlsym`)** — ART (Android Runtime) menghubungkan fungsi native secara langsung di memori saat app start, memangkas 2-3x overhead dispatch JNI.
- [x] **Direct & Full Pipeline Dispatch** — `nativeDetectDocumentDirect` dan `nativeProcessPageFull` terikat secara statis untuk performa pemindaian instan.

---

## 🟠 HIGH: GC Pressure Elimination

### 10. Java Layer — Thin Wrapper Saja

**Prinsip:** Java object untuk image processing harus **se-thin mungkin**.

- [ ] **Java `DocumentPage` cuma pegang `long nativePtr`** — Bukan Bitmap! NativePtr = pointer ke native struct yang hold Mat + metadata
- [ ] **Gak ada `Bitmap` object di Java untuk intermediate** — Semua intermediate Mat di native pool
- [ ] **Java `PdfBuilder` cuma queue & orchestrate** — Processing semua di native thread
- [ ] **Final output Bitmap (untuk preview) saja yang di Java** — Dan itu pun reuse dari pool

**Contoh wrapper:**
```kotlin
class NativePage(private val nativePtr: Long) {
    fun getThumbnail(): Bitmap { /* lock preview Mat dari native */ }
    fun getWarped(): Bitmap { /* lock warped Mat dari native */ }
    fun release() { nativeRelease(nativePtr) }  // Return ke pool
}
```

---

### 11. Bitmap Reuse (`inBitmap`) + Native Pool

**Android `BitmapFactory.Options.inBitmap`** — Decode ke Bitmap yang sudah ada (reuse memory).

- [ ] **Maintain Bitmap pool di Java** — 5 Bitmap 12MP untuk reuse. Gak perlu allocate baru tiap capture.
- [ ] **Sinkronkan dengan native MatPool** — Bitmap pool di Java ↔ MatPool di native. Satu Bitmap = satu Mat wrapper.
- [ ] **Saat capture baru** → ambil Bitmap dari pool → lockPixels → wrap jadi Mat → process → unlock → return ke pool.
- [ ] **Gak pernah `Bitmap.createBitmap()` di hot path** — Selalu reuse.

---

### 12. Eliminate Auto-Boxing di Hot Path

**Masalah tersembunyi:** `Integer`, `Float`, `Boolean` di Kotlin/Java = object allocation.

- [ ] **JNI parameter pakai primitive** — `int`, `float`, `boolean`, bukan `Integer`, `Float`
- [ ] **Return value pakai primitive array atau DirectBuffer** — Jangan `List<Integer>` atau `Map<String, Float>`
- [ ] **Kotlin `IntArray`, `FloatArray` lebih baik dari `Array<Int>`** — Primitive array vs boxed array
- [ ] **Data class hasil deteksi** — `@JvmInline value class DetectionScore(val packed: Long)` — Inline class, gak allocate object.

---

## 🟠 HIGH: Thread & Memory Model

### 13. Native Thread dengan Dedicated Stack & Affinity

- [ ] **Native processing thread** — Buat thread C++ sendiri via `pthread_create` atau `std::thread`, bukan Kotlin coroutine dispatcher. Coroutine masih di JVM thread pool.
- [ ] **Pin thread ke big core (CPU 0-1)** — `sched_setaffinity()` untuk native thread processing
- [ ] **Stack size minimal** — `pthread_attr_setstacksize()` = 512KB cukup untuk CV thread. Default 8MB = waste virtual memory.
- [ ] **Thread-local arena** — Setiap processing thread punya arena sendiri. Gak ada contention antar thread.

---

### 14. Memory Barrier & False Sharing

**False sharing:** Dua thread akses data beda tapi dalam cache line yang sama → cache line bouncing = lambat.

- [ ] **Pad struct ke 64 byte** — `struct alignas(64) ThreadData { ... };` → tiap thread punya cache line sendiri
- [ ] **Jangan share writable data antar thread** — Kalau perlu share, pakai `std::atomic` dengan memory ordering yang tepat
- [ ] **Ring buffer (SPSC) antar thread** — Single producer single consumer lock-free queue untuk pass frame antar thread. `alignas(64)` untuk head/tail pointer.

---

## 🟡 MEDIUM: Compiler & Linking Optimization

### 15. LTO + Profile-Guided Optimization (PGO)

- [ ] **Link Time Optimization (LTO)** — `-flto` di compile & link. Compiler bisa inline cross-module, eliminate dead code lebih agresif.
- [ ] **Profile-Guided Optimization** — Compile dengan `-fprofile-generate` → jalankan app, process beberapa dokumen → compile ulang dengan `-fprofile-use`. Hasil: branch prediction optimal, hot path di-inline.
- [ ] **ThinLTO** — Kalau Full LTO terlalu lambat build, pakai `-flto=thin`. Hampir sebagus Full LTO tapi build lebih cepat.

---

### 16. Function Inlining & Hot Path

- [ ] **`ALWAYS_INLINE` untuk hot function** — `__attribute__((always_inline))` untuk function yang dipanggil per-pixel (LUT apply, threshold, etc.)
- [ ] **Mark cold function** — `__attribute__((cold))` untuk error handling, logging. Compiler akan optimize untuk size & put di section terpisah.
- [ ] **Branch hint** — `__builtin_expect(condition, expected)` — Bantu branch predictor. Contoh: `if (__builtin_expect(rare_error, 0)) { handle_error(); }`

---

## 🟢 BONUS: YataGami-Specific Native Optimizations

### 17. Document Quad Detection — Cache-Friendly Hough

Hough Transform untuk skew detection = access pattern random (voting ke accumulator). **Cache killer.**

- [ ] **Accumulator array ukuran kecil** — Resolution Hough 180x (diagonal/2) cukup. Jangan terlalu fine.
- [ ] **Accumulator pakai `uint16_t` bukan `int32_t`** — 2x lebih kecil = lebih muat di cache.
- [ ] **Hough space di-split per angle range** — Process 0-60°, 60-120°, 120-180° secara terpisah. Masing-masing accumulator lebih kecil = cache friendly.
- [ ] **Probabilistic Hough** — Hanya sample subset edge piksel (bukan semua). 10-20% sample cukup untuk dokumen.

---

### 18. CLAHE — Tile-based = Sudah Cache Friendly

CLAHE sudah tile-based by design. Tapi masih bisa dioptimasi:
- [ ] **Tile size = 64x64 atau 128x128** — Muat di L1 cache A76 (64KB).
- [ ] **Histogram pakai `uint16_t[256]`** — Bukan `int[256]`. Lebih kecil, lebih cepat zeroing.
- [ ] **Clip limit di-compute sekali per tile** — Jangan recompute per piksel.
- [ ] **LUT per tile di-precompute** — `lut[tileIdx][256]`. Access sequential saat apply.

---

### 19. Bilateral Filter — Approximation untuk Real-time

Bilateral filter asli = O(N*r^2), sangat lambat. Untuk preview real-time:
- [ ] **Gunakan `cv::bilateralFilter` dengan `d=5` (radius kecil)** — Cukup untuk noise reduction dokumen.
- [ ] **Atau pakai Approximate Bilateral** — Gaussian spatial + range separable. 10x lebih cepat dengan hasil hampir sama.
- [ ] **Hanya apply di region of interest** — Kalau sudah detect dokumen quad, bilateral hanya di dalam quad. Jangan process seluruh frame.

---

### 20. Warp/Perspective Transform — Precompute Map

```cpp
// Precompute remap map sekali
Mat map1, map2;
initUndistortRectifyMap(..., map1, map2);  // atau custom perspective map

// Apply ke semua frame dengan map yang sama
remap(src, dst, map1, map2, INTER_LINEAR);
```

- [ ] **Map di-precompute saat detect quad** — Simpan di native pool.
- [ ] **Kalau dokumen gak bergerak** → Reuse map, gak perlu recompute. Processing jadi hampir instant.
- [ ] **Map pakai `CV_16SC2` (fixed-point)** — Lebih cepat dari float map.

---

## 📊 Expected Impact Summary

| Optimization | GC Pressure | Cache Hit | Speedup | Effort |
|-------------|-------------|-----------|---------|--------|
| Zero-copy Bitmap | ⬇️⬇️⬇️ Dramatic | ⬆️ | 30-50% | Medium |
| Native Pool (Mat/Buffer) | ⬇️⬇️⬇️ Dramatic | ⬆️⬆️ | 20-40% | Medium |
| Arena Allocator | ⬇️⬇️ | ⬆️⬆️⬆️ | 10-20% | Low |
| Row-major + Tiling | — | ⬆️⬆️⬆️ | 20-60% | Low |
| SoA vs AoS | — | ⬆️⬆️ | 15-30% | Medium |
| Prefetch | — | ⬆️⬆️ | 5-15% | Low |
| Batch JNI | ⬇️⬇️ | — | 10-30% | Low |
| @FastNative/@CriticalNative | — | — | 2-3x call speed | Low |
| DirectBuffer | ⬇️⬇️ | — | 20-40% | Low |
| Thin Java Wrapper | ⬇️⬇️⬇️ | — | 50%+ less GC | Medium |
| Bitmap Reuse (inBitmap) | ⬇️⬇️⬇️ | — | 30-50% | Low |
| Thread Affinity + Small Stack | — | ⬆️ | 10-20% | Low |
| False Sharing Pad | — | ⬆️⬆️ | 10-30% | Low |
| LTO + PGO | — | ⬆️⬆️⬆️ | 15-30% | High |
| ALWAYS_INLINE hot func |  | ⬆️⬆️ | 5-15% | Low |
| Approximate Bilateral | — | ⬆️ | 5-10x faster | Medium |
| Precompute Warp Map | ⬇️ | ⬆️⬆️ | 80%+ untuk reuse | Low |

---

## 🗺️ Implementation Priority (Pova 7 4G)

### Phase 1: "GC Bebas" (Minggu 1)
1. Zero-copy Bitmap pipeline (`lockPixels`)
2. Native MatPool + BufferPool
3. Batch JNI (satu call per halaman)
4. @FastNative/@CriticalNative
5. Bitmap reuse (`inBitmap`)

### Phase 2: "Cache Monster" (Minggu 2)
6. Arena allocator per halaman
7. Row-major audit + loop tiling
8. SoA untuk struct deteksi
9. Prefetch hint
10. Thread affinity + small stack

### Phase 3: "Compiler Magic" (Minggu 3)
11. LTO + PGO
12. ALWAYS_INLINE + branch hint
13. False sharing padding
14. DirectBuffer untuk config

### Phase 4: "Algorithm Tuning" (Minggu 4)
15. Approximate bilateral filter
16. Precompute warp map
17. Cache-friendly Hough
18. Region-of-interest processing

---

*Checklist ini fokus pada: (1) Memindahkan SEMUA memory pressure ke native heap & pool, (2) Maximizing cache locality via data layout & access pattern, (3) Minimizing JNI boundary crossing, (4) Leveraging compiler optimization untuk ARM Cortex-A76.*
*Target: GC pause hampir nol, cache hit rate > 90%, processing 12MP < 300ms di Tecno Pova 7 4G.*
