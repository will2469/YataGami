# Image Improvement Checklist — MVP → Production Ready
## Aplikasi: YataGami (Smart Camera to PDF)

---

## 🔴 CRITICAL — Harus Diimplementasi Dulu

### 1. Pre-Processing Pipeline (Sebelum Deteksi Dokumen)
- [x] **Noise Reduction awal** — Bilateral filter sebelum Canny edge detection. Menghilangkan noise sensor kamera terutama low-light sambil menjaga tepi dokumen tetap tajam.
- [x] **Auto-Contrast / CLAHE pre-processing** — Contrast-Limited Adaptive Histogram Equalization sebelum edge detection agar tepi dokumen keluar kontras walau cahaya redup.
- [x] **Gamma Correction dinamis** — Deteksi kecerahan gambar (mean intensity) dan koreksi gamma dinamis via LUT (gamma < 1 kalau gelap, gamma > 1 kalau overexposed).
- [x] **Shadow Removal** — Morphological background estimation & difference normalization untuk menghilangkan bayangan tangan/objek pada kertas.

### 2. Angle Detection & Deskewing (Rotasi Teks)
- [x] **Text-based Skew Detection** — Analisis garis teks horizontal morfologis + `cv::minAreaRect` untuk mendeteksi sudut kemiringan teks secara presisi (rentang -25° hingga +25°).
- [x] **Auto-rotate ke 0°** — Otomatis rotasi affine balikan (`deskewImage`) jika skew angle >= 0.5° sehingga baris teks sejajar horizontal 0° setelah warp perspective.
- [x] **Orientation Classification** — Analisis densitas gradien Sobel (`autoFixOrientation`) untuk otomatis memutar dokumen 90° jika posisi gambar/teks tertidur dalam wadah portrait.

### 3. Smart Filter Selection (Auto-Enhance)
- [x] **Blur Detection (Laplacian Variance)** — Menghitung varians operator Laplacian (`calculateBlurScore`). Peringatan otomatis pada UI FilterScreen jika skor < 85 ("dokumen agak buram").
- [x] **Glare / Overexposure Detection** — Deteksi rasio piksel jenuh > 250 (`calculateGlareRatio`) & kompresi highlight specular glare (`suppressGlare`) pada kanal luminansi Lab.
- [x] **Auto-filter recommendation** — Analisis otomatis karakteristik citra (`recommendFilterMode` berbasis chroma dispersion, bimodal white ratio, dan mean luminance) untuk memilih filter optimal secara cerdas (`FilterMode.AUTO`).

---

## 🟠 HIGH PRIORITY — Quality Impact Besar

### 4. Document Boundary Refinement
- [ ] **Subpixel Corner Refinement** — Setelah approxPolyDP, pakai cv::cornerSubPix dengan Sobel gradient untuk presisi sudut sampai sub-pixel.
- [ ] **Multi-scale Contour Detection** — Build image pyramid (resize 0.5x, 1.0x, 2.0x), deteksi contour di tiap scale, merge hasilnya. Dokumen kecil/jauh jadi kedetek.
- [ ] **Contour Validation** — Filter contour berdasarkan:
  - Aspect ratio (0.5 — 2.0, mirip kertas)
  - Solidity (> 0.8, jangan terlalu berlubang)
  - Edge density di sekitar contour (pastikan beneran ada tepi)
- [ ] **Temporal Smoothing (Auto-Capture)** — Average corner position antar frame (Kalman filter atau EMA) biar overlay gak berkedip dan auto-capture lebih stabil.

### 5. White Balance & Color Accuracy
- [ ] **Grayworld White Balance** — cv::xphoto::GrayworldWB atau implementasi manual: rata-rata channel R/G/B dianggap abu-abu netral.
- [ ] **White Patch Reference** — Deteksi area paling terang di dokumen sebagai "putih referensi", terus stretch histogram ke 255.
- [ ] **Color Constancy** — Pastikan kertas selalu terlihat putih bersih walau cahaya kuning (warm) atau biru (cool).

### 6. Resolution & Scaling Strategy
- [ ] **Adaptive Output Resolution** — Jangan hardcode 2480x3508 di ViewModel. Hitung dari:
  - Ukuran dokumen fisik estimasi (A4, Letter, KTP)
  - DPI target (300 DPI untuk print quality, 150 DPI untuk share)
- [ ] **Super-resolution opsional** — Kalau input < 150 DPI, pakai interpolation Lanczos4 atau DNN upscaling sebelum warp.
- [ ] **Preserve aspect ratio dokumen asli** — Jangan force A4 kalau dokumennya Letter atau KTP. Deteksi rasio, lalu scale accordingly.

---

## 🟡 MEDIUM PRIORITY — Nice to Have

