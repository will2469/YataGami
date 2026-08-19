#include "scene_analysis.h"
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

} // namespace yatagami
