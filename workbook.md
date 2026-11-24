# HereBuy Project Workbook

This workbook is the file-by-file guide to the Android client, FastAPI service, database migration, and build setup. Kotlin and Python source files also carry a short file guide and function documentation; consult the sections below to find the right place to change behavior.

## Start Here

| Change | Start with |
| --- | --- |
| App launch, sign-in routing, or system Back | `app/src/main/kotlin/com/nearnest/MainActivity.kt` |
| Screen transitions, tabs, posted-items overlay, or sell flow | `app/src/main/kotlin/com/nearnest/ui/home/HomeScreen.kt` |
| Sign-in, signup, or OTP layout and validation | `app/src/main/kotlin/com/nearnest/ui/auth/` |
| Create a new item | `ui/sell/SellItemScreen.kt`, `data/remote/CreateListingRepository.kt`, and `backend/app/routers/listings.py` |
| Show a seller's own posted items | `ui/home/MyListingsScreen.kt`, `data/remote/MyListingsRepository.kt`, and `backend/app/routers/listings.py` |
| Nearby map, location, or listing pins | `ui/map/MapScreen.kt` and `data/remote/NearbyListingsRepository.kt` |
| Nearby new-item notifications or radius preference | `ui/home/ListingNotificationsScreen.kt`, `data/remote/ListingAlertRepository.kt`, `ui/home/HomeScreen.kt`, and `backend/app/routers/listings.py` |
| Pick an item's pickup point | `ui/map/PickupLocationPicker.kt` and the listing fields in `backend/app/schemas.py` |
| Change API fields or JSON names | Android `data/remote/ApiModels.kt` and backend `app/schemas.py` |
| Change authentication, tokens, or password handling | Android `data/remote/AuthRepository.kt`; backend `app/routers/auth.py` and `app/security.py` |
| Change database tables or startup schema setup | `backend/app/models.py`, `backend/app/database.py`, or `supabase/migrations/202611220001_nearnest_listings.sql` |
| Change API URL for a local Android build | `local.properties` (local-only); injection is in `app/build.gradle.kts` |
| Change Android dependencies or SDK settings | `app/build.gradle.kts` |
| Change backend runtime settings | `backend/.env` (local-only); template is `backend/.env.example` and parsing is in `backend/app/config.py` |

## Android App Source

### Activity and screens

