#ifndef YATAGAMI_DETECTOR_JNI_H
#define YATAGAMI_DETECTOR_JNI_H

#include <jni.h>

namespace yatagami {

bool registerDetectorNatives(JNIEnv* env);

} // namespace yatagami

#endif // YATAGAMI_DETECTOR_JNI_H
