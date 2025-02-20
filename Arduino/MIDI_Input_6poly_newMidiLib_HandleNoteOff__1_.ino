#include <MozziGuts.h>
#include <Oscil.h>
#include <tables/saw2048_int8.h>  // Use sawtooth waveform
#include <SoftwareSerial.h>
#define AUDIO_RATE 8192
#define CONTROL_RATE 64
#define NUM_VOICES 4

// Four sawtooth wave oscillators
Oscil<SAW2048_NUM_CELLS, AUDIO_RATE> aSaw1(SAW2048_DATA);
Oscil<SAW2048_NUM_CELLS, AUDIO_RATE> aSaw2(SAW2048_DATA);
Oscil<SAW2048_NUM_CELLS, AUDIO_RATE> aSaw3(SAW2048_DATA);
Oscil<SAW2048_NUM_CELLS, AUDIO_RATE> aSaw4(SAW2048_DATA);

// Array to track which note each voice is playing (-1 means idle)
int currentNotes[NUM_VOICES] = {-1, -1, -1, -1};

SoftwareSerial btSerial(10, 11); // RX=10 (HC-02 TX), TX=11 (HC-02 RX)

void setup() {
  btSerial.begin(9600); // Bluetooth at 9600 baud
  Serial.begin(9600);   // USB debug
  delay(100);
  Serial.println("Ready");
  startMozzi(CONTROL_RATE);
  // Start all oscillators silent
  aSaw1.setFreq(0);
  aSaw2.setFreq(0);
  aSaw3.setFreq(0);
  aSaw4.setFreq(0);

}

char buffer[10]; // Buffer for "DOWN:XX\n" or "UP:XX\n"
int bufPos = 0;

void updateControl() {
  unsigned long start = micros();
  while (btSerial.available()) {
    char c = btSerial.read();
    


    if (c == '\n' && bufPos > 0) {
      buffer[bufPos] = '\0'; // Null-terminate
      String command = String(buffer);
      if (command.startsWith("DOWN:")) {
        int note = command.substring(5).toInt();
        int voice = -1;
        for (int i = 0; i < NUM_VOICES; i++) {
          if (currentNotes[i] == -1) { voice = i; break; }
        }
        if (voice == -1) voice = 0;
        currentNotes[voice] = note;
        float freq = mtof(note);
        switch (voice) {
          case 0: aSaw1.setFreq(freq); break;
          case 1: aSaw2.setFreq(freq); break;
          case 2: aSaw3.setFreq(freq); break;
          case 3: aSaw4.setFreq(freq); break;
        }
      } else if (command.startsWith("UP:")) {
        int note = command.substring(3).toInt();
        for (int i = 0; i < NUM_VOICES; i++) {
          if (currentNotes[i] == note) {
            currentNotes[i] = -1;
            switch (i) {
              case 0: aSaw1.setFreq(0); break;
              case 1: aSaw2.setFreq(0); break;
              case 2: aSaw3.setFreq(0); break;
              case 3: aSaw4.setFreq(0); break;
            }
            break;
          }
        }
      }
      bufPos = 0; // Reset buffer
    } else if (bufPos < sizeof(buffer) - 1) {
      buffer[bufPos++] = c;
    }
  }
  // ... existing updateControl code ...
unsigned long duration = micros() - start;
if (duration > 15625) Serial.println("Control took too long: " + String(duration));
}

int updateAudio() {
  // Mix all four voices and scale output
  int output = (aSaw1.next() + aSaw2.next() + aSaw3.next() + aSaw4.next()) >> 1; // Divide by 2 to avoid clipping
  return output;
}

void loop() {
  audioHook();
}

float mtof(float midiNote) {
  return 440.0 * pow(2.0, (midiNote - 69.0) / 12.0);
}
