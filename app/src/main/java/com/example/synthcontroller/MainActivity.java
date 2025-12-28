package com.example.synthcontroller;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Correct the type from Button to MaterialCardView
        MaterialCardView performCard = findViewById(R.id.row_perform);
        MaterialCardView midiCard = findViewById(R.id.row_midi);
        MaterialCardView killBluetoothCard = findViewById(R.id.row_kill_bt);
        MaterialCardView settingsCard = findViewById(R.id.row_settings);


        // Find the views inside the PERFORM card and set them
        ImageView performIcon = performCard.findViewById(R.id.row_icon);
        TextView performText = performCard.findViewById(R.id.row_text);
        performIcon.setImageResource(R.drawable.ic_graphic_eq);
        performIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary_400));
        performText.setText("Perform & change sound");

        // Find the views inside the MIDI card and set them
        ImageView midiIcon = midiCard.findViewById(R.id.row_icon);
        TextView midiText = midiCard.findViewById(R.id.row_text);
        midiIcon.setImageResource(R.drawable.baseline_queue_music_24);
        midiIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary_400));
        midiText.setText("Play MIDI");

        // Find the views inside the KILL BLUETOOTH card and set them
        ImageView killBtIcon = killBluetoothCard.findViewById(R.id.row_icon);
        TextView killBtText = killBluetoothCard.findViewById(R.id.row_text);
        killBtIcon.setImageResource(R.drawable.baseline_bluetooth_disabled_24);
        killBtIcon.setColorFilter(ContextCompat.getColor(this, R.color.red_400));
        killBtText.setText("Kill Bluetooth");

        // Find the views inside the SETTINGS card and set them
        ImageView settingsIcon = settingsCard.findViewById(R.id.row_icon);
        TextView settingsText = settingsCard.findViewById(R.id.row_text);
        settingsIcon.setImageResource(R.drawable.baseline_settings_24);
        settingsIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary_400));
        settingsText.setText("Settings and info");

        performCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PerformActivity.class);
            startActivity(intent);
        });

        midiCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MidiFilePlaybackActivity.class);
            startActivity(intent);
        });

        if (killBluetoothCard != null) {
            killBluetoothCard.setOnClickListener(v -> {
                BluetoothManager.getInstance().disconnect();
                Toast.makeText(this, "Bluetooth disconnected", Toast.LENGTH_SHORT).show();
            });
        }

        // Add a click listener for the settings card
        settingsCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, PERMISSION_REQUEST_CODE);
            } else {
                connectBluetooth();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, PERMISSION_REQUEST_CODE);
            } else {
                connectBluetooth();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                connectBluetooth();
            } else {
                Toast.makeText(this, "Permissions required for Bluetooth", Toast.LENGTH_LONG).show();
                redirectToSettings();
            }
        }
    }

    private void redirectToSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    private void connectBluetooth() {
        new Thread(() -> {
            if (BluetoothManager.getInstance().connect()) {
                runOnUiThread(() -> Toast.makeText(this, "Connected to ESP32Synth", Toast.LENGTH_SHORT).show());
            } else {
                runOnUiThread(() -> Toast.makeText(this, "Failed to connect to ESP32Synth", Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
