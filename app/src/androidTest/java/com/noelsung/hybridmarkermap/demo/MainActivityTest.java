package com.noelsung.hybridmarkermap.demo;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.fail;

import android.Manifest;
import android.content.Context;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import com.google.android.material.chip.ChipGroup;
import com.noelsung.hybridmarkermap.MapStyle;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/***
 * demo app 的實機煙霧測試：地圖載入、資料群組化、地圖樣式切換
 * 需連接裝置並於 local.properties 設定 MAPS_API_KEY
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MainActivityTest {

    //地圖載入需要網路，給予較寬鬆的等待時間
    private static final long TIMEOUT_MS = 20_000;

    //預先授予定位權限，避免系統權限對話框擋住畫面
    @Rule(order = 0)
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);

    @Rule(order = 1)
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    private final Context context = ApplicationProvider.getApplicationContext();

    //---------

    @Test
    public void mapReady_createsChipForEveryMapStyle() {
        waitForMapReady();
    }

    @Test
    public void clustering_reportsUnclusteredItems() {
        waitForMapReady();

        //狀態列顯示「展開中的可群組化項目：N」代表資料已載入並完成群組計算
        String prefix = context.getString(R.string.status_unclustered).split("%")[0];
        waitUntil("群組計算未完成", activity -> {
            TextView textStatus = activity.findViewById(R.id.text_status);
            return textStatus.getText().toString().startsWith(prefix);
        });
    }

    @Test
    public void everyMapStyleChip_canBeSelected() {
        waitForMapReady();

        //初始樣式為灰階
        onView(withText(R.string.map_style_grayscale)).check(matches(isChecked()));

        int[] names = {
                R.string.map_style_default,
                R.string.map_style_night,
                R.string.map_style_retro,
                R.string.map_style_minimal,
                R.string.map_style_grayscale,
        };
        for (int name : names) {
            onView(withText(name)).perform(scrollTo(), click()).check(matches(isChecked()));
        }
        //單選：選回灰階後其他樣式皆取消選取
        onView(withText(R.string.map_style_night)).check(matches(isNotChecked()));
    }

    //---------

    /***
     * 地圖樣式按鈕於 onMapReady 後才建立，以此判斷地圖已就緒
     */
    private void waitForMapReady() {
        waitUntil("地圖未在時限內就緒", activity -> {
            ChipGroup chipGroup = activity.findViewById(R.id.chip_group_map_style);
            return chipGroup.getChildCount() == MapStyle.values().length;
        });
    }

    private void waitUntil(String message, Predicate<MainActivity> condition) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        AtomicBoolean satisfied = new AtomicBoolean(false);
        while (SystemClock.uptimeMillis() < deadline) {
            activityRule.getScenario().onActivity(activity -> satisfied.set(condition.test(activity)));
            if (satisfied.get()) {
                return;
            }
            SystemClock.sleep(200);
        }
        fail(message);
    }
}
