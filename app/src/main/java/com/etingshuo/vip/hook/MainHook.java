package com.etingshuo.vip.hook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * E听说中学 VIP/E卡 永久破解 — Xposed Hook 主入口
 *
 * 兼容: LSPosed / NPatch / LSPatch
 *
 * 核心思路:
 *   1. 梆梆加固的APP在运行时才会解密真实dex，所以必须在 handleLoadPackage 中
 *      等待目标应用加载完毕后再Hook
 *   2. 使用延时Hook策略，确保壳代码已完成解密
 *   3. Hook的关键目标：E卡过期判断、VIP状态判断、资源锁定判断
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "ETingshuoVipHook";
    private static final String TARGET_PACKAGE = "com.ets100.secondary";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 只Hook目标应用
        if (!lpparam.packageName.equals(TARGET_PACKAGE)) {
            return;
        }

        XposedBridge.log(TAG + ": 检测到目标应用 " + lpparam.packageName);

        // ========== 阶段1: 通用Hook（壳类可见时即可执行） ==========

        // Hook梆梆加固的Application初始化回调，等壳解密完成后再执行业务Hook
        try {
            EcardHook.hookShellAppInit(lpparam);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ShellAppInit Hook失败: " + t.getMessage());
        }

        // ========== 阶段2: 延时Hook（确保壳解密完成） ==========
        // 使用延时策略：等待壳代码执行完毕、真实dex加载到内存后，再Hook业务代码
        new Thread(() -> {
            try {
                // 等待壳代码解密完成，通常3-5秒足够
                Thread.sleep(5000);
                XposedBridge.log(TAG + ": 开始延时Hook业务代码...");

                // Hook E卡状态相关方法
                EcardHook.hookEcardStatus(lpparam);

                // Hook VIP状态相关方法
                VipStatusHook.hookVipStatus(lpparam);

                // Hook 过期判断相关方法
                ExpireHook.hookExpireCheck(lpparam);

                // Hook 通用拦截（API响应篡改）
                EcardHook.hookApiResponse(lpparam);

                XposedBridge.log(TAG + ": 所有Hook部署完成");

            } catch (Throwable t) {
                XposedBridge.log(TAG + ": 延时Hook异常: " + t.getMessage());
            }
        }).start();
    }
}