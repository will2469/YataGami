#include "enhancement.h"
#include "preprocessing.h"
#include <algorithm>
#include <cmath>
#include <vector>

namespace yatagami {

double calculateBlurScore(const cv::Mat& bgr) {
    cv::Mat gray;
    if (bgr.channels() == 3) {
        cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
    } else {
        gray = bgr;
    }

    float scale = 1.0f;
    float maxDim = 800.0f;
    cv::Mat small;
    if (std::max(gray.cols, gray.rows) > maxDim) {
        scale = maxDim / static_cast<float>(std::max(gray.cols, gray.rows));
        cv::resize(gray, small, cv::Size(), scale, scale, cv::INTER_AREA);
    } else {
        small = gray;
    }

    cv::Mat lap;
    cv::Laplacian(small, lap, CV_64F);
    cv::Scalar mean, stddev;
    cv::meanStdDev(lap, mean, stddev);
    return stddev[0] * stddev[0];
}

double calculateGlareRatio(const cv::Mat& bgr) {
    cv::Mat gray;
    if (bgr.channels() == 3) {
        cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
    } else {
        gray = bgr;
    }

    int glareCount = cv::countNonZero(gray >= 250);
    return static_cast<double>(glareCount) / static_cast<double>(gray.rows * gray.cols);
}

cv::Mat suppressGlare(const cv::Mat& bgr) {
    cv::Mat lab;
    cv::cvtColor(bgr, lab, cv::COLOR_BGR2Lab);
    std::vector<cv::Mat> channels(3);
    cv::split(lab, channels);

    cv::Mat compressed;
    channels[0].convertTo(compressed, CV_32F);
    compressed = cv::min(compressed, 230.0f + (compressed - 230.0f) * 0.25f);
    compressed.convertTo(channels[0], CV_8U);

    cv::merge(channels, lab);
    cv::Mat result;
    cv::cvtColor(lab, result, cv::COLOR_Lab2BGR);
    return result;
}

int recommendFilterMode(const cv::Mat& bgr) {
    cv::Mat lab;
    cv::cvtColor(bgr, lab, cv::COLOR_BGR2Lab);
    std::vector<cv::Mat> channels(3);
    cv::split(lab, channels);

    cv::Scalar meanA, stdA, meanB, stdB;
    cv::meanStdDev(channels[1], meanA, stdA);
    cv::meanStdDev(channels[2], meanB, stdB);
    double chromaStd = std::sqrt(stdA[0] * stdA[0] + stdB[0] * stdB[0]);

    cv::Mat gray = channels[0];
    cv::Scalar meanL, stdL;
    cv::meanStdDev(gray, meanL, stdL);

    if (chromaStd > 10.0) {
        if (stdL[0] < 50.0 || meanL[0] < 120.0) {
            return 3; // Magic Color
        }
        return 4; // Sharpen
    }

    int whitePixels = cv::countNonZero(gray > 180);
    double whiteRatio = static_cast<double>(whitePixels) / static_cast<double>(gray.rows * gray.cols);

    if (whiteRatio > 0.55 && stdL[0] > 45.0) {
        return 2; // Black & White
    } else if (meanL[0] < 110.0 || stdL[0] < 35.0) {
        return 3; // Magic Color
    } else {
        return 1; // Grayscale
    }
}

cv::Mat enhanceImage(const cv::Mat& srcBgr, int mode) {
    cv::Mat bgr = srcBgr;
    cv::Mat processed;

    int effectiveMode = mode;
    if (effectiveMode == 5) { // AUTO mode
        effectiveMode = recommendFilterMode(bgr);
        if (calculateGlareRatio(bgr) > 0.05) {
            bgr = suppressGlare(bgr);
        }
    }

    switch (effectiveMode) {
        case 1: { // Grayscale
            cv::cvtColor(bgr, processed, cv::COLOR_BGR2GRAY);
            cv::cvtColor(processed, processed, cv::COLOR_GRAY2BGR);
            break;
        }
        case 2: { // Black & White with background shadow removal
            cv::Mat gray;
            cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
            cv::Mat cleanBg = removeShadowsGray(gray);
            cv::Mat blurred;
            cv::GaussianBlur(cleanBg, blurred, cv::Size(3, 3), 0);
            cv::adaptiveThreshold(blurred, processed, 255, cv::ADAPTIVE_THRESH_GAUSSIAN_C,
                                  cv::THRESH_BINARY, 15, 8);
            cv::cvtColor(processed, processed, cv::COLOR_GRAY2BGR);
            break;
        }
        case 3: { // Magic Color (Lab + CLAHE)
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
        case 4: { // Sharpen (Unsharp Masking)
            cv::Mat blurred;
            cv::GaussianBlur(bgr, blurred, cv::Size(0, 0), 3);
            cv::addWeighted(bgr, 1.5, blurred, -0.5, 0, processed);
            break;
        }
        default:
            processed = bgr.clone();
    }

    return processed;
}

} // namespace yatagami
