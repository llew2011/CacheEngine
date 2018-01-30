package com.llew.file.cache.engine.core;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.llew.file.cache.engine.config.HttpProxyCacheCallback;
import com.llew.file.cache.engine.config.HttpProxyCacheConfig;
import com.llew.file.cache.engine.config.HttpProxyCacheHeaders;
import com.llew.file.cache.engine.config.HttpProxyCacheNameGenerator;
import com.llew.file.cache.engine.config.HttpProxyCacheSource;
import com.llew.file.cache.engine.config.HttpProxyCacheSink;
import com.llew.file.cache.engine.config.HttpProxyCacheStorage;
import com.llew.file.cache.engine.config.HttpProxyCacheUrlFilter;
import com.llew.file.cache.engine.config.HttpProxyCacheUsage;
import com.llew.file.cache.engine.manager.HttpProxyCacheFileManager;
import com.llew.file.cache.engine.usage.LruFilesCountDiskUsage;
import com.llew.file.cache.engine.usage.LruFilesSizeDiskUsage;
import com.llew.file.cache.engine.usage.LruSingleFileSizeUsage;
import com.llew.file.cache.engine.utils.Constants;
import com.llew.file.cache.engine.utils.FileUtils;
import com.llew.file.cache.engine.utils.Logger;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.llew.file.cache.engine.utils.Preconditions.checkNotNull;


/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/28
 */

public final class HttpProxyCacheServer {

    private HttpProxyCacheConfig mCacheConfig;
    private HttpProxyCachePinger mCachePinger;

    public HttpProxyCacheServer(Context context) {
        this(new Builder(checkNotNull(context)).buildConfig());
    }

    private HttpProxyCacheServer(HttpProxyCacheConfig config) {
        this.mCacheConfig = config;
        try {
            mCachePinger = HttpProxyCachePinger.getPinger(config);
        } catch (Throwable e) {
            Logger.e(e);
            mCacheConfig = null;
            mCachePinger = null;
        }
    }

    /**
     * get proxy url, such as <b><code>http://127.0.0.1:9999/originUrl</code></b>
     *
     * @param url the cache url
     * @return get proxy url, such as <code>http://127.0.0.1:9999/originUrl</code>
     */
    public String proxyUrl(String url) {
        return proxyUrl(url, null, null);
    }

    /**
     * get proxy url, such as <b><code>http://127.0.0.1:9999/originUrl</code></b>
     *
     * @param url     the cache url
     * @param headers the set of headers that the cache url needs
     * @return get proxy url, such as <code>http://127.0.0.1:9999/originUrl</code>
     */
    public String proxyUrl(String url, HttpProxyCacheHeaders headers) {
        return proxyUrl(url, headers, null);
    }

    /**
     * get proxy url, such as <b><code>http://127.0.0.1:9999/originUrl</code></b>
     *
     * @param url      the cache url
     * @param callback the callback with the cache url
     * @return get proxy url, such as <code>http://127.0.0.1:9999/originUrl</code>
     */
    public String proxyUrl(String url, HttpProxyCacheCallback callback) {
        return proxyUrl(url, null, callback);
    }

    /**
     * get proxy url, such as <b><code>http://127.0.0.1:9999/originUrl</code></b>
     *
     * @param url      the cache url
     * @param headers  the headers with the cache url
     * @param callback the callback with the cache url
     * @return get proxy url, such as <code>http://127.0.0.1:9999/originUrl</code>
     */
    public String proxyUrl(String url, HttpProxyCacheHeaders headers, HttpProxyCacheCallback callback) {
        try {
            if (TextUtils.isEmpty(url)) {
                return url;
            }

            if (null == mCacheConfig || !mCacheConfig.getUrlFilter().accept(url)) {
                return url;
            }

            File cachedFile = mCacheConfig.generateCacheFile(url);
            if (isFileCached(cachedFile)) {
                touchFileSafely(cachedFile);
                if (isFileCached(cachedFile)) {
                    Logger.e("file = " + cachedFile.getName() + " has cached and cached size = " + cachedFile.length() + ", use it directly ");
                    return Uri.fromFile(cachedFile).toString();
                }
            }

            if (null != callback) {
                mCacheConfig.addCallback(url, callback);
            }

            if (null != headers) {
                mCacheConfig.addHeaders(url, headers);
            }

            return isActive() ? appendProxyUrl(url) : url;
        } catch (Throwable ignore) {
            Logger.e(ignore);
            // ignore it and return origin url
            return url;
        }
    }

