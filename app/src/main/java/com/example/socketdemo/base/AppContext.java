package com.example.socketdemo.base;

import android.app.Application;

import com.example.socketdemo.bean.FileInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Created by AA on 2017/3/23.
 */
public class AppContext extends Application {

    /**
     * App全局上下文
     */
    private static AppContext mInstance;

    /**
     * 主线程池
     */
    public static Executor MAIN_EXECUTOR = Executors.newFixedThreadPool(5);

    /**
     * 文件发送端单线程
     */
    public static Executor FILE_SENDER_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * 待发送的文件数据
     */
    public Map<String, FileInfo> mSendFileInfoMap = new HashMap<>();

    /**
     * 接收到的文件数据
     */
    public Map<String, FileInfo> mReceivedFileInfoMap = new HashMap<>();


    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
    }

    /**
     * 获取Application全局变量
     * @return
     */
    public static AppContext getAppContext() {
        return mInstance;
    }


    /**************************************************************************************
     ********************************************发送端************************************
     **************************************************************************************/

    /**
     * 删除待发送的文件Map
     */
    public void clearSendFileInfoMap() {
        mSendFileInfoMap.clear();
    }

    /**
     * 获取待发送的文件Map
     * @return
     */
    public List<Map.Entry<String, FileInfo>> getSendFileInfoMap() {
        List<Map.Entry<String, FileInfo>> fileInfoMapList = new ArrayList<>(mSendFileInfoMap.entrySet());
        return fileInfoMapList;
    }

    /**
     * 获取待发送的文件总长度
     * @return
     */
    public long getAllSendFileInfoSize() {
        long totalSize = 0;
        for(FileInfo fileInfo : mSendFileInfoMap.values()) {
            if(fileInfo != null) {
                totalSize += fileInfo.getSize();
            }
        }
        return totalSize;
    }

    /**
     * 添加FileInfo
     * @param fileInfo
     */
    public void addSendFileInfo(FileInfo fileInfo) {
        if(!mSendFileInfoMap.containsKey(fileInfo)) {
            mSendFileInfoMap.put(fileInfo.getFilePath(), fileInfo);
        }
    }

    /**
     * 更新FileInfo
     * @param fileInfo
     */
    public void updateSendFileInfo(FileInfo fileInfo) {
        mSendFileInfoMap.put(fileInfo.getFilePath(), fileInfo);
    }


    /**************************************************************************************
     ********************************************接收端************************************
     **************************************************************************************/

    /**
     * 删除接收到的文件Map
     */
    public void clearReceiverFileInfoMap() {
        mReceivedFileInfoMap.clear();
    }

    /**
     * 获取接收到的文件Map
     * @return
     */
    public List<Map.Entry<String, FileInfo>> getReceiverFileInfoMap() {
        List<Map.Entry<String, FileInfo>> fileInfoMapList = new ArrayList<>(mReceivedFileInfoMap.entrySet());
        return fileInfoMapList;
    }

    /**
     * 获取接收到的文件总长度
     * @return
     */
    public long getAllReceiverFileInfoSize() {
        long totalSize = 0;
        for(FileInfo fileInfo : mReceivedFileInfoMap.values()) {
            if(fileInfo != null) {
                totalSize += fileInfo.getSize();
            }
        }
        return totalSize;
    }

    /**
     * 添加FileInfo
     * @param fileInfo
     */
    public void addReceiverFileInfo(FileInfo fileInfo) {
        if(!mReceivedFileInfoMap.containsKey(fileInfo.getFilePath())) {
            mReceivedFileInfoMap.put(fileInfo.getFilePath(), fileInfo);
        }
    }

    /**
     * 更新FileInfo
     * @param fileInfo
     */
    public void updateReceiverFileInfo(FileInfo fileInfo) {
        mReceivedFileInfoMap.put(fileInfo.getFilePath(), fileInfo);
    }

}
