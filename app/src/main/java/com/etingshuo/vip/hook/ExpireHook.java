package com.etingshuo.vip.hook;

import java.util.Calendar;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 过期判断Hook
 *
 * 负责Hook所有与"过期"相关的判断逻辑:
 *   1. Hook System.currentTimeMillis() — 让应用认为时间永远停留在有效期内
 *   2. Hook Date/Calendar比较 — 防止日期比较判断过期
 *   3. Hook 通用过期状态方法 — 拦截isExpired()等
 *
 * 资源分析发现的关键字符串:
 *   str_expire = "已过期"
 *   str_expire1 = "(已过期)"
 *   str_not_expire = "(未过期)"
 *   str_tips_ecard_expired_soon = "资源还有 %s天 到期"
 *   str_tips_ecard_expired_soon1 = "还有%s天到期"
 *   str_tips_ecard_upgrade = "%sE卡已升级或延期成功，返回首页或重启应用会刷新状态。"
 */
public class ExpireHook {

    private static final String TAG = "ETingshuoVipHook";

    /**
     * 永久VIP的到期时间戳: 2099-12-31 23:59:59 UTC
     */
    private static final long PERMANENT_EXPIRE_TIME = 4102444799000L;

    /**
     * Hook过期判断相关方法
     */
    public static void hookExpireCheck(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;

        // 1. Hook通用过期判断方法
        hookCommonExpireMethods(cl);

        // 2. Hook SharedPreferences中的过期时间
        hookSharedPrefsExpire(cl);

        // 3. Hook "到期提醒"Toast/Dialog
        hookExpireNotification(cl);
    }

    /**
     * 通用过期判断方法Hook
     * 尝试Hook各种可能的isExpired/isValid方法
     */
    private static void hookCommonExpireMethods(ClassLoader cl) {
        // 常见的过期判断类（脱壳后可能需要调整）
        String[] possibleClasses = {
            "com.ets100.secondary.model.EcardInfo",
            "com.ets100.secondary.bean.EcardInfo",
            "com.ets100.secondary.entity.EcardInfo",
            "com.ets100.secondary.model.UserEcard",
            "com.ets100.secondary.bean.UserEcard",
            "com.ets100.secondary.model.VipStatus",
            "com.ets100.secondary.bean.VipStatus",
            "com.ets100.secondary.model.ResourceStatus",
            "com.ets100.secondary.bean.ResourceStatus",
            "com.ets100.secondary.utils.EcardUtils",
            "com.ets100.secondary.utils.VipUtils",
            "com.ets100.secondary.manager.EcardManager",
            "com.ets100.secondary.manager.VipManager",
        };

        for (String className : possibleClasses) {
            try {
                Class<?> clazz = XposedHelpers.findClass(className, cl);

                // 尝试Hook isExpired()
                tryHookMethod(cl, className, "isExpired", false);
                // 尝试Hook isExpired(Context)
                tryHookMethodWithParam(cl, className, "isExpired", false);

                // 尝试Hook isVip()
                tryHookMethod(cl, className, "isVip", true);
                // 尝试Hook isVip(Context)
                tryHookMethodWithParam(cl, className, "isVip", true);

                // 尝试Hook isValid()
                tryHookMethod(cl, className, "isValid", true);
                // 尝试Hook isActive()
                tryHookMethod(cl, className, "isActive", true);

                // 尝试Hook getExpireTime() → 返回极大时间戳
                tryHookLongMethod(cl, className, "getExpireTime", PERMANENT_EXPIRE_TIME);
                tryHookLongMethod(cl, className, "getEndTime", PERMANENT_EXPIRE_TIME);
                tryHookLongMethod(cl, className, "getDeadline", PERMANENT_EXPIRE_TIME);
                tryHookLongMethod(cl, className, "getExpireDate", PERMANENT_EXPIRE_TIME);

                // 尝试Hook getVipType() → 返回最高等级
                tryHookIntMethod(cl, className, "getVipType", 2);
                tryHookIntMethod(cl, className, "getEcardType", 2);
                tryHookIntMethod(cl, className, "getStatus", 1);

                XposedBridge.log(TAG + ": " + className + " 过期判断Hook完成");
            } catch (Throwable ignored) {
                // 类不存在，跳过
            }
        }
    }

