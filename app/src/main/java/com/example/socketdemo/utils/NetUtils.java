package com.example.socketdemo.utils;

import java.io.IOException;

/**
 * Created by AA on 2017/3/23.
 */
public class NetUtils {

    /**
     * 是否可以ping通指定IP地址
     * @param ipAddress
     * @return
     */
    public static boolean pingIpAddress(String ipAddress) {
        try {
            Process process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -w 100 " + ipAddress);
            int status = process.waitFor();
            if (status == 0) {
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }
}
