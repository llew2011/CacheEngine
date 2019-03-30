package com.llew.file.cache.engine.info;

import android.text.TextUtils;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/29
 */

public class HttpProxyCacheSourceInfo {

    public final String url;
    public final String mime;
    public final long length;

    public HttpProxyCacheSourceInfo(String url, String mime, long length) {
        this.url = null == url ? "" : url;
        this.mime = null == mime ? "" : mime;
        this.length = length;
    }

    @Override
    public int hashCode() {
        long prime = 0;
        prime = prime * 31 + (TextUtils.isEmpty(url) ? 0 : url.hashCode());
        prime = prime * 31 + (TextUtils.isEmpty(mime) ? 0 : mime.hashCode());
        prime = prime * 31 + length;
        return (int) prime;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof HttpProxyCacheSourceInfo)) {
            return false;
        }
        HttpProxyCacheSourceInfo info = (HttpProxyCacheSourceInfo) obj;
        return this.length == info.length && TextUtils.equals(this.url, info.url) && TextUtils.equals(this.mime, info.mime);
    }

    @Override
    public String toString() {
        return "SourceInfo = { url:" + url + ", mime = " + mime + ", length = " + length + "}";
    }
}