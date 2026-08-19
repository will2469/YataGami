#pragma once

#include <opencv2/opencv.hpp>
#include <vector>

namespace yatagami {

enum class DocType {
    A4 = 0,
    KTP = 1,
    F4 = 2,
    RECEIPT = 3,
    SQUARE = 4,
    FREEFORM = 5
};

enum class CurvatureHint {
    FLAT = 0,
    MAYBE_CURVED = 1,
    LIKELY_CURVED = 2
};

struct DocumentTypeResult {
    DocType type;
    bool isPortrait;
    float confidence;
    int targetWidth;
    int targetHeight;
};

// Full warp and deskew perspective with 20px padding, BORDER_CONSTANT white, homography validation, and smart trimming
cv::Mat warpAndDeskewPerspective(const cv::Mat& src, const std::vector<cv::Point2f>& corners,
                                int dstWidth = 0, int dstHeight = 0, DocType* outType = nullptr,
                                CurvatureHint* outCurvature = nullptr);

} // namespace yatagami
