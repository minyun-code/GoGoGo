package com.zcshou.utils;

import com.baidu.mapapi.search.sug.SuggestionSearchOption;

/**
 * 百度地图地点搜索参数工具。
 */
public final class MapSearchUtils {
    private static final String DEFAULT_SEARCH_REGION = "全国";

    private MapSearchUtils() {
    }

    /**
     * 创建不限制城市范围的地点建议搜索参数。
     *
     * 百度 SuggestionSearch 要求 city 必填。定位尚未完成或定位失败时，使用“全国”作为
     * 搜索区域；定位成功后以当前城市提高本地结果权重，但仍允许返回其他城市的结果。
     */
    public static SuggestionSearchOption createSuggestionOption(String keyword, String currentCity) {
        String searchKeyword = keyword == null ? "" : keyword.trim();
        String searchCity = currentCity == null ? "" : currentCity.trim();
        if (searchCity.isEmpty()) {
            searchCity = DEFAULT_SEARCH_REGION;
        }

        return new SuggestionSearchOption()
                .keyword(searchKeyword)
                .city(searchCity)
                .citylimit(false);
    }
}
