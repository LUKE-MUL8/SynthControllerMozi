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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.convergencelabstfx.pianoview.PianoTouchListener;
import com.convergencelabstfx.pianoview.PianoView;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity2 extends AppCompatActivity {

    private PianoView pianoView;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private BluetoothDevice device;
    private BluetoothAdapter bluetoothAdapter;

    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String BT_DEVICE_NAME = "HC-02";
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

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
//                sendCommand("CLICK", key);
            }
        });

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12+ permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.d("PermissionDebug", "Bluetooth permissions not granted. Requesting for Android 12+...");
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, PERMISSION_REQUEST_CODE);
            } else {
                enableBluetooth();
            }
        } else {
            // Android 10 permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.d("PermissionDebug", "Location permission not granted. Requesting for Android 10...");
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, PERMISSION_REQUEST_CODE);
            } else {
                enableBluetooth();
            }
        }
    }


    private void handleBluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

            Log.d("PermissionDebug", "Bluetooth permissions not granted. Requesting...");
            // Request Bluetooth permissions
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            }, PERMISSION_REQUEST_CODE);
        } else {
            enableBluetooth();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
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




    private void enableBluetooth() {
        Log.d("BluetoothDebug", "Checking if Bluetooth is enabled");
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Log.d("BluetoothDebug", "This device doesn't support Bluetooth.");
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.d("BluetoothDebug", "Bluetooth is disabled, requesting to enable...");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        } else {
            Log.d("BluetoothDebug", "Bluetooth is already enabled");
            findAndConnectDevice();
        }
    }

    private void findAndConnectDevice() {
        Log.d("BluetoothDebug", "Searching for device: " + BT_DEVICE_NAME);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED ||
                android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {

            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice pairedDevice : pairedDevices) {
                if (BT_DEVICE_NAME.equals(pairedDevice.getName())) {
                    device = pairedDevice;
                    break;
                }
            }

            if (device == null) {
                Log.d("BluetoothDebug", "Device not found in paired devices");
                Toast.makeText(this, "Device not found. Please pair it first.", Toast.LENGTH_LONG).show();
                return;
            }

            new Thread(() -> {
                try {
                    Log.d("BluetoothDebug", "Creating socket...");
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(BT_UUID);
                    Log.d("BluetoothDebug", "Device: " + device.getName() + ", UUID: " + BT_UUID);

                    bluetoothSocket.connect();
                    Log.d("BluetoothDebug", "Connected successfully!");

                    outputStream = bluetoothSocket.getOutputStream();
                    runOnUiThread(() -> Toast.makeText(this, "Connected to " + BT_DEVICE_NAME, Toast.LENGTH_SHORT).show());
                } catch (IOException e) {
                    Log.e("BluetoothDebug", "Connection failed, trying fallback...", e);
                    try {
                        bluetoothSocket = (BluetoothSocket) device.getClass()
                                .getMethod("createRfcommSocket", int.class)
                                .invoke(device, 1);
                        bluetoothSocket.connect();
                        outputStream = bluetoothSocket.getOutputStream();
                        runOnUiThread(() -> Toast.makeText(this, "Connected via fallback to " + BT_DEVICE_NAME, Toast.LENGTH_SHORT).show());
                    } catch (Exception fallbackException) {
                        Log.e("BluetoothDebug", "Fallback connection failed", fallbackException);
                        runOnUiThread(() -> Toast.makeText(this, "Failed to connect", Toast.LENGTH_LONG).show());
                    }
                }
            }).start();

        } else {
            Log.d("BluetoothDebug", "Missing required permissions");
            Toast.makeText(this, "Permissions are not granted", Toast.LENGTH_SHORT).show();
        }
    }


    private void sendCommand(String action, int key) {
        if (outputStream != null) {
            try {
                String command = action + ":" + key + "\n";
                outputStream.write(command.getBytes());
                Log.d("BluetoothSend", "Command sent: " + command);
            } catch (IOException e) {
                Log.e("BluetoothSend", "Failed to send command", e);
                Toast.makeText(this, "Failed to send command", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.w("BluetoothSend", "OutputStream is null");
        }
    }


    private void adjustPianoSizeBasedOnOrientation() {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) pianoView.getLayoutParams();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            params.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
            params.height = ConstraintLayout.LayoutParams.MATCH_PARENT;
        } else {
            params.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
            params.height = getResources().getDisplayMetrics().heightPixels / 2;
        }
        pianoView.setLayoutParams(params);
        pianoView.requestLayout();
    }

    private void redirectToSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (outputStream != null) {
                outputStream.close();
            }
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}