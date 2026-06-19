# E听说中学 VIP/E卡 永久破解 — Xposed Hook 模块

## 目标应用
- **应用名**: E听说中学
- **包名**: `com.ets100.secondary`
- **版本**: 7.0.52.2263
- **加固**: 梆梆加固 (BangBang / com.secneo.apkwrapper)

## 兼容框架
- ✅ LSPosed (Root环境)
- ✅ NPatch (免Root, 复刻自LSPatch)
- ✅ LSPatch (免Root)

---

## 使用方式

### 方式一: LSPosed (需Root + Magisk)
1. 安装本模块APK
2. LSPosed管理器 → 模块 → 启用本模块
3. 勾选目标应用 `com.ets100.secondary`
4. 强制停止目标应用后重新打开

### 方式二: NPatch / LSPatch (免Root)
1. 安装NPatch管理器
2. 管理器中添加目标应用 `E听说中学`
3. 为目标应用嵌入本模块
4. 重新安装修补后的APK
5. 打开应用即可生效

---

## 项目结构

```
ETingshuoVipHook/
├── app/
│   ├── src/main/
│   │   ├── java/com/etingshuo/vip/hook/
│   │   │   ├── MainHook.java          # Hook入口
│   │   │   ├── EcardHook.java         # E卡状态Hook
│   │   │   ├── VipStatusHook.java     # VIP状态Hook
│   │   │   └── ExpireHook.java        # 过期判断Hook
│   │   ├── assets/
│   │   │   └── xposed_init            # Xposed入口声明
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── README.md
└── 脱壳指南.md
```
