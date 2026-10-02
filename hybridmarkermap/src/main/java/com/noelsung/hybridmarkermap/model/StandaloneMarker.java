package com.noelsung.hybridmarkermap.model;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

/***
 * 不可被群組化、獨立的標記
 * 只有在可視範圍內才會被繪製，移出可視範圍即被回收
 *
 * @param <T> 使用者自訂資料的型別，於 icon provider 與點擊事件中透過 {@link #getPayload()} 取回
 */
public final class StandaloneMarker<T> {

    private final String id;
    private final LatLng position;
    private final T payload;

    /***
     * @param id       唯一識別碼，重新設置資料時以此判斷是否為同一個項目
     * @param position 座標
     * @param payload  使用者自訂資料
     */
    public StandaloneMarker(@NonNull String id, @NonNull LatLng position, T payload) {
        this.id = id;
        this.position = position;
        this.payload = payload;
    }

    public StandaloneMarker(@NonNull String id, double lat, double lng, T payload) {
        this(id, new LatLng(lat, lng), payload);
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public LatLng getPosition() {
        return position;
    }

    public T getPayload() {
        return payload;
    }
}
