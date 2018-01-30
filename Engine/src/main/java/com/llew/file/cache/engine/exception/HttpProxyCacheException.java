package com.llew.file.cache.engine.exception;

import java.io.IOException;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/28
 */

public class HttpProxyCacheException extends IOException {

    public HttpProxyCacheException() {
        super();
    }

    public HttpProxyCacheException(String message) {
        super(message);
    }

    public HttpProxyCacheException(Throwable cause) {
        super(cause);
    }

    public HttpProxyCacheException(String message, Throwable throwable) {
        super(message, throwable);
    }
}