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
import org.apache.http.HttpEntity;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class CountingMultipartEntity implements HttpEntity {
    private HttpEntity mDelegate;
    private Request.TransferProgressListener mListener;

    public CountingMultipartEntity(HttpEntity delegate,
                                   Request.TransferProgressListener listener) {
        super();
        mDelegate = delegate;
        mListener = listener;
    }

    public void consumeContent() throws IOException {
        mDelegate.consumeContent();
    }

    public InputStream getContent() throws IOException, IllegalStateException {
        return mDelegate.getContent();
    }

    public Header getContentEncoding() {
        return mDelegate.getContentEncoding();
    }

    public long getContentLength() {
        return mDelegate.getContentLength();
    }

    public Header getContentType() {
        return mDelegate.getContentType();
    }

    public boolean isChunked() {
        return mDelegate.isChunked();
    }

    public boolean isRepeatable() {
        return mDelegate.isRepeatable();
    }

    public boolean isStreaming() {
        return mDelegate.isStreaming();
    }

    public void writeTo(OutputStream outstream) throws IOException {
        mDelegate.writeTo(new CountingOutputStream(outstream, mListener));
    }

    private static class CountingOutputStream extends FilterOutputStream {
        private final Request.TransferProgressListener mListener;
        private long mTransferred = 0;

        public CountingOutputStream(final OutputStream out, final Request.TransferProgressListener listener) {
            super(out);
            mListener = listener;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            mTransferred += len;
            if (mListener != null) mListener.transferred(mTransferred);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            mTransferred++;
            if (mListener != null) mListener.transferred(mTransferred);
        }
    }
}
