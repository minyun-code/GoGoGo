package com.zcshou.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.zcshou.database.DataBaseHistoryLocation.*;
import static org.junit.Assert.*;

public class HistoryDatabaseTest {
    private SQLiteDatabase database;
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = SQLiteDatabase.create(null);
        new DataBaseHistoryLocation(context).onCreate(database);
        new DataBaseHistorySearch(context).onCreate(database);
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void reusingFavoritePreservesNameFavoriteAndId() {
        assertTrue(saveHistoryLocation(database, location("Home", 100, true)));
        long id = scalar("SELECT " + DB_COLUMN_ID + " FROM " + TABLE_NAME);
        assertTrue(saveHistoryLocation(database, location("Resolved address", 200, false)));
        assertEquals(1, scalar("SELECT COUNT(*) FROM " + TABLE_NAME));
        assertEquals(id, scalar("SELECT " + DB_COLUMN_ID + " FROM " + TABLE_NAME));
        assertEquals(1, scalar("SELECT " + DB_COLUMN_FAVORITE + " FROM " + TABLE_NAME));
        assertEquals(200, scalar("SELECT " + DB_COLUMN_TIMESTAMP + " FROM " + TABLE_NAME));
        assertEquals("Home", name());
    }

    @Test
    public void editedLabelSurvivesReuseAndLateAddressResponse() {
        assertTrue(saveHistoryLocation(database, location("Address", 100, false)));
        updateHistoryLocation(database, "1", "My label");
        assertTrue(saveHistoryLocation(database, location("New address", 200, false)));
        updateResolvedAddress(database, "116.397", "39.908", "Late address");
        assertEquals("My label", name());
    }

    @Test
    public void uneditedHistoryCanReceiveResolvedAddress() {
        assertTrue(saveHistoryLocation(database, location("Map location", 100, false)));
        updateResolvedAddress(database, "116.397", "39.908", "Resolved address");
        assertEquals("Resolved address", name());
    }

    @Test
    public void failedSaveRollsBackAndKeepsPreviousRecord() {
        assertTrue(saveHistoryLocation(database, location("Original", 100, true)));
        ContentValues invalid = location("Replacement", 200, false);
        invalid.putNull(DB_COLUMN_LATITUDE_CUSTOM);
        assertFalse(saveHistoryLocation(database, invalid));
        assertEquals("Original", name());
        assertEquals(100, scalar("SELECT " + DB_COLUMN_TIMESTAMP + " FROM " + TABLE_NAME));
        assertEquals(1, scalar("SELECT COUNT(*) FROM " + TABLE_NAME));
    }

    @Test
    public void versionOneMigrationKeepsExistingNamesAndCoordinates() {
        createLegacyLocation(false);
        database.execSQL("INSERT INTO HistoryLocation VALUES (7, 'Old label', '116.397', '39.908', 100, '116.4', '39.9')");
        new DataBaseHistoryLocation(context).onUpgrade(database, 1, 3);
        assertTrue(saveHistoryLocation(database, location("Replacement", 200, false)));
        assertEquals("Old label", name());
        assertEquals(7, scalar("SELECT " + DB_COLUMN_ID + " FROM " + TABLE_NAME));
        assertEquals(0, scalar("SELECT " + DB_COLUMN_FAVORITE + " FROM " + TABLE_NAME));
    }

    @Test
    public void versionTwoMigrationRetainsFavoriteWhenDeduplicating() {
        createLegacyLocation(true);
        database.execSQL("INSERT INTO HistoryLocation VALUES (1, 'Favorite', '116.397', '39.908', 100, '116.4', '39.9', 1)");
        database.execSQL("INSERT INTO HistoryLocation VALUES (2, 'Recent', '116.397', '39.908', 200, '116.4', '39.9', 0)");
        new DataBaseHistoryLocation(context).onUpgrade(database, 2, 3);
        assertEquals(1, scalar("SELECT COUNT(*) FROM " + TABLE_NAME));
        assertEquals("Favorite", name());
        assertThrows(android.database.sqlite.SQLiteException.class, () -> database.execSQL(
                "INSERT INTO HistoryLocation VALUES (3, 'Duplicate', '116.397', '39.908', 300, '116.4', '39.9', 0, 0)"));
    }

