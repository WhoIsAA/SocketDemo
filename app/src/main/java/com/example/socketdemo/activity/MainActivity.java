package com.example.socketdemo.activity;

import android.view.View;

import com.example.socketdemo.R;
import com.example.socketdemo.base.BaseActivity;

public class MainActivity extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected String getTitleText() {
        return "首页";
    }

    @Override
    protected void initData() {
        setToolbarLeftIcon(0);

    }

    public void sendFiles(View view) {
        //发送文件
//        pushActivity(ChooseReceiverActivity.class);
        pushActivity(SendFilesActivity.class);
    }

    public void receiveFiles(View view) {
        //接收文件
//        pushActivity(ReceiverWaitingActivity.class);
        pushActivity(ReceiveFilesActivity.class);
    }
}
