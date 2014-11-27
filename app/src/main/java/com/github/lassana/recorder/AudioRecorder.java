/*
 * Copyright (c) 2013, Nikolai Doronin
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.github.lassana.recorder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaRecorder;
import android.os.AsyncTask;

import java.io.File;
import java.io.IOException;

/**
 * @author lassana
 * @since 8/18/13
 */
public class AudioRecorder {

    public static enum Status {
        STATUS_UNKNOWN,
        STATUS_READY_TO_RECORD,
        STATUS_RECORDING,
        STATUS_RECORD_PAUSED
    }

    public static interface OnException {
        public void onException(Exception e);
    }

    public static interface OnStartListener extends OnException {
        public void onStarted();
    }

    public static interface OnPauseListener extends OnException {
        public void onPaused(String activeRecordFileName);
    }

    /**
     * @author lassana
     * @since 10/06/2013
     */
    public static class MediaRecorderConfig {
        private final int mAudioEncodingBitRate;
        private final int mAudioChannels;
        private final int mAudioSource;
        private final int mAudioEncoder;

        public static final MediaRecorderConfig DEFAULT =
                new MediaRecorderConfig(64 * 1024,              /* 64 Kib per second                                */
                        2,                                      /* Stereo                                           */
                        MediaRecorder.AudioSource.DEFAULT,      /* Default audio source (usually, phone microphone) */
                        ApiHelper.DEFAULT_AUDIO_ENCODER);       /* Default encoder for target Android version       */

        /**
         * Constructor.
         *
         * @param audioEncodingBitRate
         * Used for {@link android.media.MediaRecorder#setAudioEncodingBitRate}
         * @param audioChannels
         * Used for {@link android.media.MediaRecorder#setAudioChannels}
         * @param audioSource
         * Used for {@link android.media.MediaRecorder#setAudioSource}
         * @param audioEncoder
         * Used for {@link android.media.MediaRecorder#setAudioEncoder}
         */
        public MediaRecorderConfig(int audioEncodingBitRate, int audioChannels, int audioSource, int audioEncoder) {
            mAudioEncodingBitRate = audioEncodingBitRate;
            mAudioChannels = audioChannels;
            mAudioSource = audioSource;
            mAudioEncoder = audioEncoder;
        }

    }

    public class StartRecordTask extends AsyncTask<OnStartListener, Void, Exception> {

        private OnStartListener mOnStartListener;

        @Override
        protected Exception doInBackground(OnStartListener... params) {
            this.mOnStartListener = params[0];
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setAudioEncodingBitRate(mMediaRecorderConfig.mAudioEncodingBitRate);
            mMediaRecorder.setAudioChannels(mMediaRecorderConfig.mAudioChannels);
            mMediaRecorder.setAudioSource(mMediaRecorderConfig.mAudioSource);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setOutputFile(getTemporaryFileName());
            mMediaRecorder.setAudioEncoder(mMediaRecorderConfig.mAudioEncoder);

            Exception exception = null;
            try {
                mMediaRecorder.prepare();
                mMediaRecorder.start();
            } catch (IOException e) {
                exception = e;
            }
            return exception;
        }

        @Override
        protected void onPostExecute(Exception e) {
            super.onPostExecute(e);
            if (e == null) {
                setStatus(AudioRecorder.Status.STATUS_RECORDING);
	            if (mOnStartListener != null) {
		            mOnStartListener.onStarted();
	            }
            } else {
                setStatus(AudioRecorder.Status.STATUS_READY_TO_RECORD);
	            if (mOnStartListener != null) {
		            mOnStartListener.onException(e);
	            }
            }
        }
    }

    public class PauseRecordTask extends AsyncTask<OnPauseListener, Void, Exception> {
        private OnPauseListener mOnPauseListener;

