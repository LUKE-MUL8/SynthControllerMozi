package com.example.synthcontroller;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button navigateButton = findViewById(R.id.navigate_button);
        Button midiButton = findViewById(R.id.midi_button);
        Button killBluetoothButton = findViewById(R.id.kill_bluetooth_button);

        navigateButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PerformActivity.class);
            startActivity(intent);
        });

        midiButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MidiFilePlaybackActivity.class);
            startActivity(intent);
        });

        if (killBluetoothButton != null) {
            killBluetoothButton.setOnClickListener(v -> {
                BluetoothManager.getInstance().disconnect();
                Toast.makeText(this, "Bluetooth disconnected", Toast.LENGTH_SHORT).show();
            });
        }

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