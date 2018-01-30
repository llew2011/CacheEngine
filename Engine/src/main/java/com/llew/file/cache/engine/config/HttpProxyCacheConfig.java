package com.llew.file.cache.engine.config;

import android.content.Context;
import android.text.TextUtils;

import com.llew.file.cache.engine.usage.LruFilesSizeDiskUsage;
import com.llew.file.cache.engine.utils.Constants;
import com.llew.file.cache.engine.utils.FileUtils;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/28
 */

public final class HttpProxyCacheConfig {

    private final ConcurrentHashMap<String, HttpProxyCacheCallback> mCallbacks;
    private final ConcurrentHashMap<String, HttpProxyCacheHeaders> mHeaders;


    private final Context  mContext;
    private final int  mMaxAttempts;
    private final int  mMaxTimeouts;
    private final long mMaxFileSize;
    private final long callbackInterval;

    private final File mHttpProxyCacheRootDir;
    private final HttpProxyCacheSink mCacheSink;
    private final HttpProxyCacheSource mCacheSource;
    private final HttpProxyCacheStorage mCacheStorage;
    private final HttpProxyCacheHeaders mDependHeaders;
    private final HttpProxyCacheUrlFilter mUrlFilter;
    private final List<HttpProxyCacheUsage> mDiskUsages;
    private final HttpProxyCacheNameGenerator mFileNameGenerator;
    private ExecutorService mDefaultExecutorService;


    public HttpProxyCacheConfig(Context context, HttpProxyCacheHeaders headers, List<HttpProxyCacheUsage> diskUsage, com.llew.file.cache.engine.config.HttpProxyCacheSource cacheSource, HttpProxyCacheSink cacheSink, HttpProxyCacheStorage storage, HttpProxyCacheNameGenerator nameGenerator, File rootDir, HttpProxyCacheUrlFilter filter, ExecutorService service, long maxFileSize, int maxAttempts, int maxTimeouts, long callbackInterval) {
        this.mContext = context;
        this.mUrlFilter = filter;
        this.mCacheSink = cacheSink;
        this.mDiskUsages = diskUsage;
        this.mCacheStorage = storage;
        this.mDependHeaders = headers;
        this.mCacheSource = cacheSource;
        this.mMaxAttempts = maxAttempts;
        this.mMaxTimeouts = maxTimeouts;
        this.mMaxFileSize = maxFileSize;
        this.mFileNameGenerator = nameGenerator;
        this.mHttpProxyCacheRootDir = rootDir;
        this.mDefaultExecutorService = service;
        this.mCallbacks = new ConcurrentHashMap<>();
        this.mHeaders = new ConcurrentHashMap<>();
        this.callbackInterval = callbackInterval;
    }

    public Context getContext() {
        return mContext;
    }

    public HttpProxyCacheUrlFilter getUrlFilter() {
        return mUrlFilter;
    }

    public File getCacheRootDir() {
        return mHttpProxyCacheRootDir;
    }

    public HttpProxyCacheNameGenerator getFileNameGenerator() {
        return mFileNameGenerator;
    }

    public List<HttpProxyCacheUsage> getHttProxyCacheDiskUsages() {
        if (mDiskUsages.isEmpty()) {
            mDiskUsages.add(new LruFilesSizeDiskUsage(this, Constants.FILES_SIZE));
        }
        return mDiskUsages;
    }

    public ExecutorService getExecutorService() {
        return mDefaultExecutorService;
    }

    public com.llew.file.cache.engine.config.HttpProxyCacheSource getCacheSource() {
        return mCacheSource;
    }

    public HttpProxyCacheSink getCacheSink() {
        return mCacheSink;
    }

    public HttpProxyCacheStorage getCacheStorage() {
        return mCacheStorage;
    }

    public int getMaxAttempts() {
        return mMaxAttempts;
    }

    public int getMaxTimeouts() {
        return mMaxTimeouts;
    }

    public long getMaxFileSize() {
        return mMaxFileSize;
    }

    public long getTimeInternal() {
        return callbackInterval;
    }

    public HttpProxyCacheHeaders getDependHeaders() {
        return mDependHeaders;
    }

    public synchronized File generateCacheFile(String url) {
        File rootDir = getCacheRootDir();
        FileUtils.createDirs(rootDir);
        return new File(rootDir, generateCacheName(url));
    }

    private String generateCacheName(String url) {
        String name = getFileNameGenerator().generateName(url);
        if (TextUtils.isEmpty(name)) {
            name = HttpProxyCacheNameGenerator.MD5.generateName(url);
        }
        return name;
    }

    public void addCallback(String url, HttpProxyCacheCallback callback) {
        if (!TextUtils.isEmpty(url) && null != callback) {
            mCallbacks.put(url, callback);
        }
    }

    public void addHeaders(String url, HttpProxyCacheHeaders headers) {
        if (!TextUtils.isEmpty(url) && null != headers) {
            mHeaders.put(url, headers);
        }
    }

    public void removeHeaders(String url) {
        if (!TextUtils.isEmpty(url)) {
            mHeaders.remove(url);
        }
    }

    public void removeCallback(String url) {
        if (!TextUtils.isEmpty(url)) {
            mCallbacks.remove(url);
        }
    }

    public HttpProxyCacheCallback getCacheCallback(String url) {
        return TextUtils.isEmpty(url) ? null : mCallbacks.get(url);
    }

    public HttpProxyCacheHeaders getCacheHeaders(String url) {
        return TextUtils.isEmpty(url) ? null : mHeaders.get(url);
    }

    public boolean isPingRequest(String url) {
        return null != url && url.startsWith(String.format("http://%s", Constants.HOST));
    }
}