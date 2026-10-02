package com.noelsung.hybridmarkermap.demo.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/***
 * 範例資料 (assets/sample_clusterable.json、assets/sample_standalone.json)
 */
public class SamplePointResponse {

    @SerializedName("items")
    private List<Point> items;

    public List<Point> getItems() {
        return items;
    }

    public static class Point {
        @SerializedName("id")
        private String id;
        @SerializedName("name")
        private String name;
        @SerializedName("lat")
        private double lat;
        @SerializedName("lng")
        private double lng;
        /**
         * 可群組化資料：1~4 代表四種狀態
         * 不可群組化資料：數值，大於 0 視為啟用
         */
        @SerializedName("status")
        private long status;

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public double getLat() {
            return lat;
        }

        public double getLng() {
            return lng;
        }

        public long getStatus() {
            return status;
        }
    }
}
