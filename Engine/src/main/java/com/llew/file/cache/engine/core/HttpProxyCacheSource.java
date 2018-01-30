package com.llew.file.cache.engine.core;

import android.text.TextUtils;

import com.llew.file.cache.engine.config.HttpProxyCacheConfig;
import com.llew.file.cache.engine.config.HttpProxyCacheHeaders;
import com.llew.file.cache.engine.exception.HttpProxyCacheException;
import com.llew.file.cache.engine.info.HttpProxyCacheSourceInfo;
import com.llew.file.cache.engine.utils.Constants;
import com.llew.file.cache.engine.utils.FileUtils;
import com.llew.file.cache.engine.utils.Logger;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import static com.llew.file.cache.engine.utils.Preconditions.checkNotNull;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PARTIAL;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/29
 */

class HttpProxyCacheSource implements com.llew.file.cache.engine.config.HttpProxyCacheSource {

    private String mUrl;

    private InputStream mInputStream;
    private HttpURLConnection mConnection;

    private HttpProxyCacheConfig mCacheConfig;

    @Override
    public synchronized void init(HttpProxyCacheConfig config, String url) throws HttpProxyCacheException {
        this.mUrl = checkNotNull(url);
        this.mCacheConfig = checkNotNull(config);
    }

    @Override
    public synchronized void open() throws HttpProxyCacheException {
        open(0);
    }

    @Override
    public synchronized void open(long offset) throws HttpProxyCacheException {
        try {
            mConnection = openHttpUrlConnection(offset, -1);
            mInputStream = new BufferedInputStream(mConnection.getInputStream());
            if (!mCacheConfig.isPingRequest(mUrl)) {
                String mime = mConnection.getContentType();
                long length = getContentLength(mConnection, offset, mConnection.getResponseCode());
                HttpProxyCacheSourceInfo sourceInfo = new HttpProxyCacheSourceInfo(mUrl, mime, length);
                mCacheConfig.getCacheStorage().put(mUrl, sourceInfo);
                Logger.e("cache source connection has opened with source info : " + sourceInfo.toString() + " and offset = " + offset);
            }
        } catch (Throwable e) {
            throw new HttpProxyCacheException(e);
        }
    }

    private long getContentLength(HttpURLConnection connection, long offset, int responseCode) {
        long contentLength = getContentLength(connection);
        return responseCode == HTTP_OK ? contentLength : responseCode == HTTP_PARTIAL ? contentLength + offset : Integer.MIN_VALUE;
    }

    private long getContentLength(HttpURLConnection connection) {
        String contentLength = connection.getHeaderField("Content-Length");
        return !TextUtils.isEmpty(contentLength) && TextUtils.isDigitsOnly(contentLength) ? Long.parseLong(contentLength) : Integer.MIN_VALUE;
    }

    @Override
    public synchronized long length() throws HttpProxyCacheException {
        if (mCacheConfig.isPingRequest(mUrl)) {
            return 0;
        }
        HttpProxyCacheSourceInfo info = mCacheConfig.getCacheStorage().get(mUrl);
        long length = null == info ? 0 : info.length;
        if (length <= 0) {
            return fetchSourceInfo(fetchHttpUrlConnection(0, 10000)).length;
        }
        return length;
    }

    @Override
    public synchronized String mime() throws HttpProxyCacheException {
        if (mCacheConfig.isPingRequest(mUrl)) {
            return "";
        }
        HttpProxyCacheSourceInfo info = mCacheConfig.getCacheStorage().get(mUrl);
        String mime = null == info ? null : info.mime;

        if (TextUtils.isEmpty(mime)) {
            return fetchSourceInfo(fetchHttpUrlConnection(0, 10000)).mime;
        }
        return mime;
    }

    private HttpProxyCacheSourceInfo fetchSourceInfo(HttpURLConnection connection) throws HttpProxyCacheException {
        try {
            long length = getContentLength(connection);
            String mime = connection.getContentType();
            HttpProxyCacheSourceInfo info = new HttpProxyCacheSourceInfo(mUrl, mime, length);
            mCacheConfig.getCacheStorage().put(mUrl, info);

            Logger.e("cache source fetch source info has successful  info : " + info.toString());
            return info;
        } catch (Throwable e) {
            throw new HttpProxyCacheException(e);
        }
    }

    @Override
    public synchronized int read(byte[] buffer) throws HttpProxyCacheException {
        try {
            return mInputStream.read(buffer, 0, buffer.length);
        } catch (Exception e) {
            throw new HttpProxyCacheException(e);
        }
    }

    @Override
    public synchronized void close() throws HttpProxyCacheException {
        FileUtils.closeQuietly(mInputStream);
        if (null != mConnection) {
            try {
                FileUtils.closeQuietly(mConnection.getInputStream());
            } catch (Exception e) {
                Logger.e(e);
            }
            mConnection.disconnect();
        }
        mUrl = null;
        mConnection = null;
        mInputStream = null;
        mCacheConfig = null;
    }

    @Override
    public synchronized com.llew.file.cache.engine.config.HttpProxyCacheSource clone(HttpProxyCacheConfig config) throws HttpProxyCacheException {
        return new HttpProxyCacheSource();
    }

    private HttpURLConnection openHttpUrlConnection(long offset, int timeout) throws HttpProxyCacheException {
        return generateConnection(offset, timeout, "GET");
    }

    private HttpURLConnection fetchHttpUrlConnection(long offset, int timeout) throws HttpProxyCacheException {
        return generateConnection(offset, timeout, "HEAD");
    }

    private HttpURLConnection generateConnection(long offset, int timeout, String method) throws HttpProxyCacheException {
        try {
            int redirectCount = 0;
            String sourUrl = mUrl;
            boolean redirected = false;
            do {
                URL url = new URL(sourUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(method);
                appendHttpHeaders(connection, mUrl);
                if (offset > 0) {
                    connection.setRequestProperty("Range", "bytes=" + offset + "-");
                }
                if (timeout > 0) {
                    connection.setConnectTimeout(timeout);
                    connection.setReadTimeout(timeout);
                }
                int resultCode = connection.getResponseCode();
                redirected = resultCode == HTTP_MOVED_PERM || resultCode == HTTP_MOVED_TEMP || resultCode == HTTP_SEE_OTHER || resultCode == HTTP_MULT_CHOICE;
                if (redirected) {
                    sourUrl = connection.getHeaderField("Location");
                    redirectCount++;
                    connection.disconnect();
                } else {
                    return connection;
                }
                if (redirectCount > Constants.REDIRECT_COUNT) {
                    throw new HttpProxyCacheException("Too many redirects: " + redirectCount);
                }
            } while (redirected);
        } catch (Throwable e) {
            Logger.e(e);
        }
        throw new HttpProxyCacheException("source can't open connection for url : " + mUrl);
    }

    private void appendHttpHeaders(HttpURLConnection connection, String url) {
        if (null != mCacheConfig) {
            appendHttpHeaders(connection, mCacheConfig.getDependHeaders(), url);
            appendHttpHeaders(connection, mCacheConfig.getCacheHeaders(url), url);
        }
    }

    private void appendHttpHeaders(HttpURLConnection connection, HttpProxyCacheHeaders httpHeaders, String url) {
        if (null != connection && null != httpHeaders && !TextUtils.isEmpty(url)) {
            Map<String, String> headers = httpHeaders.appendHeaders(url);
            if (null != headers) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    if (null != header && null != header.getKey() && null != header.getValue()) {
                        connection.setRequestProperty(header.getKey(), header.getValue());
                    }
                }
            }
        }
    }
}
