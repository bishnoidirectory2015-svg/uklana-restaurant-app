package com.uklanafood.admin;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class RingtoneSettingsActivity extends AppCompatActivity {
    private TextView selectedToneText;
    private MediaPlayer previewPlayer;

    private final ActivityResultLauncher<Intent> ringtonePicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (uri == null) {
                    Toast.makeText(this, "Silent ringtone select nahi ki ja sakti", Toast.LENGTH_LONG).show();
                    return;
                }
                String name = ringtoneName(uri);
                RingtoneHelper.save(this, uri, name);
                updateSelectedTone();
                Toast.makeText(this, "Order ringtone save ho gayi", Toast.LENGTH_SHORT).show();
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ringtone_settings);
        selectedToneText = findViewById(R.id.selectedToneText);
        findViewById(R.id.chooseRingtoneButton).setOnClickListener(v -> openPicker());
        findViewById(R.id.testRingtoneButton).setOnClickListener(v -> testRingtone());
        findViewById(R.id.stopTestButton).setOnClickListener(v -> stopPreview());
        findViewById(R.id.defaultRingtoneButton).setOnClickListener(v -> {
            stopPreview();
            RingtoneHelper.reset(this);
            updateSelectedTone();
            Toast.makeText(this, "Default Order Tone set ho gayi", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        updateSelectedTone();
    }

    private void openPicker() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE | RingtoneManager.TYPE_NOTIFICATION | RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Order Ringtone Select Karein");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        Uri current = RingtoneHelper.getSelectedUri(this);
        if (!"android.resource".equals(current.getScheme())) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current);
        }
        ringtonePicker.launch(intent);
    }

    private void updateSelectedTone() {
        selectedToneText.setText("Selected: " + RingtoneHelper.getSelectedName(this));
    }

    private String ringtoneName(Uri uri) {
        try {
            Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone != null) {
                String title = ringtone.getTitle(this);
                if (title != null && !title.trim().isEmpty()) return title;
            }
        } catch (Exception ignored) {}
        return "Selected Ringtone";
    }

    private void testRingtone() {
        stopPreview();
        try {
            previewPlayer = new MediaPlayer();
            previewPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            previewPlayer.setDataSource(this, RingtoneHelper.getSelectedUri(this));
            previewPlayer.setLooping(false);
            previewPlayer.setOnCompletionListener(mp -> stopPreview());
            previewPlayer.prepare();
            previewPlayer.start();
        } catch (Exception e) {
            stopPreview();
            Toast.makeText(this, "Ringtone play nahi hui. Dusri ringtone select karein.", Toast.LENGTH_LONG).show();
        }
    }

    private void stopPreview() {
        try {
            if (previewPlayer != null) {
                if (previewPlayer.isPlaying()) previewPlayer.stop();
                previewPlayer.release();
            }
        } catch (Exception ignored) {}
        previewPlayer = null;
    }

    @Override protected void onDestroy() {
        stopPreview();
        super.onDestroy();
    }
}
