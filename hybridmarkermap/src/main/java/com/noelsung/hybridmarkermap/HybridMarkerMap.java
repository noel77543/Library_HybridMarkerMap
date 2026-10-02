package com.noelsung.hybridmarkermap;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.collections.MarkerManager;
import com.noelsung.hybridmarkermap.model.ClusterableMarker;
import com.noelsung.hybridmarkermap.model.StandaloneMarker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/***
 * 在同一張 GoogleMap 上同時管理兩種標記：
 * 1. 可群組化 {@link ClusterableMarker}：相近的項目會被合併成群組
 * 2. 不可群組化 {@link StandaloneMarker}：不合併，只繪製可視範圍內的項目並限制數量，移出可視範圍即回收
 *
 * 須在 onMapReady 之後建立，所有方法皆須在 UI thread 呼叫
 *
 * @param <C> 可群組化項目的自訂資料型別 (payload)
 * @param <S> 不可群組化項目的自訂資料型別 (payload)
 */
@UiThread
public class HybridMarkerMap<C, S> {

    //選取的 marker 放大倍率
    private static final float SELECTED_MARKER_SCALE = 1.5f;
    //點擊群組時 鏡頭範圍的內距(px)
    private static final int CLUSTER_ZOOM_PADDING = 100;
    private static final String STANDALONE_COLLECTION_ID = "HybridMarkerMap_Standalone";

    private final Context context;
    private final GoogleMap googleMap;
    private final ClusterManager<ClusterableMarker<C>> clusterManager;
    private final HybridClusterRenderer<C> clusterRenderer;
    private final MarkerManager.Collection standaloneCollection;

    private ClusterableIconProvider<C> clusterableIconProvider;
    private StandaloneIconProvider<S> standaloneIconProvider;
    private OnMarkerClickListener<C, S> onMarkerClickListener;
    private OnUnclusteredItemsChangeListener<C> onUnclusteredItemsChangeListener;
    private MapStyle mapStyle = MapStyle.DEFAULT;

    //所有 不可群組化之項目 <ID, 項目>
    private Map<String, StandaloneMarker<S>> standaloneMarkerMap = new HashMap<>();
    //可視範圍內的 不可群組化之marker <ID, marker>
    private final Map<String, Marker> visibleStandaloneMarkers = new HashMap<>();
    //可視範圍內 不可群組化之marker的最大數量
    private int maxVisibleStandaloneCount;
    //不可群組化之marker的錨點 預設為底部中央
    private float standaloneAnchorU = 0.5f;
    private float standaloneAnchorV = 1f;

    //當前所選的marker
    private Marker selectedMarker;
    //當前所選 可群組化之項目
    private ClusterableMarker<C> selectedClusterable;
    //當前所選 不可群組化之項目
    private StandaloneMarker<S> selectedStandalone;

    /***
     * 建立後會接管 googleMap 的 OnCameraIdleListener 與 marker 點擊事件
     * @param googleMap onMapReady 取得的 GoogleMap
     */
    public HybridMarkerMap(@NonNull Context context, @NonNull GoogleMap googleMap) {
        this.context = context.getApplicationContext();
        this.googleMap = googleMap;

        clusterManager = new ClusterManager<>(context, googleMap);
        clusterRenderer = new HybridClusterRenderer<>(context, googleMap, clusterManager,
                this::getClusterableIcon, this::onClustersChanged);
        clusterManager.setRenderer(clusterRenderer);
        clusterManager.setOnClusterClickListener(cluster -> {
            //鏡頭移至可包含群組內所有項目的範圍
            LatLngBounds.Builder builder = LatLngBounds.builder();
            for (ClusterableMarker<C> item : cluster.getItems()) {
                builder.include(item.getPosition());
            }
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), CLUSTER_ZOOM_PADDING));

