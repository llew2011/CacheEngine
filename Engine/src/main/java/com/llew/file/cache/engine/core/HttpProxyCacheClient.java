package com.llew.file.cache.engine.core;

import com.llew.file.cache.engine.config.HttpProxyCacheConfig;
import com.llew.file.cache.engine.exception.HttpProxyCacheException;
import com.llew.file.cache.engine.utils.Logger;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

import static com.llew.file.cache.engine.utils.Preconditions.checkNotNull;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/28
 */

class HttpProxyCacheClient {

    private AtomicInteger mClientCount;

    private HttpProxyCacheEngine mCacheEngine;
    private HttpProxyCacheConfig mCacheConfig;
    private String mUrl;

    HttpProxyCacheClient(HttpProxyCacheConfig cacheConfig, String url) throws HttpProxyCacheException {
        this.mUrl = checkNotNull(url);
        this.mCacheConfig = checkNotNull(cacheConfig);
        this.mClientCount = new AtomicInteger(1);
        this.mCacheEngine = new HttpProxyCacheEngine(mCacheConfig, mUrl);
    }

    void processRequest(final HttpProxyCacheRequest request, final Socket socket) throws HttpProxyCacheException {
        beforeProcess();
        Logger.e("client start  process...");
        mCacheEngine.process(request, socket);
        Logger.e("client finish process...");
    }

    private void beforeProcess() throws HttpProxyCacheException {
        if (null == mCacheEngine) {
            Logger.e("recreate engine ...");
            mCacheEngine = new HttpProxyCacheEngine(mCacheConfig, mUrl);
        }
        if (null == mClientCount) {
            mClientCount = new AtomicInteger(1);
        }
        int value = mClientCount.getAndIncrement();
        Logger.e("client proxy  count  =  " + value);
    }

    void shutdown() {
        if (null != mCacheEngine) {
            Logger.e("client shutdown engine start...");
            mCacheEngine.shutdown();
            Logger.e("client shutdown engine stop ...");
        }
        mCacheEngine = null;
        mClientCount = null;
    }
}
