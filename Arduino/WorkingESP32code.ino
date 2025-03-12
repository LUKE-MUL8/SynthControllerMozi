#include <BluetoothSerial.h>
#include <MozziGuts.h>
#include <Oscil.h>
#include <ADSR.h>
#include <tables/saw2048_int8.h>
#include <tables/sin2048_int8.h>
#include <mozzi_midi.h>
#include <LowPassFilter.h>

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
  #error Bluetooth is not enabled! Please run `make menuconfig` to enable it
#endif

BluetoothSerial SerialBT;

// Synth configuration - reduced voice count for ESP32
const int NUM_VOICES = 4; // Start with fewer voices to ensure stability

// Oscillators and envelopes
Oscil<SAW2048_NUM_CELLS, AUDIO_RATE> oscillators[NUM_VOICES];
Oscil<SIN2048_NUM_CELLS, AUDIO_RATE> subOscillators[NUM_VOICES]; // Sub oscillator for richer tone
ADSR<CONTROL_RATE, AUDIO_RATE> envelopes[NUM_VOICES];
LowPassFilter filters[NUM_VOICES]; // Optional: Add a filter per voice

// Voice structure to track active notes
struct Voice {
  byte note;
  bool active;
  unsigned long startTime;
  bool releasing;
  byte velocity;
};

Voice voices[NUM_VOICES];

// Synth parameters
int octaveShift = 0;
int transpose = 0;
int attackTime = 50;    // in ms
int decayTime = 100;    // in ms
int sustainLevel = 180; // 0-255
int releaseTime = 300;  // in ms
int filterCutoff = 255; // 0-255
int detune = 0;         // 0-255, affects sub oscillator

// Function prototypes
void playNote(byte note);
void stopNote(byte note);
void panicAllNotes();
void processCommand(String command);
void updateSynthParameters();

void setup() {
  Serial.begin(115200);
  SerialBT.begin("ESP32Synth"); // Bluetooth device name
  Serial.println("ESP32 MIDI Synth started. Pair with device name: ESP32_MIDI_Synth");

  // Initialize all voices
  for (int i = 0; i < NUM_VOICES; i++) {
    // Setup oscillators
    oscillators[i].setTable(SAW2048_DATA);
    subOscillators[i].setTable(SIN2048_DATA);

    // Setup envelope
    envelopes[i].setADLevels(255, sustainLevel);
    envelopes[i].setTimes(attackTime, decayTime, 65000, releaseTime);

    // Setup filter (if used)
    filters[i].setResonance(200);
    filters[i].setCutoffFreq(filterCutoff);

    // Initialize voice state
    voices[i].note = 0;
    voices[i].active = false;
    voices[i].startTime = 0;
    voices[i].releasing = false;
    voices[i].velocity = 127; // Default to max velocity
  }

  startMozzi(CONTROL_RATE);
  Serial.println("Mozzi started. Ready for MIDI commands.");
}

void playNote(byte note) {
  note += transpose;
  float baseFreq = mtof(note + 12 * octaveShift);

  // Find inactive voice or steal oldest
  int voiceIndex = -1;
  unsigned long oldestStartTime = mozziMicros();

  for (int i = 0; i < NUM_VOICES; i++) {
    if (!voices[i].active) {
      voiceIndex = i;
      break;
    }
    if (voices[i].startTime < oldestStartTime) {
      oldestStartTime = voices[i].startTime;
      voiceIndex = i;
    }
  }

  if (voiceIndex >= 0) {
    voices[voiceIndex].note = note;
    voices[voiceIndex].active = true;
    voices[voiceIndex].releasing = false;
    voices[voiceIndex].startTime = mozziMicros();
    voices[voiceIndex].velocity = 127;

    // Set oscillator frequencies
    oscillators[voiceIndex].setFreq(baseFreq);

    // Detune the sub oscillator
    float detuneAmount = map(detune, 0, 255, 0, 100) * 0.01f;
    float subFreq = baseFreq / 2.0f * (1.0f - detuneAmount);
    subOscillators[voiceIndex].setFreq(subFreq);

    // Reset and trigger envelope
    envelopes[voiceIndex].setADLevels(255, sustainLevel);
    envelopes[voiceIndex].setTimes(attackTime, decayTime, 65000, releaseTime);
    envelopes[voiceIndex].noteOn();

    Serial.print("Note ON: ");
    Serial.print(note);
    Serial.print(" -> Voice: ");
    Serial.println(voiceIndex);
  }
}

void stopNote(byte note) {
  note += transpose;

  for (int i = 0; i < NUM_VOICES; i++) {
    if (voices[i].active && voices[i].note == note && !voices[i].releasing) {
      voices[i].releasing = true;
      envelopes[i].noteOff();

      Serial.print("Note OFF: ");
      Serial.print(note);
      Serial.print(" -> Voice: ");
      Serial.println(i);
      break;
    }
  }
}

