package com.uklanafood.restaurant;

import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class RingtoneSettingsActivity extends AppCompatActivity {

    private TextView txtSelectedRingtone;

    private final ActivityResultLauncher<Intent> ringtonePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK ||
                                result.getData() == null) {
                            return;
                        }

                        Uri selectedUri;

                        if (android.os.Build.VERSION.SDK_INT >=
                                android.os.Build.VERSION_CODES.TIRAMISU) {

                            selectedUri = result.getData().getParcelableExtra(
                                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                                    Uri.class
                            );
                        } else {
                            selectedUri = result.getData().getParcelableExtra(
                                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI
                            );
                        }

                        if (selectedUri != null) {
                            RingtoneHelper.saveRingtone(
                                    RingtoneSettingsActivity.this,
                                    selectedUri
                            );

                            NotificationHelper.recreateNotificationChannel(
                                    RingtoneSettingsActivity.this
                            );

                            updateSelectedRingtoneName();

                            Toast.makeText(
                                    this,
                                    "Ringtone successfully saved",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ringtone_settings);

        txtSelectedRingtone = findViewById(R.id.txtSelectedRingtone);

        Button btnChooseRingtone = findViewById(R.id.btnChooseRingtone);
        Button btnTestRingtone = findViewById(R.id.btnTestRingtone);
        Button btnDefaultRingtone = findViewById(R.id.btnDefaultRingtone);
        Button btnNotificationSettings =
                findViewById(R.id.btnNotificationSettings);

        updateSelectedRingtoneName();

        btnChooseRingtone.setOnClickListener(view -> openRingtonePicker());

        btnTestRingtone.setOnClickListener(view ->
                NotificationHelper.showTestNotification(this)
        );

        btnDefaultRingtone.setOnClickListener(view -> {
            RingtoneHelper.resetToDefault(this);
            NotificationHelper.recreateNotificationChannel(this);
            updateSelectedRingtoneName();

            Toast.makeText(
                    this,
                    "Default ringtone restored",
                    Toast.LENGTH_SHORT
            ).show();
        });

        btnNotificationSettings.setOnClickListener(view ->
                openAndroidNotificationSettings()
        );
    }

    private void openRingtonePicker() {
        Intent intent = new Intent(
                RingtoneManager.ACTION_RINGTONE_PICKER
        );

        intent.putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_NOTIFICATION
        );

        intent.putExtra(
                RingtoneManager.EXTRA_RINGTONE_TITLE,
                "Select New Order Ringtone"
        );

        intent.putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                RingtoneHelper.getRingtone(this)
        );

        intent.putExtra(
                RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,
                true
        );

        intent.putExtra(
                RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,
                false
        );

        ringtonePickerLauncher.launch(intent);
    }

    private void updateSelectedRingtoneName() {
        txtSelectedRingtone.setText(
                RingtoneHelper.getRingtoneName(this)
        );
    }

    private void openAndroidNotificationSettings() {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS
            );

            intent.putExtra(
                    Settings.EXTRA_APP_PACKAGE,
                    getPackageName()
            );

            startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "Notification settings could not be opened",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }
}