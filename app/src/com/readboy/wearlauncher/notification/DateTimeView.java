package com.readboy.wearlauncher.notification;

import static android.text.format.DateUtils.DAY_IN_MILLIS;
import static android.text.format.DateUtils.HOUR_IN_MILLIS;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.text.format.DateUtils.YEAR_IN_MILLIS;
import static android.text.format.Time.getJulianDay;
//import android.app.ActivityThread;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Handler;
import android.text.format.Time;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import com.readboy.wearlauncher.LauncherApplication;
import com.readboy.wearlauncher.R;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
/**
 * Created by Administrator on 2017/6/19.
 */

public class DateTimeView extends TextView {
    private static final int SHOW_TIME = 0;
    private static final int SHOW_MONTH_DAY_YEAR = 1;

    Date mTime;
    long mTimeMillis;

    int mLastDisplay = -1;
    DateFormat mLastFormat;

    private long mUpdateTimeMillis;
    private static final ThreadLocal<ReceiverInfo> sReceiverInfo = new ThreadLocal<ReceiverInfo>();
    private String mNowText;
    private boolean mShowRelativeTime;

    public DateTimeView(Context context) {
        this(context, null);
    }

    public DateTimeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        boolean relative = true;
        setShowRelativeTime(relative);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ReceiverInfo ri = sReceiverInfo.get();
        if (ri == null) {
            ri = new ReceiverInfo();
            sReceiverInfo.set(ri);
        }
        ri.addView(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        final ReceiverInfo ri = sReceiverInfo.get();
        if (ri != null) {
            ri.removeView(this);
        }
    }

    public void setTime(long time) {
        Time t = new Time();
        t.set(time);
        mTimeMillis = t.toMillis(false);
        mTime = new Date(t.year-1900, t.month, t.monthDay, t.hour, t.minute, 0);
        update();
    }

    public void setShowRelativeTime(boolean showRelativeTime) {
        mShowRelativeTime = showRelativeTime;
        updateNowText();
        update();
    }

    public void setVisibility(int visibility) {
        boolean gotVisible = visibility != GONE && getVisibility() == GONE;
        super.setVisibility(visibility);
        if (gotVisible) {
            update();
        }
    }

    void update() {
        if (mTime == null || getVisibility() == GONE) {
            return;
        }
        if (mShowRelativeTime) {
            updateRelativeTime();
            return;
        }

        int display;
        Date time = mTime;

        Time t = new Time();
        t.set(mTimeMillis);
        t.second = 0;

        t.hour -= 12;
        long twelveHoursBefore = t.toMillis(false);
        t.hour += 12;
        long twelveHoursAfter = t.toMillis(false);
        t.hour = 0;
        t.minute = 0;
        long midnightBefore = t.toMillis(false);
        t.monthDay++;
        long midnightAfter = t.toMillis(false);

        long nowMillis = System.currentTimeMillis();
        t.set(nowMillis);
        t.second = 0;
        nowMillis = t.normalize(false);

        // Choose the display mode
        choose_display: {
            if ((nowMillis >= midnightBefore && nowMillis < midnightAfter)
                    || (nowMillis >= twelveHoursBefore && nowMillis < twelveHoursAfter)) {
                display = SHOW_TIME;
                break choose_display;
            }
            // Else, show month day and year.
            display = SHOW_MONTH_DAY_YEAR;
            break choose_display;
        }

        // Choose the format
        DateFormat format;
        if (display == mLastDisplay && mLastFormat != null) {
            // use cached format
            format = mLastFormat;
        } else {
            switch (display) {
                case SHOW_TIME:
                    format = getTimeFormat();
                    break;
                case SHOW_MONTH_DAY_YEAR:
                    format = DateFormat.getDateInstance(DateFormat.SHORT);
                    break;
                default:
                    throw new RuntimeException("unknown display value: " + display);
            }
            mLastFormat = format;
        }

        // Set the text
        String text = format.format(mTime);
        setText(text);

        // Schedule the next update
        if (display == SHOW_TIME) {
            // Currently showing the time, update at the later of twelve hours after or midnight.
            mUpdateTimeMillis = twelveHoursAfter > midnightAfter ? twelveHoursAfter : midnightAfter;
        } else {
            // Currently showing the date
            if (mTimeMillis < nowMillis) {
                // If the time is in the past, don't schedule an update
                mUpdateTimeMillis = 0;
            } else {
                // If hte time is in the future, schedule one at the earlier of twelve hours
                // before or midnight before.
                mUpdateTimeMillis = twelveHoursBefore < midnightBefore
                        ? twelveHoursBefore : midnightBefore;
            }
        }
    }