    /**
     * get cached file by url, <code>null</code> if not completely cached
     *
     * @param url the cache url
     * @return get the cached file by url, <code>null</code> if not completely cached
     */
    public File getCachedFile(String url) {
        return isCached(url) ? mCacheConfig.generateCacheFile(url) : null;
    }

    /**
     * clear all cached files
     */
    public void clearCaches() {
        if (null != mCacheConfig) {
            FileUtils.deleteFile(mCacheConfig.getCacheRootDir());
        }
    }

    /**
     * clear a cached file by url
     *
     * @param url the cache url
     */
    public void clearCache(String url) {
        if (null != mCacheConfig) {
            FileUtils.deleteFile(mCacheConfig.generateCacheFile(url));
        }
    }

    /**
     * check the file is completely cached or not
     *
     * @param url the cache url
     * @return <code>true</code> means completely cached, otherwise <code>false</code>
     */
    public boolean isCached(String url) {
        return null != mCacheConfig && mCacheConfig.generateCacheFile(url).exists();
    }

    /**
     * shutdown proxy server, if called, cache engine will not work anymore
     */
    public void shutdownProxy() {
        if (null != mCachePinger) {
            mCachePinger.shutDown();
        }
        mCachePinger = null;
        mCacheConfig = null;
    }

    /**
     * destroy resources in case of memory leak, this method should be called before Activity/Fragment destroyed
     *
     * @param url the cache url
     */
    public void onDestroy(String url) {
        if (!TextUtils.isEmpty(url)) {
            onDestroy(Collections.singletonList(url));
        }
    }

