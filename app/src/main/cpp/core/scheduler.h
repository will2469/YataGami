#pragma once

namespace yatagami {

// Initialize OpenCV thread pool limit (2 threads for 2x Cortex-A75 big cores)
void initThreadEnvironment();

// Pin calling thread to big cores (Cortex-A75) and elevate thread priority (-10)
void pinThreadToBigCores();

// Reset/restore calling thread affinity
void resetThreadAffinity();

} // namespace yatagami
