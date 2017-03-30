package com.example.socketdemo.utils;

import android.os.Environment;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.text.DecimalFormat;

/**
 * Created by AA on 2017/3/24.
 */
public class FileUtils {

    public static final String ROOT_PATH = Environment.getExternalStorageDirectory() + File.separator + "socketDemo/";


    /**
     * 根据文件路径获取文件名称
     * @param filePath
     * @return
     */
    public static String getFileName(String filePath) {
        if(TextUtils.isEmpty(filePath)) {
            return "";
        }
        return filePath.substring(filePath.lastIndexOf(File.separator) + 1);
    }

    /**
     * 生成本地文件路径
     * @param filePath
     * @return
     */
    public static File gerateLocalFile(String filePath) {
        String fileNmae = getFileName(filePath);
        File dirFile = new File(ROOT_PATH);
        if(!dirFile.exists()) {
            dirFile.mkdirs();
        }
        return new File(dirFile, fileNmae);
    }

    /**
     * 转换文件大小
     *
     * @param fileSize
     * @return
     */
    public static String FormetFileSize(long fileSize) {
        if(fileSize <= 0) {
            return "0KB";
        }

        DecimalFormat df = new DecimalFormat("#.00");
        String fileSizeString = "";
        if (fileSize < 1024) {
            fileSizeString = df.format((double) fileSize) + "B";
        } else if (fileSize < 1048576) {
            fileSizeString = df.format((double) fileSize / 1024) + "K";
        } else if (fileSize < 1073741824) {
            fileSizeString = df.format((double) fileSize / 1048576) + "M";
        } else {
            fileSizeString = df.format((double) fileSize / 1073741824) + "G";
        }
        return fileSizeString;
    }

    /**
     * 取得文件大小
     *
     * @param f
     * @return
     * @throws Exception
     */
    @SuppressWarnings("resource")
    public static long getFileSizes(File f) throws Exception {
        long size = 0;
        if (f.exists()) {
            FileInputStream fis = null;
            fis = new FileInputStream(f);
            size = fis.available();
        } else {
            f.createNewFile();
        }
        return size;
    }

}
