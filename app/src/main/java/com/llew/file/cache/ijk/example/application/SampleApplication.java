package com.llew.file.cache.ijk.example.application;

import android.app.Application;

import com.llew.file.cache.engine.core.HttpProxyCacheServer;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2018/1/2
 */

public class SampleApplication extends Application {

    private HttpProxyCacheServer mCacheServer;

    private static SampleApplication sApp;

    @Override
    public void onCreate() {
        super.onCreate();
        sApp = this;
    }

    public static SampleApplication getApplication() {
        return sApp;
    }

    public HttpProxyCacheServer getProxyServer() {
        if (null == mCacheServer) {
            mCacheServer = new HttpProxyCacheServer.Builder(getApplication())
                    .build();
        }
        return mCacheServer;
    }

}
