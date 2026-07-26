package com.uklanafood.restaurant;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class OrderAlertActivity extends AppCompatActivity {
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_incoming_order);

        String orderId = getIntent().getStringExtra("order_id");
        String title = getIntent().getStringExtra("title");
        String message = getIntent().getStringExtra("message");

        ((TextView) findViewById(R.id.alertTitle)).setText(
                title == null || title.trim().isEmpty() ? "NEW ORDER RECEIVED" : title
        );
        ((TextView) findViewById(R.id.alertMessage)).setText(
                message == null || message.trim().isEmpty() ? "Open the app to view order details." : message
        );
        ((TextView) findViewById(R.id.alertOrderId)).setText(
                orderId == null || orderId.trim().isEmpty() ? "" : "Order #" + orderId
        );

        Button open = findViewById(R.id.openOrderButton);
        Button dismiss = findViewById(R.id.dismissAlertButton);

        open.setOnClickListener(v -> {
            stopAlert();
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("open_order_id", orderId);
            intent.putExtra("open_pending_orders", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        dismiss.setOnClickListener(v -> {
            stopAlert();
            finish();
        });

        startAlert();
    }

    private void startAlert() {
        try {
            Uri uri = RingtoneHelper.getRingtone(this);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception firstError) {
            try {
                mediaPlayer = MediaPlayer.create(this, R.raw.order_alert);
                if (mediaPlayer != null) {
                    mediaPlayer.setLooping(true);
                    mediaPlayer.start();
                }
            } catch (Exception ignored) { }
        }

        long[] pattern = new long[]{0, 700, 300, 700, 300, 1200};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vm != null) vibrator = vm.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private void stopAlert() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception ignored) { }
        try {
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignored) { }
    }

    @Override
    protected void onDestroy() {
        stopAlert();
        super.onDestroy();
    }
}
