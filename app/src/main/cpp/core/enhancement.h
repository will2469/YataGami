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

// Scene analysis & Auto-tier selection
SceneAnalysis analyzeScene(const cv::Mat& bgr);
EnhancementTier autoSelectTier(const SceneAnalysis& scene);

// Native Thermal check
bool isNativeThermalThrottling();

// Metrics
double calculateBlurScore(const cv::Mat& bgr);
double calculateGlareRatio(const cv::Mat& bgr);
int recommendFilterMode(const cv::Mat& bgr);

// CLAHE in LAB L-channel helper
void applyClaheToL(cv::Mat& labL, double clipLimit = 2.0, cv::Size tileSize = cv::Size(8, 8));

// Fast edge-preserving surface blur (15-20ms)
void fastEdgePreservingFilter(const cv::Mat& src, cv::Mat& dst, int radius = 5, float threshold = 30.0f);

// Illumination estimation & flattening
cv::Mat estimateIllumination(const cv::Mat& grayOrL, int blurRadius = 101);
void flattenIllumination(const cv::Mat& src, cv::Mat& dst, const cv::Mat& illum, float strength = 0.8f);

// Master Tier Enhancement Engines
void enhanceTier1Fast(const cv::Mat& src, cv::Mat& dst);
void enhanceTier2Standard(const cv::Mat& src, cv::Mat& dst);
void enhanceTier3Quality(const cv::Mat& src, cv::Mat& dst);

// Master Enhancement Engine (generates Master RGB)
void generateMasterEnhanced(const cv::Mat& src, cv::Mat& dst, EnhancementTier tier = EnhancementTier::TIER_AUTO);

// Fast O(1) post-process filter mode application on Master
void applyFilterMode(const cv::Mat& master, cv::Mat& dst, int mode);

// Combined entry point with double-buffering & direct write
void enhanceImage(const cv::Mat& bgr, cv::Mat& dst, int mode, EnhancementTier tier = EnhancementTier::TIER_AUTO);
cv::Mat enhanceImage(const cv::Mat& bgr, int mode, EnhancementTier tier = EnhancementTier::TIER_AUTO);

} // namespace yatagami
