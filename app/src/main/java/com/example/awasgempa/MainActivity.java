package com.example.awasgempa;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
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
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;

import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private TextView tvMagnitude, tvStatusBadge, tvDistance, tvLocation;
    private MaterialCardView statusCard;
    private MaterialButton btnRefresh, btnHistory;
    private MapView mapView;
    private FusedLocationProviderClient fusedLocationClient;
    private RequestQueue requestQueue;
    private AppDatabase database;

    private double userLat = 0.0, userLon = 0.0;
    private double gempaLat = 0.0, gempaLon = 0.0;
    private double maxRadius = 0.0;

    private String gempaTanggal = "", gempaJam = "", gempaDateTime = "";
    private String gempaCoordinates = "", gempaMagnitude = "";
    private String gempaKedalaman = "", gempaWilayah = "";
    private String gempaPotensi = "", gempaDirasakan = "";

    private final List<GeoPoint> affectedAreaPoints = new ArrayList<>();

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

        tvMagnitude = findViewById(R.id.tv_magnitude);
        tvStatusBadge = findViewById(R.id.tv_status_badge);
        tvDistance = findViewById(R.id.tv_distance);
        tvLocation = findViewById(R.id.tv_location);
        statusCard = findViewById(R.id.status_card);
        btnHistory = findViewById(R.id.btn_history);
        btnRefresh = findViewById(R.id.btn_refresh);
        mapView = findViewById(R.id.map_view);

        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(5.5);
        mapView.getController().setCenter(new GeoPoint(-2.0, 118.0));
        mapView.setBackgroundColor(Color.parseColor("#0D0D1A"));

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestQueue = Volley.newRequestQueue(this);
        database = AppDatabase.getInstance(this);

        btnRefresh.setOnClickListener(v -> startDataloadingFlow());
        btnHistory.setOnClickListener(v -> showHistorySheet());
        startDataloadingFlow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    private void startDataloadingFlow() {
        tvMagnitude.setText("--");
        tvStatusBadge.setText("LOADING");
        tvStatusBadge.setTextColor(Color.parseColor("#B0B0C0"));
        tvDistance.setText("--");
        tvLocation.setText("Menunggu data...");
        mapView.getOverlays().clear();
        affectedAreaPoints.clear();
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
                    } else {
                        userLat = 0.0;
                        userLon = 0.0;
                    }
                    fetchDataBMKG();
                }
            });
        } catch (SecurityException e) {
            fetchDataBMKG();
        }
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
                            gempaDirasakan = gempaObj.getString("Dirasakan");
                            gempaCoordinates = gempaObj.getString("Coordinates");
                            gempaKedalaman = gempaObj.getString("Kedalaman");
                            gempaWilayah = gempaObj.getString("Wilayah");
                            gempaPotensi = gempaObj.optString("Potensi", "");

                            String[] sep = gempaCoordinates.split(",");
                            if (sep.length == 2) {
                                gempaLat = Double.parseDouble(sep[0]);
                                gempaLon = Double.parseDouble(sep[1]);
                            }

                            tvMagnitude.setText(gempaMagnitude);
                            tvLocation.setText(gempaWilayah);

                            addEpicenterMarker();
                            processSemuaDirasakanUntukRadius(gempaDirasakan);

                        } catch (JSONException e) {
                            tvLocation.setText("Gagal parse data BMKG");
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                tvLocation.setText("Gagal ambil data: " + error.getMessage());
            }
        });

        requestQueue.add(jsonObjectRequest);
    }

    // ==================== MARKERS ====================

    private void addEpicenterMarker() {
        GeoPoint epicenter = new GeoPoint(gempaLat, gempaLon);

        Marker marker = new Marker(mapView);
        marker.setPosition(epicenter);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setTitle("Pusat Gempa — M" + gempaMagnitude);
        marker.setSnippet(gempaWilayah + "\n" + gempaKedalaman);
        marker.setIcon(getDrawableForEpicenter());
        mapView.getOverlays().add(marker);

        Marker pulse = new Marker(mapView);
        pulse.setPosition(epicenter);
        pulse.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        pulse.setIcon(getDrawableForPulse());
        pulse.setAlpha(0.35f);
        mapView.getOverlays().add(pulse);

        mapView.invalidate();
    }

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

    // ==================== GEMPA LOGIC ====================

    private void processSemuaDirasakanUntukRadius(String dirasakanRaw) {
        if (dirasakanRaw == null || dirasakanRaw.isEmpty() || dirasakanRaw.equals("-")) return;

        String[] rawAreas = dirasakanRaw.split(",");
        List<String> cleanAreas = new ArrayList<>();
        for (String raw : rawAreas) {
            String clean = cleanDirasakanText(raw);
            if (!clean.isEmpty() && !cleanAreas.contains(clean)) cleanAreas.add(clean);
        }

        maxRadius = 0.0;
        if (cleanAreas.isEmpty()) return;

        new Thread(() -> {
            Geocoder geocoder = new Geocoder(this);
            for (String daerah : cleanAreas) cariKoordinatDaerah(geocoder, daerah);
            runOnUiThread(() -> tampilkanHasilAkhir());
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
                double jarakKm = hitungJarakHaversine(gempaLat, gempaLon, lat, lon);
                if (jarakKm > maxRadius) maxRadius = jarakKm;
            }
        } catch (IOException ignored) {}
    }

    private void tampilkanHasilAkhir() {
        if (maxRadius <= 0) { tvLocation.setText("Gagal hitung radius"); return; }

        double jarakUser = -1;
        if (userLat != 0.0 && userLon != 0.0)
            jarakUser = hitungJarakHaversine(gempaLat, gempaLon, userLat, userLon);

        double mag = 0;
        try { mag = Double.parseDouble(gempaMagnitude); } catch (NumberFormatException ignored) {}

        String statusText;
        int statusColor;
        if (jarakUser >= 0 && jarakUser <= maxRadius && mag >= 5.0) {
            statusText = "TERDAMPAK"; statusColor = Color.parseColor("#FF1744");
        } else if (jarakUser >= 0 && jarakUser <= maxRadius) {
            statusText = "WASPADA"; statusColor = Color.parseColor("#FFC107");
        } else if (jarakUser >= 0) {
            statusText = "AMAN"; statusColor = Color.parseColor("#00E676");
        } else {
            statusText = "TAK DIKETAHUI"; statusColor = Color.GRAY;
        }

        addRadiusCircle(statusColor);
        addAffectedAreaMarkers();

        tvStatusBadge.setText(statusText);
        tvStatusBadge.setTextColor(statusColor);

        if (jarakUser >= 0) {
            addUserMarker();
            tvDistance.setText(String.format("%.0f km", jarakUser));
            saveToHistory(statusText, jarakUser);
        } else {
            tvDistance.setText("--");
        }

        zoomToAllPoints();
    }

    // ==================== MAP HELPERS ====================

    private void addRadiusCircle(int statusColor) {
        GeoPoint center = new GeoPoint(gempaLat, gempaLon);
        Polygon circle = new Polygon();
        circle.setPoints(Polygon.pointsAsCircle(center, maxRadius * 1000));
        int r = Color.red(statusColor), g = Color.green(statusColor), b = Color.blue(statusColor);
        circle.setFillColor(Color.argb(25, r, g, b));
        circle.setStrokeColor(Color.argb(160, r, g, b));
        circle.setStrokeWidth(3);
        mapView.getOverlays().add(circle);
        mapView.invalidate();
    }

    private void addAffectedAreaMarkers() {
        for (GeoPoint point : affectedAreaPoints) {
            Marker m = new Marker(mapView);
            m.setPosition(point);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            m.setIcon(getDrawableForAffectedArea());
            mapView.getOverlays().add(m);
        }
        mapView.invalidate();
    }

    private void addUserMarker() {
        GeoPoint p = new GeoPoint(userLat, userLon);
        Marker m = new Marker(mapView);
        m.setPosition(p);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        m.setTitle("Lokasi Saya");
        m.setIcon(getDrawableForUser());
        mapView.getOverlays().add(m);
        mapView.invalidate();
    }

    private void zoomToAllPoints() {
        List<GeoPoint> all = new ArrayList<>();
        all.add(new GeoPoint(gempaLat, gempaLon));
        all.addAll(affectedAreaPoints);
        if (userLat != 0.0 && userLon != 0.0)
            all.add(new GeoPoint(userLat, userLon));

        double n = -90, s = 90, e = -180, w = 180;
        for (GeoPoint p : all) {
            n = Math.max(n, p.getLatitude());
            s = Math.min(s, p.getLatitude());
            e = Math.max(e, p.getLongitude());
            w = Math.min(w, p.getLongitude());
        }
        mapView.zoomToBoundingBox(new BoundingBox(n, e, s, w), true, 90);
    }

    // ==================== HISTORY ====================

    private void saveToHistory(String status, double jarak) {
        GempaHistory h = new GempaHistory(
                gempaTanggal, gempaJam, gempaDateTime, gempaCoordinates,
                gempaMagnitude, gempaKedalaman, gempaWilayah, gempaPotensi,
                gempaDirasakan, userLat, userLon, status, maxRadius, System.currentTimeMillis());
        new Thread(() -> {
            if (database.gempaDao().getByDateTime(gempaDateTime) == null)
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

        if (list.isEmpty()) {
            title.setText("Belum ada history");
            dialog.show();
            return;
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

    // ==================== UTILITIES ====================

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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                getLastLocation();
            else
                fetchDataBMKG();
        }
    }
}
