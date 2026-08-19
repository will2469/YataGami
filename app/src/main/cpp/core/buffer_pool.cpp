#include "buffer_pool.h"

namespace yatagami {

BufferPool& BufferPool::getInstance() {
    static BufferPool instance;
    return instance;
}

cv::Mat BufferPool::acquire(int rows, int cols, int type) {
    std::lock_guard<std::mutex> lock(mutex_);
    PoolKey key{rows, cols, type};
    auto it = pool_.find(key);
    if (it != pool_.end() && !it->second.empty()) {
        cv::Mat mat = std::move(it->second.back());
        it->second.pop_back();
        return mat;
    }

    cv::Mat mat;
    mat.create(rows, cols, type);
    return mat;
}

void BufferPool::release(cv::Mat& mat) {
    if (mat.empty()) return;

    std::lock_guard<std::mutex> lock(mutex_);
    PoolKey key{mat.rows, mat.cols, mat.type()};
    auto& vec = pool_[key];
    if (vec.size() < MAX_PER_KEY) {
        vec.push_back(std::move(mat));
    } else {
        mat.release();
    }
}

void BufferPool::preallocate() {
    std::lock_guard<std::mutex> lock(mutex_);
    // Pre-warm preview detection Mats (640x480 gray & BGR)
    for (int i = 0; i < 4; ++i) {
        cv::Mat mGray(480, 640, CV_8UC1);
        pool_[{480, 640, CV_8UC1}].push_back(std::move(mGray));

        cv::Mat mBgr(480, 640, CV_8UC3);
        pool_[{480, 640, CV_8UC3}].push_back(std::move(mBgr));
    }

    // Pre-warm capture & warp Mats (A4 300DPI 3508x2480 gray & BGR)
    for (int i = 0; i < 2; ++i) {
        cv::Mat mDocBgr(3508, 2480, CV_8UC3);
        pool_[{3508, 2480, CV_8UC3}].push_back(std::move(mDocBgr));

        cv::Mat mDocGray(3508, 2480, CV_8UC1);
        pool_[{3508, 2480, CV_8UC1}].push_back(std::move(mDocGray));
    }
}

void BufferPool::clear() {
    std::lock_guard<std::mutex> lock(mutex_);
    for (auto& pair : pool_) {
        for (auto& mat : pair.second) {
            mat.release();
        }
        pair.second.clear();
    }
    pool_.clear();
}

} // namespace yatagami