### 7. Advanced Binarization
- [ ] **Sauvola / Niblack Thresholding** — Lebih baik dari adaptiveThreshold Gaussian untuk teks kecil dan variasi pencahayaan lokal.
- [ ] **Background Normalization** — Hilangkan gradasi background (meja, tangan) sebelum binarisasi.
- [ ] **Multi-scale Binarization** — Kombinasi threshold pada scale berbeda untuk menangkap teks tipis dan tebal sekaligus.

### 8. Post-Processing Enhancement
- [ ] **Unsharp Mask dengan parameter adaptif** — Radius dan amount disesuaikan berdasarkan edge density gambar.
- [ ] **Deconvolution untuk motion blur** — Kalau blur karena goyang (motion blur), coba Wiener deconvolution (tapi berat komputasi).
- [ ] **Denoising setelah warp** — cv::fastNlMeansDenoising pada hasil warp untuk hasil bersih tanpa noise bintik.
- [ ] **Edge Preserving Smoothing** — cv::edgePreservingFilter untuk ratakan background tanpa menghilangkan tepi teks.

### 9. Smart Crop & Margin
- [ ] **Auto-margin removal** — Setelah warp, deteksi konten sebenarnya (teks area), crop margin putih berlebih.
- [ ] **Page Uniformity** — Pastikan semua halaman PDF punya margin konsisten, walau dokumen asli beda-beda posisi.
- [ ] **Bleed area handling** — Kalau dokumen melebihi frame, warning user sebelum warp.

---

## 🟢 LOW PRIORITY — Polish & Edge Cases

### 10. Multi-Document Detection
- [ ] **Multi-page in one shot** — Kalau ada 2 dokumen dalam 1 frame (misal KTP + SIM), deteksi keduanya dan kasih pilihan user.
- [ ] **Document type classification** — Klasifikasi sederhana: A4, KTP, struk (berdasarkan aspect ratio + konten).

### 11. HDR & Challenging Lighting
- [ ] **Exposure bracketing** — Capture 3 exposure berbeda (under, normal, over), fuse jadi 1 HDR image sebelum processing.
- [ ] **Backlight compensation** — Kalau background terang tapi dokumen gelap (backlight), apply local tone mapping.

### 12. Quality Metrics & Feedback
- [ ] **Real-time quality score** — Tampilkan indikator di UI: "Kualitas: Bagus / Buruk / Buram / Terlalu Gelap".
- [ ] **SSIM / BRISQUE score** — Hitung objective quality metric setelah enhance, bandingkan dengan threshold.
- [ ] **User feedback loop** — Simpan statistik filter mana yang paling sering dipilih user untuk training auto-filter selanjutnya.

---

## 🔧 IMPLEMENTATION ROADMAP (Rekomendasi Urutan)

### Sprint 1 — Stabilitas Deteksi
1. Bilateral filter pre-processing
2. Subpixel corner refinement
3. Temporal smoothing (EMA) untuk auto-capture

### Sprint 2 — Kualitas Gambar
4. Shadow removal
5. Auto white balance (Grayworld)
6. Adaptive output resolution (jangan hardcode A4)

### Sprint 3 — Smart Automation
7. Blur detection (Laplacian variance)
8. Auto-filter recommendation engine
9. Glare detection & handling

### Sprint 4 — Polish
10. Sauvola binarization
11. Auto-margin removal
12. Real-time quality score di UI

---

## 📊 Quick Win (Impact / Effort Ratio Tinggi)

| Improvement | Effort | Impact | Priority |
|-------------|--------|--------|----------|
| Bilateral filter pre-processing | Low | High | 🔴 Do First |
| Subpixel corner refinement | Low | High | 🔴 Do First |
| Shadow removal | Medium | High | 🔴 Do First |
| Auto white balance | Medium | High | 🟠 High |
| Blur detection | Low | Medium | 🟠 High |
| Adaptive resolution | Medium | Medium | 🟡 Medium |
| Sauvola threshold | Medium | Medium | 🟡 Medium |
| Multi-scale detection | High | High | 🟡 Medium |
| HDR fusion | High | Low | 🟢 Low |

---

## 📝 Notes untuk Native Layer (smartcamera.cpp)

### Fungsi yang perlu ditambah:
```cpp
// Pre-processing
void preprocessForDetection(cv::Mat& input, cv::Mat& output);
void removeShadows(const cv::Mat& input, cv::Mat& output);
void applyWhiteBalance(cv::Mat& image);

// Quality analysis
double calculateBlurMetric(const cv::Mat& image);
double calculateGlareRatio(const cv::Mat& image);
double calculateBrightnessMetric(const cv::Mat& image);

// Enhancement
void autoEnhance(cv::Mat& image);
void sauvolaThreshold(const cv::Mat& input, cv::Mat& output);
cv::Mat deskewImage(const cv::Mat& image, double& angle);

// Utility
std::string recommendFilterMode(const cv::Mat& image);
```

---

*Checklist ini dibuat berdasarkan kode MVP YataGami yang sudah ada.*
*Fokus utama: kecerahan, kejelasan teks, angle detection, dan stabilitas auto-capture.*
