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

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

class OAuth2HttpRequestInterceptor implements HttpRequestInterceptor {
    @Override public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
        if (request == null) throw new IllegalArgumentException("HTTP request may not be null");
        if (context == null) throw new IllegalArgumentException("HTTP context may not be null");

        if (!request.getRequestLine().getMethod().equalsIgnoreCase("CONNECT")) {
            AuthState authState = (AuthState) context.getAttribute(ClientContext.TARGET_AUTH_STATE);
            if (authState != null) {
                AuthScheme authScheme = authState.getAuthScheme();
                if (authScheme != null && !authScheme.isConnectionBased()) {
                    try {
                        request.setHeader(authScheme.authenticate(null, request));
                    } catch (AuthenticationException ignored) {
                        // ignored
                    }
                }
            }
        }
    }
}
