#include "enhancement.h"
#include "preprocessing.h"
#include "buffer_pool.h"
#include "common.h"
#include <algorithm>
#include <cmath>
#include <vector>

namespace yatagami {

bool isNativeThermalThrottling() {
    FILE* f = fopen("/sys/class/thermal/thermal_zone0/temp", "r");
    if (f) {
        int tempMilli = 0;
        if (fscanf(f, "%d", &tempMilli) == 1) {
            fclose(f);
            // Helio G100 / MT6789 thermal throttling threshold ~45°C
            return tempMilli > 45000;
        }
        fclose(f);
    }
    return false;
}

double calculateBlurScore(const cv::Mat& bgr) {
    cv::Mat gray;
    if (bgr.channels() == 3) {
        cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
    } else if (bgr.channels() == 4) {
        cv::cvtColor(bgr, gray, cv::COLOR_RGBA2GRAY);
    } else {
        gray = bgr;
    }

    float scale = 1.0f;
    float maxDim = 600.0f;
    cv::Mat small;
    if (std::max(gray.cols, gray.rows) > maxDim) {
        scale = maxDim / static_cast<float>(std::max(gray.cols, gray.rows));
        int targetW = static_cast<int>(gray.cols * scale);
        int targetH = static_cast<int>(gray.rows * scale);
        cv::resize(gray, small, cv::Size(targetW, targetH), 0, 0, cv::INTER_AREA);
    } else {
        small = gray;
    }

    ScopedMat lap(small.rows, small.cols, CV_64F);
    cv::Laplacian(small, *lap, CV_64F);
    cv::Scalar mean, stddev;
    cv::meanStdDev(*lap, mean, stddev);
    return stddev[0] * stddev[0];
}

double calculateGlareRatio(const cv::Mat& bgr) {
    cv::Mat gray;
    if (bgr.channels() == 3) cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
    else if (bgr.channels() == 4) cv::cvtColor(bgr, gray, cv::COLOR_RGBA2GRAY);
    else gray = bgr;

    int glareCount = cv::countNonZero(gray >= 248);
    return static_cast<double>(glareCount) / static_cast<double>(gray.rows * gray.cols);
}

SceneAnalysis analyzeScene(const cv::Mat& bgr) {
    SceneAnalysis s;
    cv::Mat gray;
    if (bgr.channels() == 3) cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
    else if (bgr.channels() == 4) cv::cvtColor(bgr, gray, cv::COLOR_RGBA2GRAY);
    else gray = bgr;

    cv::Scalar meanL, stdL;
    cv::meanStdDev(gray, meanL, stdL);
    s.meanL = static_cast<float>(meanL[0]);
    s.stdDevL = static_cast<float>(stdL[0]);

    float totalPix = static_cast<float>(gray.rows * gray.cols);
    int darkCount = cv::countNonZero(gray <= 50);
    int brightCount = cv::countNonZero(gray >= 230);
    int glareCount = cv::countNonZero(gray >= 248);

    s.darkRatio = darkCount / totalPix;
    s.brightRatio = brightCount / totalPix;
    s.glareRatio = glareCount / totalPix;
    s.blurScore = static_cast<float>(calculateBlurScore(bgr));

    // Noise estimation via 3x3 median difference
    cv::Mat med;
    cv::medianBlur(gray, med, 3);
    cv::Mat diff;
    cv::absdiff(gray, med, diff);
    cv::Scalar meanNoise;
    cv::meanStdDev(diff, meanNoise, stdL);
    s.noiseEstimate = static_cast<float>(meanNoise[0]);

    s.hasShadow = (s.darkRatio > 0.15f);
    s.hasGlare = (s.glareRatio > 0.03f);
    s.colorCastMag = 0.0f;

    return s;
}

EnhancementTier autoSelectTier(const SceneAnalysis& s) {
    if (s.blurScore < 50.0f || isNativeThermalThrottling() || s.brightRatio > 0.35f) {
        return EnhancementTier::TIER_1_FAST;
    }
    if ((s.darkRatio > 0.25f && s.hasShadow) || (s.glareRatio > 0.05f && s.hasGlare) ||
        (s.noiseEstimate > 25.0f && s.meanL < 85.0f)) {
        return EnhancementTier::TIER_3_QUALITY;
    }
    return EnhancementTier::TIER_2_STANDARD;
}

int recommendFilterMode(const cv::Mat& bgr) {
    ScopedMat lab(bgr.rows, bgr.cols, CV_8UC3);
    cv::cvtColor(bgr, *lab, cv::COLOR_BGR2Lab);
    std::vector<cv::Mat> channels(3);
    cv::split(*lab, channels);

    cv::Scalar meanA, stdA, meanB, stdB;
    cv::meanStdDev(channels[1], meanA, stdA);
    cv::meanStdDev(channels[2], meanB, stdB);
    double chromaStd = std::sqrt(stdA[0] * stdA[0] + stdB[0] * stdB[0]);

    cv::Mat gray = channels[0];
    cv::Scalar meanL, stdL;
    cv::meanStdDev(gray, meanL, stdL);

    if (chromaStd > 10.0) {
        if (stdL[0] < 50.0 || meanL[0] < 120.0) return 3; // Magic Color
        return 4; // Sharpen
    }

    int whitePixels = cv::countNonZero(gray > 180);
    double whiteRatio = static_cast<double>(whitePixels) / static_cast<double>(gray.rows * gray.cols);

    if (whiteRatio > 0.55 && stdL[0] > 45.0) return 2; // Black & White
    if (meanL[0] < 110.0 || stdL[0] < 35.0) return 3; // Magic Color
    return 1; // Grayscale
}

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
    cv::max(illum, 50, illum); // Prevent division by zero and over-amplification
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

