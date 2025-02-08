#include <MozziGuts.h>
#include <Oscil.h>
#include <ADSR.h>
#include <tables/sin2048_int8.h> // Sine wave table for modulation
#include <tables/saw2048_int8.h> // Sawtooth wave table for carrier
#include <mozzi_midi.h>
#include <SoftwareSerial.h>

// Bluetooth setup
SoftwareSerial BTSerial(10, 11); // RX, TX

// FM synthesis parameters
const int NUM_VOICES = 4; // Number of voices for polyphony
Oscil<SAW2048_NUM_CELLS, AUDIO_RATE> carriers[NUM_VOICES]; // Carrier oscillators
Oscil<SIN2048_NUM_CELLS, AUDIO_RATE> modulators[NUM_VOICES]; // Modulator oscillators
ADSR<CONTROL_RATE, AUDIO_RATE> envelopes[NUM_VOICES]; // Envelopes for each voice

// Voice management
struct Voice {
  byte note;
  bool active;
  unsigned long startTime;
};

Voice voices[NUM_VOICES];

// Global parameters
int attack = 10;
int decay = 20;
int sustain = 128;
int release = 50;
int filterCutoff = 255;
int detune = 0;
int transpose = 0;
int octaveShift = 2;

// FM synthesis parameters
float modulationIndex = 2.0f; // Modulation depth
float modToCarrierRatio = 2.0f; // Ratio of modulator to carrier frequency

void setup() {
  Serial.begin(9600); // For debugging
  BTSerial.begin(9600); // Bluetooth communication

  // Initialize oscillators and envelopes
  for (int i = 0; i < NUM_VOICES; i++) {
    carriers[i].setTable(SAW2048_DATA);
    modulators[i].setTable(SIN2048_DATA);
    envelopes[i].setADLevels(200, 0); // Reduced max volume
    envelopes[i].setTimes(attack, decay, sustain, release);
    voices[i].note = 0;
    voices[i].active = false;
    voices[i].startTime = 0;
  }

  startMozzi(CONTROL_RATE); // Start Mozzi audio engine
}

void updateControl() {
  static String command = "";

  // Read incoming Bluetooth data
  while (BTSerial.available()) {
    char receivedChar = BTSerial.read();

    if (receivedChar == '\n') {
      parseCommand(command); // Parse the complete command
      command = ""; // Reset the command buffer
    } else {
      command += receivedChar; // Append characters to the command buffer
    }
  }
}

void parseCommand(String command) {
  int separatorIndex = command.indexOf(':');
  if (separatorIndex == -1) return;

  String action = command.substring(0, separatorIndex);
  int value = command.substring(separatorIndex + 1).toInt();

  if (action == "DOWN") {
    playNote(value); // Play the note
  } else if (action == "UP") {
    stopNote(value); // Stop the note
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
  } else if (action == "FILTER") {
    filterCutoff = value;
  } else if (action == "DETUNE") {
    detune = value;
  } else if (action == "PANIC") {
    midiPanic(); // Trigger MIDI panic
  }
}

void updateEnvelopes() {
  for (int i = 0; i < NUM_VOICES; i++) {
    envelopes[i].setTimes(attack, decay, sustain, release);
  }
}

void playNote(byte note) {
  note += transpose;
  float carrierFreq = mtof(note + 12 * (octaveShift + 1)); // Convert MIDI note to frequency
  float modFreq = carrierFreq * modToCarrierRatio; // Modulator frequency

  int voiceIndex = -1;
  for (int i = 0; i < NUM_VOICES; i++) {
    if (!voices[i].active) {
      voiceIndex = i;
      break;
    }
  }

  if (voiceIndex == -1) voiceIndex = 0; // Use the first voice if all are active

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
      voices[i].active = false;
      break;
    }
  }
}

void midiPanic() {
  for (int i = 0; i < NUM_VOICES; i++) {
    if (voices[i].active) {
      envelopes[i].noteOff(); // Turn off the envelope
      voices[i].active = false; // Mark the voice as inactive
    }
  }
  Serial.println("MIDI Panic: All notes turned off.");
}

int updateAudio() {
  long output = 0;
  int activeVoices = 0;

  for (int i = 0; i < NUM_VOICES; i++) {
    if (voices[i].active) {
      int modulatorOutput = modulators[i].next();
      int fmOutput = carriers[i].phMod((int)(modulationIndex * modulatorOutput) >> 8);

      int envVal = envelopes[i].next();
      output += ((long)fmOutput * envVal) >> 8;
      activeVoices++;
    }
  }

  // Scale down output based on the number of active voices
  if (activeVoices > 0) {
    output /= activeVoices;
  }

  return (int)output;
}

void loop() {
  audioHook(); // Update Mozzi audio engine
}