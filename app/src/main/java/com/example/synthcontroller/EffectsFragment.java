package com.example.synthcontroller;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import com.rejowan.rotaryknob.RotaryKnob;

public class EffectsFragment extends Fragment {
    private RotaryKnob filterKnob, detuneKnob, vibRateKnob, vibDepthKnob;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_tab_effects, container, false);

        filterKnob = view.findViewById(R.id.filterKnob);
        detuneKnob = view.findViewById(R.id.detuneKnob);
        vibRateKnob = view.findViewById(R.id.vibRateKnob);
        vibDepthKnob = view.findViewById(R.id.vibDepthKnob);

        // Configure filter knob
        if (filterKnob != null) {
            filterKnob.setMin(0);
            filterKnob.setMax(255);
            filterKnob.setCurrentProgress(255);
            filterKnob.setProgressChangeListener(progress ->
                    ((PerformActivity)getActivity()).sendCommand("FILTER:", progress));
        }

        // Configure detune knob
        if (detuneKnob != null) {
            detuneKnob.setMin(0);
            detuneKnob.setMax(255);
            detuneKnob.setCurrentProgress(0);
            detuneKnob.setProgressChangeListener(progress ->
                    ((PerformActivity)getActivity()).sendCommand("DETUNE:", progress));
        }

        // Configure vibrato rate knob
        if (vibRateKnob != null) {
            vibRateKnob.setMin(0);
            vibRateKnob.setMax(255);
            vibRateKnob.setCurrentProgress(0);
            vibRateKnob.setProgressChangeListener(progress ->
                    ((PerformActivity)getActivity()).sendCommand("VIB_RATE:", progress));
        }

        // Configure vibrato depth knob
        if (vibDepthKnob != null) {
            vibDepthKnob.setMin(0);
            vibDepthKnob.setMax(255);
            vibDepthKnob.setCurrentProgress(0);
            vibDepthKnob.setProgressChangeListener(progress ->
                    ((PerformActivity)getActivity()).sendCommand("VIB_DEPTH:", progress));
        }

        return view;
    }

    // Add getter methods for preset functionality
    public int getFilterValue() {
        return filterKnob != null ? filterKnob.getCurrentProgress() : 255;
    }

    public int getDetuneValue() {
        return detuneKnob != null ? detuneKnob.getCurrentProgress() : 0;
    }

    public int getVibRateValue() {
        return vibRateKnob != null ? vibRateKnob.getCurrentProgress() : 0;
    }

    public int getVibDepthValue() {
        return vibDepthKnob != null ? vibDepthKnob.getCurrentProgress() : 0;
    }

    // Method to update UI when loading presets
    public void updateEffectValues(int filter, int detune, int vibRate, int vibDepth) {
        if (filterKnob != null) {
            filterKnob.setCurrentProgress(filter);
        }

        if (detuneKnob != null) {
            detuneKnob.setCurrentProgress(detune);
        }

        if (vibRateKnob != null) {
            vibRateKnob.setCurrentProgress(vibRate);
        }

        if (vibDepthKnob != null) {
            vibDepthKnob.setCurrentProgress(vibDepth);
        }
    }
}