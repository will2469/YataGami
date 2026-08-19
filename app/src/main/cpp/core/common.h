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

namespace yatagami {

bool bitmapToMat(JNIEnv *env, jobject bitmap, cv::Mat &outMat);
jobject matToBitmap(JNIEnv *env, const cv::Mat &mat);

} // namespace yatagami
