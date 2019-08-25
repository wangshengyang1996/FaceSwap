//
// Created by Administrator on 2019/8/25.
//

#include "Recorder.h"

long Recorder::System_currentTimeMillis() {
    timeval now;
    gettimeofday(&now, NULL);
    long when = now.tv_sec * 1000LL + now.tv_usec / 1000;
    return when;
}

int Recorder::startRecord(const char *mp4Path, int width, int height, int jFps) {
    av_register_all();
    path = static_cast<char *>(malloc(strlen(mp4Path)));
    strcpy(path, mp4Path);
    pFormatCtx = avformat_alloc_context();
    outfmt = av_guess_format(NULL, path, NULL);
    pFormatCtx->oformat = outfmt;
    //Open output URL
    if (avio_open(&pFormatCtx->pb, path, AVIO_FLAG_READ_WRITE) < 0) {
        printf("Failed to open output file! \n");
        return -1;
    }
    video_st = avformat_new_stream(pFormatCtx, 0);

    if (video_st == NULL) {
        return -1;
    }
    //Param that must set
    pCodecCtx = video_st->codec;
    //pCodecCtx->codec_id =AV_CODEC_ID_HEVC;
    pCodecCtx->codec_id = outfmt->video_codec;
    pCodecCtx->codec_type = AVMEDIA_TYPE_VIDEO;
    pCodecCtx->pix_fmt = AV_PIX_FMT_YUV420P;
    pCodecCtx->width = width;
    pCodecCtx->height = height;
    pCodecCtx->bit_rate = 100000;
    pCodecCtx->gop_size = 250;


    pCodecCtx->time_base.num = 1;
    pCodecCtx->time_base.den = jFps;
    fps = jFps;

    //H264
    //pCodecCtx->me_range = 16;
    //pCodecCtx->max_qdiff = 4;
    //pCodecCtx->qcompress = 0.6;
    pCodecCtx->qmin = 10;
    pCodecCtx->qmax = 51;

    //Optional Param
    pCodecCtx->max_b_frames = 3;

    // Set Option
    //H.264
    if (pCodecCtx->codec_id == AV_CODEC_ID_H264) {
        av_dict_set(&dictionary, "preset", "slow", 0);
        av_dict_set(&dictionary, "tune", "zerolatency", 0);
        //av_dict_set(¶m, "profile", "main", 0);
    }
    //H.265
    if (pCodecCtx->codec_id == AV_CODEC_ID_H265) {
        av_dict_set(&dictionary, "preset", "ultrafast", 0);
        av_dict_set(&dictionary, "tune", "zero-latency", 0);
    }

    //Show some Information
    av_dump_format(pFormatCtx, 0, path, 1);

    pCodec = avcodec_find_encoder(pCodecCtx->codec_id);
    if (!pCodec) {
        printf("Can not find encoder! \n");
        return -1;
    }
    if (avcodec_open2(pCodecCtx, pCodec, &dictionary) < 0) {
        printf("Failed to open encoder! \n");
        return -1;
    }


    pFrame = av_frame_alloc();
    av_image_fill_arrays(pFrame->data, pFrame->linesize,
                         picture_buf, pCodecCtx->pix_fmt, width, height, 1);
    //Write File Header
    avformat_write_header(pFormatCtx, NULL);

    av_new_packet(&pkt,
                  av_image_get_buffer_size(pCodecCtx->pix_fmt, pCodecCtx->width, pCodecCtx->height,
                                           4));
    frameIndex = 0;
    return 0;
}

int Recorder::pushFrame(char *yv12, int width, int height) {
    pFrame->data[0] = reinterpret_cast<uint8_t *>(yv12);             // Y
    pFrame->data[1] = reinterpret_cast<uint8_t *>( yv12 + width * height * 5 / 4);      // U
    pFrame->data[2] = reinterpret_cast<uint8_t *>( yv12 + width * height);  // V
    pFrame->pts = frameIndex++ * (video_st->time_base.den) / ((video_st->time_base.num) * fps);
    int got_picture = 0;
    //Encode
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
    pkt.stream_index = video_st->index;
    start = System_currentTimeMillis();
    ret = av_write_frame(pFormatCtx, &pkt);
    end = System_currentTimeMillis();
    LOGI("av_write_frame cost is %ld", end - start);
    av_packet_unref(&pkt);
    return 0;
}

int Recorder::stopRecord() {
    free(path);
    frameIndex = 0;
    //Flush Encoder
    int ret = flush_encoder(pFormatCtx, 0);
    if (ret < 0) {
        printf("Flushing encoder failed\n");
        return -1;
    }

    //Write file trailer
    av_write_trailer(pFormatCtx);

    //Clean
    if (video_st) {
        avcodec_close(pCodecCtx);
        av_free(pFrame);
        av_free(picture_buf);
    }
    avio_close(pFormatCtx->pb);
    avformat_free_context(pFormatCtx);

    return 0;
}

int Recorder::flush_encoder(AVFormatContext *fmt_ctx, unsigned int stream_index) {
    int ret;
    AVPacket enc_pkt;
    if (!(fmt_ctx->streams[stream_index]->codec->codec->capabilities &
          CODEC_CAP_DELAY))
        return 0;
    while (true) {
        enc_pkt.data = NULL;
        enc_pkt.size = 0;
        av_init_packet(&enc_pkt);
        int ret = avcodec_send_frame(pCodecCtx, pFrame);
        if (ret < 0) {
            break;
        }
        ret = avcodec_receive_packet(pCodecCtx, &pkt);
        if (ret < 0) {
            break;
        }
        printf("Flush Encoder: Succeed to encode 1 frame!\tsize:%5d\n", enc_pkt.size);
        /* mux encoded frame */
        ret = av_write_frame(fmt_ctx, &enc_pkt);
        if (ret < 0) {
            break;
        }
    }
    return ret;
}
