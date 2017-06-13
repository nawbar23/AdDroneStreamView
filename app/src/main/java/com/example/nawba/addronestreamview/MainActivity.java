package com.example.nawba.addronestreamview;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutCompat;
import android.text.InputType;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity implements StreamConnection.OnNewFrameListener {

    private ImageView imageView;
    private StreamConnection streamConnection;

    private ProgressDialog progressDialog;

    private Bitmap bitmap;
    private Lock bitmapLock = new ReentrantLock();

    private Timer timer;
    private TimerTask timerUpdateTask = new TimerTask() {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bitmapLock.lock();
                    imageView.setImageBitmap(bitmap);
                    bitmapLock.unlock();
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Connecting...");

        startConnectionDialog();
    }

    void handleError(String msg) {
        Log.e(MainActivity.class.getSimpleName(), msg);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Something went wrong :C")
                .setCancelable(false)
                .setMessage(msg)
                .setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        startConnectionDialog();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    void startConnectionDialog() {
        Log.i(MainActivity.class.getSimpleName(), "startConnectionDialog");
        final EditText ipInput = new EditText(this);
        ipInput.setInputType(InputType.TYPE_CLASS_PHONE);
        ipInput.setHint("IP address");

        final EditText portInput = new EditText(this);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setHint("Port number");

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        String ip = sharedPref.getString(getString(R.string.prefs_ip_address), null);
        int port = sharedPref.getInt(getString(R.string.prefs_port_number), -1);

        if (ip != null && port != -1) {
            ipInput.setText(ip);
            portInput.setText(String.valueOf(port));
        }

        LinearLayoutCompat lay = new LinearLayoutCompat(this);
        lay.setOrientation(LinearLayoutCompat.VERTICAL);
        lay.addView(ipInput);
        lay.addView(portInput);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connection info")
                .setCancelable(false)
                .setMessage("Where I can find streaming server?")
                .setView(lay)
                .setPositiveButton("Connect", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            connectStream(ipInput.getText().toString(), Integer.valueOf(portInput.getText().toString()));
                        } catch (NumberFormatException e) {
                            Log.e(MainActivity.class.getSimpleName(), "Wrong port value added!");
                            handleError("Wrong port value added!");
                        }
                    }
                })
                .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        finishAffinity();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }

    void safeConnectionInfo(String ip, int port) {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(getString(R.string.prefs_ip_address), ip);
        editor.putInt(getString(R.string.prefs_port_number), port);
        editor.apply();
    }

    void connectStream(String ip, int port) {
        Log.i(MainActivity.class.getSimpleName(), "Connecting stream with ip: " + ip + " port: " + String.valueOf(port));
        progressDialog.show();

        safeConnectionInfo(ip, port);

        streamConnection = new StreamConnection(ip, port, this);
        imageView = (ImageView) findViewById(R.id.image_view);
        timer = new Timer();

        streamConnection.start();
    }

    @Override
    public void onResume() {
        Log.i(MainActivity.class.getSimpleName(), "onResume");
        super.onResume();
        if (streamConnection != null) {
            streamConnection.start();
        }
    }

    @Override
    public void onPause() {
        Log.i(MainActivity.class.getSimpleName(), "onPause");
        super.onPause();
        if (streamConnection != null) {
            streamConnection.disconnect();
            timer.cancel();
        }
    }

    @Override
    public void setNewFrame(final byte[] array, final int length) {
        bitmapLock.lock();
        bitmap = BitmapFactory.decodeByteArray(array, 0, length);
        bitmapLock.unlock();
    }

    @Override
    public void onConnected() {
        Log.i(MainActivity.class.getSimpleName(), "onConnected");
        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        timer.scheduleAtFixedRate(timerUpdateTask, 0, 40);
    }

    @Override
    public void onError(final String message) {
        Log.i(MainActivity.class.getSimpleName(), "onError");
        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                handleError(message);
            }
        });
    }
}
