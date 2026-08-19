#pragma once

#include <opencv2/opencv.hpp>

namespace yatagami {

// 1. Dynamic Gamma Correction — Normalizes under/over-exposed lighting
cv::Mat applyDynamicGamma(const cv::Mat& gray);

// 2. Shadow Removal & Illumination Flattening
cv::Mat removeShadowsGray(const cv::Mat& gray);

// 3. Auto-Contrast / CLAHE Preprocessing
cv::Mat applyCLAHEGray(const cv::Mat& gray);

// 4. Edge-Preserving Noise Reduction (Bilateral Filter)
cv::Mat applyNoiseReduction(const cv::Mat& gray);

// Complete Preprocessing Pipeline for Document Detection
cv::Mat preprocessForDetection(const cv::Mat& bgr);

} // namespace yatagami
