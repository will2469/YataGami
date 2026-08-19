#pragma once

#include <opencv2/opencv.hpp>

namespace yatagami {

// 8. Blur Detection using Laplacian Variance
double calculateBlurScore(const cv::Mat& bgr);

// 9. Glare / Overexposure Ratio Detection & Suppression
double calculateGlareRatio(const cv::Mat& bgr);
void suppressGlare(const cv::Mat& bgr, cv::Mat& dst);
cv::Mat suppressGlare(const cv::Mat& bgr);

// 10. Smart Filter Recommendation Engine
int recommendFilterMode(const cv::Mat& bgr);

// Image Enhancement Filters (with pre-allocated output support)
void enhanceImage(const cv::Mat& bgr, cv::Mat& dst, int mode);
cv::Mat enhanceImage(const cv::Mat& bgr, int mode);

} // namespace yatagami
