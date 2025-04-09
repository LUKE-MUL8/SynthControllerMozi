package com.example.synthcontroller;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class PresetFragment extends Fragment {
    private static final String TAG = "PresetFragment";

    private PresetManager presetManager;
    private Spinner presetSpinner;
    private Button savePresetButton;
    private Button loadPresetButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_preset, container, false);

        presetManager = new PresetManager(requireContext());
        presetSpinner = view.findViewById(R.id.presetSpinner);
        savePresetButton = view.findViewById(R.id.savePresetButton);
        loadPresetButton = view.findViewById(R.id.loadPresetButton);

        updatePresetSpinner();

        savePresetButton.setOnClickListener(v -> showSavePresetDialog());
        loadPresetButton.setOnClickListener(v -> loadSelectedPreset());

        return view;
    }

    private void updatePresetSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item,
                presetManager.getPresetNames());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);
    }

    private void showSavePresetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Save Preset");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_save_preset, null);
        EditText input = dialogView.findViewById(R.id.presetNameInput);

        builder.setView(dialogView);
        builder.setPositiveButton("Save", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError("Please enter a name");
            } else {
                PerformActivity activity = (PerformActivity) requireActivity();
                activity.saveCurrentPreset(name);
                dialog.dismiss();
                updatePresetSpinner();
            }
        });
    }

    private void loadSelectedPreset() {
        if (presetSpinner.getSelectedItem() == null) {
            Toast.makeText(requireContext(), "No preset selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String presetName = presetSpinner.getSelectedItem().toString();
        PerformActivity activity = (PerformActivity) requireActivity();
        activity.loadPresetByName(presetName);
    }

    public void updatePresetList() {
        updatePresetSpinner();
    }
}