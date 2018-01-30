package com.llew.file.cache.engine.core;

import android.text.TextUtils;

import com.llew.file.cache.engine.config.HttpProxyCacheSink;
import com.llew.file.cache.engine.config.HttpProxyCacheConfig;
import com.llew.file.cache.engine.config.HttpProxyCacheSource;
import com.llew.file.cache.engine.exception.HttpProxyCacheException;
import com.llew.file.cache.engine.info.HttpProxyCacheSourceInfo;
import com.llew.file.cache.engine.utils.Constants;
import com.llew.file.cache.engine.utils.FileUtils;
import com.llew.file.cache.engine.utils.Logger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static com.llew.file.cache.engine.utils.Preconditions.checkNotNull;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/29
 */

class HttpProxyCacheEngine {

    private static final float MAX_RATE = 0.75F;

    private final String mUrl;
    private final HttpProxyCacheConfig mCacheConfig;

    private volatile boolean isFileSizeValid;
    private volatile boolean isFileInCaching;
    private volatile boolean isShutdownCalled;

    private AtomicInteger mCachingCount;
    private HttpProxyCacheSink mCacheSink;
    private HttpProxyCacheSource mCacheSource;


    HttpProxyCacheEngine(HttpProxyCacheConfig config, String url) throws HttpProxyCacheException {
        this.mUrl = checkNotNull(url);
        this.mCacheConfig = checkNotNull(config);
        this.mCachingCount = new AtomicInteger();
        this.mCacheSource = mCacheConfig.getCacheSource().clone(mCacheConfig);
        this.mCacheSink = mCacheConfig.getCacheSink().clone(mCacheConfig);
        this.mCacheSource.init(config, url);
        this.mCacheSink.init(config, url);
    }

    void process(final HttpProxyCacheRequest request, final Socket socket) throws HttpProxyCacheException {
        try {
            isFileInCaching = false;
            OutputStream outputStream = new BufferedOutputStream(socket.getOutputStream());
            String responseHeader = generateResponseHeader(request);
            outputStream.write(responseHeader.getBytes(Constants.CHARSET));
            isFileSizeValid = isFileSizeValid();

            long offset = request.offset;
            if (0 == offset) {
                responseWithCache(outputStream);
            } else {
                responseWithCache(outputStream, offset);
            }
        } catch (Throwable e) {
            throw new HttpProxyCacheException(e);
        }
    }

    private boolean isFileSizeValid() throws HttpProxyCacheException {
        HttpProxyCacheSourceInfo sourceInfo = mCacheConfig.getCacheStorage().get(mUrl);
        return null == sourceInfo || sourceInfo.length < 0 || sourceInfo.length <= mCacheConfig.getMaxFileSize();
    }

    private void responseWithCache(OutputStream outputStream) throws Exception {
        final long cachedLength = mCacheSink.available();
        final byte[] buffer = new byte[Constants.BUFFER_SIZE];
        int readBytes;

        if (0 == cachedLength) {
            Logger.e("engine cache full data  start...");
            HttpProxyCacheSource source = mCacheSource.clone(mCacheConfig);
            source.init(mCacheConfig, mUrl);
            source.open();
            isFileInCaching = true;
            while (!isShutdownCalled && isFileInCaching && -1 != (readBytes = source.read(buffer))) {
                if (isFileInCaching) {
                    outputStream.write(buffer, 0, readBytes);
                    outputStream.flush();
                    if (isFileSizeValid) {
                        mCacheSink.append(buffer, readBytes);
                    }
                }
            }
            isFileInCaching = false;
            source.close();
            if (isFileSizeValid && mCacheSink.available() == mCacheConfig.getCacheStorage().get(mUrl).length) {
                mCacheSink.complete();
            }
            Logger.e("engine cache full data finish...");
            destroyResourcesIfNecessary();
        } else {
            Logger.e("engine cache part data start  and offset = " + cachedLength);
            long realOffset = responseWithCache(outputStream, buffer, 0);
            responseWithCache(outputStream, realOffset);
            Logger.e("engine cache part data finish and offset = " + cachedLength);
        }
    }

    private void responseWithCache(OutputStream outputStream, long offset) throws Exception {
        continueCacheIfNecessary();
        final long cachedLength = mCacheSink.available();
        final byte[] buffer = new byte[Constants.BUFFER_SIZE];
        if (mCacheSink.isCompleted()) {
            Logger.e("engine has cached full data start and offset = " + offset);
            if (offset != cachedLength) {
                responseWithCache(outputStream, buffer, offset);
            }
            Logger.e("engine has cached full data stop  and offset = " + offset);
        } else {
            int readBytes;
            if (0 == cachedLength) {
                HttpProxyCacheSource source = mCacheSource.clone(mCacheConfig);
                source.init(mCacheConfig, mUrl);
                source.open(offset);
                isFileInCaching = true;
                while (!isShutdownCalled && isFileInCaching && -1 != (readBytes = source.read(buffer))) {
                    if (isFileInCaching) {
                        outputStream.write(buffer, 0, readBytes);
                        outputStream.flush();
                    }
                }
                isFileInCaching = false;
                source.close();
            } else {
                if (offset < cachedLength * MAX_RATE) {
                    long realOffset = responseWithCache(outputStream, buffer, offset);
                    responseWithCache(outputStream, realOffset);
                } else {
                    Logger.e("engine open new connection start...");
                    HttpProxyCacheSource source = mCacheSource.clone(mCacheConfig);
                    source.init(mCacheConfig, mUrl);
                    source.open(offset);
                    isFileInCaching = true;
                    while (!isShutdownCalled && isFileInCaching && -1 != (readBytes = source.read(buffer))) {
                        if (isFileInCaching) {
                            outputStream.write(buffer, 0, readBytes);
                            outputStream.flush();
                        }
                    }
                    isFileInCaching = false;
                    source.close();
                    Logger.e("engine open new connection  stop...");
                }
            }
        }
    }

