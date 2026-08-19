#pragma once

#include <opencv2/opencv.hpp>
#include <vector>

namespace yatagami {

// Order 4 quadrilateral corners strictly into [Top-Left, Top-Right, Bottom-Right, Bottom-Left]
std::vector<cv::Point2f> orderQuadCorners(const std::vector<cv::Point2f>& pts);

// Calculate 6-factor confidence score for a quad (0.0 to 1.0)
float calculateQuadConfidence(const std::vector<cv::Point2f>& corners, float imgWidth, float imgHeight);

// Detect document 4 corners with hierarchical fallback, subpixel refinement, and confidence scoring
std::vector<cv::Point2f> detectDocumentCorners(const cv::Mat& img, float* outConfidence = nullptr);

} // namespace yatagami
