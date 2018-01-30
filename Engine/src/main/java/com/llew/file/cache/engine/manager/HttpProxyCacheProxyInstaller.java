package com.llew.file.cache.engine.manager;

import android.text.TextUtils;

import com.llew.file.cache.engine.exception.HttpProxyCacheException;
import com.llew.file.cache.engine.utils.Logger;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/29
 */

public final class HttpProxyCacheProxyInstaller extends ProxySelector {

    private static final List<Proxy> EMPTY = Arrays.asList(Proxy.NO_PROXY);

    private final ProxySelector mDefaultProxy;
    private final String mIgnoredHost;
    private final int mIgnoredPort;

    private HttpProxyCacheProxyInstaller(ProxySelector defaultProxy, String ignoredHost, int ignoredPort) {
        this.mIgnoredHost = ignoredHost;
        this.mIgnoredPort = ignoredPort;
        this.mDefaultProxy = defaultProxy;
    }

    public static void install(String host, int port) {
        ProxySelector defaultProxy = ProxySelector.getDefault();
        ProxySelector ignoredProxy = new HttpProxyCacheProxyInstaller(defaultProxy, host, port);
        ProxySelector.setDefault(ignoredProxy);
    }

    @Override
    public List<Proxy> select(URI uri) {
        return ignoredProxy(uri) ? EMPTY : mDefaultProxy.select(uri);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        if (!ignoredProxy(uri)) {
            mDefaultProxy.connectFailed(uri, sa, ioe);
        } else {
            String errMsg = "error uri : " + uri + " , address : " + sa.toString();
            Logger.e(new HttpProxyCacheException(errMsg, ioe));
        }
    }

    private boolean ignoredProxy(URI uri) {
        return null != uri && TextUtils.equals(mIgnoredHost, uri.getHost()) && mIgnoredPort == uri.getPort();
    }
}