    private void updateRelativeTime() {
        long now = System.currentTimeMillis();
        long duration = Math.abs(now - mTimeMillis);
        int count;
        long millisIncrease;
        boolean past = (now >= mTimeMillis);
        String result;
        if (duration < MINUTE_IN_MILLIS) {
            setText(mNowText);
            mUpdateTimeMillis = mTimeMillis + MINUTE_IN_MILLIS + 1;
            return;
        } else if (duration < HOUR_IN_MILLIS) {
            count = (int)(duration / MINUTE_IN_MILLIS);
            result = String.format(getContext().getResources().getQuantityString(past
                            ? R.plurals.duration_minutes_shortest
                            : R.plurals.duration_minutes_shortest_future,
                    count),
                    count);
            millisIncrease = MINUTE_IN_MILLIS;
        } else if (duration < DAY_IN_MILLIS) {
            count = (int)(duration / HOUR_IN_MILLIS);
            result = String.format(getContext().getResources().getQuantityString(past
                            ? R.plurals.duration_hours_shortest
                            : R.plurals.duration_hours_shortest_future,
                    count),
                    count);
            millisIncrease = HOUR_IN_MILLIS;
        } else if (duration < YEAR_IN_MILLIS) {
            // In weird cases it can become 0 because of daylight savings
            TimeZone timeZone = TimeZone.getDefault();
            count = Math.max(Math.abs(dayDistance(timeZone, mTimeMillis, now)), 1);
            result = String.format(getContext().getResources().getQuantityString(past
                            ? R.plurals.duration_days_shortest
                            : R.plurals.duration_days_shortest_future,
                    count),
                    count);
            if (past || count != 1) {
                mUpdateTimeMillis = computeNextMidnight(timeZone);
                millisIncrease = -1;
            } else {
                millisIncrease = DAY_IN_MILLIS;
            }

        } else {
            count = (int)(duration / YEAR_IN_MILLIS);
            result = String.format(getContext().getResources().getQuantityString(past
                            ? R.plurals.duration_years_shortest
                            : R.plurals.duration_years_shortest_future,
                    count),
                    count);
            millisIncrease = YEAR_IN_MILLIS;
        }
        if (millisIncrease != -1) {
            if (past) {
                mUpdateTimeMillis = mTimeMillis + millisIncrease * (count + 1) + 1;
            } else {
                mUpdateTimeMillis = mTimeMillis - millisIncrease * count + 1;
            }
        }
        setText(result);
    }

