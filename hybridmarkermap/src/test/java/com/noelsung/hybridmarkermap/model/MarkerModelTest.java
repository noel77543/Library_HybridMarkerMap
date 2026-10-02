package com.noelsung.hybridmarkermap.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.google.android.gms.maps.model.LatLng;

import org.junit.Test;

public class MarkerModelTest {

    private static final class Store {
    }

    @Test
    public void clusterableMarker_keepsIdPositionAndPayload() {
        Store store = new Store();
        ClusterableMarker<Store> marker = new ClusterableMarker<>("s1", 25.03, 121.56, store);

        assertEquals("s1", marker.getId());
        assertEquals(new LatLng(25.03, 121.56), marker.getPosition());
        assertSame(store, marker.getPayload());
    }

    @Test
    public void clusterableMarker_latLngConstructorUsesSamePosition() {
        LatLng position = new LatLng(22.6, 120.3);

        assertSame(position, new ClusterableMarker<>("s1", position, null).getPosition());
    }

    /***
     * 不提供 title / snippet / zIndex，點擊時不顯示預設的 InfoWindow
     */
    @Test
    public void clusterableMarker_hasNoInfoWindowContent() {
        ClusterableMarker<String> marker = new ClusterableMarker<>("s1", 0, 0, "payload");

        assertNull(marker.getTitle());
        assertNull(marker.getSnippet());
        assertNull(marker.getZIndex());
    }

    @Test
    public void standaloneMarker_keepsIdPositionAndPayload() {
        Store store = new Store();
        StandaloneMarker<Store> marker = new StandaloneMarker<>("e1", 24.15, 120.67, store);

        assertEquals("e1", marker.getId());
        assertEquals(new LatLng(24.15, 120.67), marker.getPosition());
        assertSame(store, marker.getPayload());
    }
}
