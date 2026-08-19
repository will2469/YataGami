#include "core/common.h"
#include "core/buffer_pool.h"
#include "core/scheduler.h"
#include "jni/detector_jni.h"
#include "jni/processor_jni.h"

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    yatagami::initThreadEnvironment();
    yatagami::BufferPool::getInstance().preallocate();

    if (!yatagami::registerDetectorNatives(env)) {
        LOGE("Failed to register DocumentDetector natives");
        return JNI_ERR;
    }

    if (!yatagami::registerProcessorNatives(env)) {
        LOGE("Failed to register ImageProcessor natives");
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
