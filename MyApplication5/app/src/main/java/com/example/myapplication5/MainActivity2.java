package com.example.myapplication5;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class MainActivity2 extends AppCompatActivity {

    RelativeLayout layout;
    private TTS tts;
    private TextView t1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        layout = findViewById(R.id.relativeLayout);
        tts = new TTS(this, Locale.ENGLISH);

        Vibrator vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        t1 = findViewById(R.id.textview11);

        // Set a timer to speak the welcome message 2 seconds after the activity starts
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                tts.speak("Welcome to DRISHTI app,  Swipe right to detect object , Swipe Left to detect Text , Swipe Up to open Maps" );
            }
        }, 2000); // 2000 milliseconds = 2 seconds

        t1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tts.speak("Welcome to DRISHTI app, Swipe right to detect object , Swipe Left to detect Text, Swipe Up to open Maps");
            }
        });

        layout.setOnTouchListener(new OnSwipeTouchListener(MainActivity2.this) {
            public void onClick() {
                vibe.vibrate(200);

                tts.speak("Swipe right to detect object");
            }



            @Override
            public void onSwipeRight() {
                super.onSwipeRight();
                tts.speak("Put the camera in front of the object");
                vibe.vibrate(200);

                startActivity(new Intent(MainActivity2.this, MainActivity.class));
                Toast.makeText(MainActivity2.this, "Swipe Right gesture detected", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSwipeLeft() {
                super.onSwipeLeft();
                tts.speak("Do Single tap to open the camera and put on the text to read and select the respective language from spinner");
                vibe.vibrate(200);
                startActivity(new Intent(MainActivity2.this, TextToSpeech.class));
                Toast.makeText(MainActivity2.this, "Swipe Left gesture detected", Toast.LENGTH_SHORT).show();
            }




        });
    }

}
