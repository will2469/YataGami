#pragma once

#include <opencv2/opencv.hpp>
#include <cstddef>
#include <cstdint>
#include <vector>
#include <memory>

namespace yatagami {

class MemoryArena {
public:
    explicit MemoryArena(size_t capacity = 32 * 1024 * 1024); // 32MB default
    ~MemoryArena();

    // Allocate memory with cache-line alignment (64 bytes for Cortex-A76)
    void* allocate(size_t bytes, size_t alignment = 64);

    // Carve a cv::Mat from the arena without invoking malloc
    cv::Mat createMat(int rows, int cols, int type);

    // Reset offset to 0, instantly freeing all carved allocations in O(1)
    void reset();

    // Current utilized bytes in arena
    size_t getUsedBytes() const { return offset_; }
    size_t getCapacity() const { return capacity_; }

    // Thread-local arena instance for pipeline execution
    static MemoryArena& getThreadLocalArena();

    MemoryArena(const MemoryArena&) = delete;
    MemoryArena& operator=(const MemoryArena&) = delete;

private:
    uint8_t* buffer_;
    size_t capacity_;
    size_t offset_;
};

// RAII Helper to automatically reset arena on scope exit
class ScopedArenaReset {
public:
    explicit ScopedArenaReset(MemoryArena& arena) : arena_(arena) {}
    ~ScopedArenaReset() { arena_.reset(); }

private:
    MemoryArena& arena_;
};

} // namespace yatagami
