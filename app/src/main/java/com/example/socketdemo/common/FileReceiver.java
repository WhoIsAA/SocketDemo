package com.example.socketdemo.common;

import com.example.socketdemo.base.BaseTransfer;
import com.example.socketdemo.bean.FileInfo;
import com.example.socketdemo.utils.FileUtils;
import com.example.socketdemo.utils.LogUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Created by AA on 2017/3/24.
 */
public class FileReceiver extends BaseTransfer implements Runnable {

    /**
     * 接收文件的Socket的输入输出流
     */
    private Socket mSocket;
    private InputStream mInputStream;

    /**
     * 待接收的文件数据
     */
    private FileInfo mFileInfo;

    /**
     * 用来控制线程暂停、恢复
     */
    private final Object LOCK = new Object();
    private boolean mIsPaused = false;

    /**
     * 设置未执行线程的不执行标识
     */
    private boolean mIsStop;

    /**
     * 该线程是否执行完毕
     */
    private boolean mIsFinish;

    /**
     * 文件接收监听事件
     */
    private OnReceiveListener mOnReceiveListener;


    public FileReceiver(Socket socket, FileInfo fileInfo) {
        mSocket = socket;
        mFileInfo = fileInfo;
    }

    /**
     * 设置接收监听事件
     * @param onReceiveListener
     */
    public void setOnReceiveListener(OnReceiveListener onReceiveListener) {
        mOnReceiveListener = onReceiveListener;
    }

    @Override
    public void run() {
        if(mIsStop) {
            return;
        }

        //初始化
        try {
            if(mOnReceiveListener != null) {
                mOnReceiveListener.onStart();
            }
            init();
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.i("FileReceiver init() ------->>> occur expection");
            if(mOnReceiveListener != null) {
                mOnReceiveListener.onFailure(e, mFileInfo);
            }
        }

        //发送文件实体数据
        try {
            parseBody();
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.i("FileReceiver parseBody() ------->>> occur expection");
            if(mOnReceiveListener != null) {
                mOnReceiveListener.onFailure(e, mFileInfo);
            }
        }

        //文件传输完毕
        try {
            finishTransfer();
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.i("FileReceiver finishTransfer() ------->>> occur expection");
            if(mOnReceiveListener != null) {
                mOnReceiveListener.onFailure(e, mFileInfo);
            }
        }
    }

    @Override
    public void init() throws Exception {
        if(mSocket != null) {
            mInputStream = mSocket.getInputStream();
        }
    }

    @Override
    public void parseBody() throws Exception {
        if(mFileInfo == null) {
            return;
        }

        long fileSize = mFileInfo.getSize();
        OutputStream fos = new FileOutputStream(FileUtils.gerateLocalFile(mFileInfo.getFilePath()));

        byte[] bytes = new byte[BYTE_SIZE_DATA];
        long total = 0;
        int len = 0;

        long sTime = System.currentTimeMillis();
        long eTime = 0;
        while ((len = mInputStream.read(bytes)) != -1) {
            synchronized (LOCK) {
                if(mIsPaused) {
                    try {
                        LOCK.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                //写入文件
                fos.write(bytes, 0, len);
                total = total + len;

                //每隔200毫秒返回一次进度
                eTime = System.currentTimeMillis();
                if(eTime - sTime > 200) {
                    sTime = eTime;
                    if(mOnReceiveListener != null) {
                        mOnReceiveListener.onProgress(mFileInfo, total, fileSize);
                    }
                }
            }
        }

        //文件接收成功
        if(mOnReceiveListener != null) {
            mOnReceiveListener.onSuccess(mFileInfo);
        }
        mIsFinish = true;
    }

    @Override
    public void finishTransfer() throws Exception {
        if(mInputStream != null) {
            try {
                mInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(mSocket != null && mSocket.isConnected()) {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 暂停接收线程
     */
    public void pause() {
        synchronized (LOCK) {
            mIsPaused = true;
            LOCK.notifyAll();
        }
    }

    /**
     * 恢复接收线程
     */
    public void resume() {
        synchronized (LOCK) {
            mIsPaused = false;
            LOCK.notifyAll();
        }
    }

    /**
     * 设置当前的接收任务不执行
     */
    public void stop() {
        mIsStop = true;
    }

    /**
     * 文件是否在接收中
     * @return
     */
    public boolean isRunning() {
        return !mIsFinish;
    }

    /**
     * 文件接收监听事件
     */
    public interface OnReceiveListener {
        void onStart();
        void onProgress(FileInfo fileInfo, long progress, long total);
        void onSuccess(FileInfo fileInfo);
        void onFailure(Throwable throwable, FileInfo fileInfo);
    }
}
