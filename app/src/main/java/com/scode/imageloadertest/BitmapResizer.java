package com.scode.imageloadertest;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.FileDescriptor;

/**
 * Created by 知らないのセカイ on 2017/5/21.
 */

public class BitmapResizer {
    public static Bitmap decodeBitmapFormResource(Resources resources,int id,int reqWidth,int reqHeight){
        //这里的reqwidth 未作处理

        Bitmap bitmap = BitmapFactory.decodeResource(resources, id);
        return bitmap;
    };
    public static Bitmap decodeBitmapFormFileDescriptor(FileDescriptor fileDescriptor, int reqWidth, int reqHeight){
        //这里的reqwidth 未作处理
        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        return bitmap;
    };

    //这里可以添加一个计算options的sample方法

}