    /**
     * destroy resources in case of memory leak, this method should be called before Activity/Fragment destroyed
     *
     * @param urls the cache url
     */
    public void onDestroy(List<String> urls) {
        try {
            if (null != urls) {
                for (String url : urls) {
                    if (null != mCacheConfig) {
                        mCacheConfig.removeCallback(url);
                        mCacheConfig.removeHeaders(url);
                    }
                    if (null != mCachePinger) {
                        mCachePinger.destroy(url);
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private String appendProxyUrl(String url) throws UnsupportedEncodingException {
        return String.format(Locale.US, "http://%s:%d/%s", Constants.HOST, mCachePinger.getSocketPort(), URLEncoder.encode(url, Constants.CHARSET));
    }

    private boolean isActive() {
        return null != mCachePinger && mCachePinger.ping();
    }

    private void touchFileSafely(File file) {
        try {
            List<HttpProxyCacheUsage> usageList = mCacheConfig.getHttProxyCacheDiskUsages();
            if (null != usageList) {
                for (HttpProxyCacheUsage usage : usageList) {
                    if (null != usage) {
                        usage.touch(file);
                    }
                }
            }
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    private boolean isFileCached(File file) {
        return null != file && file.exists();
    }

    public static class Builder {

        private final Context mContext;

        private int maxAttempts;
        private int maxTimeouts;
        private long maxFileSize;
        private long callbackInterval;
        private File mCacheRootFile;

        private ExecutorService mExecutorService;
        private HttpProxyCacheSink mCacheSink;
        private HttpProxyCacheSource mCacheSource;
        private HttpProxyCacheConfig mCacheConfig;
        private HttpProxyCacheUrlFilter mUrlFilter;
        private HttpProxyCacheStorage mCacheStorage;
        private HttpProxyCacheHeaders mDependHeaders;
        private List<HttpProxyCacheUsage> mDiskUsages;
        private HttpProxyCacheNameGenerator mFileNameGenerator;

        /**
         * Creates a builder for an HttpProxyCacheServer that uses the default value
         *
         * @param context the parent context
         */
        public Builder(Context context) {

            this.mContext = checkNotNull(context).getApplicationContext();

            this.maxAttempts = Constants.ATTEMPTS;
            this.maxTimeouts = Constants.TIMEOUTS;
            this.maxFileSize = Integer.MAX_VALUE;
            this.callbackInterval = Constants.INTERVAL;

            this.mDiskUsages = new ArrayList<>();
            this.mUrlFilter = HttpProxyCacheUrlFilter.ANY;
            this.mCacheStorage = new HttpProxyDBStorage(mContext);
            this.mFileNameGenerator = HttpProxyCacheNameGenerator.MD5;
            this.mExecutorService = Executors.newCachedThreadPool();
            this.mCacheRootFile = HttpProxyCacheFileManager.getCacheDirectory(context);
            this.mCacheSource = new com.llew.file.cache.engine.core.HttpProxyCacheSource();
            this.mCacheSink = new com.llew.file.cache.engine.core.HttpProxyCacheSink();
        }

        /**
         * provide the common HTTP request headers
         * <p>
         * <b><font color='red'>NOTE:</font></b> if this method has been called, all of the cache url request will be added header in HTTP-HEADER
         * </p>
         *
         * @param headers the common HTTP request headers
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder appendHeaders(HttpProxyCacheHeaders headers) {
            this.mDependHeaders = headers;
            return this;
        }

        /**
         * Custom file cache policy.
         *
         * @param sink custom file cache policy.
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder cacheSink(HttpProxyCacheSink sink) {
            this.mCacheSink = sink;
            return this;
        }

        /**
         * Custom network request policy.
         *
         * @param cacheSource custom network request policy.
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder cacheSource(HttpProxyCacheSource cacheSource) {
            this.mCacheSource = cacheSource;
            return this;
        }

        /**
         * Add additional storage rules
         *
         * @param storage the storage rules
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder cacheStorage(HttpProxyCacheStorage storage) {
            if (null != storage) {
                mCacheStorage = storage;
            }
            return this;
        }

        /**
         * Add additional filtering rules
         *
         * @param filter the filtering rules
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder urlFilter(HttpProxyCacheUrlFilter filter) {
            if (null != filter) {
                mUrlFilter = filter;
            }
            return this;
        }

        /**
         * Set the thread pool for the cache engine
         *
         * @param service the thread pool for the cache engine
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder executorService(ExecutorService service) {
            if (null != service) {
                mExecutorService = service;
            }
            return this;
        }

        /**
         * Custom file usage policies.
         *
         * @param diskUsage custom file usage policies.
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder diskUsage(HttpProxyCacheUsage diskUsage) {
            if (null != diskUsage) {
                mDiskUsages.add(diskUsage);
            }
            return this;
        }

        /**
         * set the root directory of the cache file
         *
         * @param file the root directory of the cache file
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder rootDir(File file) {
            if (null != file && file.exists() && file.isDirectory()) {
                mCacheRootFile = file;
            }
            return this;
        }

        /**
         * set the total number of cached files
         *
         * @param count the total number of cached files
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder maxFileCount(int count) {
            if (count > 0) {
                mDiskUsages.add(new LruFilesCountDiskUsage(mCacheConfig, count));
            }
            return this;
        }

        /**
         * set the maximum size of all cached files
         *
         * @param size the maximum size of all cached files
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder maxTotalSize(long size) {
            if (size > 0) {
                mDiskUsages.add(new LruFilesSizeDiskUsage(mCacheConfig, size));
            }
            return this;
        }

        /**
         * set the maximum size of a single cache file
         *
         * @param size the maximum size of a single cache file
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder singleFileSize(long size) {
            if (size > 0) {
                this.maxFileSize = size;
                mDiskUsages.add(new LruSingleFileSizeUsage(mCacheConfig, size));
            }
            return this;
        }

        /**
         * Set the maximum number of attempts to request the proxy server.
         *
         * @param attempts the maximum number of attempts
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder maxAttempts(int attempts) {
            if (attempts > 0) {
                maxAttempts = attempts;
            }
            return this;
        }

        /**
         * Set the time interval for each callback.
         *
         * @param intervalTime the duration in milliseconds
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder callbackInterval(long intervalTime) {
            if (intervalTime > 0) {
                this.callbackInterval = intervalTime;
            }
            return this;
        }

        /**
         * Set the timeout period for the request proxy server.
         *
         * @param timeout the duration in milliseconds
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder maxTimeouts(int timeout) {
            if (timeout > 0) {
                maxTimeouts = timeout;
            }
            return this;
        }

        /**
         * Custom filename generation policy.
         *
         * @param nameGenerator custom filename generation policy.
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder fileNameGenerator(HttpProxyCacheNameGenerator nameGenerator) {
            if (null != nameGenerator) {
                this.mFileNameGenerator = nameGenerator;
            }
            return this;
        }

        /**
         * determine print log or not
         *
         * @param enabled <code>true</code> print log, otherwise <code>false</code>
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder logEnabled(boolean enabled) {
            Logger.enable(enabled);
            return this;
        }

        /**
         * Creates an {@link HttpProxyCacheServer} with the arguments supplied to this
         * builder.
         */
        public HttpProxyCacheServer build() {
            return new HttpProxyCacheServer(buildConfig());
        }

        private HttpProxyCacheConfig buildConfig() {
            return mCacheConfig = new HttpProxyCacheConfig(mContext, mDependHeaders, mDiskUsages, mCacheSource, mCacheSink, mCacheStorage, mFileNameGenerator, mCacheRootFile, mUrlFilter, mExecutorService, maxFileSize, maxAttempts, maxTimeouts, callbackInterval);
        }
    }
}
