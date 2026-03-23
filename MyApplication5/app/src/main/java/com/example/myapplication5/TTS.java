package com.example.myapplication5;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

public class TTS extends UtteranceProgressListener implements TextToSpeech.OnInitListener {

    private final String TAG = TTS.class.getSimpleName();
    private TextToSpeech textToSpeech;
    private Locale locale;
    private boolean textSpoken;
    private TTSListener ttsListener;

    public TTS(Context context, Locale locale) {
        this.locale = locale;
        textToSpeech = new TextToSpeech(context, this);
        textToSpeech.setOnUtteranceProgressListener(this);
    }

    public void setTTSListener(TTSListener listener) {
        this.ttsListener = listener;
    }

    public TextToSpeech getTextToSpeech() {
        return textToSpeech;
    }

    public void speak(String text) {
        if (textToSpeech != null) {
            String utteranceID = "myUtteranceID";
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceID);
            textSpoken = true;
        }
    }

    public void stop() {
        textToSpeech.stop();
    }

    public void shutdown() {
        textToSpeech.shutdown();
    }

    public boolean isSpeaking() {
        return textToSpeech.isSpeaking();
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
        int result = textToSpeech.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Language not supported: " + locale.toString());
        } else {
            Log.d(TAG, "Language set to: " + locale.toString());
        }
    }

    @Override
    public void onStart(String utteranceId) {
        Log.d(TAG, "onStart: " + utteranceId);
    }

    @Override
    public void onDone(String utteranceId) {
        Log.d(TAG, "onDone: " + utteranceId);
        if (utteranceId.equals("myUtteranceID")) {
            textSpoken = false;
            if (ttsListener != null) {
                ttsListener.onTextSpoken();
            }
        }
    }

    @Override
    public void onError(String utteranceId) {
        Log.d(TAG, "onError: " + utteranceId);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.ERROR) {
            textToSpeech.setLanguage(locale);
        }
    }

    public interface TTSListener {
        void onTextSpoken();
    }
}
