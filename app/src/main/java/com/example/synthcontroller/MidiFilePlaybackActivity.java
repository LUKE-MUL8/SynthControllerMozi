package com.example.synthcontroller;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.leff.midi.MidiFile;
import com.leff.midi.MidiTrack;
import com.leff.midi.event.MidiEvent;
import com.leff.midi.event.NoteOff;
import com.leff.midi.event.NoteOn;
import com.leff.midi.event.meta.Tempo;
import com.rejowan.rotaryknob.RotaryKnob;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MidiFilePlaybackActivity extends AppCompatActivity {
    private static final String TAG = "MidiPlaybackActivity";

    // Flag to control playback thread
    private AtomicBoolean isPlaying = new AtomicBoolean(false);
    private Thread playbackThread = null;
    private PresetManager presetManager;

    private final ActivityResultLauncher<String> openFileLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    playMidiFile(uri);
                } else {
                    Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_midi_file_playback);

        if (!BluetoothManager.getInstance().isConnected()) {
            Toast.makeText(this, "Bluetooth not connected", Toast.LENGTH_LONG).show();
            new Thread(() -> {
                if (BluetoothManager.getInstance().connect()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Reconnected", Toast.LENGTH_SHORT).show();
                        initializeSynthParameters();
                        setupSynthControls();
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to reconnect", Toast.LENGTH_LONG).show();
                        finish();
                    });
                }
            }).start();
        } else {
            // If already connected, initialize parameters immediately
            initializeSynthParameters();
            setupSynthControls();
        }

        // Setup preset controls
        presetManager = new PresetManager(this);
        setupPresetControls();



        Button stopButton = findViewById(R.id.stopButton);



        stopButton.setOnClickListener(v -> stopPlayback());

        Button selectFileButton = findViewById(R.id.selectFileButton);
        if (selectFileButton != null) {
            selectFileButton.setOnClickListener(v -> {
                Log.d(TAG, "Select file button clicked");
                openFileLauncher.launch("audio/midi");
            });
        }
    }

    private void setupPresetControls() {
        Spinner presetSpinner = findViewById(R.id.presetSpinner);
        Button loadPresetButton = findViewById(R.id.loadPresetButton);
        Button savePresetButton = findViewById(R.id.savePresetButton);

        updatePresetSpinner(presetSpinner);

        loadPresetButton.setOnClickListener(v -> loadSelectedPreset(presetSpinner));
        savePresetButton.setOnClickListener(v -> showSavePresetDialog());
    }

    private void updatePresetSpinner(Spinner spinner) {
        List<String> presetNames = presetManager.getPresetNames();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, R.layout.spinner_item_white_text, presetNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void loadSelectedPreset(Spinner spinner) {
        if (spinner.getSelectedItem() == null) {
            Toast.makeText(this, "No preset selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String presetName = spinner.getSelectedItem().toString();
        SynthPreset preset = presetManager.getPreset(presetName);

        if (preset == null) {
            Toast.makeText(this, "Preset not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Send parameters to the synth
        sendCommand("MAIN_WAVE:", preset.getMainWaveform());
        sendCommand("SUB_WAVE:", preset.getSubWaveform());
        sendCommand("ATTACK:", preset.getAttack());
        sendCommand("DECAY:", preset.getDecay());
        sendCommand("SUSTAIN:", preset.getSustain());
        sendCommand("RELEASE:", preset.getRelease());
        sendCommand("FILTER:", preset.getFilter());
        sendCommand("DETUNE:", preset.getDetune());
        sendCommand("VIB_RATE:", preset.getVibRate());
        sendCommand("VIB_DEPTH:", preset.getVibDepth());

        // Update UI controls to reflect loaded values
        updateUIFromPreset(preset);

        Toast.makeText(this, "Preset loaded: " + presetName, Toast.LENGTH_SHORT).show();
    }

    private void updateUIFromPreset(SynthPreset preset) {
        // Update waveform spinners
        updateSpinnerIfExists(R.id.mainWaveSpinner, preset.getMainWaveform());
        updateSpinnerIfExists(R.id.subWaveSpinner, preset.getSubWaveform());

        // Update knobs
        updateKnobIfExists(R.id.attackKnob, preset.getAttack());
        updateKnobIfExists(R.id.decayKnob, preset.getDecay());
        updateKnobIfExists(R.id.sustainKnob, preset.getSustain());
        updateKnobIfExists(R.id.releaseKnob, preset.getRelease());
        updateKnobIfExists(R.id.filterKnob, preset.getFilter());
        updateKnobIfExists(R.id.detuneKnob, preset.getDetune());
        updateKnobIfExists(R.id.vibRateKnob, preset.getVibRate());
        updateKnobIfExists(R.id.vibDepthKnob, preset.getVibDepth());
    }

    private void updateKnobIfExists(int id, int value) {
        RotaryKnob knob = findViewById(id);
        if (knob != null) {
            knob.setCurrentProgress(value);
        }
    }

    private void updateSpinnerIfExists(int id, int position) {
        Spinner spinner = findViewById(id);
        if (spinner != null && position >= 0 && position < spinner.getAdapter().getCount()) {
            spinner.setSelection(position);
        }
    }

    private void showSavePresetDialog() {
        // Use same pattern as in PerformActivity
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_save_preset, null);
        android.widget.EditText input = dialogView.findViewById(R.id.presetNameInput);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Save Preset")
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .create();

        dialog.show();

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String presetName = input.getText().toString().trim();
            if (presetName.isEmpty()) {
                input.setError("Please enter a name");
            } else {
                saveCurrentPreset(presetName);
                dialog.dismiss();
            }
        });
    }

    private void saveCurrentPreset(String name) {
        SynthPreset preset = new SynthPreset();
        preset.setName(name);

        // Get values from UI controls
        Spinner mainWaveSpinner = findViewById(R.id.mainWaveSpinner);
        Spinner subWaveSpinner = findViewById(R.id.subWaveSpinner);

        if (mainWaveSpinner != null) {
            preset.setMainWaveform(mainWaveSpinner.getSelectedItemPosition());
        }

        if (subWaveSpinner != null) {
            preset.setSubWaveform(subWaveSpinner.getSelectedItemPosition());
        }

        preset.setAttack(getKnobValue(R.id.attackKnob));
        preset.setDecay(getKnobValue(R.id.decayKnob));
        preset.setSustain(getKnobValue(R.id.sustainKnob));
        preset.setRelease(getKnobValue(R.id.releaseKnob));
        preset.setFilter(getKnobValue(R.id.filterKnob));
        preset.setDetune(getKnobValue(R.id.detuneKnob));
        preset.setVibRate(getKnobValue(R.id.vibRateKnob));
        preset.setVibDepth(getKnobValue(R.id.vibDepthKnob));

        // Save preset
        presetManager.savePreset(preset);

        // Update spinner
        updatePresetSpinner(findViewById(R.id.presetSpinner));

        Toast.makeText(this, "Preset saved: " + name, Toast.LENGTH_SHORT).show();
    }

    private int getKnobValue(int knobId) {
        RotaryKnob knob = findViewById(knobId);
        return knob != null ? knob.getCurrentProgress() : 0;
    }

    private void setupSynthControls() {
        setupAdsrControls();
        setupEffectsControls();
        setupWaveformControls();
    }

    private void setupAdsrControls() {
        // Find ADSR knobs in layout
        RotaryKnob attackKnob = findViewById(R.id.attackKnob);
        RotaryKnob decayKnob = findViewById(R.id.decayKnob);
        RotaryKnob sustainKnob = findViewById(R.id.sustainKnob);
        RotaryKnob releaseKnob = findViewById(R.id.releaseKnob);

        // Configure ADSR knobs
        if (attackKnob != null) {
            attackKnob.setMin(0);
            attackKnob.setMax(255);
            attackKnob.setCurrentProgress(0);
            attackKnob.setProgressChangeListener(progress ->
                    sendCommand("ATTACK:", progress));
        }

        if (decayKnob != null) {
            decayKnob.setMin(0);
            decayKnob.setMax(255);
            decayKnob.setCurrentProgress(255);
            decayKnob.setProgressChangeListener(progress ->
                    sendCommand("DECAY:", progress));
        }

        if (sustainKnob != null) {
            sustainKnob.setMin(0);
            sustainKnob.setMax(255);
            sustainKnob.setCurrentProgress(0);
            sustainKnob.setProgressChangeListener(progress ->
                    sendCommand("SUSTAIN:", progress));
        }

        if (releaseKnob != null) {
            releaseKnob.setMin(0);
            releaseKnob.setMax(255);
            releaseKnob.setCurrentProgress(0);
            releaseKnob.setProgressChangeListener(progress ->
                    sendCommand("RELEASE:", progress));
        }
    }

    private void setupEffectsControls() {
        // Find effects knobs in layout
        RotaryKnob filterKnob = findViewById(R.id.filterKnob);
        RotaryKnob detuneKnob = findViewById(R.id.detuneKnob);
        RotaryKnob vibRateKnob = findViewById(R.id.vibRateKnob);
        RotaryKnob vibDepthKnob = findViewById(R.id.vibDepthKnob);

        // Configure effects knobs
        if (filterKnob != null) {
            filterKnob.setMin(0);
            filterKnob.setMax(255);
            filterKnob.setCurrentProgress(255);
            filterKnob.setProgressChangeListener(progress ->
                    sendCommand("FILTER:", progress));
        }

        if (detuneKnob != null) {
            detuneKnob.setMin(0);
            detuneKnob.setMax(255);
            detuneKnob.setCurrentProgress(0);
            detuneKnob.setProgressChangeListener(progress ->
                    sendCommand("DETUNE:", progress));
        }

        if (vibRateKnob != null) {
            vibRateKnob.setMin(0);
            vibRateKnob.setMax(255);
            vibRateKnob.setCurrentProgress(0);
            vibRateKnob.setProgressChangeListener(progress ->
                    sendCommand("VIB_RATE:", progress));
        }

        if (vibDepthKnob != null) {
            vibDepthKnob.setMin(0);
            vibDepthKnob.setMax(255);
            vibDepthKnob.setCurrentProgress(0);
            vibDepthKnob.setProgressChangeListener(progress ->
                    sendCommand("VIB_DEPTH:", progress));
        }
    }

    private void setupWaveformControls() {
        Spinner mainWaveSpinner = findViewById(R.id.mainWaveSpinner);
        Spinner subWaveSpinner = findViewById(R.id.subWaveSpinner);

        final String[] waveforms = {"Saw", "Square", "Sine", "Triangle"};

        if (mainWaveSpinner != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, R.layout.spinner_item_white_text, waveforms);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            mainWaveSpinner.setAdapter(adapter);
            mainWaveSpinner.setSelection(0);  // Saw as default

            // Fix for spinner listeners
            mainWaveSpinner.post(() -> {
                mainWaveSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        Log.d(TAG, "Main wave selected: " + position);
                        sendCommand("MAIN_WAVE:", position);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
            });
        }

        if (subWaveSpinner != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, R.layout.spinner_item_white_text, waveforms);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            subWaveSpinner.setAdapter(adapter);
            subWaveSpinner.setSelection(1);  // Square as default

            // Fix for spinner listeners
            subWaveSpinner.post(() -> {
                subWaveSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        Log.d(TAG, "Sub wave selected: " + position);
                        sendCommand("SUB_WAVE:", position);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
            });
        }
    }

    private void initializeSynthParameters() {
        Log.d(TAG, "Setting synth parameters for MIDI playback");
        BluetoothManager btManager = BluetoothManager.getInstance();
        // Set decay and filter to max
        btManager.sendCommand("DECAY:255");
        btManager.sendCommand("FILTER:255");
        // Set others to min
        btManager.sendCommand("ATTACK:0");
        btManager.sendCommand("SUSTAIN:0");
        btManager.sendCommand("RELEASE:0");
        btManager.sendCommand("DETUNE:0");

        Toast.makeText(this, "Synth parameters initialized for MIDI playback", Toast.LENGTH_SHORT).show();
    }

    // Method for controls to send commands - no interface needed
    public void sendCommand(String prefix, int value) {
        String command = prefix + value;
        Log.d(TAG, "Sending command: " + command);
        BluetoothManager.getInstance().sendCommand(command);
    }

    private void stopPlayback() {
        if (isPlaying.get()) {
            isPlaying.set(false);
            if (playbackThread != null && playbackThread.isAlive()) {
                playbackThread.interrupt();
            }

            // Send all notes off command to ensure no notes are stuck
            sendAllNotesOff();

            Toast.makeText(this, "Playback stopped", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Playback stopped by user");
        } else {
            Toast.makeText(this, "No playback in progress", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendAllNotesOff() {
        // Send note off commands for all possible MIDI notes (0-127)
        // to ensure no notes are left playing
        for (int note = 0; note < 128; note++) {
            BluetoothManager.getInstance().sendCommand("UP:" + note);
        }
        Log.d(TAG, "Sent all notes off");
        sendCommand("PANIC:", 1);
    }

    private void playMidiFile(Uri uri) {
        // Stop any existing playback
        if (isPlaying.get()) {
            stopPlayback();
        }

        isPlaying.set(true);

        playbackThread = new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                MidiFile midiFile = new MidiFile(inputStream);
                inputStream.close();

                List<Command> commands = new ArrayList<>();
                float microsecondsPerQuarterNote = 500000.0f;  // Default 120 BPM
                int ticksPerQuarterNote = midiFile.getResolution();

                Log.d(TAG, "MIDI resolution: " + ticksPerQuarterNote + " ticks per quarter note");

                // First pass - collect tempo events
                List<TempoEvent> tempoEvents = new ArrayList<>();
                for (MidiTrack track : midiFile.getTracks()) {
                    long currentTick = 0;
                    for (MidiEvent event : track.getEvents()) {
                        currentTick += event.getDelta();
                        if (event instanceof Tempo) {
                            Tempo tempo = (Tempo) event;
                            tempoEvents.add(new TempoEvent(currentTick, tempo.getMpqn()));
                            Log.d(TAG, "Found tempo event at tick " + currentTick + ": " + tempo.getBpm() + " BPM");
                        }
                    }
                }

                // Sort tempo events by tick
                tempoEvents.sort((a, b) -> Long.compare(a.tick, b.tick));

                // Second pass - process note events with correct timing
                for (MidiTrack track : midiFile.getTracks()) {
                    long currentTick = 0;
                    Iterator<MidiEvent> it = track.getEvents().iterator();

                    while (it.hasNext()) {
                        MidiEvent event = it.next();
                        currentTick += event.getDelta();

                        // Calculate correct timing based on tempo changes
                        long ms = ticksToMs(currentTick, tempoEvents, ticksPerQuarterNote);

                        if (event instanceof NoteOn) {
                            NoteOn noteOn = (NoteOn) event;
                            if (noteOn.getVelocity() > 0) {
                                String command = "DOWN:" + noteOn.getNoteValue();
                                commands.add(new Command(ms, command));
                            } else {
                                String command = "UP:" + noteOn.getNoteValue();
                                commands.add(new Command(ms, command));
                            }
                        } else if (event instanceof NoteOff) {
                            NoteOff noteOff = (NoteOff) event;
                            String command = "UP:" + noteOff.getNoteValue();
                            commands.add(new Command(ms, command));
                        }
                    }
                }

                commands.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));

                long startTime = System.currentTimeMillis();
                long lastTimestamp = 0;

                for (Command cmd : commands) {
                    // Check if playback has been stopped
                    if (!isPlaying.get()) {
                        Log.d(TAG, "Playback stopped during execution");
                        return;
                    }

                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long delay = cmd.timestamp - elapsedTime;

                    // Log timing information for debugging
                    if (cmd.timestamp - lastTimestamp > 500) {
                        Log.d(TAG, "Large gap in timestamps: " + (cmd.timestamp - lastTimestamp) + "ms");
                    }
                    lastTimestamp = cmd.timestamp;

                    if (delay > 0) {
                        Thread.sleep(delay);
                    }

                    // Check again after sleep in case stop was pressed during the delay
                    if (!isPlaying.get()) {
                        Log.d(TAG, "Playback stopped during sleep");
                        return;
                    }

                    if (!BluetoothManager.getInstance().sendCommand(cmd.command)) {
                        Log.e(TAG, "Failed to send command during playback: " + cmd.command);
                        runOnUiThread(() -> Toast.makeText(this, "Playback interrupted", Toast.LENGTH_SHORT).show());
                        isPlaying.set(false);
                        return;
                    }
                    Log.d(TAG, "Sent at " + cmd.timestamp + "ms: " + cmd.command);
                }

                runOnUiThread(() -> Toast.makeText(this, "MIDI playback completed", Toast.LENGTH_SHORT).show());
                isPlaying.set(false);
            } catch (IOException e) {
                Log.e(TAG, "Error during playback: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Playback failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                isPlaying.set(false);
            } catch (InterruptedException e) {
                Log.d(TAG, "Playback thread interrupted");
                isPlaying.set(false);
            }
        });

        playbackThread.start();
    }

    // Helper method to calculate milliseconds for a tick, considering tempo changes
    private long ticksToMs(long tick, List<TempoEvent> tempoEvents, int ticksPerQuarterNote) {
        if (tempoEvents.isEmpty()) {
            // Default tempo
            return (long)((tick * 500000.0f) / (ticksPerQuarterNote * 1000.0f));
        }

        long ms = 0;
        long lastTick = 0;
        float currentMpqn = 500000.0f; // Default 120 BPM

        // Find all tempo changes before this tick
        for (TempoEvent event : tempoEvents) {
            if (event.tick < tick) {
                // Calculate ms for the segment using the previous tempo
                ms += ((event.tick - lastTick) * currentMpqn) / (ticksPerQuarterNote * 1000.0f);
                lastTick = event.tick;
                currentMpqn = event.mpqn;
            } else {
                break; // No need to check further tempo events
            }
        }

        // Add remaining time using current tempo
        ms += ((tick - lastTick) * currentMpqn) / (ticksPerQuarterNote * 1000.0f);

        return ms;
    }

    @Override
    protected void onDestroy() {
        if (isPlaying.get()) {
            stopPlayback();
        }
        super.onDestroy();
    }

    private static class Command {
        long timestamp;
        String command;

        Command(long timestamp, String command) {
            this.timestamp = timestamp;
            this.command = command;
        }
    }

    private static class TempoEvent {
        long tick;
        float mpqn;  // Microseconds per quarter note

        TempoEvent(long tick, float mpqn) {
            this.tick = tick;
            this.mpqn = mpqn;
        }
    }
}