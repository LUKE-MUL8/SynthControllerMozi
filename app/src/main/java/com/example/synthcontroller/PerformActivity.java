package com.example.synthcontroller;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.convergencelabstfx.pianoview.PianoTouchListener;
import com.convergencelabstfx.pianoview.PianoView;
import com.rejowan.rotaryknob.RotaryKnob;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class PerformActivity extends AppCompatActivity {

    private PianoView pianoView;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private BluetoothDevice device;
    private BluetoothAdapter bluetoothAdapter;
    private RotaryKnob attackKnob, decayKnob, sustainKnob, releaseKnob, filterKnob, detuneKnob;

    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String BT_DEVICE_NAME = "HC-02";
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perform);

        //midi panic button
        Button panicButton = findViewById(R.id.panicButton);
        panicButton.setOnClickListener(v -> sendCommand("PANIC", 0));

        // Initialize knobs
        RotaryKnob attackKnob = findViewById(R.id.attackKnob);
        RotaryKnob decayKnob = findViewById(R.id.decayKnob);
        RotaryKnob sustainKnob = findViewById(R.id.sustainKnob);
        RotaryKnob releaseKnob = findViewById(R.id.releaseKnob);
        RotaryKnob filterKnob = findViewById(R.id.filterKnob);
        RotaryKnob detuneKnob = findViewById(R.id.detuneKnob);

        // Set initial knob values (optional)
        attackKnob.setMin(0);
        attackKnob.setMax(255);
        attackKnob.setCurrentProgress(0);

        decayKnob.setMin(0);
        decayKnob.setMax(255);
        decayKnob.setCurrentProgress(0);

        sustainKnob.setMin(0);
        sustainKnob.setMax(255);
        sustainKnob.setCurrentProgress(0);

        releaseKnob.setMin(0);
        releaseKnob.setMax(255);
        releaseKnob.setCurrentProgress(0);

        filterKnob.setMin(0);
        filterKnob.setMax(255);
        filterKnob.setCurrentProgress(0);

        detuneKnob.setMin(0);
        detuneKnob.setMax(255);
        detuneKnob.setCurrentProgress(0);


        // Set knob listeners
        // Set listeners for knob progress changes
        attackKnob.setProgressChangeListener(progress -> sendCommand("ATTACK", progress));
        decayKnob.setProgressChangeListener(progress -> sendCommand("DECAY", progress));
        sustainKnob.setProgressChangeListener(progress -> sendCommand("SUSTAIN", progress));
        releaseKnob.setProgressChangeListener(progress -> sendCommand("RELEASE", progress));
        filterKnob.setProgressChangeListener(progress -> sendCommand("FILTER", progress));
        detuneKnob.setProgressChangeListener(progress -> sendCommand("DETUNE", progress));



        // piano
        pianoView = findViewById(R.id.pianoView);
        adjustPianoSizeBasedOnOrientation();

        pianoView.addPianoTouchListener(new PianoTouchListener() {
            @Override
            public void onKeyDown(PianoView piano, int key) {
                sendCommand("DOWN", key);
            }

            @Override
            public void onKeyUp(PianoView piano, int key) {
                sendCommand("UP", key);
            }

            @Override
            public void onKeyClick(PianoView piano, int key) {
                // No action needed for key click
            }
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
                enableBluetooth();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, PERMISSION_REQUEST_CODE);
            } else {
                enableBluetooth();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                enableBluetooth();
            } else {
                Toast.makeText(this, "Permissions are required for Bluetooth functionality", Toast.LENGTH_LONG).show();
                redirectToSettings();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void redirectToSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    private void enableBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        } else {
            findAndConnectDevice();
        }
    }

    private void findAndConnectDevice() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice pairedDevice : pairedDevices) {
            if (BT_DEVICE_NAME.equals(pairedDevice.getName())) {
                device = pairedDevice;
                break;
            }
        }

        if (device == null) {
            Toast.makeText(this, "Device not found. Please pair it first.", Toast.LENGTH_LONG).show();
            return;
        }

        new Thread(() -> {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(BT_UUID);
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                runOnUiThread(() -> Toast.makeText(this, "Connected to " + BT_DEVICE_NAME, Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                Log.e("BluetoothDebug", "Connection failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Failed to connect", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void sendCommand(String action, int value) {
        if (outputStream != null) {
            try {
                String command = action + ":" + value + "\n"; // Format: "ACTION:VALUE"
                outputStream.write(command.getBytes());
                Log.d("BluetoothDebug", "Sent command: " + command); // Log the command
            } catch (IOException e) {
                Toast.makeText(this, "Failed to send command", Toast.LENGTH_SHORT).show();
                Log.e("BluetoothDebug", "Failed to send command", e); // Log the error
            }
        } else {
            Log.e("BluetoothDebug", "OutputStream is null"); // Log if outputStream is null
        }
    }

    private void adjustPianoSizeBasedOnOrientation() {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) pianoView.getLayoutParams();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            params.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
            params.height = ConstraintLayout.LayoutParams.MATCH_PARENT;
        } else {
            params.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
            params.height = 600;  // Adjust as necessary for portrait mode
        }
        pianoView.setLayoutParams(params);
    }
}
