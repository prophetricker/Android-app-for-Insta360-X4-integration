# OmniEye 性能测试指南

本文档描述如何在 `feature/perf-telemetry-upload-fastpath` 分支上进行上传性能测试。

## 前置条件

### 软件要求

- Python 3.10+
- Android SDK (API 21+)
- Gradle 8.x
- Java 17

### 网络要求

- 手机和运行后端的电脑在同一局域网
- 或使用 ngrok/花生壳进行内网穿透

## 后端测试

### 1. 安装依赖

```powershell
cd cloud-backend
pip install -r requirements.txt
```

### 2. 启动后端服务

```powershell
# 开发模式（自动重载）
uvicorn main:app --reload --host 0.0.0.0 --port 8000

# 生产模式
uvicorn main:app --host 0.0.0.0 --port 8000
```

### 3. 验证服务运行

```powershell
# 健康检查
Invoke-RestMethod -Uri http://localhost:8000/health

# 预期输出: {"status":"healthy","service":"OmniEye Cloud Backend"}
```

### 4. 运行单元测试

```powershell
# 运行所有测试
pytest tests/ -v

# 运行特定测试文件
pytest tests/test_analyze.py -v

# 运行并显示详细输出
pytest tests/ -v --tb=short
```

## Android 测试

### 1. 构建 Debug APK

```powershell
# 设置环境变量（如果需要）
$env:JAVA_HOME='D:\path\to\jdk17'
$env:ANDROID_HOME='D:\path\to\android-sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME

# 构建
./gradlew assembleDebug
```

### 2. 运行单元测试

```powershell
# 运行所有单元测试
./gradlew testDebugUnitTest

# 运行特定测试类
./gradlew testDebugUnitTest --tests "com.omniveye.app.cloud.ImageUploadManagerTest"

# 查看测试报告
Start-Process "app/build/reports/tests/testDebugUnitTest/index.html"
```

### 3. 安装并运行

```powershell
# 安装 APK
./gradlew installDebug

# 或通过 ADB 安装
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 手动测试清单

### 功能测试

- [ ] `FAST_ANALYZE` 规格上传完成
- [ ] `HIGH_QUALITY` 规格上传完成
- [ ] 后端日志显示图片元数据（宽、高、字节数）
- [ ] 后端日志显示 `latency_ms`
- [ ] Android 日志显示压缩指标

### 性能测试

- [ ] `FAST_ANALYZE` 压缩耗时 < 500ms（1920x1080 图片）
- [ ] `HIGH_QUALITY` 压缩耗时 < 1000ms（4K 图片）
- [ ] 请求耗时 < 2000ms（局域网）
- [ ] 后端处理耗时 < 500ms（fallback 模式）

## 性能基准测试

### 步骤 1：准备测试环境

```powershell
# 清空日志
adb logcat -c

# 设置后端地址（在代码中或配置中）
```

### 步骤 2：运行上传测试

1. 打开 Android 应用
2. 选择 `FAST_ANALYZE` 规格
3. 执行上传操作
4. 记录输出

### 步骤 3：收集性能数据

```powershell
# 过滤性能日志
adb logcat -d | Select-String "PerfMetrics"

# 示例输出:
# D/PerfMetrics: Scaling 3840x2160 to 1280x720 with quality FAST_ANALYZE
# D/PerfMetrics: Compression complete: 1280x720, 153600 bytes, 245ms (quality=FAST_ANALYZE)
```

### 步骤 4：记录到 latency-baseline.md

根据日志中的数据填写表格：

| 场景 | API | 上传规格 | 原始尺寸 | 上传尺寸 | JPEG KB | 压缩 ms | 请求 ms | 后端 latency_ms | Android 总 ms | 备注 |
| --- | --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: | --- |

## 故障排除

### 后端启动失败

```
Error: [Errno 10048] Address already in use
```
**解决方案**：端口被占用，更换端口或关闭占用进程

```powershell
# 查找占用端口的进程
netstat -ano | Select-String ":8000"
# 结束进程
taskkill /PID <PID> /F
```

### Android 测试失败

```
Execution failed for task ':app:testDebugUnitTest'.
> There were failing tests.
```
**解决方案**：查看详细错误

```powershell
./gradlew testDebugUnitTest --info 2>&1 | Select-String -Context 0,5 "FAILED"
```

### 后端测试失败

```
FAILED tests/test_analyze.py::TestAnalyzeEndpoint::test_analyze_returns_correct_fields
```
**解决方案**：确保在 `cloud-backend` 目录运行，检查依赖安装

```powershell
cd cloud-backend
pip install -r requirements.txt
pytest tests/test_analyze.py::TestAnalyzeEndpoint::test_analyze_returns_correct_fields -v
```

## 日志分析

### Android 日志标签

| 标签 | 内容 |
|------|------|
| `PerfMetrics` | 压缩性能数据 |
| `ImageUploadManager` | 上传管理日志 |
| `CloudRepository` | 云端请求日志 |

### 后端日志格式

```
2026-05-27 12:00:00,123 [INFO] routers.analyze: [ANALYZE] Received image: 1280x720, 153600 bytes, quality=FAST_ANALYZE
2026-05-27 12:00:00,250 [INFO] routers.analyze: [ANALYZE] Processing complete: 127.50ms (fallback mode - no DAP inference)
```

## 相关文档

- [性能基线文档](./latency-baseline.md) - 记录性能测试结果
- [队友任务文档](../../docs/perf/teammate-agent-task.md) - 任务详细说明
