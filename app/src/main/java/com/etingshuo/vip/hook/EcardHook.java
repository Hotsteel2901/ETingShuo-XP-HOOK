package com.etingshuo.vip.hook;

import android.app.Application;
import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import org.json.JSONObject;

import java.lang.reflect.Method;

/**
 * E卡状态Hook
 *
 * E听说中学的E卡(学习卡)是资源/课程授权的核心：
 * - 用户购买E卡后，服务端记录E卡状态和到期时间
 * - 客户端每次启动/刷新时从服务端获取E卡信息
 * - E卡过期后，资源被锁定，显示VIP锁定图标
 *
 * Hook策略:
 *   1. Hook壳Application的onCreate，确保在壳解密后再加载业务类
 *   2. Hook E卡状态的Java Bean/Model的getter方法，修改返回值
 *   3. Hook API响应解析，在JSON解析时篡改E卡字段
 *   4. 通用字段名匹配：通过反射扫描可疑类，自动Hook匹配字段
 */
public class EcardHook {

    private static final String TAG = "ETingshuoVipHook";

    /**
     * Hook梆梆加固的Application初始化
     * 当壳解密完成、真实Application被创建时触发
     */
    public static void hookShellAppInit(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 梆梆加固壳类 com.secneo.apkwrapper.AW 是Application代理
            // Hook它的attachBaseContext或onCreate，在壳解密后执行我们的逻辑
            XposedHelpers.findAndHookMethod(
                "com.secneo.apkwrapper.AW",
                lpparam.classLoader,
                "attachBaseContext",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log(TAG + ": 壳Application attachBaseContext 完成，真实dex已加载");

                        // 此时壳已经解密了真实dex，但ClassLoader可能还没切换
                        // 延迟一点再执行业务Hook
                        new Thread(() -> {
                            try {
                                Thread.sleep(3000);

                                // 尝试获取壳解密后的真实ClassLoader
                                ClassLoader realClassLoader = null;
                                try {
                                    // 梆梆加固在AW中会创建新的ClassLoader加载真实dex
                                    Application app = (Application) param.thisObject;
                                    realClassLoader = app.getClassLoader();
                                    XposedBridge.log(TAG + ": 获取到真实ClassLoader: " + realClassLoader);
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + ": 获取真实ClassLoader失败: " + t.getMessage());
                                    realClassLoader = lpparam.classLoader;
                                }

                                // 使用真实ClassLoader进行业务Hook
                                hookEcardModelClasses(realClassLoader);

                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": 壳初始化后Hook异常: " + t.getMessage());
                            }
                        }).start();
                    }
                }
            );
            XposedBridge.log(TAG + ": ShellAppInit Hook部署成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ShellAppInit Hook部署失败(可能壳类名不同): " + t.getMessage());
        }
    }

    /**
     * Hook E卡状态相关方法
     */
    public static void hookEcardStatus(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;
        hookEcardModelClasses(cl);
    }

    /**
     * 使用反射扫描+Hook E卡相关的Model/Bean类
     *
     * 核心思路：E卡信息通常存储在Java Bean中，包含如下字段:
     *   - isExpired / isVip / isValid / isActive → boolean, 改为true
     *   - expireTime / endTime / deadline / validDate → long, 改为极大时间戳
     *   - ecardStatus / vipType / level → int, 改为最高等级
     *
     * 由于加固+混淆，类名可能是a.b.c这种，所以用字段名模式匹配
     */
    private static void hookEcardModelClasses(ClassLoader classLoader) {
        // =====================================================
        // 策略A: 按已知类名精确Hook（脱壳后填入）
        // =====================================================
        // 以下类名需要脱壳后用jadx分析得到，这里是模板
        String[] knownModelClasses = {
            // TODO: 脱壳后填入实际类名，例如:
            // "com.ets100.secondary.model.EcardInfo",
            // "com.ets100.secondary.bean.VipStatus",
            // "com.ets100.secondary.model.UserEcard",
            // "com.ets100.secondary.bean.ResourceStatus",
        };

        for (String className : knownModelClasses) {
            try {
                hookModelClass(classLoader, className);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Hook类 " + className + " 失败: " + t.getMessage());
            }
        }

        // =====================================================
        // 策略B: 通用Hook — Hook所有包含E卡关键字的getter
        // =====================================================
        hookCommonEcardGetters(classLoader);
    }

    /**
     * Hook指定的Model类，将VIP/过期相关字段getter修改返回值
     */
    private static void hookModelClass(ClassLoader cl, String className) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, cl);

            // Hook所有getter方法，检查方法名是否匹配VIP/E卡关键字
            for (Method method : clazz.getDeclaredMethods()) {
                String methodName = method.getName();

                // boolean类型的getter: isXxx()
                if (methodName.startsWith("is") && method.getReturnType() == boolean.class) {
                    String field = methodName.substring(2).toLowerCase();
                    if (isVipRelatedField(field)) {
                        hookBooleanGetter(cl, className, methodName, true);
                    }
                }

                // long类型的getter: getExpireTime() / getEndTime()
                if (methodName.startsWith("get") && method.getReturnType() == long.class) {
                    String field = methodName.substring(3).toLowerCase();
                    if (isExpireRelatedField(field)) {
                        hookLongGetter(cl, className, methodName, Long.MAX_VALUE);
                    }
                }

                // int类型的getter: getStatus() / getType()
                if (methodName.startsWith("get") && method.getReturnType() == int.class) {
                    String field = methodName.substring(3).toLowerCase();
                    if (isStatusRelatedField(field)) {
                        hookIntGetter(cl, className, methodName, 1); // 1 = VIP/已激活
                    }
                }
            }

            XposedBridge.log(TAG + ": Model类 " + className + " Hook完成");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Model类 " + className + " 未找到或Hook失败: " + t.getMessage());
        }
    }

    /**
     * 通用Hook: 拦截所有可能的E卡状态getter
     * 使用XposedHelpers.findAndHookMethod进行模糊匹配
     */
    private static void hookCommonEcardGetters(ClassLoader cl) {
        // 常见的E卡相关类路径模式（混淆后的类可能在各种包下）
        String[] commonPaths = {
            "com.ets100.secondary.model",
            "com.ets100.secondary.bean",
            "com.ets100.secondary.entity",
            "com.ets100.secondary.data",
            "com.ets100.secondary.api.bean",
            "com.ets100.secondary.api.entity",
        };

        // 尝试Hook这些路径下可能的类
        for (String path : commonPaths) {
            String[] possibleClasses = {
                path + ".EcardInfo",
                path + ".EcardModel",
                path + ".EcardBean",
                path + ".EcardEntity",
                path + ".VipInfo",
                path + ".VipStatus",
                path + ".VipModel",
                path + ".UserInfo",
                path + ".UserEcard",
                path + ".ResourceStatus",
                path + ".ResourceInfo",
            };

            for (String className : possibleClasses) {
                try {
                    hookModelClass(cl, className);
                } catch (Throwable ignored) {
                    // 类不存在，忽略
                }
            }
        }
    }

    /**
     * Hook API响应 — 在JSON解析层面篡改E卡字段
     *
     * 这是最强大的策略：无论服务端返回什么，客户端解析出来的永远是"永久VIP"
     */
    public static void hookApiResponse(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;

        // Hook JSONObject的get/getString/getBoolean等方法
        // 当key包含E卡/VIP相关字样时，篡改返回值
        try {
            XposedHelpers.findAndHookMethod(
                JSONObject.class,
                "optBoolean",
                String.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        if (isVipRelatedJsonKey(key)) {
                            param.setResult(true);
                            XposedBridge.log(TAG + ": JSONObject.optBoolean(" + key + ") → true");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": JSONObject.optBoolean Hook失败: " + t.getMessage());
        }

        // Hook JSONObject.optString — 篡改过期时间字段
        try {
            XposedHelpers.findAndHookMethod(
                JSONObject.class,
                "optString",
                String.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        if (isExpireRelatedJsonKey(key)) {
                            // 返回一个很远的日期 "2099-12-31"
                            param.setResult("2099-12-31");
                            XposedBridge.log(TAG + ": JSONObject.optString(" + key + ") → 2099-12-31");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": JSONObject.optString Hook失败: " + t.getMessage());
        }

        // Hook JSONObject.optLong — 篡改过期时间戳字段
        try {
            XposedHelpers.findAndHookMethod(
                JSONObject.class,
                "optLong",
                String.class,
                long.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        if (isExpireRelatedJsonKey(key)) {
                            // 2099-12-31 的时间戳
                            param.setResult(4102444800000L);
                            XposedBridge.log(TAG + ": JSONObject.optLong(" + key + ") → 4102444800000");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": JSONObject.optLong Hook失败: " + t.getMessage());
        }

        // Hook JSONObject.optInt — 篡改状态/类型字段
        try {
            XposedHelpers.findAndHookMethod(
                JSONObject.class,
                "optInt",
                String.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        if (isStatusRelatedJsonKey(key)) {
                            param.setResult(1); // 1 = VIP/已激活
                            XposedBridge.log(TAG + ": JSONObject.optInt(" + key + ") → 1");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": JSONObject.optInt Hook失败: " + t.getMessage());
        }

        XposedBridge.log(TAG + ": API响应Hook部署完成");
    }

    // ==================== 辅助方法 ====================

    /** 判断字段名是否与VIP/授权相关 */
    private static boolean isVipRelatedField(String fieldName) {
        String lower = fieldName.toLowerCase();
        return lower.contains("vip") ||
               lower.contains("expired") ||
               lower.contains("valid") ||
               lower.contains("active") ||
               lower.contains("locked") ||
               lower.contains("auth") ||
               lower.contains("premium") ||
               lower.contains("member") ||
               lower.contains("paid") ||
               lower.contains("purchased") ||
               lower.contains("ecard");
    }

    /** 判断字段名是否与过期时间相关 */
    private static boolean isExpireRelatedField(String fieldName) {
        String lower = fieldName.toLowerCase();
        return lower.contains("expire") ||
               lower.contains("endtime") ||
               lower.contains("deadline") ||
               lower.contains("validdate") ||
               lower.contains("validuntil") ||
               lower.contains("expiretime");
    }

    /** 判断字段名是否与状态/类型相关 */
    private static boolean isStatusRelatedField(String fieldName) {
        String lower = fieldName.toLowerCase();
        return lower.contains("status") ||
               lower.contains("viptype") ||
               lower.contains("level") ||
               lower.contains("ecardtype") ||
               lower.contains("resourcetype");
    }

    /** JSON key 匹配 — VIP相关 */
    private static boolean isVipRelatedJsonKey(String key) {
        String lower = key.toLowerCase();
        return lower.contains("isvip") ||
               lower.contains("is_expired") ||
               lower.contains("isexpired") ||
               lower.contains("is_valid") ||
               lower.contains("isvalid") ||
               lower.contains("is_active") ||
               lower.contains("isactive") ||
               lower.contains("is_locked") ||
               lower.contains("islocked") ||
               lower.contains("is_vip") ||
               lower.contains("is_ecard") ||
               lower.contains("has_ecard") ||
               lower.contains("has_vip") ||
               lower.contains("vip_status") ||
               lower.contains("ecard_status") ||
               lower.contains("is_purchased") ||
               lower.contains("is_paid") ||
               lower.contains("need_pay") ||
               lower.contains("need_upgrade") ||
               lower.contains("need_buy");
    }

    /** JSON key 匹配 — 过期时间相关 */
    private static boolean isExpireRelatedJsonKey(String key) {
        String lower = key.toLowerCase();
        return lower.contains("expire_time") ||
               lower.contains("expire_date") ||
               lower.contains("endtime") ||
               lower.contains("end_time") ||
               lower.contains("deadline") ||
               lower.contains("valid_date") ||
               lower.contains("valid_until") ||
               lower.contains("expire_at") ||
               lower.contains("ecard_expire") ||
               lower.contains("vip_expire");
    }

    /** JSON key 匹配 — 状态类型相关 */
    private static boolean isStatusRelatedJsonKey(String key) {
        String lower = key.toLowerCase();
        return lower.contains("vip_type") ||
               lower.contains("viptype") ||
               lower.contains("ecard_type") ||
               lower.contains("ecardtype") ||
               lower.contains("status") && (lower.contains("ecard") || lower.contains("vip") || lower.contains("resource")) ||
               lower.contains("resource_status");
    }

    /** Hook boolean类型getter，固定返回指定值 */
    private static void hookBooleanGetter(ClassLoader cl, String className, String methodName, boolean returnValue) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(returnValue);
                }
            });
            XposedBridge.log(TAG + ": Hook " + className + "." + methodName + "() → " + returnValue);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook " + className + "." + methodName + " 失败: " + t.getMessage());
        }
    }

    /** Hook long类型getter，固定返回指定值 */
    private static void hookLongGetter(ClassLoader cl, String className, String methodName, long returnValue) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(returnValue);
                }
            });
            XposedBridge.log(TAG + ": Hook " + className + "." + methodName + "() → " + returnValue);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook " + className + "." + methodName + " 失败: " + t.getMessage());
        }
    }

    /** Hook int类型getter，固定返回指定值 */
    private static void hookIntGetter(ClassLoader cl, String className, String methodName, int returnValue) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(returnValue);
                }
            });
            XposedBridge.log(TAG + ": Hook " + className + "." + methodName + "() → " + returnValue);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook " + className + "." + methodName + " 失败: " + t.getMessage());
        }
    }
}