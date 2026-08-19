#include "deskew.h"
#include "geometry.h"
#include "buffer_pool.h"
#include "common.h"
#include <algorithm>
#include <cmath>

namespace yatagami {

namespace {

float dist(const cv::Point2f& p1, const cv::Point2f& p2) {
    float dx = p1.x - p2.x;
    float dy = p1.y - p2.y;
    return std::sqrt(dx * dx + dy * dy);
}

} // namespace

DocumentTypeResult inferDocumentTypeAndSize(const std::vector<cv::Point2f>& corners) {
    DocumentTypeResult result;
    result.type = DocType::A4;
    result.isPortrait = true;
    result.confidence = 0.5f;
    result.targetWidth = 1240;
    result.targetHeight = 1754;

    if (corners.size() != 4) return result;

    std::vector<cv::Point2f> q = orderQuadCorners(corners);
    float topLen = dist(q[0], q[1]);
    float bottomLen = dist(q[3], q[2]);
    float leftLen = dist(q[0], q[3]);
    float rightLen = dist(q[1], q[2]);

    float avgW = (topLen + bottomLen) * 0.5f;
    float avgH = (leftLen + rightLen) * 0.5f;
    if (avgH <= 0.0f) avgH = 1.0f;

    float ratio = avgW / avgH; // > 1.0 means Landscape, < 1.0 means Portrait
    result.isPortrait = (avgH >= avgW);

    // Standard 150 DPI dimensions
    // KTP / ID Card: standard 85.6mm x 53.98mm = 1.586 ratio
    if (std::abs(ratio - 1.586f) < 0.15f) {
        result.type = DocType::KTP;
        result.isPortrait = false;
        result.targetWidth = 799;
        result.targetHeight = 505;
        result.confidence = 0.95f;
        return result;
    } else if (std::abs(ratio - 0.630f) < 0.10f) {
        result.type = DocType::KTP;
        result.isPortrait = true;
        result.targetWidth = 505;
        result.targetHeight = 799;
        result.confidence = 0.95f;
        return result;
    }

    // Receipt: Long aspect ratio
    if (avgH / avgW >= 2.0f) {
        result.type = DocType::RECEIPT;
        result.isPortrait = true;
        result.targetWidth = 472; // ~80mm @ 150 DPI
        float r = std::clamp(avgH / avgW, 2.0f, 5.0f);
        result.targetHeight = std::min(5906, static_cast<int>(result.targetWidth * r));
        result.confidence = 0.90f;
        return result;
    }

    // Square: 1.0 ratio
    if (std::abs(ratio - 1.0f) < 0.12f) {
        result.type = DocType::SQUARE;
        result.isPortrait = true;
        result.targetWidth = 1200;
        result.targetHeight = 1200;
        result.confidence = 0.90f;
        return result;
    }

    // F4 / Folio (Asia): 215mm x 330mm = 1.535 ratio
    if (std::abs(ratio - 0.651f) < 0.08f) {
        result.type = DocType::F4;
        result.isPortrait = true;
        result.targetWidth = 1270;
        result.targetHeight = 1949;
        result.confidence = 0.90f;
        return result;
    } else if (std::abs(ratio - 1.535f) < 0.08f) {
        result.type = DocType::F4;
        result.isPortrait = false;
        result.targetWidth = 1949;
        result.targetHeight = 1270;
        result.confidence = 0.90f;
        return result;
    }

    // A4 (Default): 1.414 ratio
    if (!result.isPortrait) {
        result.type = DocType::A4;
        result.targetWidth = 1754;
        result.targetHeight = 1240;
        result.confidence = 0.88f;
    } else {
        result.type = DocType::A4;
        result.targetWidth = 1240;
        result.targetHeight = 1754;
        result.confidence = 0.88f;
    }

    return result;
}

double detectTextSkewAngle(const cv::Mat& bgr, DocType type) {
    // Skip skew detection for non-text / structured documents
    if (type == DocType::KTP || type == DocType::SQUARE) {
        return 0.0;
    }

    cv::Mat gray;
    if (bgr.channels() == 3) {
        cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
    } else if (bgr.channels() == 4) {
        cv::cvtColor(bgr, gray, cv::COLOR_RGBA2GRAY);
    } else {
        gray = bgr;
    }

    // Resize for high speed text analysis
    cv::Mat smallImg;
    double scale = 600.0 / std::max(gray.cols, gray.rows);
    if (scale < 1.0) {
        cv::resize(gray, smallImg, cv::Size(), scale, scale, cv::INTER_AREA);
    } else {
        smallImg = gray;
    }

    cv::Mat binary;
    cv::adaptiveThreshold(smallImg, binary, 255, cv::ADAPTIVE_THRESH_GAUSSIAN_C, cv::THRESH_BINARY_INV, 15, 10);

    // Text density verification (must be between 2% and 40% black pixels)
    int textPixels = cv::countNonZero(binary);
    float textRatio = static_cast<float>(textPixels) / static_cast<float>(binary.total());
    if (textRatio < 0.02f || textRatio > 0.40f) {
        return 0.0;
    }

    // Horizontal morphological line kernel
    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(25, 1));
    cv::Mat textLines;
    cv::morphologyEx(binary, textLines, cv::MORPH_CLOSE, kernel);

