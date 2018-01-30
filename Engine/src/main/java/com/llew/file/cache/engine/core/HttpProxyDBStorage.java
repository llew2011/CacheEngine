package com.llew.file.cache.engine.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.llew.file.cache.engine.config.HttpProxyCacheStorage;
import com.llew.file.cache.engine.exception.HttpProxyCacheException;
import com.llew.file.cache.engine.info.HttpProxyCacheSourceInfo;
import com.llew.file.cache.engine.utils.Constants;
import com.llew.file.cache.engine.utils.FileUtils;
import com.llew.file.cache.engine.utils.Logger;

import java.util.concurrent.ConcurrentHashMap;

import static com.llew.file.cache.engine.utils.Preconditions.checkNotNull;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/31
 */

class HttpProxyDBStorage extends SQLiteOpenHelper implements HttpProxyCacheStorage {

    private static final String DATABASE = "FileCacheEngine.db";
    private static final String TABLE = "CachedFileInfo";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_URL = "url";
    private static final String COLUMN_LENGTH = "length";
    private static final String COLUMN_MIME = "mime";
    private static final String[] ALL_COLUMNS = new String[]{COLUMN_ID, COLUMN_URL, COLUMN_LENGTH, COLUMN_MIME};
    private static final String CREATE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                    COLUMN_URL + " TEXT NOT NULL," +
                    COLUMN_MIME + " TEXT," +
                    COLUMN_LENGTH + " INTEGER" +
                    ");";

    private final ConcurrentHashMap<String, HttpProxyCacheSourceInfo> infos = new ConcurrentHashMap<>();

    HttpProxyDBStorage(Context context) {
        super(checkNotNull(context), DATABASE, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        checkNotNull(db);
        db.execSQL(CREATE_SQL);
        Logger.e("database : " + DATABASE + " created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Should not be called. There is no any migration");
    }

    @Override
    public HttpProxyCacheSourceInfo get(String url) throws HttpProxyCacheException {
        HttpProxyCacheSourceInfo sourceInfo = infos.get(url);
        if (null != sourceInfo) {
            return sourceInfo;
        }
        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().query(TABLE, ALL_COLUMNS, COLUMN_URL + "=?", new String[]{url}, null, null, null);
            return null == cursor || !cursor.moveToFirst() ? null : generateSourceInfo(url, cursor);
        } catch (Throwable e) {
            Logger.e(e);
        } finally {
            FileUtils.closeQuietly(cursor);
        }
        return null;
    }

    private HttpProxyCacheSourceInfo generateSourceInfo(String url, Cursor cursor) {
        HttpProxyCacheSourceInfo sourceInfo = new HttpProxyCacheSourceInfo(
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MIME)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LENGTH)));
        infos.put(url, sourceInfo);
        return sourceInfo;
    }

    @Override
    public void put(String url, HttpProxyCacheSourceInfo newInfo) throws HttpProxyCacheException {
        checkNotNull(url);
        checkNotNull(newInfo);

        if (!url.contains(Constants.HOST)) {
            HttpProxyCacheSourceInfo cachedInfo = get(url);
            if (!newInfo.equals(cachedInfo)) {
                ContentValues values = getContentValues(newInfo);
                if (null != cachedInfo) {
                    getWritableDatabase().update(TABLE, values, COLUMN_URL + "=?", new String[]{url});
                } else {
                    getWritableDatabase().insert(TABLE, null, values);
                }
                infos.put(url, newInfo);
                Logger.e("update source info to memory and database, source info : " + newInfo);
            }
        }
    }

    private ContentValues getContentValues(HttpProxyCacheSourceInfo info) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_URL, info.url);
        values.put(COLUMN_MIME, info.mime);
        values.put(COLUMN_LENGTH, info.length);
        return values;
    }

    @Override
    public void release() throws HttpProxyCacheException {
        close();
    }
}
