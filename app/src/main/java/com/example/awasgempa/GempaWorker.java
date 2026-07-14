package com.example.awasgempa;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONObject;

public class GempaWorker extends Worker {
    public GempaWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Kita gunakan Volley dalam mode sinkron (menunggu sampai selesai)
        fetchDataAndSave(getApplicationContext());
        return Result.success();
    }

    private void fetchDataAndSave(Context context) {
        String url = "https://data.bmkg.go.id/DataMKG/TEWS/autogempa.json";
        AppDatabase db = AppDatabase.getInstance(context);

        // Volley Request
        RequestQueue queue = Volley.newRequestQueue(context);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null, response -> {
            try {
                JSONObject gempaObj = response.getJSONObject("Infogempa").getJSONObject("gempa");
                String dateTime = gempaObj.getString("DateTime");

                // Cek apakah data ini sudah ada di database?
                if (db.gempaDao().getByDateTime(dateTime) == null) {
                    GempaHistory h = new GempaHistory(
                            gempaObj.getString("Tanggal"), gempaObj.getString("Jam"), dateTime,
                            gempaObj.getString("Coordinates"), gempaObj.getString("Magnitude"),
                            gempaObj.getString("Kedalaman"), gempaObj.getString("Wilayah"),
                            gempaObj.optString("Potensi"), gempaObj.getString("Dirasakan"),
                            0.0, 0.0, "OTOMATIS", 0.0, System.currentTimeMillis()
                    );
                    db.gempaDao().insert(h);
                }
            } catch (Exception ignored) {}
        }, error -> {});
        queue.add(request);
    }
}