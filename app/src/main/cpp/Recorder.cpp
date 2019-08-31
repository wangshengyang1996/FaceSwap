//
// Created by WangShengyang on 2019/8/25.
//

#include "Recorder.h"

long Recorder::System_currentTimeMillis() {
    timeval now;
    gettimeofday(&now, NULL);
    long when = now.tv_sec * 1000LL + now.tv_usec / 1000;
    return when;
}

int Recorder::startRecord(const char *mp4Path, int width, int height, int fps) {
    // 初始化所有组件
    av_register_all();
    path = static_cast<char *>(malloc(strlen(mp4Path)));
    strcpy(path, mp4Path);
    // 创建一个AVFormatContext对象，这个结构体包含媒体文件或流的构成和基本信息
    pFormatCtx = avformat_alloc_context();
    // 根据文件名猜测一个输出格式
    outfmt = av_guess_format(NULL, path, NULL);
    if (outfmt == NULL) {
        return -1;
    }
    pFormatCtx->oformat = outfmt;
    // 打开输出文件
    if (avio_open(&pFormatCtx->pb, path, AVIO_FLAG_READ_WRITE) < 0) {
        printf("Failed to open output file! \n");
        return -1;
    }
    // 创建一个视频流
    video_st = avformat_new_stream(pFormatCtx, 0);

    if (video_st == NULL) {
        return -1;
    }
    //为视频流配置参数
    pCodecCtx = video_st->codec;
    pCodecCtx->codec_id = outfmt->video_codec;
    pCodecCtx->codec_type = AVMEDIA_TYPE_VIDEO;
    pCodecCtx->pix_fmt = AV_PIX_FMT_YUV420P;
    pCodecCtx->width = width;
    pCodecCtx->height = height;
    pCodecCtx->bit_rate = 3000000;
    pCodecCtx->gop_size = 250;


    pCodecCtx->time_base.num = 1;
    pCodecCtx->time_base.den = fps;
    this->fps = fps;

    pCodecCtx->qmin = 10;
    pCodecCtx->qmax = 51;


    // 属性设置，AVDictionary用于存储 Key-Value 信息
    //H.264
    if (pCodecCtx->codec_id == AV_CODEC_ID_H264) {
        av_dict_set(&dictionary, "preset", "slow", 0);
        av_dict_set(&dictionary, "tune", "zerolatency", 0);
    }
    //H.265
    if (pCodecCtx->codec_id == AV_CODEC_ID_H265) {
        av_dict_set(&dictionary, "preset", "ultrafast", 0);
        av_dict_set(&dictionary, "tune", "zero-latency", 0);
    }
    // 根据codec_id寻找编码器
    pCodec = avcodec_find_encoder(pCodecCtx->codec_id);
    if (!pCodec) {
        printf("Can not find encoder! \n");
        return -1;
    }
    if (avcodec_open2(pCodecCtx, pCodec, &dictionary) < 0) {
        printf("Failed to open encoder! \n");
        return -1;
    }

    // 分配帧数据内存
    pFrame = av_frame_alloc();
    // 格式化数据
    av_image_fill_arrays(pFrame->data, pFrame->linesize,
                         picture_buf, pCodecCtx->pix_fmt, width, height, 4);
    // 创建一个AVPacket对象，用于存储编码后的数据
    av_new_packet(&pkt,
                  av_image_get_buffer_size(pCodecCtx->pix_fmt, pCodecCtx->width, pCodecCtx->height,
                                           4));
    // 写文件头
    avformat_write_header(pFormatCtx, NULL);
    frameIndex = 0;
    return 0;
}

int Recorder::pushFrame(char *yv12, int width, int height) {
    /**
     * YV12的数据为3个plane，分别是：
     * 0:大小为width * height的Y:
     * 1:大小为width * height / 4 的V
     * 2:大小为width * height / 4 的U
     *
     * 因此 pFrame->data[i]分别指向不同的内存地址
     */
    pFrame->data[0] = reinterpret_cast<uint8_t *>(yv12);             // Y
    pFrame->data[1] = reinterpret_cast<uint8_t *>(yv12 + width * height * 5 / 4);      // U
    pFrame->data[2] = reinterpret_cast<uint8_t *>(yv12 + width * height);  // V

    pFrame->pts = frameIndex++ * (video_st->time_base.den) / ((video_st->time_base.num) * fps);
    // 编码，avcodec_send_frame发送裸数据后使用avcodec_receive_packet接收编码后内容
    long start = System_currentTimeMillis();
    int ret = avcodec_send_frame(pCodecCtx, pFrame);
    if (ret < 0) {
        printf("avcodec_send_frame failed! \n");
        return -1;
    }
    ret = avcodec_receive_packet(pCodecCtx, &pkt);
    long end = System_currentTimeMillis();
    LOGI("avcodec_encode_video2 cost is %ld", end - start);

    if (ret < 0) {
        printf("avcodec_encode_video2 failed! \n");
        return -1;
    }
    // 写入帧数据
    pkt.stream_index = video_st->index;
    start = System_currentTimeMillis();
    ret = av_write_frame(pFormatCtx, &pkt);
    end = System_currentTimeMillis();
    LOGI("av_write_frame cost is %ld", end - start);
    // 释放AVPacket
    av_packet_unref(&pkt);
    return 0;
}

int Recorder::stopRecord() {
    free(path);
    frameIndex = 0;

    // 写文件尾
    av_write_trailer(pFormatCtx);

    // 释放数据
    if (video_st) {
        avcodec_close(pCodecCtx);
        av_free(pFrame);
        av_free(picture_buf);
    }
    avio_close(pFormatCtx->pb);
    avformat_free_context(pFormatCtx);

    return 0;
}
