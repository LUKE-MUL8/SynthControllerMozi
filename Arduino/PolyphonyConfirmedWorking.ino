#include <MozziGuts.h>
#include <Oscil.h> 
#include <ADSR.h>
#include <tables/sin2048_int8.h>
#include <tables/saw2048_int8.h>
#include <tables/triangle2048_int8.h>
#include <mozzi_midi.h>
#include <mozzi_fixmath.h>

#define CONTROL_RATE 128
#define ATTACK 25
#define DECAY 25
#define SUSTAIN 3000
#define RELEASE 100
#define ATTACK_LEVEL 255
#define DECAY_LEVEL 200

Oscil<SAW2048_NUM_CELLS, AUDIO_RATE> oscil1;
Oscil<SAW2048_NUM_CELLS, AUDIO_RATE> oscil2;
Oscil<SAW2048_NUM_CELLS, AUDIO_RATE> oscil3;
ADSR<CONTROL_RATE, AUDIO_RATE> envelope1;
ADSR<CONTROL_RATE, AUDIO_RATE> envelope2;
ADSR<CONTROL_RATE, AUDIO_RATE> envelope3;

byte note1 = 0, note2 = 0, note3 = 0;
byte gain1 = 0, gain2 = 0, gain3 = 0;

#define SUSTAIN_TIME 3000 // Sustain time in milliseconds
void setup() {
  Serial.begin(115200);

  // Set the same waveform for all oscillators
  oscil1.setTable(SAW2048_DATA);
  oscil2.setTable(SAW2048_DATA);
  oscil3.setTable(SAW2048_DATA);

  // Set ADSR envelopes for all voices
  envelope1.setADLevels(ATTACK_LEVEL, DECAY_LEVEL);
  envelope1.setTimes(ATTACK, DECAY, SUSTAIN, RELEASE);
  envelope2.setADLevels(ATTACK_LEVEL, DECAY_LEVEL);
  envelope2.setTimes(ATTACK, DECAY, SUSTAIN, RELEASE);
  envelope3.setADLevels(ATTACK_LEVEL, DECAY_LEVEL);
  envelope3.setTimes(ATTACK, DECAY, SUSTAIN, RELEASE);

  startMozzi(CONTROL_RATE);
}

void simulateNoteOn(byte note, byte voiceIndex) {
  float freq = mtof(note);

  // Assign the note to the correct oscillator
  switch (voiceIndex) {
    case 0:
      note1 = note;
      oscil1.setFreq(freq);
      envelope1.noteOn();
      break;
    case 1:
      note2 = note;
      oscil2.setFreq(freq);
      envelope2.noteOn();
      break;
    case 2:
      note3 = note;
      oscil3.setFreq(freq);
      envelope3.noteOn();
      break;
  }

  // Print active notes and frequencies
  Serial.print("Voice ");
  Serial.print(voiceIndex + 1);
  Serial.print(": Note ");
  Serial.print(note);
  Serial.print(", Frequency ");
  Serial.println(freq);
}

void updateControl() {
  static unsigned long lastNoteTime = 0;
  static byte noteOffsets[] = {0, 12, 24}; // Distinct offsets for each voice

  if (millis() - lastNoteTime > 3000) { // Change each voice every 3 seconds
    for (byte voiceIndex = 0; voiceIndex < 3; ++voiceIndex) {
      byte newNote = 48 + random(12) + noteOffsets[voiceIndex]; // Unique note range
      simulateNoteOn(newNote, voiceIndex);
    }
    lastNoteTime = millis();
  }

  // Update envelopes
  envelope1.update();
  envelope2.update();
  envelope3.update();

  // Update gains for mixing
  gain1 = envelope1.next();
  gain2 = envelope2.next();
  gain3 = envelope3.next();

  // Debugging: Print gains and active notes
  Serial.print("Gains: ");
  Serial.print(gain1);
  Serial.print(", ");
  Serial.print(gain2);
  Serial.print(", ");
  Serial.print(gain3);
  Serial.print(" | Active Notes: ");
  Serial.print(note1);
  Serial.print(", ");
  Serial.print(note2);
  Serial.print(", ");
  Serial.println(note3);
}

int updateAudio() {
  // Mix all voices together
  int output = 0;
  output += (gain1 * oscil1.next()) >> 8;
  output += (gain2 * oscil2.next()) >> 8;
  output += (gain3 * oscil3.next()) >> 8;

  return output;
}

void loop() {
  audioHook();
}
