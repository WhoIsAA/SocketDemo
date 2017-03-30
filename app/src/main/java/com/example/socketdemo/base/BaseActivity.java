package com.example.socketdemo.base;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.ViewStub;
import android.widget.TextView;
import android.widget.Toast;

import com.example.socketdemo.R;
import com.example.socketdemo.utils.LogUtils;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;

/**
 * Activity基类
 */
public abstract class BaseActivity extends AppCompatActivity {

    /** WiFi热点连接和创建权限请求码 */
    protected static final int PERMISSION_REQ_CONNECT_WIFI = 3020;

    /** 创建便携热点权限请求码 */
    protected static final int PERMISSION_REQ_CREATE_HOTSPOT = 3021;

    /** 连接WiFi所需权限 */
    protected static final String[] PERMISSION_CONNECT_WIFI = new String[] {
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE};

    /** 创建便携热点所需权限 */
    protected static final String[] PERMISSION_CREATE_HOTSPOT = new String[] {
            Manifest.permission.WRITE_SETTINGS,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE};

    /** 标题栏相关控件 */
    private Toolbar mToolbar;
    private TextView tvTitle;
    /** 内容视图控件 */
    private ViewStub mViewStub;

    /**
     * 当前Activity上下文
     */
    private Context mContext;

    /**
     * 权限请求码
     */
    private int mRequestCode;

    /**
     * 获取当前Activity视图ID
     */
    protected abstract int getLayoutId();

    /**
     * 获取当前Activity标题
     * @return
     */
    protected abstract String getTitleText();

    /**
     *  初始化数据
     */
    protected abstract void initData();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;

        setContentView(R.layout.activity_base);
        mToolbar = (Toolbar) findViewById(R.id.base_toolbar);
        tvTitle = (TextView) findViewById(R.id.tv_toolbar_title);
        mViewStub = (ViewStub) findViewById(R.id.base_viewstub);
        mToolbar.setTitle("");
        setSupportActionBar(mToolbar);

