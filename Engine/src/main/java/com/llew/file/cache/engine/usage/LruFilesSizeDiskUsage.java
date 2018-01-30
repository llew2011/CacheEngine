package com.llew.file.cache.engine.usage;

import com.llew.file.cache.engine.config.HttpProxyCacheConfig;

import java.io.File;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/28
 */

public class LruFilesSizeDiskUsage extends BaseCommonDiskUsage {

    private final long filesSize;

    public LruFilesSizeDiskUsage(HttpProxyCacheConfig cacheConfig, long filesSize) {
        super(cacheConfig);
        this.filesSize = filesSize;
    }

    @Override
    public boolean accept(File file, long totalFileCount, long totalFileSize) {
        return totalFileSize < this.filesSize;
    }
}