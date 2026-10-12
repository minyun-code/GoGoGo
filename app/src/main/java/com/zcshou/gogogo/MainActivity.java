package com.zcshou.gogogo;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.InfoWindow;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.baidu.mapapi.search.sug.SuggestionResult;
import com.baidu.mapapi.search.sug.SuggestionSearch;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.zcshou.service.ServiceGo;
import com.zcshou.database.DataBaseHistoryLocation;
import com.zcshou.database.DataBaseHistorySearch;
import com.zcshou.utils.ShareUtils;
import com.zcshou.utils.GoUtils;
import com.zcshou.utils.MapUtils;
import com.zcshou.utils.MapSearchUtils;
import com.zcshou.utils.InputUtils;
import com.zcshou.utils.VersionUtils;

import com.elvishew.xlog.XLog;

import io.noties.markwon.Markwon;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends BaseActivity implements SensorEventListener {
    /* 对外 */
    public static final String LAT_MSG_ID = "LAT_VALUE";
    public static final String LNG_MSG_ID = "LNG_VALUE";
    public static final String ALT_MSG_ID = "ALT_VALUE";

    public static final String POI_NAME = "POI_NAME";
    public static final String POI_ADDRESS = "POI_ADDRESS";
    public static final String POI_LONGITUDE = "POI_LONGITUDE";
    public static final String POI_LATITUDE = "POI_LATITUDE";

    private OkHttpClient mOkHttpClient;
    private SharedPreferences sharedPreferences;

    /*============================== 主界面地图 相关 ==============================*/
    /************** 地图 *****************/
    public final static BitmapDescriptor mMapIndicator = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);
    public static String mCurrentCity = null;
    private MapView mMapView;
    private static BaiduMap mBaiduMap = null;
    private static LatLng mMarkLatLngMap = new LatLng(36.547743718042415, 117.07018449827267); // 当前标记的地图点
    private static String mMarkName = null;
    private GeoCoder mGeoCoder;
    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetic;
    private float[] mAccValues = new float[3];//加速度传感器数据
    private float[] mMagValues = new float[3];//地磁传感器数据
    private final float[] mR = new float[9];//旋转矩阵，用来保存磁场和加速度的数据
    private final float[] mDirectionValues = new float[3];//模拟方向传感器的数据（原始数据为弧度）
    /************** 定位 *****************/
    private LocationClient mLocClient = null;
    private double mCurrentLat = 0.0;       // 当前位置的百度纬度
    private double mCurrentLon = 0.0;       // 当前位置的百度经度
    private float mCurrentDirection = 0.0f;
    private boolean isFirstLoc = true; // 是否首次定位
    private boolean isMockServStart = false;
    private boolean isServiceBound;
    private ServiceGo.ServiceGoBinder mServiceBinder;
    private ServiceConnection mConnection;
    private FloatingActionButton mButtonStart;
    /*============================== 历史记录 相关 ==============================*/
    private SQLiteDatabase mLocationHistoryDB;
    private SQLiteDatabase mSearchHistoryDB;
    /*============================== SearchView 相关 ==============================*/
    private SearchView searchView;
    private ListView mSearchList;
    private LinearLayout mSearchLayout;
    private ListView mSearchHistoryList;
    private LinearLayout mHistoryLayout;
    private MenuItem searchItem;
    private SuggestionSearch mSuggestionSearch;
    /*============================== 更新 相关 ==============================*/
    private DownloadManager mDownloadManager = null;
    private static final String UPDATE_DOWNLOAD_ID = "update_download_id";
    private static final String UPDATE_DOWNLOAD_FILENAME = "update_download_filename";
    private long mDownloadId = -1;
    private BroadcastReceiver mDownloadBdRcv;
    private String mUpdateFilename;
    private boolean mUpdateCheckInFlight;
    private android.app.AlertDialog mUpdateDialog;
    private boolean mInstallPermissionPending;
    private final ActivityResultLauncher<Intent> mInstallPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                mInstallPermissionPending = false;
                if (getPackageManager().canRequestPackageInstalls()) {
                    installNewVersion();
                } else {
                    GoUtils.DisplayToast(this, getString(R.string.update_install_permission_required));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.nav_drawer_open, R.string.nav_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        XLog.i("MainActivity: onCreate");

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mOkHttpClient = new OkHttpClient();

        initNavigationView();

        initMap();

        initMapLocation();

        initMapButton();

        initGoBtn();

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceBinder = (ServiceGo.ServiceGoBinder)service;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mServiceBinder = null;
                isMockServStart = false;
                mButtonStart.setImageResource(R.drawable.ic_position);
            }
        };

        initStoreHistory();

        initSearchView();

        initUpdateVersion();
    }

    @Override
    protected void onPause() {
        XLog.i("MainActivity: onPause");
        mMapView.onPause();
        mSensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        XLog.i("MainActivity: onResume");
        mMapView.onResume();
        mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
        super.onResume();
    }

    @Override
    protected void onStop() {
        XLog.i("MainActivity: onStop");
        //取消注册传感器监听
        mSensorManager.unregisterListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        XLog.i("MainActivity: onDestroy");

        if (isMockServStart || isServiceBound) {
            stopGoLocation();
        }
        unregisterReceiver(mDownloadBdRcv);
        mOkHttpClient.dispatcher().cancelAll();
        if (mUpdateDialog != null) {
            mUpdateDialog.dismiss();
        }

        mSensorManager.unregisterListener(this);

        // 退出时销毁定位
        if (mLocClient != null) {
            mLocClient.stop();
        }
        // 关闭定位图层
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
        if (mGeoCoder != null) {
            mGeoCoder.destroy();
        }

        //poi search destroy
        mSuggestionSearch.destroy();

        //close db
        if (mLocationHistoryDB != null) {
            mLocationHistoryDB.close();
        }
        if (mSearchHistoryDB != null) {
            mSearchHistoryDB.close();
        }

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(false);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        //找到searchView
        searchItem = menu.findItem(R.id.action_search);
        searchItem.setOnActionExpandListener(new  MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                mHistoryLayout.setVisibility(View.INVISIBLE);
                return true;  // Return true to collapse action view
            }
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                //展示搜索历史
                List<Map<String, Object>> data = getSearchHistory();

                if (!data.isEmpty()) {
                    SimpleAdapter simAdapt = new SimpleAdapter(
                            MainActivity.this,
                            data,
                            R.layout.search_item,
                            new String[] {DataBaseHistorySearch.DB_COLUMN_KEY,
                                    DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                                    DataBaseHistorySearch.DB_COLUMN_TIMESTAMP,
                                    DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                                    DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM,
                                    DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM},
                            new int[] {R.id.search_key,
                                    R.id.search_description,
                                    R.id.search_timestamp,
                                    R.id.search_isLoc,
                                    R.id.search_longitude,
                                    R.id.search_latitude});
                    mSearchHistoryList.setAdapter(simAdapt);
                    mHistoryLayout.setVisibility(View.VISIBLE);
                }

                return true;  // Return true to expand action view
            }
        });

        searchView = (SearchView) searchItem.getActionView();
        searchView.setIconified(false);// 设置searchView处于展开状态
        searchView.onActionViewExpanded();// 当展开无输入内容的时候，没有关闭的图标
        searchView.setIconifiedByDefault(true);//默认为true在框内，设置false则在框外
        searchView.setSubmitButtonEnabled(false);//显示提交按钮
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                String keyword = query == null ? "" : query.trim();
                if (keyword.isEmpty()) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_input_null));
                    return true;
                }

                try {
                    mSuggestionSearch.requestSuggestion(
                            MapSearchUtils.createSuggestionOption(keyword, mCurrentCity));
                    //搜索历史 插表参数
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, keyword);
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, "搜索关键字");
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_KEY);
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

                    DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
                    mBaiduMap.clear();
                    mSearchLayout.setVisibility(View.INVISIBLE);
                } catch (Exception e) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_search));
                    XLog.d(getResources().getString(R.string.app_error_search));
                }

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //当输入框内容改变的时候回调
                //搜索历史置为不可见
                mHistoryLayout.setVisibility(View.INVISIBLE);

                if (newText != null && !newText.trim().isEmpty()) {
                    try {
                        mSuggestionSearch.requestSuggestion(
                                MapSearchUtils.createSuggestionOption(newText, mCurrentCity));
                    } catch (Exception e) {
                        GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_search));
                        XLog.d(getResources().getString(R.string.app_error_search));
                    }
                } else {
                    mSearchLayout.setVisibility(View.INVISIBLE);
                }

                return true;
            }
        });

        // 搜索框的清除按钮(该按钮属于安卓系统图标)
        ImageView closeButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        closeButton.setOnClickListener(v -> {
            EditText et = findViewById(androidx.appcompat.R.id.search_src_text);
            et.setText("");
            searchView.setQuery("", false);
            mSearchLayout.setVisibility(View.INVISIBLE);
            mHistoryLayout.setVisibility(View.VISIBLE);
        });

        return true;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            mAccValues = sensorEvent.values;
        }
        else if(sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            mMagValues = sensorEvent.values;
        }

        SensorManager.getRotationMatrix(mR, null, mAccValues, mMagValues);
        SensorManager.getOrientation(mR, mDirectionValues);
        mCurrentDirection = (float) Math.toDegrees(mDirectionValues[0]);    // 弧度转角度
        if (mCurrentDirection < 0) {    // 由 -180 ~ + 180 转为 0 ~ 360
            mCurrentDirection += 360;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    /*============================== NavigationView 相关 ==============================*/
    private void initNavigationView() {
        /*============================== NavigationView 相关 ==============================*/
        NavigationView mNavigationView = findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_history) {
                Intent intent = new Intent(MainActivity.this, HistoryActivity.class);

                startActivity(intent);
            } else if (id == R.id.nav_settings) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_dev) {
                if (!GoUtils.isDeveloperOptionsEnabled(this)) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_dev));
                } else {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                        startActivity(intent);
                    } catch (Exception e) {
                        GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_dev));
                    }
                }
            } else if (id == R.id.nav_update) {
                checkUpdateVersion();
            } else if (id == R.id.nav_feedback) {
                File file = new File(getExternalFilesDir("Logs"), GoApplication.LOG_FILE_NAME);
                ShareUtils.shareFile(this, file, item.getTitle().toString());
            } else if (id == R.id.nav_contact) {
                Uri uri = Uri.parse("https://gitee.com/itexp/gogogo/issues");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }

            DrawerLayout drawer = findViewById(R.id.drawer_layout);
            drawer.closeDrawer(GravityCompat.START);

            return true;
        });

        // 直接获取第 0 个头部视图
        View headerView = mNavigationView.getHeaderView(0);
        TextView app_version = headerView.findViewById(R.id.app_version);
        app_version.setText(GoUtils.getVersionName(this));
    }

    /*============================== 主界面地图 相关 ==============================*/
    private void initMap() {
        // 地图初始化
        mMapView = findViewById(R.id.bdMapView);
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
                mMarkName = null;
                mMarkLatLngMap = point;
                markMap();
            }
            /**
             * 单击地图中的POI点
             */
            @Override
            public void onMapPoiClick(MapPoi poi) {
                mMarkName = null;
                mMarkLatLngMap = poi.getPosition();
                markMap();
            }
        });
        mBaiduMap.setOnMapLongClickListener(new BaiduMap.OnMapLongClickListener() {
            /**
             * 长按地图
             */
            @Override
            public void onMapLongClick(LatLng point) {
                mMarkName = null;
                mMarkLatLngMap = point;
                markMap();
            }
        });
        mBaiduMap.setOnMapDoubleClickListener(new BaiduMap.OnMapDoubleClickListener() {
            /**
             * 双击地图
             */
            @Override
            public void onMapDoubleClick(LatLng point) {
                mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomIn());
            }
        });

        View poiView = View.inflate(MainActivity.this, R.layout.location_poi_info, null);
        TextView poiAddress = poiView.findViewById(R.id.poi_address);
        TextView poiLongitude = poiView.findViewById(R.id.poi_longitude);
        TextView poiLatitude = poiView.findViewById(R.id.poi_latitude);
        ImageButton ibSave = poiView.findViewById(R.id.poi_save);
        ibSave.setOnClickListener(v -> {
            try {
                String location = poiAddress.getText().toString();
                if (TextUtils.isEmpty(location)) {
                    location = getResources().getString(R.string.history_location_default_name);
                }
                saveFavoriteLocation(
                        location,
                        Double.parseDouble(poiLongitude.getText().toString()),
                        Double.parseDouble(poiLatitude.getText().toString()));
            } catch (NumberFormatException e) {
                GoUtils.DisplayToast(this, getResources().getString(R.string.search_favorite_error));
                XLog.e("Map selection has invalid coordinates", e);
            }
        });
        ImageButton ibCopy = poiView.findViewById(R.id.poi_copy);
        ibCopy.setOnClickListener(v -> {
            //获取剪贴板管理器：
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            // 创建普通字符型ClipData
            ClipData mClipData = ClipData.newPlainText("Label", mMarkLatLngMap.toString());
            // 将 ClipData内容放到系统剪贴板里。
            cm.setPrimaryClip(mClipData);

            GoUtils.DisplayToast(this,  getResources().getString(R.string.app_location_copy));
        });
        ImageButton ibShare = poiView.findViewById(R.id.poi_share);
        ibShare.setOnClickListener(v -> ShareUtils.shareText(MainActivity.this, "分享位置", poiLongitude.getText()+","+poiLatitude.getText()));
        ImageButton ibFly = poiView.findViewById(R.id.poi_fly);
        ibFly.setOnClickListener(this::doGoLocation);
        mGeoCoder = GeoCoder.newInstance();
        mGeoCoder.setOnGetGeoCodeResultListener(new OnGetGeoCoderResultListener() {
            @Override
            public void onGetGeoCodeResult(GeoCodeResult geoCodeResult) {
                XLog.i(geoCodeResult.getLocation());
            }

            @Override
            public void onGetReverseGeoCodeResult(ReverseGeoCodeResult reverseGeoCodeResult) {
                if (reverseGeoCodeResult == null || reverseGeoCodeResult.error != SearchResult.ERRORNO.NO_ERROR) {
                    XLog.i("逆地理位置失败!");
                } else {
                    mMarkName = String.valueOf(reverseGeoCodeResult.getAddress());
                    poiLatitude.setText(String.valueOf(reverseGeoCodeResult.getLocation().latitude));
                    poiLongitude.setText(String.valueOf(reverseGeoCodeResult.getLocation().longitude));
                    poiAddress.setText(reverseGeoCodeResult.getAddress());
                    final InfoWindow mInfoWindow = new InfoWindow(poiView, reverseGeoCodeResult.getLocation(), -100);
                    mBaiduMap.showInfoWindow(mInfoWindow);
                }
            }
        });

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);// 获取传感器管理服务
        if (mSensorManager != null) {
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (mSensorAccelerometer != null) {
                mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
            }
            mSensorMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (mSensorMagnetic != null) {
                mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    //开启地图的定位图层
    private void initMapLocation() {
        try {
            // 定位初始化
            mLocClient = new LocationClient(this);
            mLocClient.registerLocationListener(new BDAbstractLocationListener() {
                @Override
                public void onReceiveLocation(BDLocation bdLocation) {
                    if (bdLocation == null || mMapView == null) {// mapview 销毁后不在处理新接收的位置
                        return;
                    }

                    if (!TextUtils.isEmpty(bdLocation.getCity())) {
                        mCurrentCity = bdLocation.getCity();
                    }
                    mCurrentLat = bdLocation.getLatitude();
                    mCurrentLon = bdLocation.getLongitude();
                    MyLocationData locData = new MyLocationData.Builder()
                            .accuracy(bdLocation.getRadius())
                            .direction(mCurrentDirection)// 此处设置开发者获取到的方向信息，顺时针0-360
                            .latitude(bdLocation.getLatitude())
                            .longitude(bdLocation.getLongitude()).build();
                    mBaiduMap.setMyLocationData(locData);
                    MyLocationConfiguration configuration = new MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, true, null);
                    mBaiduMap.setMyLocationConfiguration(configuration);

                    /* 如果出现错误，则需要重新请求位置 */
                    int err = bdLocation.getLocType();
                    if (err == BDLocation.TypeCriteriaException || err == BDLocation.TypeNetWorkException) {
                        mLocClient.requestLocation();   /* 请求位置 */
                    } else {
                        if (isFirstLoc) {
                            isFirstLoc = false;
                            // 这里记录百度地图返回的位置
                            mMarkLatLngMap = new LatLng(bdLocation.getLatitude(), bdLocation.getLongitude());
                            MapStatus.Builder builder = new MapStatus.Builder();
                            builder.target(mMarkLatLngMap).zoom(18.0f);
                            mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));

                            XLog.i("First Baidu LatLng: " + mMarkLatLngMap);
                        }
                    }
                }
                /**
                 * 错误的状态码
                 * <a><a href="http://lbsyun.baidu.com/index.php?title=android-locsdk/guide/addition-func/error-code">...</a></a>
                 * <p>
                 * 回调定位诊断信息，开发者可以根据相关信息解决定位遇到的一些问题
                 *
                 * @param locType      当前定位类型
                 * @param diagnosticType  诊断类型（1~9）
                 * @param diagnosticMessage 具体的诊断信息释义
                 */
                @Override
                public void onLocDiagnosticMessage(int locType, int diagnosticType, String diagnosticMessage) {
                    XLog.i("Baidu ERROR: " + locType + "-" + diagnosticType + "-" + diagnosticMessage);
                }
            });
            LocationClientOption locationOption = getLocationClientOption();
            //需将配置好的LocationClientOption对象，通过setLocOption方法传递给LocationClient对象使用
            mLocClient.setLocOption(locationOption);
            //开始定位
            mLocClient.start();
        } catch (Exception e) {
            XLog.e("ERROR: initMapLocation");
        }
    }

    @NonNull
    private static LocationClientOption getLocationClientOption() {
        LocationClientOption locationOption = new LocationClientOption();
        //可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        locationOption.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        //可选，默认gcj02，设置返回的定位结果坐标系，如果配合百度地图使用，建议设置为bd09ll;
        locationOption.setCoorType("bd09ll");
        //可选，默认0，即仅定位一次，设置发起连续定位请求的间隔需要大于等于1000ms才是有效的
        locationOption.setScanSpan(1000);
        //可选，设置是否需要地址信息，默认不需要
        locationOption.setIsNeedAddress(true);
        //可选，设置是否需要设备方向结果
        locationOption.setNeedDeviceDirect(false);
        //可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
        locationOption.setLocationNotify(true);
        //可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死
        locationOption.setIgnoreKillProcess(true);
        //可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
        locationOption.setIsNeedLocationDescribe(false);
        //可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
        locationOption.setIsNeedLocationPoiList(false);
        //可选，默认false，设置是否收集CRASH信息，默认收集
        locationOption.setIgnoreCacheException(true);
        //可选，默认false，设置是否开启Gps定位
        //locationOption.setOpenGps(true);
        locationOption.setOpenGnss(true);
        //可选，默认false，设置定位时是否需要海拔信息，默认不需要，除基础定位版本都可用
        locationOption.setIsNeedAltitude(false);
        return locationOption;
    }

    //地图上各按键的监听
    private void initMapButton() {
        RadioGroup mGroupMapType = this.findViewById(R.id.RadioGroupMapType);
        mGroupMapType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.mapNormal) {
                mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
            }

            if (checkedId == R.id.mapSatellite) {
                mBaiduMap.setMapType(BaiduMap.MAP_TYPE_SATELLITE);
            }
        });

        ImageButton curPosBtn = this.findViewById(R.id.cur_position);
        curPosBtn.setOnClickListener(v -> resetMap());

        ImageButton zoomInBtn = this.findViewById(R.id.zoom_in);
        zoomInBtn.setOnClickListener(v -> mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomIn()));

        ImageButton zoomOutBtn = this.findViewById(R.id.zoom_out);
        zoomOutBtn.setOnClickListener(v -> mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomOut()));

        ImageButton inputPosBtn = this.findViewById(R.id.input_pos);
        inputPosBtn.setOnClickListener(v -> {
            AlertDialog dialog;
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("请输入经度和纬度");
            View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.location_input, null);
            builder.setView(view);
            dialog = builder.show();

            EditText dialog_lng = view.findViewById(R.id.joystick_longitude);
            EditText dialog_lat = view.findViewById(R.id.joystick_latitude);
            RadioButton rbBD = view.findViewById(R.id.pos_type_bd);

            Button btnGo = view.findViewById(R.id.input_position_ok);
            btnGo.setOnClickListener(v2 -> {
                try {
                    double longitude = InputUtils.parseFiniteDouble(dialog_lng.getText().toString());
                    double latitude = InputUtils.parseFiniteDouble(dialog_lat.getText().toString());
                    if (longitude < -180 || longitude > 180) {
                        GoUtils.DisplayToast(this, getString(R.string.app_error_longitude));
                        return;
                    }
                    if (latitude < -90 || latitude > 90) {
                        GoUtils.DisplayToast(this, getString(R.string.app_error_latitude));
                        return;
                    }
                    if (rbBD.isChecked()) {
                        mMarkLatLngMap = new LatLng(latitude, longitude);
                    } else {
                        double[] bdLonLat = MapUtils.wgs2bd09(longitude, latitude);
                        mMarkLatLngMap = new LatLng(bdLonLat[1], bdLonLat[0]);
                    }
                    mMarkName = "手动输入的坐标";
                    markMap();
                    mBaiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(mMarkLatLngMap));
                    dialog.dismiss();
                } catch (NumberFormatException e) {
                    GoUtils.DisplayToast(this, getString(R.string.app_error_input));
                }
            });

            Button btnCancel = view.findViewById(R.id.input_position_cancel);
            btnCancel.setOnClickListener(v1 -> dialog.dismiss());
        });

        ImageButton favoritePosBtn = this.findViewById(R.id.favorite_position);
        favoritePosBtn.setOnClickListener(v -> {
            if (mMarkLatLngMap == null) {
                GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_location));
                return;
            }
            String location = TextUtils.isEmpty(mMarkName)
                    ? getResources().getString(R.string.history_location_default_name)
                    : mMarkName;
            saveFavoriteLocation(
                    location, mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
        });
    }

    //标定选择的位置
    private void markMap() {
        if (mMarkLatLngMap != null) {
            MarkerOptions ooA = new MarkerOptions().position(mMarkLatLngMap).icon(mMapIndicator);
            mBaiduMap.clear();
            mBaiduMap.addOverlay(ooA);
            if (mGeoCoder != null) {
                mGeoCoder.reverseGeoCode(new ReverseGeoCodeOption().location(mMarkLatLngMap));
            }
        }
    }

    private void resetMap() {
        mBaiduMap.clear();
        mMarkLatLngMap = null;

        MyLocationData locData = new MyLocationData.Builder()
                .latitude(mCurrentLat)
                .longitude(mCurrentLon)
                .direction(mCurrentDirection)
                .build();
        mBaiduMap.setMyLocationData(locData);

        MapStatus.Builder builder = new MapStatus.Builder();
        builder.target(new LatLng(mCurrentLat, mCurrentLon)).zoom(18.0f);
        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
    }

    // 在地图上显示位置
    public static boolean showLocation(String name, String bd09Longitude, String bd09Latitude) {
        boolean ret = true;

        try {
            if (!bd09Longitude.isEmpty() && !bd09Latitude.isEmpty()) {
                mMarkName = name;
                mMarkLatLngMap = new LatLng(Double.parseDouble(bd09Latitude), Double.parseDouble(bd09Longitude));
                MarkerOptions ooA = new MarkerOptions().position(mMarkLatLngMap).icon(mMapIndicator);
                mBaiduMap.clear();
                mBaiduMap.addOverlay(ooA);
                MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap);
                mBaiduMap.setMapStatus(mapstatusupdate);
            }
        } catch (Exception e) {
            ret = false;
            XLog.e("ERROR: showHistoryLocation");
        }

        return ret;
    }

    private void initGoBtn() {
        mButtonStart = findViewById(R.id.faBtnStart);
        mButtonStart.setOnClickListener(this::doGoLocation);
    }

    private double getConfiguredAltitude() {
        try {
            return InputUtils.parseFiniteDouble(sharedPreferences.getString("setting_altitude", "55.0"));
        } catch (NumberFormatException e) {
            return ServiceGo.DEFAULT_ALT;
        }
    }

    private boolean startGoLocation() {
        if (isServiceBound) {
            stopGoLocation();
        }
        Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
        double[] latLng = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
        serviceGoIntent.putExtra(LNG_MSG_ID, latLng[0]);
        serviceGoIntent.putExtra(LAT_MSG_ID, latLng[1]);
        double alt = getConfiguredAltitude();
        serviceGoIntent.putExtra(ALT_MSG_ID, alt);

        try {
            startForegroundService(serviceGoIntent);
            isServiceBound = bindService(serviceGoIntent, mConnection, BIND_AUTO_CREATE);
            if (!isServiceBound) {
                stopService(serviceGoIntent);
                GoUtils.DisplayToast(this, getString(R.string.app_service_start_error));
                return false;
            }
            isMockServStart = true;
            XLog.d("startForegroundService: ServiceGo");
            return true;
        } catch (RuntimeException e) {
            stopGoLocation();
            XLog.e("Failed to start location service", e);
            GoUtils.DisplayToast(this, getString(R.string.app_service_start_error));
            return false;
        }
    }

    private void stopGoLocation() {
        if (isServiceBound) {
            unbindService(mConnection);
            isServiceBound = false;
        }
        mServiceBinder = null;
        Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
        stopService(serviceGoIntent);
        isMockServStart = false;
    }

    private void doGoLocation(View v) {
        // Stopping an active service must remain possible when prerequisites are revoked.
        if (isMockServStart && mMarkLatLngMap == null) {
            stopGoLocation();
            Snackbar.make(v, "模拟位置已终止", Snackbar.LENGTH_LONG).show();
            mButtonStart.setImageResource(R.drawable.ic_position);
            return;
        }
        if (!GoUtils.isNetworkAvailable(this)) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_network));
            return;
        }

        if (!GoUtils.isGpsOpened(this)) {
            GoUtils.showEnableGpsDialog(this);
            return;
        }

        if (!Settings.canDrawOverlays(getApplicationContext())) {//悬浮窗权限判断
            GoUtils.showEnableFloatWindowDialog(this);
            XLog.e("无悬浮窗权限!");
            return;
        }

        if (isMockServStart) {
            if (mServiceBinder == null) {
                GoUtils.DisplayToast(this, getString(R.string.app_service_starting));
                return;
            }
            double[] latLng = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
            double alt = getConfiguredAltitude();
            mServiceBinder.setPosition(latLng[0], latLng[1], alt);
            Snackbar.make(v, "已传送到新位置", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();

            recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

            mBaiduMap.clear();
            mMarkLatLngMap = null;

            if (GoUtils.isWifiEnabled(MainActivity.this)) {
                GoUtils.showDisableWifiDialog(MainActivity.this);
            }
        } else {
            if (!GoUtils.isAllowMockLocation(this)) {
                GoUtils.showEnableMockLocationDialog(this);
                XLog.e("无模拟位置权限!");
            } else {
                if (mMarkLatLngMap == null) {
                    Snackbar.make(v, "请先点击地图位置或者搜索位置", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                } else {
                    if (!startGoLocation()) {
                        return;
                    }
                    mButtonStart.setImageResource(R.drawable.ic_fly);
                    Snackbar.make(v, "模拟位置已启动", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();

                    recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
                    mBaiduMap.clear();
                    mMarkLatLngMap = null;

                    if (GoUtils.isWifiEnabled(MainActivity.this)) {
                        GoUtils.showDisableWifiDialog(MainActivity.this);
                    }
                }
            }
        }
    }

    /*============================== 历史记录 相关 ==============================*/
    private void initStoreHistory() {
        try {
            // 定位历史
            DataBaseHistoryLocation dbLocation = new DataBaseHistoryLocation(getApplicationContext());
            mLocationHistoryDB = dbLocation.getWritableDatabase();
            // 搜索历史
            DataBaseHistorySearch dbHistory = new DataBaseHistorySearch(getApplicationContext());
            mSearchHistoryDB = dbHistory.getWritableDatabase();
        } catch (Exception e) {
            XLog.e("ERROR: sqlite init error");
        }
    }

    //获取查询历史
    private List<Map<String, Object>> getSearchHistory() {
        List<Map<String, Object>> data = new ArrayList<>();

        try {
            Cursor cursor = mSearchHistoryDB.query(DataBaseHistorySearch.TABLE_NAME, null,
                    DataBaseHistorySearch.DB_COLUMN_ID + " > ?", new String[] {"0"},
                    null, null, DataBaseHistorySearch.DB_COLUMN_TIMESTAMP + " DESC", null);

            while (cursor.moveToNext()) {
                Map<String, Object> searchHistoryItem = new HashMap<>();
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_KEY, cursor.getString(1));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, cursor.getString(2));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, "" + cursor.getInt(3));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, "" + cursor.getInt(4));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, cursor.getString(7));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, cursor.getString(8));
                data.add(searchHistoryItem);
            }
            cursor.close();
        } catch (Exception e) {
            XLog.e("ERROR: getSearchHistory");
        }

        return data;
    }

    // 记录请求的位置信息
    private void recordCurrentLocation(double lng, double lat) {
        final double[] wgs84 = MapUtils.bd2wgs(lng, lat);
        final String longitude = String.valueOf(wgs84[0]);
        final String latitude = String.valueOf(wgs84[1]);
        final String name = TextUtils.isEmpty(mMarkName)
                ? getString(R.string.history_location_default_name) : mMarkName;
        // Save the selection before requesting an address so network failures never lose history.
        ContentValues values = new ContentValues();
        values.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, name);
        values.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, longitude);
        values.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, latitude);
        values.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
        values.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, String.valueOf(lng));
        values.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, String.valueOf(lat));
        if (!DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, values)) {
            return;
        }

        final Context appContext = getApplicationContext();
        String ak = sharedPreferences.getString("setting_map_key", BuildConfig.MAPS_API_KEY);
        String url = "https://api.map.baidu.com/reverse_geocoding/v3/?ak=" + ak
                + "&output=json&coordtype=bd09ll&location=" + lat + "," + lng
                + "&mcode=" + BuildConfig.MAPS_SAFE_CODE;
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).get().build();
        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!call.isCanceled()) {
                    XLog.e("Failed to resolve history address", e);
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response closeableResponse = response) {
                    ResponseBody body = closeableResponse.body();
                    if (!closeableResponse.isSuccessful() || body == null) {
                        return;
                    }
                    JSONObject result = new JSONObject(body.string());
                    if (result.optInt("status", -1) != 0) {
                        return;
                    }
                    String address = result.getJSONObject("result").optString("formatted_address");
                    if (TextUtils.isEmpty(address)) {
                        return;
                    }
                    // Use an independently owned connection; the Activity may already be destroyed.
                    try (DataBaseHistoryLocation helper = new DataBaseHistoryLocation(appContext)) {
                        DataBaseHistoryLocation.updateResolvedAddress(
                                helper.getWritableDatabase(), longitude, latitude, address);
                    }
                } catch (IOException | JSONException | RuntimeException e) {
                    XLog.e("Failed to update history address", e);
                }
            }
        });
    }

    /*============================== SearchView 相关 ==============================*/
    private void initSearchView() {
        mSearchLayout = findViewById(R.id.search_linear);
        mHistoryLayout = findViewById(R.id.search_history_linear);

        mSearchList = findViewById(R.id.search_list_view);
        mSearchList.setOnItemClickListener((parent, view, position, id) -> {
            String lng = ((TextView) view.findViewById(R.id.poi_longitude)).getText().toString();
            String lat = ((TextView) view.findViewById(R.id.poi_latitude)).getText().toString();
            mMarkName = ((TextView) view.findViewById(R.id.poi_name)).getText().toString();
            mMarkLatLngMap = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
            MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap);
            mBaiduMap.setMapStatus(mapstatusupdate);

            markMap();

            double[] latLng = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

            // mSearchList.setVisibility(View.GONE);
            //搜索历史 插表参数
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, mMarkName);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, ((TextView) view.findViewById(R.id.poi_address)).getText().toString());
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

            DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            mSearchLayout.setVisibility(View.INVISIBLE);
            searchItem.collapseActionView();
        });
        //搜索历史列表的点击监听
        mSearchHistoryList = findViewById(R.id.search_history_list_view);
        mSearchHistoryList.setOnItemClickListener((parent, view, position, id) -> {
            String searchDescription = ((TextView) view.findViewById(R.id.search_description)).getText().toString();
            String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();
            String searchIsLoc = ((TextView) view.findViewById(R.id.search_isLoc)).getText().toString();

            //如果是定位搜索
            if (searchIsLoc.equals("1")) {
                String lng = ((TextView) view.findViewById(R.id.search_longitude)).getText().toString();
                String lat = ((TextView) view.findViewById(R.id.search_latitude)).getText().toString();
                mMarkName = searchKey;
                mMarkLatLngMap = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
                MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap);
                mBaiduMap.setMapStatus(mapstatusupdate);

                markMap();

                double[] latLng = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

                //设置列表不可见
                mHistoryLayout.setVisibility(View.INVISIBLE);
                searchItem.collapseActionView();
                //更新表
                ContentValues contentValues = new ContentValues();
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, searchKey);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, searchDescription);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

                DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            } else if (searchIsLoc.equals("0")) { //如果仅仅是搜索
                try {
                    searchView.setQuery(searchKey, true);
                } catch (Exception e) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_search));
                    XLog.e(getResources().getString(R.string.app_error_search));
                }
            } else {
                XLog.e(getResources().getString(R.string.app_error_param));
            }
        });
        mSearchHistoryList.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("警告")//这里是表头的内容
                    .setMessage("确定要删除该项搜索记录吗?")//这里是中间显示的具体信息
                    .setPositiveButton("确定",(dialog, which) -> {
                        String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();

                        try {
                            mSearchHistoryDB.delete(DataBaseHistorySearch.TABLE_NAME, DataBaseHistorySearch.DB_COLUMN_KEY + " = ?", new String[] {searchKey});
                            //删除成功
                            //展示搜索历史
                            List<Map<String, Object>> data = getSearchHistory();

                            if (!data.isEmpty()) {
                                SimpleAdapter simAdapt = new SimpleAdapter(
                                        MainActivity.this,
                                        data,
                                        R.layout.search_item,
                                        new String[] {DataBaseHistorySearch.DB_COLUMN_KEY,
                                                DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                                                DataBaseHistorySearch.DB_COLUMN_TIMESTAMP,
                                                DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                                                DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM,
                                                DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM}, // 与下面数组元素要一一对应
                                        new int[] {R.id.search_key, R.id.search_description, R.id.search_timestamp, R.id.search_isLoc, R.id.search_longitude, R.id.search_latitude});
                                mSearchHistoryList.setAdapter(simAdapt);
                                mHistoryLayout.setVisibility(View.VISIBLE);
                            }
                        } catch (Exception e) {
                            XLog.e("ERROR: delete database error");
                            GoUtils.DisplayToast(MainActivity.this,getResources().getString(R.string.history_delete_error));
                        }
                    })
                    .setNegativeButton("取消",
                            (dialog, which) -> {
                            })
                    .show();
            return true;
        });
        //设置搜索建议返回值监听
        mSuggestionSearch = SuggestionSearch.newInstance();
        mSuggestionSearch.setOnGetSuggestionResultListener(suggestionResult -> {
            if (suggestionResult == null
                    || suggestionResult.error != SearchResult.ERRORNO.NO_ERROR
                    || suggestionResult.getAllSuggestions() == null) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                GoUtils.DisplayToast(this,getResources().getString(R.string.app_search_null));
            } else {
                List<Map<String, Object>> data = getMapList(suggestionResult);

                if (data.isEmpty()) {
                    mSearchLayout.setVisibility(View.INVISIBLE);
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_search_null));
                    return;
                }

                SearchResultAdapter simAdapt = new SearchResultAdapter(
                        MainActivity.this,
                        data,
                        new String[] {POI_NAME, POI_ADDRESS, POI_LONGITUDE, POI_LATITUDE}, // 与下面数组元素要一一对应
                        new int[] {R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude},
                        this::saveSearchResult);
                mSearchList.setAdapter(simAdapt);
                // mSearchList.setVisibility(View.VISIBLE);
                mSearchLayout.setVisibility(View.VISIBLE);
            }
        });
    }

    private void saveSearchResult(String name, String address, String longitude, String latitude) {
        try {
            String location = TextUtils.isEmpty(address) ? name : name + " - " + address;
            saveFavoriteLocation(
                    location, Double.parseDouble(longitude), Double.parseDouble(latitude));
        } catch (NumberFormatException e) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.search_favorite_error));
            XLog.e("Search result has invalid coordinates", e);
        }
    }

    private void saveFavoriteLocation(String location, double longitude, double latitude) {
        boolean saved = DataBaseHistoryLocation.saveLocation(
                mLocationHistoryDB, location, longitude, latitude, true);
        GoUtils.DisplayToast(this, getResources().getString(
                saved ? R.string.search_favorite_ok : R.string.search_favorite_error));
    }

    @NonNull
    private static List<Map<String, Object>> getMapList(SuggestionResult suggestionResult) {
        List<Map<String, Object>> data = new ArrayList<>();
        int retCnt = suggestionResult.getAllSuggestions().size();

        for (int i = 0; i < retCnt; i++) {
            if (suggestionResult.getAllSuggestions().get(i).pt == null) {
                continue;
            }

            Map<String, Object> poiItem = new HashMap<>();
            poiItem.put(POI_NAME, suggestionResult.getAllSuggestions().get(i).key);
            poiItem.put(POI_ADDRESS, suggestionResult.getAllSuggestions().get(i).city + " " + suggestionResult.getAllSuggestions().get(i).district);
            poiItem.put(POI_LONGITUDE, "" + suggestionResult.getAllSuggestions().get(i).pt.longitude);
            poiItem.put(POI_LATITUDE, "" + suggestionResult.getAllSuggestions().get(i).pt.latitude);
            data.add(poiItem);
        }
        return data;
    }

    /*============================== 更新 相关 ==============================*/
    private void initUpdateVersion() {
        mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        mDownloadId = sharedPreferences.getLong(UPDATE_DOWNLOAD_ID, -1);
        mUpdateFilename = sharedPreferences.getString(UPDATE_DOWNLOAD_FILENAME, null);
        mDownloadBdRcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())
                        && mDownloadId > 0
                        && intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == mDownloadId) {
                    installNewVersion();
                }
            }
        };
        registerReceiver(mDownloadBdRcv, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    // Only the navigation menu invokes this method; app startup does not check or show updates.
    private void checkUpdateVersion() {
        if (mUpdateCheckInFlight || (mUpdateDialog != null && mUpdateDialog.isShowing())
                || isFinishing() || isDestroyed()) {
            return;
        }
        mUpdateCheckInFlight = true;
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://api.github.com/repos/zcshou/gogogo/releases/latest").get().build();
        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    mUpdateCheckInFlight = false;
                    if (!call.isCanceled() && !isFinishing() && !isDestroyed()) {
                        GoUtils.DisplayToast(MainActivity.this, getString(R.string.update_check_failed));
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response closeableResponse = response) {
                    ResponseBody body = closeableResponse.body();
                    if (!closeableResponse.isSuccessful() || body == null) {
                        throw new IOException("Invalid update response");
                    }
                    JSONObject release = new JSONObject(body.string());
                    runOnUiThread(() -> {
                        mUpdateCheckInFlight = false;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        try {
                            if (VersionUtils.isNewerVersion(release.getString("tag_name"), BuildConfig.VERSION_NAME)) {
                                showUpdateDialog(release);
                            } else {
                                GoUtils.DisplayToast(MainActivity.this, getString(R.string.update_last));
                            }
                        } catch (JSONException | IllegalArgumentException e) {
                            XLog.e("Invalid release metadata", e);
                            GoUtils.DisplayToast(MainActivity.this, getString(R.string.update_check_failed));
                        }
                    });
                } catch (IOException | JSONException e) {
                    XLog.e("Update check failed", e);
                    runOnUiThread(() -> {
                        mUpdateCheckInFlight = false;
                        if (!isFinishing() && !isDestroyed()) {
                            GoUtils.DisplayToast(MainActivity.this, getString(R.string.update_check_failed));
                        }
                    });
                }
            }
        });
    }

    private JSONObject findUpdateAsset(JSONArray assets) throws JSONException {
        JSONObject fallback = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.optString("name").toLowerCase(java.util.Locale.ROOT);
            if (!name.endsWith(".apk") || name.contains("debug")) {
                continue;
            }
            if (name.contains("arm64-v8a")) {
                return asset;
            }
            if (!name.contains("x86") && !name.contains("armeabi") && fallback == null) {
                fallback = asset;
            }
        }
        return fallback;
    }

    private void showUpdateDialog(JSONObject release) throws JSONException {
        JSONObject asset = findUpdateAsset(release.getJSONArray("assets"));
        if (asset == null) {
            GoUtils.DisplayToast(this, getString(R.string.update_check_failed));
            return;
        }
        String downloadUrl = asset.getString("browser_download_url");
        String filename = asset.getString("name");
        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).create();
        mUpdateDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (mUpdateDialog == dialog) {
                mUpdateDialog = null;
            }
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) {
            dialog.dismiss();
            return;
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setContentView(R.layout.update);
        window.setGravity(Gravity.CENTER);
        window.setWindowAnimations(R.style.DialogAnimFadeInFadeOut);

        ((TextView) window.findViewById(R.id.update_title)).setText(
                release.optString("name", release.getString("tag_name")));
        ((TextView) window.findViewById(R.id.update_time)).setText(release.optString("published_at"));
        ((TextView) window.findViewById(R.id.update_commit)).setText(release.optString("target_commitish"));
        TextView content = window.findViewById(R.id.update_content);
        Markwon.create(this).setMarkdown(content, release.optString("body"));
        window.findViewById(R.id.update_ignore).setOnClickListener(v -> dialog.dismiss());
        window.findViewById(R.id.update_agree).setOnClickListener(v -> {
            dialog.dismiss();
            downloadNewVersion(downloadUrl, filename);
        });
    }

    private int getUpdateDownloadStatus() {
        if (mDownloadManager == null || mDownloadId <= 0) {
            return -1;
        }
        try (Cursor cursor = mDownloadManager.query(new DownloadManager.Query().setFilterById(mDownloadId))) {
            return cursor != null && cursor.moveToFirst()
                    ? cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) : -1;
        } catch (RuntimeException e) {
            XLog.e("Failed to query update download", e);
            return -1;
        }
    }

    private void downloadNewVersion(String url, String filename) {
        if (mDownloadManager == null || TextUtils.isEmpty(filename)
                || !filename.equals(new File(filename).getName()) || filename.contains("\\")
                || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
            GoUtils.DisplayToast(this, getString(R.string.update_download_failed));
            return;
        }
        int status = getUpdateDownloadStatus();
        if (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING
                || status == DownloadManager.STATUS_PAUSED) {
            GoUtils.DisplayToast(this, getString(R.string.update_download_in_progress));
            return;
        }
        File completedDirectory = getExternalFilesDir("Updates");
        if (status == DownloadManager.STATUS_SUCCESSFUL && filename.equals(mUpdateFilename)
                && completedDirectory != null && new File(completedDirectory, filename).isFile()
                && mDownloadManager.getUriForDownloadedFile(mDownloadId) != null) {
            installNewVersion();
            return;
        }
        try {
            File directory = getExternalFilesDir("Updates");
            if (directory == null) {
                throw new IOException("Update storage unavailable");
            }
            File file = new File(directory, filename);
            if (mDownloadId > 0) {
                mDownloadManager.remove(mDownloadId);
                clearUpdateDownload();
            }
            if (file.exists() && !file.delete()) {
                throw new IOException("Cannot replace update file");
            }
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .setAllowedOverRoaming(false)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setTitle(GoUtils.getAppName(this))
                    .setDescription(getString(R.string.update_downloading))
                    .setMimeType("application/vnd.android.package-archive")
                    .setDestinationUri(Uri.fromFile(file));
            mDownloadId = mDownloadManager.enqueue(request);
            mUpdateFilename = filename;
            sharedPreferences.edit().putLong(UPDATE_DOWNLOAD_ID, mDownloadId)
                    .putString(UPDATE_DOWNLOAD_FILENAME, filename).apply();
            GoUtils.DisplayToast(this, getString(R.string.update_downloading));
        } catch (IOException | RuntimeException e) {
            XLog.e("Failed to download update", e);
            GoUtils.DisplayToast(this, getString(R.string.update_download_failed));
        }
    }

    private void clearUpdateDownload() {
        mDownloadId = -1;
        mUpdateFilename = null;
        sharedPreferences.edit().remove(UPDATE_DOWNLOAD_ID).remove(UPDATE_DOWNLOAD_FILENAME).apply();
    }

    private void installNewVersion() {
        if (mDownloadId <= 0 || TextUtils.isEmpty(mUpdateFilename) || mInstallPermissionPending) {
            return;
        }
        if (getUpdateDownloadStatus() != DownloadManager.STATUS_SUCCESSFUL) {
            GoUtils.DisplayToast(this, getString(R.string.update_download_failed));
            return;
        }
        File directory = getExternalFilesDir("Updates");
        if (directory == null || !mUpdateFilename.equals(new File(mUpdateFilename).getName())
                || mUpdateFilename.contains("\\")) {
            GoUtils.DisplayToast(this, getString(R.string.update_download_failed));
            return;
        }
        File file = new File(directory, mUpdateFilename);
        if (!file.isFile() || mDownloadManager.getUriForDownloadedFile(mDownloadId) == null) {
            GoUtils.DisplayToast(this, getString(R.string.update_download_failed));
            return;
        }
        try {
            if (!getPackageManager().canRequestPackageInstalls()) {
                mInstallPermissionPending = true;
                GoUtils.DisplayToast(this, getString(R.string.update_install_permission_required));
                mInstallPermissionLauncher.launch(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
                return;
            }
            Intent install = new Intent(Intent.ACTION_VIEW)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .setDataAndType(ShareUtils.getUriFromFile(this, file), "application/vnd.android.package-archive");
            startActivity(install);
            clearUpdateDownload();
        } catch (RuntimeException e) {
            mInstallPermissionPending = false;
            XLog.e("Failed to open update installer", e);
            GoUtils.DisplayToast(this, getString(R.string.update_install_failed));
        }
    }
}
