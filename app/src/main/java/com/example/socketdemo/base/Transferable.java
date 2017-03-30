package com.example.socketdemo.base;

/**
 * Created by AA on 2017/3/24.
 */
public interface Transferable {

    /**
     * 初始化
     * @throws Exception
     */
    void init() throws Exception;

    /**
     * 发送/接收文件实体数据
     * @throws Exception
     */
    void parseBody() throws Exception;

    /**
     * 发送/接收完毕
     * @throws Exception
     */
    void finishTransfer() throws Exception;
}
