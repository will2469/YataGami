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
- [x] **Subpixel Corner Refinement** — Menggunakan `cv::cornerSubPix` (window $5\times 5$, `maxIter=10`, `eps=0.01`) pada kandidat sudut berkeyakinan tinggi (`confidence > 0.60`) untuk akurasi warp homografi sub-pixel.
- [x] **Hierarchical Multi-Scale Detection Strategy** — Viewfinder 30 FPS memakai resolusi terstandarisasi 640p untuk efisiensi termal Helio G100; multi-scale pyramid dicadangkan untuk verifikasi post-capture pada frame 12MP jika confidence rendah.
- [x] **Multi-Factor Contour Validation** — `calculateQuadConfidence` memvalidasi aspek geometris lengkap (Area Ratio, Paralelisme, Konveksitas, Ortogonalitas sudut $90^\circ \pm 20^\circ$, dan Rasio Kertas A4/KTP 0.2–5.0).
- [x] **Temporal Smoothing & Auto-Capture** — *Adaptive Velocity-Aware EMA* ($\alpha = 0.25\text{--}0.55$) untuk overlay stabil tanpa lag, didukung validasi kestabilan 5 frame berturut-turut ($< 8\text{px}$ drift) sebelum trigger auto-capture.

### 5. White Balance & Color Accuracy
- [x] **Selective LAB Chroma Neutralization** — Menganalisis channel A & B pada area kertas terang ($L > 195$) di `enhancement_tiers.cpp` untuk menetralkan color cast (warm tungsten / cool fluorescent) tanpa mengorbankan warna stempel/tinta.
- [x] **White Patch Reference & Percentile LUT** — Dynamic range stretch persentil 1% & 99% via 256-byte L1 Cache LUT + specular glare clamping ($L > 245$) untuk menjaga kontras kertas tetap bersih tanpa over-exposure.
- [x] **Color Constancy via Illumination Division** — Estimasi gradasi pencahayaan latar belakang dan normalisasi divisi kanal untuk menjamin kertas putih merata di seluruh permukaan dokumen.

### 6. Resolution & Scaling Strategy
- [x] **Adaptive Document Aspect Ratio & Sizing** — Klasifikasi otomatis di `doc_classifier.cpp` mengenali KTP (1.586), Struk/Receipt (panjang $\ge 2.0$), Folio F4 (1.535), Square (1.0), dan A4 (1.414) tanpa memaksa dokumen ke rasio tunggal.
- [x] **DPI-Aware Output Target** — Presisi output 150 DPI (sharing/efisien) dan 300 DPI (cetak/arsip) dihitung langsung dari dimensi fisik dokumen binned 12MP.
- [x] **Anti-Aliasing Interpolation** — Memanfaatkan `cv::INTER_LINEAR` saat warp dan `INTER_AREA` saat downscaling dari sensor 12MP ($4000\times 3000$) tanpa beban DNN upscaling berlebih.

---

## 🟡 MEDIUM PRIORITY — Nice to Have

### 7. Advanced Binarization
- [x] **Illumination-Normalized Adaptive Thresholding** — Menggabungkan perataan iluminasi latar belakang (`flattenIllumination`) sebelum `adaptiveThreshold(GAUSSIAN_C)` untuk teks hitam bersih bebas bercak bayangan.

### 8. Post-Processing Enhancement
- [x] **O(1) Fast Edge-Preserving Filter** — Box-difference filter di `enhancement_tiers.cpp` untuk meratakan tekstur kertas tanpa melunakkan ketajaman tepi teks dokumen.
- [x] **Adaptive Unsharp Masking** — Gaussian blend unsharp mask untuk mempertajam kontras micro-stroke pada teks dokumen cetak.

### 9. Smart Crop & Margin
- [x] **Edge-Preserving Boundary Padding** — Penambahan padding 20px (`BORDER_CONSTANT` putih) sebelum kalkulasi matriks homografi di `deskew.cpp` untuk mengeliminasi artifak blur hitam di tepi warp.
- [x] **Content-Aware Margin Trimming** — Pembersihan batas tepi berlebih setelah rotasi deskew untuk memastikan konten dokumen rapi.

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
