package com.llew.file.cache.engine.config;

import android.text.TextUtils;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/28
 */

public interface HttpProxyCacheUrlFilter {

    /**
     * determine to cache file or not, default cache all. see {@link com.llew.file.cache.engine.config.HttpProxyCacheUrlFilter#ANY}
     *
     * @param url the cache url
     * @return <code>true</code> means cache file, otherwise <code>false</code>
     */
    boolean accept(String url);

    HttpProxyCacheUrlFilter HTTP = new HttpProxyCacheUrlFilter() {
        @Override
        public boolean accept(String url) {
            return !TextUtils.isEmpty(url) && url.startsWith("http");
        }
    };

    HttpProxyCacheUrlFilter ANY = new HttpProxyCacheUrlFilter() {
        @Override
        public boolean accept(String url) {
            return true;
        }
    };
}
