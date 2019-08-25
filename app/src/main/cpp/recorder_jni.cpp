#include <jni.h>
#include <string>
#include <android/log.h>
#include "Recorder.h"

#define  LOG_TAG "wsy"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

extern "C" {
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libavformat/avio.h"


JNIEXPORT jlong JNICALL
Java_com_wsy_faceswap_ffmpeg_RecordUtil_nativeStartRecord(
        JNIEnv *env,
        jobject /* this */,
        jstring mp4Path,
        jint width, jint height, jint jFps) {
    const  char* path = env->GetStringUTFChars(mp4Path, JNI_FALSE);
    Recorder* recorder = new Recorder();
    recorder->startRecord(path,width,height,jFps);
    env->ReleaseStringUTFChars(mp4Path,path);
    return reinterpret_cast<jlong>(recorder);
}
JNIEXPORT jint JNICALL
Java_com_wsy_faceswap_ffmpeg_RecordUtil_stopRecord(
        JNIEnv *env,
        jobject /* this */,jlong handle) {
    Recorder* recorder = reinterpret_cast<Recorder *>(handle);
    return recorder->stopRecord();
}

JNIEXPORT jint JNICALL
Java_com_wsy_faceswap_ffmpeg_RecordUtil_pushFrame(JNIEnv *env, jobject instance,jlong handle,
                                         jbyteArray yv12_,
                                         jint width, jint height) {
    jbyte *yv12 = env->GetByteArrayElements(yv12_, JNI_FALSE);
    Recorder* recorder = reinterpret_cast<Recorder *>(handle);
    recorder->pushFrame(reinterpret_cast<char *>(yv12), width, height);
    env->ReleaseByteArrayElements(yv12_, yv12, JNI_FALSE);
    return 0;
}
}