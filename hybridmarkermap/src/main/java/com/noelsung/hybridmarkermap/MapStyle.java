package com.noelsung.hybridmarkermap;

import androidx.annotation.RawRes;

/***
 * HybridMarkerMap 提供的地圖樣式
 */
public enum MapStyle {

    //Google Map 原始樣式
    DEFAULT(0),
    //淺灰底、低彩度，突顯標記
    GRAYSCALE(R.raw.hmm_map_style_grayscale),
    //深色夜間模式
    NIGHT(R.raw.hmm_map_style_night),
    //米黃色調的復古風格
    RETRO(R.raw.hmm_map_style_retro),
    //隱藏興趣點、大眾運輸與次要標籤的精簡地圖
    MINIMAL(R.raw.hmm_map_style_minimal);

    @RawRes
    final int styleRes;

    MapStyle(@RawRes int styleRes) {
        this.styleRes = styleRes;
    }
}
