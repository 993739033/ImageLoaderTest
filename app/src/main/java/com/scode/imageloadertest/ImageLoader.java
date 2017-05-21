package com.scode.imageloadertest;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.util.LruCache;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Created by 知らないのセカイ on 2017/5/21.
 */

@RequiresApi(api = Build.VERSION_CODES.N)
public class ImageLoader {

    /**
     * 首先实现线程池
     */
    private int CORE_SIZE=Runtime.getRuntime().availableProcessors();//获取核心线程数
    //创建ThreadFactory
    private ThreadFactory factory=new ThreadFactory() {
        @Override
        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r);
        }
    };

    private Executor MY_THREAD_POOL = new ThreadPoolExecutor(CORE_SIZE + 1, CORE_SIZE * 2 + 1, 10,
            java.util.concurrent.TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>(),factory
            );

    //创建handler 进行ui操作
    private Handler mhandler=new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    };

    private LruCache<String,Bitmap> mlruCache =null;
    private DiskLruCache mdiskLruCache =null;
    private static final int TAG_KEY_URI = 1;
    private static final int BUFFER_SIZE=8*1024*1024;

    //在构造方法中实例化一些对象
    public ImageLoader(Context context) {
        int maxmemsize = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int memsize=maxmemsize/8;
        mlruCache = new LruCache<String,Bitmap>(memsize){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        File diskcachedir = getDiskCacheDir(context, "Bitmap");
        try {
            mdiskLruCache = DiskLruCache.open(diskcachedir, 1, 1, 50 * 1024 * 1024);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    /**
     * 获取所要存储的位置
     * @param context
     * @param name
     * @return
     */
    private File getDiskCacheDir(Context context,String name){
        String path = context.getExternalCacheDir().getPath();
        File file = new File(path + File.separator + name);
        if (!file.exists()) {
            file.mkdir();
        }
        return file;
    }


    /**
     * 添加内存cache 的添加数据和获取数据的方法
     */
    private void addBitmapToMemCache(String key,Bitmap bitmap){
        mlruCache.put(key, bitmap);

    }
    private Bitmap getBitmapFromMemCache(String key){
        return  mlruCache.get(key);
    }

    public void bindBitmap(String uri, ImageView imageView, int reqWidth, int reqHeight) {
        imageView.setTag(TAG_KEY_URI, uri);
        Bitmap bitmap = loadBitmapFormMemCache(uri);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return ;
        }
    }

    /**
     * 通过url转化为hashkey
     *
     * @param key
     * @return
     */

    public String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }



    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * 实现方法分别从memcache diskcache 或 通过url 来下载bitmap
     */

    // 将url转为key进行查找
    private Bitmap loadBitmapFormMemCache(String url){
        String key = hashKeyForDisk(url);
        Bitmap bitmap= getBitmapFromMemCache(key);
        return bitmap;
    }
    // 从disklrucache中查找bitmap 如果有就加载到内存中且返回
    private Bitmap loadBitmapFormDiskCache(String url) throws IOException {
        if (mdiskLruCache!=null){
            return null;
        }
        String key = hashKeyForDisk(url);
        Bitmap bitmap=null;

        DiskLruCache.Snapshot snapshot = mdiskLruCache.get(key);
        FileInputStream fis = (FileInputStream) snapshot.getInputStream(0);
        FileDescriptor fileDescriptor=fis.getFD();
        bitmap =  BitmapResizer.decodeBitmapFormFileDescriptor(fileDescriptor, 1, 1);
        if (bitmap != null) {
            addBitmapToMemCache(key, bitmap);
        }
        return bitmap;
    }

    private Bitmap loadBitmapFromHttp(String url) throws IOException {
        if (mdiskLruCache == null) {
            return null;
        }
        String key = hashKeyForDisk(url);
        DiskLruCache.Editor editor = mdiskLruCache.edit(key);
        if (editor!=null){
        OutputStream outputStream = editor.newOutputStream(0);
        if (downloadUrlToStream(url, outputStream)) {
            editor.commit();
        }else{
            editor.abort();
        }
            mdiskLruCache.flush();
        }
        mdiskLruCache.flush();
        return loadBitmapFormDiskCache(url);
    }

    //通过rul下载bitmap到DiskCache中
    private Boolean downloadUrlToStream(String urlString, OutputStream outputStream) throws IOException {
        HttpURLConnection httpURLConnection=null;
        BufferedInputStream bufferedInputStream=null;
        BufferedOutputStream bufferedOutputStream=null;
        String key;
        try {
            URL url = new URL(urlString);
            httpURLConnection = (HttpURLConnection) url.openConnection();
            bufferedInputStream  = new BufferedInputStream(httpURLConnection.getInputStream(),BUFFER_SIZE);
            bufferedOutputStream = new BufferedOutputStream(outputStream, BUFFER_SIZE);
            int i;
            while ((i = bufferedInputStream.read()) != -1) {
                bufferedOutputStream.write(i);
            }
            return true;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
                httpURLConnection=null;
            }
            if (bufferedInputStream != null) {
                bufferedInputStream.close();
                bufferedInputStream=null;
            }
            if (bufferedOutputStream != null) {
                bufferedOutputStream.close();
                bufferedOutputStream=null;
            }
        }
        return false;
    }









}
