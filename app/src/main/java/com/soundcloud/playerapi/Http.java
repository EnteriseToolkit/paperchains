/*
 * The MIT License
 *
 * Copyright (c) 2011, SoundCloud Ltd., Jan Berkel
 * Portions Copyright (c) 2010 Xtreme Labs and Pivotal Labs
 * Portions Copyright (c) 2009 urbanSTEW
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.soundcloud.playerapi;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

/**
 * Helper class for various HTTP related functions.
 */
public class Http {
    private Http() {
    }

    /**
     * Returns a String representation of the response
     *
     * @param response an HTTP response
     * @return the content body
     * @throws IOException network error
     */
    public static String getString(HttpResponse response) throws IOException {
        InputStream is = response.getEntity().getContent();
        if (is == null) return null;

        int length = ApiWrapper.BUFFER_SIZE;
        Header contentLength = null;
        try {
            contentLength = response.getFirstHeader(HTTP.CONTENT_LEN);
        } catch (UnsupportedOperationException ignored) {
        }

        if (contentLength != null) {
            try {
                length = Integer.parseInt(contentLength.getValue());
            } catch (NumberFormatException ignored) {
            }
        }

        final StringBuilder sb = new StringBuilder(length);
        int n;
        byte[] buffer = new byte[ApiWrapper.BUFFER_SIZE];
        while ((n = is.read(buffer)) != -1) sb.append(new String(buffer, 0, n));
        return sb.toString();
    }

    public static JSONObject getJSON(HttpResponse response) throws IOException {
        final String json = getString(response);
        if (json == null || json.length() == 0) throw new IOException("JSON response is empty");
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new IOException("could not parse JSON document: "+e.getMessage()+" "+
                    (json.length() > 80 ? (json.substring(0, 79) + "..." ) : json));
        }
    }

    public static String etag(HttpResponse resp) {
        Header etag = resp.getFirstHeader("Etag");
        return etag != null ? etag.getValue() : null;
    }

    public static String formatJSON(String s) {
        try {
            return new JSONObject(s).toString(4);
        } catch (JSONException e) {
            try {
                return new JSONArray(s).toString(4);
            } catch (JSONException e2) {
                return s;
            }
        }
    }
}
