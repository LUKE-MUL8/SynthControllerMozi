#define MOZZI_AUDIO_CHANNELS 2  // Enable stereo output

#include <BluetoothSerial.h>
#include <MozziGuts.h>
#include <Oscil.h>
#include <ADSR.h>
#include <tables/saw2048_int8.h>
#include <tables/sin2048_int8.h>
#include <mozzi_midi.h>
#include <LowPassFilter.h>
#include <ReverbTank.h>
#include <tables/square_no_alias_2048_int8.h>
#include <tables/triangle2048_int8.h>

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
  #error Bluetooth is not enabled! Please run make menuconfig to enable it
#endif

BluetoothSerial SerialBT;

const int NUM_VOICES = 8; 

// Oscillators and envelopes
Oscil<SAW2048_NUM_CELLS, AUDIO_RATE> oscillators[NUM_VOICES];
Oscil<SIN2048_NUM_CELLS, AUDIO_RATE> subOscillators[NUM_VOICES]; // Sub oscillator
ADSR<CONTROL_RATE, AUDIO_RATE> envelopes[NUM_VOICES];
LowPassFilter filters[NUM_VOICES]; 

// Voice structure to track active notes
struct Voice {
  byte note;
  bool active;
  unsigned long startTime;
  bool releasing;
  byte velocity;
  unsigned long releaseStartTime; // Added for note-off timer
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

// Waveform selection
byte mainWaveform = 0;  // 0=saw, 1=square, 2=sine, 3=triangle
byte subWaveform = 1;   // 0=saw, 1=square, 2=sine, 3=triangle
const int8_t* waveformTables[4] = {
  SAW2048_DATA,
  SQUARE_NO_ALIAS_2048_DATA,
  SIN2048_DATA,
  TRIANGLE2048_DATA
};

Oscil<SIN2048_NUM_CELLS, CONTROL_RATE> vibratoLFO(SIN2048_DATA);
float currentVibrato = 0.0f;
float vibratoRateHz = 5.0f; // 5Hz default
float vibratoDepthSemitones = 0.0f; // Semitones

// Reverb effect
ReverbTank reverb;
byte reverbAmount = 100; // 0-255

// Function prototypes
void playNote(byte note);
void stopNote(byte note);
void panicAllNotes();
void processCommand(String command);
void updateSynthParameters();

void setup() {
  Serial.begin(115200);
  SerialBT.begin("ESP32Synth"); // Bluetooth device name
  updateWaveforms();
  // Initialize all voices
  for (int i = 0; i < NUM_VOICES; i++) {
    // Setup oscillators
    oscillators[i].setTable(SAW2048_DATA);
    subOscillators[i].setTable(SIN2048_DATA);

    // Setup envelope
    envelopes[i].setADLevels(255, sustainLevel);
    envelopes[i].setTimes(attackTime, decayTime, 65000, releaseTime);

    filters[i].setResonance(200);
    filters[i].setCutoffFreq(filterCutoff);

    // Initialize voice
    voices[i].note = 0;
    voices[i].active = false;
    voices[i].startTime = 0;
    voices[i].releasing = false;
    voices[i].velocity = 127; 
    voices[i].releaseStartTime = 0; // Initialize the release start time
  }

  // Setup vibrato LFO
  vibratoLFO.setFreq(5.0f); // Start with 5Hz vibrato

  reverb.setFeebackLevel(reverbAmount);
  
  startMozzi(CONTROL_RATE);
  Serial.println("Mozzi started. Ready for MIDI commands.");
}

void updateWaveforms() {
  for (int i = 0; i < NUM_VOICES; i++) {
    oscillators[i].setTable(waveformTables[mainWaveform]);
    subOscillators[i].setTable(waveformTables[subWaveform]);
  }
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
    voices[voiceIndex].releaseStartTime = 0; // Initialize the release time

    // Set oscillator frequencies
    oscillators[voiceIndex].setFreq(baseFreq);

    float subFreq = baseFreq / 2.0f;  
    // Apply detune to the sub oscillator
    float detuneAmount = map(detune, 0, 255, -50, 50) * 0.01f;  // -50% to +50%
    subFreq = subFreq * (1.0f + detuneAmount);
    
    subOscillators[voiceIndex].setFreq(subFreq);

    // Reset and trigger envelope
    envelopes[voiceIndex].setADLevels(255, sustainLevel);
    envelopes[voiceIndex].setTimes(attackTime, decayTime, 65000, releaseTime);
    envelopes[voiceIndex].noteOn();

    Serial.print("Note ON: ");
    Serial.print(note);
    Serial.print(" -> Voice: ");
    Serial.println(voiceIndex);
  } else {
    Serial.println("No available voices!");
  }
}