| File | Main types and functions | Purpose / change notes |
| --- | --- | --- |
| `app/src/main/kotlin/com/nearnest/MainActivity.kt` | `MainActivity`: `onCreate`, `showAuthScreen`, `showSignupScreen`, `showSignupOtpScreen`, `showHomeScreen`, `showMessage`, `onStart`, `onResume`, `onPause`, `onStop`, `onLowMemory`, `onDestroy`, `onSaveInstanceState`, `onBackPressed`, `handleAppBack`, `onRequestPermissionsResult` | App entry point. Owns authentication routing, screen replacement, activity lifecycle forwarding, and platform Back handling. |
| `app/src/main/kotlin/com/nearnest/ui/auth/AuthScreen.kt` | `AuthScreen`: `createView`, `submit`, `inputField`, `fieldLayout`, `setLoading`, `showMessage`, `dp` | Sign-in form layout, validation, loading feedback, and submit callback. |
| `app/src/main/kotlin/com/nearnest/ui/auth/SignupScreen.kt` | `SignupScreen`: `createView`, `submit`, `inputField`, `fieldLayout`, `setLoading`, `showMessage`, `dp` | Signup form and client-side validation before requesting an email code. |
| `app/src/main/kotlin/com/nearnest/ui/auth/SignupOtpScreen.kt` | `SignupOtpScreen`: `createView`, `verify`, `resend`, `setLoading`, `showMessage`, `dp` | Email OTP entry, verify and resend actions, and status feedback. |
| `app/src/main/kotlin/com/nearnest/ui/home/HomeScreen.kt` | `HomeScreen`: `createView`, `dismissPicker`, `closeMyListings`, `closeSellScreen`, `showSellScreen`, `showMyListings`, `handleBackPressed`, `onRequestPermissionsResult`, `onStart`, `onResume`, `onPause`, `onStop`, `onLowMemory`, `onDestroy`, `onSaveInstanceState`, `startAlertPolling`, `restartAlertPolling`, `showAlertRadiusDialog`, `dp`, `isCompactLayout` | Main authenticated screen and navigation owner. Back order is pickup picker → sell form → posted items → profile/tab → app exit. Update this file when adding a top-level screen or changing overlay return behavior. |
| `app/src/main/kotlin/com/nearnest/ui/home/BottomNavBar.kt` | `BottomNavBar`: `select`, `selectDestination`, `setNotificationCount`, `dp`; `BarSurface.onDraw` | Bottom navigation appearance and selection callbacks. The raised center action opens the user's posted items. |
| `app/src/main/kotlin/com/nearnest/ui/home/ListingNotificationsScreen.kt` | `ListingNotificationsScreen`: `createView`, `open`, `refresh`, `render`, `addAlertCard`, `dp` | In-app feed of new live listings within the configured radius. |
| `app/src/main/kotlin/com/nearnest/ui/home/MyListingsScreen.kt` | `MyListingsScreen`: `createView`, `onDestroy`, `refresh`, `load`, `render`, `dp` | Loads and displays seller-owned items, plus empty, loading, and error states. |
| `app/src/main/kotlin/com/nearnest/ui/sell/SellItemScreen.kt` | `SellItemScreen`: `createView`, `onDestroy`, `submit`, `setLoading`, `showError`, `updatePickupSummary`, `sectionTitle`, `input`, `fieldBackground`, `fieldLayout`, `dp` | Listing editor. Add or alter item fields here and update API models/schema/repository together. |
| `app/src/main/kotlin/com/nearnest/ui/profile/ProfileScreen.kt` | `ProfileScreen`: `createView`, `refresh`, `accountCard`, `sectionCard`, `cardContainer`, `actionButton`, `cardLayout`, `dp` | Account details, privacy copy, app information, location settings link, and sign-out action. |

### Maps

| File | Main types and functions | Purpose / change notes |
| --- | --- | --- |
| `app/src/main/kotlin/com/nearnest/ui/map/MapScreen.kt` | `MapScreen`: `createView`, `loadMap`, `requestLocationAccess`, `onRequestPermissionsResult`, `hasLocationPermission`, `onMyLocationButtonClicked`, `showUserLocation`, `loadListingsInViewport`, `currentMapCenter`, `refreshNearbyListings`, `setBottomObstructionPx`, `setLabelsVisible`, `resetMapOrientation`, `onStart`, `onResume`, `onPause`, `onStop`, `onLowMemory`, `onDestroy`, `onSaveInstanceState`, `dp` | Map lifecycle, optional location permission, viewport-based listing pins, and camera controls. |
| `app/src/main/kotlin/com/nearnest/ui/map/PickupLocationPicker.kt` | `PickupPoint`; `PickupLocationPicker`: `createView`, local `updateSelectedPoint`, `onStart`, `onResume`, `onPause`, `onStop`, `onLowMemory`, `onSaveInstanceState`, `onDestroy`, `roundedCard`, `dp`; `CenterPin.onDraw`, `CenterPin.dp` | Seller-selected pickup point UI. Change marker or map selection behavior here. |
| `app/src/main/assets/map/nearnest-map-style.json` | No functions | MapLibre style document. JSON must remain valid JSON, so its purpose is documented here rather than with inline comments. |

### Android API client and data

