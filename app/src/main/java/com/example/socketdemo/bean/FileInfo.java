package com.example.socketdemo.bean;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.Serializable;
import java.util.List;

/**
 * Created by AA on 2017/3/23.
 */
public class FileInfo implements Serializable {

    /**
     * 文件传输结果：1 成功  -1 失败
     */
    public static final int FLAG_SUCCESS = 1;
    public static final int FLAG_FAILURE = -1;


    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件类型
     */
    private int fileType;

    /**
     * 文件大小
     */
    private long size;

    /***
     * 文件名
     */
    private String fileName;

    /**
     * 文件传送结果
     */
    private int result;

    /**
     * 传输进度
     */
    private int progress;


    private int position;


    public FileInfo(int position, String filePath, long size) {
        this.position = position;
        this.filePath = filePath;
        this.size = size;
    }

    public FileInfo() {

    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getFileType() {
        return fileType;
    }

    public void setFileType(int fileType) {
        this.fileType = fileType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public static String toJsonStr(FileInfo fileInfo) {
        return new Gson().toJson(fileInfo);
    }

    public static String toJsonStr(List<FileInfo> fileInfoList) {
        return new Gson().toJson(fileInfoList);
    }

    public static FileInfo toObject(String jsonStr) {
        return new Gson().fromJson(jsonStr, FileInfo.class);
    }

    public static List<FileInfo> toObjectList(String jsonStr) {
        return new Gson().fromJson(jsonStr, new TypeToken<List<FileInfo>>(){}.getType());
    }

    @Override
    public String toString() {
        return "FileInfo:{" +
                "filePath='" + filePath + '\'' +
                ", fileType=" + fileType +
                ", size=" + size +
                ", position=" + position +
                '}';
    }
}
