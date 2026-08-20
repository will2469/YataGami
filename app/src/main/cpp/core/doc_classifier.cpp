#include "doc_classifier.h"
#include "geometry.h"
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
    result.targetWidth = 2480;
    result.targetHeight = 3508;

    if (corners.size() != 4) return result;

    std::vector<cv::Point2f> q = orderQuadCorners(corners);
    float topLen = dist(q[0], q[1]);
    float bottomLen = dist(q[3], q[2]);
    float leftLen = dist(q[0], q[3]);
    float rightLen = dist(q[1], q[2]);

    float avgW = (topLen + bottomLen) * 0.5f;
    float avgH = (leftLen + rightLen) * 0.5f;
    if (avgH <= 0.0f) avgH = 1.0f;

    float ratio = avgW / avgH; // < 1.0 Portrait, > 1.0 Landscape
    result.isPortrait = (avgH >= avgW);

    int naturalW = static_cast<int>(std::max(topLen, bottomLen));
    int naturalH = static_cast<int>(std::max(leftLen, rightLen));

    if (result.isPortrait) {
        // 1. Receipt (Very Long): Ratio < 0.50
        if (ratio < 0.50f) {
            result.type = DocType::RECEIPT;
            result.targetWidth = std::max(1200, naturalW);
            float r = std::clamp(1.0f / ratio, 2.0f, 6.0f);
            result.targetHeight = static_cast<int>(result.targetWidth * r);
            result.confidence = 0.95f;
            return result;
        }

        // 2. F4 / Folio (Asia 215x330mm): Ratio <= 0.680 (300 DPI: 2540 x 3898)
        if (ratio <= 0.680f) {
            result.type = DocType::F4;
            result.targetWidth = (naturalW > 1600) ? naturalW : 2540;
            result.targetHeight = static_cast<int>(result.targetWidth * 1.535f);
            result.confidence = 0.95f;
            return result;
        }

        // 3. A4 (ISO 210x297mm): Ratio <= 0.850 (300 DPI: 2480 x 3508)
        if (ratio <= 0.850f) {
            result.type = DocType::A4;
            result.targetWidth = (naturalW > 1600) ? naturalW : 2480;
            result.targetHeight = static_cast<int>(result.targetWidth * 1.414f);
            result.confidence = 0.92f;
            return result;
        }

        // 4. Square: Ratio ~1.0
        result.type = DocType::SQUARE;
        result.targetWidth = std::max(2000, naturalW);
        result.targetHeight = result.targetWidth;
        result.confidence = 0.90f;
        return result;
    } else {
        // LANDSCAPE CLASSIFICATION:
        // 1. Landscape KTP / ID Card: 85.6mm x 53.98mm = 1.586 ratio
        if (std::abs(ratio - 1.586f) < 0.15f) {
            result.type = DocType::KTP;
            result.targetWidth = (naturalW > 1500) ? naturalW : 2022;
            result.targetHeight = static_cast<int>(result.targetWidth / 1.586f);
            result.confidence = 0.95f;
            return result;
        }

        // 2. Landscape F4
        if (ratio >= 1.47f) {
            result.type = DocType::F4;
            result.targetHeight = (naturalH > 1600) ? naturalH : 2540;
            result.targetWidth = static_cast<int>(result.targetHeight * 1.535f);
            result.confidence = 0.95f;
            return result;
        }

        // 3. Landscape A4
        result.type = DocType::A4;
        result.targetHeight = (naturalH > 1600) ? naturalH : 2480;
        result.targetWidth = static_cast<int>(result.targetHeight * 1.414f);
        result.confidence = 0.90f;
        return result;
    }
}

CurvatureHint detectCurvatureHint(const cv::Mat& warped, const std::vector<cv::Point2f>& corners) {
    if (corners.size() != 4) return CurvatureHint::FLAT;

    float a0 = std::abs(corners[0].x - corners[3].x);
    float a1 = std::abs(corners[1].y - corners[0].y);
    int badAngles = 0;
    if (a0 > 30.0f) badAngles++;
    if (a1 > 30.0f) badAngles++;

    if (badAngles >= 2) return CurvatureHint::LIKELY_CURVED;
    if (badAngles == 1) return CurvatureHint::MAYBE_CURVED;
    return CurvatureHint::FLAT;
}

} // namespace yatagami
