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

        // Open MIDI file button
        Button openMidiButton = findViewById(R.id.openMidiButton);
        openMidiButton.setOnClickListener(v -> openFileLauncher.launch("audio/midi"));

        Button stopButton = findViewById(R.id.playButton);
        stopButton.setText("Stop");
        stopButton.setOnClickListener(v -> stopPlayback());
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
        RotaryKnob reverbKnob = findViewById(R.id.reverbKnob);
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

        if (reverbKnob != null) {
            reverbKnob.setMin(0);
            reverbKnob.setMax(255);
            reverbKnob.setCurrentProgress(0);
            reverbKnob.setProgressChangeListener(progress ->
                    sendCommand("REVERB:", progress));
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
        // Find spinners in layout
        Spinner mainWaveSpinner = findViewById(R.id.mainWaveSpinner);
        Spinner subWaveSpinner = findViewById(R.id.subWaveSpinner);

        String[] waveforms = {"Saw", "Square", "Sine", "Triangle"};

        if (mainWaveSpinner != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, waveforms);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            mainWaveSpinner.setAdapter(adapter);
            mainWaveSpinner.setSelection(0);  // Saw as default

            mainWaveSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    sendCommand("MAIN_WAVE:", position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        if (subWaveSpinner != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, waveforms);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            subWaveSpinner.setAdapter(adapter);
            subWaveSpinner.setSelection(1);  // Square as default

            subWaveSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    sendCommand("SUB_WAVE:", position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
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
        BluetoothManager.getInstance().sendCommand(prefix + value);
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
                float microsecondsPerQuarterNote = 500000.0f;
                int ticksPerQuarterNote = midiFile.getResolution();

                for (MidiTrack track : midiFile.getTracks()) {
                    long currentTick = 0;
                    Iterator<MidiEvent> it = track.getEvents().iterator();

                    while (it.hasNext()) {
                        MidiEvent event = it.next();
                        currentTick += event.getDelta();

                        if (event instanceof Tempo) {
                            Tempo tempo = (Tempo) event;
                            microsecondsPerQuarterNote = tempo.getMpqn();
                            Log.d(TAG, "Tempo updated: " + tempo.getBpm() + " BPM");
                        }

                        long ms = (long) ((currentTick * microsecondsPerQuarterNote) / (ticksPerQuarterNote * 1000.0f));

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
                for (Command cmd : commands) {
                    // Check if playback has been stopped
                    if (!isPlaying.get()) {
                        Log.d(TAG, "Playback stopped during execution");
                        return;
                    }

                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long delay = cmd.timestamp - elapsedTime;

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
}