#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <opencv2/opencv.hpp>

#define LOG_TAG "YataGamiNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jfloatArray JNICALL
Java_com_yatagami_opencv_DocumentDetector_nativeDetectDocument(
        JNIEnv *env, jobject, jobject bitmap) {

    AndroidBitmapInfo info;
    void *pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return nullptr;

    cv::Mat img;
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
        cv::cvtColor(tmp, img, cv::COLOR_RGBA2BGR);
    } else {
        AndroidBitmap_unlockPixels(env, bitmap);
        return nullptr;
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    cv::Mat gray, blurred, edges;
    cv::cvtColor(img, gray, cv::COLOR_BGR2GRAY);
    cv::GaussianBlur(gray, blurred, cv::Size(5, 5), 0);
    cv::Canny(blurred, edges, 75, 200);

    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(edges, contours, cv::RETR_LIST, cv::CHAIN_APPROX_SIMPLE);

    std::sort(contours.begin(), contours.end(),
        [](const auto &a, const auto &b) {
            return cv::contourArea(a) > cv::contourArea(b);
        });

    float imgArea = static_cast<float>(img.rows * img.cols);
    std::vector<cv::Point2f> docCorners;

    for (const auto &contour : contours) {
        std::vector<cv::Point> approx;
        cv::approxPolyDP(contour, approx, 0.02 * cv::arcLength(contour, true), true);
        if (approx.size() == 4) {
            float area = static_cast<float>(cv::contourArea(approx));
            if (area > imgArea * 0.15f) {
                for (auto &p : approx) docCorners.emplace_back(p.x, p.y);
                break;
            }
        }
    }

    if (docCorners.size() != 4) {
        docCorners = {
            {0, 0},
            {static_cast<float>(img.cols - 1), 0},
            {static_cast<float>(img.cols - 1), static_cast<float>(img.rows - 1)},
            {0, static_cast<float>(img.rows - 1)}
        };
    } else {
        cv::RotatedRect box = cv::minAreaRect(docCorners);
        cv::Point2f boxPts[4];
        box.points(boxPts);

        std::vector<std::pair<float, int>> sums, diffs;
        for (int i = 0; i < 4; ++i) {
            sums.push_back({boxPts[i].x + boxPts[i].y, i});
            diffs.push_back({boxPts[i].x - boxPts[i].y, i});
        }
        std::sort(sums.begin(), sums.end());
        std::sort(diffs.begin(), diffs.end());

        std::vector<cv::Point2f> ordered(4);
        ordered[0] = boxPts[diffs[0].second]; // tl
        ordered[1] = boxPts[sums[0].second];   // tr
        ordered[2] = boxPts[diffs[3].second]; // br
        ordered[3] = boxPts[sums[3].second]; // bl
        docCorners = ordered;
    }

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

    AndroidBitmapInfo info;
    void *pixels = nullptr;
    AndroidBitmap_getInfo(env, srcBitmap, &info);
    AndroidBitmap_lockPixels(env, srcBitmap, &pixels);

    cv::Mat src;
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
        cv::cvtColor(tmp, src, cv::COLOR_RGBA2BGR);
    } else {
        AndroidBitmap_unlockPixels(env, srcBitmap);
        return nullptr;
    }
    AndroidBitmap_unlockPixels(env, srcBitmap);

    jfloat *pts = env->GetFloatArrayElements(corners, nullptr);
    std::vector<cv::Point2f> srcPts = {
        {pts[0], pts[1]}, {pts[2], pts[3]},
        {pts[4], pts[5]}, {pts[6], pts[7]}
    };
    env->ReleaseFloatArrayElements(corners, pts, 0);

    std::vector<cv::Point2f> dstPts = {
        {0, 0},
        {static_cast<float>(dstWidth - 1), 0},
        {static_cast<float>(dstWidth - 1), static_cast<float>(dstHeight - 1)},
        {0, static_cast<float>(dstHeight - 1)}
    };

    cv::Mat M = cv::getPerspectiveTransform(srcPts, dstPts);
    cv::Mat warped;
    cv::warpPerspective(src, warped, M, cv::Size(dstWidth, dstHeight));

    jclass bitmapCls = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmap = env->GetStaticMethodID(bitmapCls, "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jfieldID argb8888 = env->GetStaticFieldID(bitmapCls, "Config_ARGB_8888",
        "Landroid/graphics/Bitmap$Config;");
    jobject config = env->GetStaticObjectField(bitmapCls, argb8888);
    jobject outBitmap = env->CallStaticObjectMethod(bitmapCls, createBitmap, dstWidth, dstHeight, config);

    AndroidBitmap_lockPixels(env, outBitmap, &pixels);
    cv::Mat outMat(dstHeight, dstWidth, CV_8UC4, pixels);
    cv::cvtColor(warped, outMat, cv::COLOR_BGR2RGBA);
    AndroidBitmap_unlockPixels(env, outBitmap);

    return outBitmap;
}

