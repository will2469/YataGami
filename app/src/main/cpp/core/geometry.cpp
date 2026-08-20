#include "geometry.h"
#include "preprocessing.h"
#include "buffer_pool.h"
#include <algorithm>
#include <cmath>
#include <vector>
#include <android/log.h>

#define LOG_TAG "YataGamiDocDet"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

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

struct QuadCandidate {
    std::vector<cv::Point2f> corners;
    float area;
    float confidence;
    float overallScore;
};

std::vector<cv::Point2f> extractFourCornersFromContour(const std::vector<cv::Point>& contour) {
    if (contour.size() < 4) return {};

    std::vector<cv::Point> hull;
    cv::convexHull(contour, hull);
    if (hull.size() < 4) return {};

    // 1. Try progressive polygon approximation
    double perimeter = cv::arcLength(hull, true);
    const double epsilons[] = {0.015, 0.02, 0.03, 0.04, 0.05, 0.07, 0.09, 0.12};
    for (double eps : epsilons) {
        std::vector<cv::Point> approx;
        cv::approxPolyDP(hull, approx, eps * perimeter, true);
        if (approx.size() == 4 && cv::isContourConvex(approx)) {
            std::vector<cv::Point2f> res;
            for (const auto& p : approx) {
                res.emplace_back(static_cast<float>(p.x), static_cast<float>(p.y));
            }
            return orderQuadCorners(res);
        }
    }

    // 2. Extreme 4-Corner Projection from Hull Points
    cv::Point tl = hull[0], tr = hull[0], br = hull[0], bl = hull[0];
    float minSum = static_cast<float>(hull[0].x + hull[0].y);
    float maxSum = static_cast<float>(hull[0].x + hull[0].y);
    float minDiff = static_cast<float>(hull[0].x - hull[0].y);
    float maxDiff = static_cast<float>(hull[0].x - hull[0].y);

    for (const auto& p : hull) {
        float sum = static_cast<float>(p.x + p.y);
        float diff = static_cast<float>(p.x - p.y);
        if (sum < minSum) { minSum = sum; tl = p; }
        if (sum > maxSum) { maxSum = sum; br = p; }
        if (diff > maxDiff) { maxDiff = diff; tr = p; }
        if (diff < minDiff) { minDiff = diff; bl = p; }
    }

    std::vector<cv::Point2f> res = {
        cv::Point2f(static_cast<float>(tl.x), static_cast<float>(tl.y)),
        cv::Point2f(static_cast<float>(tr.x), static_cast<float>(tr.y)),
        cv::Point2f(static_cast<float>(br.x), static_cast<float>(br.y)),
        cv::Point2f(static_cast<float>(bl.x), static_cast<float>(bl.y))
    };
    return orderQuadCorners(res);
}

} // namespace

