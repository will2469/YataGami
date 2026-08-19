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
