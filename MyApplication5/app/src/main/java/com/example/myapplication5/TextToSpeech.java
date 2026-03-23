package com.example.myapplication5;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Detector.Detections;
import com.google.android.gms.vision.Detector.Processor;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TextToSpeech extends AppCompatActivity implements TTS.TTSListener {

    SurfaceView cameraView;
    TextView textView;
    Spinner languageSpinner;
    CameraSource cameraSource;
    final int RequestCameraPermissionID = 1001;
    private TTS tts;
    private boolean textSpoken = false;
    private Locale selectedLocale = Locale.ENGLISH;

    Map<String, Locale> languageMap = new HashMap<String, Locale>() {{
        put("English", Locale.ENGLISH);
        put("Hindi", new Locale("hi", "IN"));
        put("Kannada", new Locale("kn", "IN"));
        put("Tamil", new Locale("ta", "IN"));
        put("Telugu", new Locale("te", "IN"));
        put("Bengali", new Locale("bn", "IN"));
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_to_speech);

        cameraView = findViewById(R.id.surface_view);
        textView = findViewById(R.id.text_view);
        languageSpinner = findViewById(R.id.language_spinner);

        tts = new TTS(this, selectedLocale);
        tts.setTTSListener(this);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(languageMap.keySet()));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);
        languageSpinner.setSelection(0);

        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String lang = (String) parent.getItemAtPosition(position);
                selectedLocale = languageMap.get(lang);

                int result = tts.getTextToSpeech().setLanguage(selectedLocale);
                if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA ||
                        result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(TextToSpeech.this, "TTS doesn't support " + lang + " on this device", Toast.LENGTH_LONG).show();
                } else {
                    tts.setLocale(selectedLocale);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        TextRecognizer textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();
        if (!textRecognizer.isOperational()) {
            Log.w("MainActivity", "Detector dependencies not available.");
        } else {
            cameraSource = new CameraSource.Builder(getApplicationContext(), textRecognizer)
                    .setFacing(CameraSource.CAMERA_FACING_BACK)
                    .setRequestedPreviewSize(1280, 1024)
                    .setRequestedFps(2.0f)
                    .setAutoFocusEnabled(true)
                    .build();

            cameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    try {
                        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(TextToSpeech.this,
                                    new String[]{Manifest.permission.CAMERA},
                                    RequestCameraPermissionID);
                            return;
                        }
                        cameraSource.start(cameraView.getHolder());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    cameraSource.stop();
                }
            });

            cameraView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    textRecognizer.setProcessor(new Detector.Processor<TextBlock>() {
                        @Override
                        public void release() {}

                        @Override
                        public void receiveDetections(Detector.Detections<TextBlock> detections) {
                            final SparseArray<TextBlock> items = detections.getDetectedItems();
                            final StringBuilder stringBuilder = new StringBuilder();

                            if (items.size() != 0) {
                                textView.post(() -> {
                                    for (int i = 0; i < items.size(); i++) {
                                        TextBlock item = items.valueAt(i);
                                        stringBuilder.append(item.getValue()).append("\n");
                                    }

                                    textView.setText(stringBuilder.toString());

                                    if (!textSpoken) {
                                        tts.speak(stringBuilder.toString());
                                        textSpoken = true;
                                    }
                                });
                            }
                        }
                    });
                }
            });
        }

        // Back gesture fix for AndroidX
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                tts.shutdown();
                finish();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);  // ✅ FIXED

        if (requestCode == RequestCameraPermissionID) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                try {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        cameraSource.start(cameraView.getHolder());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tts.shutdown();
    }

    @Override
    public void onTextSpoken() {
        textSpoken = false;
    }
}
