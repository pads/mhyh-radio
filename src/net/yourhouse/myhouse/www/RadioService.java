package net.yourhouse.myhouse.www;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class RadioService extends Service implements MediaPlayer.OnPreparedListener {

    public static final int STOPPED = 0;
    public static final int PLAYING = 1;
    public static final int BUFFERING = 2;

    private MediaPlayer mediaPlayer;
    URLConnection urlConnection;
    private String errors = "";
    private String showName = "No show information available";
    private int state;

    public RadioService() {
        state = STOPPED;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        mediaPlayer.start();
        if(urlConnection != null) {
            showName = "Now playing: " + urlConnection.getHeaderField("icy-name");
        } else {
            showName = "Now playing: MHYH Radio";
        }
        state = PLAYING;
    }

    public String errors() {
        return errors;
    }

    public  String showName() {
        return showName;
    }

    public void start() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            urlConnection = new URL(getString(R.string.radio_url)).openConnection();
            urlConnection.connect();
            showName = "Please wait, buffering...";
            mediaPlayer.setDataSource(getString(R.string.radio_url));
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.prepareAsync();
            state = BUFFERING;
        } catch (IOException e) {
            Log.e(RadioApp.MHYH_RADIO_LOG_TAG, "Caught IOException: " + e.getMessage());
            showName = "No show information available";
            errors = "Error starting stream";
        }
    }

    public void stop() {
        if(mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            state = STOPPED;
            showName = "No show information available";
        }
    }

    public int state() {
        return state;
    }

    private final IRadioService.Stub iBinder = new IRadioService.Stub() {

        public String errors() {
            return RadioService.this.errors();
        }

        public  String showName() {
            return RadioService.this.showName();
        }

        public void start() {
            RadioService.this.start();
        }

        public void stop() {
            RadioService.this.stop();
        }

        public int state() {
            return RadioService.this.state();
        }

    };
}
