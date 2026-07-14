package com.example.awasgempa;

import android.content.Context;
import android.content.Intent; // <--- INI TADI YANG KURANG
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONObject;
import com.example.awasgempa.AppDatabase;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class GempaWorker extends Worker {
    public GempaWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Karena Volley bersifat asinkron, di dalam Worker kita harus memastikan proses selesai.
        // Untuk skenario sederhana ini, kita jalankan request.
        // Jika butuh sinkronisasi penuh, biasanya digunakan CountDownLatch.
        fetchDataAndSave(getApplicationContext());
        return Result.success();
    }

    private void fetchDataAndSave(Context context) {
        String url = "https://data.bmkg.go.id/DataMKG/TEWS/autogempa.json";
        AppDatabase db = AppDatabase.getInstance(context);

        RequestQueue queue = Volley.newRequestQueue(context);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null, response -> {
            try {
                JSONObject gempaObj = response.getJSONObject("Infogempa").getJSONObject("gempa");
                String dateTime = gempaObj.getString("DateTime");

                // Cek apakah data ini sudah ada di database
                if (db.gempaDao().getByDateTime(dateTime) == null) {
                    GempaHistory h = new GempaHistory(
                            gempaObj.getString("Tanggal"),
                            gempaObj.getString("Jam"),
                            dateTime,
                            gempaObj.getString("Coordinates"),
                            gempaObj.getString("Magnitude"),
                            gempaObj.getString("Kedalaman"),
                            gempaObj.getString("Wilayah"),
                            gempaObj.optString("Potensi"),
                            gempaObj.getString("Dirasakan"),
                            0.0, 0.0, "OTOMATIS", 0.0, System.currentTimeMillis()
                    );

                    db.gempaDao().insert(h);

                    // Kirim sinyal ke MainActivity
                    Intent intent = new Intent("DATA_GEMPA_BARU");
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, error -> {
            error.printStackTrace();
        });

        queue.add(request);
    }
}