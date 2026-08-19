# 📜 YataGami (八咫鏡 / Yata no Kagami)

> **Pemindai Dokumen Cerdas & Generator PDF Berbasis OpenCV Native C++ & Jetpack Compose**

---

## ⛩️ Filosofi & Asal Usul Nama

Nama **YataGami** terinspirasi dari **Yata no Kagami (八咫鏡 / やたのかがみ)**, salah satu dari **Tiga Harta Karun Suci Kekaisaran Jepang (*Sanshu no Jingi / 三種の神器*)**.

Dalam mitologi dan tradisi Shinto, *Yata no Kagami* adalah cermin suci yang melambangkan **kejujuran, kebijaksanaan, dan kemampuan untuk mencerminkan kebenaran secara murni tanpa distorsi**. 

Aplikasi ini mengadopsi filosofi tersebut:
- **Mencerminkan Presisi Dokumen Fisik:** Mengubah lembaran kertas fisik ke format digital dengan perspektif sempurna tanpa distorsi sudut maupun kemiringan.
- **Kejernihan Visual yang Murni:** Menghadirkan algoritma peningkatan citra (*enhancement filters*) yang membersihkan bayangan, memperjelas teks, dan menampilkan warna asli dokumen dengan tajam dan jernih.

---

## ✨ Fitur Utama

### 1. 📷 Pemindaian & Deteksi Tepi Real-Time
- **CameraX + OpenCV Integration:** Integrasi kamera berkinerja tinggi menggunakan CameraX ImageAnalysis.
- **Automatic Quad Contour Detection:** Deteksi kontur 4 sudut lembar dokumen secara otomatis menggunakan algoritma Canny Edge Detection dan Douglas-Peucker Polygon Approximation (`cv::approxPolyDP`).
- **Interactive Document Overlay:** Overlay visual interaktif pada viewfinder kamera yang menyoroti batas dokumen secara real-time.

### 2. 📐 Transformasi Perspektif Presisi (Dewarping)
- **Quad Perspective Transform:** Mengoreksi sudut kemiringan dokumen dari berbagai sudut pengambilan foto menggunakan matriks transformasi geometris OpenCV (`cv::getPerspectiveTransform` & `cv::warpPerspective`).
- **Crop Screen Interaktif:** Memungkinkan penyesuaian manual 4 titik sudut dokumen sebelum diproses lebih lanjut.

### 3. 🎨 Filter Peningkatan Citra Lanjutan (Image Enhancement)
- **Original:** Mempertahankan warna alami dari sensor kamera.
- **Grayscale:** Konversi presisi ke tingkat keabuan untuk dokumen formal.
- **Black & White:** Algoritma *Adaptive Gaussian Thresholding* untuk menghasilkan dokumen teks hitam-putih yang bersih bebas bayangan latar belakang.
- **Magic Color (CLAHE):** Peningkatan kontras adaptif (*Contrast Limited Adaptive Histogram Equalization*) pada ruang warna Lab untuk memperkaya ketajaman teks berwarna dan gambar.
- **Sharpen:** Penajaman detail teks menggunakan teknik *Unsharp Masking*.

### 4. 📑 Manajemen Multi-Halaman & Export Fleksibel
- **Multi-page Batch Scanning:** Pindai banyak halaman dalam satu sesi dokumen.
- **PDF Generation:** Kompilasi seluruh halaman menjadi satu berkas PDF berkualitas tinggi dengan rasio kompresi optimal menggunakan *Apache PDFBox*.
- **Export to Gallery:** Simpan halaman pindaian langsung ke Galeri perangkat (`Pictures/YataGami`) dengan dukungan Android Scoped Storage API (`MediaStore`).
- **Share Intent:** Bagikan langsung berkas PDF melalui aplikasi pesan, email, atau cloud storage menggunakan Android `FileProvider`.

---

## 🛠️ Arsitektur & Teknologi

