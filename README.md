Sound Synthesis with Arduino and Mozzi Library.


This project demonstrates FM + ASDR sine,saw,square synthesis using the Mozzi library on an Arduino. It features real-time Bluetooth control of the modulation index, automatic MIDI note transitions, and smooth frequency modulation for rich, evolving sound.

Features
FM synthesis(so far) with carrier and modulator oscillators.
Bluetooth control for live modulation index adjustment.
Smooth MIDI note transitions with customizable ranges and steps to simulate future midi changes.
Requirements
Arduino Uno (or compatible)
Speaker (64-ohm or similar)
Bluetooth module (e.g., HC-02)
Mozzi library
How It Works
The modulator frequency dynamically alters the carrier frequency.
Bluetooth input adjusts the modulation index in real time.
MIDI notes change automatically, producing dynamic tonal shifts.
