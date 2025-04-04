package com.example.synthcontroller;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.rejowan.rotaryknob.RotaryKnob;


public class EffectsFragment extends Fragment {
    private RotaryKnob filterKnob, detuneKnob, reverbKnob, vibRateKnob, vibDepthKnob;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_tab_effects, container, false);

        filterKnob = view.findViewById(R.id.filterKnob);
        detuneKnob = view.findViewById(R.id.detuneKnob);
        reverbKnob = view.findViewById(R.id.reverbKnob);
        vibRateKnob = view.findViewById(R.id.vibRateKnob);
        vibDepthKnob = view.findViewById(R.id.vibDepthKnob);

        // Set default values
        filterKnob.setCurrentProgress(255);
        detuneKnob.setCurrentProgress(0);
        reverbKnob.setCurrentProgress(100);
        vibRateKnob.setCurrentProgress(20);
        vibDepthKnob.setCurrentProgress(0);

        // Setup listeners
        setupKnob(filterKnob, "FILTER:");
        setupKnob(detuneKnob, "DETUNE:");
        setupKnob(reverbKnob, "REVERB:");
        setupKnob(vibRateKnob, "VIB_RATE:");
        setupKnob(vibDepthKnob, "VIB_DEPTH:");

        return view;
    }

    private void setupKnob(RotaryKnob knob, String command) {
        knob.setMin(0);
        knob.setMax(255);
        knob.setProgressChangeListener(progress ->
                ((PerformActivity)getActivity()).sendCommand(command, progress));
    }
}