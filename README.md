**Sound Synthesis with ESP32 and Mozzi Library.**

<img width="512" height="512" alt="play_store_512" src="https://github.com/user-attachments/assets/197c734e-c260-4d18-adcd-c3ebbff83525" />



This project demonstrates FM + ASDR sine,saw,square,triangle synthesis using the Mozzi library on an ESP32. It features real-time Bluetooth control of the modulation index, vibrato control, MIDI note transitions, and smooth frequency modulation for rich, evolving sound. The use of android-midi-lib allow the app to parse midi files and putput them as midi commands to the esp32 to play autonomously.

Features
FM synthesis(so far) with carrier and modulator oscillators.
Bluetooth control for live modulation index adjustment.
Smooth MIDI note transitions with customizable octaves on an on screen keyboard with optional horizonal or portrait view.
Requirements
ESP32 (or compatible)
Speaker/ aux lead (64-ohm or similar)
headphone jack module. ( I used [this](https://www.amazon.com/Onyehn-Breakout-Stereo-Headphone-Arduino/dp/B07L3P93ZD?crid=3U1P5G5C1RPX1&dib=eyJ2IjoiMSJ9.16_bxuKGtxC3N7N542JPvuOkqX_mjVk4FWJPMIaGTYOVujmb4fNn88lOGWL6B_15k6EJR-VCcBrF5nQB7Y4eHyXaaGrBIviXSc1qNkBMGgmBBgoom_OAEpAsbdmp1kTJj10Epz6iU93wDfsLV9lmX26XbT5f-UFQPoRzfjxnEGzEtV-K9TMpdr5c-9CXICYCzzi9ABXgkH0Gspmm_LpyMdDOSKSfdpkN2yy8bnX9UBk.J3T0CNXuFjwoh1wGMnQqQvaT5fhesqactNGbheZl-gE&dib_tag=se&keywords=jack+module+for+arduino&qid=1741098241&sprefix=jack+module+for+arduino,aps,214&sr=8-1) )

Bluetooth module or on board bluetooth (e.g., HC-02)
Mozzi library 2.0.1
How It Works
The modulator frequency dynamically alters the carrier frequency.
Bluetooth input adjusts the modulation index in real time.
MIDI notes change automatically, producing dynamic tonal shifts. It can play midi song files.


<img width="1316" height="2560" alt="Screenshot_20260104_154933" src="https://github.com/user-attachments/assets/b697ea25-1667-42da-9dde-8710ea5fa2af" />
<img width="2560" height="1316" alt="image" src="https://github.com/user-attachments/assets/5767ac94-30d7-4aaa-8db0-1fc28405f560" />

<img width="1316" height="2560" alt="image" src="https://github.com/user-attachments/assets/c2e9214a-59f8-4c0d-b73b-987fa457d239" />







