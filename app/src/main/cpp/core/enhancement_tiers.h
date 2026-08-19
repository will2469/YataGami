#ifndef YATAGAMI_ENHANCEMENT_TIERS_H
#define YATAGAMI_ENHANCEMENT_TIERS_H

#include "enhancement.h"

namespace yatagami {

void applyClaheToL(cv::Mat& labL, double clipLimit, cv::Size tileSize);

void fastEdgePreservingFilter(const cv::Mat& src, cv::Mat& dst, int radius, float threshold);

cv::Mat estimateIllumination(const cv::Mat& grayOrL, int blurRadius);

void flattenIllumination(const cv::Mat& src, cv::Mat& dst, const cv::Mat& illum, float strength);

void enhanceTier1Fast(const cv::Mat& src, cv::Mat& dst);

void enhanceTier2Standard(const cv::Mat& src, cv::Mat& dst);

void enhanceTier3Quality(const cv::Mat& src, cv::Mat& dst);

} // namespace yatagami

#endif // YATAGAMI_ENHANCEMENT_TIERS_H