    @Test
    public void searchMigrationAndReusePreserveHistoryAndId() {
        database.execSQL("DROP INDEX HistorySearch_key");
        database.execSQL("INSERT INTO HistorySearch (DB_COLUMN_KEY, DB_COLUMN_DESCRIPTION, DB_COLUMN_TIMESTAMP, DB_COLUMN_IS_LOCATION) VALUES ('park', 'older', 100, 0)");
        database.execSQL("INSERT INTO HistorySearch (DB_COLUMN_KEY, DB_COLUMN_DESCRIPTION, DB_COLUMN_TIMESTAMP, DB_COLUMN_IS_LOCATION) VALUES ('park', 'newer', 200, 0)");
        database.execSQL("INSERT INTO HistorySearch (DB_COLUMN_KEY, DB_COLUMN_TIMESTAMP, DB_COLUMN_IS_LOCATION) VALUES ('station', 100, 0)");
        new DataBaseHistorySearch(context).onUpgrade(database, 1, 2);
        assertEquals(2, scalar("SELECT COUNT(*) FROM HistorySearch"));
        long id = scalar("SELECT DB_COLUMN_ID FROM HistorySearch WHERE DB_COLUMN_KEY = 'park'");
        ContentValues values = new ContentValues();
        values.put(DataBaseHistorySearch.DB_COLUMN_KEY, "park");
        values.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, "updated");
        values.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, 300L);
        values.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, 0);
        DataBaseHistorySearch.saveHistorySearch(database, values);
        assertEquals(id, scalar("SELECT DB_COLUMN_ID FROM HistorySearch WHERE DB_COLUMN_KEY = 'park'"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM HistorySearch"));
        values.putNull(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP);
        DataBaseHistorySearch.saveHistorySearch(database, values);
        assertEquals(300, scalar("SELECT DB_COLUMN_TIMESTAMP FROM HistorySearch WHERE DB_COLUMN_KEY = 'park'"));
    }

    private ContentValues location(String name, long timestamp, boolean favorite) {
        ContentValues values = new ContentValues();
        values.put(DB_COLUMN_LOCATION, name);
        values.put(DB_COLUMN_LONGITUDE_WGS84, "116.397");
        values.put(DB_COLUMN_LATITUDE_WGS84, "39.908");
        values.put(DB_COLUMN_TIMESTAMP, timestamp);
        values.put(DB_COLUMN_LONGITUDE_CUSTOM, "116.4");
        values.put(DB_COLUMN_LATITUDE_CUSTOM, "39.9");
        values.put(DB_COLUMN_FAVORITE, favorite ? 1 : 0);
        return values;
    }

    private void createLegacyLocation(boolean withFavorite) {
        database.execSQL("DROP TABLE HistoryLocation");
        database.execSQL("CREATE TABLE HistoryLocation (DB_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "DB_COLUMN_LOCATION TEXT, DB_COLUMN_LONGITUDE_WGS84 TEXT NOT NULL, DB_COLUMN_LATITUDE_WGS84 TEXT NOT NULL, "
                + "DB_COLUMN_TIMESTAMP BIGINT NOT NULL, DB_COLUMN_LONGITUDE_CUSTOM TEXT NOT NULL, DB_COLUMN_LATITUDE_CUSTOM TEXT NOT NULL"
                + (withFavorite ? ", DB_COLUMN_FAVORITE INTEGER NOT NULL DEFAULT 0" : "") + ")");
    }

    private long scalar(String sql) {
        try (Cursor cursor = database.rawQuery(sql, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private String name() {
        try (Cursor cursor = database.rawQuery("SELECT " + DB_COLUMN_LOCATION + " FROM " + TABLE_NAME, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }
}
