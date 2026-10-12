package com.zcshou.joystick;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PixelFormat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.SearchView;

import androidx.preference.PreferenceManager;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.sug.SuggestionSearch;
import com.elvishew.xlog.XLog;
import com.zcshou.database.DataBaseHistoryLocation;
import com.zcshou.gogogo.HistoryActivity;
import com.zcshou.gogogo.MainActivity;
import com.zcshou.gogogo.R;
import com.zcshou.gogogo.SearchResultAdapter;
import com.zcshou.utils.GoUtils;
import com.zcshou.utils.MapUtils;
import com.zcshou.utils.MapSearchUtils;
import com.zcshou.utils.InputUtils;
import com.zcshou.utils.MockLocationEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JoyStick extends View {
    private static final int WINDOW_TYPE_JOYSTICK = 0;
    private static final int WINDOW_TYPE_MAP = 1;
    private static final int WINDOW_TYPE_HISTORY = 2;

    private final Context mContext;
    private WindowManager.LayoutParams mWindowParamCurrent;
    private WindowManager mWindowManager;
    private int mCurWin = WINDOW_TYPE_JOYSTICK;
    private final LayoutInflater inflater;
    private boolean isWalk;
    private ImageButton btnWalk;
    private boolean isRun;
    private ImageButton btnRun;
    private boolean isBike;
    private ImageButton btnBike;
    private JoyStickClickListener mListener;

    // 移动
    private View mJoystickLayout;
    private boolean isMove;
    private boolean isDestroyed;
    private boolean mResettingMovement;
    private double mSpeed = 1.2;        /* 默认的速度，单位 m/s */
    private double mAltitude = 55.0;
    private float mCurrentBearing;
    private float mCurrentAccuracy;
    private double mAngle = 0;
    private double mR = 0;
    private final SharedPreferences sharedPreferences;
    /* 历史记录悬浮窗相关 */
    private FrameLayout mHistoryLayout;
    private final List<Map<String, Object>> mAllRecord = new ArrayList<> ();
    private TextView noRecordText;
    private ListView mRecordListView;
    /* 地图悬浮窗相关 */
    private FrameLayout mMapLayout;
    private MapView mMapView;
    private BaiduMap mBaiduMap;
    private LatLng mCurMapLngLat;
    private LatLng mMarkMapLngLat;
    private String mMarkMapName;
    private long mPendingPositionId;
    private LatLng mPendingMapPoint;
    private SuggestionSearch mSuggestionSearch;
    private ListView mSearchList;
    private LinearLayout mSearchLayout;

    public JoyStick(Context context) {
        super(context);
        this.mContext = context;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);

        initWindowManager();

        inflater = LayoutInflater.from(mContext);

        if (inflater != null) {
            initJoyStickView();

            initJoyStickMapView();

            initHistoryView();
        }
    }

    public JoyStick(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mContext = context;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);

        initWindowManager();

        inflater = LayoutInflater.from(mContext);

        if (inflater != null) {
            initJoyStickView();

            initJoyStickMapView();

            initHistoryView();
        }
    }

    public JoyStick(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);

        initWindowManager();

        inflater = LayoutInflater.from(mContext);

        if (inflater != null) {
            initJoyStickView();

            initJoyStickMapView();

            initHistoryView();
        }
    }

    public void setCurrentPosition(double lng, double lat, double alt) {
        double[] lngLat = MapUtils.wgs2bd09(lng, lat);
        mCurMapLngLat = new LatLng(lngLat[1], lngLat[0]);
        mAltitude = alt;
        mCurrentBearing = 0;

        if (mMarkMapLngLat == null || (mPendingMapPoint != null
                && mMarkMapLngLat.latitude == mPendingMapPoint.latitude
                && mMarkMapLngLat.longitude == mPendingMapPoint.longitude)) {
            resetBaiduMap();
        }
    }

    public void acknowledgePosition(long requestId) {
        if (mPendingPositionId != 0 && requestId >= mPendingPositionId) {
            if (requestId == mPendingPositionId) {
                GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_location_ok));
            }
            mPendingPositionId = 0;
            mPendingMapPoint = null;
        }
    }

    public void updateCurrentPosition(double lng, double lat, double alt, double bearing, float accuracy) {
        if (isDestroyed) {
            return;
        }
        double[] mapPosition = MapUtils.wgs2bd09(lng, lat);
        mCurMapLngLat = new LatLng(mapPosition[1], mapPosition[0]);
        mAltitude = alt;
        mCurrentBearing = (float) bearing;
        mCurrentAccuracy = accuracy;
        // Refresh the location layer without clearing the user's selected destination or moving the camera.
        if (mMapLayout.getParent() != null) {
            mBaiduMap.setMyLocationData(new MyLocationData.Builder()
                    .latitude(mCurMapLngLat.latitude).longitude(mCurMapLngLat.longitude)
                    .direction((float) bearing).accuracy(accuracy).build());
        }
    }

    public void stopMovement() {
        isMove = false;
        mR = 0;
        mResettingMovement = true;
        try {
            ((RockerView) mJoystickLayout.findViewById(R.id.joystick_rocker)).resetPosition();
            ((ButtonView) mJoystickLayout.findViewById(R.id.joystick_button)).resetDirection();
        } finally {
            mResettingMovement = false;
        }
    }

    public void show() {
        switch (mCurWin) {
            case WINDOW_TYPE_MAP:
                if (mJoystickLayout.getParent() != null) {
                    mWindowManager.removeView(mJoystickLayout);
                }
                if (mHistoryLayout.getParent() != null) {
                    mWindowManager.removeView(mHistoryLayout);
                }
                if (mMapLayout.getParent() == null) {
                    resetBaiduMap();
                    mWindowManager.addView(mMapLayout, mWindowParamCurrent);
                }
                break;
            case WINDOW_TYPE_HISTORY:
                if (mMapLayout.getParent() != null) {
                    mWindowManager.removeView(mMapLayout);
                }
                if (mJoystickLayout.getParent() != null) {
                    mWindowManager.removeView(mJoystickLayout);
                }
                if (mHistoryLayout.getParent() == null) {
                    mWindowManager.addView(mHistoryLayout, mWindowParamCurrent);
                }
                break;
            case WINDOW_TYPE_JOYSTICK:
                if (mMapLayout.getParent() != null) {
                    mWindowManager.removeView(mMapLayout);
                }
                if (mHistoryLayout.getParent() != null) {
                    mWindowManager.removeView(mHistoryLayout);
                }
                if (mJoystickLayout.getParent() == null) {
                    mWindowManager.addView(mJoystickLayout, mWindowParamCurrent);
                }
                break;
        }
    }

    public void hide() {
        if (mMapLayout.getParent() != null) {
            mWindowManager.removeViewImmediate(mMapLayout);
        }

        if (mJoystickLayout.getParent() != null) {
            mWindowManager.removeViewImmediate(mJoystickLayout);
        }

        if (mHistoryLayout.getParent() != null) {
            mWindowManager.removeViewImmediate(mHistoryLayout);
        }
    }

    public void destroy() {
        if (isDestroyed) {
            return;
        }
        isDestroyed = true;
        isMove = false;
        mListener = null;
        if (mMapLayout.getParent() != null) {
            mWindowManager.removeViewImmediate(mMapLayout);
        }

        if (mJoystickLayout.getParent() != null) {
            mWindowManager.removeViewImmediate(mJoystickLayout);
        }

        if (mHistoryLayout.getParent() != null) {
            mWindowManager.removeViewImmediate(mHistoryLayout);
        }

        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
        if (mSuggestionSearch != null) {
            mSuggestionSearch.destroy();
        }
    }

    public void setListener(JoyStickClickListener mListener) {
        this.mListener = mListener;
    }

    private void initWindowManager() {
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mWindowParamCurrent = new WindowManager.LayoutParams();
        mWindowParamCurrent.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        mWindowParamCurrent.format = PixelFormat.RGBA_8888;
        mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE      // 不添加这个将导致游戏无法启动（MIUI12）,添加之后导致键盘无法显示
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        mWindowParamCurrent.gravity = Gravity.START | Gravity.TOP;
        mWindowParamCurrent.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParamCurrent.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParamCurrent.x = 300;
        mWindowParamCurrent.y = 300;
    }

    @SuppressLint("InflateParams")
    private void initJoyStickView() {
        mSpeed = readSpeed("setting_walk", 1.2);
        mJoystickLayout = inflater.inflate(R.layout.joystick, null);

        /* 整个摇杆拖动事件处理 */
        mJoystickLayout.setOnTouchListener(new JoyStickOnTouchListener());

        /* 位置按钮点击事件处理 */
        ImageButton btnPosition = mJoystickLayout.findViewById(R.id.joystick_position);
        btnPosition.setOnClickListener(v -> {
            if (mMapLayout.getParent() == null) {
                mCurWin = WINDOW_TYPE_MAP;
                show();
            }
        });

        /* 历史按钮点击事件处理 */
        ImageButton btnHistory = mJoystickLayout.findViewById(R.id.joystick_history);
        btnHistory.setOnClickListener(v -> {
            if (mHistoryLayout.getParent() == null) {
                mCurWin = WINDOW_TYPE_HISTORY;
                show();
            }
        });

        /* 步行按键的点击处理 */
        btnWalk = mJoystickLayout.findViewById(R.id.joystick_walk);
        btnWalk.setOnClickListener(v -> {
            if (!isWalk) {
                btnWalk.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));
                isWalk = true;
                btnRun.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme()));
                isRun = false;
                btnBike.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme()));
                isBike = false;
                mSpeed = readSpeed("setting_walk", 1.2);
                updateMotionSpeed();
            }
        });
        /* 默认为步行 */
        isWalk = true;
        btnWalk.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));
        /* 跑步按键的点击处理 */
        isRun = false;
        btnRun = mJoystickLayout.findViewById(R.id.joystick_run);
        btnRun.setOnClickListener(v -> {
            if (!isRun) {
                btnRun.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));
                isRun = true;
                btnWalk.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme()));
                isWalk = false;
                btnBike.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme()));
                isBike = false;
                mSpeed = readSpeed("setting_run", 3.6);
                updateMotionSpeed();
            }
        });
        /* 自行车按键的点击处理 */
        isBike = false;
        btnBike = mJoystickLayout.findViewById(R.id.joystick_bike);
        btnBike.setOnClickListener(v -> {
            if (!isBike) {
                btnBike.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));
                isBike = true;
                btnWalk.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme()));
                isWalk = false;
                btnRun.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme()));
                isRun = false;
                mSpeed = readSpeed("setting_bike", 10);
                updateMotionSpeed();
            }
        });
        /* 方向键点击处理 */
        RockerView rckView = mJoystickLayout.findViewById(R.id.joystick_rocker);
        rckView.setListener(this::processDirection);

        /* 方向键点击处理 */
        ButtonView btnView = mJoystickLayout.findViewById(R.id.joystick_button);
        btnView.setListener(this::processDirection);

        /* 这里用来决定摇杆类型 */
        if (sharedPreferences.getString("setting_joystick_type", "0").equals("0")) {
            rckView.setVisibility(VISIBLE);
            btnView.setVisibility(GONE);
        } else {
            rckView.setVisibility(GONE);
            btnView.setVisibility(VISIBLE);
        }
    }

    private double readSpeed(String key, double fallback) {
        try {
            double speed = InputUtils.parseFiniteDouble(sharedPreferences.getString(key, String.valueOf(fallback)));
            return speed >= 0 && speed <= MockLocationEngine.MAX_SPEED_METERS_PER_SECOND ? speed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void updateMotionSpeed() {
        if (isMove && !isDestroyed && mListener != null) {
            mListener.onMotionInfo(mSpeed * mR, 90.0 - mAngle);
        }
    }

    private void processDirection(boolean auto, double angle, double r) {
        if (isDestroyed || mResettingMovement || mListener == null) {
            return;
        }
        if (r <= 0) {
            isMove = false;
            mR = 0;
            mListener.onMotionInfo(0, 90.0 - mAngle);
        } else {
            mAngle = angle;
            mR = Math.min(1, r);
            if (auto) {
                isMove = true;
                mListener.onMotionInfo(mSpeed * mR, 90.0 - mAngle);
            } else {
                isMove = false;
                mListener.onStepInfo(mSpeed * mR, 90.0 - mAngle);
            }
        }
    }

    private class JoyStickOnTouchListener implements OnTouchListener {
        private int x;
        private int y;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    x = (int) event.getRawX();
                    y = (int) event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    int nowX = (int) event.getRawX();
                    int nowY = (int) event.getRawY();
                    int movedX = nowX - x;
                    int movedY = nowY - y;
                    x = nowX;
                    y = nowY;

                    mWindowParamCurrent.x += movedX;
                    mWindowParamCurrent.y += movedY;
                    mWindowManager.updateViewLayout(view, mWindowParamCurrent);
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    break;
                default:
                    break;
            }
            return false;
        }
    }

    public interface JoyStickClickListener {
        void onMotionInfo(double speed, double angle);
        void onStepInfo(double distanceMeters, double angle);
        long onPositionInfo(double lng, double lat, double alt);
    }


    @SuppressLint({"InflateParams", "ClickableViewAccessibility"})
    private void initJoyStickMapView() {
        mMapLayout = (FrameLayout)inflater.inflate(R.layout.joystick_map, null);
        mMapLayout.setOnTouchListener(new JoyStickOnTouchListener());

        mSearchList = mMapLayout.findViewById(R.id.map_search_list_view);
        mSearchLayout = mMapLayout.findViewById(R.id.map_search_linear);
        mSuggestionSearch = SuggestionSearch.newInstance();
        mSuggestionSearch.setOnGetSuggestionResultListener(suggestionResult -> {
            if (suggestionResult == null
                    || suggestionResult.error != SearchResult.ERRORNO.NO_ERROR
                    || suggestionResult.getAllSuggestions() == null) {
                mSearchLayout.setVisibility(View.GONE);
                GoUtils.DisplayToast(mContext,getResources().getString(R.string.app_search_null));
            } else {
                List<Map<String, Object>> data = new ArrayList<>();
                int retCnt = suggestionResult.getAllSuggestions().size();

                for (int i = 0; i < retCnt; i++) {
                    if (suggestionResult.getAllSuggestions().get(i).pt == null) {
                        continue;
                    }

                    Map<String, Object> poiItem = new HashMap<>();
                    poiItem.put(MainActivity.POI_NAME, suggestionResult.getAllSuggestions().get(i).key);
                    poiItem.put(MainActivity.POI_ADDRESS, suggestionResult.getAllSuggestions().get(i).city + " " + suggestionResult.getAllSuggestions().get(i).district);
                    poiItem.put(MainActivity.POI_LONGITUDE, "" + suggestionResult.getAllSuggestions().get(i).pt.longitude);
                    poiItem.put(MainActivity.POI_LATITUDE, "" + suggestionResult.getAllSuggestions().get(i).pt.latitude);
                    data.add(poiItem);
                }

                if (data.isEmpty()) {
                    mSearchLayout.setVisibility(View.GONE);
                    GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_search_null));
                    return;
                }

                SearchResultAdapter simAdapt = new SearchResultAdapter(
                        mContext,
                        data,
                        new String[] {MainActivity.POI_NAME, MainActivity.POI_ADDRESS, MainActivity.POI_LONGITUDE, MainActivity.POI_LATITUDE}, // 与下面数组元素要一一对应
                        new int[] {R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude},
                        this::saveSearchResult);
                mSearchList.setAdapter(simAdapt);
                mSearchLayout.setVisibility(View.VISIBLE);
            }
        });
        mSearchList.setOnItemClickListener((parent, view, position, id) -> {
            mSearchLayout.setVisibility(View.GONE);

            String lng = ((TextView) view.findViewById(R.id.poi_longitude)).getText().toString();
            String lat = ((TextView) view.findViewById(R.id.poi_latitude)).getText().toString();
            String name = ((TextView) view.findViewById(R.id.poi_name)).getText().toString();
            String address = ((TextView) view.findViewById(R.id.poi_address)).getText().toString();
            String location = TextUtils.isEmpty(address) ? name : name + " - " + address;
            markBaiduMap(new LatLng(Double.parseDouble(lat), Double.parseDouble(lng)), location);
        });

        TextView tips = mMapLayout.findViewById(R.id.joystick_map_tips);
        SearchView mSearchView = mMapLayout.findViewById(R.id.joystick_map_searchView);
        mSearchView.setOnSearchClickListener(v -> {
            tips.setVisibility(GONE);

            // 特殊处理：这里让搜索框获取焦点，以显示输入法
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mMapLayout, mWindowParamCurrent);
        });
        mSearchView.setOnCloseListener(() -> {
            tips.setVisibility(VISIBLE);
            mSearchLayout.setVisibility(GONE);

            // 关闭时清除焦点
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mMapLayout, mWindowParamCurrent);

            return false;       /* 这里必须返回false，否则需要自行处理搜索框的折叠 */
        });
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query == null || query.trim().isEmpty()) {
                    GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_error_input_null));
                    return true;
                }

                try {
                    mSuggestionSearch.requestSuggestion(
                            MapSearchUtils.createSuggestionOption(query, MainActivity.mCurrentCity));
                } catch (Exception e) {
                    GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_error_search));
                    XLog.e("JoyStick: Search request failed", e);
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText != null && !newText.trim().isEmpty()) {
                    try {
                        mSuggestionSearch.requestSuggestion(
                                MapSearchUtils.createSuggestionOption(newText, MainActivity.mCurrentCity));
                    } catch (Exception e) {
                        GoUtils.DisplayToast(mContext,getResources().getString(R.string.app_error_search));
                        XLog.e("JoyStick: Search request failed", e);
                    }
                } else {
                    mSearchLayout.setVisibility(GONE);
                }

                return true;
            }
        });

        ImageButton btnGo = mMapLayout.findViewById(R.id.btnGo);
        btnGo.setOnClickListener(v -> {
            // 关闭时清除焦点
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mMapLayout, mWindowParamCurrent);

            tips.setVisibility(VISIBLE);
            mSearchView.clearFocus();
            mSearchView.onActionViewCollapsed();

            if (mMarkMapLngLat == null) {
                GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_error_location));
            } else if (mPendingPositionId != 0) {
                GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_location_updating));
            } else {
                double[] lngLat = MapUtils.bd2wgs(mMarkMapLngLat.longitude, mMarkMapLngLat.latitude);
                mPendingMapPoint = mMarkMapLngLat;
                mPendingPositionId = mListener.onPositionInfo(lngLat[0], lngLat[1], mAltitude);
            }
        });
        btnGo.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));

        ImageButton btnSaveLocation = mMapLayout.findViewById(R.id.btnSaveLocation);
        btnSaveLocation.setOnClickListener(v -> {
            if (mMarkMapLngLat == null) {
                GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_error_location));
                return;
            }

            String location = TextUtils.isEmpty(mMarkMapName)
                    ? getResources().getString(R.string.history_location_default_name)
                    : mMarkMapName;
            saveFavoriteLocation(
                    location, mMarkMapLngLat.longitude, mMarkMapLngLat.latitude);
        });
        btnSaveLocation.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));

        ImageButton btnClose = mMapLayout.findViewById(R.id.map_close);
        btnClose.setOnClickListener(v -> {
            // 关闭时清除焦点
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

            tips.setVisibility(VISIBLE);
            mSearchLayout.setVisibility(GONE);
            mSearchView.clearFocus();
            mSearchView.onActionViewCollapsed();

            mCurWin = WINDOW_TYPE_JOYSTICK;
            show();
        });

        ImageButton btnBack = mMapLayout.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> resetBaiduMap());
        btnBack.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));

        initBaiduMap();
    }

    private void initBaiduMap() {
        mMapView = mMapLayout.findViewById(R.id.map_joystick);
        mMapView.showZoomControls(false);
        mBaiduMap = mMapView.getMap();
        mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
        mBaiduMap.setMyLocationEnabled(true);

        mBaiduMap.setOnMapTouchListener(event -> {

        });

        mBaiduMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
            /**
             * 单击地图
             */
            @Override
            public void onMapClick(LatLng point) {
                markBaiduMap(point);
            }

            /**
             * 单击地图中的POI点
             */
            @Override
            public void onMapPoiClick(MapPoi poi) {
                markBaiduMap(poi.getPosition());
            }
        });

        mBaiduMap.setOnMapLongClickListener(new BaiduMap.OnMapLongClickListener() {
            /**
             * 长按地图
             */
            @Override
            public void onMapLongClick(LatLng point) {
                markBaiduMap(point);
            }
        });

        mBaiduMap.setOnMapDoubleClickListener(new BaiduMap.OnMapDoubleClickListener() {
            /**
             * 双击地图
             */
            @Override
            public void onMapDoubleClick(LatLng point) {
                markBaiduMap(point);
            }
        });
    }

    private void resetBaiduMap() {
        if (mCurMapLngLat == null || isDestroyed) {
            return;
        }
        mBaiduMap.clear();
        mMarkMapLngLat = null;
        mMarkMapName = null;

        MyLocationData locData = new MyLocationData.Builder()
                .latitude(mCurMapLngLat.latitude)
                .longitude(mCurMapLngLat.longitude)
                .accuracy(mCurrentAccuracy)
                .direction(mCurrentBearing)
                .build();
        mBaiduMap.setMyLocationData(locData);

        MapStatus.Builder builder = new MapStatus.Builder();
        builder.target(mCurMapLngLat).zoom(18.0f);
        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
    }

    private void markBaiduMap(LatLng latLng) {
        markBaiduMap(latLng, null);
    }

    private void markBaiduMap(LatLng latLng, String name) {
        mMarkMapLngLat = latLng;
        mMarkMapName = name;

        MarkerOptions ooA = new MarkerOptions().position(latLng).icon(MainActivity.mMapIndicator);
        mBaiduMap.clear();
        mBaiduMap.addOverlay(ooA);

        MapStatus.Builder builder = new MapStatus.Builder();
        builder.target(latLng).zoom(18.0f);
        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
    }

    private void saveSearchResult(String name, String address, String longitude, String latitude) {
        try {
            String location = TextUtils.isEmpty(address) ? name : name + " - " + address;
            saveFavoriteLocation(
                    location, Double.parseDouble(longitude), Double.parseDouble(latitude));
        } catch (NumberFormatException e) {
            GoUtils.DisplayToast(mContext, getResources().getString(R.string.search_favorite_error));
            XLog.e("JoyStick: Search result has invalid coordinates", e);
        }
    }

    private void saveFavoriteLocation(String location, double longitude, double latitude) {
        SQLiteDatabase historyLocationDB = null;
        try {
            DataBaseHistoryLocation dbHelper = new DataBaseHistoryLocation(mContext.getApplicationContext());
            historyLocationDB = dbHelper.getWritableDatabase();
            boolean saved = DataBaseHistoryLocation.saveLocation(
                    historyLocationDB,
                    location,
                    longitude,
                    latitude,
                    true);

            GoUtils.DisplayToast(mContext, getResources().getString(
                    saved ? R.string.search_favorite_ok : R.string.search_favorite_error));
            if (saved) {
                fetchAllRecord();
                showHistory(mAllRecord);
            }
        } catch (Exception e) {
            GoUtils.DisplayToast(mContext, getResources().getString(R.string.search_favorite_error));
            XLog.e("JoyStick: Failed to save favorite location", e);
        } finally {
            if (historyLocationDB != null) {
                historyLocationDB.close();
            }
        }
    }


    @SuppressLint({"InflateParams", "ClickableViewAccessibility"})
    private void initHistoryView() {
        mHistoryLayout = (FrameLayout)inflater.inflate(R.layout.joystick_history, null);
        mHistoryLayout.setOnTouchListener(new JoyStickOnTouchListener());

        TextView tips = mHistoryLayout.findViewById(R.id.joystick_his_tips);
        SearchView mSearchView = mHistoryLayout.findViewById(R.id.joystick_his_searchView);
        mSearchView.setOnSearchClickListener(v -> {
            tips.setVisibility(GONE);

            // 特殊处理：这里让搜索框获取焦点，以显示输入法
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mHistoryLayout, mWindowParamCurrent);
        });
        mSearchView.setOnCloseListener(() -> {
            tips.setVisibility(VISIBLE);

            // 关闭时清除焦点
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mHistoryLayout, mWindowParamCurrent);

            return false;       /* 这里必须返回false，否则需要自行处理搜索框的折叠 */
        });
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {// 当点击搜索按钮时触发该方法
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {// 当搜索内容改变时触发该方法
                if (TextUtils.isEmpty(newText)) {
                    showHistory(mAllRecord);
                } else {
                    List<Map<String, Object>> searchRet = new ArrayList<>();
                    for (int i = 0; i < mAllRecord.size(); i++){
                        if (mAllRecord.get(i).toString().indexOf(newText) > 0){
                            searchRet.add(mAllRecord.get(i));
                        }
                    }

                    if (searchRet.size() > 0) {
                        showHistory(searchRet);
                    } else {
                        GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_search_null));
                        showHistory(mAllRecord);
                    }
                }

                return false;
            }
        });

        noRecordText = mHistoryLayout.findViewById(R.id.joystick_his_record_no_textview);
        mRecordListView = mHistoryLayout.findViewById(R.id.joystick_his_record_list_view);
        mRecordListView.setOnItemClickListener((adapterView, view, i, l) -> {
            // 关闭时清除焦点
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mHistoryLayout, mWindowParamCurrent);

            mSearchView.clearFocus();
            mSearchView.onActionViewCollapsed();
            tips.setVisibility(VISIBLE);

            if (mPendingPositionId != 0) {
                GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_location_updating));
                return;
            }
            String recordId = ((TextView) view.findViewById(R.id.LocationID)).getText().toString();
            try (DataBaseHistoryLocation helper = new DataBaseHistoryLocation(mContext);
                 Cursor cursor = helper.getReadableDatabase().query(DataBaseHistoryLocation.TABLE_NAME,
                         new String[] {DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84,
                                 DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84},
                         DataBaseHistoryLocation.DB_COLUMN_ID + " = ?", new String[] {recordId},
                         null, null, null)) {
                if (!cursor.moveToFirst()) {
                    throw new IllegalArgumentException("Missing history position");
                }
                double longitude = InputUtils.parseFiniteDouble(cursor.getString(0));
                double latitude = InputUtils.parseFiniteDouble(cursor.getString(1));
                double[] mapPoint = MapUtils.wgs2bd09(longitude, latitude);
                mPendingMapPoint = new LatLng(mapPoint[1], mapPoint[0]);
                mPendingPositionId = mListener.onPositionInfo(longitude, latitude, mAltitude);
            } catch (RuntimeException e) {
                XLog.e("Invalid overlay history position", e);
                GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_error_input));
            }
        });

        fetchAllRecord();

        showHistory(mAllRecord);

        ImageButton btnClose = mHistoryLayout.findViewById(R.id.joystick_his_close);
        btnClose.setOnClickListener(v -> {
            // 关闭时清除焦点
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

            mSearchView.clearFocus();
            mSearchView.onActionViewCollapsed();
            tips.setVisibility(VISIBLE);

            mCurWin = WINDOW_TYPE_JOYSTICK;
            show();
        });
    }

    private void fetchAllRecord() {
        SQLiteDatabase mHistoryLocationDB;

        mAllRecord.clear();

        try {
            DataBaseHistoryLocation hisLocDBHelper = new DataBaseHistoryLocation(mContext.getApplicationContext());
            mHistoryLocationDB = hisLocDBHelper.getWritableDatabase();

            Cursor cursor = mHistoryLocationDB.query(DataBaseHistoryLocation.TABLE_NAME, null,
                    DataBaseHistoryLocation.DB_COLUMN_ID + " > ?", new String[] {"0"},
                    null, null,
                    DataBaseHistoryLocation.DB_COLUMN_FAVORITE + " DESC, "
                            + DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " DESC",
                    null);

            while (cursor.moveToNext()) {
                Map<String, Object> item = new HashMap<>();
                int ID = cursor.getInt(0);
                String Location = cursor.getString(1);
                String Longitude = cursor.getString(2);
                String Latitude = cursor.getString(3);
                long TimeStamp = cursor.getInt(4);
                String BD09Longitude = cursor.getString(5);
                String BD09Latitude = cursor.getString(6);
                boolean favorite = cursor.getInt(7) == 1;
                XLog.d("TB: " + ID + "\t" + Location + "\t" + Longitude + "\t" + Latitude + "\t" + TimeStamp + "\t" + BD09Longitude + "\t" + BD09Latitude);
                BigDecimal bigDecimalLongitude = BigDecimal.valueOf(Double.parseDouble(Longitude));
                BigDecimal bigDecimalLatitude = BigDecimal.valueOf(Double.parseDouble(Latitude));
                BigDecimal bigDecimalBDLongitude = BigDecimal.valueOf(Double.parseDouble(BD09Longitude));
                BigDecimal bigDecimalBDLatitude = BigDecimal.valueOf(Double.parseDouble(BD09Latitude));
                double doubleLongitude = bigDecimalLongitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleLatitude = bigDecimalLatitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleBDLongitude = bigDecimalBDLongitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleBDLatitude = bigDecimalBDLatitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                item.put(HistoryActivity.KEY_ID, Integer.toString(ID));
                item.put(HistoryActivity.KEY_LOCATION, Location);
                item.put(HistoryActivity.KEY_TIME, GoUtils.timeStamp2Date(Long.toString(TimeStamp)));
                item.put(HistoryActivity.KEY_LNG_LAT_WGS, "[经度:" + doubleLongitude + " 纬度:" + doubleLatitude + "]");
                item.put(HistoryActivity.KEY_LNG_LAT_CUSTOM, "[经度:" + doubleBDLongitude + " 纬度:" + doubleBDLatitude + "]");
                item.put(HistoryActivity.KEY_FAVORITE, favorite ? "★" : "");
                mAllRecord.add(item);
            }
            cursor.close();
            mHistoryLocationDB.close();
        } catch (Exception e) {
            XLog.e("JOYSTICK: ERROR - fetchAllRecord");
        }
    }

    private void showHistory(List<Map<String, Object>> list) {
        if (list.size() == 0) {
            mRecordListView.setVisibility(View.GONE);
            noRecordText.setVisibility(View.VISIBLE);
        } else {
            noRecordText.setVisibility(View.GONE);
            mRecordListView.setVisibility(View.VISIBLE);

            try {
                SimpleAdapter simAdapt = new SimpleAdapter(
                        mContext,
                        list,
                        R.layout.history_item,
                        new String[]{HistoryActivity.KEY_ID, HistoryActivity.KEY_LOCATION, HistoryActivity.KEY_TIME, HistoryActivity.KEY_LNG_LAT_WGS, HistoryActivity.KEY_LNG_LAT_CUSTOM, HistoryActivity.KEY_FAVORITE}, // 与下面数组元素要一一对应
                        new int[]{R.id.LocationID, R.id.LocationText, R.id.TimeText, R.id.WGSLatLngText, R.id.BDLatLngText, R.id.FavoriteText});
                mRecordListView.setAdapter(simAdapt);
            } catch (Exception e) {
                XLog.e("JOYSTICK: ERROR - showHistory");
            }
        }
    }
}
