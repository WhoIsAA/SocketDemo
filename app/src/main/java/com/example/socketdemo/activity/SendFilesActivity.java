package com.example.socketdemo.activity;

import android.content.DialogInterface;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewStub;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.socketdemo.R;
import com.example.socketdemo.base.AppContext;
import com.example.socketdemo.base.BaseActivity;
import com.example.socketdemo.bean.FileInfo;
import com.example.socketdemo.common.Consts;
import com.example.socketdemo.common.FileSender;
import com.example.socketdemo.receiver.HotSpotBroadcaseReceiver;
import com.example.socketdemo.utils.FileUtils;
import com.example.socketdemo.utils.LogUtils;
import com.example.socketdemo.wifitools.ApMgr;
import com.zhy.adapter.recyclerview.CommonAdapter;
import com.zhy.adapter.recyclerview.base.ViewHolder;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import butterknife.BindView;

/**
 * Created by AA on 2017/3/28.
 */
public class SendFilesActivity extends BaseActivity {

    /**
     * 更新进度条
     */
    public static final int MSG_UPDATE_PROGRESS = 0x661;

    /**
     * 更新列表适配器
     */
    public static final int MSG_UPDATE_ADAPTER = 0x662;

    /**
     * 接收端初始化成功
     */
    public static final int MSG_FILE_RECEIVER_INIT_SUCCESS = 0x663;

    /**
     * 设置当前状态
     */
    public static final int MSG_SET_STATUS = 0x664;

    @BindView(R.id.tv_send_files_status)
    TextView tvStatus;

    @BindView(R.id.vs_send_files_open_hotspot)
    ViewStub vsOpenHotspot;

    @BindView(R.id.rv_send_files)
    RecyclerView mSendFileRecyclerView;

    private EditText etSSID;
    private EditText etPassword;

    /**
     * 发送文件列表适配器
     */
    private CommonAdapter<Map.Entry<String, FileInfo>> mSendFileAdapter;

    /**
     * 便携热点状态接收器
     */
    private HotSpotBroadcaseReceiver mHotSpotBroadcaseReceiver;

    /**
     * Udp Socket
     */
    private DatagramSocket mDatagramSocket;

    /**
     * 文件发送线程
     */
    private SenderServerRunnable mSenderServerRunnable;

    /**
     * 发送端所有待发送的文件列表
     */
    private List<FileInfo> mAllFileInfos = new ArrayList<>();

    /**
     * 发送文件线程列表数据
     */
    private List<FileSender> mFileSenderList = new ArrayList<>();

    /**
     * 获取权限是否成功
     */
    private boolean mIsPermissionGranted;

