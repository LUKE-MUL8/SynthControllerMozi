#define AUDIO_MODE STANDARD_PLUS
#define AUDIO_RATE 16384 // Higher quality within Uno limits
#include <MozziGuts.h>
#include <Oscil.h>
#include <ADSR.h>
#include <tables/saw1024_int8.h> // Smaller sawtooth table for carrier
#include <tables/sin1024_int8.h> // Smaller sine table for modulator
#include <SoftwareSerial.h>

#define CONTROL_RATE 128 // Faster updates for MIDI responsiveness
#define NUM_VOICES 4     // Max voices for Uno

// Oscillators and envelopes
Oscil<SAW1024_NUM_CELLS, AUDIO_RATE> carriers[NUM_VOICES];
Oscil<SIN1024_NUM_CELLS, AUDIO_RATE> modulators[NUM_VOICES];
ADSR<CONTROL_RATE, AUDIO_RATE> envelopes[NUM_VOICES];

// Voice management
struct Voice {
  byte note;
  bool active;
  unsigned long startTime;
};
Voice voices[NUM_VOICES];

// Bluetooth
SoftwareSerial BTSerial(10, 11); // RX=10, TX=11

// FM and envelope parameters
int attack = 10, decay = 20, sustain = 128, release = 50;
float modulationIndex = 2.0f;
float modToCarrierRatio = 2.0f;
int transpose = 0, octaveShift = 2;

// Precomputed frequencies in PROGMEM
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
  BTSerial.begin(9600); // Fixed at 9600 baud
  delay(100);

  // Initialize voices and oscillators
  for (int i = 0; i < NUM_VOICES; i++) {
    carriers[i].setTable(SAW1024_DATA);
    modulators[i].setTable(SIN1024_DATA);
    envelopes[i].setADLevels(255, sustain);
    envelopes[i].setTimes(attack, decay, sustain, release);
    voices[i].note = 0;
    voices[i].active = false;
    voices[i].startTime = 0;
  }

  startMozzi(CONTROL_RATE);
  Serial.println("Start");
}

char buffer[10];
int bufPos = 0;

void updateControl() {
  while (BTSerial.available()) {
    char c = BTSerial.read();
    if (c == '\n' && bufPos > 0) {
      buffer[bufPos] = '\0';
      parseCommand(buffer);
      bufPos = 0;
    } else if (bufPos < sizeof(buffer) - 1) {
      buffer[bufPos++] = c;
    }
  }

  // Update envelopes
  for (int i = 0; i < NUM_VOICES; i++) {
    if (voices[i].active) {
      envelopes[i].update();
      if (!envelopes[i].playing()) { // Deactivate if envelope finishes
        voices[i].active = false;
        carriers[i].setFreq(0);
        modulators[i].setFreq(0);
      }
    }
  }
}

void parseCommand(const char* command) {
  int separatorIndex = -1;
  for (int i = 0; command[i] != '\0'; i++) {
    if (command[i] == ':') {
      separatorIndex = i;
      break;
    }
  }
  if (separatorIndex == -1) return;

  String action = String(command).substring(0, separatorIndex);
  int value = atoi(command + separatorIndex + 1);

  if (action == "DOWN") {
    playNote(value);
  } else if (action == "UP") {
    stopNote(value);
  } else if (action == "ATTACK") {
    attack = value;
    updateEnvelopes();
  } else if (action == "DECAY") {
    decay = value;
    updateEnvelopes();
  } else if (action == "SUSTAIN") {
    sustain = value;
    updateEnvelopes();
  } else if (action == "RELEASE") {
    release = value;
    updateEnvelopes();
  } else if (action == "FILTER" || action == "DETUNE") {
    // Placeholder for future use
  } else if (action == "PANIC") {
    midiPanic();
  }
}

void updateEnvelopes() {
  for (int i = 0; i < NUM_VOICES; i++) {
    envelopes[i].setADLevels(255, sustain);
    envelopes[i].setTimes(attack, decay, sustain, release);
  }
}

void playNote(byte note) {
  note += transpose;
  float carrierFreq = pgm_read_float(&midiFreqs[note + 12 * octaveShift]);
  float modFreq = carrierFreq * modToCarrierRatio;

  int voiceIndex = -1;
  for (int i = 0; i < NUM_VOICES; i++) {
    if (!voices[i].active) {
      voiceIndex = i;
      break;
    }
  }
  if (voiceIndex == -1) { // Voice stealing: oldest active voice
    unsigned long oldest = voices[0].startTime;
    voiceIndex = 0;
    for (int i = 1; i < NUM_VOICES; i++) {
      if (voices[i].active && voices[i].startTime < oldest) {
        oldest = voices[i].startTime;
        voiceIndex = i;
      }
    }
  }

  voices[voiceIndex].note = note;
  voices[voiceIndex].active = true;
  voices[voiceIndex].startTime = millis();
  carriers[voiceIndex].setFreq(carrierFreq);
  modulators[voiceIndex].setFreq(modFreq);
  envelopes[voiceIndex].noteOn();
}

void stopNote(byte note) {
  note += transpose;
  for (int i = 0; i < NUM_VOICES; i++) {
    if (voices[i].active && voices[i].note == note) {
      envelopes[i].noteOff();
      break; // Stop after first match
    }
  }
}

void midiPanic() {
  for (int i = 0; i < NUM_VOICES; i++) {
    if (voices[i].active) {
      envelopes[i].noteOff();
      voices[i].active = false;
      carriers[i].setFreq(0);
      modulators[i].setFreq(0);
    }
  }
  Serial.println("MIDI Panic");
}

int updateAudio() {
  long output = 0;
  int activeVoices = 0;

  for (int i = 0; i < NUM_VOICES; i++) {
    if (voices[i].active) {
      int modOut = modulators[i].next();
      int fmOut = carriers[i].phMod((int)(modulationIndex * modOut) >> 8);
      int envVal = envelopes[i].next();
      output += ((long)fmOut * envVal) >> 8;
      activeVoices++;
    }
  }

  return activeVoices ? (output / activeVoices) : 0;
}

void loop() {
  audioHook();
}