| File | Main types and functions | Purpose / change notes |
| --- | --- | --- |
| `app/src/main/kotlin/com/nearnest/data/remote/ApiClientProvider.kt` | `ApiClientProvider` | Shared Ktor client, base URL, response serialization, and request defaults. |
| `app/src/main/kotlin/com/nearnest/data/remote/ApiHttp.kt` | `ApiException`; `HttpResponse.requireSuccess` | Shared unsuccessful-response handling and API error model. |
| `app/src/main/kotlin/com/nearnest/data/remote/ApiModels.kt` | `ApiUser`, `AuthResponse`, `PasswordLoginRequest`, `SignupStartRequest`, `SignupOtpResponse`, `SignupVerifyRequest`, `SignupResendRequest`, `ListingItem` | Serialized request and response shapes. Match backend snake_case fields with `SerialName`. |
| `app/src/main/kotlin/com/nearnest/data/remote/AuthRepository.kt` | `AuthRepository`: `hasSession`, `getAccessToken`, `fetchCurrentUser`, `signIn`, `beginSignup`, `verifySignup`, `resendSignupCode`, `signOut`, `saveSession`, `encrypt`, `decrypt`, `getOrCreateKey` | Authentication API calls and Android Keystore-backed token persistence. |
| `app/src/main/kotlin/com/nearnest/data/remote/CreateListingRepository.kt` | `CreateListingRepository.createListing`; `NewListing` | Sends a new item to the authenticated API. `NewListing` is the JSON request body; update it when adding listing fields. |
| `app/src/main/kotlin/com/nearnest/data/remote/MyListingsRepository.kt` | `MyListingsRepository.getMine` | Fetches the current user's posted listings. |
| `app/src/main/kotlin/com/nearnest/data/remote/NearbyListing.kt` | `NearbyListing` | Model for map discovery results, including distance. |
| `app/src/main/kotlin/com/nearnest/data/remote/NearbyListingsRepository.kt` | `NearbyListingsRepository.getInViewport` | Validates map bounds and requests every available listing visible in the current viewport. |
| `app/src/main/kotlin/com/nearnest/data/remote/ListingAlertRepository.kt` | `ListingAlertRepository`: `getRadiusKm`, `setRadiusKm`, `getAlerts`, `getUnreadCount`, `markRead`, `checkForNewListings`, `cacheNewAlerts`, `readUnreadIds` | Persists radius and inbox locally, fetches incremental alerts, and tracks unread listing IDs. |

## Backend Source

| File | Main types and functions | Purpose / change notes |
| --- | --- | --- |
| `backend/app/main.py` | `lifespan`, `log_api_requests`, `health`, `app`; `api_logger` | FastAPI setup, startup schema preparation, router mounting, health route, and structured API request logging. Windows launch scripts route all output to the single `backend/logs/api.log` file. |
| `backend/app/config.py` | `validate_settings`; environment constants | Loads `.env`, selects database URL, and checks required settings. |
| `backend/app/database.py` | `Base`, `prepare_development_schema`, `get_db`; `engine`, `SessionLocal` | SQLAlchemy setup, compatibility adjustments for SQLite/PostgreSQL, and request-scoped sessions. |
| `backend/app/dependencies.py` | `get_current_user` | Validates the bearer token and resolves the authenticated user. |
| `backend/app/email_sender.py` | `send_signup_otp` | SMTP delivery for signup verification messages. |
| `backend/app/models.py` | `utc_now`; `User`, `SignupVerification`, `Listing` | SQLAlchemy persistence models and listing status/location indexes. Keep API schemas and database migration aligned with model changes. |
| `backend/app/schemas.py` | `SignupStart`, `SignupVerify`, `SignupResend`, `SignupOtpResponse`, `UserLogin`, `UserPublic`, `AuthResponse`, `ListingCreate`, `ListingUpdate`, `ListingPublic`, `NearbyListing`, `ListingAlertsResponse` | Request validation and response serialization. This is the main place to change API constraints and fields. |
| `backend/app/security.py` | `hash_password`, `verify_password`, `create_access_token`, `decode_access_token`, `generate_signup_otp`, `hash_signup_otp`, `verify_signup_otp` | Password hashing, signed session tokens, and OTP generation/verification. |
| `backend/app/routers/auth.py` | `make_auth_response`, `_now`, `_aware`, `_send_challenge`, `start_signup`, `resend_signup_code`, `verify_signup`, `log_in`, `read_me` | Auth endpoints and signup challenge lifecycle. Add or change auth routes here. |
| `backend/app/routers/listings.py` | `distance_meters`, `get_nearby_listings`, `get_listings_in_viewport`, `get_item_alerts`, `create_listing`, `get_my_listings`, `get_listing`, `update_listing`, `delete_listing` | Listing API. The Android map uses `get_listings_in_viewport` for visible map pins; `get_item_alerts` returns new available listings from other sellers within the chosen radius. Seller edits and deletes are owner-checked. |
| `backend/app/routers/__init__.py` | No functions | Python package marker for API routers. |
| `backend/app/__init__.py` | No functions | Python package marker for the backend application. |

