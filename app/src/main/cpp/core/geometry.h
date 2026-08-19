#pragma once

#include <opencv2/opencv.hpp>
#include <vector>

namespace yatagami {

// Order 4 quadrilateral corners strictly into [Top-Left, Top-Right, Bottom-Right, Bottom-Left]
std::vector<cv::Point2f> orderQuadCorners(const std::vector<cv::Point2f>& pts);

// Detect document 4 corners with downscaling, Canny, contour approximation, and subpixel refinement
std::vector<cv::Point2f> detectDocumentCorners(const cv::Mat& img);

} // namespace yatagami
