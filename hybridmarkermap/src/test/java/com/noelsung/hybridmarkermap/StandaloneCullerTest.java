package com.noelsung.hybridmarkermap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.maps.android.SphericalUtil;
import com.noelsung.hybridmarkermap.model.StandaloneMarker;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StandaloneCullerTest {

    private static final LatLng CENTER = new LatLng(25.0, 121.5);
    //約 5 公里
    private static final double RADIUS = 5_000;
    //緯度 0.01 度約 1.1 公里
    private static final double LAT_PER_KM = 0.01 / 1.11;

    //---------

    @Test
    public void addsItemsInsideRadius_andSkipsItemsOutside() {
        StandaloneMarker<String> near = marker("near", 1);
        StandaloneMarker<String> far = marker("far", 20);

        StandaloneCuller.Plan<String> plan = StandaloneCuller.plan(CENTER, RADIUS, items(near, far), new HashMap<>(), 100);

        assertEquals(List.of(near), plan.toAdd);
        assertTrue(plan.toRemove.isEmpty());
        assertTrue(plan.toUpdate.isEmpty());
    }

    @Test
    public void removesVisibleItemsThatMovedOutOfRange() {
        StandaloneMarker<String> far = marker("far", 20);

        StandaloneCuller.Plan<String> plan = StandaloneCuller.plan(CENTER, RADIUS, items(far), visible(far), 100);

        assertEquals(List.of("far"), plan.toRemove);
        assertTrue(plan.toAdd.isEmpty());
    }

    @Test
    public void removesVisibleItemsNoLongerInData() {
        StandaloneMarker<String> gone = marker("gone", 1);

        StandaloneCuller.Plan<String> plan = StandaloneCuller.plan(CENTER, RADIUS, items(), visible(gone), 100);

        assertEquals(List.of("gone"), plan.toRemove);
    }

    @Test
    public void keepsVisibleItemUnchanged_whenSameObject() {
        StandaloneMarker<String> item = marker("a", 1);

        StandaloneCuller.Plan<String> plan = StandaloneCuller.plan(CENTER, RADIUS, items(item), visible(item), 100);

        assertTrue(plan.toAdd.isEmpty());
        assertTrue(plan.toRemove.isEmpty());
        assertTrue(plan.toUpdate.isEmpty());
    }

    @Test
    public void updatesVisibleItem_whenReplacedBySameIdObject() {
        StandaloneMarker<String> old = marker("a", 1);
        StandaloneMarker<String> replaced = marker("a", 2);

        StandaloneCuller.Plan<String> plan = StandaloneCuller.plan(CENTER, RADIUS, items(replaced), visible(old), 100);

        assertEquals(1, plan.toUpdate.size());
        assertSame(replaced, plan.toUpdate.get(0));
        assertTrue(plan.toAdd.isEmpty());
        assertTrue(plan.toRemove.isEmpty());
    }

    /***
     * id 的字母順序與距離相反 (a 最遠、d 最近)，確保結果不是剛好依 HashMap 的迭代順序
     */
    @Test
    public void respectsMaxVisible_preferringItemsNearestToCenter() {
        StandaloneMarker<String> km4 = marker("a", 4);
        StandaloneMarker<String> km3 = marker("b", 3);
        StandaloneMarker<String> km2 = marker("c", 2);
        StandaloneMarker<String> km1 = marker("d", 1);

        StandaloneCuller.Plan<String> plan = StandaloneCuller.plan(CENTER, RADIUS, items(km4, km3, km2, km1), new HashMap<>(), 2);

        assertEquals(List.of(km1, km2), plan.toAdd);
    }

    @Test
    public void countsAlreadyVisibleItemsAgainstMax() {
        StandaloneMarker<String> shown = marker("shown", 1);
        StandaloneMarker<String> far = marker("a", 3);
        StandaloneMarker<String> near = marker("b", 2);

        StandaloneCuller.Plan<String> plan = StandaloneCuller.plan(CENTER, RADIUS, items(shown, far, near), visible(shown), 2);

        assertEquals(List.of(near), plan.toAdd);
    }

    @Test
    public void removedMarkersFreeCapacityForNewOnes() {
        StandaloneMarker<String> leaving = marker("leaving", 20);
        StandaloneMarker<String> entering = marker("entering", 1);

        StandaloneCuller.Plan<String> plan = StandaloneCuller.plan(CENTER, RADIUS, items(leaving, entering), visible(leaving), 1);

        assertEquals(List.of("leaving"), plan.toRemove);
        assertEquals(List.of(entering), plan.toAdd);
    }

    @Test
    public void addsNothing_whenMaxVisibleIsZero() {
        StandaloneCuller.Plan<String> plan = StandaloneCuller.plan(CENTER, RADIUS, items(marker("a", 1)), new HashMap<>(), 0);

        assertTrue(plan.toAdd.isEmpty());
    }

    //---------

    @Test
    public void radiusOf_isDistanceFromCenterToCorner() {
        LatLng nearLeft = new LatLng(24.95, 121.45);
        LatLng nearRight = new LatLng(24.95, 121.55);
        LatLng farLeft = new LatLng(25.05, 121.45);
        LatLng farRight = new LatLng(25.05, 121.55);
        VisibleRegion region = new VisibleRegion(nearLeft, nearRight, farLeft, farRight, new LatLngBounds(nearLeft, farRight));

        double expected = SphericalUtil.computeDistanceBetween(CENTER, farRight);

        assertEquals(expected, StandaloneCuller.radiusOf(region), expected * 0.01);
    }

    //---------

    /***
     * 位於鏡頭中心正北方 distanceKm 公里的項目
     */
    private static StandaloneMarker<String> marker(String id, double distanceKm) {
        return new StandaloneMarker<>(id, CENTER.latitude + distanceKm * LAT_PER_KM, CENTER.longitude, id);
    }

    @SafeVarargs
    private static Map<String, StandaloneMarker<String>> items(StandaloneMarker<String>... markers) {
        Map<String, StandaloneMarker<String>> map = new HashMap<>();
        for (StandaloneMarker<String> marker : markers) {
            map.put(marker.getId(), marker);
        }
        return map;
    }

    @SafeVarargs
    private static Map<String, StandaloneMarker<String>> visible(StandaloneMarker<String>... markers) {
        return items(markers);
    }
}
