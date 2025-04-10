**Sound Synthesis with ESP32 and Mozzi Library.**

![play_store_512](https://github.com/user-attachments/assets/43109a78-5093-42fa-8bc2-35925024911d)



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


![image](https://github.com/user-attachments/assets/04e6ab47-1b28-4d05-b885-b9f7f13bf4c8)




