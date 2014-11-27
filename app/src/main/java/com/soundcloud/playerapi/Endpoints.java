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
 * Various SoundCloud API endpoints.
 * See <a href="http://developers.soundcloud.com/docs/api/">the API docs</a> for the most
 * recent listing.
 */
@SuppressWarnings({"UnusedDeclaration"})
public interface Endpoints {
    String TOKEN = "/oauth2/token";

    String TRACKS              = "/tracks";
    String TRACK_DETAILS       = "/tracks/%d";
    String TRACK_COMMENTS      = "/tracks/%d/comments";
    String TRACK_FAVORITERS    = "/tracks/%d/favoriters";
    String TRACK_PLAYS         = "/tracks/%d/plays";
    String TRACK_PERMISSIONS   = "/tracks/%d/permissions";

    String PLAYLISTS            = "/playlists";
    String PLAYLIST_DETAILS     = "/playlists/%d";
    String PLAYLIST_TRACKS      = "/playlists/%d/tracks";

    String USERS               = "/users";
    String USER_DETAILS        = "/users/%d";
    String USER_FOLLOWINGS     = "/users/%d/followings";
    String USER_FOLLOWERS      = "/users/%d/followers";
    String USER_TRACKS         = "/users/%d/tracks";
    String USER_FAVORITES      = "/users/%d/favorites";
    String USER_PLAYLISTS      = "/users/%d/playlists";

    String MY_DETAILS          = "/me";
    String MY_CONNECTIONS      = "/me/connections";
    String MY_ACTIVITIES       = "/me/activities/tracks";
    String MY_EXCLUSIVE_TRACKS = "/me/activities/tracks/exclusive";
    String MY_NEWS             = "/me/activities/all/own";
    String MY_TRACKS           = "/me/tracks";
    String MY_PLAYLISTS        = "/me/playlists";
    String MY_FAVORITES        = "/me/favorites";
    String MY_FAVORITE         = "/me/favorites/%d";
    String MY_FOLLOWERS        = "/me/followers";
    String MY_FOLLOWER         = "/me/followers/%d";
    String MY_FOLLOWINGS       = "/me/followings";
    String MY_FOLLOWING        = "/me/followings/%d";
    String MY_CONFIRMATION     = "/me/email-confirmations";
    String MY_FRIENDS          = "/me/connections/friends";
    String MY_DEVICES          = "/me/devices";

    String SUGGESTED_USERS     = "/users/suggested";

    String RESOLVE             = "/resolve";

    String SEND_PASSWORD       = "/passwords/reset-instructions";
    String CONNECT             = "/connect";
    String FACEBOOK_CONNECT    = "/connect/via/facebook";


}