    /**
     * Hook SharedPreferences中的过期时间
     *
     * 很多APP会将E卡到期时间存储在SharedPreferences中
     * 当读取包含expire/end/vip等关键字的key时，返回永久有效的时间戳
     */
    private static void hookSharedPrefsExpire(ClassLoader cl) {
        try {
            // Hook SharedPreferences.getLong()
            XposedHelpers.findAndHookMethod(
                "android.app.SharedPreferencesImpl",
                cl,
                "getLong",
                String.class,
                long.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        String lowerKey = key.toLowerCase();
                        if (lowerKey.contains("expire") ||
                            lowerKey.contains("endtime") ||
                            lowerKey.contains("deadline") ||
                            lowerKey.contains("vip_time") ||
                            lowerKey.contains("ecard_time")) {
                            long original = (long) param.getResult();
                            if (original > 0 && original < PERMANENT_EXPIRE_TIME) {
                                param.setResult(PERMANENT_EXPIRE_TIME);
                                XposedBridge.log(TAG + ": SharedPreferences.getLong(" + key + ") " + original + " → " + PERMANENT_EXPIRE_TIME);
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": SharedPreferences.getLong Hook失败: " + t.getMessage());
        }

        try {
            // Hook SharedPreferences.getString() — 过期日期字符串
            XposedHelpers.findAndHookMethod(
                "android.app.SharedPreferencesImpl",
                cl,
                "getString",
                String.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        String lowerKey = key.toLowerCase();
                        if (lowerKey.contains("expire") ||
                            lowerKey.contains("endtime") ||
                            lowerKey.contains("deadline") ||
                            lowerKey.contains("validdate")) {
                            param.setResult("2099-12-31");
                            XposedBridge.log(TAG + ": SharedPreferences.getString(" + key + ") → 2099-12-31");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": SharedPreferences.getString Hook失败: " + t.getMessage());
        }

        try {
            // Hook SharedPreferences.getBoolean() — VIP状态
            XposedHelpers.findAndHookMethod(
                "android.app.SharedPreferencesImpl",
                cl,
                "getBoolean",
                String.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        String lowerKey = key.toLowerCase();
                        if (lowerKey.contains("isvip") ||
                            lowerKey.contains("vip_status") ||
                            lowerKey.contains("is_expired") ||
                            lowerKey.contains("ecard_active") ||
                            lowerKey.contains("is_purchased")) {
                            param.setResult(true);
                            XposedBridge.log(TAG + ": SharedPreferences.getBoolean(" + key + ") → true");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": SharedPreferences.getBoolean Hook失败: " + t.getMessage());
        }
    }

    /**
     * Hook到期提醒Toast/Dialog
     * 拦截包含"已过期""即将到期"等文本的提示
     */
    private static void hookExpireNotification(ClassLoader cl) {
        try {
            // Hook Toast.show() — 拦截到期提醒Toast
            XposedHelpers.findAndHookMethod(
                "android.widget.Toast",
                cl,
                "show",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object toast = param.thisObject;
                            // 获取Toast的TextView
                            java.lang.reflect.Field mViewField = toast.getClass().getDeclaredField("mNextView");
                            mViewField.setAccessible(true);
                            View view = (View) mViewField.get(toast);

                            if (view instanceof TextView) {
                                String text = ((TextView) view).getText().toString();
                                if (text.contains("已过期") ||
                                    text.contains("即将到期") ||
                                    text.contains("到期") ||
                                    text.contains("升级资源") ||
                                    text.contains("延期资源")) {
                                    param.setResult(null);
                                    XposedBridge.log(TAG + ": 拦截到期Toast: " + text);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Toast Hook失败: " + t.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private static void tryHookMethod(ClassLoader cl, String className, String methodName, boolean returnValue) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(returnValue);
                    XposedBridge.log(TAG + ": " + className + "." + methodName + "() → " + returnValue);
                }
            });
        } catch (Throwable ignored) {}
    }

    private static void tryHookMethodWithParam(ClassLoader cl, String className, String methodName, boolean returnValue) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName,
                "android.content.Context", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(returnValue);
                    }
                });
        } catch (Throwable ignored) {}
    }

    private static void tryHookLongMethod(ClassLoader cl, String className, String methodName, long returnValue) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(returnValue);
                    XposedBridge.log(TAG + ": " + className + "." + methodName + "() → " + returnValue);
                }
            });
        } catch (Throwable ignored) {}
    }

    private static void tryHookIntMethod(ClassLoader cl, String className, String methodName, int returnValue) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(returnValue);
                }
            });
        } catch (Throwable ignored) {}
    }
}