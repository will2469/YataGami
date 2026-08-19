#include "arena.h"
#include <cstdlib>
#include <cstring>
#include <stdexcept>
#include "common.h"

namespace yatagami {

MemoryArena::MemoryArena(size_t capacity)
    : capacity_(capacity), offset_(0) {
    // Allocate 64-byte aligned contiguous block
    if (posix_memalign((void**)&buffer_, 64, capacity_) != 0) {
        buffer_ = (uint8_t*)malloc(capacity_);
    }
}

MemoryArena::~MemoryArena() {
    if (buffer_) {
        free(buffer_);
        buffer_ = nullptr;
    }
}

void* MemoryArena::allocate(size_t bytes, size_t alignment) {
    size_t currentAddr = reinterpret_cast<size_t>(buffer_ + offset_);
    size_t alignedAddr = (currentAddr + alignment - 1) & ~(alignment - 1);
    size_t newOffset = (alignedAddr - reinterpret_cast<size_t>(buffer_)) + bytes;

    if (newOffset > capacity_) {
        LOGE("MemoryArena overflow: requested %zu bytes, capacity %zu bytes", newOffset, capacity_);
        return nullptr;
    }

    offset_ = newOffset;
    return reinterpret_cast<void*>(alignedAddr);
}

cv::Mat MemoryArena::createMat(int rows, int cols, int type) {
    size_t elemSize = CV_ELEM_SIZE(type);
    size_t totalBytes = rows * cols * elemSize;
    void* ptr = allocate(totalBytes, 64);
    if (!ptr) {
        // Fallback to normal create if arena capacity is exceeded
        cv::Mat fallback;
        fallback.create(rows, cols, type);
        return fallback;
    }
    // Return cv::Mat wrapping pre-allocated contiguous arena memory
    return cv::Mat(rows, cols, type, ptr);
}

void MemoryArena::reset() {
    offset_ = 0;
}

MemoryArena& MemoryArena::getThreadLocalArena() {
    static thread_local MemoryArena tlArena(48 * 1024 * 1024); // 48MB per processing thread
    return tlArena;
}

} // namespace yatagami
