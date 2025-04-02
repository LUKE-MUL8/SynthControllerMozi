package com.example.synthcontroller;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
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
        }

        // Open MIDI file button
        Button openMidiButton = findViewById(R.id.openMidiButton);
        openMidiButton.setOnClickListener(v -> openFileLauncher.launch("audio/midi"));

        // Renamed button from stopButton to stopButton for clarity
        Button stopButton = findViewById(R.id.playButton);
        stopButton.setText("Stop"); // Change button text to reflect its new function
        stopButton.setOnClickListener(v -> stopPlayback());
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