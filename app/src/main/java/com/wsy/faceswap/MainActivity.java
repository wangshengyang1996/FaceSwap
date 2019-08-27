package com.wsy.faceswap;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.arcsoft.face.ErrorInfo;
import com.arcsoft.face.FaceEngine;
import com.arcsoft.face.FaceFeature;
import com.arcsoft.face.FaceInfo;
import com.arcsoft.face.FaceSimilar;
import com.wsy.faceswap.codec.Mp4Recorder;
import com.wsy.faceswap.codec.DecodeCallback;
import com.wsy.faceswap.codec.ImageUtil;
import com.wsy.faceswap.codec.Mp4Decoder;
import com.wsy.faceswap.ffmpeg.RecordUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity implements DecodeCallback {
    private static final String TAG = "MainActivity";

    /**
     * 请求权限的请求码
     */
    private static final int ACTION_REQUEST_PERMISSIONS = 0x001;

    /**
     * 所需的所有权限信息
     */
    private static String[] NEEDED_PERMISSIONS = new String[]{
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };


    /**
     * ArcFace引擎，用于视频画面人脸追踪和识别
     */
    private FaceEngine faceEngine;

    /**
     * 用于ArcFace SDK 激活
     */
    public static final String APP_ID = "3jV31oGD5YcGBiM4PrCmV83Npi1PGkfqDdDbTWGRq467";
    public static final String SDK_KEY = "75TtTAYdbBBjWnWQgm6co1wnUCHeyhEXECc9E8trM9Xc";


    /**
     * 第一次检测到的人脸的当前faceId
     */
    private int firstFaceId = 0;
    /**
     * 上一次特征提取的结果，用于在人脸ID变化时进行比对校验
     */
    private FaceFeature firstFaceFeature;
    /**
     * 当前处理的人脸是否是最开始的人脸
     */
    boolean isFirstFace;
    /**
     * 上一帧中的所有faceId
     */
    List<Integer> lastFrameExtractFaceIdList = new ArrayList<>();
    /**
     * 人脸相似度阈值
     */
    private static final float SIMILAR_THRESHOLD = 0.8f;

    /**
     * 视频源文件
     */
    String mp4Path = "sdcard/pdd.mp4";
    /**
     * 视频目标文件
     */
    String ffmpegMp4 = "sdcard/marked.mp4";
    String mediaCodecMp4 = "sdcard/mediaCodec.mp4";

    /**
     * 线程池
     */
    ExecutorService executorService = Executors.newFixedThreadPool(1);

    /**
     * 用于存放绘制头像的原图
     */
    Bitmap headBitmap;
    /**
     * 输入给ffmpeg的YV12数据
     */
    byte[] targetYv12;
    /**
     * 输入给MediaCodec的NV12数据
     */
    byte[] targetNv12;
    /**
     * 目标视频的宽高
     */
    private int videoWidth, videoHeight;

    /**
     * 使用ffmpeg将裸数据保存成MP4的工具类，由于是软解，没有MediaCodec效率高，不适合用于实时
     */
    private RecordUtil ffmpegRecorder;
    private Mp4Recorder mediaCodecRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        headBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.lbw);
        if (!checkPermissions(NEEDED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, NEEDED_PERMISSIONS, ACTION_REQUEST_PERMISSIONS);
        } else {
            decodeVideo();

//            try {
//                new EncodeAndMuxTest().testEncodeVideoToMp4();
//            } catch (Throwable throwable) {
//                throwable.printStackTrace();
//            }
        }

    }

    /**
     * 解码视频，并将裸数据在{@link #onFrameAvailable(Image, long)}中回传
     */
    private void decodeVideo() {
        if (!new File(mp4Path).exists()) {
            Toast.makeText(this, "file " + mp4Path + " not exists", Toast.LENGTH_SHORT).show();
            return;
        }
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                initFaceEngine();
                final Mp4Decoder mp4Decoder = new Mp4Decoder();
                try {
                    mp4Decoder.setDecodeCallback(MainActivity.this);
                    mp4Decoder.init(mp4Path);
                    mp4Decoder.videoDecode();
                    mp4Decoder.release();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void unInitFaceEngine() {
        if (faceEngine != null) {
            faceEngine.unInit();
        }
    }

    /**
     * 初始化人脸引擎，设置为全角度人脸检测
     */
    private void initFaceEngine() {
        if (faceEngine != null) {
            faceEngine.unInit();
        }
        faceEngine = new FaceEngine();
        int activeCode = faceEngine.active(this, APP_ID, SDK_KEY);
        if (activeCode == ErrorInfo.MOK || activeCode == ErrorInfo.MERR_ASF_ALREADY_ACTIVATED) {
            int code = faceEngine.init(this, FaceEngine.ASF_DETECT_MODE_VIDEO, FaceEngine.ASF_OP_0_HIGHER_EXT, 16, 1, FaceEngine.ASF_FACE_DETECT | FaceEngine.ASF_FACE_RECOGNITION);
            if (code != ErrorInfo.MOK) {
                throw new RuntimeException("init Engine failed");
            }
        } else {
            throw new RuntimeException("active failed");
        }
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        unInitFaceEngine();
        super.onDestroy();
    }

    @Override
    public void onDecodeStart(int width, int height, int frameRate) {
        targetYv12 = new byte[width * height * 3 / 2];
        targetNv12 = new byte[width * height * 3 / 2];
        videoWidth = width;
        videoHeight = height;
        ffmpegRecorder = new RecordUtil();
        ffmpegRecorder.startRecord(ffmpegMp4, videoWidth, videoHeight, frameRate);
        mediaCodecRecorder = new Mp4Recorder(width, height, frameRate, new File(mediaCodecMp4));
        mediaCodecRecorder.startRecord();
    }


    @Override
    public synchronized void onFrameAvailable(byte[] data, int width, int height, long time) {

    }

    @Override
    public void onFrameAvailable(Image image, long time) {
        Log.i(TAG, "onFrameAvailable: " + time);
        int frameWidth = image.getPlanes()[0].getRowStride();
        int frameHeight = image.getHeight();
        byte[] targetNv21 = imageToTargetNv21(image, frameWidth, frameHeight);

        // 对裁剪后的数据进行人脸检测
        List<FaceInfo> faceInfoList = new ArrayList<>();
        int code = faceEngine.detectFaces(targetNv21, videoWidth, videoHeight, FaceEngine.CP_PAF_NV21, faceInfoList);
        if (code == ErrorInfo.MOK && faceInfoList.size() > 0) {
            if (firstFaceFeature == null) {
                recordFirstFace(targetNv21, faceInfoList.get(0));
            }
            // 第一次找到的人脸在当前帧的人脸信息
            FaceInfo firstFaceInfo = null;
            // faceId存在，即存在
            for (FaceInfo faceInfo : faceInfoList) {
                if (faceInfo.getFaceId() == firstFaceId) {
                    firstFaceInfo = faceInfo;
                    break;
                }
            }
            // faceId不存在，筛选性地进行特征提取并比对，判断是否存在
            if (firstFaceInfo == null) {
                for (FaceInfo faceInfo : faceInfoList) {
                    // 当前人脸是否被处理过，处理过则跳过
                    boolean isCurrentFaceProcessed = isCurrentFaceProcessed(faceInfo,lastFrameExtractFaceIdList);
                    // 没处理过，则特征提取并比对
                    if (!isCurrentFaceProcessed) {
                        firstFaceInfo = getFaceInfo(targetNv21, faceInfo);
                        // 记录faceId
                        if (firstFaceInfo != null) {
                            firstFaceId = firstFaceInfo.getFaceId();
                            break;
                        }
                    }
                }
                // 清除处理过的人脸列表中的离开的人脸
                clearLeaveFaces(faceInfoList, lastFrameExtractFaceIdList);
            }
            Log.i(TAG, "onFrameAvailable:  isFirstFace " + isFirstFace);
            if (firstFaceInfo != null) {
                drawFrame(targetNv21, firstFaceInfo, videoWidth, videoHeight);
            }
        }
        // 处理完成，将NV21转成YV12，用于处理帧
        ImageUtil.nv21ToYv12(targetNv21, targetYv12);
        ffmpegRecorder.pushFrame(targetYv12, videoWidth, videoHeight);
        ImageUtil.nv21ToNv12(targetNv21, targetNv12);
        mediaCodecRecorder.pushFrame(targetNv12, time);
    }

    private byte[] imageToTargetNv21(Image image, int frameWidth, int frameHeight) {
        byte[] originNv21 = new byte[frameWidth * frameHeight * 3 / 2];
        byte[] y = new byte[image.getPlanes()[0].getBuffer().limit()];
        byte[] u = new byte[image.getPlanes()[1].getBuffer().limit()];
        byte[] v = new byte[image.getPlanes()[2].getBuffer().limit()];
        image.getPlanes()[0].getBuffer().get(y);
        image.getPlanes()[1].getBuffer().get(u);
        image.getPlanes()[2].getBuffer().get(v);
        // YUV422 转NV21
        ImageUtil.yuv422ToYuv420sp(y, u, v, originNv21, image.getPlanes()[0].getRowStride(), image.getHeight());
        // Image的数据一般都做了字节对齐，对齐部分都是以0填充的，也就是会显示为绿色，是无用数据，因此需要做一次裁剪
        byte[] targetNv21 = new byte[videoWidth * videoHeight * 3 / 2];
        ImageUtil.cropYuv420sp(originNv21, targetNv21, frameWidth, frameHeight, 0, frameHeight - videoHeight, videoWidth, frameHeight);
        return targetNv21;
    }

    private void drawFrame(byte[] targetNv21, FaceInfo firstFaceInfo, int videoWidth, int videoHeight) {
        Rect rect = firstFaceInfo.getRect();
        if (new Rect(0, 0, videoWidth, videoHeight).contains(rect)) {
            rect.left &= ~3;
            rect.top &= ~3;
            rect.right &= ~3;
            rect.bottom &= ~3;
            int degree = 0;
            switch (firstFaceInfo.getOrient()) {
                case FaceEngine.ASF_OC_0:
                    degree = 0;
                    break;
                case FaceEngine.ASF_OC_90:
                    degree = 270;
                    break;
                case FaceEngine.ASF_OC_180:
                    degree = 180;
                    break;
                case FaceEngine.ASF_OC_270:
                    degree = 90;
                    break;
                default:
                    break;
            }
            Bitmap bitmap = this.headBitmap.copy(Bitmap.Config.ARGB_8888, true);
            if (degree != 0) {
                bitmap = ImageUtil.rotateBitmap(bitmap, degree);
            }
            bitmap = ImageUtil.cropAndScaleBitmap(bitmap, rect.width(), rect.height());
            byte[] headMarkData = ImageUtil.getNv21FromBitmap(bitmap);
            ImageUtil.drawNv21OnNv21(targetNv21, videoWidth, videoHeight, rect.left, rect.top, headMarkData, bitmap.getWidth(), bitmap.getHeight());
        }
    }

    private FaceInfo getFaceInfo(byte[] targetNv21, FaceInfo faceInfo) {
        int code;
        FaceInfo firstFaceInfo;
        FaceFeature faceFeature = new FaceFeature();
        code = faceEngine.extractFaceFeature(targetNv21, videoWidth, videoHeight, FaceEngine.CP_PAF_NV21, faceInfo, faceFeature);
        if (code != ErrorInfo.MOK) {
            firstFaceInfo = null;
        } else {
            FaceSimilar faceSimilar = new FaceSimilar();
            code = faceEngine.compareFaceFeature(firstFaceFeature, faceFeature, faceSimilar);
            firstFaceInfo = (code != ErrorInfo.MOK || faceSimilar.getScore() < SIMILAR_THRESHOLD) ? null : faceInfo;
            lastFrameExtractFaceIdList.add(faceInfo.getFaceId());
        }
        return firstFaceInfo;
    }

    /**
     * 判断当前人脸是否已处理
     * @param faceInfo
     * @return
     */
    private boolean isCurrentFaceProcessed(FaceInfo faceInfo,List<Integer> faceIdList) {
        boolean isCurrentFaceProcessed = false;
        for (Integer integer : faceIdList) {
            if (integer == faceInfo.getFaceId()) {
                isCurrentFaceProcessed = true;
                break;
            }
        }
        return isCurrentFaceProcessed;
    }

    /**
     * 清除lastFrameExtractFaceIdList中多余的人脸ID
     * @param faceInfoList
     * @param lastFrameExtractFaceIdList
     */
    private void clearLeaveFaces(List<FaceInfo> faceInfoList, List<Integer> lastFrameExtractFaceIdList) {
        for (FaceInfo faceInfo : faceInfoList) {
            for (int i = lastFrameExtractFaceIdList.size() - 1; i > 0; i--) {
                if (lastFrameExtractFaceIdList.get(i) == faceInfo.getFaceId()) {
                    lastFrameExtractFaceIdList.remove(i);
                }
            }
        }
    }

    /**
     * 记录第一个被识别的人脸，之后都用这个人脸
     *
     * @param nv21         图像数据
     * @param faceInfo 人脸信息列表
     */
    private void recordFirstFace(byte[] nv21, FaceInfo faceInfo) {
        int code;
        // 特征还没存，存下人脸特征
        firstFaceFeature = new FaceFeature();
        code = faceEngine.extractFaceFeature(nv21, videoWidth, videoHeight, FaceEngine.CP_PAF_NV21, faceInfo, firstFaceFeature);
        if (code != ErrorInfo.MOK) {
            firstFaceFeature = null;
        } else {
            firstFaceId = faceInfo.getFaceId();
        }
    }

    @Override
    public void onDecodeFinished() {
        ffmpegRecorder.stopRecord();
        mediaCodecRecorder.stopRecord();
        Log.i(TAG, "onDecodeFinished: ");
    }

    /**
     * 权限检测
     *
     * @param neededPermissions 所需的所有权限
     * @return 是否检测通过
     */
    private boolean checkPermissions(String[] neededPermissions) {
        if (neededPermissions == null || neededPermissions.length == 0) {
            return true;
        }
        boolean allGranted = true;
        for (String neededPermission : neededPermissions) {
            allGranted &= ContextCompat.checkSelfPermission(this, neededPermission) == PackageManager.PERMISSION_GRANTED;
        }
        return allGranted;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == ACTION_REQUEST_PERMISSIONS) {
            boolean isAllGranted = true;
            for (int grantResult : grantResults) {
                isAllGranted &= grantResult == PackageManager.PERMISSION_GRANTED;
            }
            if (isAllGranted) {
                decodeVideo();
            } else {
                Toast.makeText(this, "权限被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
    }
}