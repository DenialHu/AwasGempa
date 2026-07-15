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
    public GempaWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
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

            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            if (db.gempaDao().getByDateTime(dateTime) == null) {
                GempaHistory h = new GempaHistory(
                        gempaObj.getString("Tanggal"), gempaObj.getString("Jam"), dateTime,
                        gempaObj.getString("Coordinates"), magnitude,
                        gempaObj.getString("Kedalaman"), wilayah,
                        gempaObj.optString("Potensi"), gempaObj.getString("Dirasakan"),
                        0.0, 0.0, "OTOMATIS", 0.0, System.currentTimeMillis()
                );
                db.gempaDao().insert(h);

                // Kirim sinyal ke MainActivity jika app sedang dibuka
                Intent intent = new Intent("DATA_GEMPA_BARU");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

                // MUNCULKAN NOTIFIKASI
                tampilkanNotifikasi(magnitude, wilayah);
            }
            return Result.success();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        }
    }

    private void tampilkanNotifikasi(String mag, String wilayah) {
        String channelId = "GEMPA_CHANNEL";
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        // Wajib membuat Notification Channel untuk Android 8.0 (Oreo) ke atas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Peringatan Gempa",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        // Intent agar saat notifikasi diklik, aplikasi AwasGempa terbuka
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                getApplicationContext(),
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
        );

        // Membangun tampilan notifikasi
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert) // Bisa kamu ganti dengan R.drawable.logo_aplikasimu
                .setContentTitle("Gempa Baru M " + mag)
                .setContentText(wilayah)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Munculkan pop-up di atas layar (head-up)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true); // Hilang setelah diklik

        // Tampilkan notifikasi (ID 1 agar jika ada gempa baru lagi, notifikasi lama tertimpa)
        notificationManager.notify(1, builder.build());
    }
}