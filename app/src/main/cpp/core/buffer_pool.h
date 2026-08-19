#pragma once

#include <opencv2/opencv.hpp>
#include <mutex>
#include <vector>
#include <unordered_map>

namespace yatagami {

class BufferPool {
public:
    static BufferPool& getInstance();

    // Acquire a Mat buffer with given size and type
    cv::Mat acquire(int rows, int cols, int type);

    // Release buffer back to pool
    void release(cv::Mat& mat);

    // Warm-up and preallocate common Mats at startup
    void preallocate();

    // Clear pool (e.g., on low memory)
    void clear();

private:
    BufferPool() = default;
    ~BufferPool() { clear(); }

    struct PoolKey {
        int rows;
        int cols;
        int type;

        bool operator==(const PoolKey& other) const {
            return rows == other.rows && cols == other.cols && type == other.type;
        }
    };

    struct KeyHash {
        std::size_t operator()(const PoolKey& k) const {
            return (std::hash<int>()(k.rows) ^ (std::hash<int>()(k.cols) << 1)) ^ (std::hash<int>()(k.type) << 2);
        }
    };

    std::mutex mutex_;
    std::unordered_map<PoolKey, std::vector<cv::Mat>, KeyHash> pool_;
    static constexpr size_t MAX_PER_KEY = 16;
};

// RAII Scoped Pooled Mat
class ScopedMat {
public:
    ScopedMat(int rows, int cols, int type) {
        mat_ = BufferPool::getInstance().acquire(rows, cols, type);
    }

    ~ScopedMat() {
        BufferPool::getInstance().release(mat_);
    }

    cv::Mat& get() { return mat_; }
    const cv::Mat& get() const { return mat_; }
    cv::Mat* operator->() { return &mat_; }
    cv::Mat& operator*() { return mat_; }

    ScopedMat(const ScopedMat&) = delete;
    ScopedMat& operator=(const ScopedMat&) = delete;

private:
    cv::Mat mat_;
};

} // namespace yatagami
