//
// Created by WangShengyang on 2019/8/25.
//

#ifndef MP4EXTRACTOR_RECORDER_H
#define MP4EXTRACTOR_RECORDER_H

#include <android/log.h>

#define  LOG_TAG "wsy"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

extern "C" {
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libavformat/avio.h"
#include "libavutil/imgutils.h"
};

class Recorder {
private:
    AVFormatContext *pFormatCtx;
    AVOutputFormat *outfmt;
    AVStream *video_st;
    AVCodecContext *pCodecCtx;
    AVCodec *pCodec;
    AVPacket pkt;
    uint8_t *picture_buf;
    AVFrame *pFrame;
    int fps;
    int frameIndex;
    char *path;
    AVDictionary *dictionary = 0;

public:
    long System_currentTimeMillis() ;

    int startRecord(const  char *mp4Path, int width, int height, int fps);

    int pushFrame(char *yv12, int width, int height);

    int stopRecord();
};


#endif //MP4EXTRACTOR_RECORDER_H
