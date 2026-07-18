package com.example.awasgempa;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;


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
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private TextView tvMagnitude, tvStatusBadge, tvDistance, tvLocation, tvDatetime, tvAppTitle, tvDataAge;
    private MaterialCardView statusCard;
    private MaterialButton btnRefresh, btnHistory, btnZoomIn, btnZoomOut;
    private MapView mapView;
    private FusedLocationProviderClient fusedLocationClient;
    private RequestQueue requestQueue;
    private AppDatabase database;
    private com.google.android.gms.location.LocationRequest locationRequest;
    private double userLat = 0.0, userLon = 0.0;
    private double gempaLat = 0.0, gempaLon = 0.0;
    private double maxRadius = 0.0;

    private String gempaTanggal = "", gempaJam = "", gempaDateTime = "";
    private String gempaCoordinates = "", gempaMagnitude = "";
    private String gempaKedalaman = "", gempaWilayah = "";
    private String gempaDirasakan = "", gempaPotensi = "";

    private final List<GeoPoint> affectedAreaPoints = new ArrayList<>();
    private final List<String> affectedAreaNames = new ArrayList<>();
    private final android.content.BroadcastReceiver gempaReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            // Jika sinyal diterima, jalankan refresh otomatis
            startDataloadingFlow();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue("AwasGempa/1.0");
        Configuration.getInstance().setOsmdroidBasePath(getCacheDir());
        Configuration.getInstance().setOsmdroidTileCache(getCacheDir());

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvAppTitle = findViewById(R.id.tv_app_title);
        tvMagnitude = findViewById(R.id.tv_magnitude);
        tvStatusBadge = findViewById(R.id.tv_status_badge);
        tvDistance = findViewById(R.id.tv_distance);
        tvLocation = findViewById(R.id.tv_location);
        tvDatetime = findViewById(R.id.tv_datetime);
        tvDataAge = findViewById(R.id.tv_data_age);
        statusCard = findViewById(R.id.status_card);
        btnHistory = findViewById(R.id.btn_history);
        btnRefresh = findViewById(R.id.btn_refresh);
        mapView = findViewById(R.id.map_view);
        btnZoomIn = findViewById(R.id.btn_zoom_in);
        btnZoomOut = findViewById(R.id.btn_zoom_out);

        btnHistory.setBackgroundColor(Color.parseColor("#1A1A24"));
        btnHistory.setIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#00E5FF")));
        btnRefresh.setBackgroundColor(Color.parseColor("#1A1A24"));
        btnRefresh.setIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#00E5FF")));

        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(5.5);
        mapView.getController().setCenter(new GeoPoint(-2.0, 118.0));
        mapView.setBackgroundColor(Color.parseColor("#0D0D1A"));

        mapView.getZoomController().setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);
        btnZoomIn.setOnClickListener(v -> mapView.getController().zoomIn());
        btnZoomOut.setOnClickListener(v -> mapView.getController().zoomOut());

        tvAppTitle.setOnClickListener(v -> startDataloadingFlow());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestQueue = Volley.newRequestQueue(this);
        database = AppDatabase.getInstance(this);

        btnRefresh.setOnClickListener(v -> startDataloadingFlow());
        btnHistory.setOnClickListener(v -> showHistorySheet());
        startDataloadingFlow();
        fetchHistoryGempa();
        setupBackgroundWork();
        mintaIzinBackground();
    }
    private void setupBackgroundWork() {
        PeriodicWorkRequest gempaWorkRequest =
                new PeriodicWorkRequest.Builder(GempaWorker.class, 15, TimeUnit.MINUTES)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "GempaSyncWork",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP, // Jangan restart kalau sudah jalan
                gempaWorkRequest
        );
    }
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .registerReceiver(gempaReceiver, new android.content.IntentFilter("DATA_GEMPA_BARU"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(gempaReceiver);
    }

    private void startDataloadingFlow() {
        tvMagnitude.setText("--");
        tvStatusBadge.setText("MENCARI LOKASI...");
        tvStatusBadge.setTextColor(Color.parseColor("#B0B0C0"));
        tvStatusBadge.setAlpha(1.0f);
        tvStatusBadge.setOnClickListener(null); // Matikan klik saat sedang loading

        tvDataAge.setText("Memeriksa status...");
        tvDistance.setText("--");
        tvLocation.setText("Mendapatkan koordinat user...");
        tvDatetime.setText("Menunggu waktu...");

        mapView.getOverlays().clear();
        affectedAreaPoints.clear();
        affectedAreaNames.clear();

        // Panggil langsung pengecekan lokasi
        checkLocationPermissionAndGet();
    }

    private void checkLocationPermissionAndGet() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // UBAH INI:
            requestNewLocation();
        }
    }

    private void requestNewLocation() {
        // Minta lokasi yang akurat dan segar
        locationRequest = com.google.android.gms.location.LocationRequest.create();
        locationRequest.setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(0); // Update secepat mungkin
        locationRequest.setFastestInterval(0);
        locationRequest.setNumUpdates(1); // Cukup satu kali update yang akurat

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, new com.google.android.gms.location.LocationCallback() {
                @Override
                public void onLocationResult(@NonNull com.google.android.gms.location.LocationResult locationResult) {
                    Location location = locationResult.getLastLocation();
                    // ... kode sebelumnya di dalam requestNewLocation() ...
                    if (location != null) {
                        userLat = location.getLatitude();
                        userLon = location.getLongitude();

                        // TAMBAHKAN 3 BARIS INI: Simpan lokasi untuk dibaca GempaWorker nanti
                        android.content.SharedPreferences prefs = getSharedPreferences("AwasGempaPrefs", MODE_PRIVATE);
                        prefs.edit().putFloat("USER_LAT", (float) userLat).putFloat("USER_LON", (float) userLon).apply();
                    }
                    fetchDataBMKG();
// ...
                }
            }, android.os.Looper.getMainLooper());
        } catch (SecurityException e) {
            fetchDataBMKG();
        }
    }

    private void fetchHistoryGempa() {
        String url = "https://data.bmkg.go.id/DataMKG/TEWS/gempaterkini.json";
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        org.json.JSONArray list = response.getJSONObject("Infogempa").getJSONArray("gempa");
                        new Thread(() -> {
                            for (int i = 0; i < list.length(); i++) {
                                try {
                                    org.json.JSONObject g = list.getJSONObject(i);
                                    String dt = g.getString("DateTime");
                                    if (database.gempaDao().getByDateTime(dt) != null) continue;

                                    GempaHistory h = new GempaHistory(
                                            g.getString("Tanggal"), g.getString("Jam"), dt,
                                            g.getString("Coordinates"), g.getString("Magnitude"),
                                            g.getString("Kedalaman"), g.getString("Wilayah"),
                                            g.optString("Potensi", ""), g.optString("Dirasakan", "-"),
                                            0.0, 0.0, "RIWAYAT", 0.0, System.currentTimeMillis()
                                    );
                                    database.gempaDao().insert(h);
                                } catch (Exception ignored) {}
                            }
                        }).start();
                    } catch (Exception ignored) {}
                }, error -> {});

        requestQueue.add(req);
    }

    private void fetchDataBMKG() {
        String url = "https://data.bmkg.go.id/DataMKG/TEWS/autogempa.json";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            JSONObject gempaObj = response.getJSONObject("Infogempa").getJSONObject("gempa");

                            gempaTanggal = gempaObj.getString("Tanggal");
                            gempaJam = gempaObj.getString("Jam");
                            gempaDateTime = gempaObj.getString("DateTime");
                            gempaMagnitude = gempaObj.getString("Magnitude");
                            gempaCoordinates = gempaObj.getString("Coordinates");
                            gempaKedalaman = gempaObj.getString("Kedalaman");
                            gempaWilayah = gempaObj.getString("Wilayah");
                            gempaDirasakan = gempaObj.getString("Dirasakan");
                            gempaPotensi = gempaObj.optString("Potensi", "");

                            // LOGIKA STATUS DATA (AKTUAL/LAMA)
                            if (tvDataAge != null) {
                                // GANTI BLOK LOGIKA STATUS DATA (AKTUAL/LAMA) DI fetchDataBMKG DENGAN INI:

                                // GANTI BLOK try-catch pengecekan tanggal di fetchDataBMKG DENGAN INI:

                                // GANTI BLOK PENGECEKAN TANGGAL DI fetchDataBMKG DENGAN INI:

                                try {
                                    // Format untuk BMKG ISO 8601: yyyy-MM-dd'T'HH:mm:ssXXX
                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
                                    Date gempaDate = sdf.parse(gempaDateTime);

                                    if (gempaDate != null) {
                                        long diffInMillis = System.currentTimeMillis() - gempaDate.getTime();
                                        long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis);

                                        if (totalMinutes <= 15) {
                                            tvDataAge.setText("STATUS: AKTUAL (DATA TERBARU)");
                                            tvDataAge.setTextColor(Color.parseColor("#00E676"));
                                        } else {
                                            // Kalkulasi manual untuk hari, jam, dan menit
                                            long days = totalMinutes / (24 * 60); // 1 hari = 1440 menit
                                            long sisaMenitSetelahHari = totalMinutes - (days * 24 * 60);

                                            long hours = sisaMenitSetelahHari / 60; // 1 jam = 60 menit
                                            long minutes = sisaMenitSetelahHari - (hours * 60);

                                            // Menyusun teks agar rapi (menyembunyikan yang bernilai 0)
                                            StringBuilder timeString = new StringBuilder("STATUS: DATA LAMA (");
                                            if (days > 0) {
                                                timeString.append(days).append(" hari ");
                                            }
                                            if (hours > 0) {
                                                timeString.append(hours).append(" jam ");
                                            }
                                            timeString.append(minutes).append(" menit lalu)");

                                            tvDataAge.setText(timeString.toString());
                                            tvDataAge.setTextColor(Color.parseColor("#FFC107"));
                                        }
                                    }
                                } catch (Exception e) {
                                    tvDataAge.setText("FORMAT ERROR: " + e.getMessage());
                                    tvDataAge.setTextColor(Color.parseColor("#FF1744"));
                                }
                            }

                            String[] sep = gempaCoordinates.split(",");
                            if (sep.length == 2) {
                                gempaLat = Double.parseDouble(sep[0]);
                                gempaLon = Double.parseDouble(sep[1]);
                            }

                            tvMagnitude.setText(gempaMagnitude);
                            tvLocation.setText(gempaWilayah);
                            tvDatetime.setText(gempaTanggal + " | " + gempaJam);

                            addEpicenterMarker();
                            processSemuaDirasakanUntukRadius(gempaDirasakan);

                        } catch (JSONException e) {
                            tvLocation.setText("Gagal parse data BMKG");
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                tvLocation.setText("Gagal ambil data.");
                if (tvDataAge != null) {
                    tvDataAge.setText("STATUS: GAGAL MENGAMBIL DATA");
                    tvDataAge.setTextColor(Color.parseColor("#FF1744"));
                }
            }
        });

        requestQueue.add(jsonObjectRequest);
    }

    private void mintaIzinBackground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            // Cek apakah aplikasi kita masih dibatasi baterainya
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {

                // Bisa tambahkan AlertDialog di sini untuk menjelaskan ke user
                // "Mohon izinkan aktivitas latar belakang agar notifikasi gempa tetap berjalan"

                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }
    
    // ==================== MAP & MARKERS LOGIC ====================

    private void addEpicenterMarker() {
        GeoPoint epicenter = new GeoPoint(gempaLat, gempaLon);

        Marker marker = new Marker(mapView);
        marker.setPosition(epicenter);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setTitle("Pusat Gempa — M" + gempaMagnitude);
        marker.setSnippet(gempaWilayah + "\nKedalaman: " + gempaKedalaman);
        marker.setIcon(getDrawableForEpicenter());

        marker.setOnMarkerClickListener((m, mv) -> {
            showModernMarkerInfo(m.getTitle(), m.getSnippet());
            return true;
        });
        mapView.getOverlays().add(marker);

        Marker pulse = new Marker(mapView);
        pulse.setPosition(epicenter);
        pulse.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        pulse.setIcon(getDrawableForPulse());
        pulse.setAlpha(0.35f);
        mapView.getOverlays().add(pulse);

        mapView.invalidate();
    }

    private void addRadiusCircles(double mag) {
        GeoPoint center = new GeoPoint(gempaLat, gempaLon);

        // 1. Lingkaran Kuning (Batas Luar Rambatan: maxRadius + (mag*10))
        double radiusKuningKm = maxRadius + (mag * 10.0);
        Polygon circleKuning = new Polygon();
        circleKuning.setPoints(Polygon.pointsAsCircle(center, radiusKuningKm * 1000));
        circleKuning.setFillColor(Color.argb(20, 255, 193, 7));
        circleKuning.setStrokeColor(Color.argb(120, 255, 193, 7));
        circleKuning.setStrokeWidth(2);
        mapView.getOverlays().add(circleKuning);

        // 2. Lingkaran Merah (Batas Dalam Resmi Terdampak)
        Polygon circleMerah = new Polygon();
        circleMerah.setPoints(Polygon.pointsAsCircle(center, maxRadius * 1000));
        circleMerah.setFillColor(Color.argb(30, 255, 23, 68));
        circleMerah.setStrokeColor(Color.argb(160, 255, 23, 68));
        circleMerah.setStrokeWidth(3);
        mapView.getOverlays().add(circleMerah);

        mapView.invalidate();
    }

    private void addAffectedAreaMarkers() {
        for (int i = 0; i < affectedAreaPoints.size(); i++) {
            GeoPoint point = affectedAreaPoints.get(i);
            String areaName = affectedAreaNames.get(i);

            Marker m = new Marker(mapView);
            m.setPosition(point);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            m.setTitle("Area Terdampak");
            m.setSnippet(areaName);
            m.setIcon(getDrawableForAffectedArea());

            m.setOnMarkerClickListener((marker, mv) -> {
                showModernMarkerInfo(marker.getTitle(), marker.getSnippet());
                return true;
            });
            mapView.getOverlays().add(m);
        }
        mapView.invalidate();
    }

    private void addUserMarker() {
        GeoPoint p = new GeoPoint(userLat, userLon);
        Marker m = new Marker(mapView);
        m.setPosition(p);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        m.setTitle("Lokasi Anda");
        m.setSnippet("Ini adalah posisi HP Anda saat ini.");
        m.setIcon(getDrawableForUser());

        m.setOnMarkerClickListener((marker, mv) -> {
            showModernMarkerInfo(marker.getTitle(), marker.getSnippet());
            return true;
        });
        mapView.getOverlays().add(m);
        mapView.invalidate();
    }

    private void zoomToAllPoints() {
        List<GeoPoint> all = new ArrayList<>();
        all.add(new GeoPoint(gempaLat, gempaLon));
        all.addAll(affectedAreaPoints);
        if (userLat != 0.0 && userLon != 0.0) {
            all.add(new GeoPoint(userLat, userLon));
        }

        if (all.isEmpty()) return;

        double n = -90, s = 90, e = -180, w = 180;
        for (GeoPoint p : all) {
            n = Math.max(n, p.getLatitude());
            s = Math.min(s, p.getLatitude());
            e = Math.max(e, p.getLongitude());
            w = Math.min(w, p.getLongitude());
        }

        // Mencegah error bounding box jika titik berkumpul / cuma 1
        if (n == s && e == w) {
            mapView.getController().setZoom(6.5);
            mapView.getController().animateTo(new GeoPoint(n, e));
        } else {
            mapView.zoomToBoundingBox(new BoundingBox(n, e, s, w), true, 90);
        }
    }

    private void showModernMarkerInfo(String title, String snippet) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);
        layout.setBackgroundColor(Color.parseColor("#1A1A24"));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvSnippet = new TextView(this);
        tvSnippet.setText(snippet);
        tvSnippet.setTextSize(16);
        tvSnippet.setTextColor(Color.parseColor("#B0B0C0"));
        tvSnippet.setPadding(0, 16, 0, 0);

        layout.addView(tvTitle);
        layout.addView(tvSnippet);

        dialog.setContentView(layout);
        dialog.show();
    }

    // ==================== CALCULATION & PROCESSING ====================

    private void processSemuaDirasakanUntukRadius(String dirasakanRaw) {
        if (dirasakanRaw == null || dirasakanRaw.isEmpty() || dirasakanRaw.equals("-")) {
            runOnUiThread(this::tampilkanHasilAkhir);
            return;
        }

        String[] rawAreas = dirasakanRaw.split(",");
        List<String> cleanAreas = new ArrayList<>();
        for (String raw : rawAreas) {
            String clean = cleanDirasakanText(raw);
            if (!clean.isEmpty() && !cleanAreas.contains(clean)) cleanAreas.add(clean);
        }

        maxRadius = 0.0;
        if (cleanAreas.isEmpty()) {
            runOnUiThread(this::tampilkanHasilAkhir);
            return;
        }

        new Thread(() -> {
            Geocoder geocoder = new Geocoder(this);
            for (String daerah : cleanAreas) {
                cariKoordinatDaerah(geocoder, daerah);
            }
            runOnUiThread(this::tampilkanHasilAkhir);
        }).start();
    }

    private void cariKoordinatDaerah(Geocoder geocoder, String namaDaerah) {
        try {
            List<Address> addresses = geocoder.getFromLocationName(namaDaerah + ", Indonesia", 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                double lat = addr.getLatitude();
                double lon = addr.getLongitude();

                affectedAreaPoints.add(new GeoPoint(lat, lon));
                affectedAreaNames.add(namaDaerah);

                double jarakKm = hitungJarakHaversine(gempaLat, gempaLon, lat, lon);
                if (jarakKm > maxRadius) maxRadius = jarakKm;
            }
        } catch (IOException ignored) {}
    }

    private void tampilkanHasilAkhir() {
        if (maxRadius <= 0) {
            // Tetap jalankan logic meskipun tidak ada radius, agar marker & status tampil
        }

        double jarakUser = -1;
        if (userLat != 0.0 && userLon != 0.0) {
            jarakUser = hitungJarakHaversine(gempaLat, gempaLon, userLat, userLon);
        }

        double mag = 0;
        try { mag = Double.parseDouble(gempaMagnitude); } catch (NumberFormatException ignored) {}

        double batasLuarKuning = maxRadius + (mag * 10.0);

        String statusText;
        int statusColor;

        if (maxRadius == 0.0) {
            statusText = "INFO GEMPA";
            statusColor = Color.parseColor("#2979FF");
        } else if (jarakUser >= 0 && jarakUser <= maxRadius) {
            statusText = "TERDAMPAK";
            statusColor = Color.parseColor("#FF1744");
        } else if (jarakUser > maxRadius && jarakUser <= batasLuarKuning) {
            statusText = "WASPADA";
            statusColor = Color.parseColor("#FFC107");
        } else if (jarakUser > batasLuarKuning) {
            statusText = "AMAN";
            statusColor = Color.parseColor("#00E676");
        } else {
            statusText = "TAK DIKETAHUI";
            statusColor = Color.GRAY;
        }

        if (maxRadius > 0) {
            addRadiusCircles(mag);
            addAffectedAreaMarkers();
        }

        tvStatusBadge.setText(statusText);
        tvStatusBadge.setTextColor(statusColor);

        // Jika status TAK DIKETAHUI, buat tombolnya bisa diklik ulang untuk retry
        if (statusText.equals("TAK DIKETAHUI")) {
            tvStatusBadge.setAlpha(1.0f);
            tvStatusBadge.setText("TAK DIKETAHUI (KLIK UNTUK CARI)");
            tvStatusBadge.setOnClickListener(v -> {
                tvStatusBadge.setText("MENCARI...");
                checkLocationPermissionAndGet(); // Panggil ulang proses dari awal
            });
        } else {
            tvStatusBadge.setAlpha(1.0f);
            tvStatusBadge.setOnClickListener(null);
        }

        if (jarakUser >= 0) {
            addUserMarker();
            tvDistance.setText(String.format("%.0f km", jarakUser));
            saveToHistory(statusText, jarakUser);
        } else {
            tvDistance.setText("--");
        }

        zoomToAllPoints();
    }

    // ==================== HISTORY LOGIC ====================

    private void saveToHistory(String status, double jarak) {
        new Thread(() -> {
            if (database.gempaDao().getByDateTime(gempaDateTime) != null) return;

            GempaHistory h = new GempaHistory(
                    gempaTanggal, gempaJam, gempaDateTime, gempaCoordinates,
                    gempaMagnitude, gempaKedalaman, gempaWilayah, gempaPotensi,
                    gempaDirasakan, userLat, userLon, status, maxRadius, System.currentTimeMillis());
            database.gempaDao().insert(h);
        }).start();
    }

    private void showHistorySheet() {
        new Thread(() -> {
            List<GempaHistory> list = database.gempaDao().getAllHistory();
            runOnUiThread(() -> buildHistorySheet(list));
        }).start();
    }

    private void buildHistorySheet(List<GempaHistory> list) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_history, null);
        dialog.setContentView(sheet);

        TextView title = sheet.findViewById(R.id.sheet_title);
        View container = sheet.findViewById(R.id.sheet_container);

        if (list == null || list.isEmpty()) {
            title.setText("Belum ada riwayat");
            dialog.show();
            return;
        }

        final int[] xValue = {Math.min(5, list.size())};
        TextView tvXValue = sheet.findViewById(R.id.tv_x_value);
        MaterialButton btnMinus = sheet.findViewById(R.id.btn_minus_x);
        MaterialButton btnPlus = sheet.findViewById(R.id.btn_plus_x);
        MaterialButton btnShowMap = sheet.findViewById(R.id.btn_show_history_map);

        if(tvXValue != null) {
            tvXValue.setText(String.valueOf(xValue[0]));

            btnMinus.setOnClickListener(v -> {
                if(xValue[0] > 1) {
                    xValue[0]--;
                    tvXValue.setText(String.valueOf(xValue[0]));
                }
            });

            btnPlus.setOnClickListener(v -> {
                if(xValue[0] < list.size()) {
                    xValue[0]++;
                    tvXValue.setText(String.valueOf(xValue[0]));
                }
            });

            btnShowMap.setOnClickListener(v -> {
                dialog.dismiss();
                showHistoryOnMap(xValue[0], list);
            });
        }

        title.setText("Riwayat Gempa (" + list.size() + ")");

        for (GempaHistory h : list) {
            View item = getLayoutInflater().inflate(R.layout.item_history, null);

            ((TextView) item.findViewById(R.id.item_date)).setText(h.getTanggal() + " " + h.getJam());
            ((TextView) item.findViewById(R.id.item_mag)).setText("M" + h.getMagnitude());
            ((TextView) item.findViewById(R.id.item_location)).setText(h.getWilayah());
            ((TextView) item.findViewById(R.id.item_radius)).setText("Radius " + String.format("%.0f", h.getMaxRadiusKm()) + " km");

            TextView badge = item.findViewById(R.id.item_status);
            badge.setText(h.getStatus());
            int c = Color.GRAY;
            if (h.getStatus().contains("MERAH") || h.getStatus().contains("TERDAMPAK")) c = Color.parseColor("#FF1744");
            else if (h.getStatus().contains("KUNING") || h.getStatus().contains("WASPADA")) c = Color.parseColor("#FFC107");
            else if (h.getStatus().contains("HIJAU") || h.getStatus().contains("AMAN")) c = Color.parseColor("#00E676");
            badge.setTextColor(c);

            ((LinearLayout) container).addView(item);
        }

        dialog.show();
    }

    private void showHistoryOnMap(int limit, List<GempaHistory> allHistory) {
        mapView.getOverlays().clear();
        List<GeoPoint> points = new ArrayList<>();

        for(int i = 0; i < Math.min(limit, allHistory.size()); i++) {
            GempaHistory h = allHistory.get(i);

            if (h.getCoordinates() == null || !h.getCoordinates().contains(",")) continue;

            String[] sep = h.getCoordinates().split(",");
            if (sep.length == 2) {
                try {
                    double hLat = Double.parseDouble(sep[0]);
                    double hLon = Double.parseDouble(sep[1]);
                    GeoPoint p = new GeoPoint(hLat, hLon);
                    points.add(p);

                    Marker m = new Marker(mapView);
                    m.setPosition(p);
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                    m.setTitle("Gempa M" + h.getMagnitude());
                    m.setSnippet(h.getWilayah() + "\nWaktu: " + h.getTanggal() + " " + h.getJam());
                    m.setIcon(getDrawableForHistory());

                    m.setOnMarkerClickListener((marker, mv) -> {
                        showModernMarkerInfo(marker.getTitle(), marker.getSnippet());
                        return true;
                    });
                    mapView.getOverlays().add(m);
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
        mapView.invalidate();

        if(!points.isEmpty()){
            double n = -90, s = 90, e = -180, w = 180;
            for (GeoPoint p : points) {
                n = Math.max(n, p.getLatitude());
                s = Math.min(s, p.getLatitude());
                e = Math.max(e, p.getLongitude());
                w = Math.min(w, p.getLongitude());
            }

            if (n == s && e == w) {
                mapView.getController().setZoom(6.5);
                mapView.getController().animateTo(new GeoPoint(n, e));
            } else {
                mapView.zoomToBoundingBox(new BoundingBox(n, e, s, w), true, 100);
            }
        }

        tvLocation.setText("Menampilkan " + points.size() + " titik gempa sebelumnya.\nKlik teks AwasGempa di atas untuk kembali.");
        tvMagnitude.setText("--");
        tvDistance.setText("--");
        tvDatetime.setText("Mode Histori Peta");
        tvStatusBadge.setText("HISTORI");
        tvStatusBadge.setTextColor(Color.WHITE);
        tvStatusBadge.setAlpha(1.0f);
        tvStatusBadge.setOnClickListener(null);

        if (tvDataAge != null) {
            tvDataAge.setText("MODE PENINJAUAN");
            tvDataAge.setTextColor(Color.parseColor("#00E5FF"));
        }
    }

    // ==================== DRAWABLE UTILITIES ====================

    private android.graphics.drawable.Drawable getDrawableForEpicenter() {
        int s = 52;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.parseColor("#FF1744"));
        p.setStyle(android.graphics.Paint.Style.FILL);
        canvas.drawCircle(s / 2f, s / 2f, s / 2f - 2, p);
        p.setColor(Color.WHITE);
        p.setTextSize(26);
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        p.setFakeBoldText(true);
        canvas.drawText("!", s / 2f, s / 2f + 10, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
    }

    private android.graphics.drawable.Drawable getDrawableForPulse() {
        int s = 76;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.parseColor("#FF1744"));
        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(2.5f);
        canvas.drawCircle(s / 2f, s / 2f, s / 2f - 8, p);
        p.setAlpha(80);
        canvas.drawCircle(s / 2f, s / 2f, s / 2f - 4, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
    }

    private android.graphics.drawable.Drawable getDrawableForAffectedArea() {
        int s = 22;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.parseColor("#FFC107"));
        p.setStyle(android.graphics.Paint.Style.FILL);
        canvas.drawCircle(s / 2f, s / 2f, s / 2f - 2, p);
        p.setColor(Color.WHITE);
        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(2);
        canvas.drawCircle(s / 2f, s / 2f, s / 2f - 2, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
    }

    private android.graphics.drawable.Drawable getDrawableForUser() {
        int s = 34;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.parseColor("#2979FF"));
        p.setStyle(android.graphics.Paint.Style.FILL);
        canvas.drawCircle(s / 2f, s / 2f, s / 2f - 2, p);
        p.setColor(Color.WHITE);
        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(3);
        canvas.drawCircle(s / 2f, s / 2f, s / 2f - 6, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
    }

    private android.graphics.drawable.Drawable getDrawableForHistory() {
        int s = 24;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.parseColor("#FF1744"));
        p.setStyle(android.graphics.Paint.Style.FILL);
        canvas.drawCircle(s / 2f, s / 2f, s / 2f - 2, p);
        p.setColor(Color.WHITE);
        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(2);
        canvas.drawCircle(s / 2f, s / 2f, s / 2f - 2, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
    }

    // ==================== OTHER UTILITIES ====================

    private String cleanDirasakanText(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("-")) return "";
        String a = raw;
        a = a.replaceAll("(?i)\\bMMI\\b", "");
        a = a.replaceAll("(?i)\\b(VII|VI|IV|III|II|I|V)\\b", "");
        a = a.replace("-", "");
        return a.trim().replaceAll("\\s+", " ");
    }

    private double hitungJarakHaversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // UBAH INI:
                requestNewLocation();
            } else {
                fetchDataBMKG();
            }
        }
    }
}