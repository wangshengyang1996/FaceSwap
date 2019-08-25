package com.wsy.faceswap.ffmpeg;

public class RecordUtil {
    static {
        System.loadLibrary("recorder_jni");
    }

    private long handle = 0;

    private native long nativeStartRecord(String path, int width, int height, int fps);
    public native int pushFrame(long handle, byte[] yv12, int width, int height);
    public native int stopRecord(long handle);

    public boolean startRecord(String path, int width, int height, int fps) {
        handle = nativeStartRecord(path, width, height, fps);
        return handle > 0;
    }


    public int pushFrame(byte[] yv12, int width, int height) {
        return pushFrame(handle, yv12, width, height);
    }


    public int stopRecord() {
        return stopRecord(handle);
    }

}
