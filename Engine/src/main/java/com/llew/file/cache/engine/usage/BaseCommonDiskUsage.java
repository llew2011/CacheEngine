package com.llew.file.cache.engine.usage;

import com.llew.file.cache.engine.config.HttpProxyCacheConfig;
import com.llew.file.cache.engine.config.HttpProxyCacheUsage;
import com.llew.file.cache.engine.exception.HttpProxyCacheException;
import com.llew.file.cache.engine.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/28
 */

abstract class BaseCommonDiskUsage implements HttpProxyCacheUsage {

    private HttpProxyCacheConfig mCacheConfig;

    BaseCommonDiskUsage(HttpProxyCacheConfig cacheConfig) {
        this.mCacheConfig = cacheConfig;
    }

    @Override
    public void touch(File file) throws HttpProxyCacheException {
        try {
            // 1、update file time
            updateLastModifiedTime(file);

            // 2、get file as list
            List<File> files = getSortedFiles();

            // 3、remove if necessary
            long totalFiles = files.size();
            long totalSizes = getTotalSize(files);
            long fileSize;
            for (File f : files) {
                boolean accept = accept(f, totalFiles, totalSizes);
                if (!accept) {
                    fileSize = f.length();
                    if (f.delete()) {
                        totalSizes -= fileSize;
                        totalFiles--;
                    }
                }
            }
        } catch (Throwable ignore) {
            Logger.e(ignore);
        }
    }

    private void updateLastModifiedTime(File file) throws IOException {
        if (null != file && file.exists()) {
            long nowTime = System.currentTimeMillis();
            boolean success = file.setLastModified(nowTime);
            if (!success) {
                updateLastModifiedTimeInternal(file);
                if (file.lastModified() < nowTime) {
                    // NOTE: apparently this is a known issue (see: http://stackoverflow.com/questions/6633748/file-lastmodified-is-never-what-was-set-with-file-setlastmodified)
                    Logger.e("modify time failure : http://stackoverflow.com/questions/6633748/file-lastmodified-is-never-what-was-set-with-file-setlastmodified");
                }
            }
        }
    }

    private void recreateEmptyFile(File file) throws IOException {
        if (null != file) {
            if (!file.delete() || !file.createNewFile()) {
                throw new IOException("Error recreate zero-size file " + file);
            }
        }
    }

    private void updateLastModifiedTimeInternal(File file) throws IOException {
        if (null != file) {
            long size = file.length();
            if (0 == size) {
                recreateEmptyFile(file);
                return;
            }

            RandomAccessFile accessFile = new RandomAccessFile(file, "rwd");
            accessFile.seek(size - 1);
            byte lastByte = accessFile.readByte();
            accessFile.seek(size - 1);
            accessFile.write(lastByte);
            accessFile.close();
        }
    }

    private List<File> getSortedFiles() {
        File[] files = mCacheConfig.getCacheRootDir().listFiles();
        if (null == files) {
            return new ArrayList<>();
        }
        List<File> fileList = Arrays.asList(files);
        Collections.sort(fileList, new FileComparator());
        return fileList;
    }

    private long getTotalSize(List<File> files) {
        long totalSize = 0;
        if (null != files) {
            for (File file : files) {
                totalSize += file.length();
            }
        }
        return totalSize;
    }

    private static class FileComparator implements Comparator<File> {
        @Override
        public int compare(File firstFile, File secondFile) {
            long first = firstFile.lastModified();
            long second = secondFile.lastModified();
            return first < second ? 1 : first == second ? 0 : -1;
        }
    }


    /**
     * delete current file or not
     *
     * @param file           the file that determine to keep or delete
     * @param totalFileCount total number of cached file
     * @param totalFileSize  total size of cached file
     * @return <code>true</code> means keep this file, otherwise delete it
     */
    public abstract boolean accept(File file, long totalFileCount, long totalFileSize);

}
