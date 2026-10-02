# WheelPad

Turn your Android phone into a racing-game controller for PC: tilt/rotate the phone like a steering wheel, with gas/brake/nitro/horn/camera/gear buttons. The PC app exposes a virtual Xbox 360 gamepad (ViGEmBus), so controller-supported games can use it.

## PC setup — ready to install

The Windows build artifact contains:

- **WheelPadServer.exe** — the PC server.
- **Install-WheelPad.bat** — one-time installer.
- **Uninstall-WheelPad.bat** — removes the server and its Windows settings.

### Installation

1. Download the **WheelPad-PC-Windows** artifact from GitHub Actions and extract it.
2. Make sure **ViGEmBus** is installed on Windows. The `vgamepad` package uses it to create the virtual Xbox 360 controller.
3. Double-click **Install-WheelPad.bat**.
4. Approve the Windows Administrator prompt.
5. The installer:
   - installs `WheelPadServer.exe` into `C:\Program Files\WheelPad\`;
   - opens **UDP 5005** for **Private networks** in Windows Firewall;
   - creates a Windows Startup shortcut;
   - starts WheelPadServer immediately.

After this, the PC server starts automatically when the current Windows user signs in.

### Phone connection

1. Put the phone and PC on the same Wi-Fi network.
2. Open WheelPad on the phone.
3. Go to **Settings → Find PC**.
4. If discovery is blocked by the network, enter the PC's local IP manually.
5. Press **Calibrate** and use the phone as the steering wheel.

The server window shows the PC IP, connection status, steering, gas, brake and pressed buttons.

### Important Windows note

The firewall rule is intentionally limited to **UDP port 5005 on Private networks**. Public-network traffic is not opened by the installer.

If the PC is on a Public Wi-Fi profile, change that network to Private in Windows or use manual firewall configuration.

## Manual development run

```text
cd pc
pip install -r requirements.txt
python wheelpad_server.py
```

## Protocol (UDP 5005)

Phone → PC: `steer,gas,brake,mask`

- steer: -1..1
- mask bit 1: A / Nitro
- mask bit 2: B / Horn
- mask bit 4: Y / Camera
- mask bit 8: RB / Gear+
- mask bit 16: LB / Gear-

Discovery: phone broadcasts `WHEELPAD_DISCOVER`, PC answers `WHEELPAD_HERE`.

## Roadmap

- Haptic feedback
- D-pad / Start buttons
- Multiple layout profiles
- Bluetooth HID mode

---

**بالعربية:** نسخة Windows أصبحت جاهزة للمستخدم: شغّل `Install-WheelPad.bat` مرة واحدة، وسيتم تثبيت `WheelPadServer.exe`، فتح UDP 5005 في جدار حماية Windows للشبكات الخاصة، وإضافة التشغيل التلقائي مع Windows. بعد ذلك افتح التطبيق في الهاتف واضغط **Settings → Find PC**.
