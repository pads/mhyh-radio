package net.yourhouse.myhouse.www;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;

public class RadioService extends Service implements MediaPlayer.OnPreparedListener {

    public static final int STOPPED = 0;
    public static final int PLAYING = 1;
    public static final int BUFFERING = 2;

    private MediaPlayer mediaPlayer;
    private String errors = "";
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
        state = PLAYING;
    }

    public String errors() {
        return errors;
    }

    public  String showName() {
        return "MHYH Radio";
    }

    public void start() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(getString(R.string.radio_url));
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.prepareAsync();
            state = BUFFERING;
        } catch (IOException e) {
            Log.e(RadioApp.MHYH_RADIO_LOG_TAG, "Caught IOException: " + e.getMessage());
            errors = "Error starting stream";
        }
    }

    public void stop() {
        if(mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            state = STOPPED;
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
