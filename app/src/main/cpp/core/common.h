#pragma once

#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <opencv2/opencv.hpp>
#include <algorithm>
#include <cmath>
#include <vector>

#define LOG_TAG "YataGamiNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if defined(__GNUC__) || defined(__clang__)
#define YATAGAMI_PREFETCH_READ(ptr) __builtin_prefetch((const void*)(ptr), 0, 3)
#define YATAGAMI_PREFETCH_WRITE(ptr) __builtin_prefetch((const void*)(ptr), 1, 3)
#else
#define YATAGAMI_PREFETCH_READ(ptr) ((void)0)
#define YATAGAMI_PREFETCH_WRITE(ptr) ((void)0)
#endif

namespace yatagami {

bool bitmapToMat(JNIEnv *env, jobject bitmap, cv::Mat &outMat);
bool bitmapToMatWrap(JNIEnv *env, jobject bitmap, cv::Mat &outMat, void **outPixels);
bool matToBitmapDirect(JNIEnv *env, const cv::Mat &mat, jobject dstBitmap);
jobject matToBitmap(JNIEnv *env, const cv::Mat &mat);

} // namespace yatagami
