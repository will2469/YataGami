#include "deskew.h"
#include "geometry.h"
#include <algorithm>
#include <cmath>

namespace yatagami {

double detectTextSkewAngle(const cv::Mat& bgr) {
    cv::Mat gray;
    if (bgr.channels() == 3) {
        cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
    } else {
        gray = bgr;
    }

    float scale = 1.0f;
    float maxDim = 800.0f;
    cv::Mat small;
    if (std::max(gray.cols, gray.rows) > maxDim) {
        scale = maxDim / static_cast<float>(std::max(gray.cols, gray.rows));
        cv::resize(gray, small, cv::Size(), scale, scale, cv::INTER_AREA);
    } else {
        small = gray;
    }

    cv::Mat bin;
    cv::threshold(small, bin, 0, 255, cv::THRESH_BINARY_INV | cv::THRESH_OTSU);

    cv::Mat morphKernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(25, 3));
    cv::Mat dilated;
    cv::dilate(bin, dilated, morphKernel);

    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(dilated, contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);

    std::vector<double> angles;
    float minWidth = small.cols * 0.12f;

    for (const auto& c : contours) {
        if (c.size() < 5) continue;
        cv::RotatedRect rect = cv::minAreaRect(c);

        float width = rect.size.width;
        float height = rect.size.height;
        float angle = rect.angle;

        if (width < height) {
            std::swap(width, height);
            angle += 90.0f;
        }

        while (angle > 45.0f) angle -= 90.0f;
        while (angle < -45.0f) angle += 90.0f;

        if (width >= minWidth && (width / std::max(1.0f, height)) >= 2.5f) {
            angles.push_back(static_cast<double>(angle));
        }
    }

    if (angles.empty()) {
        return 0.0;
    }

    std::sort(angles.begin(), angles.end());
    double medianAngle = angles[angles.size() / 2];

    if (std::abs(medianAngle) > 25.0) {
        return 0.0;
    }

    return medianAngle;
}

cv::Mat deskewImage(const cv::Mat& src, double& outAngle) {
    outAngle = detectTextSkewAngle(src);
    if (std::abs(outAngle) < 0.5) {
        return src;
    }

    cv::Point2f center(src.cols / 2.0f, src.rows / 2.0f);
    cv::Mat rotMat = cv::getRotationMatrix2D(center, outAngle, 1.0);
    cv::Mat deskewed;
    cv::warpAffine(src, deskewed, rotMat, src.size(), cv::INTER_CUBIC, cv::BORDER_REPLICATE);
    return deskewed;
}

cv::Mat autoFixOrientation(const cv::Mat& bgr) {
    cv::Mat gray;
    if (bgr.channels() == 3) {
        cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
    } else {
        gray = bgr;
    }

    cv::Mat gradX, gradY;
    cv::Sobel(gray, gradX, CV_32F, 1, 0, 3);
    cv::Sobel(gray, gradY, CV_32F, 0, 1, 3);

    cv::Scalar meanGradX = cv::mean(cv::abs(gradX));
    cv::Scalar meanGradY = cv::mean(cv::abs(gradY));

    cv::Mat result = bgr;
    if (meanGradX[0] > 1.5 * meanGradY[0] && bgr.rows > bgr.cols) {
        cv::rotate(bgr, result, cv::ROTATE_90_CLOCKWISE);
    }

    return result;
}

cv::Mat warpAndDeskewPerspective(const cv::Mat& src, const std::vector<cv::Point2f>& corners, int dstWidth, int dstHeight) {
    std::vector<cv::Point2f> srcPts = orderQuadCorners(corners);

    std::vector<cv::Point2f> dstPts = {
        {0.0f, 0.0f},
        {static_cast<float>(dstWidth - 1), 0.0f},
        {static_cast<float>(dstWidth - 1), static_cast<float>(dstHeight - 1)},
        {0.0f, static_cast<float>(dstHeight - 1)}
    };

    cv::Mat M = cv::getPerspectiveTransform(srcPts, dstPts);
    cv::Mat warped;
    cv::warpPerspective(src, warped, M, cv::Size(dstWidth, dstHeight), cv::INTER_CUBIC, cv::BORDER_REPLICATE);

    cv::Mat oriented = autoFixOrientation(warped);

    double skewAngle = 0.0;
    cv::Mat deskewed = deskewImage(oriented, skewAngle);
    return deskewed;
}

} // namespace yatagami
