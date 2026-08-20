#include "enhancement_tiers.h"
#include "buffer_pool.h"
#include <algorithm>
#include <vector>
#include <cmath>

namespace yatagami {

void applyClaheToL(cv::Mat& labL, double clipLimit, cv::Size tileSize) {
    cv::Ptr<cv::CLAHE> clahe = cv::createCLAHE(clipLimit, tileSize);
    clahe->apply(labL, labL);
}

void applySpecularGlareSuppression(cv::Mat& lChannel) {
    alignas(64) uchar lut[256];
    for (int i = 0; i < 256; ++i) {
        if (i < 235) {
            lut[i] = static_cast<uchar>(i);
        } else {
            double diff = static_cast<double>(i - 235);
            double compressed = 235.0 + 9.0 * std::tanh(diff / 10.0);
            lut[i] = cv::saturate_cast<uchar>(compressed);
        }
    }
    cv::Mat lutMat(1, 256, CV_8U, lut);
    cv::LUT(lChannel, lutMat, lChannel);
}

void fastEdgePreservingFilter(const cv::Mat& src, cv::Mat& dst, int radius, float threshold) {
    ScopedMat blurred(src.rows, src.cols, src.type());
    cv::boxFilter(src, *blurred, -1, cv::Size(radius * 2 + 1, radius * 2 + 1));

    ScopedMat diff(src.rows, src.cols, src.type());
    cv::absdiff(src, *blurred, *diff);

    ScopedMat grayDiff(src.rows, src.cols, CV_8UC1);
    if (src.channels() == 3) cv::cvtColor(*diff, *grayDiff, cv::COLOR_BGR2GRAY);
    else *grayDiff = *diff;

    ScopedMat mask(src.rows, src.cols, CV_8UC1);
    cv::threshold(*grayDiff, *mask, threshold, 255, cv::THRESH_BINARY_INV);

    src.copyTo(dst);
    blurred->copyTo(dst, *mask);
}

cv::Mat estimateIllumination(const cv::Mat& grayOrL, int blurRadius) {
    cv::Mat illum;
    int ksize = (blurRadius % 2 == 0) ? blurRadius + 1 : blurRadius;
    cv::GaussianBlur(grayOrL, illum, cv::Size(ksize, ksize), 0);
    cv::max(illum, 50, illum);
    return illum;
}

void flattenIllumination(const cv::Mat& src, cv::Mat& dst, const cv::Mat& illum, float strength) {
    cv::Mat floatSrc, floatIllum;
    src.convertTo(floatSrc, CV_32F, 1.0 / 255.0);
    illum.convertTo(floatIllum, CV_32F, 1.0 / 255.0);

    cv::Mat powIllum;
    cv::pow(floatIllum, strength, powIllum);

    cv::Mat result = floatSrc / powIllum;
    cv::normalize(result, result, 0.0, 1.0, cv::NORM_MINMAX);
    result.convertTo(dst, src.type(), 255.0);
}

void enhanceTier1Fast(const cv::Mat& src, cv::Mat& dst) {
    enhanceTier2Standard(src, dst);
}

void enhanceTier2Standard(const cv::Mat& src, cv::Mat& dst) {
    if (src.empty()) return;

    // 1. Calculate luminance histogram to determine precise black and white reference levels
    cv::Mat gray;
    cv::cvtColor(src, gray, cv::COLOR_BGR2GRAY);

    int hist[256] = {0};
    int totalPix = gray.rows * gray.cols;
    for (int r = 0; r < gray.rows; ++r) {
        const uchar* ptr = gray.ptr<uchar>(r);
        for (int c = 0; c < gray.cols; ++c) {
            hist[ptr[c]]++;
        }
    }

    int pLowCount = static_cast<int>(totalPix * 0.02);  // 2% darkest (ink)
    int pHighCount = static_cast<int>(totalPix * 0.96); // 96% brightest (paper background)

    int blackPt = 0, whitePt = 255;
    int acc = 0;
    for (int i = 0; i < 256; ++i) {
        acc += hist[i];
        if (acc >= pLowCount && blackPt == 0) blackPt = i;
        if (acc >= pHighCount) { whitePt = i; break; }
    }

    blackPt = std::clamp(blackPt, 15, 75);
    whitePt = std::clamp(whitePt, 175, 245);
    if (whitePt <= blackPt + 30) whitePt = blackPt + 30;

    // 2. Build smooth 256-entry Look-Up Table (LUT) for natural paper & ink dynamic range
    alignas(64) uchar lut[256];
    float range = static_cast<float>(whitePt - blackPt);
    for (int i = 0; i < 256; ++i) {
        float val = 255.0f * (static_cast<float>(i) - blackPt) / range;
        val = std::clamp(val, 0.0f, 255.0f);
        // Gentle gamma 0.95 keeps colored headers (purple, green, red) rich and vibrant
        val = std::pow(val / 255.0f, 0.95f) * 255.0f;
        lut[i] = cv::saturate_cast<uchar>(val);
    }
    cv::Mat lutMat(1, 256, CV_8U, lut);

    ScopedMat stretched(src.rows, src.cols, CV_8UC3);
    cv::LUT(src, lutMat, *stretched);

    // 3. Crisp Unsharp Masking for razor-sharp table text and small numbers
    ScopedMat blurred(src.rows, src.cols, CV_8UC3);
    cv::GaussianBlur(*stretched, *blurred, cv::Size(0, 0), 1.0);
    cv::addWeighted(*stretched, 1.25, *blurred, -0.25, 0, dst);
}

void enhanceTier3Quality(const cv::Mat& src, cv::Mat& dst) {
    enhanceTier2Standard(src, dst);
}

} // namespace yatagami
