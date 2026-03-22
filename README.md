# PRMS Flutter App — Build Guide

## Quick Reference
- **API Base**: https://genusitsolution.com/rentalsolutionfinal/api/v1
- **Min SDK**: Android 5.0 (API 21)
- **Target SDK**: Android 14 (API 34)
- **Flutter**: 3.22.x

---

## Option A — Docker Build (Recommended, Zero Setup)

### Prerequisites
- Docker Desktop installed (Windows/Mac/Linux)
- 8 GB RAM recommended (build takes ~20 min first time)

```bash
# 1. Go to the project folder
cd prms_flutter/

# 2. Build the Docker image (one-time, ~15 min)
docker build -t prms-builder .

# 3. Run the build — APK saved to ./output/
docker run --rm -v "$(pwd)/output:/output" prms-builder

# 4. Your APKs are in:
#    output/app-arm64-v8a-release.apk   (64-bit — use this for modern phones)
#    output/app-armeabi-v7a-release.apk  (32-bit — older devices)
```

### Install on Android
```bash
# Via USB cable (ADB)
adb install output/app-arm64-v8a-release.apk

# Or just copy the APK to your phone and open it
# (Enable "Install from Unknown Sources" in Android Settings first)
```

---

## Option B — Local Flutter Setup

### Prerequisites
1. Flutter 3.22+ installed → https://flutter.dev/docs/get-started/install
2. Android Studio with SDK (API 34)
3. Java 17

```bash
# Check setup
flutter doctor

# Install dependencies
flutter pub get

# Debug build (fast, for testing)
flutter build apk --debug

# Release build (optimized)
flutter build apk --release --split-per-abi

# APK location:
# build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
```

---

## Change API URL

Edit `lib/config/constants.dart`:
```dart
static const String baseUrl =
    'https://YOUR_SERVER/rentalsolutionfinal/api/v1';
```
Then rebuild.

---

## Folder Structure

```
lib/
├── main.dart                    ← App entry point
├── config/
│   ├── constants.dart           ← API URLs
│   └── theme.dart               ← Colors, fonts, styles
├── providers/
│   └── auth_provider.dart       ← Login/logout state
├── services/
│   ├── api_service.dart         ← HTTP client
│   └── storage_service.dart     ← SharedPreferences
├── widgets/
│   ├── stat_card.dart           ← Reusable stat cards
│   └── common_widgets.dart      ← Shared UI components
└── screens/
    ├── splash_screen.dart
    ├── login_screen.dart
    ├── profile_screen.dart
    ├── owner/
    │   ├── owner_home.dart      ← Bottom nav host
    │   ├── owner_dashboard.dart ← Stats + quick actions
    │   ├── properties_screen.dart
    │   ├── tenants_screen.dart
    │   ├── employees_screen.dart
    │   └── invoices_screen.dart
    ├── employee/
    │   ├── employee_home.dart
    │   ├── employee_dashboard.dart
    │   ├── collections_screen.dart
    │   └── emp_tenants_screen.dart
    └── tenant/
        ├── tenant_home.dart
        ├── tenant_dashboard.dart
        └── tenant_invoices_screen.dart
```

---

## Login Credentials (from your server)

| Role   | Username | Notes              |
|--------|----------|--------------------|
| Owner  | abhi     | From your DB       |
| Agent  | (agent username) | Any employee login |
| Tenant | (tenant username) | Any tenant login |

Role is auto-detected from backend — no dropdown needed.

---

## Features

### Login Screen
- Modern card-based UI with gradient header
- Auto role detection (no dropdown)
- Error toast messages
- Session persistence (stay logged in)

### Owner Dashboard
- Greeting + date
- Monthly revenue gradient card
- Properties / Tenants / Dues / Agents stats
- Quick action buttons (Add Property, Add Tenant, Invoices, Reports)
- Recent invoices list
- Offline fallback with dummy data

### Owner Properties
- Card list with status badges
- Occupied/Vacant filter chips
- Add property bottom sheet form
- Edit/Delete actions

### Owner Tenants
- Search bar
- Tenant cards with property + phone
- Status badges + rent display

### Owner Agents/Employees
- Performance ranking (#1, #2, #3)
- Collections this month
- Assigned properties count

### Owner Invoices
- Tab view: All / Pending / Paid / Overdue
- Progress bar per invoice
- Record Payment bottom sheet

### Employee Dashboard
- Orange gradient theme
- Collections stats
- Recent collections list

### Employee Collections
- Add collection bottom sheet
- Payment mode selector (Cash/UPI/Bank/Cheque)
- History with receipt numbers

### Tenant Dashboard
- Teal gradient theme
- Payment due alert banner
- Property allocation info
- Invoice history

### Profile Screen (All Roles)
- User info display
- Change password
- Logout with confirmation

---

## Offline Mode
All screens show dummy/cached data if API fails — no blank screens.
A yellow banner appears when in offline mode.

---

## Security
- Tokens stored in SharedPreferences
- Auto-restored on app restart
- Logout clears all local data
- HTTPS enforced (update your server SSL)
