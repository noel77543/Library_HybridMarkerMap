package com.noelsung.hybridmarkermap.demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.Gson;
import com.noelsung.hybridmarkermap.HybridMarkerMap;
import com.noelsung.hybridmarkermap.MapStyle;
import com.noelsung.hybridmarkermap.demo.model.SamplePointResponse;
import com.noelsung.hybridmarkermap.demo.model.SamplePointResponse.Point;
import com.noelsung.hybridmarkermap.model.ClusterableMarker;
import com.noelsung.hybridmarkermap.model.StandaloneMarker;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/***
 * HybridMarkerMap 示範：
 * 可群組化項目 - assets/sample_clusterable.json，依狀態 1~4 顯示不同顏色
 * 不可群組化項目 - assets/sample_standalone.json，數值大於 0 視為啟用
 * 上方可切換 lib 提供的地圖樣式
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback,
        HybridMarkerMap.OnMarkerClickListener<Point, Point>, HybridMarkerMap.OnUnclusteredItemsChangeListener<Point> {

    private static final String TAG = "HybridMarkerMapDemo";
    //可視範圍內最多繪製的不可群組化項目數量
    private static final int MAX_VISIBLE_STANDALONE = 300;
    private static final LatLng INITIAL_CENTER = new LatLng(23.7, 120.95);
    private static final float INITIAL_ZOOM = 7.5f;

    private GoogleMap googleMap;
    //兩種標記的 payload 皆直接使用範例資料物件
    private HybridMarkerMap<Point, Point> hybridMarkerMap;
    private TextView textStatus;
    private ChipGroup chipGroupMapStyle;

    //可群組化項目 狀態1~4 的icon
    private final List<Bitmap> clusterableIcons = new ArrayList<>();
    private Bitmap standaloneActiveIcon;
    private Bitmap standaloneInactiveIcon;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> enableMyLocation());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textStatus = findViewById(R.id.text_status);
        chipGroupMapStyle = findViewById(R.id.chip_group_map_style);

        initMarkerIcons();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        mapFragment.getMapAsync(this);
    }

    //-----------

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;

        googleMap.getUiSettings().setMapToolbarEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(false);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(INITIAL_CENTER, INITIAL_ZOOM));

        hybridMarkerMap = new HybridMarkerMap<>(this, googleMap);
        hybridMarkerMap.setMapStyle(MapStyle.GRAYSCALE);
        //先設置icon與監聽 再放入資料
        hybridMarkerMap.setClusterableIconProvider(this::getClusterableIcon);
        hybridMarkerMap.setStandaloneIconProvider(this::getStandaloneIcon);
        //圓點圖示以中心對齊座標 (水滴形 pin 維持預設的底部中央)
        hybridMarkerMap.setStandaloneIconAnchor(0.5f, 0.5f);
        hybridMarkerMap.setOnMarkerClickListener(this);
        hybridMarkerMap.setOnUnclusteredItemsChangeListener(this);

        hybridMarkerMap.setClusterableMarkers(loadClusterableMarkers());
        hybridMarkerMap.setStandaloneMarkers(loadStandaloneMarkers(), MAX_VISIBLE_STANDALONE);

        initMapStyleChips();
        requestLocationPermission();
    }

    //-----------

    /***
     * 依 lib 提供的 MapStyle 建立切換按鈕
     */
    private void initMapStyleChips() {
        for (MapStyle mapStyle : MapStyle.values()) {
            Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setText(getMapStyleName(mapStyle));
            chip.setCheckable(true);
            chip.setChecked(mapStyle == hybridMarkerMap.getMapStyle());
            chip.setOnCheckedChangeListener((button, isChecked) -> {
                if (isChecked) {
                    hybridMarkerMap.setMapStyle(mapStyle);
                }
            });
            chipGroupMapStyle.addView(chip);
        }
    }

    //-----------

    @StringRes
    private int getMapStyleName(MapStyle mapStyle) {
        switch (mapStyle) {
            case GRAYSCALE:
                return R.string.map_style_grayscale;
            case NIGHT:
                return R.string.map_style_night;
            case RETRO:
                return R.string.map_style_retro;
            case MINIMAL:
                return R.string.map_style_minimal;
            case DEFAULT:
            default:
                return R.string.map_style_default;
        }
    }

    //-----------

    private List<ClusterableMarker<Point>> loadClusterableMarkers() {
        List<ClusterableMarker<Point>> items = new ArrayList<>();
        for (Point point : loadSamplePoints("sample_clusterable.json")) {
            items.add(new ClusterableMarker<>(point.getId(), point.getLat(), point.getLng(), point));
        }
        return items;
    }

    //-----------

    private List<StandaloneMarker<Point>> loadStandaloneMarkers() {
        List<StandaloneMarker<Point>> items = new ArrayList<>();
        for (Point point : loadSamplePoints("sample_standalone.json")) {
            items.add(new StandaloneMarker<>(point.getId(), point.getLat(), point.getLng(), point));
        }
        return items;
    }

    //-----------

    /***
     * 可群組化項目icon 依狀態1~4著色
     */
    private Bitmap getClusterableIcon(@NonNull ClusterableMarker<Point> item) {
        int index = (int) item.getPayload().getStatus() - 1;
        if (index < 0 || index >= clusterableIcons.size()) {
            index = clusterableIcons.size() - 1;
        }
        return clusterableIcons.get(index);
    }

    //-----------

    /***
     * 不可群組化項目icon 數值大於0為啟用色 否則為灰色
     */
    private Bitmap getStandaloneIcon(@NonNull StandaloneMarker<Point> item) {
        return item.getPayload().getStatus() > 0 ? standaloneActiveIcon : standaloneInactiveIcon;
    }

    //-----------

    @Override
    public void onClusterClick(@NonNull Marker marker, @NonNull List<ClusterableMarker<Point>> items) {
        textStatus.setText(getString(R.string.status_cluster_click, items.size()));
    }

    @Override
    public void onClusterableMarkerClick(@NonNull Marker marker, @NonNull ClusterableMarker<Point> item) {
        textStatus.setText(getString(R.string.status_clusterable_click, item.getPayload().getName(), item.getPayload().getStatus()));
    }

    @Override
    public void onStandaloneMarkerClick(@NonNull Marker marker, @NonNull StandaloneMarker<Point> item) {
        textStatus.setText(getString(R.string.status_standalone_click, item.getPayload().getName(), item.getPayload().getStatus()));
    }

    @Override
    public void onUnclusteredItemsChanged(@NonNull List<ClusterableMarker<Point>> items) {
        Log.d(TAG, "未被群組化的項目: " + items.size());
        textStatus.setText(getString(R.string.status_unclustered, items.size()));
    }

    //-----------

    private void requestLocationPermission() {
        if (hasLocationPermission()) {
            enableMyLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    //-----------

    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (googleMap != null && hasLocationPermission()) {
            //顯示小藍點 隱藏準心按鈕
            googleMap.setMyLocationEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        }
    }

    //-----------

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    //-----------

    private List<Point> loadSamplePoints(String fileName) {
        try (Reader reader = new InputStreamReader(getAssets().open(fileName), StandardCharsets.UTF_8)) {
            SamplePointResponse response = new Gson().fromJson(reader, SamplePointResponse.class);
            if (response != null && response.getItems() != null) {
                return response.getItems();
            }
        } catch (IOException e) {
            Log.e(TAG, "讀取範例資料失敗: " + fileName, e);
        }
        return Collections.emptyList();
    }

    //------------

    /***
     * 初始化會被多次重複使用的icon
     */
    private void initMarkerIcons() {
        for (int colorRes : new int[]{R.color.marker_status_1, R.color.marker_status_2, R.color.marker_status_3, R.color.marker_status_4}) {
            clusterableIcons.add(createMarkerBitmap(R.drawable.ic_marker_pin_body, R.drawable.ic_marker_pin_overlay, colorRes));
        }
        standaloneActiveIcon = createMarkerBitmap(R.drawable.ic_marker_dot_body, R.drawable.ic_marker_dot_overlay, R.color.marker_active);
        standaloneInactiveIcon = createMarkerBitmap(R.drawable.ic_marker_dot_body, R.drawable.ic_marker_dot_overlay, R.color.marker_inactive);
    }

    //------------

    /***
     * 將著色後的主體與白色覆蓋層繪製成 Bitmap
     */
    private Bitmap createMarkerBitmap(@DrawableRes int bodyRes, @DrawableRes int overlayRes, @ColorRes int colorRes) {
        Drawable body = ContextCompat.getDrawable(this, bodyRes).mutate();
        Drawable overlay = ContextCompat.getDrawable(this, overlayRes);
        body.setTint(ContextCompat.getColor(this, colorRes));

        int width = body.getIntrinsicWidth();
        int height = body.getIntrinsicHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        body.setBounds(0, 0, width, height);
        body.draw(canvas);
        overlay.setBounds(0, 0, width, height);
        overlay.draw(canvas);
        return bitmap;
    }
}
