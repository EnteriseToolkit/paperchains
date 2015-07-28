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

import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;

import com.soundcloud.api.ApiWrapper;
import com.soundcloud.api.Endpoints;
import com.soundcloud.api.Params;
import com.soundcloud.api.Request;
import com.soundcloud.api.Token;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

class SoundCloudUploadTask extends AsyncTask<String, Long, JSONObject> {

	// how long to wait before removing the notification (after completion or failure)
	private static final int NOTIFICATION_REMOVAL_DELAY = 3500;

	private final WeakReference<PaperChainsActivity> mContext;

	private final NotificationManager mNotifyManager;
	private final NotificationCompat.Builder mBuilder;
	private final int notificationId = 1;

	private final ApiWrapper mApiWrapper;
	private final Token mAccessToken;
	private final String mClientId;
	private final String mUploadFileTitle;
	private final String mUploadFileDescription;
	private final String mPageId;
	private final Rect mAudioRect;

	public SoundCloudUploadTask(PaperChainsActivity context, ApiWrapper wrapper, Token accessToken, String pageId,
	                            Rect audioRect) {
		mContext = new WeakReference<>(context);

		// set up upload parameters
		mApiWrapper = wrapper;
		mAccessToken = accessToken;
		mClientId = context.getString(R.string.soundcloud_client_id);

		mUploadFileTitle = context.getString(R.string.audio_upload_file_title);
		mUploadFileDescription = context.getString(R.string.audio_upload_file_description);
		mPageId = pageId;
		mAudioRect = audioRect;

		// set up notifications
		mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		mBuilder = new NotificationCompat.Builder(context);
		mBuilder.setContentTitle(context.getString(R.string.audio_upload_title)).setContentText(context.getString(R
				.string.audio_upload_progress)).setSmallIcon(R.drawable.ic_notification).setColor(context.getResources
				().getColor(R.color.primary)).setAutoCancel(true);
	}

	@Override
	protected JSONObject doInBackground(String... uploadFiles) {
		if (uploadFiles.length < 1) {
			return null;
		}
		final File file = new File(uploadFiles[0]);
		try {
			// @formatter:off
			Request request = Request.to(Endpoints.TRACKS)
					.withFile(Params.Track.ASSET_DATA, file)
					.add(Params.Track.TITLE, mUploadFileTitle)
					.add(Params.Track.DESCRIPTION, mUploadFileDescription)
					.add(Params.Track.TYPE, "spoken")
					.add(Params.Track.GENRE, "storytelling")
					.add(Params.Track.TAG_LIST, "PaperChains " +
							"soundcloud:created-with-client-id=" + mClientId + " " +
							"paperchains:page-key=" + mPageId)
					.add(Params.Track.SHARING, Params.Track.PUBLIC)
					.add(Params.Track.STREAMABLE, true)
					.add(Params.Track.DOWNLOADABLE, true)
					.add(Params.Track.LICENSE, "cc-by-sa");
			// @formatter:on

			// add our app icon if possible
			final File artwork = getAppIconCacheFile(mContext.get());
			final long length;
			if (artwork != null) {
				request.withFile(Params.Track.ARTWORK_DATA, artwork);
				length = file.length() + artwork.length();
			} else {
				length = file.length();
			}

			// notify progress
			request.setProgressListener(new Request.TransferProgressListener() {
				@Override
				public void transferred(long l) throws IOException {
					if (isCancelled()) {
						throw new IOException("Upload cancelled");
					}
					publishProgress(l, length);
				}
			});

			// upload, then get the JSON response
			HttpResponse response = mApiWrapper.post(request.usingToken(mAccessToken));
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED) {
				return new JSONObject(EntityUtils.toString(response.getEntity()));
			}
			return null;

		} catch (IOException e) {
			return null;
		} catch (JSONException e) {
			return null;
		}
	}

	@Override
	protected void onProgressUpdate(Long... values) {
		// update the progress bar in the notification
		mBuilder.setProgress(values[1].intValue(), values[0].intValue(), false);
		mNotifyManager.notify(notificationId, mBuilder.build());
	}

	@Override
	protected void onPostExecute(JSONObject response) {
		// check whether we succeeded
		boolean success = false;
		long trackId = -1;
		if (response != null) {
			try {
				trackId = response.getLong("id");
				success = true;
			} catch (JSONException ignored) {
			}
		}

		// notify the activity
		PaperChainsActivity activity = mContext.get();
		if (activity != null) {
			if (success) {
				mBuilder.setContentText(activity.getString(R.string.audio_upload_completed));
				activity.audioSaveCompleted(mAudioRect, trackId);
			} else {
				mBuilder.setContentText(activity.getString(R.string.audio_upload_failed));
				activity.audioSaveFailed(R.string.hint_audio_save_failed);
			}
		}

		// finish the notification and queue its removal
		mBuilder.setProgress(0, 0, false);
		mNotifyManager.notify(notificationId, mBuilder.build());
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				mNotifyManager.cancel(notificationId);
			}
		}, NOTIFICATION_REMOVAL_DELAY);
	}

	private File getAppIconCacheFile(Context context) {
		if (context == null) {
			return null;
		}

		// if we've already created the file, just return it
		File cacheFile = new File(context.getCacheDir(), "paperchains.png");
		if (cacheFile.exists()) {
			return cacheFile;
		}

		// otherwise, load from assets
		try {
			InputStream inputStream = context.getAssets().open("paperchains.png");
			// suppressed as it requires API level 19
			//noinspection TryFinallyCanBeTryWithResources
			try {
				FileOutputStream outputStream = new FileOutputStream(cacheFile);
				//noinspection TryFinallyCanBeTryWithResources
				try {
					byte[] buf = new byte[1024];
					int len;
					while ((len = inputStream.read(buf)) > 0) {
						outputStream.write(buf, 0, len);
					}
				} finally {
					outputStream.close();
				}
			} finally {
				inputStream.close();
			}
		} catch (IOException e) {
			return null;
		}
		return cacheFile;
	}
}
