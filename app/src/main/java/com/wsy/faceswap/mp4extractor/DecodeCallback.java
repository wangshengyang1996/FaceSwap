package com.wsy.faceswap.mp4extractor;

import android.media.Image;

/**
 * 视频解码回调
 */
public interface DecodeCallback {
    /**
     * 开始解码
     *
     * @param width     视频宽度
     * @param height    视频高度
     * @param frameRate 视频帧率
     */
    void onDecodeStart(int width, int height, int frameRate);

    /**
     * 视频帧解码回调，在Android 5.0以下使用
     *
     * @param data   视频帧裸数据，格式由{@link Mp4Decoder#decodeColorFormat}指定
     * @param width  宽度
     * @param height 高度
     * @param time   微秒时间戳
     */
    void onFrameAvailable(byte[] data, int width, int height, long time);

    /**
     * 视频帧解码回调，在Android 5.0及以上使用，建议使用该项，因为一般帧数据会有做字节对齐操作，width不一定为stride
     *
     * @param image 视频帧图像数据，其中包含宽高、步长、裸数据
     * @param time  微秒时间戳
     */
    void onFrameAvailable(Image image, long time);

    /**
     * 解码结束
     */
    void onDecodeFinished();
}
