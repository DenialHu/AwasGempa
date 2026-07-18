package com.example.awasgempa;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class DemoWorker extends Worker {

    private static final String GEMPA_CHANNEL = "GEMPA_CHANNEL";
    private static final int GEMPA_NOTIF_ID = 999;

    public DemoWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String tipeDemo = getInputData().getString("TIPE");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(GEMPA_CHANNEL, "Peringatan Gempa", NotificationManager.IMPORTANCE_HIGH));
        }

        if ("MERAH".equals(tipeDemo)) {
            tampilkanAlarmBahaya("8.8", "DEMO: Megathrust Pesisir Selatan");
        } else if ("KUNING".equals(tipeDemo)) {
            tampilkanNotifikasi("WASPADA: Area Anda Mungkin Terdampak", "DEMO: Gempa M7.0 dirasakan di wilayah Anda", NotificationCompat.PRIORITY_HIGH);
        } else {
            tampilkanNotifikasi("Info Gempa (Anda Aman)", "DEMO: Gempa terjadi sangat jauh dari lokasi Anda", NotificationCompat.PRIORITY_LOW);
        }

        return Result.success();
    }

    private void tampilkanNotifikasi(String judul, String pesan, int priority) {
        NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), GEMPA_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(judul)
                .setContentText(pesan)
                .setPriority(priority)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        nm.notify(GEMPA_NOTIF_ID, builder.build());
    }

    private void tampilkanAlarmBahaya(String mag, String wilayah) {
        NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        Intent fullScreenIntent = new Intent(getApplicationContext(), AlarmActivity.class);
        fullScreenIntent.putExtra("MAGNITUDE", mag);
        fullScreenIntent.putExtra("WILAYAH", wilayah);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                getApplicationContext(), 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), GEMPA_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("BAHAYA GEMPA!")
                .setContentText("Anda berada di area terdampak! Layar peringatan diaktifkan.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true);

        nm.notify(GEMPA_NOTIF_ID, builder.build());
    }
}