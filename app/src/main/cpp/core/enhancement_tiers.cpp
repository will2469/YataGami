#include "enhancement_tiers.h"
#include "buffer_pool.h"
#include <algorithm>
#include <vector>

namespace yatagami {

void applyClaheToL(cv::Mat& labL, double clipLimit, cv::Size tileSize) {
    cv::Ptr<cv::CLAHE> clahe = cv::createCLAHE(clipLimit, tileSize);
    clahe->apply(labL, labL);
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
    cv::max(illum, 50, illum); // Prevent division by zero
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
    ScopedMat lab(src.rows, src.cols, CV_8UC3);
    cv::cvtColor(src, *lab, cv::COLOR_BGR2Lab);

    std::vector<cv::Mat> channels(3);
    cv::split(*lab, channels);

    alignas(64) uchar lut[256];
    int hist[256] = {0};
    int totalPix = channels[0].rows * channels[0].cols;
    for (int r = 0; r < channels[0].rows; ++r) {
        const uchar* row = channels[0].ptr<uchar>(r);
        for (int c = 0; c < channels[0].cols; ++c) {
            hist[row[c]]++;
        }
    }

    int p1Count = totalPix * 0.01;
    int p99Count = totalPix * 0.99;
    int acc = 0;
    int minL = 0, maxL = 255;
    for (int i = 0; i < 256; ++i) {
        acc += hist[i];
        if (acc >= p1Count && minL == 0) minL = i;
        if (acc >= p99Count) { maxL = i; break; }
    }
    if (maxL <= minL) maxL = minL + 1;

    for (int i = 0; i < 256; ++i) {
        float v = 255.0f * (i - minL) / (maxL - minL);
        lut[i] = cv::saturate_cast<uchar>(v);
    }
    cv::Mat lutMat(1, 256, CV_8U, lut);
    cv::LUT(channels[0], lutMat, channels[0]);

    applyClaheToL(channels[0], 2.0, cv::Size(8, 8));

    cv::merge(channels, *lab);
    cv::cvtColor(*lab, dst, cv::COLOR_Lab2BGR);
}

void enhanceTier2Standard(const cv::Mat& src, cv::Mat& dst) {
    ScopedMat lab(src.rows, src.cols, CV_8UC3);
    cv::cvtColor(src, *lab, cv::COLOR_BGR2Lab);

    std::vector<cv::Mat> channels(3);
    cv::split(*lab, channels);

    // 1. Specular Glare clamp on L channel
    ScopedMat glareMask(channels[0].rows, channels[0].cols, CV_8UC1);
    cv::threshold(channels[0], *glareMask, 245, 255, cv::THRESH_BINARY);
    channels[0].setTo(242, *glareMask);

    // 2. True Auto White Point
    ScopedMat brightMask(channels[0].rows, channels[0].cols, CV_8UC1);
    cv::threshold(channels[0], *brightMask, 195, 255, cv::THRESH_BINARY);
    int brightCount = cv::countNonZero(*brightMask);
    if (brightCount > 100) {
        cv::Scalar meanA = cv::mean(channels[1], *brightMask);
        cv::Scalar meanB = cv::mean(channels[2], *brightMask);
        double shiftA = (meanA[0] - 128.0) * 0.85;
        double shiftB = (meanB[0] - 128.0) * 0.85;
        if (std::abs(shiftA) > 1.0) {
            cv::Mat fA;
            channels[1].convertTo(fA, CV_32F);
            fA = fA - shiftA;
            fA.convertTo(channels[1], CV_8U);
        }
        if (std::abs(shiftB) > 1.0) {
            cv::Mat fB;
            channels[2].convertTo(fB, CV_32F);
            fB = fB - shiftB;
            fB.convertTo(channels[2], CV_8U);
        }
    }

    // 3. CLAHE on L channel
    applyClaheToL(channels[0], 2.5, cv::Size(8, 8));

    cv::merge(channels, *lab);
    ScopedMat merged(src.rows, src.cols, CV_8UC3);
    cv::cvtColor(*lab, *merged, cv::COLOR_Lab2BGR);

    // 4. Fast Edge-Preserving Filter (Surface blur)
    fastEdgePreservingFilter(*merged, dst, 3, 25.0f);
}

void enhanceTier3Quality(const cv::Mat& src, cv::Mat& dst) {
    // STEP 1: Color Constancy on LAB
    ScopedMat lab(src.rows, src.cols, CV_8UC3);
    cv::cvtColor(src, *lab, cv::COLOR_BGR2Lab);
    std::vector<cv::Mat> channels(3);
    cv::split(*lab, channels);

    ScopedMat neutralMask(channels[0].rows, channels[0].cols, CV_8UC1);
    cv::inRange(*lab, cv::Scalar(180, 115, 115), cv::Scalar(255, 140, 140), *neutralMask);
    double minVal, maxVal;
    cv::minMaxLoc(channels[0], &minVal, &maxVal, nullptr, nullptr, *neutralMask);
    if (maxVal > 150.0) {
        float scale = 255.0f / static_cast<float>(maxVal);
        channels[0] = channels[0] * scale;
    }
    cv::merge(channels, *lab);
    ScopedMat colorCorrected(src.rows, src.cols, CV_8UC3);
    cv::cvtColor(*lab, *colorCorrected, cv::COLOR_Lab2BGR);

    // STEP 2: Background Illumination Estimation & Division
    ScopedMat gray(src.rows, src.cols, CV_8UC1);
    cv::cvtColor(*colorCorrected, *gray, cv::COLOR_BGR2GRAY);
    cv::Mat illum = estimateIllumination(*gray, 101);
    ScopedMat shadowCorrected(src.rows, src.cols, CV_8UC3);
    flattenIllumination(*colorCorrected, *shadowCorrected, illum, 0.8f);

    // STEP 3: Bilateral Denoising
    ScopedMat denoised(src.rows, src.cols, CV_8UC3);
    cv::bilateralFilter(*shadowCorrected, *denoised, 9, 75, 75);

    // STEP 4: CLAHE in LAB L channel
    cv::cvtColor(*denoised, *lab, cv::COLOR_BGR2Lab);
    cv::split(*lab, channels);
    applyClaheToL(channels[0], 4.0, cv::Size(8, 8));
    cv::merge(channels, *lab);
    ScopedMat clahed(src.rows, src.cols, CV_8UC3);
    cv::cvtColor(*lab, *clahed, cv::COLOR_Lab2BGR);

    // STEP 5: Adaptive Unsharp Masking at edges only
    ScopedMat edgeMap(src.rows, src.cols, CV_16S);
    cv::Laplacian(*gray, *edgeMap, CV_16S, 1);
    ScopedMat edgeAbs(src.rows, src.cols, CV_8UC1);
    cv::convertScaleAbs(*edgeMap, *edgeAbs);
    ScopedMat edgeMask(src.rows, src.cols, CV_8UC1);
    cv::threshold(*edgeAbs, *edgeMask, 18, 255, cv::THRESH_BINARY);

    ScopedMat blurred(src.rows, src.cols, CV_8UC3);
    cv::GaussianBlur(*clahed, *blurred, cv::Size(0, 0), 1.0);
    ScopedMat sharpened(src.rows, src.cols, CV_8UC3);
    cv::addWeighted(*clahed, 2.2, *blurred, -1.2, 0, *sharpened);

    clahed->copyTo(dst);
    sharpened->copyTo(dst, *edgeMask);
}

} // namespace yatagami
