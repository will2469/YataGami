#include "common.h"

namespace yatagami {

bool bitmapToMat(JNIEnv *env, jobject bitmap, cv::Mat &outMat) {
    AndroidBitmapInfo info;
    void *pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return false;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return false;

    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
        cv::cvtColor(tmp, outMat, cv::COLOR_RGBA2BGR);
        AndroidBitmap_unlockPixels(env, bitmap);
        return true;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return false;
}

jobject matToBitmap(JNIEnv *env, const cv::Mat &mat) {
    jclass bitmapCls = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmap = env->GetStaticMethodID(bitmapCls, "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jfieldID argb8888 = env->GetStaticFieldID(bitmapCls, "Config_ARGB_8888",
        "Landroid/graphics/Bitmap$Config;");
    jobject config = env->GetStaticObjectField(bitmapCls, argb8888);
    jobject outBitmap = env->CallStaticObjectMethod(bitmapCls, createBitmap, mat.cols, mat.rows, config);

    void *pixels = nullptr;
    AndroidBitmap_lockPixels(env, outBitmap, &pixels);
    cv::Mat outMat(mat.rows, mat.cols, CV_8UC4, pixels);
    if (mat.channels() == 3) {
        cv::cvtColor(mat, outMat, cv::COLOR_BGR2RGBA);
    } else if (mat.channels() == 1) {
        cv::cvtColor(mat, outMat, cv::COLOR_GRAY2RGBA);
    } else if (mat.channels() == 4) {
        mat.copyTo(outMat);
    }
    AndroidBitmap_unlockPixels(env, outBitmap);

    return outBitmap;
}

} // namespace yatagami
