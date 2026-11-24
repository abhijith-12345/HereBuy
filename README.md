# HereBuy

HereBuy is a native Android neighbourhood marketplace. The Android app uses MapLibre for maps and the FastAPI service in `backend/` for authentication and listing data. Supabase is not used by the Android app.

## Run on a USB-connected Android device

1. Start FastAPI from `backend/` with `.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload`.
2. In another PowerShell window, run `adb reverse tcp:8001 tcp:8001`.
3. Set `API_BASE_URL=http://127.0.0.1:8001/api/v1` in ignored `local.properties` and build the Android app.

For an Android emulator, use `http://10.0.2.2:8001/api/v1`. For a phone without USB port forwarding, run `ngrok http 8001` and use the HTTPS forwarding address followed by `/api/v1` as `API_BASE_URL`.

The Android sign-in screen accepts a phone number or email and password. Passwords are hashed by the API; authenticated app requests carry a signed bearer JWT.
