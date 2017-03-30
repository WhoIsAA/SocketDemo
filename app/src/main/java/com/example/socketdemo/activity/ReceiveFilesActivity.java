package com.example.socketdemo.activity;

import android.content.DialogInterface;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.socketdemo.R;
import com.example.socketdemo.base.AppContext;
import com.example.socketdemo.base.BaseActivity;
import com.example.socketdemo.base.BaseTransfer;
import com.example.socketdemo.bean.FileInfo;
import com.example.socketdemo.common.Consts;
import com.example.socketdemo.common.FileReceiver;
import com.example.socketdemo.common.SpaceItemDecoration;
import com.example.socketdemo.receiver.WifiBroadcaseReceiver;
import com.example.socketdemo.utils.FileUtils;
import com.example.socketdemo.utils.LogUtils;
import com.example.socketdemo.utils.NetUtils;
import com.example.socketdemo.wifitools.WifiMgr;
import com.zhy.adapter.recyclerview.CommonAdapter;
import com.zhy.adapter.recyclerview.MultiItemTypeAdapter;
import com.zhy.adapter.recyclerview.base.ViewHolder;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by AA on 2017/3/28.
 */
public class ReceiveFilesActivity extends BaseActivity implements MultiItemTypeAdapter.OnItemClickListener {

    /**
     * 接收端初始化完毕
     */
    public static final int MSG_FILE_RECEIVER_INIT_SUCCESS = 0x661;

    /**
     * 更新适配器
     */
    public static final int MSG_UPDATE_ADAPTER = 0x662;

    /**
     * 发送选中要接收的文件列表
     */
    public static final int MSG_SEND_RECEIVE_FILE_LIST = 0x663;

    /**
     * 添加接收文件
     */
    public static final int MSG_ADD_FILEINFO = 0x664;

    /**
     * 更新进度条
     */
    public static final int MSG_UPDATE_PROGRESS = 0x665;

    /**
     * 设置当前状态
     */
    public static final int MSG_SET_STATUS = 0x666;

    @BindView(R.id.tv_receive_files_status)
    TextView tvStatus;

    @BindView(R.id.btn_receive_files)
    Button btnSendFileList;

    @BindView(R.id.rv_receive_files_choose_hotspot)
    RecyclerView mChooseHotspotRecyclerView;
    private CommonAdapter<ScanResult> mChooseHotspotAdapter;

    @BindView(R.id.rv_receive_files)
    RecyclerView mReceiveFilesRecyclerView;
    private CommonAdapter<Map.Entry<String, FileInfo>> mReceiveFilesAdapter;

    /**
     * 选中待发送的文件列表
     */
    private List<FileInfo> mSendFileInfos = new ArrayList<>();

    /**
     * 接收文件线程列表数据
     */
    private List<FileReceiver> mFileReceiverList = new ArrayList<>();

    /**
     * WiFi工具类
     */
    private WifiMgr mWifiMgr;

    /**
     * 扫描到的可用WiFi列表
     */
    private List<ScanResult> mScanResults = new ArrayList<>();

    /**
     * 用来接收文件的Socket
     */
    private Socket mClientSocket;

    /**
     * UDP Socket
     */
    private DatagramSocket mDatagramSocket;

    /**
     * 接收文件线程
     */
    private ReceiveServerRunnable mReceiveServerRunnable;

    /**
     * 是否已发送初始化指令
     */
    private boolean mIsSendInitOrder;

    /**
     * 获取权限是否成功
     */
    private boolean mIsPermissionGranted;

