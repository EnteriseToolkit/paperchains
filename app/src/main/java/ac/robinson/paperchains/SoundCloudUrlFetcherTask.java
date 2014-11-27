/*
 * Copyright (c) 2014 Simon Robinson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ac.robinson.paperchains;

import android.os.AsyncTask;

import com.soundcloud.api.Endpoints;
import com.soundcloud.playerapi.ApiWrapper;
import com.soundcloud.playerapi.Request;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;

class SoundCloudUrlFetcherTask extends AsyncTask<Long, Void, String> {

	private final WeakReference<PaperChainsActivity> mContext;
	private final ApiWrapper mWrapper;

	private int errorReason = -1;

	public SoundCloudUrlFetcherTask(PaperChainsActivity context, ApiWrapper wrapper) {
		mContext = new WeakReference<PaperChainsActivity>(context);
		mWrapper = wrapper;
	}

	@Override
	protected String doInBackground(Long... trackIds) {
		if (trackIds.length < 1) {
			return null;
		}
		final long trackId = trackIds[0];

		try {
			HttpResponse trackResponse = mWrapper.get(Request.to(Endpoints.TRACK_DETAILS, trackId));
			if (trackResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				JSONObject trackJSON = new JSONObject(EntityUtils.toString(trackResponse.getEntity()));
				if (trackJSON.getBoolean("streamable")) { // should always be the case
					HttpResponse streamResponse = mWrapper.get(Request.to("/tracks/%d/stream", trackId));
					JSONObject streamJSON = new JSONObject(EntityUtils.toString(streamResponse.getEntity()));
					return streamJSON.getString("location");
				}
				errorReason = R.string.hint_soundcloud_load_too_early;
				return null;
			} else {
				errorReason = R.string.hint_soundcloud_load_too_early;
				return null;
			}
		} catch (IOException e) {
			return null;
		} catch (ParseException e) {
			return null;
		} catch (JSONException e) {
			return null;
		}
	}

	@Override
	protected void onPostExecute(String url) {
		// notify the activity
		PaperChainsActivity activity = mContext.get();
		if (activity != null) {
			if (url != null) {
				activity.streamAudioLoadCompleted(url);
			} else {
				activity.streamAudioLoadFailed(errorReason == -1 ? R.string.hint_soundcloud_load_failed : errorReason);
			}
		}
	}
}
