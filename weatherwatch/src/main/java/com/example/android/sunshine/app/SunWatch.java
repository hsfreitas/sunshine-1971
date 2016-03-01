    /*
     * Copyright (C) 2014 The Android Open Source Project
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *      http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */

    package com.example.android.sunshine.app;

    import android.content.BroadcastReceiver;
    import android.content.Context;
    import android.content.Intent;
    import android.content.IntentFilter;
    import android.content.res.Resources;
    import android.graphics.Bitmap;
    import android.graphics.BitmapFactory;
    import android.graphics.Canvas;
    import android.graphics.Paint;
    import android.graphics.Rect;
    import android.graphics.Typeface;
    import android.os.Bundle;
    import android.os.Handler;
    import android.os.Message;
    import android.support.wearable.watchface.CanvasWatchFaceService;
    import android.support.wearable.watchface.WatchFaceStyle;
    import android.text.format.Time;
    import android.util.Log;
    import android.view.SurfaceHolder;
    import android.view.WindowInsets;

    import com.google.android.gms.common.ConnectionResult;
    import com.google.android.gms.common.api.GoogleApiClient;
    import com.google.android.gms.common.api.ResultCallback;
    import com.google.android.gms.wearable.DataApi;
    import com.google.android.gms.wearable.DataEvent;
    import com.google.android.gms.wearable.DataEventBuffer;
    import com.google.android.gms.wearable.DataItem;
    import com.google.android.gms.wearable.DataItemBuffer;
    import com.google.android.gms.wearable.DataMap;
    import com.google.android.gms.wearable.DataMapItem;
    import com.google.android.gms.wearable.Wearable;

    import java.lang.ref.WeakReference;
    import java.text.DateFormatSymbols;
    import java.util.Calendar;
    import java.util.Date;
    import java.util.GregorianCalendar;
    import java.util.TimeZone;
    import java.util.concurrent.TimeUnit;

    /**
     * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
     * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
     */
    public class SunWatch extends CanvasWatchFaceService {
        private static final Typeface NORMAL_TYPEFACE =
                Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

        /**
         * Update rate in milliseconds for interactive mode. We update once a second since seconds are
         * displayed in interactive mode.
         */
        private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

        /**
         * Handler message id for updating the time periodically in interactive mode.
         */
        private static final int MSG_UPDATE_TIME = 0;

        @Override
        public Engine onCreateEngine() {
            return new Engine();
        }

        private static class EngineHandler extends Handler {
            private final WeakReference<SunWatch.Engine> mWeakReference;

            public EngineHandler(SunWatch.Engine reference) {
                mWeakReference = new WeakReference<>(reference);
            }

            @Override
            public void handleMessage(Message msg) {
                SunWatch.Engine engine = mWeakReference.get();
                if (engine != null) {
                    switch (msg.what) {
                        case MSG_UPDATE_TIME:
                            engine.handleUpdateTimeMessage();
                            break;
                    }
                }
            }
        }

        private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks,
                GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener, ResultCallback<DataItemBuffer>
        {
            final Handler mUpdateTimeHandler = new EngineHandler(this);

            /*
            *
            *
            * */

            private boolean mRegisteredTimeZoneReceiver = false;
            private GoogleApiClient mGoogleApiClient;
            private Paint mBackgroundPaint;
            private Paint mTextPaint;
            private Paint mDateText;
            private Paint mMaxTempPaint;
            private Paint mMinTempPaint;
            private boolean mAmbient;
            private String mMinTemp = "";
            private String mMaxTemp = "";
            private Time mTime;
            private Calendar mCalendarT;
            private float mDateYOffSet;
            private String[] mDaysOfWeek;
            private String[] mMonthsOfYear;

            private int mWeatherImageId = -1;


            /*
          *
          *
          * */
            final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    mTime.clear(intent.getStringExtra("time-zone"));
                    mTime.setToNow();
                }
            };
            int mTapCount;

            private float mXOffset;
            private float mYOffset;



            /**
             * Whether the display supports fewer bits for each color in ambient mode. When true, we
             * disable anti-aliasing in ambient mode.
             */
            boolean mLowBitAmbient;

            @Override
            public void onCreate(SurfaceHolder holder) {
                super.onCreate(holder);

                setWatchFaceStyle(new WatchFaceStyle.Builder(SunWatch.this)
                        .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                        .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                        .setShowSystemUiTime(false)
                        .setAcceptsTapEvents(true)
                        .build());
                Resources resources = SunWatch.this.getResources();
                mYOffset = resources.getDimension(R.dimen.digital_y_offset);
                mDateYOffSet = resources.getDimension(R.dimen.digital_date_y_offset);



                mBackgroundPaint = new Paint();
                mBackgroundPaint.setColor(resources.getColor(R.color.digital_background));


                mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
                mDateText = createDateTextPaint(resources.getColor(R.color.digital_text));
                mMaxTempPaint = createMaxPaint(resources.getColor(R.color.digital_text));
                mMinTempPaint = createMinPaint(resources.getColor(R.color.digital_text));

                mTime = new Time();
                mCalendarT = new GregorianCalendar();
                Date mCurrentDateTime = new Date();
                mCalendarT.setTime(mCurrentDateTime);
                mDaysOfWeek = new DateFormatSymbols().getShortWeekdays();
                mMonthsOfYear = new DateFormatSymbols().getShortMonths();

                mGoogleApiClient = new GoogleApiClient.Builder(SunWatch.this)
                        .addApi(Wearable.API)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .build();
            }


            // Create a paint using builder pattern
            private Paint createDateTextPaint(int textColor) {
                Paint dataPaint = new Paint();
                dataPaint.setColor(textColor);
                dataPaint.setAntiAlias(true);

                dataPaint.setTextAlign(Paint.Align.CENTER);
                dataPaint.setStrokeWidth(2);
                return dataPaint;
            }


            private Paint createMaxPaint(int textColor){
                Paint maxTempPaint = new Paint();
                maxTempPaint.setColor(textColor);
                maxTempPaint.setAntiAlias(true);
                maxTempPaint.setStrokeWidth(3);
                maxTempPaint.setTypeface(NORMAL_TYPEFACE);
                maxTempPaint.setTextAlign(Paint.Align.CENTER);

                return maxTempPaint;
            }

            private Paint createMinPaint(int textColor){
                Paint minTempPaint = new Paint();
                minTempPaint.setColor(textColor);
                minTempPaint.setAntiAlias(true);
                minTempPaint.setStrokeWidth(2);
                return minTempPaint;
            }


            private Paint createTextPaint(int textColor) {
                Paint paint = new Paint();
                paint.setColor(textColor);
                paint.setTypeface(NORMAL_TYPEFACE);
                paint.setAntiAlias(true);
                return paint;
            }

            @Override
            public void onDestroy() {
                mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
                super.onDestroy();
            }

            @Override
            public void onVisibilityChanged(boolean visible) {
                super.onVisibilityChanged(visible);

                if (visible) {
                    registerReceiver();

                    // Update time zone in case it changed while we weren't visible.
                    mTime.clear(TimeZone.getDefault().getID());
                    mTime.setToNow();
                    mGoogleApiClient.connect();
                } else {
                    unregisterReceiver();

                    if(mGoogleApiClient != null && mGoogleApiClient.isConnected()){
                        mGoogleApiClient.disconnect();
                    }
                }

                // Whether the timer should be running depends on whether we're visible (as well as
                // whether we're in ambient mode), so we may need to start or stop the timer.
                updateTimer();
            }

            private void registerReceiver() {
                if (mRegisteredTimeZoneReceiver) {
                    return;
                }
                mRegisteredTimeZoneReceiver = true;
                IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
                SunWatch.this.registerReceiver(mTimeZoneReceiver, filter);
            }

            private void unregisterReceiver() {
                if (!mRegisteredTimeZoneReceiver) {
                    return;
                }
                mRegisteredTimeZoneReceiver = false;
                SunWatch.this.unregisterReceiver(mTimeZoneReceiver);
            }

            @Override
            public void onApplyWindowInsets(WindowInsets insets) {
                super.onApplyWindowInsets(insets);

                // Load resources that have alternate values for round watches.
                Resources resources = SunWatch.this.getResources();
                boolean isRound = insets.isRound();

                mXOffset = resources.getDimension(isRound
                        ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);

                float textSize = resources.getDimension(isRound
                        ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

                float dateTextSize = resources.getDimension(isRound
                        ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);
                float maxTempTextSize = resources.getDimension(isRound
                        ? R.dimen.digital_temp_text_size_round: R.dimen.digital_temp_text_size);
                float minTempTextSize = resources.getDimension(isRound
                        ? R.dimen.digital_min_temp_text_size_round: R.dimen.digital_min_temp_text_size);

                mTextPaint.setTextSize(textSize);
                mDateText.setTextSize(dateTextSize);
                mMinTempPaint.setTextSize(minTempTextSize);
                mMaxTempPaint.setTextSize(maxTempTextSize);

            }

            @Override
            public void onPropertiesChanged(Bundle properties) {
                super.onPropertiesChanged(properties);
                mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            }

            @Override
            public void onTimeTick() {
                super.onTimeTick();
                invalidate();
            }

            @Override
            public void onAmbientModeChanged(boolean inAmbientMode) {
                super.onAmbientModeChanged(inAmbientMode);
                if (mAmbient != inAmbientMode) {
                    //mAmbient = inAmbientMode;
                    mBackgroundPaint.setColor(inAmbientMode ? getResources().getColor(R.color.background) : getResources().getColor(R.color.digital_background));
                    if (mLowBitAmbient) {
                        mTextPaint.setAntiAlias(!inAmbientMode);
                        mDateText.setAntiAlias(!inAmbientMode);
                        mMaxTempPaint.setAntiAlias(!inAmbientMode);
                        mMinTempPaint.setAntiAlias(!inAmbientMode);

                    }
                    invalidate();
                }

                // Whether the timer should be running depends on whether we're visible (as well as
                // whether we're in ambient mode), so we may need to start or stop the timer.
                updateTimer();
            }

            /**
             * Captures tap event (and tap type) and toggles the background color if the user finishes
             * a tap.
             */
            @Override
            public void onTapCommand(int tapType, int x, int y, long eventTime) {
                Resources resources = SunWatch.this.getResources();
                switch (tapType) {
                    case TAP_TYPE_TOUCH:
                        // The user has started touching the screen.
                        break;
                    case TAP_TYPE_TOUCH_CANCEL:
                        // The user has started a different gesture or otherwise cancelled the tap.
                        break;
                    case TAP_TYPE_TAP:
                        // The user has completed the tap gesture.
                        mTapCount++;
                        mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                                R.color.background : R.color.digital_background));
                        break;
                }
                invalidate();
            }

            @Override
            public void onDraw(Canvas canvas, Rect bounds) {
                // Draw the background.

                    canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

                    // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
                    int vertical = 80;

                    mTime.setToNow();
                    String hourText = String.format("%d:%02d", mTime.hour, mTime.minute);
                 if(!mAmbient) {



                    canvas.drawText(hourText, bounds.width() / 4, mYOffset, mTextPaint);

                    String dateText = mDaysOfWeek[mCalendarT.get(Calendar.DAY_OF_WEEK)] + " "
                            + mMonthsOfYear[mCalendarT.get(Calendar.MONTH)] + " "
                            + mCalendarT.get(Calendar.DATE) + " "
                            + mCalendarT.get(Calendar.YEAR);


                    canvas.drawText(dateText.toUpperCase()
                            ,bounds.width() / 2
                            ,mDateYOffSet
                            ,mDateText);


                    canvas.drawText(mMaxTemp,bounds.width()/2,
                            bounds.height()/2 + vertical
                            ,mMaxTempPaint);

                    canvas.drawText(mMinTemp
                            , bounds.width() / 2 + 35
                            , bounds.height() / 2 + vertical
                            , mMinTempPaint);

                    if(mWeatherImageId != -1){
                        Bitmap icon = BitmapFactory.decodeResource(SunWatch.this.getResources()
                                ,WearUtility.getArtResourceForWeatherCondition(mWeatherImageId));
                        Paint paint= new Paint();
                        canvas.drawBitmap(icon, bounds.width() / 2 - SunWatch.this.getResources().getDimension(R.dimen.digital_weather_image_x_offset),
                                bounds.height() / 2 + vertical /2,
                                paint);
                    }



                }

            }

            /**
             * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
             * or stops it if it shouldn't be running but currently is.
             */
            private void updateTimer() {
                mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
                if (shouldTimerBeRunning()) {
                    mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
                }
            }

            /**
             * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
             * only run when we're visible and in interactive mode.
             */
            private boolean shouldTimerBeRunning() {
                return isVisible() && !isInAmbientMode();
            }

            /**
             * Handle updating the time periodically in interactive mode.
             */
            private void handleUpdateTimeMessage() {
                invalidate();
                if (shouldTimerBeRunning()) {
                    long timeMs = System.currentTimeMillis();
                    long delayMs = INTERACTIVE_UPDATE_RATE_MS
                            - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                    mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                }
            }

            @Override
            public void onConnected(Bundle bundle) {
                Log.d("Android Wear ", "connected");
                Wearable.DataApi.addListener(mGoogleApiClient,this);
                Wearable.DataApi.getDataItems(mGoogleApiClient).setResultCallback(this);

            }

            @Override
            public void onConnectionSuspended(int i) {
                Log.d("Android Wear", "Connection failed");

            }

            @Override
            public void onDataChanged(DataEventBuffer dataEventBuffer) {
                Log.d("Android Wear","data changed" + dataEventBuffer.toString());
                for (DataEvent event : dataEventBuffer) {
                    if (event.getType() == DataEvent.TYPE_CHANGED) {
                        DataItem item = event.getDataItem();
                        if (item.getUri().getPath().compareTo("/weather_data") == 0) {
                            DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                            mMinTemp = dataMap.getString("min_temp");
                            mMaxTemp = dataMap.getString("max_temp");
                            mWeatherImageId = dataMap.getInt("weather_image_id");
                        }
                    } else if (event.getType() == DataEvent.TYPE_DELETED) {
                        // DataItem deleted
                    }
                }
                dataEventBuffer.release();
                if (isVisible() && !isInAmbientMode()) {
                    invalidate();
                }

            }

            @Override
            public void onConnectionFailed(ConnectionResult connectionResult) {
                Log.d("Android Wear","Connection failed");
            }

            @Override
            public void onResult(DataItemBuffer dataItems) {
                for (DataItem dataItem:dataItems){
                    if (dataItem.getUri().getPath().compareTo("/weather_data") == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        mMinTemp = dataMap.getString("min_temp");
                        mMaxTemp = dataMap.getString("max_temp");
                        mWeatherImageId = dataMap.getInt("weather_image_id");

                    }
                }
                dataItems.release();
                if (isVisible() && !isInAmbientMode()) {
                    invalidate();
                }

            }



        }
    }
