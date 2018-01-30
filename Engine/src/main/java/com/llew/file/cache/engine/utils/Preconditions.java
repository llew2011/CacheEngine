package com.llew.file.cache.engine.utils;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/31
 */

public class Preconditions {

    public static <T> T checkNotNull(T reference) {
        if (reference == null) {
            throw new NullPointerException();
        }
        return reference;
    }

}
