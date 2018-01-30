package com.llew.file.cache.engine.utils;

import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.llew.file.cache.engine.exception.HttpProxyCacheException;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/28
 */

public class FileUtils {

    public static void createDirs(File file) {
        if (null != file && !file.exists()) {
            boolean success = file.mkdirs();
            if (success) {
                Logger.e(file.getName() + " has successfully created");
            } else {
                Logger.e(file.getName() + " hasn't successfully created");
            }
        }
    }

    public static String getMd5(String string) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digestBytes = md5.digest(string.getBytes());
            return convertToHexString(digestBytes);
        } catch (Exception e) {
            Logger.e(e);
        }
        return "";
    }

    public static String getMd5(File file) {
        FileInputStream fileInputStream = null;
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            fileInputStream = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fileInputStream.read(buffer)) != -1) {
                md5.update(buffer, 0, length);
            }
            return convertToHexString(md5.digest());
        } catch (Exception e) {
            return "";
        } finally {
            closeQuietly(fileInputStream);
        }
    }

    private static String convertToHexString(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(Integer.toHexString(0xff & b));
        }
        return sb.toString();
    }

    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable instanceof Socket) {
                Socket socket = (Socket) closeable;
                closeQuietly(socket.getOutputStream());
                closeQuietly(socket.getInputStream());
            }
            if (null != closeable) {
                closeable.close();
                closeable = null;
            }
        } catch (Exception e) {
            Logger.e(e);
        }
    }

    public static void deleteFile(File file) {
        if (null != file && file.exists()) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (null != files) {
                    for (File f : files) {
                        deleteFile(f);
                    }
                }
            }
            file.delete();
        }
    }

    public static boolean copy(File src, File dst) throws HttpProxyCacheException {
        if (null == src || null == dst) {
            Logger.e("src or dst is null !!!");
            return false;
        }
        if (!src.exists()) {
            Logger.e("src not exist !!!");
            return false;
        }
        if (!dst.getParentFile().exists()) {
            boolean mkdirs = dst.getParentFile().mkdirs();
            Logger.e("mkdirs = " + mkdirs);
            if (!mkdirs) {
                return false;
            }
        }

        if (!dst.exists()) {
            try {
                if (!dst.createNewFile()) {
                    return false;
                }
            } catch (Exception e) {
                throw new HttpProxyCacheException(e);
            }
        }

        FileInputStream fileInputStream = null;
        FileOutputStream fileOutputStream = null;

        FileChannel fileInputChannel = null;
        FileChannel fileOutputChannel = null;

        try {
            fileInputStream = new FileInputStream(src);
            fileOutputStream = new FileOutputStream(dst);

            fileInputChannel = fileInputStream.getChannel();
            fileOutputChannel = fileOutputStream.getChannel();

            fileInputChannel.transferTo(0, fileInputChannel.size(), fileOutputChannel);
            return true;
        } catch (Throwable e) {
            Logger.e(e);
        } finally {
            closeQuietly(fileInputStream);
            closeQuietly(fileInputChannel);
            closeQuietly(fileOutputStream);
            closeQuietly(fileOutputChannel);
        }
        return false;
    }

    public static String getSupposablyMime(String url) {
        MimeTypeMap mimes = MimeTypeMap.getSingleton();
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        return TextUtils.isEmpty(extension) ? null : mimes.getMimeTypeFromExtension(extension);
    }
}
