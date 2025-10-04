package com.example.synthcontroller;

import android.app.AlertDialog;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
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
import com.rejowan.rotaryknob.RotaryKnob;

import java.util.List;

public class PerformActivity extends AppCompatActivity {
    private static final String TAG = "PerformActivity";
    private PianoView pianoView;
    private TextView octaveTextView;

    private PresetManager presetManager;
    private Spinner presetSpinner;
    private Button savePresetButton;
    private Button loadPresetButton;

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

        setupTabLayout();

        setupLandscapeControls();
        setupPresetControls();
    }

    private void setupPresetControls() {
        presetManager = new PresetManager(this);

        presetSpinner = findViewById(R.id.presetSpinner);
        loadPresetButton = findViewById(R.id.loadPresetButton);
        savePresetButton = findViewById(R.id.savePresetButton);

        if (presetSpinner != null && loadPresetButton != null && savePresetButton != null) {
            updatePresetSpinner();

            loadPresetButton.setOnClickListener(v -> loadSelectedPreset());
            savePresetButton.setOnClickListener(v -> showSavePresetDialog());
        } else {
            Log.e(TAG, "Preset UI elements not found in layout");
        }
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

        updateOctaveDisplay(currentOctave); // Pass currentOctave as an argument
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

    private void updateOctaveDisplay(int octave) {
        TextView octaveTextView = findViewById(R.id.octaveTextView);
        octaveTextView.setText(String.valueOf(octave)); // Convert integer to string
    }

    private void changeOctave(int delta) {
        currentOctave += delta;

        // Limit octave range (0-8)
        if (currentOctave < 0) currentOctave = 0;
        if (currentOctave > 8) currentOctave = 8;

        // Update the MIDI offset
        midiNoteOffset = (currentOctave + 1) * 12; // C(octave+1)

        updateOctaveDisplay(currentOctave); // Pass currentOctave as an argument
        sendCommand("OCTAVE:", currentOctave - 4); // Octave offset from middle C
    }

    private void updatePresetSpinner() {
        List<String> presetNames = presetManager.getPresetNames();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, presetNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);
    }

    private void showSavePresetDialog() {
        Log.d(TAG, "Showing save preset dialog");

        // Use LayoutInflater to create a proper dialog view
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_save_preset, null);
        EditText input = dialogView.findViewById(R.id.presetNameInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Save Preset")
                .setView(dialogView)
                .setPositiveButton("Save", null) // Set to null initially
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .create();

        dialog.show();

        // Set button click listeners after dialog is shown
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(v -> {
            String presetName = input.getText().toString().trim();
            if (presetName.isEmpty()) {
                input.setError("Please enter a name");
            } else {
                Log.d(TAG, "Saving preset with name: " + presetName);
                saveCurrentPreset(presetName);
                dialog.dismiss();
            }
        });
    }


    public void saveCurrentPreset(String name) {
        Log.d(TAG, "Saving preset with name: " + name);

        // Create preset from current values
        SynthPreset preset = new SynthPreset();
        preset.setName(name);

        try {
            // Get waveform selections from the tab
            WaveformFragment waveformFragment = getWaveformFragment();
            if (waveformFragment != null) {
                preset.setMainWaveform(waveformFragment.getMainWaveformPosition());
                preset.setSubWaveform(waveformFragment.getSubWaveformPosition());
            }

            // Get ADSR values from the tab
            AdsrFragment adsrFragment = getAdsrFragment();
            if (adsrFragment != null) {
                preset.setAttack(adsrFragment.getAttackValue());
                preset.setDecay(adsrFragment.getDecayValue());
                preset.setSustain(adsrFragment.getSustainValue());
                preset.setRelease(adsrFragment.getReleaseValue());
            }

            // Get Effects values from the tab
            EffectsFragment effectsFragment = getEffectsFragment();
            if (effectsFragment != null) {
                preset.setFilter(effectsFragment.getFilterValue());
                preset.setDetune(effectsFragment.getDetuneValue());
                preset.setVibRate(effectsFragment.getVibRateValue());
                preset.setVibDepth(effectsFragment.getVibDepthValue());
            }

            // Save octave setting
            preset.setOctave(currentOctave);

            // Save preset
            if (presetManager == null) {
                presetManager = new PresetManager(this);
            }
            presetManager.savePreset(preset);

            // Update UI
            if (presetSpinner != null) {
                updatePresetSpinner();
                // Select the saved preset in the spinner
                int index = getPresetIndex(name);
                if (index >= 0 && index < presetSpinner.getAdapter().getCount()) {
                    presetSpinner.setSelection(index);
                }
            }

            Toast.makeText(this, "Preset saved: " + name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error saving preset", e);
            Toast.makeText(this, "Error saving preset", Toast.LENGTH_SHORT).show();
        }
    }



    public void loadPresetByName(String presetName) {
        if (presetManager == null) {
            presetManager = new PresetManager(this);
        }

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

        // Update UI to reflect loaded values
        updateUIFromPreset(preset);

        Toast.makeText(this, "Preset loaded: " + presetName, Toast.LENGTH_SHORT).show();
    }

    private WaveformFragment getWaveformFragment() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            TabLayout tabLayout = findViewById(R.id.tabLayout);
            ViewPager2 viewPager = findViewById(R.id.viewPager);

            if (tabLayout != null && viewPager != null) {
                SynthPagerAdapter adapter = (SynthPagerAdapter) viewPager.getAdapter();
                if (adapter != null) {
                    return (WaveformFragment) getSupportFragmentManager()
                            .findFragmentByTag("f" + adapter.getItemId(1));  // Changed from 3 to 1
                }
            }
        }
        return null;
    }

    private AdsrFragment getAdsrFragment() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            TabLayout tabLayout = findViewById(R.id.tabLayout);
            ViewPager2 viewPager = findViewById(R.id.viewPager);

            if (tabLayout != null && viewPager != null) {
                SynthPagerAdapter adapter = (SynthPagerAdapter) viewPager.getAdapter();
                if (adapter != null) {
                    return (AdsrFragment) getSupportFragmentManager()
                            .findFragmentByTag("f" + adapter.getItemId(2));  // Changed from 0 to 2
                }
            }
        }
        return null;
    }

    private EffectsFragment getEffectsFragment() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            TabLayout tabLayout = findViewById(R.id.tabLayout);
            ViewPager2 viewPager = findViewById(R.id.viewPager);

            if (tabLayout != null && viewPager != null) {
                SynthPagerAdapter adapter = (SynthPagerAdapter) viewPager.getAdapter();
                if (adapter != null) {
                    return (EffectsFragment) getSupportFragmentManager()
                            .findFragmentByTag("f" + adapter.getItemId(3));  // Changed from 1 to 3
                }
            }
        }
        return null;
    }

    private void saveKnobValueToPreset(SynthPreset preset, int knobId, String knobName) {
        RotaryKnob knob = findViewById(knobId);
        if (knob != null) {
            int value = knob.getCurrentProgress();
            Log.d(TAG, knobName + " value: " + value);

            switch (knobName) {
                case "Attack": preset.setAttack(value); break;
                case "Decay": preset.setDecay(value); break;
                case "Sustain": preset.setSustain(value); break;
                case "Release": preset.setRelease(value); break;
                case "Filter": preset.setFilter(value); break;
                case "Detune": preset.setDetune(value); break;
                case "VibRate": preset.setVibRate(value); break;
                case "VibDepth": preset.setVibDepth(value); break;
            }
        }
    }

    private int getPresetIndex(String name) {
        List<String> presetNames = presetManager.getPresetNames();
        for (int i = 0; i < presetNames.size(); i++) {
            if (presetNames.get(i).equals(name)) {
                return i;
            }
        }
        return 0;
    }

    private void loadSelectedPreset() {
        if (presetSpinner.getSelectedItem() == null) {
            Toast.makeText(this, "No preset selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String presetName = presetSpinner.getSelectedItem().toString();
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

        // Update UI to reflect loaded values
        updateUIFromPreset(preset);

        Toast.makeText(this, "Preset loaded: " + presetName, Toast.LENGTH_SHORT).show();
    }

    private void updateUIFromPreset(SynthPreset preset) {
        // Update waveform fragment
        WaveformFragment waveformFragment = getWaveformFragment();
        if (waveformFragment != null) {
            waveformFragment.updateWaveformSelections(preset.getMainWaveform(), preset.getSubWaveform());
        }

        // Update ADSR fragment
        AdsrFragment adsrFragment = getAdsrFragment();
        if (adsrFragment != null) {
            adsrFragment.updateAdsrValues(preset.getAttack(), preset.getDecay(),
                    preset.getSustain(), preset.getRelease());
        }

        // Update Effects fragment
        EffectsFragment effectsFragment = getEffectsFragment();
        if (effectsFragment != null) {
            effectsFragment.updateEffectValues(preset.getFilter(), preset.getDetune(),
                    preset.getVibRate(), preset.getVibDepth());
        }

        // Update landscape controls if available
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            updateSpinnerIfExists(R.id.mainWaveSpinner, preset.getMainWaveform());
            updateSpinnerIfExists(R.id.subWaveSpinner, preset.getSubWaveform());
            updateKnobIfExists(R.id.attackKnob, preset.getAttack());
            updateKnobIfExists(R.id.decayKnob, preset.getDecay());
            updateKnobIfExists(R.id.sustainKnob, preset.getSustain());
            updateKnobIfExists(R.id.releaseKnob, preset.getRelease());
            updateKnobIfExists(R.id.filterKnob, preset.getFilter());
            updateKnobIfExists(R.id.detuneKnob, preset.getDetune());
            updateKnobIfExists(R.id.vibRateKnob, preset.getVibRate());
            updateKnobIfExists(R.id.vibDepthKnob, preset.getVibDepth());
        }

        // Update octave
        currentOctave = preset.getOctave();
        updateOctaveDisplay(currentOctave);
    }

    private void updateKnobIfExists(int id, int value) {
        RotaryKnob knob = findViewById(id);
        if (knob != null) {
            knob.setCurrentProgress(value);
        }
    }

    private void updateSpinnerIfExists(int id, int position) {
        Spinner spinner = findViewById(id);
        if (spinner != null && position >= 0 && position < spinner.getCount()) {
            spinner.setSelection(position);
        }
    }


    private void setupTabLayout() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            TabLayout tabLayout = findViewById(R.id.tabLayout);
            ViewPager2 viewPager = findViewById(R.id.viewPager);

            if (viewPager != null) {
                // Disable swipe navigation to prevent interference with knob controls
                viewPager.setUserInputEnabled(false);

                // Use FragmentStateAdapter with FragmentActivity
                viewPager.setAdapter(new SynthPagerAdapter(this));


                new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
                    switch (position) {
                        case 0: tab.setText("Presets"); break;  // Added this tab
                        case 1: tab.setText("Waveform"); break;
                        case 2: tab.setText("ADSR"); break;
                        case 3: tab.setText("Effects"); break;
                    }
                }).attach();;
            } else {
                Log.e(TAG, "ViewPager2 is null");
            }
        } else {
            Log.i(TAG, "Landscape mode: ViewPager2 and TabLayout are not used");
        }
    }

    // Adapter class
    private static class SynthPagerAdapter extends FragmentStateAdapter {
        public SynthPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @Override
        public int getItemCount() {
            return 4;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return new PresetFragment();
                case 1: return new WaveformFragment();  // Changed order
                case 2: return new AdsrFragment();      // Changed order
                case 3: return new EffectsFragment();   // Changed order
                default: return new PresetFragment();
            }
        }
    }

    private void setupLandscapeControls() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Setup preset controls for landscape mode
            presetManager = new PresetManager(this);

            presetSpinner = findViewById(R.id.presetSpinner);
            savePresetButton = findViewById(R.id.savePresetButton);
            loadPresetButton = findViewById(R.id.loadPresetButton);

            if (presetSpinner != null && savePresetButton != null && loadPresetButton != null) {
                updatePresetSpinner();

                savePresetButton.setOnClickListener(v -> showSavePresetDialog());
                loadPresetButton.setOnClickListener(v -> loadSelectedPreset());
            }

            // Setup other landscape controls...
            setupLandscapeAdsrControls();
            setupLandscapeEffectControls();
            setupLandscapeWaveformControls();
        }
    }

    private void setupLandscapeWaveformControls() {
        // Find spinners in landscape layout
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

    private void setupLandscapeAdsrControls() {
        // Find ADSR knobs in landscape layout
        RotaryKnob attackKnob = findViewById(R.id.attackKnob);
        RotaryKnob decayKnob = findViewById(R.id.decayKnob);
        RotaryKnob sustainKnob = findViewById(R.id.sustainKnob);
        RotaryKnob releaseKnob = findViewById(R.id.releaseKnob);

        // Configure ADSR knobs if they exist
        if (attackKnob != null) {
            attackKnob.setMin(0);
            attackKnob.setMax(255);
            attackKnob.setCurrentProgress(50);
            attackKnob.setProgressChangeListener(progress ->
                    sendCommand("ATTACK:", progress));
        }

        if (decayKnob != null) {
            decayKnob.setMin(0);
            decayKnob.setMax(255);
            decayKnob.setCurrentProgress(100);
            decayKnob.setProgressChangeListener(progress ->
                    sendCommand("DECAY:", progress));
        }

        if (sustainKnob != null) {
            sustainKnob.setMin(0);
            sustainKnob.setMax(255);
            sustainKnob.setCurrentProgress(180);
            sustainKnob.setProgressChangeListener(progress ->
                    sendCommand("SUSTAIN:", progress));
        }

        if (releaseKnob != null) {
            releaseKnob.setMin(0);
            releaseKnob.setMax(255);
            releaseKnob.setCurrentProgress(100);
            releaseKnob.setProgressChangeListener(progress ->
                    sendCommand("RELEASE:", progress));
        }
    }

    private void setupLandscapeEffectControls() {
        // Find effects knobs in landscape layout
        RotaryKnob filterKnob = findViewById(R.id.filterKnob);
        RotaryKnob detuneKnob = findViewById(R.id.detuneKnob);
        RotaryKnob vibRateKnob = findViewById(R.id.vibRateKnob);
        RotaryKnob vibDepthKnob = findViewById(R.id.vibDepthKnob);

        // Configure effects knobs if they exist
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


    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save current octave setting
        outState.putInt("currentOctave", currentOctave);

        // Save synth parameters based on current orientation
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Save landscape controls
            saveKnobValueIfExists(outState, R.id.attackKnob, "attackValue");
            saveKnobValueIfExists(outState, R.id.decayKnob, "decayValue");
            saveKnobValueIfExists(outState, R.id.sustainKnob, "sustainValue");
            saveKnobValueIfExists(outState, R.id.releaseKnob, "releaseValue");
            saveKnobValueIfExists(outState, R.id.filterKnob, "filterValue");
            saveKnobValueIfExists(outState, R.id.detuneKnob, "detuneValue");
            saveKnobValueIfExists(outState, R.id.vibRateKnob, "vibRateValue");
            saveKnobValueIfExists(outState, R.id.vibDepthKnob, "vibDepthValue");
            saveSpinnerValueIfExists(outState, R.id.mainWaveSpinner, "mainWaveform");
            saveSpinnerValueIfExists(outState, R.id.subWaveSpinner, "subWaveform");
        } else {
            // Save fragment values
            WaveformFragment waveFragment = getWaveformFragment();
            if (waveFragment != null) {
                outState.putInt("mainWaveform", waveFragment.getMainWaveformPosition());
                outState.putInt("subWaveform", waveFragment.getSubWaveformPosition());
            }

            AdsrFragment adsrFragment = getAdsrFragment();
            if (adsrFragment != null) {
                outState.putInt("attackValue", adsrFragment.getAttackValue());
                outState.putInt("decayValue", adsrFragment.getDecayValue());
                outState.putInt("sustainValue", adsrFragment.getSustainValue());
                outState.putInt("releaseValue", adsrFragment.getReleaseValue());
            }

            EffectsFragment effectsFragment = getEffectsFragment();
            if (effectsFragment != null) {
                outState.putInt("filterValue", effectsFragment.getFilterValue());
                outState.putInt("detuneValue", effectsFragment.getDetuneValue());
                outState.putInt("vibRateValue", effectsFragment.getVibRateValue());
                outState.putInt("vibDepthValue", effectsFragment.getVibDepthValue());
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        // Restore octave
        currentOctave = savedInstanceState.getInt("currentOctave", 3);
        updateOctaveDisplay(currentOctave);

        // Get all saved values
        int attackValue = savedInstanceState.getInt("attackValue", 50);
        int decayValue = savedInstanceState.getInt("decayValue", 100);
        int sustainValue = savedInstanceState.getInt("sustainValue", 180);
        int releaseValue = savedInstanceState.getInt("releaseValue", 100);
        int filterValue = savedInstanceState.getInt("filterValue", 255);
        int detuneValue = savedInstanceState.getInt("detuneValue", 0);
        int vibRateValue = savedInstanceState.getInt("vibRateValue", 0);
        int vibDepthValue = savedInstanceState.getInt("vibDepthValue", 0);
        int mainWaveform = savedInstanceState.getInt("mainWaveform", 0);
        int subWaveform = savedInstanceState.getInt("subWaveform", 1);

        // Update UI based on current orientation
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            updateKnobIfExists(R.id.attackKnob, attackValue);
            updateKnobIfExists(R.id.decayKnob, decayValue);
            updateKnobIfExists(R.id.sustainKnob, sustainValue);
            updateKnobIfExists(R.id.releaseKnob, releaseValue);
            updateKnobIfExists(R.id.filterKnob, filterValue);
            updateKnobIfExists(R.id.detuneKnob, detuneValue);
            updateKnobIfExists(R.id.vibRateKnob, vibRateValue);
            updateKnobIfExists(R.id.vibDepthKnob, vibDepthValue);
            updateSpinnerIfExists(R.id.mainWaveSpinner, mainWaveform);
            updateSpinnerIfExists(R.id.subWaveSpinner, subWaveform);
        } else {
            // Update fragments
            WaveformFragment waveFragment = getWaveformFragment();
            if (waveFragment != null) {
                waveFragment.updateWaveformSelections(mainWaveform, subWaveform);
            }

            AdsrFragment adsrFragment = getAdsrFragment();
            if (adsrFragment != null) {
                adsrFragment.updateAdsrValues(attackValue, decayValue, sustainValue, releaseValue);
            }

            EffectsFragment effectsFragment = getEffectsFragment();
            if (effectsFragment != null) {
                effectsFragment.updateEffectValues(filterValue, detuneValue, vibRateValue, vibDepthValue);
            }
        }

        // Send all parameters to the ESP32
        sendCommand("MAIN_WAVE:", mainWaveform);
        sendCommand("SUB_WAVE:", subWaveform);
        sendCommand("ATTACK:", attackValue);
        sendCommand("DECAY:", decayValue);
        sendCommand("SUSTAIN:", sustainValue);
        sendCommand("RELEASE:", releaseValue);
        sendCommand("FILTER:", filterValue);
        sendCommand("DETUNE:", detuneValue);
        sendCommand("VIB_RATE:", vibRateValue);
        sendCommand("VIB_DEPTH:", vibDepthValue);
        sendCommand("OCTAVE:", currentOctave - 4);
    }

    // Helper method to save knob values
    private void saveKnobValueIfExists(Bundle outState, int knobId, String key) {
        RotaryKnob knob = findViewById(knobId);
        if (knob != null) {
            outState.putInt(key, knob.getCurrentProgress());
        }
    }

    // Helper method to save spinner values
    private void saveSpinnerValueIfExists(Bundle outState, int spinnerId, String key) {
        Spinner spinner = findViewById(spinnerId);
        if (spinner != null) {
            outState.putInt(key, spinner.getSelectedItemPosition());
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