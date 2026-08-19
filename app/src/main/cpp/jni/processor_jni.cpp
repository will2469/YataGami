#include "processor_jni.h"
#include "core/common.h"
#include "core/scheduler.h"
#include "core/buffer_pool.h"
#include "core/deskew.h"
#include "core/doc_classifier.h"
#include "core/text_deskew.h"
#include "core/enhancement.h"
#include "core/scene_analysis.h"
#include "core/geometry.h"

extern "C" {

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

JNIEXPORT jintArray JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeInferDocumentType(
        JNIEnv *env, jobject, jfloatArray corners) {

    jintArray result = env->NewIntArray(4);
    if (!corners || env->GetArrayLength(corners) < 8) {
        jint defaultRes[4] = {0, 1, 1240, 1754};
        env->SetIntArrayRegion(result, 0, 4, defaultRes);
        return result;
    }

    jfloat *pts = env->GetFloatArrayElements(corners, nullptr);
    std::vector<cv::Point2f> srcPts = {
        {pts[0], pts[1]}, {pts[2], pts[3]},
        {pts[4], pts[5]}, {pts[6], pts[7]}
    };
    env->ReleaseFloatArrayElements(corners, pts, 0);

    yatagami::DocumentTypeResult docRes = yatagami::inferDocumentTypeAndSize(srcPts);
    jint vals[4] = {
        static_cast<jint>(docRes.type),
        docRes.isPortrait ? 1 : 0,
        docRes.targetWidth,
        docRes.targetHeight
    };
    env->SetIntArrayRegion(result, 0, 4, vals);
    return result;
}

} // extern "C"

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
    {"nativeProcessPageFull", "(Landroid/graphics/Bitmap;Landroid/graphics/Bitmap;[FIII)Z", (void*)Java_com_yatagami_opencv_ImageProcessor_nativeProcessPageFull},
    {"nativeInferDocumentType", "([F)[I", (void*)Java_com_yatagami_opencv_ImageProcessor_nativeInferDocumentType}
};

namespace yatagami {

bool registerProcessorNatives(JNIEnv* env) {
    jclass imgProcCls = env->FindClass("com/yatagami/opencv/ImageProcessor");
    if (!imgProcCls) return false;
    return env->RegisterNatives(imgProcCls, gImageProcessorMethods,
        sizeof(gImageProcessorMethods) / sizeof(gImageProcessorMethods[0])) == JNI_OK;
}

} // namespace yatagami
