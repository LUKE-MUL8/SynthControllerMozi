package com.example.synthcontroller;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomnavigation.BottomNavigationView;
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

    private AtomicBoolean isPlaying = new AtomicBoolean(false);
    private Thread playbackThread = null;
    private PresetManager presetManager;
    private Uri selectedMidiFileUri;

    private final ActivityResultLauncher<String> openFileLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedMidiFileUri = uri;
                    findViewById(R.id.playButton).setEnabled(true);
                    Toast.makeText(this, "MIDI file selected", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_midi_file_playback);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

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
            initializeSynthParameters();
            setupSynthControls();
        }

        presetManager = new PresetManager(this);
        setupPresetControls();

        Button playButton = findViewById(R.id.playButton);
        playButton.setEnabled(false);
        playButton.setOnClickListener(v -> {
            if (selectedMidiFileUri != null) {
                playMidiFile(selectedMidiFileUri);
            } else {
                Toast.makeText(this, "Please select a MIDI file first", Toast.LENGTH_SHORT).show();
            }
        });

        Button stopButton = findViewById(R.id.stopButton);
        stopButton.setOnClickListener(v -> stopPlayback());

        Button selectFileButton = findViewById(R.id.selectFileButton);
        selectFileButton.setOnClickListener(v -> {
            Log.d(TAG, "Select file button clicked");
            openFileLauncher.launch("audio/midi");
        });

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_midi);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_perform) {
                startActivity(new Intent(this, PerformActivity.class));
                return true;
            } else if (itemId == R.id.nav_midi) {
                return true;
            } else if (itemId == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    private void setupPresetControls() {
        Spinner presetSpinner = findViewById(R.id.presetSpinner);
        Button loadPresetButton = findViewById(R.id.loadPresetButton);
        Button savePresetButton = findViewById(R.id.savePresetButton);
        ImageView prevPresetButton = findViewById(R.id.prevPresetButton);
        ImageView nextPresetButton = findViewById(R.id.nextPresetButton);

        updatePresetSpinner(presetSpinner);

        loadPresetButton.setOnClickListener(v -> loadSelectedPreset(presetSpinner));
        savePresetButton.setOnClickListener(v -> showSavePresetDialog());

        prevPresetButton.setOnClickListener(v -> {
            int currentPosition = presetSpinner.getSelectedItemPosition();
            if (currentPosition > 0) {
                presetSpinner.setSelection(currentPosition - 1);
            }
        });

        nextPresetButton.setOnClickListener(v -> {
            int currentPosition = presetSpinner.getSelectedItemPosition();
            if (currentPosition < presetSpinner.getAdapter().getCount() - 1) {
                presetSpinner.setSelection(currentPosition + 1);
            }
        });
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

        sendCommand("ATTACK:", preset.getAttack());
        sendCommand("DECAY:", preset.getDecay());
        sendCommand("SUSTAIN:", preset.getSustain());
        sendCommand("RELEASE:", preset.getRelease());

        updateUIFromPreset(preset);

        Toast.makeText(this, "Preset loaded: " + presetName, Toast.LENGTH_SHORT).show();
    }

    private void updateUIFromPreset(SynthPreset preset) {
        updateKnobIfExists(R.id.attackKnob, preset.getAttack());
        updateKnobIfExists(R.id.decayKnob, preset.getDecay());
        updateKnobIfExists(R.id.sustainKnob, preset.getSustain());
        updateKnobIfExists(R.id.releaseKnob, preset.getRelease());
    }

    private void updateKnobIfExists(int id, int value) {
        RotaryKnob knob = findViewById(id);
        if (knob != null) {
            knob.setCurrentProgress(value);
        }
    }

    private void showSavePresetDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_save_preset, null);
        EditText input = dialogView.findViewById(R.id.presetNameInput);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveButton = dialogView.findViewById(R.id.saveButton);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        saveButton.setOnClickListener(v -> {
            String presetName = input.getText().toString().trim();
            if (presetName.isEmpty()) {
                input.setError("Please enter a name");
            } else {
                saveCurrentPreset(presetName);
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void saveCurrentPreset(String name) {
        SynthPreset preset = new SynthPreset();
        preset.setName(name);

        preset.setAttack(getKnobValue(R.id.attackKnob));
        preset.setDecay(getKnobValue(R.id.decayKnob));
        preset.setSustain(getKnobValue(R.id.sustainKnob));
        preset.setRelease(getKnobValue(R.id.releaseKnob));

        presetManager.savePreset(preset);

        updatePresetSpinner(findViewById(R.id.presetSpinner));

        Toast.makeText(this, "Preset saved: " + name, Toast.LENGTH_SHORT).show();
    }

    private int getKnobValue(int knobId) {
        RotaryKnob knob = findViewById(knobId);
        return knob != null ? knob.getCurrentProgress() : 0;
    }

    private void setupSynthControls() {
        setupAdsrControls();
    }

    private void setupAdsrControls() {
        RotaryKnob attackKnob = findViewById(R.id.attackKnob);
        RotaryKnob decayKnob = findViewById(R.id.decayKnob);
        RotaryKnob sustainKnob = findViewById(R.id.sustainKnob);
        RotaryKnob releaseKnob = findViewById(R.id.releaseKnob);

        if (attackKnob != null) {
            attackKnob.setMin(0);
            attackKnob.setMax(255);
            attackKnob.setCurrentProgress(0);
            attackKnob.setProgressChangeListener(progress -> sendCommand("ATTACK:", progress));
        }

        if (decayKnob != null) {
            decayKnob.setMin(0);
            decayKnob.setMax(255);
            decayKnob.setCurrentProgress(255);
            decayKnob.setProgressChangeListener(progress -> sendCommand("DECAY:", progress));
        }

        if (sustainKnob != null) {
            sustainKnob.setMin(0);
            sustainKnob.setMax(255);
            sustainKnob.setCurrentProgress(0);
            sustainKnob.setProgressChangeListener(progress -> sendCommand("SUSTAIN:", progress));
        }

        if (releaseKnob != null) {
            releaseKnob.setMin(0);
            releaseKnob.setMax(255);
            releaseKnob.setCurrentProgress(0);
            releaseKnob.setProgressChangeListener(progress -> sendCommand("RELEASE:", progress));
        }
    }

    private void initializeSynthParameters() {
        Log.d(TAG, "Setting synth parameters for MIDI playback");
        BluetoothManager btManager = BluetoothManager.getInstance();
        btManager.sendCommand("DECAY:255");
        btManager.sendCommand("FILTER:255");
        btManager.sendCommand("ATTACK:0");
        btManager.sendCommand("SUSTAIN:0");
        btManager.sendCommand("RELEASE:0");
        btManager.sendCommand("DETUNE:0");
        Toast.makeText(this, "Synth parameters initialized for MIDI playback", Toast.LENGTH_SHORT).show();
    }

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
            sendAllNotesOff();
            Toast.makeText(this, "Playback stopped", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Playback stopped by user");
        } else {
            Toast.makeText(this, "No playback in progress", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendAllNotesOff() {
        for (int note = 0; note < 128; note++) {
            BluetoothManager.getInstance().sendCommand("UP:" + note);
        }
        Log.d(TAG, "Sent all notes off");
        sendCommand("PANIC:", 1);
    }

    private void playMidiFile(Uri uri) {
        if (isPlaying.get()) {
            stopPlayback();
        }
        isPlaying.set(true);
        playbackThread = new Thread(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                MidiFile midiFile = new MidiFile(inputStream);

                List<Command> commands = new ArrayList<>();
                int ticksPerQuarterNote = midiFile.getResolution();

                List<TempoEvent> tempoEvents = new ArrayList<>();
                for (MidiTrack track : midiFile.getTracks()) {
                    long currentTick = 0;
                    for (MidiEvent event : track.getEvents()) {
                        currentTick += event.getDelta();
                        if (event instanceof Tempo) {
                            Tempo tempo = (Tempo) event;
                            tempoEvents.add(new TempoEvent(currentTick, tempo.getMpqn()));
                        }
                    }
                }
                tempoEvents.sort((a, b) -> Long.compare(a.tick, b.tick));

                for (MidiTrack track : midiFile.getTracks()) {
                    long currentTick = 0;
                    for (MidiEvent event : track.getEvents()) {
                        currentTick += event.getDelta();
                        long ms = ticksToMs(currentTick, tempoEvents, ticksPerQuarterNote);
                        if (event instanceof NoteOn) {
                            NoteOn noteOn = (NoteOn) event;
                            String command = (noteOn.getVelocity() > 0) ? "DOWN:" + noteOn.getNoteValue() : "UP:" + noteOn.getNoteValue();
                            commands.add(new Command(ms, command));
                        } else if (event instanceof NoteOff) {
                            NoteOff noteOff = (NoteOff) event;
                            String command = "UP:" + noteOff.getNoteValue();
                            commands.add(new Command(ms, command));
                        }
                    }
                }
                commands.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));

                long startTime = System.currentTimeMillis();
                for (Command cmd : commands) {
                    if (!isPlaying.get()) return;
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long delay = cmd.timestamp - elapsedTime;
                    if (delay > 0) Thread.sleep(delay);
                    if (!isPlaying.get()) return;
                    if (!BluetoothManager.getInstance().sendCommand(cmd.command)) {
                        runOnUiThread(() -> Toast.makeText(this, "Playback interrupted", Toast.LENGTH_SHORT).show());
                        isPlaying.set(false);
                        return;
                    }
                }
                runOnUiThread(() -> Toast.makeText(this, "MIDI playback completed", Toast.LENGTH_SHORT).show());
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Error during playback: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Playback failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                isPlaying.set(false);
            }
        });
        playbackThread.start();
    }

    private long ticksToMs(long tick, List<TempoEvent> tempoEvents, int ticksPerQuarterNote) {
        if (tempoEvents.isEmpty()) {
            return (long)((tick * 500000.0f) / (ticksPerQuarterNote * 1000.0f));
        }
        long ms = 0;
        long lastTick = 0;
        float currentMpqn = 500000.0f;
        for (TempoEvent event : tempoEvents) {
            if (event.tick < tick) {
                ms += ((event.tick - lastTick) * currentMpqn) / (ticksPerQuarterNote * 1000.0f);
                lastTick = event.tick;
                currentMpqn = event.mpqn;
            } else {
                break;
            }
        }
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
        float mpqn;
        TempoEvent(long tick, float mpqn) {
            this.tick = tick;
            this.mpqn = mpqn;
        }
    }
}
