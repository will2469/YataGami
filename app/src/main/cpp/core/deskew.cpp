#include "deskew.h"
#include "geometry.h"
#include "doc_classifier.h"
#include "text_deskew.h"
#include "common.h"
#include <algorithm>
#include <cmath>

namespace yatagami {

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