## Database

| File | Purpose |
| --- | --- |
| `supabase/migrations/202611220001_nearnest_listings.sql` | Creates the PostGIS listings table and indexes, enables row-level security, defines seller policies, and adds `public.get_nearby_listings`. Run through the Supabase SQL editor when applying the hosted database migration. |

## Build, Runtime, and Documentation Files

| File | Purpose / change notes |
| --- | --- |
| `README.md` | Root project setup and overview; links back to this workbook. |
| `backend/README.md` | Backend setup and operation guide. |
| `app/build.gradle.kts` | Android SDK, app ID, API URL BuildConfig injection, Java/Kotlin compatibility, and Android dependencies. `asBuildConfigString` escapes a value for generated Kotlin source. |
| `build.gradle.kts` | Root plugin versions shared by Gradle modules. |
| `settings.gradle.kts` | Plugin/dependency repositories and included modules. |
| `gradle.properties` | Shared Gradle JVM and AndroidX/Kotlin settings. |
| `gradle/gradle-daemon-jvm.properties` | Gradle daemon JVM selection. |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle distribution version used by the wrapper. |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` | Generated Gradle wrapper launchers and bootstrap binary; use these to run builds, and do not hand-edit the wrapper implementation. |
| `app/lint.xml` | Android lint exceptions. |
| `app/src/main/AndroidManifest.xml` | App permissions, launcher activity, label, icon, and theme. |
| `app/src/debug/AndroidManifest.xml` | Debug-only network behavior. Keep debug relaxations out of release configuration. |
| `backend/.env.example` | Safe local backend settings template; copy it to ignored `backend/.env` and supply a real secret locally. |
| `backend/requirements.txt` | Python dependency ranges for the FastAPI backend. |
| `backend/start-local.ps1` | Creates/uses `.venv`, prepares local `.env`, installs requirements, and starts Uvicorn with reload. |
| `backend/start-background.ps1` | Runs backend Uvicorn in a restart loop and stores logs under ignored `backend/logs/`. |
| `backend/install-startup-task.ps1` | Registers the background backend runner as a Windows startup task. |
| `.gitignore`, `.idea/.gitignore` | Ignore generated builds, local secrets/data, logs, installers, and IDE-generated files. |

## Android Resources and Brand Assets

These files are referenced by name from Android screens, manifests, or styles. They do not contain functions; use this table to locate visual changes.

| File(s) | Purpose |
| --- | --- |
| `app/src/main/res/values/colors.xml` | Shared Android color values. |
| `app/src/main/res/values/styles.xml` | App theme and shared Android styling. |
| `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` | Launcher icon layers. |
| `app/src/main/res/drawable/ic_map_compass.xml`, `ic_map_settings.xml`, `ic_my_location.xml` | Map controls. |
| `app/src/main/res/drawable/ic_nav_cart.xml`, `ic_nav_chat.xml`, `ic_nav_explore.xml`, `ic_nav_home.xml`, `ic_nav_notifications.xml`, `ic_nav_profile.xml`, `ic_nav_saved.xml`, `ic_nav_settings.xml` | Navigation icons. |
| `app/src/main/res/drawable/ic_search.xml` | Search icon. |
| `app/src/main/res/mipmap-anydpi/ic_launcher.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive and legacy launcher icon definitions. |
| `nearnest-blue.png`, `nearnest-rpg-blue.png`, `nearnest-rpg.png` | Raster brand assets used for project branding or future app artwork. |

XML resource files have a short inline purpose comment. PNG, JAR, wrapper, and strict JSON assets cannot carry useful source comments; their roles are documented here instead. Local-only files such as `local.properties`, `backend/.env`, the SQLite database, logs, and downloaded installers are deliberately excluded from the project source map because they are machine-specific or generated.
