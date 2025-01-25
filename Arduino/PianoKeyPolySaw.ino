#include <SoftwareSerial.h>
#include <MozziGuts.h>
#include <Oscil.h>
#include <ADSR.h>
#include <tables/saw2048_int8.h>
#include <mozzi_midi.h>

SoftwareSerial BTSerial(10, 11);

const int NUM_VOICES = 4;
Oscil<SAW2048_NUM_CELLS, AUDIO_RATE> oscillators[NUM_VOICES];
ADSR<CONTROL_RATE, AUDIO_RATE> envelopes[NUM_VOICES];

struct Voice {
  byte note;
  bool active;
  unsigned long startTime;
};

Voice voices[NUM_VOICES];

int octaveShift = 2;
int transpose = 0;

void setup() {
  Serial.begin(115200);
  BTSerial.begin(9600);

  for (int i = 0; i < NUM_VOICES; i++) {
    oscillators[i].setTable(SAW2048_DATA);
    envelopes[i].setADLevels(200, 0);  // Reduced max volume
    envelopes[i].setTimes(10, 20, 0, 50);
    voices[i].note = 0;
    voices[i].active = false;
    voices[i].startTime = 0;
  }

  startMozzi(CONTROL_RATE);
}

void playNote(byte note) {
  note += transpose;
  float freq = mtof(note + 12 * (octaveShift + 1));

  int voiceIndex = -1;
  for (int i = 0; i < NUM_VOICES; i++) {
    if (!voices[i].active) {
      voiceIndex = i;
      break;
    }
  }

  if (voiceIndex == -1) voiceIndex = 0;

  voices[voiceIndex].note = note;
  voices[voiceIndex].active = true;
  voices[voiceIndex].startTime = millis();

  oscillators[voiceIndex].setFreq(freq);
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

void updateControl() {
  static String command = "";

  while (BTSerial.available()) {
    char receivedChar = BTSerial.read();
    
    if (receivedChar == '\n') {
      if (command.startsWith("DOWN:")) {
        byte note = command.substring(5).toInt();
        playNote(note);
      } else if (command.startsWith("UP:")) {
        byte note = command.substring(3).toInt();
        stopNote(note);
      } else if (command.startsWith("OCTAVE:")) {
        octaveShift = command.substring(7).toInt();
      } else if (command.startsWith("TRANSPOSE:")) {
        transpose = command.substring(10).toInt();
      }
      command = "";
    } else {
      command += receivedChar;
    }
  }
}

int updateAudio() {
  long output = 0;
  int activeVoices = 0;

  for (int i = 0; i < NUM_VOICES; i++) {
    if (voices[i].active) {
      int envVal = envelopes[i].next();
      int oscVal = oscillators[i].next();
      output += ((long)oscVal * envVal) >> 8;
      activeVoices++;
    }
  }

  // Scale down output based on number of active voices
  if (activeVoices > 0) {
    output /= activeVoices;
  }

  return (int)output;
}

void loop() {
  audioHook();
}