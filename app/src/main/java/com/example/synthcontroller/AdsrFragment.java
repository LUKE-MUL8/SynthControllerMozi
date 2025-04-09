package com.example.synthcontroller;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import com.rejowan.rotaryknob.RotaryKnob;

public class AdsrFragment extends Fragment {
    private RotaryKnob attackKnob, decayKnob, sustainKnob, releaseKnob;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_tab_adsr, container, false);

        attackKnob = view.findViewById(R.id.attackKnob);
        decayKnob = view.findViewById(R.id.decayKnob);
        sustainKnob = view.findViewById(R.id.sustainKnob);
        releaseKnob = view.findViewById(R.id.releaseKnob);

        // Configure attack knob
        if (attackKnob != null) {
            attackKnob.setMin(0);
            attackKnob.setMax(255);
            attackKnob.setCurrentProgress(50);
            attackKnob.setProgressChangeListener(progress ->
                    ((PerformActivity)getActivity()).sendCommand("ATTACK:", progress));
        }

        // Configure decay knob
        if (decayKnob != null) {
            decayKnob.setMin(0);
            decayKnob.setMax(255);
            decayKnob.setCurrentProgress(100);
            decayKnob.setProgressChangeListener(progress ->
                    ((PerformActivity)getActivity()).sendCommand("DECAY:", progress));
        }

        // Configure sustain knob
        if (sustainKnob != null) {
            sustainKnob.setMin(0);
            sustainKnob.setMax(255);
            sustainKnob.setCurrentProgress(180);
            sustainKnob.setProgressChangeListener(progress ->
                    ((PerformActivity)getActivity()).sendCommand("SUSTAIN:", progress));
        }

        // Configure release knob
        if (releaseKnob != null) {
            releaseKnob.setMin(0);
            releaseKnob.setMax(255);
            releaseKnob.setCurrentProgress(100);
            releaseKnob.setProgressChangeListener(progress ->
                    ((PerformActivity)getActivity()).sendCommand("RELEASE:", progress));
        }

        return view;
    }

    // Add getter methods for preset functionality
    public int getAttackValue() {
        return attackKnob != null ? attackKnob.getCurrentProgress() : 50;
    }

    public int getDecayValue() {
        return decayKnob != null ? decayKnob.getCurrentProgress() : 100;
    }

    public int getSustainValue() {
        return sustainKnob != null ? sustainKnob.getCurrentProgress() : 180;
    }

    public int getReleaseValue() {
        return releaseKnob != null ? releaseKnob.getCurrentProgress() : 100;
    }

    // Method to update UI when loading presets
    public void updateAdsrValues(int attack, int decay, int sustain, int release) {
        if (attackKnob != null) {
            attackKnob.setCurrentProgress(attack);
        }

        if (decayKnob != null) {
            decayKnob.setCurrentProgress(decay);
        }

        if (sustainKnob != null) {
            sustainKnob.setCurrentProgress(sustain);
        }

        if (releaseKnob != null) {
            releaseKnob.setCurrentProgress(release);
        }
    }
}