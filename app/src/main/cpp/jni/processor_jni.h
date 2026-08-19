#ifndef YATAGAMI_PROCESSOR_JNI_H
#define YATAGAMI_PROCESSOR_JNI_H

#include <jni.h>

namespace yatagami {

bool registerProcessorNatives(JNIEnv* env);

} // namespace yatagami

#endif // YATAGAMI_PROCESSOR_JNI_H
