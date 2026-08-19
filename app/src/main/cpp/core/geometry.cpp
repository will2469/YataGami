#include "geometry.h"
#include "preprocessing.h"
#include "buffer_pool.h"
#include <algorithm>
#include <cmath>

namespace yatagami {

namespace {

float pointDist(const cv::Point2f& p1, const cv::Point2f& p2) {
    float dx = p1.x - p2.x;
    float dy = p1.y - p2.y;
    return std::sqrt(dx * dx + dy * dy);
}

float computeAngleDegrees(const cv::Point2f& pt1, const cv::Point2f& pt0, const cv::Point2f& pt2) {
    float dx1 = pt1.x - pt0.x;
    float dy1 = pt1.y - pt0.y;
    float dx2 = pt2.x - pt0.x;
    float dy2 = pt2.y - pt0.y;
    float dot = dx1 * dx2 + dy1 * dy2;
    float norm = std::sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2));
    if (norm < 1e-6f) return 0.0f;
    float cosAngle = std::clamp(dot / norm, -1.0f, 1.0f);
    return std::acos(cosAngle) * 180.0f / static_cast<float>(M_PI);
}

} // namespace

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

float calculateQuadConfidence(const std::vector<cv::Point2f>& corners, float imgWidth, float imgHeight) {
    if (corners.size() != 4) return 0.0f;

    std::vector<cv::Point2f> q = orderQuadCorners(corners);

    // 1. Area Ratio (0.25 weight)
    double quadArea = cv::contourArea(q);
    float totalArea = imgWidth * imgHeight;
    if (totalArea <= 0.0f) return 0.0f;
    float areaRatio = static_cast<float>(quadArea / totalArea);
    float areaScore = std::clamp((areaRatio - 0.05f) / 0.85f, 0.0f, 1.0f);

    // 2. Parallelism (0.20 weight)
    float topLen = pointDist(q[0], q[1]);
    float rightLen = pointDist(q[1], q[2]);
    float bottomLen = pointDist(q[2], q[3]);
    float leftLen = pointDist(q[3], q[0]);

    if (topLen < 1.0f || bottomLen < 1.0f || leftLen < 1.0f || rightLen < 1.0f) return 0.0f;
    float tbRatio = std::min(topLen / bottomLen, bottomLen / topLen);
    float lrRatio = std::min(leftLen / rightLen, rightLen / leftLen);
    float parallelScore = (tbRatio + lrRatio) * 0.5f;

    // 3. Convexity (0.15 weight)
    std::vector<cv::Point> qInt;
    for (const auto& p : q) qInt.emplace_back(static_cast<int>(p.x), static_cast<int>(p.y));
    float convexScore = cv::isContourConvex(qInt) ? 1.0f : 0.0f;

    // 4. Orthogonality (0.20 weight) - Target 90 deg +- 20 deg
    float a0 = computeAngleDegrees(q[3], q[0], q[1]);
    float a1 = computeAngleDegrees(q[0], q[1], q[2]);
    float a2 = computeAngleDegrees(q[1], q[2], q[3]);
    float a3 = computeAngleDegrees(q[2], q[3], q[0]);
    float avgAngleDev = (std::abs(a0 - 90.0f) + std::abs(a1 - 90.0f) +
                         std::abs(a2 - 90.0f) + std::abs(a3 - 90.0f)) / 4.0f;
    float orthoScore = std::clamp(1.0f - (avgAngleDev / 35.0f), 0.0f, 1.0f);

    // 5. Aspect Ratio Feasibility (0.15 weight) - Range 0.2 to 5.0
    float avgWidth = (topLen + bottomLen) * 0.5f;
    float avgHeight = (leftLen + rightLen) * 0.5f;
    float aspect = avgWidth / std::max(1.0f, avgHeight);
    float aspectScore = 0.0f;
    if (aspect >= 0.2f && aspect <= 5.0f) {
        // High score for standard ratios: A4 (0.707 or 1.414), ID cards (0.628 or 1.586)
        aspectScore = 1.0f;
    }

    // 6. Temporal Stability Base (0.05 weight)
    float stabilityScore = 0.9f;

    float finalScore = 0.25f * areaScore +
                       0.20f * parallelScore +
                       0.15f * convexScore +
                       0.20f * orthoScore +
                       0.15f * aspectScore +
                       0.05f * stabilityScore;

    return std::clamp(finalScore, 0.0f, 1.0f);
}

