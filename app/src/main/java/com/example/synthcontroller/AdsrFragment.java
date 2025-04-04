package com.example.synthcontroller;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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

        // Set default values
        attackKnob.setCurrentProgress(50);
        decayKnob.setCurrentProgress(100);
        sustainKnob.setCurrentProgress(180);
        releaseKnob.setCurrentProgress(100);

        // Setup listeners
        setupKnob(attackKnob, "ATTACK:");
        setupKnob(decayKnob, "DECAY:");
        setupKnob(sustainKnob, "SUSTAIN:");
        setupKnob(releaseKnob, "RELEASE:");

        return view;
    }

    private void setupKnob(RotaryKnob knob, String command) {
        knob.setMin(0);
        knob.setMax(255);
        knob.setProgressChangeListener(progress ->
                ((PerformActivity)getActivity()).sendCommand(command, progress));
    }
}