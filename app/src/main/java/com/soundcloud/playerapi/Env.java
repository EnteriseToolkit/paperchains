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

import org.apache.http.HttpHost;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * The environment to operate against.
 * Use SANDBOX for testing your app, and LIVE for production applications.
 */
public enum Env {
    /** The main production site, http://soundcloud.com */
    LIVE("api.soundcloud.com", "soundcloud.com"),
    /** For testing, http://sandbox-soundcloud.com */
    @Deprecated
    SANDBOX("api.sandbox-soundcloud.com", "sandbox-soundcloud.com");

    public final HttpHost resourceHost, sslResourceHost, authResourceHost, sslAuthResourceHost;

    /**
     * @param resourceHost          the resource host
     * @param authResourceHost      the authentication resource host
     */
    Env(String resourceHost, String authResourceHost) {
        this.resourceHost = new HttpHost(resourceHost, 80, "http");
        sslResourceHost = new HttpHost(resourceHost, 443, "https");

        this.authResourceHost = new HttpHost(authResourceHost, 80, "http");
        sslAuthResourceHost = new HttpHost(authResourceHost, 443, "https");
    }

    public HttpHost getResourceHost(boolean secure) {
        return secure ? sslResourceHost : resourceHost;
    }

    public HttpHost getAuthResourceHost(boolean secure) {
        return secure ? sslAuthResourceHost : authResourceHost;
    }

    public URI getResourceURI(boolean secure) {
        return hostToUri(getResourceHost(secure));
    }

    public URI getAuthResourceURI(boolean secure) {
        return hostToUri(getAuthResourceHost(secure));
    }

    public boolean isApiHost(HttpHost host) {
        return ("http".equals(host.getSchemeName()) ||
               "https".equals(host.getSchemeName())) &&
                resourceHost.getHostName().equals(host.getHostName());
    }

    private static URI hostToUri(HttpHost host) {
        try {
            return new URI(host.getSchemeName(), host.getHostName(), null, null);
        } catch (URISyntaxException ignored) {
            throw new RuntimeException();
        }
    }
}