    /**
     * 当前所选WiFi的SSID
     */
    private String mSelectedSSID;


    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_FILE_RECEIVER_INIT_SUCCESS) {
                //告知发送端，接收端初始化完毕
                sendInitSuccessToFileSender();
            } else if (msg.what == MSG_UPDATE_ADAPTER) {
                //更新适配器
                setupReceiveFilesAdapter();
            } else if (msg.what == MSG_SEND_RECEIVE_FILE_LIST) {
                //发送选中要接收的文件列表
                sendFileListToFileSender();
            } else if (msg.what == MSG_ADD_FILEINFO) {
                //添加接收文件
                mReceiveFilesAdapter.notifyDataSetChanged();
            } else if (msg.what == MSG_UPDATE_PROGRESS) {
                //更新进度条
                int position = msg.arg1;
                int progress = msg.arg2;
                if (position >= 0 && position < mReceiveFilesAdapter.getItemCount()) {
                    updateProgress(position, progress);
                }
            } else if (msg.what == MSG_SET_STATUS) {
                //设置当前状态
                setStatus(msg.obj.toString());
            }
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_receive_files;
    }

    @Override
    protected String getTitleText() {
        return "接收文件";
    }

    @Override
    protected void initData() {
        //请求权限
        requestPermission(PERMISSION_CONNECT_WIFI, PERMISSION_REQ_CONNECT_WIFI);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mIsPermissionGranted && mWifiBroadcaseReceiver == null) {
            registerWifiReceiver();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mWifiBroadcaseReceiver != null) {
            unregisterWifiReceiver();
        }
    }

    @Override
    public void onBackPressed() {
        if(hasFileReceiving()) {
            showTipsDialog("文件正在接收，是否退出？", "是", new DialogInterface.OnClickListener() {
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
        if(requestCode == PERMISSION_REQ_CONNECT_WIFI) {
            //权限请求成功
            mIsPermissionGranted = true;

            //开启WiFi，监听WiFi广播
            registerWifiReceiver();
            mWifiMgr = new WifiMgr(getContext());
            if(mWifiMgr.isWifiEnabled()) {
                setStatus("正在扫描可用WiFi...");
                mWifiMgr.startScan();
            } else {
                mWifiMgr.openWifi();
            }
        }
    }

    @Override
    protected void permissionFail(int requestCode) {
        super.permissionFail(requestCode);
        if(requestCode == PERMISSION_REQ_CONNECT_WIFI) {
            //权限请求失败
            mIsPermissionGranted = false;
            showTipsDialog("WiFi权限获取失败", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    onBackPressed();
                }
            });
        }
    }

    /**
     * 注册监听WiFi操作的系统广播
     */
    private void registerWifiReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(mWifiBroadcaseReceiver, filter);
    }

    /**
     * 反注册WiFi相关的系统广播
     */
    private void unregisterWifiReceiver() {
        if (mWifiBroadcaseReceiver != null) {
            unregisterReceiver(mWifiBroadcaseReceiver);
            mWifiBroadcaseReceiver = null;
        }
    }

    /**
     * 开启文件接收服务
     */
    private void initReceiverServer() {
        mReceiveServerRunnable = new ReceiveServerRunnable();
        new Thread(mReceiveServerRunnable).start();
    }

    /**
     * 告知发送端初始化完毕
     */
    private void sendInitSuccessToFileSender() {
        new Thread() {
            @Override
            public void run() {
                try {
                    //确保WiFi连接后获取正确IP地址
                    int tryCount = 0;
                    String serverIp = mWifiMgr.getIpAddressFromHotspot();
                    while (serverIp.equals(Consts.DEFAULT_UNKNOW_IP) && tryCount < Consts.DEFAULT_TRY_COUNT) {
                        Thread.sleep(1000);
                        serverIp = mWifiMgr.getIpAddressFromHotspot();
                        tryCount ++;
                    }

                    //是否可以ping通指定IP地址
                    tryCount = 0;
                    while (!NetUtils.pingIpAddress(serverIp) && tryCount < Consts.DEFAULT_TRY_COUNT) {
                        Thread.sleep(500);
                        LogUtils.i("Try to ping ------" + serverIp + " - " + tryCount);
                        tryCount ++;
                    }

                    //创建UDP通信
                    if(mDatagramSocket == null) {
                        //解决：java.net.BindException: bind failed: EADDRINUSE (Address already in use)
                        mDatagramSocket = new DatagramSocket(null);
                        mDatagramSocket.setReuseAddress(true);
                        mDatagramSocket.bind(new InetSocketAddress(Consts.DEFAULT_SERVER_UDP_PORT));
                    }
                    //发送初始化完毕指令
                    InetAddress ipAddress = InetAddress.getByName(serverIp);
                    byte[] sendData = Consts.MSG_FILE_RECEIVER_INIT_SUCCESS.getBytes(BaseTransfer.UTF_8);
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, Consts.DEFAULT_SERVER_UDP_PORT);
                    mDatagramSocket.send(sendPacket);
                    LogUtils.i("发送消息 ------->>>" + Consts.MSG_FILE_RECEIVER_INIT_SUCCESS);

                    //接收文件列表
                    while (true) {
                        byte[] receiveData = new byte[1024];
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        mDatagramSocket.receive(receivePacket);
                        String response = new String(receivePacket.getData()).trim();
                        if(isNotEmptyString(response)) {
                            //发送端发来的文件列表
                            LogUtils.e("接收到的消息 -------->>>" + response);
                            parseFileInfoList(response);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * 设置WiFi列表适配器
     */
    private void setupWifiAdapter() {
        if(mChooseHotspotAdapter == null) {
            mChooseHotspotAdapter = new CommonAdapter<ScanResult>(getContext(), R.layout.item_choose_hotspot, mScanResults) {
                @Override
                protected void convert(ViewHolder holder, ScanResult scanResult, int position) {
                    holder.setText(R.id.tv_item_choose_hotspot_ssid, scanResult.SSID);
                    holder.setText(R.id.tv_item_choose_hotspot_level, String.format(getString(R.string.item_level), scanResult.level));
                }
            };
            //设置点击事件
            mChooseHotspotAdapter.setOnItemClickListener(this);
            //设置适配器
            mChooseHotspotRecyclerView.setAdapter(mChooseHotspotAdapter);
            //设置间隔
            mChooseHotspotRecyclerView.addItemDecoration(new SpaceItemDecoration(10));
            mChooseHotspotRecyclerView.setVisibility(View.VISIBLE);
        } else {
            mChooseHotspotAdapter.notifyDataSetChanged();
        }
    }

    /**
     * 设置接收文件列表适配器
     */
    private void setupReceiveFilesAdapter() {
        List<Map.Entry<String, FileInfo>> fileInfos = AppContext.getAppContext().getReceiverFileInfoMap();
        Collections.sort(fileInfos, Consts.DEFAULT_COMPARATOR);
        //设置适配器
        mReceiveFilesAdapter = new CommonAdapter<Map.Entry<String, FileInfo>>(getContext(), R.layout.item_files_selector, fileInfos) {
            @Override
            protected void convert(ViewHolder holder, Map.Entry<String, FileInfo> fileInfoMap, int position) {
                final FileInfo fileInfo = fileInfoMap.getValue();
                //文件路径
                holder.setText(R.id.tv_item_files_selector_file_path, fileInfo.getFilePath());
                //文件大小
                holder.setText(R.id.tv_item_files_selector_size, FileUtils.FormetFileSize(fileInfo.getSize()));
                //文件接收状态
                if(fileInfo.getProgress() >= 100) {
                    holder.setText(R.id.tv_item_files_selector_status, "接收完毕");
                } else if(fileInfo.getProgress() == 0) {
                    holder.setText(R.id.tv_item_files_selector_status, "准备接收");
                } else if(fileInfo.getProgress() < 100) {
                    holder.setText(R.id.tv_item_files_selector_status, "正在接收");
                } else {
                    holder.setText(R.id.tv_item_files_selector_status, "接收失败");
                }
                //文件接收进度
                ProgressBar progressBar = holder.getView(R.id.pb_item_files_selector);
                progressBar.setProgress(fileInfo.getProgress());

                //选中文件
                CheckBox checkBox = holder.getView(R.id.cb_item_files_selector);
                checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if(isChecked) {
                            mSendFileInfos.add(fileInfo);
                        } else {
                            mSendFileInfos.remove(fileInfo);
                        }
                        //选中的文件个数大于零才可点击底部按钮
                        btnSendFileList.setEnabled(mSendFileInfos.size() > 0);
                    }
                });
            }
        };
        mReceiveFilesRecyclerView.setAdapter(mReceiveFilesAdapter);
        //设置ListView样式
        mReceiveFilesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        //分割线
        mReceiveFilesRecyclerView.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));
    }

    /**
     * 更新文件接收进度
     * @param position 文件索引
     * @param progress 接收进度
     */
    private void updateProgress(int position, int progress) {
        FileInfo fileInfo = mReceiveFilesAdapter.getDatas().get(position).getValue();
        fileInfo.setProgress(progress);
        mReceiveFilesAdapter.notifyItemChanged(position);

        if(position == AppContext.getAppContext().getReceiverFileInfoMap().size() - 1 && progress == 100) {
            toast("所有文件接收完毕");
            LogUtils.e("所有文件接收完毕");
        }
    }

    /**
     * 将字符串解析成FileInfo列表
     * @param jsonStr
     */
    private void parseFileInfoList(String jsonStr) {
        if(isNotEmptyString(jsonStr)) {
            List<FileInfo> fileInfos = FileInfo.toObjectList(jsonStr);
            if(!isEmptyList(fileInfos)) {
                for(FileInfo fileInfo : fileInfos) {
                    if(fileInfo != null && isNotEmptyString(fileInfo.getFilePath())) {
                        AppContext.getAppContext().addReceiverFileInfo(fileInfo);
                    }
                }
                //更新适配器
                mHandler.sendEmptyMessage(MSG_UPDATE_ADAPTER);
            }
        }
    }

    /**
     * 发送选中的文件列表给发送端
     */
    private void sendFileListToFileSender() {
        new Thread() {
            @Override
            public void run() {
                try {
                    //确保WiFi连接后获取正确IP地址
                    String serverIp = mWifiMgr.getIpAddressFromHotspot();
                    if(mDatagramSocket == null) {
                        //解决：java.net.BindException: bind failed: EADDRINUSE (Address already in use)
                        mDatagramSocket = new DatagramSocket(null);
                        mDatagramSocket.setReuseAddress(true);
                        mDatagramSocket.bind(new InetSocketAddress(Consts.DEFAULT_SERVER_UDP_PORT));
                    }

                    //发送选中的文件列表
                    InetAddress ipAddress = InetAddress.getByName(serverIp);
                    String jsonStr = FileInfo.toJsonStr(mSendFileInfos);
                    DatagramPacket sendPacket = new DatagramPacket(jsonStr.getBytes(), jsonStr.getBytes().length, ipAddress, Consts.DEFAULT_SERVER_UDP_PORT);
                    mDatagramSocket.send(sendPacket);
                    LogUtils.i("Send Msg To FileSender ------->>>" + jsonStr);

                    //发送开始发送文件指令
                    byte[] sendData = Consts.MSG_START_SEND.getBytes(BaseTransfer.UTF_8);
                    DatagramPacket sendPacket2 = new DatagramPacket(sendData, sendData.length, ipAddress, Consts.DEFAULT_SERVER_UDP_PORT);
                    mDatagramSocket.send(sendPacket2);
                    LogUtils.i("Send Msg To FileSender ------->>>" + sendData);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * 显示WiFi密码输入框
     * @param title
     * @param listener
     */
    protected void showDialogWithEditText(String title, final OnWifiPasswordConfirmListener listener) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_dialog_with_edittext, null);
        final EditText etPassword = (EditText) dialogView.findViewById(R.id.et_dialog_with_edittext);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setView(dialogView);
        builder.setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (listener != null) {
                    listener.onConfirm(etPassword.getText().toString().trim());
                }
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.create().show();
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
     * 是否还有文件在接收
     * @return
     */
    private boolean hasFileReceiving() {
        for(FileReceiver fileReceiver : mFileReceiverList) {
            if(fileReceiver != null && fileReceiver.isRunning()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 停止所有文件发送任务
     */
    private void stopAllFileReceivingTask() {
        for(FileReceiver fileReceiver : mFileReceiverList) {
            if(fileReceiver != null) {
                fileReceiver.stop();
            }
        }
    }

    /**
     * 关闭此Activity
     */
    private void finishActivity() {
        //断开UDP Socket
        closeUdpSocket();

        //停止所有文件接收任务
        stopAllFileReceivingTask();

        //断开接收文件的Socket
        closeClientSocket();

        //清除WiFi网络
        mWifiMgr.clearWifiConfig();

        //清空接收文件列表
        AppContext.getAppContext().clearReceiverFileInfoMap();

        finish();
    }

    /**
     * 断开接收文件的Socket
     */
    private void closeClientSocket() {
        if(mClientSocket != null) {
            try {
                mClientSocket.close();
                mClientSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
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

    @OnClick(R.id.btn_receive_files)
    public void sendReceiveFileListToFileSender() {
        //将选择的文件列表发给发送端，开始接收文件
        AppContext.getAppContext().clearReceiverFileInfoMap();
        for(FileInfo fileInfo : mSendFileInfos) {
            fileInfo.setPosition(mSendFileInfos.indexOf(fileInfo));
            AppContext.getAppContext().addReceiverFileInfo(fileInfo);
        }
        setupReceiveFilesAdapter();
        initReceiverServer();
    }

    @Override
    public void onItemClick(View view, RecyclerView.ViewHolder holder, int position) {
        if(position < mChooseHotspotAdapter.getItemCount() && position >= 0) {
            //获取当前点击WiFi的SSID
            ScanResult scanResult = mChooseHotspotAdapter.getDatas().get(position);
            mSelectedSSID = scanResult.SSID;

            if((scanResult.capabilities != null && !scanResult.capabilities.equals(WifiMgr.NO_PASSWORD)) || (scanResult.capabilities != null && !scanResult.capabilities.equals(WifiMgr.NO_PASSWORD_WPS))){
                //弹出密码输入框
                showDialogWithEditText(mSelectedSSID, new OnWifiPasswordConfirmListener() {
                    @Override
                    public void onConfirm(String password) {
                        //使用密码连接WiFi
                        if(isNotEmptyString(password)) {
                            try {
                                setStatus("正在连接Wifi...");
                                mWifiMgr.connectWifi(mSelectedSSID, password, mScanResults);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } else {
                            toast("密码不能为空");
                        }
                    }
                });
            } else {
                //连接免密码WiFi
                try {
                    setStatus("正在连接Wifi...");
                    mWifiMgr.connectWifi(mSelectedSSID, "", mScanResults);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public boolean onItemLongClick(View view, RecyclerView.ViewHolder holder, int position) {
        return false;
    }

    /**
     * WiFi广播接收器
     */
    private WifiBroadcaseReceiver mWifiBroadcaseReceiver = new WifiBroadcaseReceiver() {
        @Override
        public void onWifiEnabled() {
            //WiFi已开启，开始扫描可用WiFi
            setStatus("正在扫描可用WiFi...");
            mWifiMgr.startScan();
        }

        @Override
        public void onWifiDisabled() {
            //WiFi已关闭，清除可用WiFi列表
            mSelectedSSID = "";
            mScanResults.clear();
            setupWifiAdapter();
        }

        @Override
        public void onScanResultsAvailable(List<ScanResult> scanResults) {
            //扫描周围可用WiFi成功，设置可用WiFi列表
            mScanResults.clear();
            mScanResults.addAll(scanResults);
            setupWifiAdapter();
        }

        @Override
        public void onWifiConnected(String connectedSSID) {
            //判断指定WiFi是否连接成功
            if (connectedSSID.equals(mSelectedSSID) && !mIsSendInitOrder) {
                //连接成功
                setStatus("Wifi连接成功...");
                //显示发送列表，隐藏WiFi选择列表
                mChooseHotspotRecyclerView.setVisibility(View.GONE);
                mReceiveFilesRecyclerView.setVisibility(View.VISIBLE);

                //告知发送端，接收端初始化完毕
                mHandler.sendEmptyMessage(MSG_FILE_RECEIVER_INIT_SUCCESS);
                mIsSendInitOrder = true;
            } else {
//                //连接成功的不是设备WiFi，清除该WiFi，重新扫描周围WiFi
//                LogUtils.e("连接到错误WiFi，正在断开重连...");
//                mWifiMgr.disconnectWifi(connectedSSID);
//                mWifiMgr.startScan();
            }
        }

        @Override
        public void onWifiDisconnected() {

        }
    };

    /**
     * ServerSocket启动线程
     */
    private class ReceiveServerRunnable implements Runnable {

        @Override
        public void run() {
            try {
                //发送选择接收的文件
                mHandler.sendEmptyMessage(MSG_SEND_RECEIVE_FILE_LIST);

                Thread.sleep(3000);
                //开始接收文件
                String serverIp = mWifiMgr.getIpAddressFromHotspot();
                List<Map.Entry<String, FileInfo>> fileInfoList = AppContext.getAppContext().getReceiverFileInfoMap();
                Collections.sort(fileInfoList, Consts.DEFAULT_COMPARATOR);
                for(final Map.Entry<String, FileInfo> fileInfoMap : fileInfoList) {
                    //连接发送端，逐个文件进行接收
                    final int position = fileInfoList.indexOf(fileInfoMap);
                    mClientSocket = new Socket(serverIp, Consts.DEFAULT_FILE_RECEIVE_SERVER_PORT);
                    FileReceiver fileReceiver = new FileReceiver(mClientSocket, fileInfoMap.getValue());
                    fileReceiver.setOnReceiveListener(new FileReceiver.OnReceiveListener() {
                        @Override
                        public void onStart() {
                            mHandler.obtainMessage(MSG_SET_STATUS, "开始接收"+ FileUtils.getFileName(fileInfoMap.getValue().getFilePath())).sendToTarget();
                        }

                        @Override
                        public void onProgress(FileInfo fileInfo, long progress, long total) {
                            //更新接收进度视图
                            int i_progress = (int) (progress * 100 / total);
                            LogUtils.e("正在接收：" + fileInfo.getFilePath() + "\n当前进度：" + i_progress);

                            Message msg = new Message();
                            msg.what = MSG_UPDATE_PROGRESS;
                            msg.arg1 = position;
                            msg.arg2 = i_progress;
                            mHandler.sendMessage(msg);
                        }

                        @Override
                        public void onSuccess(FileInfo fileInfo) {
                            //接收成功
                            mHandler.obtainMessage(MSG_SET_STATUS, "文件：" + FileUtils.getFileName(fileInfo.getFilePath()) + "接收成功").sendToTarget();
                            fileInfo.setResult(FileInfo.FLAG_SUCCESS);
                            AppContext.getAppContext().updateReceiverFileInfo(fileInfo);

                            Message msg = new Message();
                            msg.what = MSG_UPDATE_PROGRESS;
                            msg.arg1 = position;
                            msg.arg2 = 100;
                            mHandler.sendMessage(msg);
                        }

                        @Override
                        public void onFailure(Throwable throwable, FileInfo fileInfo) {
                            if(fileInfo != null) {
                                //接收失败
                                mHandler.obtainMessage(MSG_SET_STATUS, "文件：" + FileUtils.getFileName(fileInfo.getFilePath()) + "接收失败").sendToTarget();
                                fileInfo.setResult(FileInfo.FLAG_FAILURE);
                                AppContext.getAppContext().updateReceiverFileInfo(fileInfo);

                                Message msg = new Message();
                                msg.what = MSG_UPDATE_PROGRESS;
                                msg.arg1 = position;
                                msg.arg2 = -1;
                                mHandler.sendMessage(msg);
                            }
                        }
                    });

                    //加入线程池执行
                    mFileReceiverList.add(fileReceiver);
                    AppContext.getAppContext().MAIN_EXECUTOR.execute(fileReceiver);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private interface OnWifiPasswordConfirmListener {
        void onConfirm(String password);
    }
}
