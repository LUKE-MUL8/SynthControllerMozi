#include <SoftwareSerial.h>
#include <MozziGuts.h>
#include <Oscil.h>
#include <ADSR.h>
#include <tables/saw2048_int8.h> // Include saw wave table
#include <mozzi_midi.h>

// Declare Bluetooth communication
SoftwareSerial BTSerial(10, 11);  // RX, TX for HC-02 Bluetooth module

// Oscillator and ADSR setup
Oscil<SAW2048_NUM_CELLS, AUDIO_RATE> oscil1;
Oscil<SAW2048_NUM_CELLS, AUDIO_RATE> oscil2;
ADSR<CONTROL_RATE, AUDIO_RATE> envelope1;
ADSR<CONTROL_RATE, AUDIO_RATE> envelope2;

// MIDI note variables
byte note1 = 0, note2 = 0;  // Stores the current note
byte gain1 = 128, gain2 = 128;  // Increased gain for sound output

// Function declarations
void playNote(byte note);
void stopNote(byte note);

void setup() {
    Serial.begin(115200);    // For serial communication with the PC (debugging)
    BTSerial.begin(9600);    // For Bluetooth communication with HC-02 module

    // Set up oscillators and envelopes
    oscil1.setTable(SAW2048_DATA);
    oscil2.setTable(SAW2048_DATA);
    envelope1.setADLevels(255, 200);
    envelope1.setTimes(25, 25, 3000, 100);
    envelope2.setADLevels(255, 200);
    envelope2.setTimes(25, 25, 3000, 100);

    startMozzi(CONTROL_RATE);  // Start Mozzi Audio Library
    Serial.println("Setup complete. Waiting for Bluetooth data...");
}

void updateControl() {
    static String command = "";  // Buffer for incoming Bluetooth data

    while (BTSerial.available()) {
        char receivedChar = BTSerial.read();  // Read incoming byte
        Serial.print(receivedChar);           // Debug to Serial Monitor

        if (receivedChar == '\n') {  // Command ends with newline
            Serial.println("\nCommand received: " + command);

            if (command.startsWith("UP:")) {
                byte note = command.substring(3).toInt();
                playNote(note);  // Trigger note-on
            } else if (command.startsWith("DOWN:")) {
                byte note = command.substring(5).toInt();
                stopNote(note);  // Trigger note-off
            } else {
                Serial.println("Invalid command: " + command);
            }

            command = "";  // Clear buffer for next command
        } else {
            command += receivedChar;  // Append character to buffer
        }
    }
}

void playNote(byte note) {
    float freq = mtof(note);  // Convert MIDI note to frequency

    // Increase the pitch by a factor of 2 (one octave higher)
    freq *= 2;

    // Assign the note to the correct oscillator
    oscil1.setFreq(freq);  // First oscillator
    oscil2.setFreq(freq);  // Second oscillator

    envelope1.noteOn();  // Trigger envelope for the first oscillator
    envelope2.noteOn();  // Trigger envelope for the second oscillator

    Serial.print("Playing note: ");
    Serial.println(note);  // Log the note number
}


void stopNote(byte note) {
    envelope1.noteOff();  // Stop envelope for the first oscillator
    envelope2.noteOff();  // Stop envelope for the second oscillator

    Serial.print("Stopping note: ");
    Serial.println(note);
}

int updateAudio() {
    // Mix both voices together
    int output = 0;
    output += (gain1 * oscil1.next()) >> 8;
    output += (gain2 * oscil2.next()) >> 8;

    return output;
}

void loop() {
    audioHook();  
}
