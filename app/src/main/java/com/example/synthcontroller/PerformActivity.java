package com.example.synthcontroller;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.convergencelabstfx.pianoview.PianoTouchListener;
import com.convergencelabstfx.pianoview.PianoView;
import com.rejowan.rotaryknob.RotaryKnob;

public class PerformActivity extends AppCompatActivity {
    private static final String TAG = "PerformActivity";
    private PianoView pianoView;
    private RotaryKnob attackKnob, decayKnob, sustainKnob, releaseKnob, filterKnob, detuneKnob;

    // Define the starting MIDI note (e.g., 72 for C5)
    private static final int MIDI_NOTE_OFFSET = 72; // Adjust this value to change the starting note

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perform);

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

        // MIDI panic button
        Button panicButton = findViewById(R.id.panicButton);
        panicButton.setOnClickListener(v -> sendCommand("PANIC", 0));

        // Initialize knobs
        attackKnob = findViewById(R.id.attackKnob);
        decayKnob = findViewById(R.id.decayKnob);
        sustainKnob = findViewById(R.id.sustainKnob);
        releaseKnob = findViewById(R.id.releaseKnob);
        filterKnob = findViewById(R.id.filterKnob);
        detuneKnob = findViewById(R.id.detuneKnob);

        setupKnob(attackKnob, "ATTACK");
        setupKnob(decayKnob, "DECAY");
        setupKnob(sustainKnob, "SUSTAIN");
        setupKnob(releaseKnob, "RELEASE");
        setupKnob(filterKnob, "FILTER");
        setupKnob(detuneKnob, "DETUNE");

        // Piano setup
        pianoView = findViewById(R.id.pianoView);
        adjustPianoSizeBasedOnOrientation();

        pianoView.addPianoTouchListener(new PianoTouchListener() {
            @Override
            public void onKeyDown(PianoView piano, int key) {
                int midiNote = MIDI_NOTE_OFFSET + key; // Shift the key to a higher note
                sendCommand("DOWN", midiNote);
                Log.d(TAG, "Key down: " + key + " -> MIDI: " + midiNote); // Debug
            }

            @Override
            public void onKeyUp(PianoView piano, int key) {
                int midiNote = MIDI_NOTE_OFFSET + key; // Shift the key to a higher note
                sendCommand("UP", midiNote);
                Log.d(TAG, "Key up: " + key + " -> MIDI: " + midiNote); // Debug
            }

            @Override
            public void onKeyClick(PianoView piano, int key) {
                // No action needed for key click
            }
        });
    }

    private void setupKnob(RotaryKnob knob, String command) {
        knob.setMin(0);
        knob.setMax(255);
        knob.setCurrentProgress(0);
        knob.setProgressChangeListener(progress -> sendCommand(command, progress));
    }

    private void sendCommand(String action, int value) {
        String command = action + ":" + value;
        if (BluetoothManager.getInstance().sendCommand(command)) {
            Log.d(TAG, "Sent command: " + command);
        } else {
            Toast.makeText(this, "Failed to send command", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to send command: " + command);
        }
    }

    private void adjustPianoSizeBasedOnOrientation() {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) pianoView.getLayoutParams();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            params.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
            params.height = ConstraintLayout.LayoutParams.MATCH_PARENT;
        } else {
            params.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
            params.height = 600;
        }
        pianoView.setLayoutParams(params);
    }
}