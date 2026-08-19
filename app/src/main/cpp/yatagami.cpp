#include "core/common.h"
#include "core/buffer_pool.h"
#include "core/scheduler.h"
#include "core/preprocessing.h"
#include "core/geometry.h"
#include "core/deskew.h"
#include "core/enhancement.h"

extern "C" {

JNIEXPORT jfloatArray JNICALL
Java_com_yatagami_opencv_DocumentDetector_nativeDetectDocument(
        JNIEnv *env, jobject, jobject bitmap);

JNIEXPORT jboolean JNICALL
Java_com_yatagami_opencv_DocumentDetector_nativeDetectDocumentDirect(
        JNIEnv *env, jobject, jobject bitmap, jobject directBuffer);

JNIEXPORT jfloat JNICALL
Java_com_yatagami_opencv_DocumentDetector_nativeCalculateConfidence(
        JNIEnv *env, jobject, jfloatArray corners, jfloat imgWidth, jfloat imgHeight);

JNIEXPORT jobject JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeWarpPerspective(
        JNIEnv *env, jobject, jobject srcBitmap, jfloatArray corners, jint dstWidth, jint dstHeight);

JNIEXPORT jfloat JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeDetectSkewAngle(
        JNIEnv *env, jobject, jobject bitmap);

JNIEXPORT jobject JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeDeskew(
        JNIEnv *env, jobject, jobject bitmap);

JNIEXPORT jfloat JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeCalculateBlurScore(
        JNIEnv *env, jobject, jobject bitmap);

JNIEXPORT jfloat JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeCalculateGlareRatio(
        JNIEnv *env, jobject, jobject bitmap);

JNIEXPORT jint JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeRecommendFilter(
        JNIEnv *env, jobject, jobject bitmap);

JNIEXPORT jobject JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeEnhanceImage(
        JNIEnv *env, jobject, jobject bitmap, jint mode);

JNIEXPORT jboolean JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeEnhanceImageDirect(
        JNIEnv *env, jobject, jobject srcBitmap, jobject dstBitmap, jint mode);

JNIEXPORT void JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeClearBufferPool(
        JNIEnv *, jobject);

JNIEXPORT jboolean JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeProcessPageFull(
        JNIEnv *env, jobject, jobject srcBitmap, jobject dstBitmap, jfloatArray outCorners,
        jint filterMode, jint dstWidth, jint dstHeight);

}

static const JNINativeMethod gDocumentDetectorMethods[] = {
    {"nativeDetectDocument", "(Landroid/graphics/Bitmap;)[F", (void*)Java_com_yatagami_opencv_DocumentDetector_nativeDetectDocument},
    {"nativeDetectDocumentDirect", "(Landroid/graphics/Bitmap;Ljava/nio/ByteBuffer;)Z", (void*)Java_com_yatagami_opencv_DocumentDetector_nativeDetectDocumentDirect},
    {"nativeCalculateConfidence", "([FFF)F", (void*)Java_com_yatagami_opencv_DocumentDetector_nativeCalculateConfidence}
};

static const JNINativeMethod gImageProcessorMethods[] = {
    {"nativeWarpPerspective", "(Landroid/graphics/Bitmap;[FII)Landroid/graphics/Bitmap;", (void*)Java_com_yatagami_opencv_ImageProcessor_nativeWarpPerspective},
    {"nativeDetectSkewAngle", "(Landroid/graphics/Bitmap;)F", (void*)Java_com_yatagami_opencv_ImageProcessor_nativeDetectSkewAngle},
    {"nativeDeskew", "(Landroid/graphics/Bitmap;)Landroid/graphics/Bitmap;", (void*)Java_com_yatagami_opencv_ImageProcessor_nativeDeskew},
    {"nativeCalculateBlurScore", "(Landroid/graphics/Bitmap;)F", (void*)Java_com_yatagami_opencv_ImageProcessor_nativeCalculateBlurScore},
    {"nativeCalculateGlareRatio", "(Landroid/graphics/Bitmap;)F", (void*)Java_com_yatagami_opencv_ImageProcessor_nativeCalculateGlareRatio},
    {"nativeRecommendFilter", "(Landroid/graphics/Bitmap;)I", (void*)Java_com_yatagami_opencv_ImageProcessor_nativeRecommendFilter},
    {"nativeEnhanceImage", "(Landroid/graphics/Bitmap;I)Landroid/graphics/Bitmap;", (void*)Java_com_yatagami_opencv_ImageProcessor_nativeEnhanceImage},
    {"nativeEnhanceImageDirect", "(Landroid/graphics/Bitmap;Landroid/graphics/Bitmap;I)Z", (void*)Java_com_yatagami_opencv_ImageProcessor_nativeEnhanceImageDirect},
    {"nativeClearBufferPool", "()V", (void*)Java_com_yatagami_opencv_ImageProcessor_nativeClearBufferPool},
    {"nativeProcessPageFull", "(Landroid/graphics/Bitmap;Landroid/graphics/Bitmap;[FIII)Z", (void*)Java_com_yatagami_opencv_ImageProcessor_nativeProcessPageFull}
};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    yatagami::initThreadEnvironment();
    yatagami::BufferPool::getInstance().preallocate();

    jclass docDetectorCls = env->FindClass("com/yatagami/opencv/DocumentDetector");
    if (docDetectorCls) {
        env->RegisterNatives(docDetectorCls, gDocumentDetectorMethods,
            sizeof(gDocumentDetectorMethods) / sizeof(gDocumentDetectorMethods[0]));
    }

    jclass imgProcCls = env->FindClass("com/yatagami/opencv/ImageProcessor");
    if (imgProcCls) {
        env->RegisterNatives(imgProcCls, gImageProcessorMethods,
            sizeof(gImageProcessorMethods) / sizeof(gImageProcessorMethods[0]));
    }

    return JNI_VERSION_1_6;
}

