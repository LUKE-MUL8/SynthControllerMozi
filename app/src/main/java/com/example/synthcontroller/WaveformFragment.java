package com.example.synthcontroller;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

public class WaveformFragment extends Fragment {
    private Spinner mainWaveSpinner, subWaveSpinner;
    private final String[] waveforms = {"Saw", "Square", "Sine", "Triangle"};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_tab_waveform, container, false);

        mainWaveSpinner = view.findViewById(R.id.mainWaveSpinner);
        subWaveSpinner = view.findViewById(R.id.subWaveSpinner);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getActivity(), R.layout.spinner_item_white_text, waveforms);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mainWaveSpinner.setAdapter(adapter);
        subWaveSpinner.setAdapter(adapter);

        // Set default values
        mainWaveSpinner.setSelection(0);  // Saw
        subWaveSpinner.setSelection(1);   // Square

        // Setup listeners
        mainWaveSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ((PerformActivity)getActivity()).sendCommand("MAIN_WAVE:", position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        subWaveSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ((PerformActivity)getActivity()).sendCommand("SUB_WAVE:", position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        return view;
    }

    // Add these getter methods for preset functionality
    public int getMainWaveformPosition() {
        return mainWaveSpinner != null ? mainWaveSpinner.getSelectedItemPosition() : 0;
    }

    public int getSubWaveformPosition() {
        return subWaveSpinner != null ? subWaveSpinner.getSelectedItemPosition() : 1;
    }

    // Method to update UI when loading presets
    public void updateWaveformSelections(int mainPosition, int subPosition) {
        if (mainWaveSpinner != null && mainPosition >= 0 && mainPosition < waveforms.length) {
            mainWaveSpinner.setSelection(mainPosition);
        }

        if (subWaveSpinner != null && subPosition >= 0 && subPosition < waveforms.length) {
            subWaveSpinner.setSelection(subPosition);
        }
    }
}