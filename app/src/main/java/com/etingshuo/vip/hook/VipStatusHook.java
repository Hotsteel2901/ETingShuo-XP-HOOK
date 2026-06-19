package com.etingshuo.vip.hook;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * VIP状态Hook
 *
 * 负责Hook以下场景:
 *   1. VIP锁定图标隐藏 — 移除ic_vip_lock_flag的显示
 *   2. VIP升级/延期弹窗拦截 — 不再弹出"去升级""去延期"对话框
 *   3. BuyEcardWebView购买页面拦截 — 跳过购买流程
 *   4. 资源锁定关卡解锁 — 让锁定关卡可正常访问
 */
public class VipStatusHook {

    private static final String TAG = "ETingshuoVipHook";

    /**
     * Hook VIP状态相关方法
     */
    public static void hookVipStatus(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;

        // 1. 隐藏VIP锁定图标
        hookVipLockIcon(cl);

        // 2. 拦截VIP升级弹窗
        hookVipUpgradeDialog(cl);

        // 3. 拦截购买页面
        hookBuyEcardWebView(cl);

        // 4. 解锁资源关卡
        hookResourceLock(cl);
    }

    /**
     * 隐藏VIP锁定图标 (ic_vip_lock_flag)
     *
     * 在布局XML中发现:
     *   res/KJ2.xml → android:src="@drawable/ic_vip_lock_flag"
     *   res/LS6.xml → android:src="@drawable/ic_vip_lock_flag"
     *   res/Luj.xml → android:src="@drawable/ic_vip_lock_flag"
     *
     * Hook ImageView.setImageDrawable / setImageResource,
     * 当检测到设置的是VIP锁图标时，将其隐藏(GONE)
     */
    private static void hookVipLockIcon(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                ImageView.class,
                "setImageResource",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int resId = (int) param.args[0];
                        // ic_vip_lock_flag 的资源ID是 0x7f0806bf
                        if (resId == 0x7f0806bf) {
                            ImageView iv = (ImageView) param.thisObject;
                            iv.setVisibility(View.GONE);
                            XposedBridge.log(TAG + ": 隐藏VIP锁定图标");
                        }
                    }
                }
            );
            XposedBridge.log(TAG + ": VIP锁图标Hook部署成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": VIP锁图标Hook失败: " + t.getMessage());
        }
    }

    /**
     * 拦截VIP升级/延期弹窗
     *
     * 资源中发现:
     *   str_vip_upgrade = "去升级"
     *   str_vip_renew = "去延期"
     *   str_level_lock_recharge = "升级资源立享所有内容"
     *   str_level_lock_recharge2 = "延期资源立享所有内容"
     *
     * Hook Dialog.show() / AlertDialog.Builder 等弹窗类
     * 当弹窗文本包含VIP相关关键字时，取消弹窗显示
     */
    private static void hookVipUpgradeDialog(ClassLoader cl) {
        try {
            // Hook android.app.Dialog.show()
            XposedHelpers.findAndHookMethod(
                "android.app.Dialog",
                cl,
                "show",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // 尝试获取Dialog中的TextView内容
                        try {
                            Object dialog = param.thisObject;
                            // 通过反射获取DecorView中的所有TextView
                            java.lang.reflect.Method getWindow = dialog.getClass().getMethod("getWindow");
                            Object window = getWindow.invoke(dialog);
                            java.lang.reflect.Method getDecorView = window.getClass().getMethod("getDecorView");
                            View decorView = (View) getDecorView.invoke(window);

                            if (containsVipText(decorView)) {
                                param.setResult(null); // 取消弹窗
                                XposedBridge.log(TAG + ": 拦截VIP升级弹窗");
                            }
                        } catch (Throwable ignored) {
                            // 无法获取Dialog内容，放行
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": VIP弹窗Hook失败: " + t.getMessage());
        }
    }

    /**
     * 拦截购买页面 BuyEcardWebView
     *
     * 布局XML中: com.ets100.secondary.widget.webview.BuyEcardWebView
     * 当检测到打开购买WebView时，直接跳过
     */
    private static void hookBuyEcardWebView(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.ets100.secondary.widget.webview.BuyEcardWebView",
                cl,
                "loadUrl",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String url = (String) param.args[0];
                        XposedBridge.log(TAG + ": BuyEcardWebView.loadUrl 拦截: " + url);
                        // 可以选择不加载购买页面
                        // param.setResult(null);
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": BuyEcardWebView Hook失败(可能混淆): " + t.getMessage());
        }
    }

    /**
     * 解锁资源关卡
     *
     * 资源中发现:
     *   str_lock = "未解锁"
     *   str_level_lock_course_tip = "完成上个lesson可解锁"
     *   str_level_lock_recharge = "升级资源立享所有内容"
     *
     * 尝试Hook关卡锁定判断方法
     */
    private static void hookResourceLock(ClassLoader cl) {
        // 这部分需要脱壳后确定具体类名
        // 以下是通用Hook模板

        String[] possibleLockClasses = {
            "com.ets100.secondary.model.ResourceStatus",
            "com.ets100.secondary.bean.ResourceInfo",
            "com.ets100.secondary.ui.course.CourseViewModel",
            "com.ets100.secondary.ui.level.LevelViewModel",
        };

        for (String className : possibleLockClasses) {
            try {
                Class<?> clazz = XposedHelpers.findClass(className, cl);

                // 尝试Hook isLocked() 方法
                try {
                    XposedHelpers.findAndHookMethod(clazz, "isLocked", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(false); // 永远解锁
                            XposedBridge.log(TAG + ": " + className + ".isLocked() → false");
                        }
                    });
                } catch (Throwable ignored) {}

                // 尝试Hook isLocked(boolean) 方法
                try {
                    XposedHelpers.findAndHookMethod(clazz, "isLocked", boolean.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(false);
                        }
                    });
                } catch (Throwable ignored) {}

            } catch (Throwable ignored) {
                // 类不存在
            }
        }
    }

    /**
     * 递归检查View树中是否包含VIP相关文本
     */
    private static boolean containsVipText(View view) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null) {
                String str = text.toString();
                return str.contains("升级") ||
                       str.contains("延期") ||
                       str.contains("去升级") ||
                       str.contains("去延期") ||
                       str.contains("升级资源") ||
                       str.contains("延期资源") ||
                       str.contains("VIP") ||
                       str.contains("vip");
            }
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsVipText(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }
}