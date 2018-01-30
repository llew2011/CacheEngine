package com.llew.file.cache.engine.core;

import android.text.TextUtils;

import com.llew.file.cache.engine.config.HttpProxyCacheCallback;
import com.llew.file.cache.engine.config.HttpProxyCacheConfig;
import com.llew.file.cache.engine.config.HttpProxyCacheSource;
import com.llew.file.cache.engine.exception.HttpProxyCacheException;
import com.llew.file.cache.engine.manager.HttpProxyCacheMainHandler;
import com.llew.file.cache.engine.manager.HttpProxyCacheProxyInstaller;
import com.llew.file.cache.engine.utils.Constants;
import com.llew.file.cache.engine.utils.FileUtils;
import com.llew.file.cache.engine.utils.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/28
 */

class HttpProxyCachePinger {

    private final ConcurrentHashMap<String, HttpProxyCacheClient> mClientMap = new ConcurrentHashMap<>();

    private ServerSocket mServerSocket;

    private Future mCacheFuture;

    private final int mSocketPort;

    private volatile boolean isServerRunning;

    private HttpProxyCacheConfig mCacheConfig;

    static HttpProxyCachePinger getPinger(HttpProxyCacheConfig config) throws HttpProxyCacheException {
        return new HttpProxyCachePinger(config);
    }

    private HttpProxyCachePinger(HttpProxyCacheConfig config) throws HttpProxyCacheException {
        try {
            this.isServerRunning = true;
            this.mCacheConfig = config;

            InetAddress inetAddress = InetAddress.getByName(Constants.HOST);
            this.mServerSocket = new ServerSocket(0, 8, inetAddress);
            this.mSocketPort = mServerSocket.getLocalPort();
            HttpProxyCacheProxyInstaller.install(Constants.HOST, mSocketPort);
            CountDownLatch countDownLatch = new CountDownLatch(1);
            this.mCacheFuture = mCacheConfig.getExecutorService().submit(new HttpProxyServerRunnable(countDownLatch));
            countDownLatch.await();
            Logger.e("server launched successful...");
        } catch (Throwable e) {
            shutDown();
            throw new HttpProxyCacheException(e);
        }
    }

    void shutDown() {
        try {
            isServerRunning = false;
            FileUtils.closeQuietly(mServerSocket);
            if (null != mCacheFuture) {
                mCacheFuture.cancel(true);
            }
            if (null != mClientMap) {
                Set<Map.Entry<String, HttpProxyCacheClient>> entrySet = mClientMap.entrySet();
                for (Map.Entry<String, HttpProxyCacheClient> entry : entrySet) {
                    if (null != entry && null != entry.getValue()) {
                        entry.getValue().shutdown();
                    }
                }
                mClientMap.clear();
            }
            mCacheConfig = null;
            mCacheFuture = null;
            mServerSocket = null;
        } catch (Throwable ignore) {
        }
    }

    void destroy(String url) {
        if (null != mClientMap && !TextUtils.isEmpty(url)) {
            HttpProxyCacheClient client = mClientMap.remove(url);
            if (null != client) {
                client.shutdown();
                client = null;
            }
        }
    }

    int getSocketPort() {
        return mSocketPort;
    }

    boolean ping() {
        if (null == mCacheConfig) return false;

        int maxAttempts = mCacheConfig.getMaxAttempts();
        int maxTimeouts = mCacheConfig.getMaxTimeouts();
        int attempts = 0;
        while (attempts < maxAttempts) {
            Future<Boolean> pingFuture = mCacheConfig.getExecutorService().submit(new PingCallable());
            try {
                boolean pinged = pingFuture.get(maxTimeouts, TimeUnit.MILLISECONDS);
                if (pinged) {
                    return true;
                }
            } catch (Throwable ignore) {
                Logger.e(ignore);
            }
            attempts++;
            maxTimeouts *= 2;
        }
        return false;
    }


    private class HttpProxyServerRunnable implements Runnable {

        private CountDownLatch mLatch;

        HttpProxyServerRunnable(CountDownLatch latch) {
            this.mLatch = latch;
        }

        @Override
        public void run() {
            if (null != mLatch) {
                mLatch.countDown();
                mLatch = null;
            }
            Logger.e("server  start  launching  ...");
            acceptSocket();
        }

        private void acceptSocket() {
            while (isServerRunning) {
                if (null != mServerSocket) {
                    try {
                        final Socket socket = mServerSocket.accept();
                        mCacheConfig.getExecutorService().submit(new HttpProxyCacheProcessor(socket));
                    } catch (Throwable e) {
                        Logger.e(e);
                    }
                } else {
                    Logger.e("server socket is null and quit loop");
                    break;
                }
            }
        }
    }

    private class PingCallable implements Callable<Boolean> {
        @Override
        public Boolean call() throws Exception {
            return pingServer();
        }

        private boolean pingServer() throws HttpProxyCacheException {
            final byte[] expectedResponse = Constants.PONG.getBytes();
            final byte[] buffer = new byte[expectedResponse.length];
            String pingUrl = getPingUrl();
            HttpProxyCacheSource source = null;
            try {
                source = mCacheConfig.getCacheSource().clone(mCacheConfig);
                source.init(mCacheConfig, pingUrl);
                source.open();
                source.read(buffer);
                return Arrays.equals(expectedResponse, buffer);
            } catch (Throwable e) {
                Logger.e(e);
            } finally {
                FileUtils.closeQuietly(source);
            }
            return false;
        }

        private String getPingUrl() {
            return String.format(Locale.US, "http://%s:%d/%s", Constants.HOST, mSocketPort, Constants.PING);
        }
    }


    private class HttpProxyCacheProcessor implements Runnable {

        private final Socket mSocket;
        private String mUrl;

        HttpProxyCacheProcessor(Socket socket) {
            this.mSocket = socket;
        }

        @Override
        public void run() {
            try {
                if (null != mSocket) {
                    HttpProxyCacheRequest request = HttpProxyCacheRequest.getRequest(mSocket.getInputStream());
                    Logger.e("new socket accepted, and " + request);
                    mUrl = request.url;
                    if (isPingRequest(mUrl)) {
                        responseToPing();
                    } else {
                        HttpProxyCacheClient client = getClient(mUrl);
                        client.processRequest(request, mSocket);
                    }
                }
            } catch (final Throwable e) {
                Logger.e(e);
                if (null != mCacheConfig) {
                    final HttpProxyCacheCallback callback = mCacheConfig.getCacheCallback(mUrl);
                    if (null != callback) {
                        HttpProxyCacheMainHandler.getIMPL().post(new Runnable() {
                            @Override
                            public void run() {
                                callback.error(mUrl, new HttpProxyCacheException(e));
                            }
                        });
                    }
                }
            }
        }

        private HttpProxyCacheClient getClient(String url) throws HttpProxyCacheException {
            HttpProxyCacheClient client = mClientMap.get(url);
            if (null == client) {
                client = new HttpProxyCacheClient(mCacheConfig, url);
                mClientMap.put(url, client);
            }
            return client;
        }

        private boolean isPingRequest(String url) {
            return Constants.PING.equals(url);
        }

        private void responseToPing() throws IOException {
            OutputStream outputStream = mSocket.getOutputStream();
            outputStream.write("HTTP/1.1 200 OK\n\n".getBytes());
            outputStream.write(Constants.PONG.getBytes());
            outputStream.flush();
        }
    }
}
