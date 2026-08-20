#include "text_deskew.h"
#include <algorithm>
#include <cmath>

namespace yatagami {

double detectTextSkewAngle(const cv::Mat& bgr, DocType type) {
    if (type == DocType::KTP || type == DocType::SQUARE) {
        return 0.0;
    }

    cv::Mat gray;
    if (bgr.channels() == 3) {
        cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
    } else if (bgr.channels() == 4) {
        cv::cvtColor(bgr, gray, cv::COLOR_RGBA2GRAY);
    } else {
        gray = bgr;
    }

    cv::Mat smallImg;
    double scale = 600.0 / std::max(gray.cols, gray.rows);
    if (scale < 1.0) {
        cv::resize(gray, smallImg, cv::Size(), scale, scale, cv::INTER_AREA);
    } else {
        smallImg = gray;
    }

    cv::Mat binary;
    cv::adaptiveThreshold(smallImg, binary, 255, cv::ADAPTIVE_THRESH_GAUSSIAN_C, cv::THRESH_BINARY_INV, 15, 10);

    int textPixels = cv::countNonZero(binary);
    float textRatio = static_cast<float>(textPixels) / static_cast<float>(binary.total());
    if (textRatio < 0.02f || textRatio > 0.40f) {
        return 0.0;
    }

    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(25, 1));
    cv::Mat textLines;
    cv::morphologyEx(binary, textLines, cv::MORPH_CLOSE, kernel);

    double bestScore = -1.0;
    double bestAngle = 0.0;
    cv::Point2f center(smallImg.cols / 2.0f, smallImg.rows / 2.0f);

    for (double angle = -5.0; angle <= 5.0; angle += 0.5) {
        cv::Mat rotMat = cv::getRotationMatrix2D(center, angle, 1.0);
        cv::Mat rotated;
        cv::warpAffine(textLines, rotated, rotMat, textLines.size(), cv::INTER_NEAREST, cv::BORDER_CONSTANT, cv::Scalar(0));

        cv::Mat proj;
        cv::reduce(rotated, proj, 1, cv::REDUCE_SUM, CV_32S);

        cv::Scalar mean, stddev;
        cv::meanStdDev(proj, mean, stddev);
        double variance = stddev[0] * stddev[0];

        if (variance > bestScore) {
            bestScore = variance;
            bestAngle = angle;
        }
    }

    return bestAngle;
}

cv::Mat deskewImage(const cv::Mat& src, double& outAngle, DocType type) {
    outAngle = detectTextSkewAngle(src, type);
    if (std::abs(outAngle) < 0.3 || std::abs(outAngle) > 5.0) {
        return src;
    }

    cv::Point2f center(src.cols / 2.0f, src.rows / 2.0f);
    cv::Mat rotMat = cv::getRotationMatrix2D(center, outAngle, 1.0);

    cv::Rect2f bbox = cv::RotatedRect(cv::Point2f(), src.size(), static_cast<float>(outAngle)).boundingRect2f();
    rotMat.at<double>(0, 2) += bbox.width / 2.0 - src.cols / 2.0;
    rotMat.at<double>(1, 2) += bbox.height / 2.0 - src.rows / 2.0;

    cv::Mat dst;
    cv::warpAffine(src, dst, rotMat, bbox.size(), cv::INTER_LINEAR, cv::BORDER_CONSTANT, cv::Scalar(255, 255, 255));
    return dst;
}

cv::Mat autoFixOrientation(const cv::Mat& bgr, DocType type) {
    return bgr;
}

cv::Mat trimContentMargins(const cv::Mat& src) {
    return src;
}

} // namespace yatagami