YataGami dibangun dengan standar modern Android Development, memanfaatkan performa komputasi native C++ untuk pemrosesan citra berat.

```
┌────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI                   │
│   (CameraScreen, CropScreen, FilterScreen, PageList)   │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│              ScanViewModel & Repository                │
│    (StateFlow, Coroutines, MediaStore, PDFBox Gen)     │
└─────────────┬────────────────────────────┬─────────────┘
              │                            │
┌─────────────▼─────────────┐ ┌────────────▼─────────────┐
│    CameraX ImageStream    │ │       PDFBox Android     │
└─────────────┬─────────────┘ └──────────────────────────┘
              │
┌─────────────▼──────────────────────────────────────────┐
│             JNI Bridge (Kotlin ↔ Native C++)           │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│          Native C++17 Engine (yatagami.cpp)            │
│   - OpenCV 4.12.0 (Canny, WarpPerspective, CLAHE)      │
│   - Android NDK (jnigraphics, Bitmap direct access)    │
└────────────────────────────────────────────────────────┘
```

### Tech Stack:
- **Language:** Kotlin 2.0 & C++17
- **UI Framework:** Jetpack Compose (Material 3)
- **Camera:** AndroidX CameraX (Camera2 API)
- **Computer Vision Engine:** OpenCV 4.12.0 Native SDK
- **Build System:** Gradle (Kotlin DSL), Android Gradle Plugin 8.5+, CMake 3.22.1
- **NDK Version:** Android NDK `26.3.11579264`
- **PDF Engine:** Apache PDFBox for Android (`com.tom-roush:pdfbox-android`)
- **Concurrency:** Kotlin Coroutines & Asynchronous Flow

---

## 📂 Struktur Direktori Proyek

```
YataGami/
├── app/
│   ├── src/main/
│   │   ├── cpp/                          # Native C++ Source & OpenCV
│   │   │   ├── CMakeLists.txt            # Konfigurasi build CMake
│   │   │   ├── yatagami.cpp              # Implementasi JNI & Algoritma OpenCV
│   │   │   └── opencv/                   # OpenCV Android SDK Native & Header
│   │   ├── java/com/yatagami/            # Kode Sumber Kotlin
│   │   │   ├── data/                     # Model data & ScanRepository
│   │   │   ├── opencv/                   # JNI Bindings (DocumentDetector, ImageProcessor)
│   │   │   ├── ui/                       # UI Compose (Screens, Components, Theme)
│   │   │   ├── utils/                    # Utilitas Bitmap & Pemrosesan
│   │   │   └── MainActivity.kt           # Single-Activity Navigation Host
│   │   ├── res/                          # Android Resources (strings, themes, xml)
│   │   └── AndroidManifest.xml           # Konfigurasi App, Permissions & FileProvider
│   ├── build.gradle.kts                  # Konfigurasi modul aplikasi
│   └── proguard-rules.pro                # Aturan ProGuard / R8
├── buildSrc/                             # Dependency Management (Version Catalog / Deps)
├── settings.gradle.kts                   # Root project configuration
└── README.md
```

---

## 🚀 Panduan Build & Menjalankan Aplikasi

### Prasyarat:
1. **Android Studio** Ladybug / Jellyfish (atau yang lebih baru) atau Gradle CLI.
2. **Android SDK:**
   - Compile SDK: `35`
   - Min SDK: `24` (Android 7.0+)
3. **Android NDK:** Versi `26.3.11579264`
4. **CMake:** Versi `3.22.1`

### Langkah Instalasi Komponen NDK & CMake via SDK Manager:
```bash
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "cmake;3.22.1" "ndk;26.3.11579264"
```

### Langkah Kompilasi Proyek:
```bash
# Clone atau buka repositori
cd /path/to/YataGami

# Build APK Debug
./gradlew assembleDebug

# Install ke perangkat Android yang terhubung
./gradlew installDebug
```

---

## 📄 Lisensi
Hak Cipta © 2026 **YataGami Project**. Dilindungi undang-undang.
