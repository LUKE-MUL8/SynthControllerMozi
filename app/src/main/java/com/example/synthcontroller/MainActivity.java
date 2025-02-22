package com.example.synthcontroller;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find the button by its ID
        Button navigateButton = findViewById(R.id.navigate_button);

        Button midiButton = findViewById(R.id.midi_button);


        // Set OnClickListener
        navigateButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PerformActivity.class);
            startActivity(intent);
        });

        // Set OnClickListener
        midiButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MidiFilePlaybackActivity.class);
            startActivity(intent);
        });

    }


}
