package com.llew.file.cache.engine.core;

import com.llew.file.cache.engine.config.HttpProxyCacheCallback;
import com.llew.file.cache.engine.config.HttpProxyCacheConfig;
import com.llew.file.cache.engine.config.HttpProxyCacheUsage;
import com.llew.file.cache.engine.exception.HttpProxyCacheException;
import com.llew.file.cache.engine.manager.HttpProxyCacheMainHandler;
import com.llew.file.cache.engine.usage.LruFilesSizeDiskUsage;
import com.llew.file.cache.engine.utils.Constants;
import com.llew.file.cache.engine.utils.FileUtils;
import com.llew.file.cache.engine.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/30
 */

class HttpProxyCacheSink implements com.llew.file.cache.engine.config.HttpProxyCacheSink {

    private static final String SUFFIX = ".download";

    private List<HttpProxyCacheUsage> mDiskUsages;
    private HttpProxyCacheConfig mCacheConfig;
    private String mUrl;
    private int mCachedPercent;
    private NotifyDataSizeChangedRunnable mCallbackRunnable;

    private File mCachedFile;
    private RandomAccessFile mReadCursor;
    private RandomAccessFile mWriteCursor;

    HttpProxyCacheSink() {
    }

    @Override
    public synchronized void init(HttpProxyCacheConfig config, String url) throws HttpProxyCacheException {
        this.mCacheConfig = config;
        this.mUrl = url;
        File cachedFile = config.generateCacheFile(url);
        boolean completed = cachedFile.exists();
        this.mCachedFile = completed ? cachedFile : new File(cachedFile.getParent(), String.format("%s%s", cachedFile.getName(), SUFFIX));
        try {
            if (!this.mCachedFile.exists()) {
                boolean success = this.mCachedFile.createNewFile();
                if (!success) {
                    throw new IOException(this.mCachedFile.getName() + " has created failure !!!");
                }
            }
            this.mReadCursor = new RandomAccessFile(this.mCachedFile, "r");
            this.mWriteCursor = new RandomAccessFile(this.mCachedFile, "rw");
        } catch (Exception e) {
            throw new HttpProxyCacheException(e);
        }
    }

    @Override
    public synchronized long available() throws HttpProxyCacheException {
        try {
            return this.mCachedFile.length();
        } catch (Throwable e) {
            throw new HttpProxyCacheException(e);
        }
    }

    @Override
    public synchronized int read(byte[] buffer, long offset, int length) throws HttpProxyCacheException {
        try {
            mReadCursor.seek(offset);
            return mReadCursor.read(buffer, 0, length);
        } catch (Throwable e) {
            throw new HttpProxyCacheException(e);
        }
    }

    @Override
    public synchronized void append(byte[] data, int length) throws HttpProxyCacheException {
        if (isCompleted()) {
            throw new HttpProxyCacheException("Error append cache: cache file " + mCachedFile + " is completed!");
        }
        try {
            if (null != data) {
                mWriteCursor.seek(available());
                mWriteCursor.write(data, 0, length);
            }
            notifyCachedLengthChanged();
        } catch (Throwable e) {
            throw new HttpProxyCacheException(e);
        }
    }

    @Override
    public synchronized void close() throws HttpProxyCacheException {
        FileUtils.closeQuietly(mReadCursor);
        FileUtils.closeQuietly(mWriteCursor);
    }

    @Override
    public synchronized void complete() throws HttpProxyCacheException {
        if (isCompleted()) return;
        close();
        File completedFile = mCacheConfig.generateCacheFile(mUrl);
        boolean renamed = mCachedFile.renameTo(completedFile);
        if (!renamed) {
            renamed = FileUtils.copy(mCachedFile, completedFile);
            Logger.e("copy result = " + renamed);
            if (!mCachedFile.delete()) {
                Logger.e("delete failure : " + mCachedFile.getName());
            }
        }
        if (!renamed) {
            throw new HttpProxyCacheException("Error renaming file " + mCachedFile + " to " + completedFile + " for completion!");
        }
        mCachedFile = completedFile;
        mCachedPercent = 100;
        HttpProxyCacheMainHandler.getIMPL().post(mCallbackRunnable);
        try {
            mReadCursor = new RandomAccessFile(mCachedFile, "r");
            mWriteCursor = new RandomAccessFile(mCachedFile, "rw");
            touchFile();
            Logger.e("file : " + completedFile.getName() + " has successfully cached and cache size = " + this.mCachedFile.length());
        } catch (Throwable e) {
            throw new HttpProxyCacheException(e);
        }
    }

    @Override
    public synchronized boolean isCompleted() {
        return null != mCachedFile && !mCachedFile.getName().endsWith(SUFFIX);
    }

    @Override
    public synchronized com.llew.file.cache.engine.config.HttpProxyCacheSink clone(HttpProxyCacheConfig config) throws HttpProxyCacheException {
        return new HttpProxyCacheSink();
    }

    private void touchFile() throws HttpProxyCacheException {
        final List<HttpProxyCacheUsage> usages = getDiskUsage();
        for (HttpProxyCacheUsage usage : usages) {
            if (null != usage) {
                usage.touch(mCachedFile);
            }
        }
    }

    private List<HttpProxyCacheUsage> getDiskUsage() {
        if (null == mDiskUsages) {
            mDiskUsages = new ArrayList<>();
            mDiskUsages.add(new LruFilesSizeDiskUsage(mCacheConfig, Constants.FILES_SIZE));
        }
        return mDiskUsages;
    }

    private void notifyCachedLengthChanged() {
        try {
            final long totalLength   = mCacheConfig.getCacheStorage().get(mUrl).length;
            final long currentLength = available();
            mCachedPercent = 0 == totalLength ? 100 : Math.round(currentLength * 1.0f / totalLength * 100);
            if (null == mCallbackRunnable) {
                HttpProxyCacheMainHandler.getIMPL().post(mCallbackRunnable = new NotifyDataSizeChangedRunnable());
            }
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    private class NotifyDataSizeChangedRunnable implements Runnable {

        private long mLastPercent = -1;

        NotifyDataSizeChangedRunnable() {
        }

        @Override
        public void run() {
            if (null != mCacheConfig) {
                HttpProxyCacheCallback callback = mCacheConfig.getCacheCallback(mUrl);
                if (null != callback && mLastPercent != mCachedPercent && mCachedPercent <= 100) {
                    callback.progress(mUrl, mCachedPercent, 100 == mCachedPercent);
                    if (null != mCallbackRunnable && mCachedPercent < 100) {
                        HttpProxyCacheMainHandler.getIMPL().post(this, mCacheConfig.getTimeInternal());
                    }
                    mLastPercent = mCachedPercent;
                }
            }
        }
    }
}