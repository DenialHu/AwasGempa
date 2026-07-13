package com.example.awasgempa;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private TextView tvDebugData;
    private Button btnRefresh;
    private FusedLocationProviderClient fusedLocationClient;
    private RequestQueue requestQueue;

    private final StringBuilder debugText = new StringBuilder();

    // Simpan koordinat lokasi user
    private double userLat = 0.0;
    private double userLon = 0.0;

    // Simpan koordinat pusat gempa
    private double gempaLat = 0.0;
    private double gempaLon = 0.0;

    // Variabel untuk melacak status geocoding banyak lokasi
    private int totalGeocoding = 0;
    private int completedGeocoding = 0;
    private double maxRadius = 0.0;
    private String areaTerjauh = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvDebugData = findViewById(R.id.tv_debug_data);
        btnRefresh = findViewById(R.id.btn_refresh);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestQueue = Volley.newRequestQueue(this);

        btnRefresh.setOnClickListener(v -> startDataloadingFlow());
        startDataloadingFlow();
    }

    private void startDataloadingFlow() {
        clearDebugText();
        checkLocationPermissionAndGet();
    }

    private void checkLocationPermissionAndGet() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getLastLocation();
        }
    }

    private void getLastLocation() {
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        userLat = location.getLatitude();
                        userLon = location.getLongitude();
                        appendToDebug("=== LOKASI ANDROID SEKARANG ===");
                        appendToDebug("Lat: " + userLat);
                        appendToDebug("Lon: " + userLon);
                        appendToDebug("===============================\n");
                    } else {
                        // Reset jika gagal
                        userLat = 0.0;
                        userLon = 0.0;
                        appendToDebug("=== LOKASI ANDROID SEKARANG ===");
                        appendToDebug("Lokasi tidak ditemukan (Pastikan GPS aktif)");
                        appendToDebug("===============================\n");
                    }
                    fetchDataBMKG();
                }
            });
        } catch (SecurityException e) {
            appendToDebug("Gagal mengambil lokasi: " + e.getMessage());
            fetchDataBMKG();
        }
    }

    private void fetchDataBMKG() {
        String url = "https://data.bmkg.go.id/DataMKG/TEWS/autogempa.json";
        appendToDebug("Mengambil data BMKG...");

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            JSONObject gempaObj = response.getJSONObject("Infogempa").getJSONObject("gempa");

                            String tanggal = gempaObj.getString("Tanggal");
                            String jam = gempaObj.getString("Jam");
                            String magnitude = gempaObj.getString("Magnitude");
                            String dirasakan = gempaObj.getString("Dirasakan");
                            String coordinates = gempaObj.getString("Coordinates");

                            String[] sep = coordinates.split(",");
                            if (sep.length == 2) {
                                gempaLat = Double.parseDouble(sep[0]);
                                gempaLon = Double.parseDouble(sep[1]);
                            }

                            appendToDebug("\n=== DATA API BMKG (TERBARU) ===");
                            appendToDebug("Waktu: " + tanggal + " " + jam);
                            appendToDebug("Magnitude: " + magnitude + " SR");
                            appendToDebug("Pusat Koordinat: " + coordinates);
                            appendToDebug("Daerah Dirasakan: " + dirasakan);
                            appendToDebug("===============================\n");

                            processSemuaDirasakanUntukRadius(dirasakan);

                        } catch (JSONException e) {
                            appendToDebug("JSON Parse Error BMKG: " + e.getMessage());
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                appendToDebug("Gagal ambil data BMKG: " + error.getMessage());
            }
        });

        requestQueue.add(jsonObjectRequest);
    }

    private void processSemuaDirasakanUntukRadius(String dirasakanRaw) {
        if (dirasakanRaw == null || dirasakanRaw.isEmpty() || dirasakanRaw.equals("-")) {
            appendToDebug("Tidak ada data daerah dirasakan.");
            return;
        }

        // Pecah berdasarkan koma
        String[] rawAreas = dirasakanRaw.split(",");
        List<String> cleanAreas = new ArrayList<>();

        // Bersihkan masing-masing daerah dan kumpulkan yang tidak duplikat
        for (String raw : rawAreas) {
            String clean = cleanDirasakanText(raw);
            if (!clean.isEmpty() && !cleanAreas.contains(clean)) {
                cleanAreas.add(clean);
            }
        }

        totalGeocoding = cleanAreas.size();
        completedGeocoding = 0;
        maxRadius = 0.0;
        areaTerjauh = "";

        if (totalGeocoding == 0) {
            appendToDebug("Gagal mengekstrak nama daerah dari data BMKG.");
            return;
        }

        appendToDebug("Sedang mengkalkulasi radius dari " + totalGeocoding + " titik lokasi terdampak...\n");

        for (String targetLokasi : cleanAreas) {
            fetchKoordinatDariNominatim(targetLokasi);
        }
    }

    private void fetchKoordinatDariNominatim(final String namaDaerah) {
        String url = "https://nominatim.openstreetmap.org/search?q=" + namaDaerah + "&format=json&limit=1";

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONArray jsonArray = new JSONArray(response);
                            if (jsonArray.length() > 0) {
                                JSONObject placeObj = jsonArray.getJSONObject(0);
                                double targetLat = placeObj.getDouble("lat");
                                double targetLon = placeObj.getDouble("lon");

                                // Hitung jarak
                                double jarakKm = hitungJarakHaversine(gempaLat, gempaLon, targetLat, targetLon);

                                // Logika penentuan yang paling jauh (Radius)
                                if (jarakKm > maxRadius) {
                                    maxRadius = jarakKm;
                                    areaTerjauh = namaDaerah;
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        } finally {
                            cekPenyelesaianGeocoding();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // Walaupun gagal untuk 1 daerah, kalkulasi daerah lain harus tetap lanjut
                cekPenyelesaianGeocoding();
            }
        }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "AwasGempaAndroidApp-com.example.awasgempa");
                return headers;
            }
        };

        requestQueue.add(stringRequest);
    }

    // Dipanggil setiap kali request Nominatim selesai (sukses/gagal)
    private void cekPenyelesaianGeocoding() {
        completedGeocoding++;
        if (completedGeocoding == totalGeocoding) {
            tampilkanHasilAkhir();
        }
    }

    private void tampilkanHasilAkhir() {
        appendToDebug("=== HASIL KALKULASI ===");
        if (maxRadius > 0) {
            appendToDebug("Titik Terdampak Terjauh: " + areaTerjauh);
            appendToDebug("Radius Gempa (Jarak Maksimal): " + String.format("%.2f", maxRadius) + " km\n");

            // Cek apakah lokasi HP valid
            if (userLat != 0.0 && userLon != 0.0) {
                double jarakUserKeGempa = hitungJarakHaversine(gempaLat, gempaLon, userLat, userLon);
                appendToDebug("Jarak Lokasi Anda ke Pusat Gempa: " + String.format("%.2f", jarakUserKeGempa) + " km\n");

                // LOGIKA UTAMA: Apakah user ada di dalam radius?
                if (jarakUserKeGempa <= maxRadius) {
                    appendToDebug("STATUS: ⚠️ WASPADA! LOKASI ANDA BERADA DI DALAM RADIUS TERDAMPAK.");
                } else {
                    appendToDebug("STATUS: ✅ AMAN. LOKASI ANDA BERADA DI LUAR RADIUS TERDAMPAK.");
                }
            } else {
                appendToDebug("STATUS: ❓ Tidak dapat mendeteksi lokasi HP Anda, status radius tidak dapat dihitung.");
            }
        } else {
            appendToDebug("Gagal menghitung radius (API koordinat tidak merespon).");
        }
        appendToDebug("=======================");
    }

    private String cleanDirasakanText(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("-")) return "";

        // Kita tidak split by koma lagi di sini karena sudah displit di fungsi utama
        String area = raw;

        // Hapus teks MMI dan angka romawi
        area = area.replaceAll("(?i)\\bMMI\\b", "");
        area = area.replaceAll("(?i)\\b(VII|VI|IV|III|II|I|V)\\b", "");
        area = area.replace("-", "");

        return area.trim().replaceAll("\\s+", " ");
    }

    private double hitungJarakHaversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void clearDebugText() {
        debugText.setLength(0);
        runOnUiThread(() -> tvDebugData.setText(""));
    }

    private void appendToDebug(String text) {
        debugText.append(text).append("\n");
        runOnUiThread(() -> tvDebugData.setText(debugText.toString()));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation();
            } else {
                appendToDebug("Permission lokasi ditolak. Lewati data lokasi HP.\n");
                fetchDataBMKG();
            }
        }
    }
}