            Marker marker = clusterRenderer.getMarker(cluster);
            if (onMarkerClickListener != null && marker != null) {
                onMarkerClickListener.onClusterClick(marker, new ArrayList<>(cluster.getItems()));
            }
            return true;
        });
        clusterManager.setOnClusterItemClickListener(item -> {
            Marker marker = clusterRenderer.getMarker(item);
            if (marker != null) {
                select(marker, item, null);
                if (onMarkerClickListener != null) {
                    onMarkerClickListener.onClusterableMarkerClick(marker, item);
                }
            }
            return false;
        });

        //MarkerManager 已將自身設為 googleMap 的 marker 點擊監聽，不可群組化的 marker 放入同一個 manager 的獨立 collection
        standaloneCollection = clusterManager.getMarkerManager().newCollection(STANDALONE_COLLECTION_ID);
        standaloneCollection.setOnMarkerClickListener(marker -> {
            StandaloneMarker<S> item = getStandaloneItem(marker);
            if (item != null) {
                select(marker, null, item);
                if (onMarkerClickListener != null) {
                    onMarkerClickListener.onStandaloneMarkerClick(marker, item);
                }
            }
            return false;
        });

        //每當地圖停止移動(地圖初始化完成時也會觸發一次)
        googleMap.setOnCameraIdleListener(() -> {
            clusterManager.onCameraIdle();
            refreshStandaloneMarkers();
        });
    }

    //-------------

    /***
     * 設置/替換 所有可群組化的項目
     */
    public void setClusterableMarkers(@NonNull Collection<ClusterableMarker<C>> items) {
        //所選的項目已不在新資料中 其marker將被移除 無須走入還原邏輯
        if (selectedClusterable != null && !items.contains(selectedClusterable)) {
            clearSelectionState();
        }
        clusterManager.clearItems();
        clusterManager.addItems(items);
        clusterManager.cluster();
    }

    //-------------

    /***
     * 設置/替換 所有不可群組化的項目
     * @param maxVisibleCount 可視範圍內最多繪製的數量 (建議不超過一千)
     */
    public void setStandaloneMarkers(@NonNull Collection<StandaloneMarker<S>> items, int maxVisibleCount) {
        Map<String, StandaloneMarker<S>> map = new HashMap<>();
        for (StandaloneMarker<S> item : items) {
            map.put(item.getId(), item);
        }
        standaloneMarkerMap = map;
        maxVisibleStandaloneCount = maxVisibleCount;
        refreshStandaloneMarkers();
    }

    //-------------

    /***
     * 移除所有標記
     */
    public void clear() {
        clearSelectionState();
        clusterManager.clearItems();
        clusterManager.cluster();
        standaloneMarkerMap = new HashMap<>();
        visibleStandaloneMarkers.clear();
        standaloneCollection.clear();
    }

    //-------------

    /***
     * 設置 可群組化的項目被延展出來時的icon，未設置則使用預設marker
     */
    public void setClusterableIconProvider(@Nullable ClusterableIconProvider<C> provider) {
        this.clusterableIconProvider = provider;
        for (Marker marker : clusterManager.getMarkerCollection().getMarkers()) {
            ClusterableMarker<C> item = clusterRenderer.getClusterItem(marker);
            if (item != null) {
                applyIcon(marker, getClusterableIcon(item), marker.equals(selectedMarker));
            }
        }
    }

    //----------

    /***
     * 設置 不可群組化的項目的icon，未設置則使用預設marker
     */
    public void setStandaloneIconProvider(@Nullable StandaloneIconProvider<S> provider) {
        this.standaloneIconProvider = provider;
        for (Marker marker : visibleStandaloneMarkers.values()) {
            StandaloneMarker<S> item = getStandaloneItem(marker);
            if (item != null) {
                applyIcon(marker, getStandaloneIcon(item), marker.equals(selectedMarker));
            }
        }
    }

    //---------

    /***
     * 設置 可群組化的項目被延展出來時 icon 對齊座標的錨點
     * 以 icon 的比例表示，(0, 0) 為左上角、(1, 1) 為右下角
     * 預設為 (0.5, 1) 底部中央，適用水滴形圖示；圓形圖示可設為 (0.5, 0.5)
     */
    public void setClusterableIconAnchor(float anchorU, float anchorV) {
        clusterRenderer.setItemAnchor(anchorU, anchorV);
        for (Marker marker : clusterManager.getMarkerCollection().getMarkers()) {
            marker.setAnchor(anchorU, anchorV);
        }
    }

    //---------

    /***
     * 設置 不可群組化的項目 icon 對齊座標的錨點
     * 以 icon 的比例表示，(0, 0) 為左上角、(1, 1) 為右下角
     * 預設為 (0.5, 1) 底部中央，適用水滴形圖示；圓形圖示可設為 (0.5, 0.5)
     */
    public void setStandaloneIconAnchor(float anchorU, float anchorV) {
        standaloneAnchorU = anchorU;
        standaloneAnchorV = anchorV;
        for (Marker marker : visibleStandaloneMarkers.values()) {
            marker.setAnchor(anchorU, anchorV);
        }
    }

    //---------

    /***
     * 設置 marker click listener
     * 包含 可群組化的群組、可群組化的項目、不可群組化的項目
     */
    public void setOnMarkerClickListener(@Nullable OnMarkerClickListener<C, S> listener) {
        this.onMarkerClickListener = listener;
    }

    //---------

    /***
     * 設置 當未被群組化(被延展開)的可群組化項目改變
     */
    public void setOnUnclusteredItemsChangeListener(@Nullable OnUnclusteredItemsChangeListener<C> listener) {
        this.onUnclusteredItemsChangeListener = listener;
    }

    //---------

    /***
     * 取消當前選取 並還原其marker大小
     */
    public void clearSelection() {
        if (selectedMarker != null) {
            if (selectedClusterable != null) {
                applyIcon(selectedMarker, getClusterableIcon(selectedClusterable), false);
            } else if (selectedStandalone != null) {
                applyIcon(selectedMarker, getStandaloneIcon(selectedStandalone), false);
            }
        }
        clearSelectionState();
    }

    //---------

    /***
     * 設置地圖樣式
     */
    public void setMapStyle(@NonNull MapStyle mapStyle) {
        this.mapStyle = mapStyle;
        googleMap.setMapStyle(mapStyle == MapStyle.DEFAULT ? null : MapStyleOptions.loadRawResourceStyle(context, mapStyle.styleRes));
    }

    //---------

    /***
     * 當前的地圖樣式，未設置過則為 {@link MapStyle#DEFAULT}
     */
    @NonNull
    public MapStyle getMapStyle() {
        return mapStyle;
    }

    //---------

    /***
     * 底層的 ClusterManager，可用於調整群組演算法、動畫等進階設定
     */
    @NonNull
    public ClusterManager<ClusterableMarker<C>> getClusterManager() {
        return clusterManager;
    }

    //---------

    /***
     * 依據鏡頭位置 新增可視範圍內、回收可視範圍外的 不可群組化之marker
     */
    private void refreshStandaloneMarkers() {
        //目前已繪製的項目 <ID, 繪製時所用的項目>
        Map<String, StandaloneMarker<S>> visibleItems = new HashMap<>();
        for (Map.Entry<String, Marker> entry : visibleStandaloneMarkers.entrySet()) {
            visibleItems.put(entry.getKey(), getStandaloneItem(entry.getValue()));
        }
        StandaloneCuller.Plan<S> plan = StandaloneCuller.plan(
                googleMap.getCameraPosition().target,
                StandaloneCuller.radiusOf(googleMap.getProjection().getVisibleRegion()),
                standaloneMarkerMap, visibleItems, maxVisibleStandaloneCount);

        //回收已不在資料中、或已移出可視範圍的marker
        for (String id : plan.toRemove) {
            removeStandaloneMarker(visibleStandaloneMarkers.remove(id));
        }

        for (StandaloneMarker<S> item : plan.toAdd) {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(item.getPosition())
                    .anchor(standaloneAnchorU, standaloneAnchorV);
            Bitmap bitmap = getStandaloneIcon(item);
            if (bitmap != null) {
                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(bitmap));
            }
            Marker marker = standaloneCollection.addMarker(markerOptions);
            marker.setTag(item);
            visibleStandaloneMarkers.put(item.getId(), marker);
        }

        //同一個ID的資料被替換 更新既有的marker
        for (StandaloneMarker<S> item : plan.toUpdate) {
            Marker marker = visibleStandaloneMarkers.get(item.getId());
            boolean isSelected = marker.equals(selectedMarker);
            if (isSelected) {
                selectedStandalone = item;
            }
            marker.setTag(item);
            marker.setPosition(item.getPosition());
            applyIcon(marker, getStandaloneIcon(item), isSelected);
        }
    }

    //---------

    /***
     * 取出不可群組化之marker所對應的項目 (tag 僅由本類別於建立marker時設置)
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private StandaloneMarker<S> getStandaloneItem(Marker marker) {
        Object tag = marker.getTag();
        return tag instanceof StandaloneMarker ? (StandaloneMarker<S>) tag : null;
    }

    //---------

    private void removeStandaloneMarker(Marker marker) {
        //所選的項目因在視野之外而被回收
        if (marker.equals(selectedMarker)) {
            clearSelectionState();
        }
        standaloneCollection.remove(marker);
    }

    //---------

    /***
     * 群組計算完成
     */
    private void onClustersChanged(List<ClusterableMarker<C>> unclustered, List<ClusterableMarker<C>> clustered) {
        //所選的可群組化項目 被群組化回收了
        if (selectedClusterable != null) {
            Set<String> clusteredIds = new HashSet<>();
            for (ClusterableMarker<C> item : clustered) {
                clusteredIds.add(item.getId());
            }
            if (clusteredIds.contains(selectedClusterable.getId())) {
                clearSelectionState();
            }
        }

        if (onUnclusteredItemsChangeListener != null) {
            onUnclusteredItemsChangeListener.onUnclusteredItemsChanged(unclustered);
        }
    }

    //-----------

    /***
     * 還原之前選的marker 並放大新選取的marker
     */
    private void select(Marker marker, @Nullable ClusterableMarker<C> clusterable, @Nullable StandaloneMarker<S> standalone) {
        clearSelection();
        if (clusterable != null) {
            applyIcon(marker, getClusterableIcon(clusterable), true);
        } else if (standalone != null) {
            applyIcon(marker, getStandaloneIcon(standalone), true);
        }
        selectedMarker = marker;
        selectedClusterable = clusterable;
        selectedStandalone = standalone;
    }

    //-----------

    private void clearSelectionState() {
        selectedMarker = null;
        selectedClusterable = null;
        selectedStandalone = null;
    }

    //-----------

    private void applyIcon(Marker marker, @Nullable Bitmap bitmap, boolean enlarge) {
        if (bitmap == null) {
            return;
        }
        marker.setIcon(BitmapDescriptorFactory.fromBitmap(enlarge ? getEnlargedBitmap(bitmap) : bitmap));
    }

    //--------------

    /***
     *  放大圖資
     */
    private Bitmap getEnlargedBitmap(Bitmap bitmap) {
        return Bitmap.createScaledBitmap(bitmap,
                Math.round(bitmap.getWidth() * SELECTED_MARKER_SCALE),
                Math.round(bitmap.getHeight() * SELECTED_MARKER_SCALE),
                true);
    }

    //--------------

    @Nullable
    private Bitmap getClusterableIcon(ClusterableMarker<C> item) {
        return clusterableIconProvider != null ? clusterableIconProvider.getIcon(item) : null;
    }

    @Nullable
    private Bitmap getStandaloneIcon(StandaloneMarker<S> item) {
        return standaloneIconProvider != null ? standaloneIconProvider.getIcon(item) : null;
    }

    //-----------

    /***
     * @param <C> 可群組化項目的自訂資料型別
     */
    public interface ClusterableIconProvider<C> {
        /***
         * @return 可群組化項目被延展出來時的icon，回傳 null 則使用預設marker
         */
        @Nullable
        Bitmap getIcon(@NonNull ClusterableMarker<C> item);
    }

    //-----------

    /***
     * @param <S> 不可群組化項目的自訂資料型別
     */
    public interface StandaloneIconProvider<S> {
        /***
         * @return 不可群組化項目的icon，回傳 null 則使用預設marker
         */
        @Nullable
        Bitmap getIcon(@NonNull StandaloneMarker<S> item);
    }

    //-----------

    /***
     * 三種點擊皆有預設的空實作，只需覆寫需要的事件
     *
     * @param <C> 可群組化項目的自訂資料型別
     * @param <S> 不可群組化項目的自訂資料型別
     */
    public interface OnMarkerClickListener<C, S> {

        /***
         * 當點選可群組化的群組 (地圖會自動移至可包含群組內所有項目的範圍)
         * @param items 群組內的所有項目
         */
        default void onClusterClick(@NonNull Marker marker, @NonNull List<ClusterableMarker<C>> items) {
        }

        /***
         * 當點選可群組化的項目
         */
        default void onClusterableMarkerClick(@NonNull Marker marker, @NonNull ClusterableMarker<C> item) {
        }

        /***
         * 當點選不可群組化的項目
         */
        default void onStandaloneMarkerClick(@NonNull Marker marker, @NonNull StandaloneMarker<S> item) {
        }
    }

    //-----------

    /***
     * @param <C> 可群組化項目的自訂資料型別
     */
    public interface OnUnclusteredItemsChangeListener<C> {
        /***
         * @param items 目前未被群組化(以單一marker呈現)的所有可群組化項目
         */
        void onUnclusteredItemsChanged(@NonNull List<ClusterableMarker<C>> items);
    }
}
