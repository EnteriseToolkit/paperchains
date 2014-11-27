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

import org.apache.http.FormattedHeader;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeFactory;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.message.BasicHeaderValueParser;
import org.apache.http.message.HeaderValueParser;
import org.apache.http.message.ParserCursor;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;

import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class OAuth2Scheme implements AuthScheme {
    public HashMap<String, String> mParams;
    public HttpParams mHttpParams;
    private CloudAPI mApi;

    public static Pattern AUTHORIZATION_HEADER_PATTERN = Pattern.compile("^OAuth (\\w+)$");

    public OAuth2Scheme(CloudAPI api, HttpParams params) {
        mApi = api;
        mHttpParams = params;
        mParams = new HashMap<String, String>();
    }

    @Override public String getSchemeName() {
        return CloudAPI.OAUTH_SCHEME;
    }

    @Override public String getParameter(String name) {
        return mParams.get(name);
    }

    @Override public String getRealm() {
        return getParameter("realm");
    }

    @Override public boolean isConnectionBased() {
        return false;
    }

    @Override public boolean isComplete() {
        return true;
    }

    @Override public Header authenticate(Credentials credentials, HttpRequest request)
            throws AuthenticationException {
        final String usedToken = extractToken(request);
        // make sure only one refresh request gets sent out
        synchronized (OAuth2Scheme.class) {
            final Token apiToken = mApi.getToken();
            if (apiToken == null || apiToken.access == null || apiToken.access.equals(usedToken)) {
                if (mApi.invalidateToken() == null) {
                    // we actually need to refresh it ourselves
                    try {
                        mApi.refreshToken();
                    } catch (IOException e) {
                        throw new AuthenticationException("Error refreshing token", e);
                    } catch (IllegalStateException e) {
                        throw new AuthenticationException("Error refreshing token", e);
                    }
                }
            }
            return ApiWrapper.createOAuthHeader(mApi.getToken());
        }
    }

    @Override public void processChallenge(Header header) throws MalformedChallengeException {
        if (header == null) {
            throw new IllegalArgumentException("Header may not be null");
        }
        String authHeader = header.getName();
        if (!authHeader.equalsIgnoreCase(AUTH.WWW_AUTH)) {
            throw new MalformedChallengeException("Unexpected header name: " + authHeader);
        }

        CharArrayBuffer buffer;
        int pos;
        if (header instanceof FormattedHeader) {
            buffer = ((FormattedHeader) header).getBuffer();
            pos = ((FormattedHeader) header).getValuePos();
        } else {
            String s = header.getValue();
            if (s == null) {
                throw new MalformedChallengeException("Header value is null");
            }
            buffer = new CharArrayBuffer(s.length());
            buffer.append(s);
            pos = 0;
        }
        while (pos < buffer.length() && HTTP.isWhitespace(buffer.charAt(pos))) {
            pos++;
        }
        int beginIndex = pos;
        while (pos < buffer.length() && !HTTP.isWhitespace(buffer.charAt(pos))) {
            pos++;
        }
        int endIndex = pos;
        String s = buffer.substring(beginIndex, endIndex);
        if (!s.equalsIgnoreCase(getSchemeName())) {
            throw new MalformedChallengeException("Invalid scheme identifier: " + s);
        }
        HeaderValueParser parser = BasicHeaderValueParser.DEFAULT;
        ParserCursor cursor = new ParserCursor(pos, buffer.length());
        HeaderElement[] elements = parser.parseElements(buffer, cursor);
        if (elements.length == 0) {
            throw new MalformedChallengeException("Authentication challenge is empty");
        }
        for (HeaderElement element : elements) {
            this.mParams.put(element.getName(), element.getValue());
        }
    }

    static String extractToken(HttpRequest r) {
        return (r == null) ? null : extractToken(r.getFirstHeader(AUTH.WWW_AUTH_RESP));
    }

    static String extractToken(Header h) {
        if (h ==null || h.getValue() == null) return null;
        if (AUTH.WWW_AUTH_RESP.equalsIgnoreCase(h.getName())) {
            Matcher m = AUTHORIZATION_HEADER_PATTERN.matcher(h.getValue());
            return m.matches() ? m.group(1) : null;
        } else {
            return null;
        }
    }

    static class Factory implements AuthSchemeFactory {
        private CloudAPI api;

        public Factory(CloudAPI api) {
            this.api = api;
        }

        @Override
        public AuthScheme newInstance(HttpParams params) {
            return new OAuth2Scheme(api, params);
        }
    }

    static class EmptyCredentials implements Credentials {
        public static final Credentials INSTANCE = new EmptyCredentials();
        @Override public Principal getUserPrincipal() {
            return null;
        }

        @Override public String getPassword() {
            return null;
        }
    }
}
