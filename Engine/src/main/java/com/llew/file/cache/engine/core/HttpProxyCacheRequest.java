package com.llew.file.cache.engine.core;

import android.text.TextUtils;

import com.llew.file.cache.engine.utils.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/28
 */

class HttpProxyCacheRequest {

    private static final Pattern RANGE_HEADER_PATTERN = Pattern.compile("[R,r]ange:[ ]?bytes=(\\d*)-");
    private static final Pattern URL_PATTERN = Pattern.compile("GET /(.*) HTTP");

    final String url;
    final long offset;
    final boolean partial;

    static HttpProxyCacheRequest getRequest(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (null != inputStream) {
            InputStreamReader ir = new InputStreamReader(inputStream, Constants.CHARSET);
            BufferedReader br = new BufferedReader(ir);
            String line = null;
            while (!TextUtils.isEmpty(line = br.readLine())) {
                sb.append(line).append('\n');
            }
        }
        return new HttpProxyCacheRequest(sb.toString());
    }

    private HttpProxyCacheRequest(String request) throws UnsupportedEncodingException {
        long offset = findRangeOffset(request);
        this.url = URLDecoder.decode(findUri(request), Constants.CHARSET);
        this.offset = Math.max(0, offset);
        this.partial = offset > 0;
    }

    private long findRangeOffset(String request) {
        if (!TextUtils.isEmpty(request)) {
            Matcher matcher = RANGE_HEADER_PATTERN.matcher(request);
            if (matcher.find()) {
                String rangeValue = matcher.group(1);
                if (!TextUtils.isEmpty(rangeValue) && TextUtils.isDigitsOnly(rangeValue)) {
                    return Long.parseLong(rangeValue);
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private String findUri(String request) {
        if (!TextUtils.isEmpty(request)) {
            Matcher matcher = URL_PATTERN.matcher(request);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new IllegalArgumentException("Invalid request `" + request + "`: url not found!");
    }

    @Override
    public String toString() {
        return "Request = { url = " + url +", partial = " + partial + ", offset = " + offset +"}";
    }
}