    // Search optimal skew angle in [-15, +15] degrees with 0.5 degree steps
    double bestScore = -1.0;
    double bestAngle = 0.0;
    cv::Point2f center(smallImg.cols / 2.0f, smallImg.rows / 2.0f);

    for (double angle = -15.0; angle <= 15.0; angle += 0.5) {
        cv::Mat rotMat = cv::getRotationMatrix2D(center, angle, 1.0);
        cv::Mat rotated;
        cv::warpAffine(textLines, rotated, rotMat, textLines.size(), cv::INTER_NEAREST, cv::BORDER_CONSTANT, cv::Scalar(0));

        // Horizontal projection variance
        cv::Mat proj;
        cv::reduce(rotated, proj, 1, cv::REDUCE_SUM, CV_32S);

        cv::Scalar mean, stddev;
        cv::meanStdDev(proj, mean, stddev);
        double variance = stddev[0] * stddev[0];

        if (variance > bestScore) {
            bestScore = variance;
            bestAngle = angle;
        }
    }

    return bestAngle;
}

cv::Mat deskewImage(const cv::Mat& src, double& outAngle, DocType type) {
    outAngle = detectTextSkewAngle(src, type);
    if (std::abs(outAngle) < 0.3) {
        return src;
    }

    cv::Point2f center(src.cols / 2.0f, src.rows / 2.0f);
    cv::Mat rotMat = cv::getRotationMatrix2D(center, outAngle, 1.0);

    cv::Rect2f bbox = cv::RotatedRect(cv::Point2f(), src.size(), static_cast<float>(outAngle)).boundingRect2f();
    rotMat.at<double>(0, 2) += bbox.width / 2.0 - src.cols / 2.0;
    rotMat.at<double>(1, 2) += bbox.height / 2.0 - src.rows / 2.0;

    cv::Mat dst;
    cv::warpAffine(src, dst, rotMat, bbox.size(), cv::INTER_LINEAR, cv::BORDER_CONSTANT, cv::Scalar(255, 255, 255));
    return dst;
}

cv::Mat autoFixOrientation(const cv::Mat& bgr, DocType type) {
    if (type == DocType::SQUARE || type == DocType::KTP) {
        return bgr;
    }

    cv::Mat gray;
    if (bgr.channels() == 3) {
        cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
    } else if (bgr.channels() == 4) {
        cv::cvtColor(bgr, gray, cv::COLOR_RGBA2GRAY);
    } else {
        gray = bgr;
    }

    // Multi-heuristic text baseline horizontal projection check
    cv::Mat binary;
    cv::adaptiveThreshold(gray, binary, 255, cv::ADAPTIVE_THRESH_GAUSSIAN_C, cv::THRESH_BINARY_INV, 15, 10);

    cv::Mat projH, projV;
    cv::reduce(binary, projH, 1, cv::REDUCE_SUM, CV_32S);
    cv::reduce(binary, projV, 0, cv::REDUCE_SUM, CV_32S);

    cv::Scalar meanH, stdH, meanV, stdV;
    cv::meanStdDev(projH, meanH, stdH);
    cv::meanStdDev(projV, meanV, stdV);

    // Text variance ratio: horizontal text rows produce higher variance than vertical columns
    double varRatio = (stdH[0] + 1e-5) / (stdV[0] + 1e-5);
    if (varRatio < 0.65) {
        // Text is aligned vertically -> Rotate 90 degrees clockwise
        cv::Mat rotated;
        cv::rotate(bgr, rotated, cv::ROTATE_90_CLOCKWISE);
        return rotated;
    }

    return bgr;
}

