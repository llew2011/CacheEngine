package com.llew.file.cache.engine.config;

import android.text.TextUtils;

import com.llew.file.cache.engine.utils.FileUtils;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/28
 */

public interface HttpProxyCacheNameGenerator {

    /**
     * generate cache file name by url, default is {@link HttpProxyCacheNameGenerator#MD5}
     *
     * @param url the cache url
     * @return file name that must be unique
     */
    String generateName(String url);

    HttpProxyCacheNameGenerator MD5 = new HttpProxyCacheNameGenerator() {

        private static final int MAX_EXTENSION_LENGTH = 4;

        @Override
        public String generateName(String url) {
            String extension = getExtension(url);
            String name = FileUtils.getMd5(url);
            return TextUtils.isEmpty(extension) ? name : String.format("%s.%s", name, extension);
        }

        private String getExtension(String url) {
            int dotIndex = url.lastIndexOf('.');
            int slashIndex = url.lastIndexOf('/');
            return dotIndex != -1 && dotIndex > slashIndex && dotIndex + 2 + MAX_EXTENSION_LENGTH > url.length() ?
                    url.substring(dotIndex + 1, url.length()) : "";
        }
    };
}
