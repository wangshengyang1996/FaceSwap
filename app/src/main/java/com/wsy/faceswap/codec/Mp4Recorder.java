package com.wsy.faceswap.codec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 使用MediaCodec硬编码
 */
public class Mp4Recorder {

    private static final String TAG = "Mp4Recorder";
    private final static int TIMEOUT_USEC = 10000;
    private int frameRate = 15;

    private int videoWidth;
    private int videoHeight;
    private MediaCodec videoMediaCodec;
    private MediaMuxer mediaMuxer;
    private int videoTrack = -1;
    boolean recording = false;
    long lastPresentationTime = 0;

    public boolean isRecording() {
        return recording;
    }

    public Mp4Recorder(int videoWidth, int videoHeight, int frameRate, File file) {
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.frameRate = frameRate;
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            throw new RuntimeException("file can not be created");
        }
        try {
            mediaMuxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            throw new RuntimeException("file not exists");
        }
    }

    public void startRecord() {
        // 根据格式和宽高创建MediaFormat
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight);
        // 设置颜色格式为YUV420SP，这里指的是NV12
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        // 比特率设置，越大，视频质量越高
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 3000000);
        // 帧率
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        // 关键帧间隔，单位是秒
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        try {
            // 根据格式创建encoder
            videoMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            throw new RuntimeException("createEncoderByType failed: " + e.getMessage());
        }
        // 配置encoder并开始，若失败会报运行时异常
        videoMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        videoMediaCodec.start();

        recording = true;
    }

    /**
     * 推流，用于编码
     * @param nv12 NV12格式的原数据
     * @param time 时间戳，微秒
     */
    public void pushFrame(byte[] nv12, long time) {
        if (recording) {
            encodeVideo(nv12, time);
        }
    }

    /**
     * 编码帧数据
     * @param nv12 NV12格式的原数据
     * @param time 时间戳，微秒
     */
    private void encodeVideo(byte[] nv12, long time) {
        // 获取编码器的输入流缓存数据下标
        int inputIndex = videoMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = null;
            // 兼容地获取输入流缓存数据
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                inputBuffer = videoMediaCodec.getInputBuffer(inputIndex);
            } else {
                inputBuffer = videoMediaCodec.getInputBuffers()[inputIndex];
            }
            inputBuffer.clear();
            // 把要编码的数据添加进去
            inputBuffer.put(nv12);
            // 入队列，等待编码
            if (time == -1) {
                videoMediaCodec.queueInputBuffer(inputIndex, 0, nv12.length, lastPresentationTime += (1000 * 1000 / frameRate), 0);
            } else {
                videoMediaCodec.queueInputBuffer(inputIndex, 0, nv12.length, time, 0);
                lastPresentationTime = time;
            }
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        //读取MediaCodec编码后的数据
        int outputIndex;
        while ((outputIndex = videoMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)) >= 0) {
            ByteBuffer outputBuffer = null;
            // 兼容地获取输出流，此时是编码后的数据
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                outputBuffer = videoMediaCodec.getOutputBuffer(outputIndex);
            } else {
                outputBuffer = videoMediaCodec.getOutputBuffers()[outputIndex];
            }
            // 在拿到CSD(Codec Specific Data)时为MediaMuxer添加视频轨道
            if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                bufferInfo.size = 0;
                if (videoTrack < 0) {
                    videoTrack = mediaMuxer.addTrack(videoMediaCodec.getOutputFormat());
                    mediaMuxer.start();
                }
            }
            if (bufferInfo.size != 0) {
                outputBuffer.position(bufferInfo.offset);
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                mediaMuxer.writeSampleData(videoTrack, outputBuffer, bufferInfo);
            }

            //数据写入本地成功 通知MediaCodec释放data
            videoMediaCodec.releaseOutputBuffer(outputIndex, false);

        }
    }

    public void stopRecord() {
        recording = false;
        try {
            mediaMuxer.stop();
            mediaMuxer.release();
            videoMediaCodec.stop();
            videoMediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