    /**
     * @param timeZone the timezone we are in
     * @return the timepoint in millis at UTC at midnight in the current timezone
     */
    private long computeNextMidnight(TimeZone timeZone) {
        Calendar c = Calendar.getInstance();
        c.setTimeZone(timeZone);
        c.add(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateNowText();
        update();
    }

    private void updateNowText() {
        if (!mShowRelativeTime) {
            return;
        }
        mNowText = getContext().getResources().getString(
                R.string.now_string_shortest);
    }

    // Return the date difference for the two times in a given timezone.
    private static int dayDistance(TimeZone timeZone, long startTime,
                                   long endTime) {
        return getJulianDay(endTime, timeZone.getOffset(endTime) / 1000)
                - getJulianDay(startTime, timeZone.getOffset(startTime) / 1000);
    }

    private DateFormat getTimeFormat() {
        return android.text.format.DateFormat.getTimeFormat(getContext());
    }

    void clearFormatAndUpdate() {
        mLastFormat = null;
        update();
    }

    public void onInitializeAccessibilityNodeInfoInternal(AccessibilityNodeInfo info) {
        if (mShowRelativeTime) {
            // The short version of the time might not be completely understandable and for
            // accessibility we rather have a longer version.
            long now = System.currentTimeMillis();
            long duration = Math.abs(now - mTimeMillis);
            int count;
            boolean past = (now >= mTimeMillis);
            String result;
            if (duration < MINUTE_IN_MILLIS) {
                result = mNowText;
            } else if (duration < HOUR_IN_MILLIS) {
                count = (int)(duration / MINUTE_IN_MILLIS);
                result = String.format(getContext().getResources().getQuantityString(past
                                ? R.plurals.duration_minutes_relative
                                : R.plurals.duration_minutes_relative_future,
                        count),
                        count);
            } else if (duration < DAY_IN_MILLIS) {
                count = (int)(duration / HOUR_IN_MILLIS);
                result = String.format(getContext().getResources().getQuantityString(past
                                ? R.plurals.duration_hours_relative
                                : R.plurals.duration_hours_relative_future,
                        count),
                        count);
            } else if (duration < YEAR_IN_MILLIS) {
                // In weird cases it can become 0 because of daylight savings
                TimeZone timeZone = TimeZone.getDefault();
                count = Math.max(Math.abs(dayDistance(timeZone, mTimeMillis, now)), 1);
                result = String.format(getContext().getResources().getQuantityString(past
                                ? R.plurals.duration_days_relative
                                : R.plurals.duration_days_relative_future,
                        count),
                        count);

            } else {
                count = (int)(duration / YEAR_IN_MILLIS);
                result = String.format(getContext().getResources().getQuantityString(past
                                ? R.plurals.duration_years_relative
                                : R.plurals.duration_years_relative_future,
                        count),
                        count);
            }
            info.setText(result);
        }
    }

    private static class ReceiverInfo {
        private final ArrayList<DateTimeView> mAttachedViews = new ArrayList<DateTimeView>();
        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_TIME_TICK.equals(action)) {
                    if (System.currentTimeMillis() < getSoonestUpdateTime()) {
                        // The update() function takes a few milliseconds to run because of
                        // all of the time conversions it needs to do, so we can't do that
                        // every minute.
                        return;
                    }
                }
                // ACTION_TIME_CHANGED can also signal a change of 12/24 hr. format.
                updateAll();
            }
        };

        private final ContentObserver mObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                updateAll();
            }
        };

        public void addView(DateTimeView v) {
            final boolean register = mAttachedViews.isEmpty();
            mAttachedViews.add(v);
            if (register) {
                register(getApplicationContextIfAvailable(v.getContext()));
            }
        }

        public void removeView(DateTimeView v) {
            mAttachedViews.remove(v);
            if (mAttachedViews.isEmpty()) {
                unregister(getApplicationContextIfAvailable(v.getContext()));
            }
        }

        void updateAll() {
            final int count = mAttachedViews.size();
            for (int i = 0; i < count; i++) {
                mAttachedViews.get(i).clearFormatAndUpdate();
            }
        }

        long getSoonestUpdateTime() {
            long result = Long.MAX_VALUE;
            final int count = mAttachedViews.size();
            for (int i = 0; i < count; i++) {
                final long time = mAttachedViews.get(i).mUpdateTimeMillis;
                if (time < result) {
                    result = time;
                }
            }
            return result;
        }

        static final Context getApplicationContextIfAvailable(Context context) {
            final Context ac = context.getApplicationContext();
            //return ac != null ? ac : ActivityThread.currentApplication().getApplicationContext();
            return ac != null ? ac : LauncherApplication.getApplication();
        }

        void register(Context context) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            context.registerReceiver(mReceiver, filter);
        }

        void unregister(Context context) {
            context.unregisterReceiver(mReceiver);
        }
    }
}

