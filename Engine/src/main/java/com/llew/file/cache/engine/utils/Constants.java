package com.llew.file.cache.engine.utils;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/30
 */

public interface Constants {

    String HOST = "127.0.0.1";

    String CHARSET = "UTF-8";

    String PING = "ping";

    String PONG = "ping ok";

    int BUFFER_SIZE = 32 * 1024;

    int FILES_SIZE = 512 * 1024 * 1024;

    int INTERVAL = 1000;

    int ATTEMPTS = 3;

    int TIMEOUTS = 70;

    int REDIRECT_COUNT = 5;
}
