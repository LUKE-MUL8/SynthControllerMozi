package com.example.synthcontroller;

import java.io.Serializable;

public class SynthPreset implements Serializable {
    private String name;
    private int mainWaveform;
    private int subWaveform;
    private int attack;
    private int decay;
    private int sustain;
    private int release;
    private int filter;
    private int detune;
    private int vibRate;
    private int vibDepth;
    private int octave;

    public SynthPreset() {
        // Default values
        this.mainWaveform = 0;
        this.subWaveform = 1;
        this.attack = 50;
        this.decay = 100;
        this.sustain = 180;
        this.release = 100;
        this.filter = 255;
        this.detune = 0;
        this.vibRate = 0;
        this.vibDepth = 0;
        this.octave = 3;
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getMainWaveform() { return mainWaveform; }
    public void setMainWaveform(int mainWaveform) { this.mainWaveform = mainWaveform; }

    public int getSubWaveform() { return subWaveform; }
    public void setSubWaveform(int subWaveform) { this.subWaveform = subWaveform; }

    public int getAttack() { return attack; }
    public void setAttack(int attack) { this.attack = attack; }

    public int getDecay() { return decay; }
    public void setDecay(int decay) { this.decay = decay; }

    public int getSustain() { return sustain; }
    public void setSustain(int sustain) { this.sustain = sustain; }

    public int getRelease() { return release; }
    public void setRelease(int release) { this.release = release; }

    public int getFilter() { return filter; }
    public void setFilter(int filter) { this.filter = filter; }

    public int getDetune() { return detune; }
    public void setDetune(int detune) { this.detune = detune; }

    public int getVibRate() { return vibRate; }
    public void setVibRate(int vibRate) { this.vibRate = vibRate; }

    public int getVibDepth() { return vibDepth; }
    public void setVibDepth(int vibDepth) { this.vibDepth = vibDepth; }

    public int getOctave() { return octave; }
    public void setOctave(int octave) { this.octave = octave; }
}