        @Override
        protected Exception doInBackground(OnPauseListener... params) {
            mOnPauseListener = params[0];
            Exception exception = null;
            try {
                mMediaRecorder.stop();
                mMediaRecorder.release();
            } catch (Exception e) {
                exception = e;
            }
            if ( exception == null ) {
                appendToFile(mTargetRecordFileName, getTemporaryFileName());
            }
            return exception;
        }

        @Override
        protected void onPostExecute(Exception e) {
            super.onPostExecute(e);
            if (e == null) {
                setStatus(AudioRecorder.Status.STATUS_RECORD_PAUSED);
	            if (mOnPauseListener != null) {
		            mOnPauseListener.onPaused(mTargetRecordFileName);
	            }
            } else {
                setStatus(AudioRecorder.Status.STATUS_READY_TO_RECORD);
	            if (mOnPauseListener != null) {
		            mOnPauseListener.onException(e);
	            }
            }
        }
    }

    private Status mStatus;
    private MediaRecorder mMediaRecorder;
    private final String mTargetRecordFileName;
    private final Context mContext;
    private final MediaRecorderConfig mMediaRecorderConfig;

    private AudioRecorder(final Context context,
                          final String targetRecordFileName,
                          final MediaRecorderConfig mediaRecorderConfig) {
        mTargetRecordFileName = targetRecordFileName;
        mContext = context;
        mMediaRecorderConfig = mediaRecorderConfig;
        mStatus = Status.STATUS_UNKNOWN;
	    //noinspection ResultOfMethodCallIgnored
	    new File(getTemporaryFileName()).delete();
    }

    /**
     * Returns the ready-to-use AudioRecorder.
     * Uses {@link com.github.lassana.recorder.AudioRecorder.MediaRecorderConfig#DEFAULT} as
     * {@link android.media.MediaRecorder} config.
     */
    public static AudioRecorder build(final Context context,
                                      final String targetFileName) {
        return build(context, targetFileName, MediaRecorderConfig.DEFAULT);
    }

    /**
     * Returns the ready-to-use AudioRecorder.
     */
    public static AudioRecorder build(final Context context,
                                      final String targetFileName,
                                      final MediaRecorderConfig mediaRecorderConfig) {
        AudioRecorder rvalue = new AudioRecorder(context, targetFileName, mediaRecorderConfig);
        rvalue.mStatus = Status.STATUS_READY_TO_RECORD;
        return rvalue;
    }

    /**
     * Continues existing record or starts new one.
     */
    @SuppressLint("NewApi")
    public void start(final OnStartListener listener) {
        StartRecordTask task = new StartRecordTask();
        if (ApiHelper.HAS_EXECUTE_ON_EXECUTOR_METHOD) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, listener);
        } else {
            task.execute(listener);
        }
    }

    /**
     * Pauses active recording.
     */
    @SuppressLint("NewApi")
    public void pause(final OnPauseListener listener) {
        PauseRecordTask task = new PauseRecordTask();
        if (ApiHelper.HAS_EXECUTE_ON_EXECUTOR_METHOD) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, listener);
        } else {
            task.execute(listener);
        }
    }

    public Status getStatus() {
        return mStatus;
    }

    public String getRecordFileName() {
        return mTargetRecordFileName;
    }

    public boolean isRecording() {
        return mStatus == Status.STATUS_RECORDING;
    }

    public boolean isReady() {
        return mStatus == Status.STATUS_READY_TO_RECORD;
    }

    public boolean isPaused() {
        return mStatus == Status.STATUS_RECORD_PAUSED;
    }

	public int getMaxAmplitude() { return mMediaRecorder.getMaxAmplitude(); }

    private void setStatus(final Status status) {
        mStatus = status;
    }

    private String getTemporaryFileName() {
        return mContext.getCacheDir().getAbsolutePath() + File.separator + "tmprecord";
    }

    private void appendToFile(final String targetFileName, final String newFileName) {
        Mp4ParserWrapper.append(targetFileName, newFileName);
    }
}
