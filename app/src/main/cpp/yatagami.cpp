#include "core/common.h"
#include "core/preprocessing.h"
#include "core/geometry.h"
#include "core/deskew.h"
#include "core/enhancement.h"

extern "C" {

JNIEXPORT jfloatArray JNICALL
Java_com_yatagami_opencv_DocumentDetector_nativeDetectDocument(
        JNIEnv *env, jobject, jobject bitmap) {

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

JNIEXPORT jobject JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeWarpPerspective(
        JNIEnv *env, jobject, jobject srcBitmap, jfloatArray corners, jint dstWidth, jint dstHeight) {

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

    cv::Mat bgr;
    if (!yatagami::bitmapToMat(env, bitmap, bgr)) {
        return 0.0f;
    }

    return static_cast<jfloat>(yatagami::calculateBlurScore(bgr));
}

JNIEXPORT jfloat JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeCalculateGlareRatio(
        JNIEnv *env, jobject, jobject bitmap) {

    cv::Mat bgr;
    if (!yatagami::bitmapToMat(env, bitmap, bgr)) {
        return 0.0f;
    }

    return static_cast<jfloat>(yatagami::calculateGlareRatio(bgr));
}

JNIEXPORT jint JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeRecommendFilter(
        JNIEnv *env, jobject, jobject bitmap) {

    cv::Mat bgr;
    if (!yatagami::bitmapToMat(env, bitmap, bgr)) {
        return 0;
    }

    return yatagami::recommendFilterMode(bgr);
}

JNIEXPORT jobject JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeEnhanceImage(
        JNIEnv *env, jobject, jobject bitmap, jint mode) {

    cv::Mat bgr;
    if (!yatagami::bitmapToMat(env, bitmap, bgr)) {
        return nullptr;
    }

    cv::Mat processed = yatagami::enhanceImage(bgr, mode);
    return yatagami::matToBitmap(env, processed);
}

}
