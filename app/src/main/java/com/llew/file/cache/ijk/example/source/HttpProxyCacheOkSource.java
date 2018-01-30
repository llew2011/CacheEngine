package com.llew.file.cache.ijk.example.source;

import android.text.TextUtils;

import com.llew.file.cache.engine.config.HttpProxyCacheConfig;
import com.llew.file.cache.engine.config.HttpProxyCacheHeaders;
import com.llew.file.cache.engine.config.HttpProxyCacheSource;
import com.llew.file.cache.engine.exception.HttpProxyCacheException;
import com.llew.file.cache.engine.info.HttpProxyCacheSourceInfo;
import com.llew.file.cache.engine.utils.Constants;
import com.llew.file.cache.engine.utils.FileUtils;
import com.llew.file.cache.engine.utils.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2018/1/3
 */

public class HttpProxyCacheOkSource implements HttpProxyCacheSource {

    private static final int DEFAULT_TIME_OUT_SECONDS = 30;

    private String mUrl;
    private OkHttpClient mClient;
    private HttpProxyCacheConfig mCacheConfig;

    private Response mResponse;
    private InputStream mInputStream;

    public HttpProxyCacheOkSource() {
        mClient = new OkHttpClient.Builder()
                .hostnameVerifier(new HttpRequestHostnameVerifier())
                .readTimeout(DEFAULT_TIME_OUT_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(DEFAULT_TIME_OUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIME_OUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    private static class HttpRequestHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    @Override
    public void init(HttpProxyCacheConfig config, String url) throws HttpProxyCacheException {
        mUrl = url;
        mCacheConfig = config;
    }

    @Override
    public void open() throws HttpProxyCacheException {
        open(0);
    }

    @Override
    public void open(long offset) throws HttpProxyCacheException {
        try {
            mResponse = openOkHttpConnection(offset);
            ResponseBody body = mResponse.body();
            if (null != body && !mCacheConfig.isPingRequest(mUrl)) {
                mInputStream = new BufferedInputStream(body.byteStream());
                long length = mResponse.isRedirect() ? body.contentLength() + offset : body.contentLength();
                String mime = null == body.contentType() ? "" : body.contentType().toString();
                HttpProxyCacheSourceInfo info = new HttpProxyCacheSourceInfo(mUrl, mime, length);
                mCacheConfig.getCacheStorage().put(mUrl, info);
                Logger.e("cache source connection has opened with source info : " + info.toString() + " and offset = " + offset);
            }
        } catch (Throwable e) {
            throw new HttpProxyCacheException(e);
        }
    }

    @Override
    public long length() throws HttpProxyCacheException {
        if (mCacheConfig.isPingRequest(mUrl)) {
            return 0;
        }
        HttpProxyCacheSourceInfo info = mCacheConfig.getCacheStorage().get(mUrl);
        long length = null == info ? 0 : info.length;
        if (length <= 0) {
            return fetchSourceInfo(fetchOkHttpConnection()).length;
        }
        return length;
    }

    @Override
    public String mime() throws HttpProxyCacheException {
        if (mCacheConfig.isPingRequest(mUrl)) {
            return "";
        }
        HttpProxyCacheSourceInfo info = mCacheConfig.getCacheStorage().get(mUrl);
        String mime = null == info ? null : info.mime;

        if (TextUtils.isEmpty(mime)) {
            return fetchSourceInfo(fetchOkHttpConnection()).mime;
        }
        return mime;
    }

    @Override
    public int read(byte[] buffer) throws HttpProxyCacheException {
        try {
            return mInputStream.read(buffer, 0, buffer.length);
        } catch (Throwable e) {
            throw new HttpProxyCacheException(e);
        }
    }

    @Override
    public HttpProxyCacheSource clone(HttpProxyCacheConfig config) throws HttpProxyCacheException {
        return new HttpProxyCacheOkSource();
    }

    @Override
    public void close() throws IOException {
        FileUtils.closeQuietly(mInputStream);
        FileUtils.closeQuietly(mResponse);
        mUrl = null;
        mInputStream = null;
        mResponse = null;
        mCacheConfig = null;
    }

    private HttpProxyCacheSourceInfo fetchSourceInfo(Response response) throws HttpProxyCacheException {
        if (null != response) {
            try {
                ResponseBody body = response.body();
                if (null != body) {
                    long length = body.contentLength();
                    String mime = null == body.contentType() ? "" : body.contentType().toString();
                    HttpProxyCacheSourceInfo info = new HttpProxyCacheSourceInfo(mUrl, mime, length);
                    mCacheConfig.getCacheStorage().put(mUrl, info);

                    Logger.e("cache source fetch source info has successful  info : " + info.toString());
                    return info;
                }
            } catch (Throwable e) {
                throw new HttpProxyCacheException(e);
            }
        }
        return null;
    }

    private Response openOkHttpConnection(long offset) throws HttpProxyCacheException {
        return generateConnection(offset, "GET");
    }

    private Response fetchOkHttpConnection() throws HttpProxyCacheException {
        return generateConnection(0, "HEAD");
    }

    private Response generateConnection(long offset, String method) throws HttpProxyCacheException {
        try {
            int redirectCount = 0;
            String sourUrl = mUrl;
            boolean redirect = false;
            do {
                Request.Builder builder = new Request.Builder();
                builder.url(sourUrl);
                builder.method(method, null);
                appendHttpHeaders(builder, mUrl);
                if (offset > 0) {
                    builder.addHeader("Range", "bytes=" + offset + "-");
                }
                Response response = mClient.newCall(builder.build()).execute();
                redirect = response.isRedirect();
                if (redirect) {
                    sourUrl = response.header("Location");
                    redirectCount++;
                    response.close();
                } else {
                    return response;
                }
                if (redirectCount > Constants.REDIRECT_COUNT) {
                    throw new HttpProxyCacheException("Too many redirects: " + redirectCount);
                }
            } while (redirect);
        } catch (Throwable e) {
            Logger.e(e);
        }
        throw new HttpProxyCacheException("source can't open connection for url : " + mUrl);
    }

    private void appendHttpHeaders(Request.Builder builder, String url) {
        if (null != mCacheConfig) {
            appendHttpHeaders(builder, mCacheConfig.getDependHeaders(), url);
            appendHttpHeaders(builder, mCacheConfig.getCacheHeaders(url), url);
        }
    }

    private void appendHttpHeaders(Request.Builder builder, HttpProxyCacheHeaders httpHeaders, String url) {
        if (null != builder && null != httpHeaders && !TextUtils.isEmpty(url)) {
            Map<String, String> headers = httpHeaders.appendHeaders(url);
            if (null != headers) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    if (null != header) {
                        String key = header.getKey();
                        String value = header.getValue();
                        if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                            builder.addHeader(key, value);
                        }
                    }
                }
            }
        }
    }
}
