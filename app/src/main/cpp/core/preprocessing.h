#pragma once

#include <opencv2/opencv.hpp>

namespace yatagami {

// 1. Dynamic Gamma Correction — Normalizes under/over-exposed lighting
void applyDynamicGamma(const cv::Mat& gray, cv::Mat& dst);
cv::Mat applyDynamicGamma(const cv::Mat& gray);

// 2. Shadow Removal & Illumination Flattening
void removeShadowsGray(const cv::Mat& gray, cv::Mat& dst);
cv::Mat removeShadowsGray(const cv::Mat& gray);

// 3. Auto-Contrast / CLAHE Preprocessing
void applyCLAHEGray(const cv::Mat& gray, cv::Mat& dst);
cv::Mat applyCLAHEGray(const cv::Mat& gray);

// 4. Edge-Preserving Noise Reduction (Bilateral Filter)
void applyNoiseReduction(const cv::Mat& gray, cv::Mat& dst);
cv::Mat applyNoiseReduction(const cv::Mat& gray);

// Complete Preprocessing Pipeline for Document Detection
void preprocessForDetection(const cv::Mat& bgr, cv::Mat& dst);
cv::Mat preprocessForDetection(const cv::Mat& bgr);

} // namespace yatagami
