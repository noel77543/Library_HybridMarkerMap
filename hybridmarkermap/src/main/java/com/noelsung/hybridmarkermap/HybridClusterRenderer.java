package com.noelsung.hybridmarkermap;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.noelsung.hybridmarkermap.model.ClusterableMarker;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 可群組化項目的 renderer，僅供 {@link HybridMarkerMap} 內部使用
 *
 * @param <C> 可群組化項目的自訂資料型別
 */
class HybridClusterRenderer<C> extends DefaultClusterRenderer<ClusterableMarker<C>> {

    interface IconSource<C> {
        Bitmap getIcon(ClusterableMarker<C> item);
    }

    interface OnClustersChangeListener<C> {
        /***
         * @param unclustered 被延展開、以單一 marker 呈現的項目
         * @param clustered   被聚合在群組中的項目
         */
        void onClustersChanged(List<ClusterableMarker<C>> unclustered, List<ClusterableMarker<C>> clustered);
    }

    private final IconSource<C> iconSource;
    private final OnClustersChangeListener<C> onClustersChangeListener;
    //被延展開的項目marker之錨點 預設為底部中央
    private float itemAnchorU = 0.5f;
    private float itemAnchorV = 1f;

    HybridClusterRenderer(Context context, GoogleMap googleMap, ClusterManager<ClusterableMarker<C>> clusterManager,
                          IconSource<C> iconSource, OnClustersChangeListener<C> onClustersChangeListener) {
        super(context, googleMap, clusterManager);
        this.iconSource = iconSource;
        this.onClustersChangeListener = onClustersChangeListener;
    }

    //--------

    /***
     * 設置之後才被延展開的項目marker所使用的錨點
     */
    void setItemAnchor(float anchorU, float anchorV) {
        itemAnchorU = anchorU;
        itemAnchorV = anchorV;
    }

    //--------

    /***
     * 每當群組計算完成（地圖縮放、資料改變），在 marker 實際延展/聚合之前觸發
     */
    @Override
    public void onClustersChanged(@NonNull Set<? extends Cluster<ClusterableMarker<C>>> clusters) {
        super.onClustersChanged(clusters);

        List<ClusterableMarker<C>> unclustered = new ArrayList<>();
        List<ClusterableMarker<C>> clustered = new ArrayList<>();
        for (Cluster<ClusterableMarker<C>> cluster : clusters) {
            if (shouldRenderAsCluster(cluster)) {
                clustered.addAll(cluster.getItems());
            } else {
                unclustered.addAll(cluster.getItems());
            }
        }
        onClustersChangeListener.onClustersChanged(unclustered, clustered);
    }

    //--------------

    /***
     * 每當有子項目被延展前 設置其 icon
     */
    @Override
    protected void onBeforeClusterItemRendered(@NonNull ClusterableMarker<C> item, @NonNull MarkerOptions markerOptions) {
        super.onBeforeClusterItemRendered(item, markerOptions);
        markerOptions.anchor(itemAnchorU, itemAnchorV);
        Bitmap bitmap = iconSource.getIcon(item);
        if (bitmap != null) {
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(bitmap));
        }
    }
}
