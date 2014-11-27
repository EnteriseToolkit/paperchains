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

/**
 * Request parameters for various objects.
 */
public interface Params {
    /**
     * see <a href="http://developers.soundcloud.com/docs/api/tracks">developers.soundcloud.com/docs/api/tracks</a>
     */
    @SuppressWarnings({"UnusedDeclaration"})
    interface Track {
        String TITLE         = "track[title]";          // required
        String TYPE          = "track[track_type]";
        String DESCRIPTION   = "track[description]";
        String ASSET_DATA    = "track[asset_data]";
        String ARTWORK_DATA  = "track[artwork_data]";
        String POST_TO       = "track[post_to][][id]";
        String POST_TO_EMPTY = "track[post_to][]";
        String TAG_LIST      = "track[tag_list]";
        String PERMALINK     = "track[permalink]";
        String SHARING       = "track[sharing]";
        String STREAMABLE    = "track[streamable]";
        String DOWNLOADABLE  = "track[downloadable]";
        String GENRE         = "track[genre]";
        String RELEASE       = "track[release]";
        String RELEASE_DAY   = "track[release_day]";
        String RELEASE_MONTH = "track[release_month]";
        String RELEASE_YEAR  = "track[release_year]";
        String PURCHASE_URL  = "track[purchase_url]";
        String LABEL_NAME    = "track[label_name]";
        String LABEL_ID      = "track[label_id]";
        String VIDEO_URL     = "track[video_url]";
        String ISRC          = "track[isrc]";
        String KEY_SIGNATURE = "track[key_signature]";
        String BPM           = "track[bpm]";
        String LICENSE       = "track[license]";
        String SHARED_EMAILS = "track[shared_to][emails][][address]";
        String SHARED_IDS    = "track[shared_to][users][][id]";
        String SHARING_NOTE  = "track[sharing_note]";
        String PUBLIC        = "public";
        String PRIVATE       = "private";
    }

    /**
     * see <a href="http://developers.soundcloud.com/docs/api/users">developers.soundcloud.com/docs/api/users</a>
     */
    @SuppressWarnings({"UnusedDeclaration"})
    interface User {
        String NAME                  = "user[username]";
        String FULLNAME              = "user[full_name]";
        String DESCRIPTION           = "user[description]";
        String CITY                  = "user[city]";
        String PERMALINK             = "user[permalink]";
        String DISCOGS_NAME          = "user[discogs_name]";
        String MYSPACE_NAME          = "user[myspace_name]";
        String WEBSITE               = "user[website]";
        String WEBSITE_TITLE         = "user[website_title]";
        String EMAIL                 = "user[email]";
        String PASSWORD              = "user[password]";
        String PASSWORD_CONFIRMATION = "user[password_confirmation]";
        String TERMS_OF_USE          = "user[terms_of_use]";
        String AVATAR                = "user[avatar_data]";
    }

    /**
     * see <a href="http://developers.soundcloud.com/docs/api/comments">developers.soundcloud.com/docs/api/comments</a>
     */
    @SuppressWarnings({"UnusedDeclaration"})
    interface Comment {
        String BODY      = "comment[body]";
        String TIMESTAMP = "comment[timestamp]";
        String REPLY_TO  = "comment[reply_to]";
    }
}
