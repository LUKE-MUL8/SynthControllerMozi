package com.example.synthcontroller;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PresetManager {
    private static final String TAG = "PresetManager";
    private static final String PREF_NAME = "synth_presets";
    private static final String PRESETS_KEY = "all_presets";

    private final Context context;
    private final Gson gson;
    private Map<String, SynthPreset> presets;

    public PresetManager(Context context) {
        this.context = context;
        this.gson = new Gson();
        this.presets = new HashMap<>();
        loadPresets();
    }

    private void loadPresets() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(PRESETS_KEY, null);
            Log.d(TAG, "Loading presets, JSON: " + (json != null ? "found" : "not found"));

            if (json != null) {
                Type type = new TypeToken<HashMap<String, SynthPreset>>(){}.getType();
                Map<String, SynthPreset> loadedPresets = gson.fromJson(json, type);
                if (loadedPresets != null) {
                    presets = loadedPresets;
                    Log.d(TAG, "Loaded " + presets.size() + " presets");
                } else {
                    createDefaultPreset();
                }
            } else {
                createDefaultPreset();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading presets", e);
            createDefaultPreset();
        }
    }

    private void createDefaultPreset() {
        presets = new HashMap<>();
        SynthPreset defaultPreset = new SynthPreset();
        defaultPreset.setName("Default");
        presets.put("Default", defaultPreset);
        Log.d(TAG, "Created default preset");
        savePresets();
    }

    private void savePresets() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String json = gson.toJson(presets);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(PRESETS_KEY, json);
            editor.apply();
            Log.d(TAG, "Saved " + presets.size() + " presets");
        } catch (Exception e) {
            Log.e(TAG, "Error saving presets", e);
        }
    }

    public void savePreset(SynthPreset preset) {
        if (preset == null || preset.getName() == null || preset.getName().isEmpty()) {
            Log.e(TAG, "Cannot save preset: invalid preset or empty name");
            return;
        }

        Log.d(TAG, "Saving preset: " + preset.getName());
        presets.put(preset.getName(), preset);
        savePresets();
    }

    public SynthPreset getPreset(String name) {
        return presets.get(name);
    }

    public List<String> getPresetNames() {
        return new ArrayList<>(presets.keySet());
    }
}