package com.example.awasgempa;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

public class GempaWorker extends Worker {

    private static final int FG_NOTIF_ID = 2;
    private static final int GEMPA_NOTIF_ID = 1;
    private static final String FG_CHANNEL = "GEMPA_FG";
    private static final String GEMPA_CHANNEL = "GEMPA_CHANNEL";

    public GempaWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        buatChannel(getApplicationContext(), FG_CHANNEL, "Pemeriksaan Gempa", NotificationManager.IMPORTANCE_MIN);
        buatChannel(getApplicationContext(), GEMPA_CHANNEL, "Peringatan Gempa", NotificationManager.IMPORTANCE_HIGH);

        NotificationCompat.Builder fgBuilder = new NotificationCompat.Builder(getApplicationContext(), FG_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("AwasGempa")
                .setContentText("Memeriksa data gempa...")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN);

        setForegroundAsync(new ForegroundInfo(FG_NOTIF_ID, fgBuilder.build()));

        String url = "https://data.bmkg.go.id/DataMKG/TEWS/autogempa.json";
        RequestFuture<JSONObject> future = RequestFuture.newFuture();
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null, future, future);
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        queue.add(request);

        try {
            JSONObject response = future.get(30, TimeUnit.SECONDS);
            JSONObject gempaObj = response.getJSONObject("Infogempa").getJSONObject("gempa");
            String dateTime = gempaObj.getString("DateTime");
            String magnitude = gempaObj.getString("Magnitude");
            String wilayah = gempaObj.getString("Wilayah");
            String coordinates = gempaObj.getString("Coordinates");

            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            boolean isNew = (db.gempaDao().getByDateTime(dateTime) == null);

            if (isNew) {
                // Ambil koordinat User dari SharedPreferences
                android.content.SharedPreferences prefs = getApplicationContext().getSharedPreferences("AwasGempaPrefs", Context.MODE_PRIVATE);
                double userLat = prefs.getFloat("USER_LAT", 0f);
                double userLon = prefs.getFloat("USER_LON", 0f);

                double gempaLat = 0, gempaLon = 0;
                String[] sep = coordinates.split(",");
                if (sep.length == 2) {
                    gempaLat = Double.parseDouble(sep[0]);
                    gempaLon = Double.parseDouble(sep[1]);
                }

                double mag = 0;
                try { mag = Double.parseDouble(magnitude); } catch (Exception ignored) {}

                // Menghitung Jarak
                double jarakUser = -1;
                if (userLat != 0 && userLon != 0 && gempaLat != 0) {
                    jarakUser = hitungJarakHaversine(gempaLat, gempaLon, userLat, userLon);
                }

                // Asumsi radius (Karena Geocoder di background rentan gagal, kita gunakan estimasi kasar berdasar Magnitude)
                // Magnitude 5 = ~50km radius bahaya (merah)
                double radiusMerah = mag * 10.0;
                double radiusKuning = radiusMerah + (mag * 15.0);

                // PENENTUAN LOGIKA NOTIFIKASI
                if (jarakUser >= 0 && jarakUser <= radiusMerah) {
                    // ZONA MERAH: Panggil Layar Alarm Full Screen
                    tampilkanAlarmBahaya(magnitude, wilayah);
                } else if (jarakUser > radiusMerah && jarakUser <= radiusKuning) {
                    // ZONA KUNING: Notifikasi Standar Tinggi
                    tampilkanNotifikasi("WASPADA: Area Anda Mungkin Terdampak", "Gempa M" + magnitude + " di " + wilayah, NotificationCompat.PRIORITY_HIGH);
                } else {
                    // ZONA HIJAU / JAUH: Notifikasi Diam
                    tampilkanNotifikasi("Info Gempa (Anda Aman)", "Gempa M" + magnitude + " terjadi jauh di " + wilayah, NotificationCompat.PRIORITY_LOW);
                }

                // Simpan ke DB & Broadcast UI
                Intent intent = new Intent("DATA_GEMPA_BARU");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
            }

            return Result.success();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        }
    }

    // Fungsi Pembantu Jarak (Di-copy dari MainActivity)
    private double hitungJarakHaversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // Fungsi Notifikasi Standar (Hijau & Kuning)
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

    // Fungsi Notifikasi Layar Penuh (Merah)
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
                .setFullScreenIntent(fullScreenPendingIntent, true) // INTENT LAYAR PENUH
                .setAutoCancel(true);

        nm.notify(GEMPA_NOTIF_ID, builder.build());
    }

    private void buatChannel(Context ctx, String id, String nama, int importance) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(id, nama, importance));
        }
    }




}