JNIEXPORT jobject JNICALL
Java_com_yatagami_opencv_ImageProcessor_nativeEnhanceImage(
        JNIEnv *env, jobject, jobject bitmap, jint mode) {

    AndroidBitmapInfo info;
    void *pixels = nullptr;
    AndroidBitmap_getInfo(env, bitmap, &info);
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    cv::Mat src, bgr;
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
        cv::cvtColor(tmp, bgr, cv::COLOR_RGBA2BGR);
    } else {
        AndroidBitmap_unlockPixels(env, bitmap);
        return nullptr;
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    cv::Mat processed;
    switch (mode) {
        case 1: { // Grayscale
            cv::cvtColor(bgr, processed, cv::COLOR_BGR2GRAY);
            cv::cvtColor(processed, processed, cv::COLOR_GRAY2BGR);
            break;
        }
        case 2: { // Black & White
            cv::Mat gray;
            cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
            cv::adaptiveThreshold(gray, processed, 255, cv::ADAPTIVE_THRESH_GAUSSIAN_C,
                                cv::THRESH_BINARY, 11, 2);
            cv::cvtColor(processed, processed, cv::COLOR_GRAY2BGR);
            break;
        }
        case 3: { // Magic Color
            cv::Mat lab;
            cv::cvtColor(bgr, lab, cv::COLOR_BGR2Lab);
            std::vector<cv::Mat> channels(3);
            cv::split(lab, channels);
            cv::Ptr<cv::CLAHE> clahe = cv::createCLAHE(2.0, cv::Size(8, 8));
            clahe->apply(channels[0], channels[0]);
            cv::merge(channels, lab);
            cv::cvtColor(lab, processed, cv::COLOR_Lab2BGR);
            break;
        }
        case 4: { // Sharpen
            cv::Mat blurred;
            cv::GaussianBlur(bgr, blurred, cv::Size(0, 0), 3);
            cv::addWeighted(bgr, 1.5, blurred, -0.5, 0, processed);
            break;
        }
        default:
            processed = bgr.clone();
    }

    jclass bitmapCls = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmap = env->GetStaticMethodID(bitmapCls, "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jfieldID argb8888 = env->GetStaticFieldID(bitmapCls, "Config_ARGB_8888",
        "Landroid/graphics/Bitmap$Config;");
    jobject config = env->GetStaticObjectField(bitmapCls, argb8888);
    jobject outBitmap = env->CallStaticObjectMethod(bitmapCls, createBitmap, info.width, info.height, config);

    AndroidBitmap_lockPixels(env, outBitmap, &pixels);
    cv::Mat outMat(info.height, info.width, CV_8UC4, pixels);
    cv::cvtColor(processed, outMat, cv::COLOR_BGR2RGBA);
    AndroidBitmap_unlockPixels(env, outBitmap);

    return outBitmap;
}

}
