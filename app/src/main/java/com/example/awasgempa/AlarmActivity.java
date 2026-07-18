package com.example.awasgempa;

import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AlarmActivity extends AppCompatActivity {
    private Ringtone ringtone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // KODE SAKTI: Memaksa layar menyala menembus Lock Screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_alarm);

        String mag = getIntent().getStringExtra("MAGNITUDE");
        String wilayah = getIntent().getStringExtra("WILAYAH");

        TextView tvDesc = findViewById(R.id.tv_alarm_desc);
        tvDesc.setText("Gempa M" + mag + "\n" + wilayah + "\n\nSegera jauhi bangunan retak!");

        // Putar suara Alarm kencang
        Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        ringtone = RingtoneManager.getRingtone(this, alarmUri);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ringtone.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        }
        ringtone.play();

        // Tombol Matikan
        findViewById(R.id.btn_matikan_alarm).setOnClickListener(v -> {
            if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
            finish(); // Tutup layar merah
        });
    }

    @Override
    protected void onDestroy() {
        if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        super.onDestroy();
    }
}