    // 1. White Point dynamic range percentile stretch on L channel via 256-byte L1 LUT
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

    // 2. CLAHE exclusively on Lightness channel
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

    // 2. True Auto White Point: Color Cast compensation on A & B channels from bright highlight region
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

void generateMasterEnhanced(const cv::Mat& src, cv::Mat& dst, EnhancementTier tier) {
    EnhancementTier selected = tier;
    if (selected == EnhancementTier::TIER_AUTO) {
        SceneAnalysis s = analyzeScene(src);
        selected = autoSelectTier(s);
    }

    switch (selected) {
        case EnhancementTier::TIER_1_FAST:
            enhanceTier1Fast(src, dst);
            break;
        case EnhancementTier::TIER_3_QUALITY:
            enhanceTier3Quality(src, dst);
            break;
        case EnhancementTier::TIER_2_STANDARD:
        default:
            enhanceTier2Standard(src, dst);
            break;
    }
}

void applyFilterMode(const cv::Mat& master, cv::Mat& dst, int mode) {
    switch (mode) {
        case 1: { // Grayscale
            ScopedMat gray(master.rows, master.cols, CV_8UC1);
            cv::cvtColor(master, *gray, cv::COLOR_BGR2GRAY);
            cv::cvtColor(*gray, dst, cv::COLOR_GRAY2BGR);
            break;
        }
        case 2: { // High Contrast Black & White
            ScopedMat gray(master.rows, master.cols, CV_8UC1);
            cv::cvtColor(master, *gray, cv::COLOR_BGR2GRAY);
            ScopedMat binary(master.rows, master.cols, CV_8UC1);
            cv::adaptiveThreshold(*gray, *binary, 255, cv::ADAPTIVE_THRESH_GAUSSIAN_C, cv::THRESH_BINARY, 15, 8);
            cv::cvtColor(*binary, dst, cv::COLOR_GRAY2BGR);
            break;
        }
        case 3: { // Magic Color (LAB A & B vibrance scale x1.18)
            ScopedMat lab(master.rows, master.cols, CV_8UC3);
            cv::cvtColor(master, *lab, cv::COLOR_BGR2Lab);
            std::vector<cv::Mat> ch(3);
            cv::split(*lab, ch);
            ch[1] = ch[1] * 1.18f;
            ch[2] = ch[2] * 1.18f;
            cv::merge(ch, *lab);
            cv::cvtColor(*lab, dst, cv::COLOR_Lab2BGR);
            break;
        }
        case 4: { // Sharpen (Unsharp mask r=1.0)
            ScopedMat blurred(master.rows, master.cols, CV_8UC3);
            cv::GaussianBlur(master, *blurred, cv::Size(0, 0), 1.0);
            cv::addWeighted(master, 2.0, *blurred, -1.0, 0, dst);
            break;
        }
        case 5: // Auto mode -> Use Master as-is
        case 0: // None
        default:
            if (&dst != &master) {
                master.copyTo(dst);
            }
            break;
    }
}

void enhanceImage(const cv::Mat& bgr, cv::Mat& dst, int mode, EnhancementTier tier) {
    ScopedMat master(bgr.rows, bgr.cols, CV_8UC3);
    generateMasterEnhanced(bgr, *master, tier);
    applyFilterMode(*master, dst, mode);
}

cv::Mat enhanceImage(const cv::Mat& bgr, int mode, EnhancementTier tier) {
    cv::Mat dst;
    enhanceImage(bgr, dst, mode, tier);
    return dst;
}

} // namespace yatagami
