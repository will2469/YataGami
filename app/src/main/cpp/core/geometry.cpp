#include "geometry.h"
#include "preprocessing.h"
#include "buffer_pool.h"
#include <algorithm>

namespace yatagami {

std::vector<cv::Point2f> orderQuadCorners(const std::vector<cv::Point2f>& pts) {
    if (pts.size() != 4) return pts;

    std::vector<cv::Point2f> ordered(4);
    alignas(64) float sums[4];
    alignas(64) float diffs[4];
    for (int i = 0; i < 4; ++i) {
        sums[i] = pts[i].x + pts[i].y;
        diffs[i] = pts[i].x - pts[i].y;
    }

    int tlIdx = 0, brIdx = 0, trIdx = 0, blIdx = 0;
    float minSum = sums[0], maxSum = sums[0];
    float minDiff = diffs[0], maxDiff = diffs[0];

    for (int i = 1; i < 4; ++i) {
        if (sums[i] < minSum) { minSum = sums[i]; tlIdx = i; }
        if (sums[i] > maxSum) { maxSum = sums[i]; brIdx = i; }
        if (diffs[i] > maxDiff) { maxDiff = diffs[i]; trIdx = i; }
        if (diffs[i] < minDiff) { minDiff = diffs[i]; blIdx = i; }
    }

    ordered[0] = pts[tlIdx];
    ordered[1] = pts[trIdx];
    ordered[2] = pts[brIdx];
    ordered[3] = pts[blIdx];
    return ordered;
}

std::vector<cv::Point2f> detectDocumentCorners(const cv::Mat& img) {
    float maxDim = 640.0f;
    float scale = 1.0f;
    cv::Mat processImg;
    if (std::max(img.cols, img.rows) > maxDim) {
        scale = maxDim / static_cast<float>(std::max(img.cols, img.rows));
        int targetW = static_cast<int>(img.cols * scale);
        int targetH = static_cast<int>(img.rows * scale);
        cv::resize(img, processImg, cv::Size(targetW, targetH), 0, 0, cv::INTER_AREA);
    } else {
        processImg = img;
    }

    ScopedMat preprocessed(processImg.rows, processImg.cols, CV_8UC1);
    preprocessForDetection(processImg, *preprocessed);

    cv::Mat dummy;
    double highThresh = cv::threshold(*preprocessed, dummy, 0, 255, cv::THRESH_BINARY | cv::THRESH_OTSU);
    double lowThresh = std::max(20.0, 0.4 * highThresh);
    highThresh = std::min(220.0, std::max(70.0, highThresh));

    ScopedMat edges(processImg.rows, processImg.cols, CV_8UC1);
    cv::Canny(*preprocessed, *edges, lowThresh, highThresh);

    cv::Mat morphKernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(3, 3));
    cv::morphologyEx(*edges, *edges, cv::MORPH_CLOSE, morphKernel);

    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(*edges, contours, cv::RETR_LIST, cv::CHAIN_APPROX_SIMPLE);

    std::sort(contours.begin(), contours.end(),
        [](const auto &a, const auto &b) {
            return cv::contourArea(a) > cv::contourArea(b);
        });

    float imgArea = static_cast<float>(processImg.rows * processImg.cols);
    std::vector<cv::Point2f> docCorners;

    for (const auto &contour : contours) {
        double perimeter = cv::arcLength(contour, true);
        std::vector<cv::Point> approx;
        cv::approxPolyDP(contour, approx, 0.02 * perimeter, true);

        if (approx.size() == 4 && cv::isContourConvex(approx)) {
            float area = static_cast<float>(cv::contourArea(approx));
            if (area > imgArea * 0.12f) {
                for (const auto &p : approx) {
                    docCorners.emplace_back(static_cast<float>(p.x), static_cast<float>(p.y));
                }
                break;
            }
        }
    }

    if (docCorners.size() == 4) {
        docCorners = orderQuadCorners(docCorners);

        ScopedMat grayProcess(processImg.rows, processImg.cols, CV_8UC1);
        cv::cvtColor(processImg, *grayProcess, cv::COLOR_BGR2GRAY);
        cv::TermCriteria criteria(cv::TermCriteria::EPS + cv::TermCriteria::COUNT, 30, 0.05);
        cv::cornerSubPix(*grayProcess, docCorners, cv::Size(5, 5), cv::Size(-1, -1), criteria);

        for (auto &pt : docCorners) {
            pt.x = std::clamp(pt.x / scale, 0.0f, static_cast<float>(img.cols - 1));
            pt.y = std::clamp(pt.y / scale, 0.0f, static_cast<float>(img.rows - 1));
        }
    } else {
        docCorners = {
            {0.0f, 0.0f},
            {static_cast<float>(img.cols - 1), 0.0f},
            {static_cast<float>(img.cols - 1), static_cast<float>(img.rows - 1)},
            {0.0f, static_cast<float>(img.rows - 1)}
        };
    }

    return docCorners;
}

} // namespace yatagami
