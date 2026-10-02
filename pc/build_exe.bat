@echo off
pip install -r requirements.txt pyinstaller
pyinstaller --onefile --noconsole --collect-all vgamepad --name WheelPadServer wheelpad_server.py
echo Done: dist\WheelPadServer.exe
