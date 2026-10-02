package com.noelsung.hybridmarkermap.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;

/***
 * 可被群組化的標記
 * 地圖縮小時，相近的項目會被合併為一個群組，放大後再展開
 *
 * @param <T> 使用者自訂資料的型別，於 icon provider 與點擊事件中透過 {@link #getPayload()} 取回
 */
public final class ClusterableMarker<T> implements ClusterItem {

    private final String id;
    private final LatLng position;
    private final T payload;

    /***
     * @param id       唯一識別碼，用以追蹤選取狀態
     * @param position 座標，建立後不可變更；位置改變時請建立新的物件並重新設置
     * @param payload  使用者自訂資料
     */
    public ClusterableMarker(@NonNull String id, @NonNull LatLng position, T payload) {
        this.id = id;
        this.position = position;
        this.payload = payload;
    }

    public ClusterableMarker(@NonNull String id, double lat, double lng, T payload) {
        this(id, new LatLng(lat, lng), payload);
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    @Override
    public LatLng getPosition() {
        return position;
    }

    public T getPayload() {
        return payload;
    }

    //不提供 title / snippet，點擊時不顯示預設的 InfoWindow
    @Nullable
    @Override
    public String getTitle() {
        return null;
    }

    @Nullable
    @Override
    public String getSnippet() {
        return null;
    }

    @Nullable
    @Override
    public Float getZIndex() {
        return null;
    }
}
