#ifndef YATAGAMI_DOC_CLASSIFIER_H
#define YATAGAMI_DOC_CLASSIFIER_H

#include "deskew.h"
#include <vector>

namespace yatagami {

DocumentTypeResult inferDocumentTypeAndSize(const std::vector<cv::Point2f>& corners);

CurvatureHint detectCurvatureHint(const cv::Mat& warped, const std::vector<cv::Point2f>& corners);

} // namespace yatagami

#endif // YATAGAMI_DOC_CLASSIFIER_H
