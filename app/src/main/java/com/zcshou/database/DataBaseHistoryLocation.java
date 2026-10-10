package com.zcshou.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

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

    private static final int DB_VERSION = 2;
    private static final String DB_NAME = "HistoryLocation.db";
    private static final String CREATE_TABLE = "create table if not exists " + TABLE_NAME +
            " (DB_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, DB_COLUMN_LOCATION TEXT, " +
            "DB_COLUMN_LONGITUDE_WGS84 TEXT NOT NULL, DB_COLUMN_LATITUDE_WGS84 TEXT NOT NULL, " +
            "DB_COLUMN_TIMESTAMP BIGINT NOT NULL, DB_COLUMN_LONGITUDE_CUSTOM TEXT NOT NULL, " +
            "DB_COLUMN_LATITUDE_CUSTOM TEXT NOT NULL, DB_COLUMN_FAVORITE INTEGER NOT NULL DEFAULT 0)";
            
    public DataBaseHistoryLocation(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN "
                    + DB_COLUMN_FAVORITE + " INTEGER NOT NULL DEFAULT 0");
        }
    }

    // 保存选择的位置
    public static boolean saveHistoryLocation(SQLiteDatabase sqLiteDatabase, ContentValues contentValues) {
        try {
            // 先删除原来的记录，再插入新记录
            String longitudeWgs84 = contentValues.getAsString(DB_COLUMN_LONGITUDE_WGS84);
            String latitudeWgs84 = contentValues.getAsString(DB_COLUMN_LATITUDE_WGS84);
            Integer favorite = contentValues.getAsInteger(DB_COLUMN_FAVORITE);
            if ((favorite == null || favorite == 0)
                    && isFavorite(sqLiteDatabase, longitudeWgs84, latitudeWgs84)) {
                contentValues.put(DB_COLUMN_FAVORITE, 1);
            }
            sqLiteDatabase.delete(TABLE_NAME,
                            DB_COLUMN_LONGITUDE_WGS84 + " = ? AND " +
                            DB_COLUMN_LATITUDE_WGS84 + " = ?",
                    new String[] {longitudeWgs84, latitudeWgs84});
            return sqLiteDatabase.insert(TABLE_NAME, null, contentValues) != -1;
        } catch (Exception e) {
            XLog.e("DATABASE: insert error");
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

    private static boolean isFavorite(SQLiteDatabase sqLiteDatabase,
                                      String longitudeWgs84, String latitudeWgs84) {
        try (Cursor cursor = sqLiteDatabase.query(
                TABLE_NAME,
                new String[] {DB_COLUMN_FAVORITE},
                DB_COLUMN_LONGITUDE_WGS84 + " = ? AND " + DB_COLUMN_LATITUDE_WGS84 + " = ?",
                new String[] {longitudeWgs84, latitudeWgs84},
                null, null, null, "1")) {
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    // 修改历史记录名称
    public static void updateHistoryLocation(SQLiteDatabase sqLiteDatabase, String locID, String location) {
        try{
            ContentValues contentValues = new ContentValues();
            contentValues.put(DB_COLUMN_LOCATION, location);
            sqLiteDatabase.update(TABLE_NAME, contentValues, DB_COLUMN_ID + " = ?", new String[]{locID});
        } catch (Exception e){
            XLog.e("DATABASE: update error");
        }
    }
}
