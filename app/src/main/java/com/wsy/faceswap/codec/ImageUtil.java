package com.wsy.faceswap.codec;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import java.nio.ByteBuffer;

public class ImageUtil {
    private static final int MASK_A = 0xFF000000;
    private static final int MASK_R = 0xFF0000;
    private static final int MASK_G = 0xFF00;
    private static final int MASK_B = 0xFF;

    public static int rgbToY(int r, int g, int b) {
        return (((66 * r + 129 * g + 25 * b + 128) >> 8) + 16);
    }

    public static int rgbToU(int r, int g, int b) {

        return (((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128);
    }

    public static int rgbToV(int r, int g, int b) {
        return (((112 * r - 94 * g - 18 * b + 128) >> 8) + 128);
    }

    public static int yuvToR(int y, int u, int v) {
        return (int) ((y & 0xFF) + 1.4075 * ((v & 0xFF) - 128));
    }

    public static int yuvToG(int y, int u, int v) {
        return (int) ((y & 0xFF) - 0.3455 * ((u & 0xFF) - 128) - 0.7169 * ((v & 0xFF) - 128));
    }

    public static int yuvToB(int y, int u, int v) {
        return (int) ((y & 0xFF) + 1.779 * ((u & 0xFF) - 128));
    }

    public static int alignIntToByte(int c) {
        return c & 0xFF;
    }

    public static int max(int a, int b) {
        return a > b ? a : b;
    }

    public static void nv21ToNv12(byte[] nv21, byte[] nv12) {
        System.arraycopy(nv21, 0, nv12, 0, nv21.length * 2 / 3);

        int length = Math.min(nv12.length, nv21.length);
        int uvStart = length * 2 / 3;
        for (int i = uvStart; i < length; i += 2) {
            nv12[i + 1] = nv21[i];
            nv12[i] = nv21[i + 1];
        }
    }

    public static void nv21ToYv12(byte[] nv21, byte[] yv12) {
        int ySize = nv21.length * 2 / 3;
        int totalSize = nv21.length;
        int i420UIndex = ySize;
        int i420VIndex = ySize * 5 / 4;
        //复制y
        System.arraycopy(nv21, 0, yv12, 0, ySize);
        //复制uv
        for (int uvIndex = ySize; uvIndex < totalSize; uvIndex += 2) {
            yv12[i420UIndex++] = nv21[uvIndex];
            yv12[i420VIndex++] = nv21[uvIndex + 1];
        }
    }

    public static void drawRectOnNv21(byte[] nv21, int width, int height, int color, int paintWidth, Rect rect) {
        drawRectOnNv21(nv21, width, height, color, paintWidth, rect.left, rect.top, rect.right, rect.bottom);
    }

    public static void drawRectOnNv21(byte[] nv21, int width, int height, int color, int paintWidth, int left, int top,
                                      int right, int bottom) {
        left &= ~0b11;
        top &= ~0b11;
        right &= ~0b11;
        bottom &= ~0b11;

        int r = (color & MASK_R) >> 16;
        int g = (color & MASK_G) >> 8;
        int b = color & MASK_B;
        int y = rgbToY(r, g, b);
        int u = rgbToU(r, g, b);
        int v = rgbToV(r, g, b);
        y = alignIntToByte(y);
        u = alignIntToByte(u);
        v = alignIntToByte(v);

        int innerTop = top + paintWidth;
        int innerBottom = bottom - paintWidth;
        int innerLeft = left + paintWidth;
        int innerRight = right - paintWidth;

        //上边
        int singleLength = right - left;
        int yStartIndex = top * width + left;
        int uvStartIndex = width * height + ((top / 2 * width) + left);
        boolean adjustUV = false;
        for (int i = top; i < innerTop; i++) {
            for (int j = 0; j < singleLength; j++) {
                nv21[yStartIndex + j] = (byte) y;
            }
            yStartIndex += width;
            if (adjustUV = !adjustUV) {
                for (int j = 0; j < singleLength; j += 2) {
                    nv21[uvStartIndex + j] = (byte) v;
                    nv21[uvStartIndex + j + 1] = (byte) u;
                }
                uvStartIndex += width;
            }
        }

        //左边
        yStartIndex = innerTop * width + left;
        uvStartIndex = width * height + (innerTop / 2 * width + left);
        adjustUV = false;
        for (int i = innerTop; i < innerBottom; i++) {
            for (int j = 0; j < paintWidth; j++) {
                nv21[yStartIndex + j] = (byte) y;
            }
            yStartIndex += width;
            if (adjustUV = !adjustUV) {
                for (int j = 0; j < paintWidth; j += 2) {
                    nv21[uvStartIndex + j] = (byte) v;
                    nv21[uvStartIndex + j + 1] = (byte) u;
                }
                uvStartIndex += width;
            }
        }
        //右边
        yStartIndex = innerTop * width + innerRight;
        uvStartIndex = width * height + (innerTop / 2 * width + innerRight);
        adjustUV = false;
        for (int i = innerTop; i < innerBottom; i++) {
            for (int j = 0; j < paintWidth; j++) {
                nv21[yStartIndex + j] = (byte) y;
            }
            yStartIndex += width;
            if (adjustUV = !adjustUV) {
                for (int j = 0; j < paintWidth; j += 2) {
                    nv21[uvStartIndex + j] = (byte) v;
                    nv21[uvStartIndex + j + 1] = (byte) u;
                }
                uvStartIndex += width;
            }
        }


        //下边
        yStartIndex = innerBottom * width + left;
        uvStartIndex = width * height + ((innerBottom / 2 * width) + left);
        adjustUV = false;
        for (int i = innerBottom; i < bottom; i++) {
            for (int j = 0; j < singleLength; j++) {
                nv21[yStartIndex + j] = (byte) y;
            }
            yStartIndex += width;
            if (adjustUV = !adjustUV) {
                for (int j = 0; j < singleLength; j += 2) {
                    nv21[uvStartIndex + j] = (byte) v;
                    nv21[uvStartIndex + j + 1] = (byte) u;
                }
                uvStartIndex += width;
            }
        }
    }

    /**
     * 将NV21数据绘制到NV21数据上
     *
     * @param nv21            大图NV21数据
     * @param width           大图宽度
     * @param height          大图高度
     * @param left            大图被绘制的左边
     * @param top             大图被绘制的右边
     * @param waterMarkNv21   小图NV21数据
     * @param waterMarkWidth  小图的宽度
     * @param waterMarkHeight 小图的高度
     */
    public static void drawNv21OnNv21(byte[] nv21, int width, int height, int left, int top, byte[] waterMarkNv21,
                                      int waterMarkWidth,
                                      int waterMarkHeight) {
        //确保偶数
        left &= ~1;
        top &= ~1;

        int nv21YLineDataSize = width;
        int waterMarkYLineDataSize = waterMarkWidth;
        int nv21YLinePositionOffset = left;
        int nv21YPositionOffset = nv21YLineDataSize * top + nv21YLinePositionOffset;
        int waterMarkYPositionOffset = 0;
        for (int i = 0; i < waterMarkHeight; ++i) {
            System.arraycopy(waterMarkNv21, waterMarkYPositionOffset, nv21, nv21YPositionOffset, waterMarkYLineDataSize);
            nv21YPositionOffset += nv21YLineDataSize;
            waterMarkYPositionOffset += waterMarkYLineDataSize;
        }
        int waterMarkUVLineDataSize = waterMarkWidth;
        int nv21UVLinePositionOffset = left;
        int waterMarkUVPositionOffset = waterMarkWidth * waterMarkHeight;
        for (int i = 0; i < waterMarkHeight; i += 2) {
            System.arraycopy(waterMarkNv21, waterMarkUVPositionOffset, nv21, nv21UVLinePositionOffset, waterMarkUVLineDataSize);
            waterMarkUVPositionOffset += waterMarkUVLineDataSize;
        }
    }

    public static byte[] getNv21FromBitmap(Bitmap bitmap) {
        int allocationByteCount = bitmap.getAllocationByteCount();
        byte[] data = new byte[allocationByteCount];
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        bitmap.copyPixelsToBuffer(byteBuffer);
        byte[] nv21 = new byte[bitmap.getWidth() * bitmap.getHeight() * 3 / 2];
        rgba32ToNv21(data, nv21, bitmap.getWidth(), bitmap.getHeight());
        return nv21;
    }

    public static Bitmap cropAndScaleBitmap(Bitmap bitmap, int newWidth, int newHeight) {
        int drawSide = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Rect rect;
        if (bitmap.getWidth() == drawSide) {
            int padding = (bitmap.getHeight() - drawSide) / 2;
            rect = new Rect(0, padding, bitmap.getWidth(), bitmap.getHeight() - padding);
        } else {
            int padding = (bitmap.getWidth() - drawSide) / 2;
            rect = new Rect(padding, 0, bitmap.getWidth() - padding, bitmap.getHeight());
        }
        Bitmap newBitmap = Bitmap.createBitmap(newWidth & ~0b11, newHeight & ~0b11, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        canvas.drawBitmap(bitmap, rect, new RectF(0, 0, newWidth, newHeight), new Paint());
        return newBitmap;
    }


    public static Bitmap rotateBitmap(Bitmap bitmap, float rotateDegree) {
        if (bitmap == null) {
            return null;
        } else {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotateDegree);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
        }
    }

    public static void rgba32ToNv21(byte[] rgba32, byte[] nv21, int width, int height) {
        int yIndex = 0;
        int uvIndex = width * height;
        int rgbaIndex = 0;
        int nv21Length = width * height * 3 / 2;
        for (int j = 0; j < height; ++j) {
            for (int i = 0; i < width; ++i) {
                int r = rgba32[rgbaIndex++];
                int g = rgba32[rgbaIndex++];
                int b = rgba32[rgbaIndex++];
                rgbaIndex++;
                b = alignIntToByte(b);
                g = alignIntToByte(g);
                r = alignIntToByte(r);
                int y = rgbToY(r, g, b);
                nv21[yIndex++] = (byte) alignIntToByte(y);
                if ((j & 1) == 0 && ((rgbaIndex >> 2) & 1) == 0 && uvIndex < nv21Length - 2) {
                    int u = rgbToU(r, g, b);
                    int v = rgbToV(r, g, b);
                    nv21[uvIndex++] = (byte) alignIntToByte(v);
                    nv21[uvIndex++] = (byte) alignIntToByte(u);
                }
            }
        }
    }

    /**
     * 将Y:U:V == 4:2:2的数据转换为nv21
     *
     * @param y      Y 数据
     * @param u      U 数据
     * @param v      V 数据
     * @param nv21   生成的nv21，需要预先分配内存
     * @param stride 步长
     * @param height 图像高度
     */
    public static void yuv422ToYuv420sp(byte[] y, byte[] u, byte[] v, byte[] nv21, int stride, int height) {
        System.arraycopy(y, 0, nv21, 0, y.length);
        int nv21UVIndex = stride * height;
        int length = y.length + u.length / 2 + v.length / 2 - 2;
        int uIndex = 0, vIndex = 0;
        for (int i = nv21UVIndex; i < length; i += 2) {
            vIndex += 2;
            uIndex += 2;
            nv21[i] = v[vIndex];
            nv21[i + 1] = u[uIndex];
        }
    }

    /**
     * 裁剪YUV420SP（NV21/NV12）
     *
     * @param yuv420sp     原始数据
     * @param cropYuv420sp 裁剪后的数据，需要预先分配内存
     * @param width        原始宽度
     * @param height       原始高度
     * @param left         原始数据被裁剪的左边界
     * @param top          原始数据被裁剪的上边界
     * @param right        原始数据被裁剪的右边界
     * @param bottom       原始数据被裁剪的下边界
     */
    public static void cropYuv420sp(byte[] yuv420sp, byte[] cropYuv420sp, int width, int height, int left, int top,
                                    int right, int bottom) {
        int halfWidth = width / 2;
        int cropImageWidth = right - left;
        int cropImageHeight = bottom - top;

        //复制Y
        int originalYLineStart = top * width;
        int targetYIndex = 0;

        //复制UV
        int originalUVLineStart = width * height + top * halfWidth;
        int targetUVIndex = cropImageWidth * cropImageHeight;
        for (int i = top; i < bottom; i++) {
            System.arraycopy(yuv420sp, originalYLineStart + left, cropYuv420sp, targetYIndex, cropImageWidth);
            originalYLineStart += width;
            targetYIndex += cropImageWidth;
            if ((i & 1) == 0) {

                System.arraycopy(yuv420sp, originalUVLineStart + left, cropYuv420sp, targetUVIndex,
                        cropImageWidth);
                originalUVLineStart += width;
                targetUVIndex += cropImageWidth;
            }
        }
    }
}
