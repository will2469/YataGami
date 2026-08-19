#include "enhancement.h"
#include "scene_analysis.h"
#include "enhancement_tiers.h"
#include "buffer_pool.h"
#include "common.h"
#include <algorithm>
#include <vector>

namespace yatagami {

void generateMasterEnhanced(const cv::Mat& src, cv::Mat& dst, EnhancementTier tier) {
    EnhancementTier selected = tier;
    if (selected == EnhancementTier::TIER_AUTO) {
        SceneAnalysis s = analyzeScene(src);
        selected = autoSelectTier(s);
    }

    switch (selected) {
        case EnhancementTier::TIER_1_FAST:
            enhanceTier1Fast(src, dst);
            break;
        case EnhancementTier::TIER_3_QUALITY:
            enhanceTier3Quality(src, dst);
            break;
        case EnhancementTier::TIER_2_STANDARD:
        default:
            enhanceTier2Standard(src, dst);
            break;
    }
}

void applyFilterMode(const cv::Mat& master, cv::Mat& dst, int mode) {
    switch (mode) {
        case 1: { // Grayscale
            ScopedMat gray(master.rows, master.cols, CV_8UC1);
            cv::cvtColor(master, *gray, cv::COLOR_BGR2GRAY);
            cv::cvtColor(*gray, dst, cv::COLOR_GRAY2BGR);
            break;
        }
        case 2: { // High Contrast Black & White
            ScopedMat gray(master.rows, master.cols, CV_8UC1);
            cv::cvtColor(master, *gray, cv::COLOR_BGR2GRAY);
            ScopedMat binary(master.rows, master.cols, CV_8UC1);
            cv::adaptiveThreshold(*gray, *binary, 255, cv::ADAPTIVE_THRESH_GAUSSIAN_C, cv::THRESH_BINARY, 15, 8);
            cv::cvtColor(*binary, dst, cv::COLOR_GRAY2BGR);
            break;
        }
        case 3: { // Magic Color (LAB A & B vibrance scale x1.18)
            ScopedMat lab(master.rows, master.cols, CV_8UC3);
            cv::cvtColor(master, *lab, cv::COLOR_BGR2Lab);
            std::vector<cv::Mat> ch(3);
            cv::split(*lab, ch);
            ch[1] = ch[1] * 1.18f;
            ch[2] = ch[2] * 1.18f;
            cv::merge(ch, *lab);
            cv::cvtColor(*lab, dst, cv::COLOR_Lab2BGR);
            break;
        }
        case 4: { // Sharpen (Unsharp mask r=1.0)
            ScopedMat blurred(master.rows, master.cols, CV_8UC3);
            cv::GaussianBlur(master, *blurred, cv::Size(0, 0), 1.0);
            cv::addWeighted(master, 2.0, *blurred, -1.0, 0, dst);
            break;
        }
        case 5: // Auto mode -> Use Master as-is
        case 0: // None
        default:
            if (&dst != &master) {
                master.copyTo(dst);
            }
            break;
    }
}

void enhanceImage(const cv::Mat& bgr, cv::Mat& dst, int mode, EnhancementTier tier) {
    ScopedMat master(bgr.rows, bgr.cols, CV_8UC3);
    generateMasterEnhanced(bgr, *master, tier);
    applyFilterMode(*master, dst, mode);
}

cv::Mat enhanceImage(const cv::Mat& bgr, int mode, EnhancementTier tier) {
    cv::Mat dst;
    enhanceImage(bgr, dst, mode, tier);
    return dst;
}

} // namespace yatagami
