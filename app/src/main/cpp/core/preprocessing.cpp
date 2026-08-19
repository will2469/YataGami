#include "preprocessing.h"
#include <algorithm>
#include <cmath>

namespace yatagami {

cv::Mat applyDynamicGamma(const cv::Mat& gray) {
    cv::Scalar meanScalar = cv::mean(gray);
    double meanVal = std::clamp(meanScalar[0], 10.0, 245.0);

    double gamma = std::log(0.5) / std::log(meanVal / 255.0);
    gamma = std::clamp(gamma, 0.4, 2.2);

    if (std::abs(gamma - 1.0) < 0.05) {
        return gray.clone();
    }

    uchar lut[256];
    for (int i = 0; i < 256; ++i) {
        lut[i] = cv::saturate_cast<uchar>(std::pow(i / 255.0, gamma) * 255.0);
    }
    cv::Mat lutMat(1, 256, CV_8U, lut);
    cv::Mat corrected;
    cv::LUT(gray, lutMat, corrected);
    return corrected;
}

cv::Mat removeShadowsGray(const cv::Mat& gray) {
    cv::Mat dilated, bg;
    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(21, 21));
    cv::dilate(gray, dilated, kernel);
    cv::medianBlur(dilated, bg, 21);

    cv::Mat diff;
    cv::absdiff(bg, gray, diff);
    cv::Mat normalized = 255 - diff;

    cv::Mat result;
    cv::normalize(normalized, result, 0, 255, cv::NORM_MINMAX, CV_8UC1);
    return result;
}

cv::Mat applyCLAHEGray(const cv::Mat& gray) {
    cv::Ptr<cv::CLAHE> clahe = cv::createCLAHE(2.5, cv::Size(8, 8));
    cv::Mat enhanced;
    clahe->apply(gray, enhanced);
    return enhanced;
}

cv::Mat applyNoiseReduction(const cv::Mat& gray) {
    cv::Mat filtered;
    cv::bilateralFilter(gray, filtered, 7, 50, 50);
    return filtered;
}

cv::Mat preprocessForDetection(const cv::Mat& bgr) {
    cv::Mat gray;
    cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);

    cv::Mat gammaCorrected = applyDynamicGamma(gray);
    cv::Mat shadowRemoved = removeShadowsGray(gammaCorrected);
    cv::Mat contrastEnhanced = applyCLAHEGray(shadowRemoved);
    cv::Mat denoised = applyNoiseReduction(contrastEnhanced);

    return denoised;
}

} // namespace yatagami
