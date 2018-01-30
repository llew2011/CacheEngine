package com.llew.file.cache.engine.manager;

import android.os.Handler;
import android.os.Looper;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/30
 */

public final class HttpProxyCacheMainHandler {

    private static HttpProxyCacheMainHandler sHandler;

    private Handler mHandler;

    private HttpProxyCacheMainHandler() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    public static HttpProxyCacheMainHandler getIMPL() {
        if (null == sHandler) {
            synchronized (HttpProxyCacheMainHandler.class) {
                if (null == sHandler) {
                    sHandler = new HttpProxyCacheMainHandler();
                }
            }
        }
        return sHandler;
    }

    public void post(Runnable runnable) {
        if (null == runnable) return;
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            runnable.run();
        } else {
            mHandler.post(runnable);
        }
    }

    public void post(Runnable runnable, long delayMillis) {
        if (null != runnable) {
            mHandler.postDelayed(runnable, delayMillis);
        }
    }
}
