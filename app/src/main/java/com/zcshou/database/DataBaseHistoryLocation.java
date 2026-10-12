package com.zcshou.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteException;

import com.elvishew.xlog.XLog;
import com.zcshou.utils.MapUtils;

public class DataBaseHistoryLocation extends SQLiteOpenHelper {
    public static final String TABLE_NAME = "HistoryLocation";
    public static final String DB_COLUMN_ID = "DB_COLUMN_ID";
    public static final String DB_COLUMN_LOCATION = "DB_COLUMN_LOCATION";
    public static final String DB_COLUMN_LONGITUDE_WGS84 = "DB_COLUMN_LONGITUDE_WGS84";
    public static final String DB_COLUMN_LATITUDE_WGS84 = "DB_COLUMN_LATITUDE_WGS84";
    public static final String DB_COLUMN_TIMESTAMP = "DB_COLUMN_TIMESTAMP";
    public static final String DB_COLUMN_LONGITUDE_CUSTOM = "DB_COLUMN_LONGITUDE_CUSTOM";
    public static final String DB_COLUMN_LATITUDE_CUSTOM = "DB_COLUMN_LATITUDE_CUSTOM";
    public static final String DB_COLUMN_FAVORITE = "DB_COLUMN_FAVORITE";
    public static final String DB_COLUMN_NAME_CUSTOMIZED = "DB_COLUMN_NAME_CUSTOMIZED";

    private static final int DB_VERSION = 3;
    private static final String DB_NAME = "HistoryLocation.db";
    private static final String CREATE_TABLE = "create table if not exists " + TABLE_NAME +
            " (DB_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, DB_COLUMN_LOCATION TEXT, " +
            "DB_COLUMN_LONGITUDE_WGS84 TEXT NOT NULL, DB_COLUMN_LATITUDE_WGS84 TEXT NOT NULL, " +
            "DB_COLUMN_TIMESTAMP BIGINT NOT NULL, DB_COLUMN_LONGITUDE_CUSTOM TEXT NOT NULL, " +
            "DB_COLUMN_LATITUDE_CUSTOM TEXT NOT NULL, DB_COLUMN_FAVORITE INTEGER NOT NULL DEFAULT 0, " +
            "DB_COLUMN_NAME_CUSTOMIZED INTEGER NOT NULL DEFAULT 0)";
    private static final String CREATE_UNIQUE_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS " +
            "HistoryLocation_coordinates ON " + TABLE_NAME + " (" +
            DB_COLUMN_LONGITUDE_WGS84 + ", " + DB_COLUMN_LATITUDE_WGS84 + ")";
            
