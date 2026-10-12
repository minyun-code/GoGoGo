package com.zcshou.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import com.elvishew.xlog.XLog;
import com.zcshou.gogogo.MainActivity;
import com.zcshou.gogogo.R;
import com.zcshou.joystick.JoyStick;
import com.zcshou.utils.GoUtils;
import com.zcshou.utils.MockLocationEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceGo extends Service {
    public static final double DEFAULT_ALT = 55.0;
    public static final float GPS_ACCURACY_METERS = 5.0f;
    public static final float NETWORK_ACCURACY_METERS = 20.0f;
    public static final String REQUEST_ID = "POSITION_REQUEST_ID";
    private static final long MOVING_INTERVAL_MILLIS = 100;
    private static final long STATIONARY_INTERVAL_MILLIS = 1000;
    private static final int MAX_INJECTION_FAILURES = 3;
    private static final int NOTE_ID = 1;
    private static final String CHANNEL_ID = "SERVICE_GO_NOTE";
    private static final String ACTION_SHOW = "com.zcshou.gogogo.SHOW_JOYSTICK";
    private static final String ACTION_HIDE = "com.zcshou.gogogo.HIDE_JOYSTICK";
    private static final String ACTION_STOP = "com.zcshou.gogogo.STOP_LOCATION";

    public enum Status { STARTING, RUNNING, FAILED, STOPPED }

    public static final class Snapshot {
        public final Status status;
        public final MockLocationEngine.Position position;
        public final long requestId;
        public final int errorMessage;

        private Snapshot(Status status, MockLocationEngine.Position position, long requestId, int errorMessage) {
            this.status = status;
            this.position = position;
            this.requestId = requestId;
            this.errorMessage = errorMessage;
        }
    }

    public interface Listener {
        void onStateChanged(Snapshot snapshot);
    }

    private static volatile ServiceGo sActiveService;
    private static final Object PROVIDER_LOCK = new Object();
    private static final Map<String, ServiceGo> PROVIDER_OWNERS = new HashMap<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final MockLocationEngine mEngine = new MockLocationEngine();
    private final List<String> mRegisteredProviders = new ArrayList<>();
    private final ServiceGoBinder mBinder = new ServiceGoBinder();
    private volatile Snapshot mSnapshot = new Snapshot(Status.STARTING, null, 0, 0);
    private volatile boolean isStop;
    private final Object mStateLock = new Object();
    private LocationManager mLocManager;
    private HandlerThread mLocThread;
    private Handler mLocHandler;
    private JoyStick mJoyStick;
    private BroadcastReceiver mActionReceiver;
    private Listener mListener;
    private boolean mProvidersReady;
    private int mInjectionFailures;
    private long mRequestedId;
    private long mLastUiUpdate;
    private long mLastDeliveredRequestId;
    private boolean mFailureReported;

    public static boolean isActive() {
        ServiceGo service = sActiveService;
        return service != null && !service.isStop
                && service.mSnapshot.status != Status.FAILED;
    }

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            if (isStop || mSnapshot.status == Status.FAILED) {
                return;
            }
            try {
                MockLocationEngine.Position position = mEngine.advance(SystemClock.elapsedRealtime());
                inject(position);
                if (isStop) {
                    return;
                }
                mInjectionFailures = 0;
                publish(Status.RUNNING, position, 0);
                mLocHandler.postDelayed(this, position.speed > 0
                        ? MOVING_INTERVAL_MILLIS : STATIONARY_INTERVAL_MILLIS);
            } catch (RuntimeException e) {
                handleInjectionFailure(e);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sActiveService = this;
        mLocManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mLocThread = new HandlerThread("ServiceGoLocation", Process.THREAD_PRIORITY_DEFAULT);
        mLocThread.start();
        mLocHandler = new Handler(mLocThread.getLooper());
        try {
            // Enter foreground before inflating any map/overlay or registering providers.
            initNotification();
            initJoyStick();
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !intent.hasExtra(MainActivity.LNG_MSG_ID)
                || !intent.hasExtra(MainActivity.LAT_MSG_ID)) {
            fail(new IllegalArgumentException("A target position is required"));
        } else {
            setPosition(intent.getDoubleExtra(MainActivity.LNG_MSG_ID, Double.NaN),
                    intent.getDoubleExtra(MainActivity.LAT_MSG_ID, Double.NaN),
                    intent.getDoubleExtra(MainActivity.ALT_MSG_ID, DEFAULT_ALT),
                    intent.getLongExtra(REQUEST_ID, 0));
        }
        return START_NOT_STICKY;
    }

    private void setPosition(double longitude, double latitude, double altitude, long requestId) {
        mLocHandler.post(() -> {
            if (isStop || mSnapshot.status == Status.FAILED) {
                return;
            }
            try {
                mEngine.setPosition(longitude, latitude, altitude, SystemClock.elapsedRealtime());
                mRequestedId = requestId;
                ensureProviders();
                // This operation and every tick run on the same queue. There is only one tick chain.
                mLocHandler.removeCallbacks(mTick);
                mTick.run();
            } catch (RuntimeException e) {
                fail(e);
            }
        });
    }

    private void initJoyStick() {
        mJoyStick = new JoyStick(this);
        mJoyStick.setListener(new JoyStick.JoyStickClickListener() {
            @Override
            public void onMotionInfo(double speed, double bearing) {
                mLocHandler.post(() -> {
                    if (isStop || mSnapshot.status != Status.RUNNING) {
                        return;
                    }
                    try {
                        boolean wasMoving = mSnapshot.position != null && mSnapshot.position.speed > 0;
                        mEngine.setMotion(speed, bearing, SystemClock.elapsedRealtime());
                        if (!wasMoving || speed == 0) {
                            mLocHandler.removeCallbacks(mTick);
                            mTick.run();
                        }
                    } catch (RuntimeException e) {
                        fail(e);
                    }
                });
            }

            @Override
            public void onStepInfo(double distanceMeters, double bearing) {
                mLocHandler.post(() -> {
                    if (isStop || mSnapshot.status != Status.RUNNING) {
                        return;
                    }
                    try {
                        mEngine.step(distanceMeters, bearing, SystemClock.elapsedRealtime());
                        mLocHandler.removeCallbacks(mTick);
                        mTick.run();
                    } catch (RuntimeException e) {
                        fail(e);
                    }
                });
            }

            @Override
            public long onPositionInfo(double longitude, double latitude, double altitude) {
                long requestId = SystemClock.elapsedRealtimeNanos();
                setPosition(longitude, latitude, altitude, requestId);
                return requestId;
            }
        });
        mJoyStick.show();
    }

    @SuppressLint("WrongConstant")
    private void ensureProviders() {
        synchronized (PROVIDER_LOCK) {
            if (mProvidersReady) {
                return;
            }
            if (mLocManager == null) {
                throw new IllegalStateException("Location manager unavailable");
            }
            for (String provider : new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                boolean gps = LocationManager.GPS_PROVIDER.equals(provider);
                int power = gps ? Criteria.POWER_HIGH : Criteria.POWER_LOW;
                int accuracy = gps ? Criteria.ACCURACY_FINE : Criteria.ACCURACY_COARSE;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    power = gps ? ProviderProperties.POWER_USAGE_HIGH : ProviderProperties.POWER_USAGE_LOW;
                    accuracy = gps ? ProviderProperties.ACCURACY_FINE : ProviderProperties.ACCURACY_COARSE;
                }
                mLocManager.addTestProvider(provider, !gps, gps, !gps, !gps, true, true, true, power, accuracy);
                PROVIDER_OWNERS.put(provider, this);
                // Track registration before enable, so partial initialization is also cleaned up.
                mRegisteredProviders.add(provider);
                mLocManager.setTestProviderEnabled(provider, true);
            }
            mProvidersReady = true;
        }
    }

    private void inject(MockLocationEngine.Position position) {
        if (!GoUtils.hasLocationPermission(this) || !GoUtils.isGpsOpened(this)
                || !Settings.canDrawOverlays(this)) {
            throw new SecurityException("Location or overlay permission was revoked");
        }
        synchronized (PROVIDER_LOCK) {
            if (isStop) {
                return;
            }
            long wallTime = System.currentTimeMillis();
            long elapsedNanos = SystemClock.elapsedRealtimeNanos();
            for (String provider : mRegisteredProviders) {
                if (PROVIDER_OWNERS.get(provider) != this) {
                    throw new IllegalStateException("Mock provider ownership changed");
                }
                Location location = new Location(provider);
                location.setLatitude(position.latitude);
                location.setLongitude(position.longitude);
                location.setAltitude(position.altitude);
                location.setBearing((float) position.bearing);
                location.setSpeed((float) position.speed);
                location.setAccuracy(LocationManager.GPS_PROVIDER.equals(provider)
                        ? GPS_ACCURACY_METERS : NETWORK_ACCURACY_METERS);
                location.setTime(wallTime);
                location.setElapsedRealtimeNanos(elapsedNanos);
                mLocManager.setTestProviderLocation(provider, location);
            }
        }
    }

    private void handleInjectionFailure(RuntimeException error) {
        if (isStop) {
            return;
        }
        if (error instanceof SecurityException || error instanceof IllegalArgumentException
                || ++mInjectionFailures >= MAX_INJECTION_FAILURES) {
            fail(error);
        } else {
            XLog.w("Location injection failed; retry " + mInjectionFailures, error);
            mLocHandler.postDelayed(mTick, STATIONARY_INTERVAL_MILLIS);
        }
    }

    private void fail(RuntimeException error) {
        synchronized (mStateLock) {
            if (isStop) {
                return;
            }
            isStop = true;
            XLog.e("Location session failed", error);
            int message = error instanceof SecurityException ? R.string.app_service_permission_error
                    : error instanceof IllegalArgumentException ? R.string.app_error_input
                    : R.string.app_service_injection_error;
            publish(Status.FAILED, mSnapshot.position, message);
            mMainHandler.post(this::stopSelf);
        }
    }

    private void publish(Status status, MockLocationEngine.Position position, int errorMessage) {
        Snapshot previous;
        Snapshot snapshot;
        synchronized (mStateLock) {
            if (isStop && status == Status.RUNNING) {
                return;
            }
            previous = mSnapshot;
            snapshot = new Snapshot(status, position, mRequestedId, errorMessage);
            mSnapshot = snapshot;
        }
        long now = SystemClock.elapsedRealtime();
        if (status == Status.RUNNING && previous.status == status
                && previous.requestId == snapshot.requestId && now - mLastUiUpdate < 250) {
            return;
        }
        mLastUiUpdate = now;
        mMainHandler.post(() -> {
            // Render the latest complete snapshot even if a newer tick was throttled.
            Snapshot delivered = status == Status.RUNNING ? mSnapshot : snapshot;
            if (status == Status.RUNNING && (isStop || delivered.status != Status.RUNNING)) {
                return;
            }
            MockLocationEngine.Position shownPosition = delivered.position;
            if (mJoyStick != null && shownPosition != null && !isStop) {
                if (delivered.requestId != mLastDeliveredRequestId) {
                    mJoyStick.stopMovement();
                    mJoyStick.setCurrentPosition(shownPosition.longitude, shownPosition.latitude, shownPosition.altitude);
                    mJoyStick.acknowledgePosition(delivered.requestId);
                    mLastDeliveredRequestId = delivered.requestId;
                }
                mJoyStick.updateCurrentPosition(shownPosition.longitude, shownPosition.latitude,
                        shownPosition.altitude, shownPosition.bearing, GPS_ACCURACY_METERS);
            }
            if (mListener != null) {
                mListener.onStateChanged(delivered);
                if (status == Status.FAILED) {
                    mFailureReported = true;
                }
            } else if (status == Status.FAILED && !mFailureReported) {
                GoUtils.DisplayToast(this, getString(delivered.errorMessage));
                mFailureReported = true;
            }
        });
    }

    private void initNotification() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SHOW);
        filter.addAction(ACTION_HIDE);
        filter.addAction(ACTION_STOP);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_STOP.equals(intent.getAction())) {
                    stopSelf();
                } else if (mJoyStick != null) {
                    if (ACTION_SHOW.equals(intent.getAction())) {
                        try {
                            mJoyStick.show();
                        } catch (RuntimeException e) {
                            fail(e);
                        }
                    } else if (ACTION_HIDE.equals(intent.getAction())) {
                        mJoyStick.hide();
                    }
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        mActionReceiver = receiver;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("Notification manager unavailable");
        }
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW));
        PendingIntent content = PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.app_service_tips))
                .setContentIntent(content)
                .setOngoing(true)
                .addAction(0, getString(R.string.note_show), notificationAction(ACTION_SHOW))
                .addAction(0, getString(R.string.note_hide), notificationAction(ACTION_HIDE))
                .addAction(0, getString(R.string.note_stop), notificationAction(ACTION_STOP))
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTE_ID, notification);
        }
    }

    private PendingIntent notificationAction(String action) {
        return PendingIntent.getBroadcast(this, 0, new Intent(action).setPackage(getPackageName()),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onDestroy() {
        synchronized (mStateLock) {
            isStop = true;
            if (mSnapshot.status != Status.FAILED) {
                mSnapshot = new Snapshot(Status.STOPPED, mSnapshot.position, mSnapshot.requestId, 0);
            }
        }
        if (sActiveService == this) {
            sActiveService = null;
        }
        mMainHandler.removeCallbacksAndMessages(null);
        if (mListener != null) {
            mListener.onStateChanged(mSnapshot);
            if (mSnapshot.status == Status.FAILED) {
                mFailureReported = true;
            }
            mListener = null;
        } else if (mSnapshot.status == Status.FAILED && !mFailureReported) {
            GoUtils.DisplayToast(this, getString(mSnapshot.errorMessage));
        }
        // Finish any in-flight tick before cleanup; providers are owned by this queue.
        mLocHandler.removeCallbacksAndMessages(null);
        mLocHandler.post(() -> {
            synchronized (PROVIDER_LOCK) {
                for (String provider : mRegisteredProviders) {
                    if (PROVIDER_OWNERS.get(provider) != this) {
                        continue;
                    }
                    try {
                        mLocManager.removeTestProvider(provider);
                    } catch (RuntimeException e) {
                        XLog.e("Failed to remove mock provider: " + provider, e);
                    } finally {
                        PROVIDER_OWNERS.remove(provider);
                    }
                }
                mRegisteredProviders.clear();
            }
        });
        mLocThread.quitSafely();
        if (mJoyStick != null) {
            mJoyStick.destroy();
        }
        if (mActionReceiver != null) {
            unregisterReceiver(mActionReceiver);
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    public class ServiceGoBinder extends Binder {
        public Snapshot getSnapshot() {
            return mSnapshot;
        }

        public void setListener(Listener listener) {
            mListener = listener;
            if (listener != null) {
                listener.onStateChanged(mSnapshot);
            }
        }

        public void clearListener(Listener listener) {
            if (mListener == listener) {
                mListener = null;
            }
        }

        public void setPosition(double longitude, double latitude, double altitude, long requestId) {
            ServiceGo.this.setPosition(longitude, latitude, altitude, requestId);
        }
    }
}


