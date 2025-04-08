**Sound Synthesis with Arduino and Mozzi Library.**

![play_store_512](https://github.com/user-attachments/assets/43109a78-5093-42fa-8bc2-35925024911d)



This project demonstrates FM + ASDR sine,saw,square synthesis using the Mozzi library on an ESP32. It features real-time Bluetooth control of the modulation index, automatic MIDI note transitions, and smooth frequency modulation for rich, evolving sound.

Features
FM synthesis(so far) with carrier and modulator oscillators.
Bluetooth control for live modulation index adjustment.
Smooth MIDI note transitions with customizable ranges and steps to simulate future midi changes.
Requirements
ESP32 (or compatible)
Speaker (64-ohm or similar)
Bluetooth module or on board bluetooth (e.g., HC-02)
Mozzi library 2.0.1
How It Works
The modulator frequency dynamically alters the carrier frequency.
Bluetooth input adjusts the modulation index in real time.
MIDI notes change automatically, producing dynamic tonal shifts. It can play midi song files.


![image](https://github.com/user-attachments/assets/772fdd1a-7bcb-4b13-b400-253fcaadeb4e)



