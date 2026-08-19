#ifndef YATAGAMI_TEXT_DESKEW_H
#define YATAGAMI_TEXT_DESKEW_H

#include "deskew.h"

namespace yatagami {

double detectTextSkewAngle(const cv::Mat& bgr, DocType type = DocType::A4);

cv::Mat deskewImage(const cv::Mat& src, double& outAngle, DocType type = DocType::A4);

cv::Mat autoFixOrientation(const cv::Mat& bgr, DocType type = DocType::A4);

cv::Mat trimContentMargins(const cv::Mat& src);

} // namespace yatagami

#endif // YATAGAMI_TEXT_DESKEW_H
