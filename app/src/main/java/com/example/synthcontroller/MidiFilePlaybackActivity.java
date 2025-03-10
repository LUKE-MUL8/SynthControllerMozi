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

public class MidiFilePlaybackActivity extends AppCompatActivity {
    private static final String TAG = "MidiPlaybackActivity";

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
                    runOnUiThread(() -> Toast.makeText(this, "Reconnected", Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to reconnect", Toast.LENGTH_LONG).show();
                        finish();
                    });
                }
            }).start();
            return;
        }

        // Open MIDI file button
        Button openMidiButton = findViewById(R.id.openMidiButton);
        openMidiButton.setOnClickListener(v -> openFileLauncher.launch("audio/midi"));

        // Play test note button
        Button playButton = findViewById(R.id.playButton);
        playButton.setOnClickListener(v -> playTestNote());
    }

    private void playTestNote() {
        if (BluetoothManager.getInstance().sendCommand("TEST")) {
            Log.d(TAG, "Sent: TEST");
            runOnUiThread(() -> Toast.makeText(this, "Test note sent", Toast.LENGTH_SHORT).show());
        } else {
            Toast.makeText(this, "Failed to send test note", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to send: TEST");
        }
    }

    private void playMidiFile(Uri uri) {
        new Thread(() -> {
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
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long delay = cmd.timestamp - elapsedTime;

                    if (delay > 0) {
                        Thread.sleep(delay);
                    }

                    if (!BluetoothManager.getInstance().sendCommand(cmd.command)) {
                        Log.e(TAG, "Failed to send command during playback: " + cmd.command);
                        runOnUiThread(() -> Toast.makeText(this, "Playback interrupted", Toast.LENGTH_SHORT).show());
                        return;
                    }
                    Log.d(TAG, "Sent at " + cmd.timestamp + "ms: " + cmd.command);
                }

                runOnUiThread(() -> Toast.makeText(this, "MIDI playback completed", Toast.LENGTH_SHORT).show());
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Error during playback: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Playback failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
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