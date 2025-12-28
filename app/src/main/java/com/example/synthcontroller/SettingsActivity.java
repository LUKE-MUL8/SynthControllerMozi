package com.example.synthcontroller;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_settings);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_perform) {
                startActivity(new Intent(this, PerformActivity.class));
                return true;
            } else if (itemId == R.id.nav_midi) {
                startActivity(new Intent(this, MidiFilePlaybackActivity.class));
                return true;
            } else if (itemId == R.id.nav_settings) {
                return true;
            }
            return false;
        });
    }
}
