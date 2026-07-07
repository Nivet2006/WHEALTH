package com.nived.whealth;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class HealthDb extends SQLiteOpenHelper {
    static final String[] DEFAULT_MODULES = {
            "Blood Pressure", "Blood Sugar", "Weight", "Heart Rate", "Oxygen",
            "Temperature", "Medicines", "Water", "Sleep", "Custom"
    };

    HealthDb(Context context) {
        super(context, "whealth.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE profile (" +
                "id INTEGER PRIMARY KEY CHECK(id = 1), " +
                "name TEXT NOT NULL, dob TEXT, gender TEXT, height TEXT, phone TEXT NOT NULL, " +
                "emergency_name TEXT NOT NULL, emergency_phone TEXT NOT NULL, blood_group TEXT, " +
                "conditions TEXT, allergies TEXT, medications TEXT, doctor TEXT)");
        db.execSQL("CREATE TABLE enabled_modules (name TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE records (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, notes TEXT, image_uri TEXT, " +
                "source TEXT NOT NULL, confidence INTEGER, recorded_at INTEGER NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE measurements (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, record_id INTEGER NOT NULL, field_name TEXT NOT NULL, " +
                "value REAL NOT NULL, unit TEXT NOT NULL, FOREIGN KEY(record_id) REFERENCES records(id) ON DELETE CASCADE)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS measurements");
        db.execSQL("DROP TABLE IF EXISTS records");
        db.execSQL("DROP TABLE IF EXISTS enabled_modules");
        db.execSQL("DROP TABLE IF EXISTS profile");
        onCreate(db);
    }

    boolean hasProfile() {
        Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM profile WHERE id = 1", null);
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    Map<String, String> profile() {
        Map<String, String> profile = new LinkedHashMap<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM profile WHERE id = 1", null);
        if (c.moveToFirst()) {
            for (int i = 0; i < c.getColumnCount(); i++) {
                profile.put(c.getColumnName(i), c.getString(i));
            }
        }
        c.close();
        return profile;
    }

    void saveProfile(Map<String, String> values, List<String> modules) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put("id", 1);
            cv.put("name", values.get("name"));
            cv.put("dob", values.get("dob"));
            cv.put("gender", values.get("gender"));
            cv.put("height", values.get("height"));
            cv.put("phone", values.get("phone"));
            cv.put("emergency_name", values.get("emergency_name"));
            cv.put("emergency_phone", values.get("emergency_phone"));
            cv.put("blood_group", values.get("blood_group"));
            cv.put("conditions", values.get("conditions"));
            cv.put("allergies", values.get("allergies"));
            cv.put("medications", values.get("medications"));
            cv.put("doctor", values.get("doctor"));
            db.insertWithOnConflict("profile", null, cv, SQLiteDatabase.CONFLICT_REPLACE);

            db.delete("enabled_modules", null, null);
            for (String module : modules) {
                ContentValues row = new ContentValues();
                row.put("name", module);
                db.insert("enabled_modules", null, row);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    List<String> enabledModules() {
        List<String> modules = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT name FROM enabled_modules ORDER BY rowid", null);
        while (c.moveToNext()) {
            modules.add(c.getString(0));
        }
        c.close();
        if (modules.isEmpty()) {
            modules.add("Blood Pressure");
            modules.add("Blood Sugar");
            modules.add("Weight");
        }
        return modules;
    }

    long saveRecord(String type, Map<String, Double> values, String unit, String notes, String imageUri, String source, int confidence) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            ContentValues record = new ContentValues();
            record.put("type", type);
            record.put("notes", notes);
            record.put("image_uri", imageUri);
            record.put("source", source);
            record.put("confidence", confidence);
            record.put("recorded_at", now);
            record.put("created_at", now);
            long id = db.insertOrThrow("records", null, record);

            for (Map.Entry<String, Double> value : values.entrySet()) {
                ContentValues measurement = new ContentValues();
                measurement.put("record_id", id);
                measurement.put("field_name", value.getKey());
                measurement.put("value", value.getValue());
                measurement.put("unit", unitFor(type, value.getKey(), unit));
                db.insertOrThrow("measurements", null, measurement);
            }
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    List<RecordSummary> records(String type, int limit) {
        List<RecordSummary> records = new ArrayList<>();
        String sql = "SELECT id, type, notes, image_uri, source, confidence, recorded_at FROM records " +
                (type == null ? "" : "WHERE type = ? ") + "ORDER BY recorded_at DESC LIMIT ?";
        String[] args = type == null ? new String[]{String.valueOf(limit)} : new String[]{type, String.valueOf(limit)};
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) {
            long id = c.getLong(0);
            records.add(new RecordSummary(
                    id,
                    c.getString(1),
                    c.getString(2),
                    c.getString(3),
                    c.getString(4),
                    c.getInt(5),
                    c.getLong(6),
                    measurementsFor(id)
            ));
        }
        c.close();
        return records;
    }

    RecordSummary latest(String type) {
        List<RecordSummary> records = records(type, 1);
        return records.isEmpty() ? null : records.get(0);
    }

    private Map<String, String> measurementsFor(long recordId) {
        Map<String, String> values = new LinkedHashMap<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT field_name, value, unit FROM measurements WHERE record_id = ? ORDER BY id",
                new String[]{String.valueOf(recordId)}
        );
        while (c.moveToNext()) {
            values.put(c.getString(0), prettyNumber(c.getDouble(1)) + " " + c.getString(2));
        }
        c.close();
        return values;
    }

    private static String unitFor(String type, String field, String fallback) {
        if ("Blood Pressure".equals(type) && ("SYS".equals(field) || "DIA".equals(field))) return "mmHg";
        if ("Heart Rate".equals(type) || "Pulse".equals(field)) return "bpm";
        if ("Blood Sugar".equals(type)) return "mg/dL";
        if ("Weight".equals(type)) return "kg";
        if ("Oxygen".equals(type)) return "%";
        if ("Temperature".equals(type)) return "°C";
        if ("Water".equals(type)) return "glasses";
        if ("Sleep".equals(type)) return "hours";
        return fallback == null || fallback.isEmpty() ? "count" : fallback;
    }

    static String prettyNumber(double value) {
        if (value == Math.rint(value)) return String.valueOf((long) value);
        return String.format(Locale.US, "%.1f", value);
    }

    static String dateTime(long millis) {
        return new SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(new Date(millis));
    }

    static final class RecordSummary {
        final long id;
        final String type;
        final String notes;
        final String imageUri;
        final String source;
        final int confidence;
        final long recordedAt;
        final Map<String, String> measurements;

        RecordSummary(long id, String type, String notes, String imageUri, String source, int confidence, long recordedAt, Map<String, String> measurements) {
            this.id = id;
            this.type = type;
            this.notes = notes;
            this.imageUri = imageUri;
            this.source = source;
            this.confidence = confidence;
            this.recordedAt = recordedAt;
            this.measurements = measurements;
        }

        String displayValue() {
            if ("Blood Pressure".equals(type) && measurements.containsKey("SYS") && measurements.containsKey("DIA")) {
                return measurements.get("SYS").replace(" mmHg", "") + "/" + measurements.get("DIA").replace(" mmHg", "");
            }
            return measurements.isEmpty() ? "No values" : measurements.values().iterator().next();
        }
    }
}
