# WheelPad

Turn your Android phone into a racing-game controller for PC: tilt/rotate the phone like a steering wheel, with gas/brake/nitro/horn/camera/gear buttons. The PC app exposes a virtual Xbox 360 gamepad (ViGEmBus), so any game with controller support works.

```
android/   Android app (Kotlin + Jetpack Compose)
pc/        PC link app (Python + tkinter + vgamepad)
.github/   CI: builds the APK and the Windows exe
```

## Quick start
1. **PC (Windows):** `cd pc && pip install -r requirements.txt && python wheelpad_server.py`
   (or download `WheelPadServer.exe` from the Actions artifacts). Allow it in the firewall (private network).
2. **Phone:** open `android/` in Android Studio and run, or install the APK from the Actions artifacts.
3. Same Wi-Fi on both. In the app: **Settings → Find PC** (or type the PC address), then hold the phone in landscape like a wheel and press **Calibrate**.

## App features
- Steering by rotation (max angle, deadzone, smoothing, invert, calibrate)
- Draggable buttons: **Edit** → move, resize, change opacity; **Reset layout**
- Settings and layout saved on the phone

## Protocol (UDP 5005)
Phone → PC: `steer,gas,brake,mask` (e.g. `-0.420,1,0,5`); steer −1..1, mask bits: 1 A/nitro, 2 B/horn, 4 Y/camera, 8 RB/gear+, 16 LB/gear−.
Discovery: phone broadcasts `WHEELPAD_DISCOVER`, PC answers `WHEELPAD_HERE`.

## Roadmap
- Haptic feedback, D-pad/Start buttons, multiple layout profiles
- Bluetooth HID mode (no PC app)
- Console support (PlayStation requires licensed-controller auth; Remote Play on PC is the realistic path)

---
**بالعربية:** حوّل هاتفك إلى يد تحكم لألعاب السيارات على PC. شغّل تطبيق PC ثم التطبيق على الهاتف (نفس شبكة Wi-Fi)، اضغط Settings ثم Find PC، وبعدها Calibrate.