std::vector<cv::Point2f> detectDocumentCorners(const cv::Mat& img, float* outConfidence) {
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
    bool foundQuad = false;

    // Stage 1: Standard Polygon Approximation (epsilon = 0.02 * perimeter)
    for (const auto &contour : contours) {
        double perimeter = cv::arcLength(contour, true);
        std::vector<cv::Point> approx;
        cv::approxPolyDP(contour, approx, 0.02 * perimeter, true);

        if (approx.size() == 4 && cv::isContourConvex(approx)) {
            float area = static_cast<float>(cv::contourArea(approx));
            if (area > imgArea * 0.05f) {
                for (const auto &p : approx) {
                    docCorners.emplace_back(static_cast<float>(p.x), static_cast<float>(p.y));
                }
                foundQuad = true;
                break;
            }
        }
    }

    // Fallback Stage 1: Convex Hull + approxPolyDP with looser epsilon (0.04)
    if (!foundQuad && !contours.empty() && cv::contourArea(contours[0]) > imgArea * 0.05f) {
        std::vector<cv::Point> hull;
        cv::convexHull(contours[0], hull);
        double perimeter = cv::arcLength(hull, true);
        std::vector<cv::Point> approx;
        cv::approxPolyDP(hull, approx, 0.04 * perimeter, true);

        if (approx.size() == 4 && cv::isContourConvex(approx)) {
            for (const auto &p : approx) {
                docCorners.emplace_back(static_cast<float>(p.x), static_cast<float>(p.y));
            }
            foundQuad = true;
        }
    }

    // Fallback Stage 2: Minimum Area Bounding Rectangle (minAreaRect)
    if (!foundQuad && !contours.empty() && cv::contourArea(contours[0]) > imgArea * 0.05f) {
        cv::RotatedRect minRect = cv::minAreaRect(contours[0]);
        cv::Point2f rectPts[4];
        minRect.points(rectPts);
        for (int i = 0; i < 4; ++i) {
            docCorners.push_back(rectPts[i]);
        }
        foundQuad = true;
    }

    // Fallback Stage 3: Full Image Quadrilateral
    if (!foundQuad || docCorners.size() != 4) {
        docCorners = {
            {0.0f, 0.0f},
            {static_cast<float>(processImg.cols - 1), 0.0f},
            {static_cast<float>(processImg.cols - 1), static_cast<float>(processImg.rows - 1)},
            {0.0f, static_cast<float>(processImg.rows - 1)}
        };
    }

    docCorners = orderQuadCorners(docCorners);
    float confidence = calculateQuadConfidence(docCorners, processImg.cols, processImg.rows);

    if (outConfidence) {
        *outConfidence = confidence;
    }

    // Subpixel refinement with small window (5x5, maxIter=10, eps=0.01) if confidence is reliable
    if (confidence > 0.60f && foundQuad) {
        ScopedMat grayProcess(processImg.rows, processImg.cols, CV_8UC1);
        cv::cvtColor(processImg, *grayProcess, cv::COLOR_BGR2GRAY);
        cv::TermCriteria criteria(cv::TermCriteria::EPS + cv::TermCriteria::MAX_ITER, 10, 0.01);
        cv::cornerSubPix(*grayProcess, docCorners, cv::Size(5, 5), cv::Size(-1, -1), criteria);
    }

    for (auto &pt : docCorners) {
        pt.x = std::clamp(pt.x / scale, 0.0f, static_cast<float>(img.cols - 1));
        pt.y = std::clamp(pt.y / scale, 0.0f, static_cast<float>(img.rows - 1));
    }

    return docCorners;
}

} // namespace yatagami
