package com.catalyst.voicetosigndemo;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Layout;
import android.view.View;
import android.widget.SeekBar;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.slider.Slider;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.unity3d.player.UnityPlayer;
import com.unity3d.player.UnityPlayerActivity;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.speech.RecognitionListener;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends UnityPlayerActivity {
    SeekBar zoomSlider;
    private SpeechRecognizer sr;
    private TextView mText;
    private static final String TAG = "MyStt3Activity";
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference mDatabase;
    private float zoomLevel = 0;
    private boolean toggleStatus = false;
    FirebaseFirestore db;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        zoomSlider = (SeekBar) findViewById(R.id.seekBar);
        mText = (TextView) findViewById(R.id.textView);
        zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                                                  @Override
                                                  public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                                                      SetZoomLevel((double) i / 100);
                                                  }

                                                  @Override
                                                  public void onStartTrackingTouch(SeekBar seekBar) {

                                                  }

                                                  @Override
                                                  public void onStopTrackingTouch(SeekBar seekBar) {
                                                    SaveUserValues();
                                                  }
                                              }

        );

        ConstraintLayout myLayout = (ConstraintLayout) findViewById(R.id.unityLayout);
        myLayout.addView(mUnityPlayer.getView());

        requestRecordAudioPermission();
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new listener());
        startRecognizing();

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        db = FirebaseFirestore.getInstance();

        if(currentUser == null)
        {
            mAuth.signInAnonymously()
                    .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                // Sign in success, update UI with the signed-in user's information
                                Log.d(TAG, "signInAnonymously:success");
                                FirebaseUser user = mAuth.getCurrentUser();
                                Toast.makeText(MainActivity.this, "Baglanti basarili.",
                                        Toast.LENGTH_SHORT).show();
                                currentUser = user;

                            } else {
                                // If sign in fails, display a message to the user.
                                Log.w(TAG, "signInAnonymously:failure", task.getException());
                                Toast.makeText(MainActivity.this, "Baglanti basarisiz.",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
        else
        {
            firstSettings();
        }
    }

    public void SetZoomLevel(double zoom)
    {
        mUnityPlayer.UnitySendMessage("Character", "SetCameraZoom", Double.toString(zoom));
        zoomLevel = (float)zoom;
    }

    public void firstSettings()
    {
        DocumentReference docRef = db.collection("UserSettings").document(currentUser.getUid());
        docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();

                    SetZoomLevel((double)document.get("zoomLevel"));
                    SetNightMode(document.getBoolean("isNightMode"));

                } else {
                    Log.d(TAG, "get failed with ", task.getException());
                }
            }
        });
    }

    public void clickStart(View view) {



    }

    public void nightModeToggle(View view)
    {
        ToggleButton simpleToggleButton = (ToggleButton) view;
        SetNightMode(simpleToggleButton.isChecked());
    }

    public void SetNightMode(boolean status)
    {
        toggleStatus = status;
        if(status)
        {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            mUnityPlayer.UnitySendMessage("Character", "SetBackgroundColor", "#AAAAAAFF");
            mUnityPlayer.UnitySendMessage("Character", "SetMainColor", "#354563FF");
            mUnityPlayer.UnitySendMessage("Character", "SetSecondsColor", "#354563FF");
        }
        else
        {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            mUnityPlayer.UnitySendMessage("Character", "SetBackgroundColor", "#FFFFFFFF");
            mUnityPlayer.UnitySendMessage("Character", "SetMainColor", "#D54D43FF");
            mUnityPlayer.UnitySendMessage("Character", "SetSecondsColor", "#C3928DFF");
        }
        SaveUserValues();
    }

    private void SaveUserValues()
    {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", currentUser.getUid());
        data.put("zoomLevel", zoomLevel);
        data.put("isNightMode",toggleStatus);

        UserValues userValues = new UserValues(toggleStatus, currentUser.getUid(), zoomLevel);
        db.collection("UserSettings").document(currentUser.getUid())
                .set(data, SetOptions.merge());
    }

    private void startRecognizing()
    {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,"voice.recognition.test");

        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);
        sr.startListening(intent);
        Log.i("111111","11111111");
    }

    private void requestRecordAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String requiredPermission = Manifest.permission.RECORD_AUDIO;

            // If the user previously denied this permission then show a message explaining why
            // this permission is needed
            if (checkCallingOrSelfPermission(requiredPermission) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{requiredPermission}, 101);
            }
        }
    }

    class listener implements RecognitionListener
    {
        Random random = new Random();
        public void onReadyForSpeech(Bundle params)
        {
            Log.d(TAG, "onReadyForSpeech");
        }
        public void onBeginningOfSpeech()
        {
            mUnityPlayer.UnitySendMessage("Character", "SetCharacterStatus", Integer.toString(random.nextInt(2)));
            Log.d(TAG, "onBeginningOfSpeech");
        }
        public void onRmsChanged(float rmsdB)
        {
            Log.d(TAG, "onRmsChanged");
        }
        public void onBufferReceived(byte[] buffer)
        {
            Log.d(TAG, "onBufferReceived");
        }
        public void onEndOfSpeech()
        {
            mUnityPlayer.UnitySendMessage("Character", "SetCharacterStatus", Integer.toString(random.nextInt(3)+2));
            Log.d(TAG, "onEndofSpeech");
        }
        public void onError(int error)
        {
            mUnityPlayer.UnitySendMessage("Character", "SetCharacterStatus", Integer.toString(random.nextInt(3)+2));
            Log.d(TAG,  "error " +  error);
            startRecognizing();
        }
        public void onResults(Bundle results)
        {
            mUnityPlayer.UnitySendMessage("Character", "SetCharacterStatus", Integer.toString(random.nextInt(3)+2));
            String str = new String();
            String res = new String();
            Log.d(TAG, "onResults " + results);
            ArrayList data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            for (int i = 0; i < data.size(); i++)
            {
                Log.d(TAG, "result " + data.get(i));
                str += data.get(i);

            }
            res = data.get(0).toString();
            mText.setText(res);
            startRecognizing();
        }
        public void onPartialResults(Bundle partialResults)
        {

            String res = new String();
            Log.d(TAG, "onPartialResults " + partialResults);
            ArrayList data = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            res = data.get(0).toString();
            mText.setText(res);
            startRecognizing();
        }
        public void onEvent(int eventType, Bundle params)
        {
            Log.d(TAG, "onEvent " + eventType);
        }
    }


    //@IgnoreExtraProperties
    public class UserValues {
        public boolean isNightMode;
        public String userId;
        public float zoomLevel;

        @Keep
        public UserValues()
        {

        }

        public UserValues(boolean isNightMode, String userId, float zoomLevel) {
            this.isNightMode = isNightMode;
            this.userId = userId;
            this.zoomLevel = zoomLevel;
        }

    }

}