    /**
     * 是否初始化成功
     */
    private boolean mIsInitialized;


    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if(msg.what == MSG_UPDATE_PROGRESS) {
                //更新文件发送进度
                int position = msg.arg1;
                int progress = msg.arg2;
                if(position >= 0 && position < mSendFileAdapter.getItemCount()) {
                    updateProgress(position, progress);
                }
            } else if(msg.what == MSG_UPDATE_ADAPTER) {
                //更新列表适配器
                initSendFilesLayout();
            } else if(msg.what == MSG_SET_STATUS) {
                //设置当前状态
                setStatus(msg.obj.toString());
            } else if(msg.what == MSG_FILE_RECEIVER_INIT_SUCCESS) {
                //接收端初始化完毕
                setStatus("接收端初始化成功...");
                //显示发送文件视图
                initSendFilesLayout();
            }
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_send_files;
    }

    @Override
    protected String getTitleText() {
        return "发送文件";
    }

    @Override
    protected void initData() {
        //假装添加文件
        String file1 = Environment.getExternalStorageDirectory() + File.separator + "2.rar";
        String file2 = Environment.getExternalStorageDirectory() + File.separator + "test.jpg";

        try {
            FileInfo fileInfo1 = new FileInfo(1, file1, FileUtils.getFileSizes(new File(file1)));
            FileInfo fileInfo2 = new FileInfo(2, file2, FileUtils.getFileSizes(new File(file2)));
            mAllFileInfos.add(fileInfo1);
            mAllFileInfos.add(fileInfo2);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //初始化开启热点视图
        initOpenHotspotLayout();
        //请求权限，开启热点
        requestPermission(PERMISSION_CREATE_HOTSPOT, PERMISSION_REQ_CREATE_HOTSPOT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mIsPermissionGranted && mHotSpotBroadcaseReceiver == null) {
            //注册便携热点状态接收器
            registerHotSpotReceiver();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mHotSpotBroadcaseReceiver != null) {
            //反注册便携热点状态接收器
            unregisterHotSpotReceiver();
        }
    }

    @Override
    public void onBackPressed() {
        if(hasFileSending()) {
            showTipsDialog("文件正在发送，是否退出？", "是", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finishActivity();
                }
            }, "否", null);
        } else {
            finishActivity();
        }
    }

    @Override
    protected void permissionSuccess(int requestCode) {
        super.permissionSuccess(requestCode);
        if(requestCode == PERMISSION_REQ_CREATE_HOTSPOT) {
            //获取创建便携热点权限成功
            mIsPermissionGranted = true;
        }
    }

    @Override
    protected void permissionFail(int requestCode) {
        super.permissionFail(requestCode);
        if(requestCode == PERMISSION_REQ_CREATE_HOTSPOT) {
            //获取创建便携热点权限失败
            mIsPermissionGranted = false;
        }
    }

    /**
     * 初始化开启热点视图
     */
    private void initOpenHotspotLayout() {
        View view = vsOpenHotspot.inflate();
        etSSID = (EditText) view.findViewById(R.id.et_open_hotspot_ssid);
        etPassword = (EditText) view.findViewById(R.id.et_open_hotspot_password);
    }

    /**
     * 初始化发送文件视图
     */
    private void initSendFilesLayout() {
        vsOpenHotspot.setVisibility(View.GONE);
        mSendFileRecyclerView.setVisibility(View.VISIBLE);

        //设置适配器
        List<Map.Entry<String, FileInfo>> fileInfos = AppContext.getAppContext().getSendFileInfoMap();
        Collections.sort(fileInfos, Consts.DEFAULT_COMPARATOR);
        mSendFileAdapter = new CommonAdapter<Map.Entry<String, FileInfo>>(getContext(), R.layout.item_file_transfer, fileInfos) {
            @Override
            protected void convert(ViewHolder holder, Map.Entry<String, FileInfo> fileInfoMap, int position) {
                FileInfo fileInfo = fileInfoMap.getValue();
                //文件路径
                holder.setText(R.id.tv_item_file_transfer_file_path, fileInfo.getFilePath());
                //文件大小
                holder.setText(R.id.tv_item_file_transfer_size, FileUtils.FormetFileSize(fileInfo.getSize()));
                //文件发送状态
                if(fileInfo.getProgress() >= 100) {
                    holder.setText(R.id.tv_item_file_transfer_status, "发送完毕");
                } else if(fileInfo.getProgress() == 0) {
                    holder.setText(R.id.tv_item_file_transfer_status, "准备发送");
                } else if(fileInfo.getProgress() < 100) {
                    holder.setText(R.id.tv_item_file_transfer_status, "正在发送");
                } else {
                    holder.setText(R.id.tv_item_file_transfer_status, "发送失败");
                }
                //文件发送进度
                ProgressBar progressBar = holder.getView(R.id.pb_item_file_transfer);
                progressBar.setProgress(fileInfo.getProgress());
            }
        };
        mSendFileRecyclerView.setAdapter(mSendFileAdapter);
        //设置ListView样式
        mSendFileRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        //分割线
        mSendFileRecyclerView.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));
    }

    /**
     * 开启便携热点
     * @param view
     */
    public void openHotspot(View view) {
        String ssid = etSSID.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if(isEmptyString(ssid)) {
            ssid = Build.MODEL;
        }

        //是否有权限
        if(mIsPermissionGranted) {
            //开启热点前，先关闭WiFi，如有其他热点已开启，先关闭
            ApMgr.closeWifi(getContext());
            if(ApMgr.isApOn(getContext())) {
                ApMgr.closeAp(getContext());
            }

            //注册便携热点状态接收器
            registerHotSpotReceiver();

            //以手机型号为SSID，开启热点
            boolean isSuccess = ApMgr.openAp(getContext(), ssid, password);
            if(!isSuccess) {
                setStatus("创建热点失败");
            }
        } else {
            showTipsDialog("获取权限失败，开启热点", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
        }
    }

    /**
     * 注册便携热点状态接收器
     */
    private void registerHotSpotReceiver() {
        if(mHotSpotBroadcaseReceiver == null) {
            mHotSpotBroadcaseReceiver = new HotSpotBroadcaseReceiver() {
                @Override
                public void onHotSpotEnabled() {
                    //热点成功开启
                    if(!mIsInitialized) {
                        mIsInitialized = true;
                        setStatus("成功开启热点...");

                        tvStatus.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                setStatus("正在等待连接...");

                                //等待接收端连接
                                Runnable mUdpServerRunnable = receiveInitSuccessOrderRunnable();
                                AppContext.MAIN_EXECUTOR.execute(mUdpServerRunnable);
                            }
                        }, 2000);
                    }
                }
            };
        }
        IntentFilter filter = new IntentFilter(HotSpotBroadcaseReceiver.ACTION_HOTSPOT_STATE_CHANGED);
        registerReceiver(mHotSpotBroadcaseReceiver, filter);
    }

    /**
     * 反注册便携热点状态接收器
     */
    private void unregisterHotSpotReceiver() {
        if(mHotSpotBroadcaseReceiver != null) {
            unregisterReceiver(mHotSpotBroadcaseReceiver);
            mHotSpotBroadcaseReceiver = null;
        }
    }

    /**
     * 等待接收端发送初始化完成指令线程
     * @return
     */
    private Runnable receiveInitSuccessOrderRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    //开始接收接收端发来的指令
                    receiveInitSuccessOrder(Consts.DEFAULT_SERVER_UDP_PORT);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    /**
     * 等待接收端发送初始化完成指令，向其发送文件列表
     * @param serverPort
     * @throws Exception
     */
    private void receiveInitSuccessOrder(int serverPort) throws Exception {
        //确保WiFi连接后获取正确IP地址
        int tryCount = 0;
        String localIpAddress = ApMgr.getHotspotLocalIpAddress(getContext());
        while (localIpAddress.equals(Consts.DEFAULT_UNKNOW_IP) && tryCount < Consts.DEFAULT_TRY_COUNT) {
            Thread.sleep(1000);
            localIpAddress = ApMgr.getHotspotLocalIpAddress(getContext());
            tryCount ++;
        }

        /** 这里使用UDP发送和接收指令 */
        mDatagramSocket = new DatagramSocket(serverPort);
        while (true) {
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            mDatagramSocket.receive(receivePacket);
            String response = new String(receivePacket.getData()).trim();
            if(isNotEmptyString(response)) {
                LogUtils.e("接收到的消息 -------->>>" + response);
                if(response.equals(Consts.MSG_FILE_RECEIVER_INIT_SUCCESS)) {
                    //初始化成功指令
                    mHandler.sendEmptyMessage(MSG_FILE_RECEIVER_INIT_SUCCESS);
                    //发送文件列表
                    InetAddress inetAddress = receivePacket.getAddress();
                    int port = receivePacket.getPort();
                    //通过UDP发送文件列表给接收端
                    sendFileInfoListToFileReceiverWithUdp(inetAddress, port);
                } else if(response.equals(Consts.MSG_START_SEND)) {
                    //开始发送指令
                    initSenderServer();
                } else {
                    //接收端发来的待发送文件列表
                    parseFileInfo(response);
                }
            }
        }
    }

    /**
     * 通过UDP发送文件列表给接收端
     * @param ipAddress IP地址
     * @param serverPort 端口号
     */
    private void sendFileInfoListToFileReceiverWithUdp(InetAddress ipAddress, int serverPort) {
        if(!isEmptyList(mAllFileInfos)) {
            String jsonStr = FileInfo.toJsonStr(mAllFileInfos);
            DatagramPacket sendFileInfoPacket = new DatagramPacket(jsonStr.getBytes(), jsonStr.getBytes().length, ipAddress, serverPort);
            try {
                //发送文件列表
                mDatagramSocket.send(sendFileInfoPacket);
                LogUtils.i("发送消息 --------->>>" + jsonStr + "=== Success!");
                mHandler.obtainMessage(MSG_SET_STATUS, "成功发送文件列表...").sendToTarget();
            } catch (IOException e) {
                e.printStackTrace();
                LogUtils.i("发送消息 --------->>>" + jsonStr + "=== 失败！");
            }
        }
    }

    /**
     * 初始化发送端服务，开始发送文件
     */
    private void initSenderServer() {
        mSenderServerRunnable = new SenderServerRunnable();
        new Thread(mSenderServerRunnable).start();
    }

    /**
     * 将字符串解析成FileInfo
     * @param jsonStr
     */
    private void parseFileInfo(String jsonStr) {
        if(isNotEmptyString(jsonStr)) {
            List<FileInfo> fileInfoList = FileInfo.toObjectList(jsonStr);
            if(!isEmptyList(fileInfoList)) {
                for(FileInfo fileInfo : fileInfoList) {
                    if(fileInfo != null && isNotEmptyString(fileInfo.getFilePath())) {
                        fileInfo.setPosition(fileInfoList.indexOf(fileInfo));
                        AppContext.getAppContext().addSendFileInfo(fileInfo);
                        mHandler.sendEmptyMessage(MSG_UPDATE_ADAPTER);
                    }
                }
            }
        }
    }

    /**
     * 设置状态
     * @param status
     */
    private void setStatus(String status) {
        tvStatus.setText(status);
        LogUtils.e(status);
    }

    /**
     * 更新文件接收进度
     * @param position 文件索引
     * @param progress 接收进度
     */
    private void updateProgress(int position, int progress) {
        if(position < 0 || position >= mSendFileAdapter.getItemCount()) {
            return;
        }

        FileInfo fileInfo = mSendFileAdapter.getDatas().get(position).getValue();
        fileInfo.setProgress(progress);
        mSendFileAdapter.notifyItemChanged(position);

        if(position == AppContext.getAppContext().getSendFileInfoMap().size() - 1 && progress == 100) {
            toast("所有文件发送完毕");
            LogUtils.e("所有文件发送完毕");
        }
    }

    /**
     * 是否还有文件在发送
     * @return
     */
    private boolean hasFileSending() {
        for(FileSender fileSender : mFileSenderList) {
            if(fileSender != null && fileSender.isRunning()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 关闭此Activity
     */
    private void finishActivity() {
        //关闭UDP Socket连接
        closeUdpSocket();

        //停止所有文件发送任务
        stopAllFileSendingTask();

        //关闭发送端Socket
        if(mSenderServerRunnable != null) {
            mSenderServerRunnable.closeServerSocket();
            mSenderServerRunnable = null;
        }

        //关闭便携热点
        ApMgr.closeAp(getContext());

        //清除待发送的文件列表
        AppContext.getAppContext().clearSendFileInfoMap();

        finish();
    }

    /**
     * 停止所有文件发送任务
     */
    private void stopAllFileSendingTask() {
        for(FileSender fileSender : mFileSenderList) {
            if(fileSender != null) {
                fileSender.stop();
            }
        }
    }

    /**
     * 关闭UDP Socket
     */
    private void closeUdpSocket() {
        if(mDatagramSocket != null) {
            mDatagramSocket.disconnect();
            mDatagramSocket.close();
            mDatagramSocket = null;
        }
    }

    /**
     * 文件发送线程
     */
    private class SenderServerRunnable implements Runnable {

        private ServerSocket mServerSocket;

        @Override
        public void run() {
            try {
                //获取待发送的文件列表数据，按position索引排序
                List<Map.Entry<String, FileInfo>> fileInfoList = AppContext.getAppContext().getSendFileInfoMap();
                Collections.sort(fileInfoList, Consts.DEFAULT_COMPARATOR);
                mServerSocket = new ServerSocket(Consts.DEFAULT_FILE_RECEIVE_SERVER_PORT);
                //逐个文件进行发送
                for(final Map.Entry<String, FileInfo> fileInfoMap : fileInfoList) {
                    final FileInfo fileInfo = fileInfoMap.getValue();
                    Socket socket = mServerSocket.accept();
                    FileSender fileSender = new FileSender(getContext(), socket, fileInfo);
                    fileSender.setOnSendListener(new FileSender.OnSendListener() {
                        @Override
                        public void onStart() {
                            mHandler.obtainMessage(MSG_SET_STATUS, "开始发送"+ FileUtils.getFileName(fileInfo.getFilePath())).sendToTarget();
                        }

                        @Override
                        public void onProgress(long progress, long total) {
                            //更新发送进度视图
                            int i_progress = (int) (progress * 100 / total);
                            LogUtils.e("正在发送：" + fileInfo.getFilePath() + "\n当前进度：" + i_progress);

                            Message msg = new Message();
                            msg.what = MSG_UPDATE_PROGRESS;
                            msg.arg1 = fileInfo.getPosition();
                            msg.arg2 = i_progress;
                            mHandler.sendMessage(msg);
                        }

                        @Override
                        public void onSuccess(FileInfo fileInfo) {
                            //发送成功
                            mHandler.obtainMessage(MSG_SET_STATUS, "文件：" + FileUtils.getFileName(fileInfo.getFilePath()) + "发送成功").sendToTarget();
                            fileInfo.setResult(FileInfo.FLAG_SUCCESS);
                            AppContext.getAppContext().updateSendFileInfo(fileInfo);

                            Message msg = new Message();
                            msg.what = MSG_UPDATE_PROGRESS;
                            msg.arg1 = fileInfo.getPosition();
                            msg.arg2 = 100;
                            mHandler.sendMessage(msg);
                        }

                        @Override
                        public void onFailure(Throwable throwable, FileInfo fileInfo) {
                            //发送失败
                            mHandler.obtainMessage(MSG_SET_STATUS, "文件：" + FileUtils.getFileName(fileInfo.getFilePath()) + "发送失败").sendToTarget();
                            fileInfo.setResult(FileInfo.FLAG_FAILURE);
                            AppContext.getAppContext().updateSendFileInfo(fileInfo);

                            Message msg = new Message();
                            msg.what = MSG_UPDATE_PROGRESS;
                            msg.arg1 = fileInfo.getPosition();
                            msg.arg2 = -1;
                            mHandler.sendMessage(msg);
                        }
                    });
                    //添加到线程池执行
                    mFileSenderList.add(fileSender);
                    AppContext.FILE_SENDER_EXECUTOR.execute(fileSender);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * 关闭Socket连接
         */
        public void closeServerSocket() {
            if(mServerSocket != null) {
                try {
                    mServerSocket.close();
                    mServerSocket = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
