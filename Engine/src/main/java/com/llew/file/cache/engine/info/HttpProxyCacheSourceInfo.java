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
        final int prime = 31;
        return (prime + url.hashCode()) * prime + mime.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj) {
            return false;
        }
        if (getClass() != obj.getClass()) {
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