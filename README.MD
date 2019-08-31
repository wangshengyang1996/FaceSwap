- **效果如下**

|![原视频](https://github.com/wangshengyang1996/FaceSwap/blob/master/sample/pdd.gif)|![换脸后](https://github.com/wangshengyang1996/FaceSwap/blob/master/sample/marked.gif)|
|:-|:-|

- **所需的技术介绍：**
   1. 视频编解码 （用于将视频解码为裸数据，再编码成视频，解码使用MediaCodec，编码使用ffmpeg）
   2. 人脸检测，人脸识别（使用虹软人脸识别SDK，[虹软人脸识别 - ArcFace SDK介绍及使用注意事项](https://www.jianshu.com/p/8ceb1c6d4e2c)）
   3. 帧数据图像处理（格式转换、裁剪、绘制），如果对**格式转换**和**图像裁剪**不太了解可以看下我以前写的介绍。[图像实战 - RGB、YUV图像格式转换](https://www.jianshu.com/p/7cf0e294ffd9)、[图像实战 - 裁剪RGB、YUV图像](https://www.jianshu.com/p/9e4bf1047c9b)。


- **视频换脸流程：**
   1. mp4等视频文件帧数据解析，将编码数据解码为YUV数据
   2. 对YUV数据进行格式转换，转换为人脸识别SDK支持的图像格式（NV21）
   3. 对NV21图像数据进行人脸追踪
   4. 若是第一次检测到的人脸，则对人脸区域进行绘制
   5. 将绘制好的数据进行格式转换，用于视频录制
   6. 裸数据编码视频流
