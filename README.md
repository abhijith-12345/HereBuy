# HereBuy

See [workbook.md](workbook.md) for the complete project file map, key functions, and change locations.

HereBuy is a native Android neighbourhood marketplace. The Android app uses MapLibre for maps and the FastAPI service in `backend/` for authentication and listing data. Supabase is not used by the Android app.

The map fetches all available listings inside the current visible bounds. It waits briefly for panning or zooming to stop, cancels outdated requests, and loads the newly visible area; there is no fixed-distance map search radius. Location permission is only needed for the recenter button.

Map settings also let a signed-in user choose a 1–100 km radius for nearby item alerts. While the app is open, the app checks for alerts and shows them in the Notifications tab. Firebase Cloud Messaging sends a system notification for new listings when the app is open or in the background. Android 13+ asks for notification permission the first time the user opens the signed-in home screen.

To enable push delivery, create/register the Android app (`com.nearnest`) in Firebase and add `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID`, and `FIREBASE_SENDER_ID` to ignored `local.properties`. Set `FIREBASE_CREDENTIALS_PATH` in `backend/.env` to a Firebase Admin service-account JSON file kept outside version control. Without these Firebase settings the app still builds, but push delivery stays disabled.

## Run on a USB-connected Android device

On Windows, connect one authorized Android device and double-click `build-and-launch.bat` to build, install, and open the app. For multiple connected devices, run `.\build-and-launch.bat -DeviceSerial <serial>` in Command Prompt or PowerShell.

1. Start FastAPI from `backend/` with `.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload`.
2. In another PowerShell window, run `adb reverse tcp:8001 tcp:8001`.
3. Set `API_BASE_URL=http://127.0.0.1:8001/api/v1` in ignored `local.properties` and build the Android app.

For an Android emulator, use `http://10.0.2.2:8001/api/v1`. For a phone without USB port forwarding, run `ngrok http 8001` and use the HTTPS forwarding address followed by `/api/v1` as `API_BASE_URL`.

The Android sign-in screen accepts a phone number or email and password. Passwords are hashed by the API; authenticated app requests carry a signed bearer JWT.
