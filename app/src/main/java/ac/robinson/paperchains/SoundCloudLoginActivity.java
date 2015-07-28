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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ac.robinson.dualqrscanner.camera.CameraUtilities;

public class SoundCloudLoginActivity extends AppCompatActivity {

	public static final String ACCESS_TOKEN_RESULT = "access_token_result";

	private static final Pattern sAccessTokenPattern = Pattern.compile("access_token=(.*)&");

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); // for consistency with main activity
		setContentView(R.layout.soundcloud);

		// for consistency with other activities, and to avoid losing WebView data (no way to save) we don't mind
		// which way the screen is set up, as long as it doesn't rotate
		CameraUtilities.setScreenOrientationFixed(SoundCloudLoginActivity.this);

		// set up action bar
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setTitle(R.string.title_activity_soundcloud_login);
			actionBar.setDisplayShowTitleEnabled(true);
		}

		// load soundcloud API login page
		WebView webView = (WebView) findViewById(R.id.web_view);

		WebSettings webSettings = webView.getSettings();
		webSettings.setJavaScriptEnabled(true); // required; SoundCloud uses JavaScript for login
		webSettings.setJavaScriptCanOpenWindowsAutomatically(true); // required; login window is a popup

		SoundCloudWebViewClient webViewClient = new SoundCloudWebViewClient();
		webView.setWebViewClient(webViewClient);
		webView.loadUrl(PaperChainsActivity.SOUNDCLOUD_LOGIN_URL); // TODO: deal with lack of connection
	}

	private class SoundCloudWebViewClient extends WebViewClient {
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			// if the url has an access token we've succeeded; return
			Matcher matcher = sAccessTokenPattern.matcher(url);
			if (url.startsWith(PaperChainsActivity.SOUNDCLOUD_LOGIN_URL) && matcher.find()) {
				String token = matcher.group(1);

				Intent resultIntent = new Intent();
				resultIntent.putExtra(ACCESS_TOKEN_RESULT, token);
				setResult(Activity.RESULT_OK, resultIntent);

				finish();
				return true;
			}
			return super.shouldOverrideUrlLoading(view, url);
		}
	}
}