        //设置视图
        int layoutId = getLayoutId();
        if(layoutId > 0) {
            mViewStub.setLayoutResource(getLayoutId());
            mViewStub.inflate();
        }
        //设置标题
        String titleText = getTitleText();
        if(isNotEmptyString(titleText)) {
            tvTitle.setText(getTitleText());
        }
        //绑定注解类
        ButterKnife.bind(this);
        //初始化数据
        initData();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
        }
        return true;
    }

    /**
     * 获取当前Activity上下文
     * @return
     */
    protected Context getContext() {
        return mContext;
    }

    /**
     * 设置标题栏左边Icon
     * @param resId
     */
    protected void setToolbarLeftIcon(int resId) {
        if(resId > 0) {
            mToolbar.setNavigationIcon(resId);
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        } else {
            mToolbar.setNavigationIcon(null);
            getSupportActionBar().setHomeButtonEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
    }

    /**
     * 请求权限
     * @param permissions 需要的权限列表
     * @param requestCode 请求码
     */
    protected void requestPermission(String[] permissions, int requestCode) {
        this.mRequestCode = requestCode;
        if(checkPermissions(permissions)) {
            permissionSuccess(mRequestCode);
        } else {
            List<String> needPermissions = getDeniedPermissions(permissions);
            ActivityCompat.requestPermissions(this, needPermissions.toArray(new String[needPermissions.size()]), mRequestCode);
        }
    }

    /**
     * 检查所需的权限是否都已授权
     * @param permissions
     * @return
     */
    private boolean checkPermissions(String[] permissions) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        for(String permission : permissions) {
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取所需权限列表中需要申请权限的列表
     * @param permissions
     * @return
     */
    private List<String> getDeniedPermissions(String[] permissions) {
        List<String> needRequestPermissionList = new ArrayList<>();
        for(String permission : permissions) {
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                needRequestPermissionList.add(permission);
            }
        }
        return  needRequestPermissionList;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //系统请求权限回调
        if(requestCode == mRequestCode) {
            if(verifyPermissions(grantResults)) {
                permissionSuccess(mRequestCode);
            } else {
                permissionFail(mRequestCode);
                showPermissionTipsDialog();
            }
        }
    }

    /**
     * 确认所需权限是否都已授权
     * @param grantResults
     * @return
     */
    private boolean verifyPermissions(int[] grantResults) {
        for(int grantResult : grantResults) {
            if(grantResult != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 显示权限提示对话框
     */
    private void showPermissionTipsDialog() {
        showTipsDialogWithTitle("提示", "当前应用缺少必要权限，该功能暂时无法使用。如若需要，请点击【确定】按钮前往设置中心进行权限授权", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startAppSettings();
            }
        }, null);
    }

    /**
     * 权限请求成功
     * @param requestCode
     */
    protected void permissionSuccess(int requestCode) {
        LogUtils.e("获取权限成功：" + requestCode);
    }

    /**
     * 权限请求失败
     * @param requestCode
     */
    protected void permissionFail(int requestCode) {
        LogUtils.e("获取权限失败：" + requestCode);
    }

    /**
     * 启动当前应用设置页面
     */
    private void startAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    /**
     * 弹出Toast提示
     * @param text 提示内容
     */
    protected void toast(String text) {
        if(this.isFinishing()) {
            return;
        }

        if(!TextUtils.isEmpty(text)) {
            Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示提示对话框
     * @param content 内容
     * @param confirmListener 确定按钮点击事件
     */
    protected void showTipsDialog(String content, DialogInterface.OnClickListener confirmListener) {
        showTipsDialogWithTitle(null, content, getString(R.string.confirm), confirmListener, null, null);
    }

    /**
     * 显示提示对话框
     * @param content 内容
     * @param confirmText 确定按钮文字
     * @param confirmListener 确定按钮点击事件
     * @param cancelText 取消按钮文字
     * @param cancelListener 取消按钮点击事件
     */
    protected void showTipsDialog(String content, String confirmText, DialogInterface.OnClickListener confirmListener, String cancelText, DialogInterface.OnClickListener cancelListener) {
        showTipsDialogWithTitle("", content, confirmText, confirmListener, cancelText, cancelListener);
    }

    /**
     * 显示提示对话框（带标题）
     * @param title 标题
     * @param content 内容
     * @param confirmListener 确定按钮点击事件
     * @param cancelListener 取消按钮点击事件
     */
    protected void showTipsDialogWithTitle(String title, String content, DialogInterface.OnClickListener confirmListener, DialogInterface.OnClickListener cancelListener) {
        showTipsDialogWithTitle(title, content, getString(R.string.confirm), confirmListener, getString(R.string.cancel), cancelListener);
    }

    /**
     * 显示提示对话框（带标题）
     * @param title 标题
     * @param content 内容
     * @param confirmText 确定按钮文字
     * @param confirmListener 确定按钮点击事件
     * @param cancelText 取消按钮文字
     * @param cancelListener 取消按钮点击事件
     */
    protected void showTipsDialogWithTitle(String title, String content, String confirmText, DialogInterface.OnClickListener confirmListener, String cancelText, DialogInterface.OnClickListener cancelListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if(isNotEmptyString(title)) {
            builder.setTitle(title);
        }
        builder.setMessage(content);
        builder.setPositiveButton(confirmText, confirmListener);
        if(isNotEmptyString(cancelText)) {
            builder.setNegativeButton(cancelText, cancelListener);
        }
        builder.create().show();
    }

    protected void pushActivity(Class<?> mClass) {
        startActivity(new Intent(mContext, mClass));
    }

    /**
     * 判断字符串是否不为空
     * @param text
     * @return
     */
    protected boolean isNotEmptyString(String text) {
        return !TextUtils.isEmpty(text) && !text.equals("null");
    }

    /**
     * 判断字符串是否为空
     * @param text
     * @return
     */
    protected boolean isEmptyString(String text) {
        return TextUtils.isEmpty(text) || text.equals("null");
    }

    /**
     * 判断列表是否为空
     * @param list
     * @return
     */
    protected boolean isEmptyList(List<?> list) {
        return list == null || list.size() <= 0;
    }

}
