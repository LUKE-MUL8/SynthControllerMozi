package com.example.synthcontroller;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.leff.midi.MidiFile;
import com.leff.midi.event.MidiEvent;
import com.leff.midi.event.NoteOn;
import com.leff.midi.util.MidiEventListener;
import com.leff.midi.util.MidiProcessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MidiFilePlaybackActivity extends AppCompatActivity {
    private static final String TAG = "MidiPlaybackActivity";
    private MidiProcessor midiProcessor;
    private String midiFilePath;

    // ActivityResultLauncher for the file picker
    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        Uri uri = data.getData();
                        Log.d(TAG, "Selected URI: " + uri.toString());  // Log the URI
                        midiFilePath = getFilePathFromUri(uri); // Get the file path from URI
                        if (uri != null) {
                            // Load the selected MIDI file
                            loadMidiFile(uri); // Pass the Uri directly instead of the file path
                        } else {
                            Toast.makeText(this, "Unable to open file", Toast.LENGTH_SHORT).show();
                        }

                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_midi_file_playback);

        Button playButton = findViewById(R.id.playButton);
        Button openFileButton = findViewById(R.id.openFileButton); // Add a new button to open the file picker

        // Open file picker when the "Open File" button is clicked
        openFileButton.setOnClickListener(v -> openFilePicker());

        playButton.setOnClickListener(v -> playMidiFile());
    }

    // Open file picker to select a MIDI file
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/midi");
        filePickerLauncher.launch(intent);
    }

    private String getFilePathFromUri(Uri uri) {
        String filePath = null;

        // Check if it's a document URI (common for external files)
        if (DocumentsContract.isDocumentUri(this, uri)) {
            String docId = DocumentsContract.getDocumentId(uri);
            String[] split = docId.split(":");
            String type = split[0];
            Log.d(TAG, "Document URI detected. Type: " + type);  // Log type

            if ("primary".equalsIgnoreCase(type)) {
                // Handle primary storage files (like those on internal storage)
                filePath = Environment.getExternalStorageDirectory() + "/" + split[1];
                Log.d(TAG, "Primary file path: " + filePath);  // Log the file path
            } else if ("msf".equalsIgnoreCase(type)) {
                // Handle msf (media file type) - specific case for Downloads folder
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(split[1]));
                filePath = getDataColumn(this, contentUri, null, null);
                Log.d(TAG, "MSF file path: " + filePath);  // Log the MSF file path
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // If the URI is a content URI (most file URIs from file picker)
            Log.d(TAG, "Content URI detected: " + uri.toString());  // Log URI

            if (uri.getAuthority().equals("com.android.providers.downloads.documents")) {
                // Handle downloads folder
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(uri.getLastPathSegment()));
                filePath = getDataColumn(this, contentUri, null, null);
            } else {
                filePath = getDataColumn(this, uri, null, null);
            }
        }

        return filePath;
    }


    // Helper method to get the data column (file path) from URI
    private String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        String column = "_data";
        String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file path from URI", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }

    private void loadMidiFile(Uri uri) {
        try {
            // Open an input stream for the URI
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                MidiFile midi = new MidiFile(inputStream);

                // Initialize MIDI Processor
                midiProcessor = new MidiProcessor(midi);
                midiProcessor.registerEventListener(new MidiEventListener() {
                    @Override
                    public void onStart(boolean fromBeginning) {
                        Log.d(TAG, "MIDI Playback started");
                    }

                    @Override
                    public void onEvent(MidiEvent event, long ms) {
                        if (event instanceof NoteOn) {
                            NoteOn note = (NoteOn) event;
                            Log.d(TAG, "Note On: " + note.getNoteValue() + " Velocity: " + note.getVelocity());
                        }
                    }

                    @Override
                    public void onStop(boolean finished) {
                        Log.d(TAG, "MIDI Playback stopped");
                    }
                }, MidiEvent.class);
            } else {
                Toast.makeText(this, "Unable to open file stream", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading MIDI file", e);
            Toast.makeText(this, "Error reading MIDI file", Toast.LENGTH_SHORT).show();
        }
    }



    private void playMidiFile() {
        if (midiProcessor != null) {
            midiProcessor.start();
        }
    }
}
