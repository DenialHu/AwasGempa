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

            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            boolean isNew = (db.gempaDao().getByDateTime(dateTime) == null);

            if (isNew) {
                Intent intent = new Intent("DATA_GEMPA_BARU");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                tampilkanNotifikasi(magnitude, wilayah);
            }

            return Result.success();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        }
    }

    private void buatChannel(Context ctx, String id, String nama, int importance) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(id, nama, importance));
        }
    }

    private void tampilkanNotifikasi(String mag, String wilayah) {
        NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                getApplicationContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), GEMPA_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Gempa Baru M " + mag)
                .setContentText(wilayah)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        nm.notify(GEMPA_NOTIF_ID, builder.build());
    }
}
