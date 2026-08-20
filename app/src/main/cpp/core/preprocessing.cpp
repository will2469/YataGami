#include "preprocessing.h"
#include "buffer_pool.h"
#include <algorithm>
#include <cmath>

namespace yatagami {

void applyDynamicGamma(const cv::Mat& gray, cv::Mat& dst) {
    cv::Scalar meanScalar = cv::mean(gray);
    double meanVal = std::clamp(meanScalar[0], 10.0, 245.0);

    double gamma = std::log(0.5) / std::log(meanVal / 255.0);
    gamma = std::clamp(gamma, 0.5, 2.0);

    if (std::abs(gamma - 1.0) < 0.05) {
        if (&dst != &gray) gray.copyTo(dst);
        return;
    }

    alignas(64) uchar lut[256];
    for (int i = 0; i < 256; ++i) {
        lut[i] = cv::saturate_cast<uchar>(std::pow(i / 255.0, gamma) * 255.0);
    }
    cv::Mat lutMat(1, 256, CV_8U, lut);
    cv::LUT(gray, lutMat, dst);
}

cv::Mat applyDynamicGamma(const cv::Mat& gray) {
    cv::Mat dst;
    applyDynamicGamma(gray, dst);
    return dst;
}

void removeShadowsGray(const cv::Mat& gray, cv::Mat& dst) {
    ScopedMat dilated(gray.rows, gray.cols, CV_8UC1);
    ScopedMat bg(gray.rows, gray.cols, CV_8UC1);
    ScopedMat diff(gray.rows, gray.cols, CV_8UC1);

    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(15, 15));
    cv::dilate(gray, *dilated, kernel);
    cv::medianBlur(*dilated, *bg, 15);

    cv::absdiff(*bg, gray, *diff);
    cv::subtract(cv::Scalar(255), *diff, dst);
    cv::normalize(dst, dst, 0, 255, cv::NORM_MINMAX, CV_8UC1);
}

cv::Mat removeShadowsGray(const cv::Mat& gray) {
    cv::Mat dst;
    removeShadowsGray(gray, dst);
    return dst;
}

void applyCLAHEGray(const cv::Mat& gray, cv::Mat& dst) {
    cv::Ptr<cv::CLAHE> clahe = cv::createCLAHE(2.0, cv::Size(8, 8));
    clahe->apply(gray, dst);
}

cv::Mat applyCLAHEGray(const cv::Mat& gray) {
    cv::Mat dst;
    applyCLAHEGray(gray, dst);
    return dst;
}

void applyNoiseReduction(const cv::Mat& gray, cv::Mat& dst) {
    cv::GaussianBlur(gray, dst, cv::Size(5, 5), 0);
}

cv::Mat applyNoiseReduction(const cv::Mat& gray) {
    cv::Mat dst;
    applyNoiseReduction(gray, dst);
    return dst;
}

void preprocessForDetection(const cv::Mat& bgr, cv::Mat& dst) {
    ScopedMat gray(bgr.rows, bgr.cols, CV_8UC1);
    cv::cvtColor(bgr, *gray, cv::COLOR_BGR2GRAY);

    // Fast 5x5 Gaussian blur preserves outer paper perimeter without CLAHE tile artifacts
    cv::GaussianBlur(*gray, dst, cv::Size(5, 5), 0);
}

cv::Mat preprocessForDetection(const cv::Mat& bgr) {
    cv::Mat dst;
    preprocessForDetection(bgr, dst);
    return dst;
}

} // namespace yatagami
