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

    float ratio = avgW / avgH; // > 1.0 Landscape, < 1.0 Portrait
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
