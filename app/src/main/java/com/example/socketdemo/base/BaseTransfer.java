package com.example.socketdemo.base;

/**
 * Created by AA on 2017/3/24.
 */
public abstract class BaseTransfer implements Transferable {

    /**
     * 字节数组长度
     */
    public static final int BYTE_SIZE_HEADER = 1024 * 10;
    public static final int BYTE_SIZE_DATA = 1024 * 4;

    /**
     * 传输字节类型
     */
    public static final String UTF_8 = "UTF-8";

}
