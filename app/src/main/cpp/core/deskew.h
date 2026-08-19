#pragma once

#include <opencv2/opencv.hpp>
#include <vector>

namespace yatagami {

// 5. Text-Based Skew Angle Detection
double detectTextSkewAngle(const cv::Mat& bgr);

// 6. Deskew Image
cv::Mat deskewImage(const cv::Mat& src, double& outAngle);

// 7. Orientation Classification (Upright 0° / 90° fix)
cv::Mat autoFixOrientation(const cv::Mat& bgr);

// Warp perspective and auto-deskew / orientation
cv::Mat warpAndDeskewPerspective(const cv::Mat& src, const std::vector<cv::Point2f>& corners, int dstWidth, int dstHeight);

} // namespace yatagami
