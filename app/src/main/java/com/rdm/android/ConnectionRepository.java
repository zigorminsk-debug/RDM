package com.rdm.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local profile store. Password blobs are encrypted separately by {@link PasswordVault}. */
final class ConnectionRepository extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "rdm-connections.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE = "connections";

    ConnectionRepository(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "hostname TEXT NOT NULL,"
                + "port INTEGER NOT NULL DEFAULT 3389,"
                + "username TEXT NOT NULL DEFAULT '',"
                + "domain TEXT NOT NULL DEFAULT '',"
                + "password_encrypted BLOB,"
                + "is_favorite INTEGER NOT NULL DEFAULT 0,"
                + "last_connected_at INTEGER NOT NULL DEFAULT 0"
                + ")");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Keep user profiles when a future schema is introduced; add explicit migrations here.
    }

    List<ConnectionProfile> list(String filter) {
        ArrayList<ConnectionProfile> profiles = new ArrayList<>();
        String orderBy = "is_favorite DESC, last_connected_at DESC, name COLLATE NOCASE ASC";
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, null, null, null, null,
                orderBy)) {
            String query = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
            while (cursor.moveToNext()) {
                ConnectionProfile profile = readProfile(cursor);
                if (query.isEmpty() || contains(profile.name, query) || contains(profile.hostname, query)
                        || contains(profile.username, query) || contains(profile.domain, query)) {
                    profiles.add(profile);
                }
            }
        }
        return profiles;
    }

    ConnectionProfile get(long id, boolean includePassword)
            throws GeneralSecurityException, IOException {
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, "_id=?",
                new String[]{String.valueOf(id)}, null, null, null, "1")) {
            if (!cursor.moveToFirst()) return null;
            ConnectionProfile profile = readProfile(cursor);
            if (includePassword && profile.passwordSaved) {
                profile.password = PasswordVault.decrypt(cursor.getBlob(
                        cursor.getColumnIndexOrThrow("password_encrypted")));
            }
            return profile;
        }
    }

    long save(ConnectionProfile profile, boolean rememberPassword)
            throws GeneralSecurityException, IOException {
        ContentValues values = new ContentValues();
        values.put("name", profile.name.trim());
        values.put("hostname", profile.hostname.trim());
        values.put("port", profile.port);
        values.put("username", safe(profile.username));
        values.put("domain", safe(profile.domain));
        values.put("is_favorite", profile.favorite ? 1 : 0);
        values.put("last_connected_at", profile.lastConnectedAt);

        String password = profile.password == null ? "" : profile.password;
        byte[] encryptedPassword = null;
        if (rememberPassword && !password.isEmpty()) {
            encryptedPassword = PasswordVault.encrypt(password);
        }
        if (encryptedPassword == null) values.putNull("password_encrypted");
        else values.put("password_encrypted", encryptedPassword);
        profile.passwordSaved = encryptedPassword != null;

        SQLiteDatabase db = getWritableDatabase();
        if (profile.id > 0) {
            int updated = db.update(TABLE, values, "_id=?",
                    new String[]{String.valueOf(profile.id)});
            if (updated > 0) return profile.id;
        }
        long id = db.insertOrThrow(TABLE, null, values);
        profile.id = id;
        return id;
    }

    void setFavorite(long id, boolean favorite) {
        ContentValues values = new ContentValues();
        values.put("is_favorite", favorite ? 1 : 0);
        getWritableDatabase().update(TABLE, values, "_id=?",
                new String[]{String.valueOf(id)});
    }

    void markConnected(long id) {
        ContentValues values = new ContentValues();
        values.put("last_connected_at", System.currentTimeMillis());
        getWritableDatabase().update(TABLE, values, "_id=?",
                new String[]{String.valueOf(id)});
    }

    void delete(long id) {
        getWritableDatabase().delete(TABLE, "_id=?", new String[]{String.valueOf(id)});
    }

    private static ConnectionProfile readProfile(Cursor cursor) {
        ConnectionProfile profile = new ConnectionProfile();
        profile.id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
        profile.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        profile.hostname = cursor.getString(cursor.getColumnIndexOrThrow("hostname"));
        profile.port = cursor.getInt(cursor.getColumnIndexOrThrow("port"));
        profile.username = cursor.getString(cursor.getColumnIndexOrThrow("username"));
        profile.domain = cursor.getString(cursor.getColumnIndexOrThrow("domain"));
        profile.favorite = cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")) == 1;
        profile.lastConnectedAt = cursor.getLong(cursor.getColumnIndexOrThrow("last_connected_at"));
        byte[] encryptedPassword = cursor.getBlob(cursor.getColumnIndexOrThrow("password_encrypted"));
        profile.passwordSaved = encryptedPassword != null && encryptedPassword.length > 0;
        profile.password = "";
        return profile;
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