void stopNote(byte note) {
  note += transpose;

  for (int i = 0; i < NUM_VOICES; i++) {
    if (voices[i].active && voices[i].note == note && !voices[i].releasing) {
      voices[i].releasing = true;
      voices[i].releaseStartTime = millis(); // Set the release start time
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


  if (vibratoDepthSemitones > 0.1f) {
    currentVibrato = vibratoLFO.next() * (vibratoDepthSemitones / 64.0f); // Reduced sensitivity
  } else {
    currentVibrato = 0.0f;
  }

  // Voice management
  for (int i = 0; i < NUM_VOICES; i++) {
    if (voices[i].active) {

      float baseNote = voices[i].note + 12 * octaveShift + transpose;
      float modulatedNote = baseNote + currentVibrato;
      
      // Main oscillator
      float mainFreq = mtof(modulatedNote);
      oscillators[i].setFreq(mainFreq);

      // Sub oscillator (with detune)
      float subNote = baseNote - 12 + (detune/255.0f)*24.0f;
      float subFreq = mtof(subNote + currentVibrato * 0.8f); // Slightly less vibrato on sub
      subOscillators[i].setFreq(subFreq);
    }
    
    // Handle note-off timeout (300ms after release)
    if (voices[i].releasing && 
        millis() - voices[i].releaseStartTime > releaseTime) {
      voices[i].active = false;
      voices[i].releasing = false;
    }
    
    // Update envelopes
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
  } else if (command.startsWith("MAIN_WAVE:")) {
    mainWaveform = command.substring(10).toInt() % 4;
    updateWaveforms();
  } else if (command.startsWith("SUB_WAVE:")) {
    subWaveform = command.substring(9).toInt() % 4;
    updateWaveforms();
  } else if (command.startsWith("VIB_DEPTH:")) {
    // Map input 0-255 to 0.0-2.0 semitones for better control
    vibratoDepthSemitones = command.substring(10).toFloat() / 127.5f;
    Serial.print("Vibrato depth: ");
    Serial.print(vibratoDepthSemitones);
    Serial.println(" semitones");
  } else if (command.startsWith("VIB_RATE:")) {
    vibratoRateHz = command.substring(9).toFloat();
    vibratoLFO.setFreq(vibratoRateHz); 
  } else if (command.startsWith("REVERB:")) {
    reverbAmount = command.substring(7).toInt();
    reverb.setFeebackLevel(reverbAmount);
  }
}

void updateSynthParameters() {
  // Update all envelopes with new settings
  for (int i = 0; i < NUM_VOICES; i++) {
    envelopes[i].setADLevels(255, sustainLevel);
    envelopes[i].setTimes(attackTime, decayTime, 65000, releaseTime);
    filters[i].setCutoffFreq(filterCutoff);
  }
}

AudioOutput_t updateAudio() {
  int32_t leftOutput = 0;
  int32_t rightOutput = 0;
  int activeVoices = 0;

  // Sum all active voices
  for (int i = 0; i < NUM_VOICES; i++) {
    if (voices[i].active || voices[i].releasing) {
      int envVal = envelopes[i].next();

      // Get oscillator values (frequencies were already set in updateControl())
      int oscVal = oscillators[i].next();
      int subOscVal = subOscillators[i].next();

      // Mix oscillators
      int32_t voiceMix = ((oscVal * 7) + (subOscVal * 3)) / 10;
      int32_t contribution = (voiceMix * envVal * voices[i].velocity) >> 8;
      contribution = filters[i].next(contribution);

      // Pan voices
      if (i % 2 == 0) {
        leftOutput += contribution;
      } else {
        rightOutput += contribution;
      }

      activeVoices++;
    }
  }

  // Scaling and reverb
  if (activeVoices > 1) {
    leftOutput = leftOutput / activeVoices;
    rightOutput = rightOutput / activeVoices;
  }

  if (reverbAmount > 0) {
    int32_t leftRev = reverb.next(leftOutput);
    int32_t rightRev = reverb.next(rightOutput);
    leftOutput = leftOutput + (leftRev >> 2);
    rightOutput = rightOutput + (rightRev >> 2);
  }

  StereoOutput stereoOut = StereoOutput::fromNBit(16, leftOutput, rightOutput);
  return stereoOut;
}

void loop() {
  audioHook(); 
}