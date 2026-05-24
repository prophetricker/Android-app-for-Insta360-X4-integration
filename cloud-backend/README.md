# OmniEye Cloud Backend

FastAPI backend for the Android-first OmniEye flow.

```text
Android frame upload -> cloud-backend /analyze -> latest DAP depth result -> distance/level/scene_text
```

## Run the lightweight service

```powershell
cd D:\MyProject\Bohack2\OmniEye-Mobile
python -m pip install -r cloud-backend\requirements.txt
python -m uvicorn omnieye_cloud.main:app --app-dir cloud-backend --host 0.0.0.0 --port 8000
```

Health check:

```powershell
curl.exe http://127.0.0.1:8000/health
```

Upload a frame:

```powershell
curl.exe -F "frame=@sample.jpg" http://127.0.0.1:8000/analyze
```

When DAP is not configured yet, `/analyze` still returns an Android-compatible fallback:

```json
{
  "distance_m": 3.5,
  "level": 0,
  "confidence": 0.0,
  "scene_text": "正在分析前方环境",
  "latency_ms": 0
}
```

## DAP setup

DAP and model weights stay outside this Git repository.

```powershell
git clone https://github.com/Insta360-Research-Team/DAP D:\Models\DAP
```

Create a separate Python environment for DAP, because the current repo Python may not match DAP's tested stack. On the current Windows dev machine, the working environment lives outside Git at `D:\MyProject\Bohack2\.tooling\dap-venv`.

```powershell
D:\MyProject\Bohack2\.tooling\python312\python.exe -m venv D:\MyProject\Bohack2\.tooling\dap-venv
D:\MyProject\Bohack2\.tooling\dap-venv\Scripts\python.exe -m pip install -r D:\Models\DAP\requirements.txt
D:\MyProject\Bohack2\.tooling\dap-venv\Scripts\python.exe -m pip install --force-reinstall torch==2.7.1 torchvision==0.22.1 --index-url https://download.pytorch.org/whl/cu128
D:\MyProject\Bohack2\.tooling\dap-venv\Scripts\python.exe -m pip install pillow==10.4.0 MarkupSafe==2.1.5
```

Download the pretrained model from:

```text
https://huggingface.co/Insta360-Research/DAP-weights
```

Set environment variables before starting `cloud-backend`:

```powershell
$env:DAP_REPO_DIR = "D:\Models\DAP"
$env:DAP_WEIGHTS_PATH = "D:\Models\DAP-weights-repo\model.pth"
$env:DAP_DEVICE = "cuda"
$env:DAP_PYTHON = "D:\MyProject\Bohack2\.tooling\dap-venv\Scripts\python.exe"
$env:DAP_DEPTH_SCALE = "100"
python -m uvicorn omnieye_cloud.main:app --app-dir cloud-backend --host 0.0.0.0 --port 8000
```

The backend invokes DAP as an external runner and reads DAP's generated `depth_npy\000001.npy`. DAP's public inference output is normalized to the 0-1 range used by its 100m training/evaluation setup, so the backend multiplies by `DAP_DEPTH_SCALE` before applying meter thresholds. Keep the default at `100` for the MVP and tune it during real-world calibration.

## Android contract

Android should send:

```text
POST /analyze
multipart form-data field: frame
```

Response:

```json
{
  "distance_m": 1.2,
  "level": 2,
  "confidence": 0.8,
  "scene_text": "前方约一米有障碍物，请减速。",
  "latency_ms": 25
}
```

`level` uses the shared thresholds:

```text
< 0.4m -> 4
< 0.8m -> 3
< 1.5m -> 2
< 3.0m -> 1
else   -> 0
```

## Tests

```powershell
python -m pytest cloud-backend -q --basetemp D:\MyProject\Bohack2\.pytest_tmp_repo -p no:cacheprovider
```

`--basetemp` avoids local Windows temp-directory permission issues observed on this machine.

## Git branch

Work should stay on:

```text
cloud-backend-mvp
```

Push to:

```powershell
git remote add android-insta360 https://github.com/prophetricker/Android-app-for-Insta360-X4-integration.git
git push -u android-insta360 cloud-backend-mvp
```
