package com.wsy.faceswap.mp4extractor;

import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 使用MediaCodec硬解码
 */
public class Mp4Decoder {

    private static final String TAG = "Mp4Decoder";

    private static final long DEFAULT_TIMEOUT_US = 10000;
    private static final int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;

    private MediaExtractor extractor = null;
    private MediaCodec decoder = null;
    private int width;
    private int height;

    DecodeCallback decodeCallback;

    public void setDecodeCallback(DecodeCallback decodeCallback) {
        this.decodeCallback = decodeCallback;
    }

    /**
     *  1. 创建MediaExtractor，并绑定数据
     *  2. 选择视频轨道
     *  3. 获取视频轨道的MediaFormat，并获取视频的宽高、帧率等信息
     *  4. 根据视频的mimeType创建解码器
     *  5. 为解码器设置MediaFormat，用于确认可输出的裸数据格式
     *
     * @param mp4Path 视频文件路径
     * @throws IOException 设置视频文件出错的异常
     */
    public void init(String mp4Path) throws IOException {

        extractor = new MediaExtractor();
        extractor.setDataSource(mp4Path);
        int trackIndex = selectTrack(extractor);
        if (trackIndex < 0) {
            throw new RuntimeException("decode failed for file " + mp4Path);
        }
        extractor.selectTrack(trackIndex);
        MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);

        width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
        int frameRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
        Log.i(TAG, "init: " + frameRate + " " + width + " " + height);
        decoder = MediaCodec.createDecoderByType(mime);
        showSupportedColorFormat(decoder.getCodecInfo().getCapabilitiesForType(mime));
        if (isColorFormatSupported(decodeColorFormat, decoder.getCodecInfo().getCapabilitiesForType(mime))) {
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
        } else {
            throw new IllegalArgumentException("unable to set decode color format");
        }
        decoder.configure(mediaFormat, null, null, 0);
        decoder.start();
        if (decodeCallback != null) {
            decodeCallback.onDecodeStart(width, height, frameRate);
        }

    }

    public void videoDecode() {
        decodeFramesToYUV(decoder, extractor);
    }

    public void release() {
        if (decoder != null) {
            decoder.stop();
            decoder.release();
            decoder = null;
        }
        if (extractor != null) {
            extractor.release();
            extractor = null;
        }
    }

    private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
        Log.i(TAG, "showSupportedColorFormat: ");
        for (int c : caps.colorFormats) {
            Log.i(TAG, "showSupportedColorFormat: " + c);
        }
    }

    private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
        for (int c : caps.colorFormats) {
            if (c == colorFormat) {
                return true;
            }
        }
        return false;
    }

    private void decodeFramesToYUV(MediaCodec decoder, MediaExtractor extractor) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputFinished = false;
        boolean outputFinished = false;
        while (!outputFinished) {
            if (!inputFinished) {
                int inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = null;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        inputBuffer = decoder.getInputBuffer(inputBufferId);
                    } else {
                        inputBuffer = decoder.getInputBuffers()[inputBufferId];
                    }
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputFinished = true;
                    } else {
                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();
                    }
                }
            }
            int outputBufferId = decoder.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US);
            if (outputBufferId >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputFinished = true;
                }
                if (info.size > 0) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        Image image = null;
                        image = decoder.getOutputImage(outputBufferId);
                        if (decodeCallback != null) {
                            decodeCallback.onFrameAvailable(image, extractor.getSampleTime());
                        }
                        image.close();
                    }else {
                        ByteBuffer outputBuffer = decoder.getOutputBuffers()[outputBufferId];
                        if (decodeCallback != null) {
                            byte[] data = new byte[width * height * 3 / 2];
                            outputBuffer.get(data, 0, data.length);
                            decodeCallback.onFrameAvailable(data, width, height, extractor.getSampleTime());
                        }
                    }

                    decoder.releaseOutputBuffer(outputBufferId, false);
                }
            }
        }
        if (decodeCallback != null) {
            decodeCallback.onDecodeFinished();
        }
    }

    /**
     * 选择视频文件中的视频轨道
     * @param extractor 媒体解析器
     * @return 视频轨道，-1代表失败
     */
    private int selectTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                return i;
            }
        }
        return -1;
    }

}
