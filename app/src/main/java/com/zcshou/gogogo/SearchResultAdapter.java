package com.zcshou.gogogo;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SimpleAdapter;

import java.util.List;
import java.util.Map;

/**
 * 地点搜索结果适配器，为每个结果提供独立的收藏入口。
 */
public class SearchResultAdapter extends SimpleAdapter {
    public interface OnSaveClickListener {
        void onSave(String name, String address, String longitude, String latitude);
    }

    private final List<? extends Map<String, ?>> data;
    private final OnSaveClickListener onSaveClickListener;

    public SearchResultAdapter(Context context,
                               List<? extends Map<String, ?>> data,
                               String[] from,
                               int[] to,
                               OnSaveClickListener onSaveClickListener) {
        super(context, data, R.layout.search_poi_item, from, to);
        this.data = data;
        this.onSaveClickListener = onSaveClickListener;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = super.getView(position, convertView, parent);
        ImageButton saveButton = row.findViewById(R.id.poi_save);
        Map<String, ?> item = data.get(position);

        saveButton.setOnClickListener(view -> onSaveClickListener.onSave(
                getString(item, MainActivity.POI_NAME),
                getString(item, MainActivity.POI_ADDRESS),
                getString(item, MainActivity.POI_LONGITUDE),
                getString(item, MainActivity.POI_LATITUDE)));

        return row;
    }

    private static String getString(Map<String, ?> item, String key) {
        Object value = item.get(key);
        return value == null ? "" : value.toString();
    }
}