    public DataBaseHistoryLocation(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE);
        sqLiteDatabase.execSQL(CREATE_UNIQUE_INDEX);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN "
                    + DB_COLUMN_FAVORITE + " INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) {
            sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN "
                    + DB_COLUMN_NAME_CUSTOMIZED + " INTEGER NOT NULL DEFAULT 0");
            // Old versions did not distinguish user labels from resolved addresses; preserve both.
            sqLiteDatabase.execSQL("UPDATE " + TABLE_NAME + " SET " + DB_COLUMN_NAME_CUSTOMIZED
                    + " = 1 WHERE " + DB_COLUMN_LOCATION + " IS NOT NULL AND " + DB_COLUMN_LOCATION + " <> ''");
            sqLiteDatabase.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + DB_COLUMN_ID
                    + " <> (SELECT kept." + DB_COLUMN_ID + " FROM " + TABLE_NAME + " AS kept WHERE kept."
                    + DB_COLUMN_LONGITUDE_WGS84 + " = " + TABLE_NAME + "." + DB_COLUMN_LONGITUDE_WGS84
                    + " AND kept." + DB_COLUMN_LATITUDE_WGS84 + " = " + TABLE_NAME + "." + DB_COLUMN_LATITUDE_WGS84
                    + " ORDER BY kept." + DB_COLUMN_FAVORITE + " DESC, kept." + DB_COLUMN_TIMESTAMP
                    + " DESC, kept." + DB_COLUMN_ID + " DESC LIMIT 1)");
            sqLiteDatabase.execSQL(CREATE_UNIQUE_INDEX);
        }
    }

    // 保存选择的位置
    public static boolean saveHistoryLocation(SQLiteDatabase sqLiteDatabase, ContentValues contentValues) {
        try {
            sqLiteDatabase.beginTransaction();
            try {
                ContentValues values = new ContentValues(contentValues);
                values.remove(DB_COLUMN_ID);
                String selection = DB_COLUMN_LONGITUDE_WGS84 + " = ? AND " + DB_COLUMN_LATITUDE_WGS84 + " = ?";
                String[] coordinates = {values.getAsString(DB_COLUMN_LONGITUDE_WGS84),
                        values.getAsString(DB_COLUMN_LATITUDE_WGS84)};
                try (Cursor cursor = sqLiteDatabase.query(TABLE_NAME,
                        new String[] {DB_COLUMN_ID, DB_COLUMN_LOCATION, DB_COLUMN_FAVORITE, DB_COLUMN_NAME_CUSTOMIZED},
                        selection, coordinates, null, null, null, "1")) {
                    if (cursor.moveToFirst()) {
                        boolean favorite = cursor.getInt(2) == 1;
                        boolean customized = cursor.getInt(3) == 1;
                        if (favorite) {
                            values.put(DB_COLUMN_FAVORITE, 1);
                        }
                        if ((favorite || customized) && cursor.getString(1) != null) {
                            values.put(DB_COLUMN_LOCATION, cursor.getString(1));
                        }
                        values.put(DB_COLUMN_NAME_CUSTOMIZED, customized ? 1 : 0);
                        if (sqLiteDatabase.update(TABLE_NAME, values, DB_COLUMN_ID + " = ?",
                                new String[] {cursor.getString(0)}) != 1) {
                            throw new SQLiteException("History record was not updated");
                        }
                    } else {
                        sqLiteDatabase.insertOrThrow(TABLE_NAME, null, values);
                    }
                }
                sqLiteDatabase.setTransactionSuccessful();
                return true;
            } finally {
                sqLiteDatabase.endTransaction();
            }
        } catch (Exception e) {
            XLog.e("DATABASE: save location error", e);
            return false;
        }
    }

    /**
     * 将百度坐标地点保存为可复用的本地定位记录。
     */
    public static boolean saveLocation(SQLiteDatabase sqLiteDatabase, String location,
                                       double longitudeBd09, double latitudeBd09,
                                       boolean favorite) {
        double[] wgs84 = MapUtils.bd2wgs(longitudeBd09, latitudeBd09);
        ContentValues contentValues = new ContentValues();
        contentValues.put(DB_COLUMN_LOCATION, location);
        contentValues.put(DB_COLUMN_LONGITUDE_WGS84, String.valueOf(wgs84[0]));
        contentValues.put(DB_COLUMN_LATITUDE_WGS84, String.valueOf(wgs84[1]));
        contentValues.put(DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
        contentValues.put(DB_COLUMN_LONGITUDE_CUSTOM, String.valueOf(longitudeBd09));
        contentValues.put(DB_COLUMN_LATITUDE_CUSTOM, String.valueOf(latitudeBd09));
        contentValues.put(DB_COLUMN_FAVORITE, favorite ? 1 : 0);
        return saveHistoryLocation(sqLiteDatabase, contentValues);
    }

    // Resolve an address only while the record still has an automatically generated name.
    public static void updateResolvedAddress(SQLiteDatabase database, String longitude, String latitude, String address) {
        ContentValues values = new ContentValues();
        values.put(DB_COLUMN_LOCATION, address);
        database.update(TABLE_NAME, values,
                DB_COLUMN_LONGITUDE_WGS84 + " = ? AND " + DB_COLUMN_LATITUDE_WGS84 + " = ? AND "
                        + DB_COLUMN_FAVORITE + " = 0 AND " + DB_COLUMN_NAME_CUSTOMIZED + " = 0",
                new String[] {longitude, latitude});
    }

    // 修改历史记录名称
    public static void updateHistoryLocation(SQLiteDatabase sqLiteDatabase, String locID, String location) {
        try{
            ContentValues contentValues = new ContentValues();
            contentValues.put(DB_COLUMN_LOCATION, location);
            contentValues.put(DB_COLUMN_NAME_CUSTOMIZED, 1);
            sqLiteDatabase.update(TABLE_NAME, contentValues, DB_COLUMN_ID + " = ?", new String[]{locID});
        } catch (Exception e){
            XLog.e("DATABASE: update error");
        }
    }
}
