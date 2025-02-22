#define AUDIO_MODE STANDARD_PLUS
#define AUDIO_RATE 16384 // Higher quality, still viable on Uno
#include <MozziGuts.h>
#include <Oscil.h>
#include <tables/saw1024_int8.h> // Smaller table for memory
#include <SoftwareSerial.h>

#define CONTROL_RATE 128 // Faster updates
#define NUM_VOICES 4

Oscil<SAW1024_NUM_CELLS, AUDIO_RATE> aSaw1(SAW1024_DATA);
Oscil<SAW1024_NUM_CELLS, AUDIO_RATE> aSaw2(SAW1024_DATA);
Oscil<SAW1024_NUM_CELLS, AUDIO_RATE> aSaw3(SAW1024_DATA);
Oscil<SAW1024_NUM_CELLS, AUDIO_RATE> aSaw4(SAW1024_DATA);

int currentNotes[NUM_VOICES] = {-1, -1, -1, -1};
unsigned long voiceTimes[NUM_VOICES] = {0, 0, 0, 0};
SoftwareSerial btSerial(10, 11);

// Precomputed MIDI frequencies in PROGMEM
const float midiFreqs[128] PROGMEM = {
  8.18, 8.66, 9.18, 9.72, 10.30, 10.91, 11.56, 12.25, 12.98, 13.75, 14.57, 15.43,
  16.35, 17.32, 18.35, 19.45, 20.60, 21.83, 23.12, 24.50, 25.96, 27.50, 29.14, 30.87,
  32.70, 34.65, 36.71, 38.89, 41.20, 43.65, 46.25, 49.00, 51.91, 55.00, 58.27, 61.74,
  65.41, 69.30, 73.42, 77.78, 82.41, 87.31, 92.50, 98.00, 103.83, 110.00, 116.54, 123.47,
  130.81, 138.59, 146.83, 155.56, 164.81, 174.61, 185.00, 196.00, 207.65, 220.00, 233.08, 246.94,
  261.63, 277.18, 293.66, 311.13, 329.63, 349.23, 369.99, 392.00, 415.30, 440.00, 466.16, 493.88,
  523.25, 554.37, 587.33, 622.25, 659.25, 698.46, 739.99, 783.99, 830.61, 880.00, 932.33, 987.77,
  1046.50, 1108.73, 1174.66, 1244.51, 1318.51, 1396.91, 1479.98, 1567.98, 1661.22, 1760.00, 1864.66, 1975.53,
  2093.00, 2217.46, 2349.32, 2489.02, 2637.02, 2793.83, 2959.96, 3135.96, 3322.44, 3520.00, 3729.31, 3951.07,
  4186.01, 4434.92, 4698.63, 4978.03, 5274.04, 5587.65, 5919.91, 6271.93, 6644.88, 7040.00, 7458.62, 7902.13
};

void setup() {
  Serial.begin(9600);
  delay(100);
  Serial.println("Ready");
  btSerial.begin(9600); // Fixed at 9600 baud
  delay(100);
  startMozzi(CONTROL_RATE);
  aSaw1.setFreq(0);
  aSaw2.setFreq(0);
  aSaw3.setFreq(0);
  aSaw4.setFreq(0);
  Serial.println("Start");
}

char buffer[10];
int bufPos = 0;

void updateControl() {
  while (btSerial.available()) {
    char c = btSerial.read();
    if (c == '\n' && bufPos > 0) {
      buffer[bufPos] = '\0';
      if (buffer[0] == 'D') {
        int note = atoi(buffer + 5);
        if (note >= 0 && note < 128) {
          int voice = -1;
          for (int i = 0; i < NUM_VOICES; i++) {
            if (currentNotes[i] == -1) { voice = i; break; }
          }
          if (voice == -1) {
            unsigned long oldest = voiceTimes[0];
            voice = 0;
            for (int i = 1; i < NUM_VOICES; i++) {
              if (voiceTimes[i] < oldest) {
                oldest = voiceTimes[i];
                voice = i;
              }
            }
          }
          currentNotes[voice] = note;
          voiceTimes[voice] = millis();
          float freq = pgm_read_float(&midiFreqs[note]);
          switch (voice) {
            case 0: aSaw1.setFreq(freq); break;
            case 1: aSaw2.setFreq(freq); break;
            case 2: aSaw3.setFreq(freq); break;
            case 3: aSaw4.setFreq(freq); break;
          }
        }
      } else if (buffer[0] == 'U') {
        int note = atoi(buffer + 3);
        for (int i = 0; i < NUM_VOICES; i++) {
          if (currentNotes[i] == note) {
            currentNotes[i] = -1;
            voiceTimes[i] = 0;
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
      bufPos = 0;
    } else if (bufPos < sizeof(buffer) - 1) {
      buffer[bufPos++] = c;
    }
  }
}

int updateAudio() {
  int sum = aSaw1.next() + aSaw2.next() + aSaw3.next() + aSaw4.next();
  return sum >> 2; // Fixed scaling, reliable up to 4 voices
}

void loop() {
  audioHook();
}