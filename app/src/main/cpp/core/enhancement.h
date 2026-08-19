#pragma once

#include <opencv2/opencv.hpp>
#include <vector>

namespace yatagami {

enum class EnhancementTier {
    TIER_AUTO = 0,
    TIER_1_FAST = 1,
    TIER_2_STANDARD = 2,
    TIER_3_QUALITY = 3
};

struct SceneAnalysis {
    float meanL;           // 0-255 brightness
    float stdDevL;         // contrast
    float darkRatio;       // % pixels with L < 50 (shadow)
    float brightRatio;     // % pixels with L > 230
    float glareRatio;      // % pixels with L > 245
    float blurScore;       // Laplacian variance
    float noiseEstimate;   // RMS noise
    float colorCastMag;    // Distance from neutral (128,128) in AB
    bool hasShadow;
    bool hasGlare;
};

// Master Enhancement Engine (generates Master RGB)
void generateMasterEnhanced(const cv::Mat& src, cv::Mat& dst, EnhancementTier tier = EnhancementTier::TIER_AUTO);

// Fast O(1) post-process filter mode application on Master
void applyFilterMode(const cv::Mat& master, cv::Mat& dst, int mode);

// Combined entry point with double-buffering & direct write
void enhanceImage(const cv::Mat& bgr, cv::Mat& dst, int mode, EnhancementTier tier = EnhancementTier::TIER_AUTO);
cv::Mat enhanceImage(const cv::Mat& bgr, int mode, EnhancementTier tier = EnhancementTier::TIER_AUTO);

} // namespace yatagami