std::vector<cv::Point2f> orderQuadCorners(const std::vector<cv::Point2f>& pts) {
    if (pts.size() != 4) return pts;

    // 1. Calculate centroid
    cv::Point2f center(0.0f, 0.0f);
    for (const auto& p : pts) {
        center.x += p.x;
        center.y += p.y;
    }
    center.x /= 4.0f;
    center.y /= 4.0f;

    // 2. Sort by polar angle around centroid (produces guaranteed clockwise order)
    struct PolarPoint {
        cv::Point2f pt;
        float angle;
    };
    std::vector<PolarPoint> polarPts(4);
    for (int i = 0; i < 4; ++i) {
        polarPts[i].pt = pts[i];
        polarPts[i].angle = std::atan2(pts[i].y - center.y, pts[i].x - center.x);
    }
    std::sort(polarPts.begin(), polarPts.end(), [](const PolarPoint& a, const PolarPoint& b) {
        return a.angle < b.angle;
    });

    // 3. Find the Top-Left corner (smallest x+y among polar points)
    int tlIndex = 0;
    float minSum = polarPts[0].pt.x + polarPts[0].pt.y;
    for (int i = 1; i < 4; ++i) {
        float sum = polarPts[i].pt.x + polarPts[i].pt.y;
        if (sum < minSum) {
            minSum = sum;
            tlIndex = i;
        }
    }

    // 4. Return in clockwise order: TL (0), TR (1), BR (2), BL (3)
    std::vector<cv::Point2f> ordered(4);
    for (int i = 0; i < 4; ++i) {
        ordered[i] = polarPts[(tlIndex + i) % 4].pt;
    }

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
    
    // Ignore full-frame fallback (>98%) or tiny noise (<3%)
    if (areaRatio > 0.98f || areaRatio < 0.03f) {
        return 0.0f;
    }

    float areaScore = std::clamp((areaRatio - 0.03f) / 0.85f, 0.0f, 1.0f);

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

    // 4. Orthogonality (0.20 weight) - Target 90 deg +- 30 deg
    float a0 = computeAngleDegrees(q[3], q[0], q[1]);
    float a1 = computeAngleDegrees(q[0], q[1], q[2]);
    float a2 = computeAngleDegrees(q[1], q[2], q[3]);
    float a3 = computeAngleDegrees(q[2], q[3], q[0]);
    float avgAngleDev = (std::abs(a0 - 90.0f) + std::abs(a1 - 90.0f) +
                         std::abs(a2 - 90.0f) + std::abs(a3 - 90.0f)) / 4.0f;
    float orthoScore = std::clamp(1.0f - (avgAngleDev / 45.0f), 0.0f, 1.0f);

    // 5. Aspect Ratio Feasibility (0.15 weight) - Range 0.2 to 5.0
    float avgWidth = (topLen + bottomLen) * 0.5f;
    float avgHeight = (leftLen + rightLen) * 0.5f;
    float aspect = avgWidth / std::max(1.0f, avgHeight);
    float aspectScore = 0.0f;
    if (aspect >= 0.2f && aspect <= 5.0f) {
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

    float imgArea = static_cast<float>(processImg.rows * processImg.cols);
    std::vector<QuadCandidate> candidates;

    auto evaluateAndAddContour = [&](const std::vector<cv::Point>& contour, float bonusWeight = 0.0f) {
        double area = cv::contourArea(contour);
        if (area < imgArea * 0.05f || area > imgArea * 0.95f) return;

        std::vector<cv::Point2f> bestCorners = extractFourCornersFromContour(contour);
        if (bestCorners.size() == 4) {
            float conf = calculateQuadConfidence(bestCorners, processImg.cols, processImg.rows);
            if (conf >= 0.25f) {
                float areaNorm = static_cast<float>(area / imgArea);
                float overallScore = conf * 0.4f + areaNorm * 0.6f + bonusWeight;
                candidates.push_back({bestCorners, static_cast<float>(area), conf, overallScore});
            }
        }
    };

    // PIPELINE 0: White Document Color Isolation (HSV: Low Saturation S <= 90, High Value V >= 90)
    // Completely strips colored beds, red strawberries, green leaves, and carpets!
    {
        ScopedMat hsv(processImg.rows, processImg.cols, CV_8UC3);
        cv::cvtColor(processImg, *hsv, cv::COLOR_BGR2HSV);

        std::vector<cv::Mat> hsvChannels;
        cv::split(*hsv, hsvChannels);
        cv::Mat sChan = hsvChannels[1];
        cv::Mat vChan = hsvChannels[2];

        ScopedMat sLow(processImg.rows, processImg.cols, CV_8UC1);
        ScopedMat vHigh(processImg.rows, processImg.cols, CV_8UC1);
        ScopedMat whiteDocMask(processImg.rows, processImg.cols, CV_8UC1);

        cv::threshold(sChan, *sLow, 90, 255, cv::THRESH_BINARY_INV);
        cv::threshold(vChan, *vHigh, 90, 255, cv::THRESH_BINARY);
        cv::bitwise_and(*sLow, *vHigh, *whiteDocMask);

        cv::Mat morphKernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(9, 9));
        cv::morphologyEx(*whiteDocMask, *whiteDocMask, cv::MORPH_CLOSE, morphKernel);

        std::vector<std::vector<cv::Point>> contours;
        cv::findContours(*whiteDocMask, contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);
        for (const auto& c : contours) evaluateAndAddContour(c, 0.35f); // 0.35 bonus for pure white paper
    }

    // PIPELINE 1: Multi-scale Canny Edges
    {
        ScopedMat edges(processImg.rows, processImg.cols, CV_8UC1);
        cv::Canny(*preprocessed, *edges, 30, 90);
        cv::Mat morphKernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(5, 5));
        cv::morphologyEx(*edges, *edges, cv::MORPH_CLOSE, morphKernel);

        std::vector<std::vector<cv::Point>> contours;
        cv::findContours(*edges, contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);
        for (const auto& c : contours) evaluateAndAddContour(c);
    }

    // PIPELINE 2: Otsu Binary Thresholding
    {
        ScopedMat binary(processImg.rows, processImg.cols, CV_8UC1);
        cv::threshold(*preprocessed, *binary, 0, 255, cv::THRESH_BINARY | cv::THRESH_OTSU);
        cv::Mat morphKernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(5, 5));
        cv::morphologyEx(*binary, *binary, cv::MORPH_CLOSE, morphKernel);

        std::vector<std::vector<cv::Point>> contours;
        cv::findContours(*binary, contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);
        for (const auto& c : contours) evaluateAndAddContour(c);
    }

    // PIPELINE 3: Otsu Binary Inverted Thresholding
    {
        ScopedMat binaryInv(processImg.rows, processImg.cols, CV_8UC1);
        cv::threshold(*preprocessed, *binaryInv, 0, 255, cv::THRESH_BINARY_INV | cv::THRESH_OTSU);
        cv::Mat morphKernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(5, 5));
        cv::morphologyEx(*binaryInv, *binaryInv, cv::MORPH_CLOSE, morphKernel);

        std::vector<std::vector<cv::Point>> contours;
        cv::findContours(*binaryInv, contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);
        for (const auto& c : contours) evaluateAndAddContour(c);
    }

    // Select the best candidate with maximum overallScore
    std::vector<cv::Point2f> docCorners;
    float bestConfidence = 0.0f;

    if (!candidates.empty()) {
        std::sort(candidates.begin(), candidates.end(),
            [](const QuadCandidate& a, const QuadCandidate& b) {
                return a.overallScore > b.overallScore;
            });

        docCorners = candidates[0].corners;
        bestConfidence = candidates[0].confidence;
        LOGD("Doc detected! Candidates=%zu, bestConf=%.2f, bestArea=%.0f", candidates.size(), bestConfidence, candidates[0].area);
    } else {
        LOGD("No doc candidate found in frame.");
    }

    // Fallback: Full Image Quadrilateral with 0 Confidence
    if (docCorners.size() != 4 || bestConfidence < 0.25f) {
        docCorners = {
            {0.0f, 0.0f},
            {static_cast<float>(processImg.cols - 1), 0.0f},
            {static_cast<float>(processImg.cols - 1), static_cast<float>(processImg.rows - 1)},
            {0.0f, static_cast<float>(processImg.rows - 1)}
        };
        if (outConfidence) {
            *outConfidence = 0.0f;
        }
    } else {
        if (outConfidence) {
            *outConfidence = bestConfidence;
        }

        // Subpixel refinement with small window (5x5) if confidence is high
        if (bestConfidence > 0.50f) {
            bool allInside = true;
            int margin = 6;
            for (const auto& pt : docCorners) {
                if (pt.x < margin || pt.x >= processImg.cols - margin ||
                    pt.y < margin || pt.y >= processImg.rows - margin) {
                    allInside = false;
                    break;
                }
            }

            if (allInside) {
                try {
                    ScopedMat grayProcess(processImg.rows, processImg.cols, CV_8UC1);
                    cv::cvtColor(processImg, *grayProcess, cv::COLOR_BGR2GRAY);
                    cv::TermCriteria criteria(cv::TermCriteria::EPS + cv::TermCriteria::MAX_ITER, 10, 0.01);
                    cv::cornerSubPix(*grayProcess, docCorners, cv::Size(5, 5), cv::Size(-1, -1), criteria);
                } catch (...) {}
            }
        }
    }

    for (auto &pt : docCorners) {
        pt.x = std::clamp(pt.x / scale, 0.0f, static_cast<float>(img.cols - 1));
        pt.y = std::clamp(pt.y / scale, 0.0f, static_cast<float>(img.rows - 1));
    }

    return docCorners;
}

} // namespace yatagami
