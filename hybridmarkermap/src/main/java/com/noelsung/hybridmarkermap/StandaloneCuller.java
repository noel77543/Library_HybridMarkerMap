package com.noelsung.hybridmarkermap;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.maps.android.SphericalUtil;
import com.noelsung.hybridmarkermap.model.StandaloneMarker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 不可群組化項目的可視範圍裁切計算，僅供 {@link HybridMarkerMap} 內部使用
 * 純計算、不操作地圖，以便單元測試
 */
final class StandaloneCuller {

    private StandaloneCuller() {
    }

    //---------

    /***
     * 計算結果：需要移除、新增、更新的項目
     */
    static final class Plan<S> {
        //需移除marker的項目ID：已不在資料中，或已移出可視範圍
        final List<String> toRemove = new ArrayList<>();
        //需新建marker的項目
        final List<StandaloneMarker<S>> toAdd = new ArrayList<>();
        //已有marker、但資料物件被替換的項目
        final List<StandaloneMarker<S>> toUpdate = new ArrayList<>();
    }

    //---------

    /***
     * @param center     鏡頭中心
     * @param radius     可視半徑 (公尺)
     * @param items      所有項目 <ID, 項目>
     * @param visible    目前已繪製marker的項目 <ID, 繪製時所用的項目>
     * @param maxVisible 可視範圍內最多繪製的數量
     */
    @NonNull
    static <S> Plan<S> plan(@NonNull LatLng center, double radius,
                            @NonNull Map<String, StandaloneMarker<S>> items,
                            @NonNull Map<String, StandaloneMarker<S>> visible,
                            int maxVisible) {
        Plan<S> plan = new Plan<>();
        List<StandaloneMarker<S>> candidates = new ArrayList<>();

        for (String id : visible.keySet()) {
            if (!items.containsKey(id)) {
                plan.toRemove.add(id);
            }
        }

        for (StandaloneMarker<S> item : items.values()) {
            boolean isVisible = visible.containsKey(item.getId());
            boolean inRange = SphericalUtil.computeDistanceBetween(center, item.getPosition()) < radius;

            if (inRange) {
                if (!isVisible) {
                    candidates.add(item);
                } else if (visible.get(item.getId()) != item) {
                    plan.toUpdate.add(item);
                }
            } else if (isVisible) {
                plan.toRemove.add(item.getId());
            }
        }

        //先扣除將被移除的marker再計算剩餘名額，名額不足時優先繪製離鏡頭中心較近的項目
        int capacity = maxVisible - (visible.size() - plan.toRemove.size());
        if (capacity > 0) {
            if (candidates.size() > capacity) {
                Collections.sort(candidates, (a, b) -> Double.compare(
                        SphericalUtil.computeDistanceBetween(center, a.getPosition()),
                        SphericalUtil.computeDistanceBetween(center, b.getPosition())));
            }
            plan.toAdd.addAll(candidates.subList(0, Math.min(capacity, candidates.size())));
        }
        return plan;
    }

    //---------

    /***
     * 以可視範圍中心點為圓心，至角落的距離為半徑
     * @return 單位 公尺
     */
    static double radiusOf(@NonNull VisibleRegion visibleRegion) {
        LatLng farLeft = visibleRegion.farLeft;
        LatLng farRight = visibleRegion.farRight;
        LatLng nearLeft = visibleRegion.nearLeft;
        LatLng nearRight = visibleRegion.nearRight;

        //鏡頭左至右的直線距離
        double width = SphericalUtil.computeDistanceBetween(
                new LatLng((farLeft.latitude + nearLeft.latitude) / 2, farLeft.longitude),
                new LatLng((farRight.latitude + nearRight.latitude) / 2, farRight.longitude));
        //鏡頭上至下的直線距離
        double height = SphericalUtil.computeDistanceBetween(
                new LatLng(farRight.latitude, (farRight.longitude + farLeft.longitude) / 2),
                new LatLng(nearRight.latitude, (nearRight.longitude + nearLeft.longitude) / 2));

        //將寬高都除以二後 用畢氏定理得解中心至角落的距離即為半徑
        return Math.sqrt(width * width + height * height) / 2;
    }
}
