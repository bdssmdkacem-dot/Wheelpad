"""WheelPad PC app: receives UDP from the phone and drives a virtual Xbox 360 gamepad.

Requires (Windows): pip install vgamepad   (installs the ViGEmBus driver)
Run:                python wheelpad_server.py
"""
import socket
import threading
import time
import tkinter as tk
from tkinter import ttk

try:
    import vgamepad as vg
except Exception:  # not installed / driver missing
    vg = None

PORT = 5005
BUTTON_NAMES = {1: "NITRO (A)", 2: "HORN (B)", 4: "CAM (Y)", 8: "GEAR+ (RB)", 16: "GEAR- (LB)"}


def local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))  # no packet is sent
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


class Server(threading.Thread):
    def __init__(self):
        super().__init__(daemon=True)
        self.stop_event = threading.Event()
        self.error = ""
        self.steer = self.gas = self.brake = 0.0
        self.mask = 0
        self.last_seen = 0.0
        self.phone_ip = ""

    def run(self):
        if vg is None:
            self.error = "vgamepad / ViGEmBus not installed (pip install vgamepad)"
            return
        try:
            pad = vg.VX360Gamepad()
        except Exception as e:
            self.error = f"Cannot create virtual gamepad: {e}"
            return

        B = vg.XUSB_BUTTON
        buttons = {
            1: B.XUSB_GAMEPAD_A, 2: B.XUSB_GAMEPAD_B, 4: B.XUSB_GAMEPAD_Y,
            8: B.XUSB_GAMEPAD_RIGHT_SHOULDER, 16: B.XUSB_GAMEPAD_LEFT_SHOULDER,
        }
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(("0.0.0.0", PORT))
        except OSError as e:
            self.error = f"Cannot open UDP port {PORT}: {e}"
            return
        sock.settimeout(0.3)

        held, neutral = 0, True
        while not self.stop_event.is_set():
            try:
                data, addr = sock.recvfrom(128)
            except socket.timeout:
                # Safety: release everything if the phone goes silent
                if not neutral and time.time() - self.last_seen > 1.0:
                    pad.reset()
                    pad.update()
                    held, neutral = 0, True
                    self.steer = self.gas = self.brake = 0.0
                    self.mask = 0
                continue
            except OSError:
                break

            if data.startswith(b"WHEELPAD_DISCOVER"):
                sock.sendto(b"WHEELPAD_HERE", addr)
                continue
            try:
                steer, gas, brake, mask = data.decode().split(",")
                self.steer, self.gas, self.brake = float(steer), float(gas), float(brake)
                self.mask = int(mask)
            except ValueError:
                continue

            pad.left_joystick_float(self.steer, 0.0)
            pad.right_trigger_float(self.gas)
            pad.left_trigger_float(self.brake)
            for bit, btn in buttons.items():
                if self.mask & bit and not held & bit:
                    pad.press_button(btn)
                elif not self.mask & bit and held & bit:
                    pad.release_button(btn)
            held = self.mask
            pad.update()
            neutral = False
            self.last_seen = time.time()
            self.phone_ip = addr[0]
        sock.close()


class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("WheelPad PC")
        self.geometry("380x330")
        self.resizable(False, False)
        self.server = Server()
        self.server.start()

        pad = {"padx": 12, "pady": 4}
        ttk.Label(self, text="WheelPad PC", font=("Segoe UI", 16, "bold")).pack(**pad)
        ttk.Label(self, text=f"PC address: {local_ip()}   (UDP {PORT})").pack(**pad)
        self.status = ttk.Label(self, text="Waiting for phone...", font=("Segoe UI", 11, "bold"))
        self.status.pack(**pad)

        ttk.Label(self, text="Steering").pack()
        self.steer_bar = ttk.Progressbar(self, length=320, maximum=200)
        self.steer_bar.pack(**pad)
        ttk.Label(self, text="Gas").pack()
        self.gas_bar = ttk.Progressbar(self, length=320, maximum=100)
        self.gas_bar.pack(**pad)
        ttk.Label(self, text="Brake").pack()
        self.brake_bar = ttk.Progressbar(self, length=320, maximum=100)
        self.brake_bar.pack(**pad)
        self.btn_label = ttk.Label(self, text="Buttons: -")
        self.btn_label.pack(**pad)

        ttk.Label(self, text="Tip: allow the app in Windows Firewall (private network).",
                  foreground="gray").pack(side="bottom", pady=6)
        self.protocol("WM_DELETE_WINDOW", self.on_close)
        self.after(50, self.refresh)

    def refresh(self):
        s = self.server
        if s.error:
            self.status.config(text=s.error, foreground="red")
        elif time.time() - s.last_seen < 1.5:
            self.status.config(text=f"Connected: {s.phone_ip}", foreground="green")
        else:
            self.status.config(text="Waiting for phone...", foreground="black")
        self.steer_bar["value"] = (s.steer + 1.0) * 100
        self.gas_bar["value"] = s.gas * 100
        self.brake_bar["value"] = s.brake * 100
        pressed = [n for b, n in BUTTON_NAMES.items() if s.mask & b]
        self.btn_label.config(text="Buttons: " + (", ".join(pressed) if pressed else "-"))
        self.after(50, self.refresh)

    def on_close(self):
        self.server.stop_event.set()
        self.destroy()


if __name__ == "__main__":
    App().mainloop()
