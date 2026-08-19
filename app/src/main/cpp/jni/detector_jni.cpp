#include "detector_jni.h"
#include "core/common.h"
#include "core/scheduler.h"
#include "core/preprocessing.h"
#include "core/geometry.h"

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

} // extern "C"

static const JNINativeMethod gDocumentDetectorMethods[] = {
    {"nativeDetectDocument", "(Landroid/graphics/Bitmap;)[F", (void*)Java_com_yatagami_opencv_DocumentDetector_nativeDetectDocument},
    {"nativeDetectDocumentDirect", "(Landroid/graphics/Bitmap;Ljava/nio/ByteBuffer;)Z", (void*)Java_com_yatagami_opencv_DocumentDetector_nativeDetectDocumentDirect},
    {"nativeCalculateConfidence", "([FFF)F", (void*)Java_com_yatagami_opencv_DocumentDetector_nativeCalculateConfidence}
};

namespace yatagami {

bool registerDetectorNatives(JNIEnv* env) {
    jclass docDetectorCls = env->FindClass("com/yatagami/opencv/DocumentDetector");
    if (!docDetectorCls) return false;
    return env->RegisterNatives(docDetectorCls, gDocumentDetectorMethods,
        sizeof(gDocumentDetectorMethods) / sizeof(gDocumentDetectorMethods[0])) == JNI_OK;
}

} // namespace yatagami
