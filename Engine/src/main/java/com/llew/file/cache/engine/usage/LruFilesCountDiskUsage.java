package com.llew.file.cache.engine.usage;

import com.llew.file.cache.engine.config.HttpProxyCacheConfig;

import java.io.File;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/28
 */

public class LruFilesCountDiskUsage extends BaseCommonDiskUsage {

    private final int mFilesCount;

    public LruFilesCountDiskUsage(HttpProxyCacheConfig cacheConfig, int filesCount) {
        super(cacheConfig);
        this.mFilesCount = filesCount;
    }

    @Override
    public boolean accept(File file, long totalFileCount, long totalFileSize) {
        return totalFileCount < this.mFilesCount;
    }
}