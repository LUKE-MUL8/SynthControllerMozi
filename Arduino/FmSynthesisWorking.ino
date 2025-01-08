#include <Mozzi.h>
#include <Oscil.h>
#include <tables/cos2048_int8.h> // Table for Oscils to play
#include <mozzi_midi.h>
#include <mozzi_fixmath.h>
#include <EventDelay.h>
#include <Smooth.h>

// Oscillators
Oscil<COS2048_NUM_CELLS, MOZZI_AUDIO_RATE> aCarrier(COS2048_DATA);
Oscil<COS2048_NUM_CELLS, MOZZI_AUDIO_RATE> aModulator(COS2048_DATA);
Oscil<COS2048_NUM_CELLS, MOZZI_CONTROL_RATE> kModIndex(COS2048_DATA); // Oscillator for modulation index

// Control variables
Q8n8 mod_index;  // Modulation index, controlled via Bluetooth input
Q16n16 deviation; // Deviation for FM synthesis
Q16n16 carrier_freq, mod_freq; // Frequencies for carrier and modulator
Q8n8 mod_to_carrier_ratio = float_to_Q8n8(3.f); // Modulation ratio

EventDelay kNoteChangeDelay; // Event delay for note changes

Q7n8 target_note, note0, note1, note_upper_limit, note_lower_limit, note_change_step, smoothed_note;
Smooth<int> kSmoothNote(0.95f); // Smoothing for note changes

void setup(){
  Serial.begin(9600); // Start serial communication for Bluetooth
  kNoteChangeDelay.set(768); // Adjust for control rate resolution
  kModIndex.setFreq(0.768f); // Sync modulation index with note change delay
  target_note = note0;
  note_change_step = Q7n0_to_Q7n8(3);
  note_upper_limit = Q7n0_to_Q7n8(50); // Upper limit for MIDI note
  note_lower_limit = Q7n0_to_Q7n8(32); // Lower limit for MIDI note
  note0 = note_lower_limit;
  note1 = note_lower_limit + Q7n0_to_Q7n8(5);
  startMozzi(); // Start the Mozzi audio system
}

void setFreqs(Q8n8 midi_note){
  // Convert MIDI note to fractional frequency
  carrier_freq = Q16n16_mtof(Q8n8_to_Q16n16(midi_note));
  mod_freq = ((carrier_freq >> 8) * mod_to_carrier_ratio); // Modulation frequency
  deviation = ((mod_freq >> 16) * mod_index); // Calculate deviation based on modulation index
  aCarrier.setFreq_Q16n16(carrier_freq); // Set frequency of carrier oscillator
  aModulator.setFreq_Q16n16(mod_freq); // Set frequency of modulator oscillator
}

void updateControl(){
  // Change note based on event delay
  if(kNoteChangeDelay.ready()){
    if (target_note == note0){
      note1 += note_change_step;
      target_note = note1;
    }
    else{
      note0 += note_change_step;
      target_note = note0;
    }

    // Change direction of note changes
    if(note0 > note_upper_limit) note_change_step = Q7n0_to_Q7n8(-3);
    if(note0 < note_lower_limit) note_change_step = Q7n0_to_Q7n8(3);

    kNoteChangeDelay.start(); // Reset event delay
  }

  // Read modulation index from Bluetooth serial input
  if (Serial.available() > 0) {
    mod_index = map(Serial.read(), 0, 255, 100, 500);  // Map to modulation index range
  }

  // Smooth the note change
  smoothed_note = kSmoothNote.next(target_note);

  // Set frequencies for the carrier and modulator based on the current note
  setFreqs(smoothed_note);
}

AudioOutput updateAudio(){
  // Perform FM synthesis by applying phase modulation
  Q15n16 modulation = deviation * aModulator.next() >> 8;
  return MonoOutput::from8Bit(aCarrier.phMod(modulation));
}

void loop(){
  updateControl(); // Update control parameters (note and modulation index)
  audioHook(); // Update audio output
}
