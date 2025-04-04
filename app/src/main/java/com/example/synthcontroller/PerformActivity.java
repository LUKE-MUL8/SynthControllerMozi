package com.example.synthcontroller;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.convergencelabstfx.pianoview.PianoTouchListener;
import com.convergencelabstfx.pianoview.PianoView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class PerformActivity extends AppCompatActivity {
    private static final String TAG = "PerformActivity";
    private PianoView pianoView;
    private TextView octaveTextView;

    // Define the starting MIDI note and octave
    private int midiNoteOffset = 48; // C3
    private int currentOctave = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perform);

        // Initialize piano and other controls
        pianoView = findViewById(R.id.pianoView);
        octaveTextView = findViewById(R.id.octaveTextView);

        // Set up piano listeners
        setupPianoView();

        // Set up octave controls
        setupOctaveControls();

        // Set up panic button
        Button panicButton = findViewById(R.id.panicButton);
        panicButton.setOnClickListener(v -> sendAllNotesOff());

        // Initialize Bluetooth
        if (BluetoothManager.getInstance().connect()) {
            Toast.makeText(this, "Connected to synthesizer", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Failed to connect", Toast.LENGTH_LONG).show();
        }

        // Setup the tab layout
        setupTabLayout();
    }

    private void setupPianoView() {
        pianoView.addPianoTouchListener(new PianoTouchListener() {
            @Override
            public void onKeyDown(PianoView piano, int key) {
                int midiNote = midiNoteOffset + key;
                sendCommand("DOWN:", midiNote);
                Log.d(TAG, "Key down: " + key + " -> MIDI: " + midiNote);
            }

            @Override
            public void onKeyUp(PianoView piano, int key) {
                int midiNote = midiNoteOffset + key;
                sendCommand("UP:", midiNote);
                Log.d(TAG, "Key up: " + key + " -> MIDI: " + midiNote);
            }

            @Override
            public void onKeyClick(PianoView piano, int key) {
                // Optional - handle click events if needed
            }
        });
    }

    private void setupOctaveControls() {
        Button octaveUpButton = findViewById(R.id.octaveUpButton);
        Button octaveDownButton = findViewById(R.id.octaveDownButton);

        octaveUpButton.setOnClickListener(v -> changeOctave(1));
        octaveDownButton.setOnClickListener(v -> changeOctave(-1));

        updateOctaveDisplay();
    }

    private void sendAllNotesOff() {
        sendCommand("PANIC:", 1);
    }

    // Single implementation of sendCommand
    public void sendCommand(String command, int value) {
        String fullCommand = command + value;
        Log.d(TAG, "Sending command: " + fullCommand);

        if (BluetoothManager.getInstance().isConnected()) {
            BluetoothManager.getInstance().sendCommand(fullCommand);
        } else {
            Toast.makeText(this, "Failed to send command", Toast.LENGTH_SHORT).show();
        }
    }

    private void adjustPianoSizeBasedOnOrientation() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            pianoView.setNumberOfKeys(36);
        } else {
            pianoView.setNumberOfKeys(24);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        adjustPianoSizeBasedOnOrientation();
    }

    private void updateOctaveDisplay() {
        octaveTextView.setText("Octave: " + currentOctave);
    }

    private void changeOctave(int delta) {
        currentOctave += delta;

        // Limit octave range (0-8)
        if (currentOctave < 0) currentOctave = 0;
        if (currentOctave > 8) currentOctave = 8;

        // Update the MIDI offset
        midiNoteOffset = (currentOctave + 1) * 12; // C(octave+1)

        updateOctaveDisplay();
        sendCommand("OCTAVE:", currentOctave - 4); // Octave offset from middle C
    }

    private void setupTabLayout() {
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        ViewPager2 viewPager = findViewById(R.id.viewPager);

        // Disable swipe navigation to prevent interference with knob controls
        viewPager.setUserInputEnabled(false);

        // Use FragmentStateAdapter with FragmentActivity
        viewPager.setAdapter(new SynthPagerAdapter(this));

        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("ADSR"); break;
                case 1: tab.setText("Effects"); break;
                case 2: tab.setText("Waveform"); break;
            }
        }).attach();
    }

    // Adapter class
    private static class SynthPagerAdapter extends FragmentStateAdapter {
        public SynthPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @Override
        public int getItemCount() {
            return 3;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return new AdsrFragment();
                case 1: return new EffectsFragment();
                case 2: return new WaveformFragment();
                default: return new AdsrFragment();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Send PANIC command to release all notes when activity is destroyed
        if (BluetoothManager.getInstance().isConnected()) {
            BluetoothManager.getInstance().sendCommand("PANIC");
        }
    }
}