    private long responseWithCache(OutputStream outputStream, byte[] buffer, long realOffset) throws IOException {
        Logger.e("engine read local data start  and offset length = " + realOffset);
        int readBytes;
        isFileInCaching = true;
        while (!isShutdownCalled && isFileInCaching && -1 != (readBytes = mCacheSink.read(buffer, realOffset, buffer.length))) {
            if (isFileInCaching) {
                outputStream.write(buffer, 0, readBytes);
                outputStream.flush();
                realOffset += readBytes;
            }
        }
        isFileInCaching = false;
        Logger.e("engine read local data finished and read length = " + realOffset);
        return realOffset;
    }

    private void continueCacheIfNecessary() throws HttpProxyCacheException, InterruptedException {
        if (isFileSizeValid && 0 == mCachingCount.getAndIncrement() && !mCacheSink.isCompleted()) {
            CountDownLatch latch = new CountDownLatch(1);
            mCacheConfig.getExecutorService().submit(new CacheContentRunnable(latch));
            latch.await();
        }
    }

    private String generateResponseHeader(HttpProxyCacheRequest request) throws HttpProxyCacheException {
        HttpProxyCacheSourceInfo sourceInfo = getStorageSourceInfo(mCacheConfig, mUrl);
        String mime = sourceInfo.mime;
        long realLength = mCacheSink.isCompleted() ? mCacheSink.available() : sourceInfo.length;
        long contentLength = request.partial ? realLength - request.offset : realLength;
        boolean mimeKnow = !TextUtils.isEmpty(mime);
        boolean lengthKnow = realLength > 0;
        boolean addRange = lengthKnow && request.partial;
        StringBuilder header = new StringBuilder()
                .append(request.partial? "HTTP/1.1 206 PARTIAL CONTENT" : "HTTP/1.1 200 OK").append("\n")
                .append("Accept-Ranges: bytes").append("\n")
                .append(lengthKnow ? format("Content-Length: %d\n", contentLength) : "")
                .append(  addRange ? format("Content-Range: bytes %d-%d/%d\n", request.offset, realLength - 1, realLength) : "")
                .append(  mimeKnow ? format("Content-Type: %s\n", mime) : "")
                .append("\n");
        return header.toString();
    }

    private HttpProxyCacheSourceInfo getStorageSourceInfo(HttpProxyCacheConfig config, String url) throws HttpProxyCacheException {
        HttpProxyCacheSourceInfo sourceInfo = config.getCacheStorage().get(url);
        if (null == sourceInfo) {
            final long length = mCacheSource.length();
            final String mime = mCacheSource.mime();
            sourceInfo = new HttpProxyCacheSourceInfo(url, mime, length);
        }
        return sourceInfo;
    }

    private String format(String pattern, Object... args) {
        return String.format(Locale.US, pattern, args);
    }

    void shutdown() {
        isShutdownCalled = true;
        if (null != mCacheSink && mCacheSink.isCompleted()) {
            destroyResourcesIfNecessary();
        }
    }

    private void destroyResourcesIfNecessary() {
        if (isShutdownCalled) {
            Logger.e("engine resources destroy start...");
            FileUtils.closeQuietly(mCacheSource);
            FileUtils.closeQuietly(mCacheSink);
            mCachingCount = null;
            mCacheSource = null;
            mCacheSink = null;
            Logger.e("engine resources destroy  stop...");
        }
    }

    private class CacheContentRunnable implements Runnable {

        private CountDownLatch mLatch;

        CacheContentRunnable(CountDownLatch latch) {
            this.mLatch = latch;
        }

        @Override
        public void run() {
            try {
                if (null != mLatch) {
                    mLatch.countDown();
                    mLatch = null;
                }

                Logger.e("engine cache part data  start...");
                final byte[] buffer = new byte[Constants.BUFFER_SIZE];
                long offset = mCacheSink.available();
                int readBytes;

                HttpProxyCacheSource source = mCacheConfig.getCacheSource().clone(mCacheConfig);
                source.init(mCacheConfig, mUrl);
                source.open(offset);
                while (-1 != (readBytes = source.read(buffer))) {
                    mCacheSink.append(buffer, readBytes);
                }
                mCacheSink.complete();
                source.close();
            } catch (Throwable e) {
                Logger.e(e);
            } finally {
                Logger.e("engine cache part data finish...");
                destroyResourcesIfNecessary();
            }
        }
    }
}

