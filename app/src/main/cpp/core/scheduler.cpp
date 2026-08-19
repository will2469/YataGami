#include "scheduler.h"
#include "common.h"
#include <sched.h>
#include <sys/resource.h>
#include <unistd.h>
#include <fstream>
#include <string>
#include <vector>
#include <algorithm>

namespace yatagami {

namespace {

std::vector<int> detectBigCoreIndices() {
    std::vector<std::pair<long, int>> freqs;
    int numCpus = sysconf(_SC_NPROCESSORS_CONF);
    if (numCpus <= 0) numCpus = 8;

    for (int i = 0; i < numCpus; ++i) {
        std::string path = "/sys/devices/system/cpu/cpu" + std::to_string(i) + "/cpufreq/cpuinfo_max_freq";
        std::ifstream file(path);
        long maxFreq = 0;
        if (file >> maxFreq) {
            freqs.push_back({maxFreq, i});
        } else {
            // Default MediaTek 8-core (6 LITTLE A55 + 2 Big A75): CPU 6 & 7 are big cores
            freqs.push_back({(i >= 6) ? 2200000L : 2000000L, i});
        }
    }

    std::sort(freqs.rbegin(), freqs.rend());
    std::vector<int> bigCores;
    for (size_t i = 0; i < freqs.size() && i < 2; ++i) {
        bigCores.push_back(freqs[i].second);
    }
    if (bigCores.empty()) {
        bigCores = {6, 7};
    }
    return bigCores;
}

} // namespace

void initThreadEnvironment() {
    // Restrict OpenCV parallel threads to 2 (matching 2x Cortex-A76 big cores on Helio G100 Ultimate)
    cv::setNumThreads(2);
    LOGI("OpenCV thread limit initialized to 2 threads for Helio G100 Ultimate (Cortex-A76)");
}

void pinThreadToBigCores() {
    // Elevate priority (nice -10 for real-time document computation)
    setpriority(PRIO_PROCESS, 0, -10);

    // Set CPU affinity to Cortex-A76 big cores
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    static const std::vector<int> bigCores = detectBigCoreIndices();
    for (int core : bigCores) {
        CPU_SET(core, &cpuset);
    }

    if (sched_setaffinity(0, sizeof(cpu_set_t), &cpuset) == 0) {
        LOGI("Thread pinned to Cortex-A76 big cores");
    }
}

void resetThreadAffinity() {
    setpriority(PRIO_PROCESS, 0, 0);
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    int numCpus = sysconf(_SC_NPROCESSORS_CONF);
    if (numCpus <= 0) numCpus = 8;
    for (int i = 0; i < numCpus; ++i) {
        CPU_SET(i, &cpuset);
    }
    sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
}

} // namespace yatagami