void panicAllNotes() {
  Serial.println("PANIC: All notes off");
  for (int i = 0; i < NUM_VOICES; i++) {
    envelopes[i].noteOff();
    voices[i].active = false;
    voices[i].releasing = false;
  }
}

void updateControl() {
  static String command = "";

  // Process Bluetooth messages
  while (SerialBT.available()) {
    char receivedChar = SerialBT.read();

    if (receivedChar == '\n') {
      processCommand(command);
      command = "";
    } else {
      command += receivedChar;
    }
  }

  // Voice management: check if any releasing voices have completed
  for (int i = 0; i < NUM_VOICES; i++) {
    if (voices[i].active && voices[i].releasing) {
      if (!envelopes[i].playing()) {
        voices[i].active = false;
        voices[i].releasing = false;
      }
    }

    // Update envelopes independently
    if (voices[i].active || voices[i].releasing) {
      envelopes[i].update();
    }
  }
}

void processCommand(String command) {
  Serial.print("Command received: ");
  Serial.println(command);

  if (command.startsWith("DOWN:")) {
    byte note = command.substring(5).toInt();
    playNote(note);
  } else if (command.startsWith("UP:")) {
    byte note = command.substring(3).toInt();
    stopNote(note);
  } else if (command.startsWith("OCTAVE:")) {
    octaveShift = command.substring(7).toInt();
    Serial.print("Octave shift: ");
    Serial.println(octaveShift);
  } else if (command.startsWith("TRANSPOSE:")) {
    transpose = command.substring(10).toInt();
    Serial.print("Transpose: ");
    Serial.println(transpose);
  } else if (command.startsWith("ATTACK:")) {
    attackTime = map(command.substring(7).toInt(), 0, 255, 10, 1000);
    updateSynthParameters();
  } else if (command.startsWith("DECAY:")) {
    decayTime = map(command.substring(6).toInt(), 0, 255, 10, 1000);
    updateSynthParameters();
  } else if (command.startsWith("SUSTAIN:")) {
    sustainLevel = command.substring(8).toInt();
    updateSynthParameters();
  } else if (command.startsWith("RELEASE:")) {
    releaseTime = map(command.substring(8).toInt(), 0, 255, 10, 2000);
    updateSynthParameters();
  } else if (command.startsWith("FILTER:")) {
    filterCutoff = command.substring(7).toInt();
    updateSynthParameters();
  } else if (command.startsWith("DETUNE:")) {
    detune = command.substring(7).toInt();
    updateSynthParameters();
  } else if (command.startsWith("PANIC")) {
    panicAllNotes();
  }
}

void updateSynthParameters() {
  // Update all envelopes with new settings
  for (int i = 0; i < NUM_VOICES; i++) {
    envelopes[i].setADLevels(255, sustainLevel);
    envelopes[i].setTimes(attackTime, decayTime, 65000, releaseTime);
  }

  // Update filter
  for (int i = 0; i < NUM_VOICES; i++) {
    filters[i].setCutoffFreq(filterCutoff);
  }

  Serial.print("Synth params updated - A:");
  Serial.print(attackTime);
  Serial.print(" D:");
  Serial.print(decayTime);
  Serial.print(" S:");
  Serial.print(sustainLevel);
  Serial.print(" R:");
  Serial.print(releaseTime);
  Serial.print(" F:");
  Serial.print(filterCutoff);
  Serial.print(" Det:");
  Serial.println(detune);
}

int updateAudio() {
  int32_t output = 0;
  int activeVoices = 0;

  // Sum all active voices
  for (int i = 0; i < NUM_VOICES; i++) {
    if (voices[i].active || voices[i].releasing) {
      // Get envelope value
      int envVal = envelopes[i].next();

      // Get oscillator values
      int oscVal = oscillators[i].next();
      int subOscVal = subOscillators[i].next();

      // Mix oscillators (2/3 main, 1/3 sub)
      int voiceMix = ((oscVal * 2) + subOscVal) / 3;

      // Apply envelope and velocity
      int32_t contribution = ((int32_t)voiceMix * envVal * voices[i].velocity) >> 8;

      // Apply filter (if used)
      contribution = filters[i].next(contribution);

      // Add to output
      output += contribution;
      activeVoices++;
    }
  }

  // Prevent clipping by scaling based on number of active voices
  if (activeVoices > 1) {
    output = output / activeVoices;
  }

  // Use Mozzi's proper output functions
  return MonoOutput::fromNBit(16, output);
}

void loop() {
  audioHook(); // Required for Mozzi to work
}
