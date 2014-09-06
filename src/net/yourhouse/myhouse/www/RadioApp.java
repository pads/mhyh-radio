/*
 * $Author: essexpads@googlemail.com $
 * $URL: https://mhyh-radio.googlecode.com/svn/trunk/src/net/yourhouse/myhouse/www/RadioApp.java $
 * $Date: 2013-08-05 12:17:38 +0100 (Mon, 05 Aug 2013) $
 * $Rev: 23 $
 */
package net.yourhouse.myhouse.www;

import com.giantrabbit.nagare.INagareService;
import com.giantrabbit.nagare.NagareService;
import com.giantrabbit.nagare.PlayListFactory;
import com.giantrabbit.nagare.PlayListFile;

import android.app.TabActivity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.SlidingDrawer.OnDrawerCloseListener;
import android.widget.SlidingDrawer.OnDrawerOpenListener;

/**
 * Activity that forms the main application.
 *
 * @author Ben
 */
public class RadioApp extends TabActivity implements OnClickListener, OnSeekBarChangeListener,
        OnDrawerOpenListener, OnDrawerCloseListener {

    public static final String MHYH_RADIO_LOG_TAG = "MHYHRadio";

    private static final int MHYH_ID = 1;

    private static int maxVolume = 0;
    private static int currentVolume = 0;

    private String ns = Context.NOTIFICATION_SERVICE;
    private String as = Context.AUDIO_SERVICE;
    private String ps = Context.TELEPHONY_SERVICE;
    private String ws = Context.WINDOW_SERVICE;

    private NotificationManager notificationManager;
    private AudioManager audioManager;
    private TelephonyManager telephonyManager;
    private WindowManager windowManager;

    private INagareService nagareService = null;

    private Notification notification;

    private Display display;
    private Resources res;
    private TabHost tabHost;
    private ImageButton playButton;
    private TextView outputView;
    private WebView shoutBoxWebView;
    private WebView featuredWebView;
    private WebView scheduleWebView;
    private WebView eventsWebView;
    private SeekBar volumeSeekBar;
    private ImageView imageView;
    private ImageView volumeImageView;

    private String nowPlaying = "";
    private String previousPlaying = "";
    private boolean inCall = false;
    private boolean wasPlaying = false;
    private boolean inBackground = false;

    private final Handler handler = new Handler();

    @SuppressWarnings("unused")
    private SharedPreferences preferences;

    /**
     * ******************************************************************************************
     * **									Nagare Serivce									   ***
     * *******************************************************************************************
     */

    private final Runnable runRefresh = new Runnable() {
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    public void refresh() {
        if (nagareService == null) {
            return;
        }
        try {
            int state = nagareService.state();
            if (state == NagareService.STOPPED) {
                playButton.setImageResource(android.R.drawable.ic_media_play);
                previousPlaying = nowPlaying;
                nowPlaying = "No show information available";
                outputView.setText(nowPlaying);
                if (!nagareService.errors().equals("")) {
                    String errors = nagareService.errors();
                    handleNagareErrors(errors);
                }
            } else {
                playButton.setImageResource(android.R.drawable.ic_media_pause);
                if (nagareService.errors().equals("")) {
                    previousPlaying = nowPlaying;
                    nowPlaying = "Now playing: " + nagareService.show_name();
                    outputView.setText(nowPlaying);
                } else {
                    previousPlaying = nowPlaying;
                    nowPlaying = "No show information available";
                    outputView.setText(nowPlaying);
                    String errors = nagareService.errors();
                    handleNagareErrors(errors);
                    nagareService.stop();
                }
            }
            if (notification != null && !nowPlaying.equals(previousPlaying) && inBackground) {
                Intent notificationIntent = new Intent(this, RadioApp.class);
                PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
                notification.setLatestEventInfo(getApplicationContext(), "MHYH Radio", nowPlaying, contentIntent);
                notificationManager.notify(MHYH_ID, notification);
            }
        } catch (RemoteException e) {
            Log.e(MHYH_RADIO_LOG_TAG, "Caught RemoteException: " + e.getMessage());
        }
    }

    private ServiceConnection nagareServiceConn = new ServiceConnection() {

        public void onServiceConnected(ComponentName classname, IBinder service) {
            nagareService = INagareService.Stub.asInterface(service);
            Log.d(MHYH_RADIO_LOG_TAG, "Nagare service connected");
        }

        public void onServiceDisconnected(ComponentName name) {
            nagareService = null;
            Log.d(MHYH_RADIO_LOG_TAG, "Nagare service disconnected");
        }

    };

    private void handleNagareErrors(String errors) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (errors.startsWith("java.io.FileNotFoundException")) {
            builder.setMessage("A valid SD card is required for broadcasting");
        } else if (errors.startsWith("java.lang.NumberFormatException")) {
            builder.setMessage("No stream available, please try again later");
        } else if (errors.equals("inCall")) {
            builder.setMessage("Cannot play audio while in a call");
        } else {
            builder.setMessage("An unknown error occured");
            Log.w(MHYH_RADIO_LOG_TAG, "Found un-handled error(s): " + errors);
        }
        builder.setCancelable(false);
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * ******************************************************************************************
     * **										APP											   ***
     * *******************************************************************************************
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        windowManager = (WindowManager) getSystemService(ws);
        notificationManager = (NotificationManager) getSystemService(ns);
        audioManager = (AudioManager) getSystemService(as);
        telephonyManager = (TelephonyManager) getSystemService(ps);
        telephonyManager.listen(new RadioPhoneStateListener(), PhoneStateListener.LISTEN_CALL_STATE);
        //UI
        display = windowManager.getDefaultDisplay();
        display.getOrientation();
        res = getResources();
        setContentView(R.layout.main);
        playButton = (ImageButton) findViewById(R.id.PlayButton);
        outputView = (TextView) findViewById(R.id.OutputView);
        volumeSeekBar = (SeekBar) findViewById(R.id.VolumeSeekBar);
        volumeImageView = (ImageView) findViewById(R.id.VolumeImageView);

        featuredWebView = (WebView) findViewById(R.id.FeaturedWebview);
        featuredWebView.getSettings().setJavaScriptEnabled(true);
        featuredWebView.setWebViewClient(new RadioWebViewClient());
        featuredWebView.loadUrl(getString(R.string.web_url) + getString(R.string.featured_url));

        shoutBoxWebView = (WebView) findViewById(R.id.ShoutBoxWebview);
        shoutBoxWebView.getSettings().setJavaScriptEnabled(true);
        shoutBoxWebView.setWebViewClient(new RadioWebViewClient());
        shoutBoxWebView.loadUrl(getString(R.string.web_url) + getString(R.string.shout_url));

        scheduleWebView = (WebView) findViewById(R.id.ScheduleWebview);
        scheduleWebView.getSettings().setJavaScriptEnabled(true);
        scheduleWebView.setWebViewClient(new RadioWebViewClient());
        scheduleWebView.loadUrl(getString(R.string.web_url) + getString(R.string.schedule_url));

        eventsWebView = (WebView) findViewById(R.id.EventsWebview);
        eventsWebView.getSettings().setJavaScriptEnabled(true);
        eventsWebView.setWebViewClient(new RadioWebViewClient());
        eventsWebView.loadUrl(getString(R.string.web_url) + getString(R.string.events_url));

        tabHost = getTabHost();
        tabHost.addTab(tabHost.newTabSpec("radio_tab").setIndicator("Radio", res.getDrawable(R.drawable.music)).setContent(R.id.RelativeLayout));
        tabHost.addTab(tabHost.newTabSpec("featured_tab").setIndicator("Featured", res.getDrawable(R.drawable.featured)).setContent(R.id.FeaturedWebview));
        tabHost.addTab(tabHost.newTabSpec("shoutbox_tab").setIndicator("Shoutbox", res.getDrawable(R.drawable.shoutbox)).setContent(R.id.ShoutBoxWebview));
        tabHost.addTab(tabHost.newTabSpec("shedule_tab").setIndicator("Schedule", res.getDrawable(R.drawable.schedule)).setContent(R.id.ScheduleWebview));
        tabHost.addTab(tabHost.newTabSpec("events_tab").setIndicator("Events", res.getDrawable(R.drawable.events)).setContent(R.id.EventsWebview));

        tabHost.setCurrentTab(0);

        playButton.setOnClickListener(this);
        playButton.setImageResource(android.R.drawable.ic_media_play);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (currentVolume == 0) {
            volumeImageView.setImageResource(android.R.drawable.ic_lock_silent_mode);
        } else {
            volumeImageView.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
        }
        volumeSeekBar.setMax(maxVolume);
        volumeSeekBar.setProgress(currentVolume);
        volumeSeekBar.setOnSeekBarChangeListener(this);
        // Retrieve preferences
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Setup the Nagare service
        bindService(new Intent(getApplicationContext(), NagareService.class), nagareServiceConn, Context.BIND_AUTO_CREATE);
        runRefresh.run();
        Intent intent = getIntent();
        if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_VIEW)) {
            PlayListFile playListFile = PlayListFactory.create(intent.getType(), intent.getData());
            if (playListFile == null) {
                outputView.setText("Not sure what to do with: " + intent.getAction() + ":" + intent.getDataString() + ":" + intent.getType());
            } else {
                playListFile.parse();
                if (playListFile.errors() != "") {
                    outputView.setText(playListFile.errors());
                } else {
                    outputView.setText(playListFile.play_list().m_entries.getFirst().m_url_string);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        // This informs other applications that access media on the SD card that our audio file no longer exists.
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://" + Environment.getExternalStorageDirectory())));

        notificationManager.cancel(MHYH_ID);
        try {
            if (nagareService != null) {
                nagareService.stop();
            }
        } catch (RemoteException e) {
            Log.e(MHYH_RADIO_LOG_TAG, "Caught RemoteException: " + e.getMessage());
        }
        unbindService(nagareServiceConn);
        super.onDestroy();
    }

    @Override
    protected void onRestart() {
        inBackground = false;
        notificationManager.cancel(MHYH_ID);
        super.onRestart();
    }

    @Override
    protected void onStop() {
        if (!isFinishing()) {
            inBackground = true;
            int icon = R.drawable.icon;
            CharSequence tickerText = "MHYH Radio running in background";
            long when = System.currentTimeMillis();
            notification = new Notification(icon, tickerText, when);
            notification.flags = Notification.FLAG_AUTO_CANCEL | Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
            Context context = getApplicationContext();
            CharSequence contentTitle = "MHYH Radio";
            CharSequence contentText = nowPlaying;
            Intent notificationIntent = new Intent(this, RadioApp.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
            notificationManager.notify(MHYH_ID, notification);
        }
        super.onStop();
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.PlayButton:
                if (nagareService == null) {
                    outputView.setText("No service available!");
                    Log.w(MHYH_RADIO_LOG_TAG, "Nagare service is NULL");
                    return;
                }
                try {
                    int state = nagareService.state();
                    if (state == NagareService.STOPPED && !inCall) {
                        nagareService.download(getString(R.string.radio_url));
                    } else if (state == NagareService.STOPPED && inCall) {
                        handleNagareErrors("inCall");
                    } else {
                        nagareService.stop();
                    }
                } catch (RemoteException e) {
                    outputView.setText("Error connecting to Nagare service: " + e.toString() + "\n");
                    Log.e(MHYH_RADIO_LOG_TAG, "Caught RemoteException: " + e.getMessage());
                }
                break;
        }

    }

    /**
     * ******************************************************************************************
     * **									TELEPHONY										   ***
     * *******************************************************************************************
     */

    private class RadioPhoneStateListener extends PhoneStateListener {

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE:
                    inCall = false;
                    try {
                        if (nagareService != null && wasPlaying) {
                            nagareService.download(getString(R.string.radio_url));
                        }
                    } catch (RemoteException e) {
                        Log.e(MHYH_RADIO_LOG_TAG, "Caught RemoteException: " + e.getMessage());
                    }
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                case TelephonyManager.CALL_STATE_RINGING:
                    try {
                        if (!inCall) {
                            if (nagareService != null) {
                                if (nagareService.state() != NagareService.STOPPED) {
                                    wasPlaying = true;
                                } else {
                                    wasPlaying = false;
                                }
                            }
                        }
                        inCall = true;
                    } catch (RemoteException e1) {
                        Log.e(MHYH_RADIO_LOG_TAG, "Caught RemoteException: " + e1.getMessage());
                    }
                    try {
                        if (nagareService != null) {
                            nagareService.stop();
                        }
                    } catch (RemoteException e2) {
                        Log.e(MHYH_RADIO_LOG_TAG, "Caught RemoteException: " + e2.getMessage());
                    }
                    break;
                default:
                    Log.w(MHYH_RADIO_LOG_TAG, "Found un-handled call state: " + state);
            }
        }

    }

    /**
     * ******************************************************************************************
     * **										AUDIO										   ***
     * *******************************************************************************************
     */

    public void onProgressChanged(SeekBar seekBar, int progress,
                                  boolean fromUser) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, AudioManager.FLAG_SHOW_UI);
        if (progress == 0) {
            volumeImageView.setImageResource(android.R.drawable.ic_lock_silent_mode);
        } else {
            volumeImageView.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
        }
    }

    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    /**
     * ******************************************************************************************
     * **									WEB VIEW										   ***
     * *******************************************************************************************
     */

    private class RadioWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }
    }

    public void onDrawerOpened() {
        imageView.setImageBitmap(rotateImage(android.R.drawable.ic_menu_more, 0));
    }

    public void onDrawerClosed() {
        imageView.setImageBitmap(rotateImage(android.R.drawable.ic_menu_more, 180));
    }

    /**
     * ******************************************************************************************
     * **										MENUS										   ***
     * *******************************************************************************************
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ExitMenuItem:
                onDestroy();
                System.exit(0);
                return true;
            /*case R.id.SettingsMenuItem:
               Intent i = new Intent(RadioApp.this, Preferences.class);
               startActivity(i);
               return true;*/
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * ******************************************************************************************
     * **									UTILITIES									  	   ***
     * *******************************************************************************************
     */

    private Bitmap rotateImage(int id, float degrees) {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), id);
        Matrix mat = new Matrix();
        mat.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mat, true);
    }

}