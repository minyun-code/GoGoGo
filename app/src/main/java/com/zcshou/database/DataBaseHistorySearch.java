package com.zcshou.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.elvishew.xlog.XLog;

public class DataBaseHistorySearch extends SQLiteOpenHelper {
    public static final String TABLE_NAME = "HistorySearch";
    public static final String DB_COLUMN_ID = "DB_COLUMN_ID";
    public static final String DB_COLUMN_KEY = "DB_COLUMN_KEY";
    public static final String DB_COLUMN_DESCRIPTION = "DB_COLUMN_DESCRIPTION";
    public static final String DB_COLUMN_TIMESTAMP = "DB_COLUMN_TIMESTAMP";
    public static final String DB_COLUMN_IS_LOCATION = "DB_COLUMN_IS_LOCATION";
    public static final String DB_COLUMN_LONGITUDE_WGS84 = "DB_COLUMN_LONGITUDE_WGS84";
    public static final String DB_COLUMN_LATITUDE_WGS84 = "DB_COLUMN_LATITUDE_WGS84";
    public static final String DB_COLUMN_LONGITUDE_CUSTOM = "DB_COLUMN_LONGITUDE_CUSTOM";
    public static final String DB_COLUMN_LATITUDE_CUSTOM = "DB_COLUMN_LATITUDE_CUSTOM";
    // 搜索的关键字
    public static final int DB_SEARCH_TYPE_KEY = 0;
    // 搜索结果
    public static final int DB_SEARCH_TYPE_RESULT = 1;

    private static final int DB_VERSION = 2;
    private static final String DB_NAME = "HistorySearch.db";
    private static final String CREATE_TABLE = "create table if not exists " + TABLE_NAME +
            " (DB_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, DB_COLUMN_KEY TEXT NOT NULL, " +
            "DB_COLUMN_DESCRIPTION TEXT, DB_COLUMN_TIMESTAMP BIGINT NOT NULL, DB_COLUMN_IS_LOCATION INTEGER NOT NULL, " +
            "DB_COLUMN_LONGITUDE_WGS84 TEXT, DB_COLUMN_LATITUDE_WGS84 TEXT, " +
            "DB_COLUMN_LONGITUDE_CUSTOM TEXT, DB_COLUMN_LATITUDE_CUSTOM TEXT)";
    private static final String CREATE_UNIQUE_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS "
            + "HistorySearch_key ON " + TABLE_NAME + " (" + DB_COLUMN_KEY + ")";
            
    public DataBaseHistorySearch(Context context) {
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
            sqLiteDatabase.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + DB_COLUMN_ID
                    + " <> (SELECT kept." + DB_COLUMN_ID + " FROM " + TABLE_NAME + " AS kept WHERE kept."
                    + DB_COLUMN_KEY + " = " + TABLE_NAME + "." + DB_COLUMN_KEY
                    + " ORDER BY kept." + DB_COLUMN_TIMESTAMP + " DESC, kept." + DB_COLUMN_ID + " DESC LIMIT 1)");
            sqLiteDatabase.execSQL(CREATE_UNIQUE_INDEX);
        }
    }

    public static void saveHistorySearch(SQLiteDatabase sqLiteDatabase, ContentValues contentValues) {
        try {
            sqLiteDatabase.beginTransaction();
            try {
                ContentValues values = new ContentValues(contentValues);
                values.remove(DB_COLUMN_ID);
                String searchKey = values.getAsString(DB_COLUMN_KEY);
                int updated = sqLiteDatabase.update(TABLE_NAME, values, DB_COLUMN_KEY + " = ?", new String[] {searchKey});
                if (updated == 0) {
                    sqLiteDatabase.insertOrThrow(TABLE_NAME, null, values);
                }
                sqLiteDatabase.setTransactionSuccessful();
            } finally {
                sqLiteDatabase.endTransaction();
            }
        } catch (Exception e) {
            XLog.e("DATABASE: save search error", e);
        }
    }
}