extern "C" {

JNIEXPORT jfloatArray JNICALL
Java_com_yatagami_opencv_DocumentDetector_nativeDetectDocument(
        JNIEnv *env, jobject, jobject bitmap) {

    yatagami::pinThreadToBigCores();

    cv::Mat img;
    if (!yatagami::bitmapToMat(env, bitmap, img)) {
        return nullptr;
    }

    std::vector<cv::Point2f> docCorners = yatagami::detectDocumentCorners(img);

    jfloatArray result = env->NewFloatArray(8);
    float flat[8];
    for (int i = 0; i < 4; ++i) {
        flat[i * 2] = docCorners[i].x;
        flat[i * 2 + 1] = docCorners[i].y;
    }
    env->SetFloatArrayRegion(result, 0, 8, flat);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_yatagami_opencv_DocumentDetector_nativeDetectDocumentDirect(
        JNIEnv *env, jobject, jobject bitmap, jobject directBuffer) {

    yatagami::pinThreadToBigCores();

    if (!directBuffer) return JNI_FALSE;
    void *bufPtr = env->GetDirectBufferAddress(directBuffer);
    if (!bufPtr) return JNI_FALSE;

    cv::Mat img;
    if (!yatagami::bitmapToMat(env, bitmap, img)) {
        return JNI_FALSE;
    }

    std::vector<cv::Point2f> docCorners = yatagami::detectDocumentCorners(img);
    float *floatOut = static_cast<float *>(bufPtr);
    for (int i = 0; i < 4; ++i) {
        floatOut[i * 2] = docCorners[i].x;
        floatOut[i * 2 + 1] = docCorners[i].y;
    }

    return JNI_TRUE;
}

JNIEXPORT jfloat JNICALL
Java_com_yatagami_opencv_DocumentDetector_nativeCalculateConfidence(
        JNIEnv *env, jobject, jfloatArray corners, jfloat imgWidth, jfloat imgHeight) {

    if (!corners || env->GetArrayLength(corners) < 8) return 0.0f;
    jfloat *pts = env->GetFloatArrayElements(corners, nullptr);
    std::vector<cv::Point2f> docCorners = {
        {pts[0], pts[1]}, {pts[2], pts[3]},
        {pts[4], pts[5]}, {pts[6], pts[7]}
    };
    env->ReleaseFloatArrayElements(corners, pts, 0);

    return yatagami::calculateQuadConfidence(docCorners, imgWidth, imgHeight);
}

JNIEXPORT jobject JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeWarpPerspective(
        JNIEnv *env, jobject, jobject srcBitmap, jfloatArray corners, jint dstWidth, jint dstHeight) {

    yatagami::pinThreadToBigCores();

    cv::Mat src;
    if (!yatagami::bitmapToMat(env, srcBitmap, src)) {
        return nullptr;
    }

    jfloat *pts = env->GetFloatArrayElements(corners, nullptr);
    std::vector<cv::Point2f> srcPts = {
        {pts[0], pts[1]}, {pts[2], pts[3]},
        {pts[4], pts[5]}, {pts[6], pts[7]}
    };
    env->ReleaseFloatArrayElements(corners, pts, 0);

    cv::Mat deskewed = yatagami::warpAndDeskewPerspective(src, srcPts, dstWidth, dstHeight);
    return yatagami::matToBitmap(env, deskewed);
}

JNIEXPORT jfloat JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeDetectSkewAngle(
        JNIEnv *env, jobject, jobject bitmap) {

    yatagami::pinThreadToBigCores();

    cv::Mat bgr;
    if (!yatagami::bitmapToMat(env, bitmap, bgr)) {
        return 0.0f;
    }

    double angle = yatagami::detectTextSkewAngle(bgr);
    return static_cast<jfloat>(angle);
}

JNIEXPORT jobject JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeDeskew(
        JNIEnv *env, jobject, jobject bitmap) {

    yatagami::pinThreadToBigCores();

    cv::Mat bgr;
    if (!yatagami::bitmapToMat(env, bitmap, bgr)) {
        return nullptr;
    }

    double angle = 0.0;
    cv::Mat deskewed = yatagami::deskewImage(bgr, angle);
    return yatagami::matToBitmap(env, deskewed);
}

JNIEXPORT jfloat JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeCalculateBlurScore(
        JNIEnv *env, jobject, jobject bitmap) {

    yatagami::pinThreadToBigCores();

    cv::Mat bgr;
    if (!yatagami::bitmapToMat(env, bitmap, bgr)) {
        return 0.0f;
    }

    return static_cast<jfloat>(yatagami::calculateBlurScore(bgr));
}

JNIEXPORT jfloat JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeCalculateGlareRatio(
        JNIEnv *env, jobject, jobject bitmap) {

    yatagami::pinThreadToBigCores();

    cv::Mat bgr;
    if (!yatagami::bitmapToMat(env, bitmap, bgr)) {
        return 0.0f;
    }

    return static_cast<jfloat>(yatagami::calculateGlareRatio(bgr));
}

JNIEXPORT jint JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeRecommendFilter(
        JNIEnv *env, jobject, jobject bitmap) {

    yatagami::pinThreadToBigCores();

    cv::Mat bgr;
    if (!yatagami::bitmapToMat(env, bitmap, bgr)) {
        return 0;
    }

    return yatagami::recommendFilterMode(bgr);
}

JNIEXPORT jobject JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeEnhanceImage(
        JNIEnv *env, jobject, jobject bitmap, jint mode) {

    yatagami::pinThreadToBigCores();

    cv::Mat bgr;
    if (!yatagami::bitmapToMat(env, bitmap, bgr)) {
        return nullptr;
    }

    cv::Mat processed = yatagami::enhanceImage(bgr, mode);
    return yatagami::matToBitmap(env, processed);
}

JNIEXPORT jboolean JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeEnhanceImageDirect(
        JNIEnv *env, jobject, jobject srcBitmap, jobject dstBitmap, jint mode) {

    yatagami::pinThreadToBigCores();

    cv::Mat bgr;
    if (!yatagami::bitmapToMat(env, srcBitmap, bgr)) {
        return JNI_FALSE;
    }

    cv::Mat processed = yatagami::enhanceImage(bgr, mode);
    bool ok = yatagami::matToBitmapDirect(env, processed, dstBitmap);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeClearBufferPool(
        JNIEnv *, jobject) {
    yatagami::BufferPool::getInstance().clear();
}

JNIEXPORT jboolean JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeProcessPageFull(
        JNIEnv *env, jobject, jobject srcBitmap, jobject dstBitmap, jfloatArray outCorners,
        jint filterMode, jint dstWidth, jint dstHeight) {

    yatagami::pinThreadToBigCores();

    cv::Mat src;
    if (!yatagami::bitmapToMat(env, srcBitmap, src)) {
        return JNI_FALSE;
    }

    // 1. Detect document corners with subpixel precision
    std::vector<cv::Point2f> docCorners = yatagami::detectDocumentCorners(src);

    if (outCorners != nullptr && env->GetArrayLength(outCorners) >= 8) {
        float flat[8];
        for (int i = 0; i < 4; ++i) {
            flat[i * 2] = docCorners[i].x;
            flat[i * 2 + 1] = docCorners[i].y;
        }
        env->SetFloatArrayRegion(outCorners, 0, 8, flat);
    }

    // 2. Warp perspective, correct orientation, and deskew text
    cv::Mat warped = yatagami::warpAndDeskewPerspective(src, docCorners, dstWidth, dstHeight);

    // 3. Apply enhancement filter (with auto glare suppression if AUTO mode)
    cv::Mat finalEnhanced = yatagami::enhanceImage(warped, filterMode);

    // 4. Zero-copy write to destination bitmap
    bool ok = yatagami::matToBitmapDirect(env, finalEnhanced, dstBitmap);
    return ok ? JNI_TRUE : JNI_FALSE;
}

}
