package com.llew.file.cache.engine.utils;

import android.util.Log;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/29
 */

public class Logger {

    private static boolean logEnable;

    public static void enable(boolean enable) {
        logEnable = enable;
    }

    public static void e(String message) {
        e("Logger", message);
    }

    public static void e(String tag, String message) {
        Log.e(tag, "Thread : " + Thread.currentThread() + " || message = 【" + message + "】");
    }

    public static void e(Throwable e) {
        if (logEnable && null != e) {
            e.printStackTrace();
        }
    }
}
