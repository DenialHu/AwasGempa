package com.example.awasgempa;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "gempa_history")
public class GempaHistory {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "tanggal")
    private String tanggal;

    @ColumnInfo(name = "jam")
    private String jam;

    @ColumnInfo(name = "date_time")
    private String dateTime;

    @ColumnInfo(name = "coordinates")
    private String coordinates;

    @ColumnInfo(name = "magnitude")
    private String magnitude;

    @ColumnInfo(name = "kedalaman")
    private String kedalaman;

    @ColumnInfo(name = "wilayah")
    private String wilayah;

    @ColumnInfo(name = "potensi")
    private String potensi;

    @ColumnInfo(name = "dirasakan")
    private String dirasakan;

    @ColumnInfo(name = "user_lat")
    private double userLat;

    @ColumnInfo(name = "user_lon")
    private double userLon;

    @ColumnInfo(name = "status")
    private String status;

    @ColumnInfo(name = "max_radius_km")
    private double maxRadiusKm;

    @ColumnInfo(name = "saved_at")
    private long savedAt;

    public GempaHistory(String tanggal, String jam, String dateTime, String coordinates,
                        String magnitude, String kedalaman, String wilayah, String potensi,
                        String dirasakan, double userLat, double userLon,
                        String status, double maxRadiusKm, long savedAt) {
        this.tanggal = tanggal;
        this.jam = jam;
        this.dateTime = dateTime;
        this.coordinates = coordinates;
        this.magnitude = magnitude;
        this.kedalaman = kedalaman;
        this.wilayah = wilayah;
        this.potensi = potensi;
        this.dirasakan = dirasakan;
        this.userLat = userLat;
        this.userLon = userLon;
        this.status = status;
        this.maxRadiusKm = maxRadiusKm;
        this.savedAt = savedAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTanggal() { return tanggal; }
    public void setTanggal(String tanggal) { this.tanggal = tanggal; }

    public String getJam() { return jam; }
    public void setJam(String jam) { this.jam = jam; }

    public String getDateTime() { return dateTime; }
    public void setDateTime(String dateTime) { this.dateTime = dateTime; }

    public String getCoordinates() { return coordinates; }
    public void setCoordinates(String coordinates) { this.coordinates = coordinates; }

    public String getMagnitude() { return magnitude; }
    public void setMagnitude(String magnitude) { this.magnitude = magnitude; }

    public String getKedalaman() { return kedalaman; }
    public void setKedalaman(String kedalaman) { this.kedalaman = kedalaman; }

    public String getWilayah() { return wilayah; }
    public void setWilayah(String wilayah) { this.wilayah = wilayah; }

    public String getPotensi() { return potensi; }
    public void setPotensi(String potensi) { this.potensi = potensi; }

    public String getDirasakan() { return dirasakan; }
    public void setDirasakan(String dirasakan) { this.dirasakan = dirasakan; }

    public double getUserLat() { return userLat; }
    public void setUserLat(double userLat) { this.userLat = userLat; }

    public double getUserLon() { return userLon; }
    public void setUserLon(double userLon) { this.userLon = userLon; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getMaxRadiusKm() { return maxRadiusKm; }
    public void setMaxRadiusKm(double maxRadiusKm) { this.maxRadiusKm = maxRadiusKm; }

    public long getSavedAt() { return savedAt; }
    public void setSavedAt(long savedAt) { this.savedAt = savedAt; }
}