cv::Mat trimContentMargins(const cv::Mat& src) {
    cv::Mat gray;
    if (src.channels() == 3) cv::cvtColor(src, gray, cv::COLOR_BGR2GRAY);
    else if (src.channels() == 4) cv::cvtColor(src, gray, cv::COLOR_RGBA2GRAY);
    else gray = src;

    cv::Mat binary;
    cv::adaptiveThreshold(gray, binary, 255, cv::ADAPTIVE_THRESH_GAUSSIAN_C, cv::THRESH_BINARY_INV, 15, 10);

    // Horizontal and vertical projections
    cv::Mat projH, projV;
    cv::reduce(binary, projH, 1, cv::REDUCE_SUM, CV_32S);
    cv::reduce(binary, projV, 0, cv::REDUCE_SUM, CV_32S);

    int top = 0, bottom = src.rows - 1;
    int left = 0, right = src.cols - 1;
    int margin = 10; // 10px safety margin

    for (int r = 0; r < src.rows; ++r) {
        if (projH.at<int>(r, 0) > 0) { top = std::max(0, r - margin); break; }
    }
    for (int r = src.rows - 1; r >= 0; --r) {
        if (projH.at<int>(r, 0) > 0) { bottom = std::min(src.rows - 1, r + margin); break; }
    }
    for (int c = 0; c < src.cols; ++c) {
        if (projV.at<int>(0, c) > 0) { left = std::max(0, c - margin); break; }
    }
    for (int c = src.cols - 1; c >= 0; --c) {
        if (projV.at<int>(0, c) > 0) { right = std::min(src.cols - 1, c + margin); break; }
    }

    int contentW = right - left + 1;
    int contentH = bottom - top + 1;

    // Safety check: Don't crop if content area is less than 80% of original
    if (contentW < src.cols * 0.80f || contentH < src.rows * 0.80f || contentW <= 0 || contentH <= 0) {
        return src;
    }

    cv::Rect roi(left, top, contentW, contentH);
    return src(roi).clone();
}

CurvatureHint detectCurvatureHint(const cv::Mat& warped, const std::vector<cv::Point2f>& corners) {
    if (corners.size() != 4) return CurvatureHint::FLAT;

    // Signal 1: Corner orthogonality test
    float a0 = std::abs(corners[0].x - corners[3].x);
    float a1 = std::abs(corners[1].y - corners[0].y);
    int badAngles = 0;
    if (a0 > 30.0f) badAngles++;
    if (a1 > 30.0f) badAngles++;

    if (badAngles >= 2) return CurvatureHint::LIKELY_CURVED;
    if (badAngles == 1) return CurvatureHint::MAYBE_CURVED;
    return CurvatureHint::FLAT;
}

cv::Mat warpAndDeskewPerspective(const cv::Mat& src, const std::vector<cv::Point2f>& corners,
                                int dstWidth, int dstHeight, DocType* outType,
                                CurvatureHint* outCurvature) {
    std::vector<cv::Point2f> srcPts = orderQuadCorners(corners);
    DocumentTypeResult docResult = inferDocumentTypeAndSize(srcPts);
    if (outType) *outType = docResult.type;

    int targetW = (dstWidth > 0) ? dstWidth : docResult.targetWidth;
    int targetH = (dstHeight > 0) ? dstHeight : docResult.targetHeight;

    // 1. Add 20px padding to avoid edge blur interpolation artifacts
    int pad = 20;
    cv::Mat padded;
    cv::copyMakeBorder(src, padded, pad, pad, pad, pad, cv::BORDER_CONSTANT, cv::Scalar(255, 255, 255));

    std::vector<cv::Point2f> paddedCorners = srcPts;
    for (auto& pt : paddedCorners) {
        pt.x += pad;
        pt.y += pad;
    }

    std::vector<cv::Point2f> dstPts = {
        {0.0f, 0.0f},
        {static_cast<float>(targetW - 1), 0.0f},
        {static_cast<float>(targetW - 1), static_cast<float>(targetH - 1)},
        {0.0f, static_cast<float>(targetH - 1)}
    };

    // 2. Homography calculation & validation
    cv::Mat M = cv::getPerspectiveTransform(paddedCorners, dstPts);
    double det = cv::determinant(M(cv::Rect(0, 0, 2, 2)));

    cv::Mat warped;
    if (std::abs(det) < 0.05 || std::abs(det) > 20.0 || !M.isContinuous()) {
        // Homography extreme fallback: direct resize crop
        cv::resize(src, warped, cv::Size(targetW, targetH), 0, 0, cv::INTER_LINEAR);
    } else {
        cv::warpPerspective(padded, warped, M, cv::Size(targetW, targetH), cv::INTER_LINEAR, cv::BORDER_CONSTANT, cv::Scalar(255, 255, 255));
    }

    // 3. Auto-Orientation check
    cv::Mat oriented = autoFixOrientation(warped, docResult.type);

    // 4. Conditional text deskew
    double skewAngle = 0.0;
    cv::Mat deskewed = deskewImage(oriented, skewAngle, docResult.type);

    // 5. Smart content margin trimming
    cv::Mat trimmed = trimContentMargins(deskewed);

    // 6. Curvature detection hint
    if (outCurvature) {
        *outCurvature = detectCurvatureHint(trimmed, srcPts);
    }

    return trimmed;
}

} // namespace yatagami
