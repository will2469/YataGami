#ifndef YATAGAMI_SCENE_ANALYSIS_H
#define YATAGAMI_SCENE_ANALYSIS_H

#include "enhancement.h"

namespace yatagami {

bool isNativeThermalThrottling();

double calculateBlurScore(const cv::Mat& bgr);

double calculateGlareRatio(const cv::Mat& bgr);

SceneAnalysis analyzeScene(const cv::Mat& bgr);

EnhancementTier autoSelectTier(const SceneAnalysis& s);

int recommendFilterMode(const cv::Mat& bgr);

} // namespace yatagami

#endif // YATAGAMI_SCENE_ANALYSIS_H
