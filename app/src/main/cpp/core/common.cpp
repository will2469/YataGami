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

bool bitmapToMatWrap(JNIEnv *env, jobject bitmap, cv::Mat &outMat, void **outPixels) {
    AndroidBitmapInfo info;
    void *pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return false;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return false;

    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        outMat = cv::Mat(info.height, info.width, CV_8UC4, pixels, info.stride);
        if (outPixels) *outPixels = pixels;
        return true;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return false;
}

bool matToBitmapDirect(JNIEnv *env, const cv::Mat &mat, jobject dstBitmap) {
    AndroidBitmapInfo info;
    void *pixels = nullptr;

    if (AndroidBitmap_getInfo(env, dstBitmap, &info) < 0) return false;
    if (AndroidBitmap_lockPixels(env, dstBitmap, &pixels) < 0) return false;

    if (info.width != mat.cols || info.height != mat.rows) {
        AndroidBitmap_unlockPixels(env, dstBitmap);
        return false;
    }

    cv::Mat outMat(info.height, info.width, CV_8UC4, pixels, info.stride);
    if (mat.channels() == 3) {
        cv::cvtColor(mat, outMat, cv::COLOR_BGR2RGBA);
    } else if (mat.channels() == 1) {
        cv::cvtColor(mat, outMat, cv::COLOR_GRAY2RGBA);
    } else if (mat.channels() == 4) {
        mat.copyTo(outMat);
    }

    AndroidBitmap_unlockPixels(env, dstBitmap);
    return true;
}

jobject matToBitmap(JNIEnv *env, const cv::Mat &mat) {
    jclass configCls = env->FindClass("android/graphics/Bitmap$Config");
    if (!configCls) return nullptr;
    jfieldID argb8888Field = env->GetStaticFieldID(configCls, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    if (!argb8888Field) return nullptr;
    jobject config = env->GetStaticObjectField(configCls, argb8888Field);

    jclass bitmapCls = env->FindClass("android/graphics/Bitmap");
    if (!bitmapCls) return nullptr;
    jmethodID createBitmap = env->GetStaticMethodID(bitmapCls, "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    if (!createBitmap) return nullptr;
    jobject outBitmap = env->CallStaticObjectMethod(bitmapCls, createBitmap, mat.cols, mat.rows, config);
    if (!outBitmap) return nullptr;

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, outBitmap, &pixels) < 0) {
        return nullptr;
    }
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
