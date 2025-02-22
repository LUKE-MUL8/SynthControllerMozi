package com.example.synthcontroller;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.leff.midi.MidiFile;
import com.leff.midi.MidiTrack;
import com.leff.midi.event.MidiEvent;
import com.leff.midi.event.NoteOff;
import com.leff.midi.event.NoteOn;
import com.leff.midi.event.meta.Tempo;
import com.leff.midi.util.MidiEventListener;
import com.leff.midi.util.MidiProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MidiFilePlaybackActivity extends AppCompatActivity {
    private static final String TAG = "MidiPlaybackActivity";
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice device;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String BT_DEVICE_NAME = "HC-02";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 100;
    private MidiProcessor midiProcessor;

    // Launcher for picking a MIDI file
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

        checkAndRequestPermissions();

        // Open MIDI file button
        Button openMidiButton = findViewById(R.id.openMidiButton);
        openMidiButton.setOnClickListener(v -> openFileLauncher.launch("audio/midi"));

        // Play test note button (for debugging)
        Button playButton = findViewById(R.id.playButton);
        playButton.setOnClickListener(v -> playTestNote());
    }

    private void checkAndRequestPermissions() {
        String[] permissions = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.READ_EXTERNAL_STORAGE} :
                new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.READ_EXTERNAL_STORAGE};

        boolean allGranted = true;
        for (String perm : permissions) {
            if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        } else {
            initializeBluetooth();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeBluetooth();
        } else {
            Toast.makeText(this, "Permissions denied", Toast.LENGTH_LONG).show();
        }
    }

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            connectToDevice();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            connectToDevice();
        } else if (resultCode != RESULT_OK) {
            Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_LONG).show();
        }
    }

    private void connectToDevice() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice pairedDevice : pairedDevices) {
            Log.d(TAG, "Paired device: " + pairedDevice.getName());
            if (BT_DEVICE_NAME.equals(pairedDevice.getName())) {
                device = pairedDevice;
                break;
            }
        }

        if (device == null) {
            Toast.makeText(this, "HC-02 not found", Toast.LENGTH_LONG).show();
            return;
        }

        new Thread(() -> {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(BT_UUID);
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                runOnUiThread(() -> Toast.makeText(this, "Connected to " + BT_DEVICE_NAME, Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Failed to connect: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void playTestNote() {
        if (outputStream == null) {
            Toast.makeText(this, "Not connected to HC-02", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                outputStream.write("TEST\n".getBytes());
                Log.d(TAG, "Sent: TEST");
            } catch (IOException e) {
                Log.e(TAG, "Error: " + e.getMessage());
            }
        }).start();
    }

    private void playMidiFile(android.net.Uri uri) {
        if (outputStream == null) {
            Toast.makeText(this, "Not connected to HC-02", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                // Open the MIDI file from URI
                InputStream inputStream = getContentResolver().openInputStream(uri);
                MidiFile midiFile = new MidiFile(inputStream);
                inputStream.close();

                // List to store commands with timestamps
                List<Command> commands = new ArrayList<>();
                float microsecondsPerQuarterNote = 500000.0f; // Default tempo: 120 BPM
                int ticksPerQuarterNote = midiFile.getResolution();

                // Process tracks (assuming a simple file with one track)
                for (MidiTrack track : midiFile.getTracks()) {
                    long currentTick = 0; // Cumulative ticks
                    Iterator<MidiEvent> it = track.getEvents().iterator();

                    while (it.hasNext()) {
                        MidiEvent event = it.next();
                        currentTick += event.getDelta(); // Add delta time to absolute tick

                        // Check for tempo events to update timing
                        if (event instanceof Tempo) {
                            Tempo tempo = (Tempo) event;
                            microsecondsPerQuarterNote = tempo.getMpqn(); // Microseconds per quarter note
                            Log.d(TAG, "Tempo updated: " + tempo.getBpm() + " BPM");
                        }

                        // Calculate milliseconds from ticks
                        long ms = (long) ((currentTick * microsecondsPerQuarterNote) / (ticksPerQuarterNote * 1000.0f));

                        if (event instanceof NoteOn) {
                            NoteOn noteOn = (NoteOn) event;
                            if (noteOn.getVelocity() > 0) {
                                String command = "DOWN:" + noteOn.getNoteValue() + "\n";
                                commands.add(new Command(ms, command));
                            } else {
                                String command = "UP:" + noteOn.getNoteValue() + "\n";
                                commands.add(new Command(ms, command));
                            }
                        } else if (event instanceof NoteOff) {
                            NoteOff noteOff = (NoteOff) event;
                            String command = "UP:" + noteOff.getNoteValue() + "\n";
                            commands.add(new Command(ms, command));
                        }
                    }
                }

                // Sort commands by timestamp (should already be sorted, but ensures correctness)
                commands.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));

                // Send commands with precise timing
                long startTime = System.currentTimeMillis();
                for (Command cmd : commands) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long delay = cmd.timestamp - elapsedTime;

                    if (delay > 0) {
                        Thread.sleep(delay); // Wait until the exact time to send
                    }

                    outputStream.write(cmd.command.getBytes());
                    Log.d(TAG, "Sent at " + cmd.timestamp + "ms: " + cmd.command.trim());
                }

                runOnUiThread(() -> Toast.makeText(this, "MIDI playback completed", Toast.LENGTH_SHORT).show());
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Error during playback: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Playback failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // Helper class for commands (unchanged)
    private static class Command {
        long timestamp; // in milliseconds
        String command;

        Command(long timestamp, String command) {
            this.timestamp = timestamp;
            this.command = command;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (midiProcessor != null) {
            midiProcessor.stop();
        }
        try {
            if (outputStream != null) outputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to close Bluetooth: " + e.getMessage());
        }
    }
}