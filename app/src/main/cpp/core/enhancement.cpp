#include "enhancement.h"
#include "preprocessing.h"
#include "buffer_pool.h"
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
    if (bgr.channels() == 3) {
        cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
    } else {
        gray = bgr;
    }

    int glareCount = cv::countNonZero(gray >= 250);
    return static_cast<double>(glareCount) / static_cast<double>(gray.rows * gray.cols);
}

void suppressGlare(const cv::Mat& bgr, cv::Mat& dst) {
    ScopedMat lab(bgr.rows, bgr.cols, CV_8UC3);
    cv::cvtColor(bgr, *lab, cv::COLOR_BGR2Lab);
    std::vector<cv::Mat> channels(3);
    cv::split(*lab, channels);

    // Cache-friendly 256-entry LUT (aligned to 64-byte Cortex-A76 cache line)
    alignas(64) uchar glareLut[256];
    for (int i = 0; i < 256; ++i) {
        if (i > 230) {
            glareLut[i] = static_cast<uchar>(230 + (i - 230) * 0.25f);
        } else {
            glareLut[i] = static_cast<uchar>(i);
        }
    }
    cv::Mat lutMat(1, 256, CV_8U, glareLut);
    cv::LUT(channels[0], lutMat, channels[0]);

    cv::merge(channels, *lab);
    cv::cvtColor(*lab, dst, cv::COLOR_Lab2BGR);
}

cv::Mat suppressGlare(const cv::Mat& bgr) {
    cv::Mat dst;
    suppressGlare(bgr, dst);
    return dst;
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

void enhanceImage(const cv::Mat& srcBgr, cv::Mat& dst, int mode) {
    cv::Mat bgr = srcBgr;
    ScopedMat glareSuppressed(srcBgr.rows, srcBgr.cols, CV_8UC3);

    int effectiveMode = mode;
    if (effectiveMode == 5) { // AUTO mode
        effectiveMode = recommendFilterMode(bgr);
        if (calculateGlareRatio(bgr) > 0.05) {
            suppressGlare(bgr, *glareSuppressed);
            bgr = *glareSuppressed;
        }
    }

    switch (effectiveMode) {
        case 1: { // Grayscale
            ScopedMat gray(bgr.rows, bgr.cols, CV_8UC1);
            cv::cvtColor(bgr, *gray, cv::COLOR_BGR2GRAY);
            cv::cvtColor(*gray, dst, cv::COLOR_GRAY2BGR);
            break;
        }
        case 2: { // Black & White with background shadow removal
            ScopedMat gray(bgr.rows, bgr.cols, CV_8UC1);
            cv::cvtColor(bgr, *gray, cv::COLOR_BGR2GRAY);

            ScopedMat cleanBg(bgr.rows, bgr.cols, CV_8UC1);
            removeShadowsGray(*gray, *cleanBg);

            ScopedMat blurred(bgr.rows, bgr.cols, CV_8UC1);
            cv::GaussianBlur(*cleanBg, *blurred, cv::Size(3, 3), 0);

            ScopedMat binary(bgr.rows, bgr.cols, CV_8UC1);
            cv::adaptiveThreshold(*blurred, *binary, 255, cv::ADAPTIVE_THRESH_GAUSSIAN_C,
                                  cv::THRESH_BINARY, 15, 8);
            cv::cvtColor(*binary, dst, cv::COLOR_GRAY2BGR);
            break;
        }
        case 3: { // Magic Color (Lab + CLAHE)
            ScopedMat lab(bgr.rows, bgr.cols, CV_8UC3);
            cv::cvtColor(bgr, *lab, cv::COLOR_BGR2Lab);
            std::vector<cv::Mat> channels(3);
            cv::split(*lab, channels);
            cv::Ptr<cv::CLAHE> clahe = cv::createCLAHE(2.0, cv::Size(8, 8));
            clahe->apply(channels[0], channels[0]);
            cv::merge(channels, *lab);
            cv::cvtColor(*lab, dst, cv::COLOR_Lab2BGR);
            break;
        }
        case 4: { // Sharpen (Unsharp Masking)
            ScopedMat blurred(bgr.rows, bgr.cols, CV_8UC3);
            cv::GaussianBlur(bgr, *blurred, cv::Size(0, 0), 3);
            cv::addWeighted(bgr, 1.5, *blurred, -0.5, 0, dst);
            break;
        }
        default:
            if (&dst != &bgr) {
                bgr.copyTo(dst);
            }
    }
}

cv::Mat enhanceImage(const cv::Mat& bgr, int mode) {
    cv::Mat dst;
    enhanceImage(bgr, dst, mode);
    return dst;
}

} // namespace yatagami
