package com.zcshou.gogogo;

import android.os.Bundle;
import android.text.InputType;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.zcshou.utils.GoUtils;
import com.zcshou.utils.InputUtils;
import com.zcshou.utils.AppSettings;

import java.util.Objects;

public class FragmentSettings extends PreferenceFragmentCompat {

    // Set a non-empty decimal EditTextPreference
    private void setupDecimalEditTextPreference(EditTextPreference preference) {
        if (preference != null) {
            preference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) EditTextPreference::getText);
            preference.setOnBindEditTextListener(editText -> {
                int inputType = InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_CLASS_NUMBER;
                if ("setting_altitude".equals(preference.getKey())) {
                    inputType |= InputType.TYPE_NUMBER_FLAG_SIGNED;
                }
                editText.setInputType(inputType);
                editText.setSelection(editText.length());
            });
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                try {
                    double value = InputUtils.parseFiniteDouble(newValue.toString());
                    if ((!"setting_altitude".equals(pref.getKey()) && value < 0)
                            || (AppSettings.HISTORY_EXPIRATION.equals(pref.getKey())
                            && value > Long.MAX_VALUE / 86400.0)) {
                        throw new NumberFormatException("Setting out of range");
                    }
                } catch (NumberFormatException e) {
                    GoUtils.DisplayToast(this.getContext(), getString(R.string.app_error_input));
                    return false;
                }
                return true;
            });
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences_main);

        ListPreference pfJoystick = findPreference("setting_joystick_type");
        if (pfJoystick != null) {
            // 使用自定义 SummaryProvider
            pfJoystick.setSummaryProvider((Preference.SummaryProvider<ListPreference>) preference -> Objects.requireNonNull(preference.getEntry()));
            pfJoystick.setOnPreferenceChangeListener((preference, newValue) -> !newValue.toString().trim().isEmpty());
        }

        EditTextPreference pfWalk = findPreference("setting_walk");
        setupDecimalEditTextPreference(pfWalk);

        EditTextPreference pfRun = findPreference("setting_run");
        setupDecimalEditTextPreference(pfRun);

        EditTextPreference pfBike = findPreference("setting_bike");
        setupDecimalEditTextPreference(pfBike);

        EditTextPreference pfAltitude = findPreference("setting_altitude");
        setupDecimalEditTextPreference(pfAltitude);

        EditTextPreference pfLatOffset = findPreference("setting_lat_max_offset");
        setupDecimalEditTextPreference(pfLatOffset);

        EditTextPreference pfLonOffset = findPreference("setting_lon_max_offset");
        setupDecimalEditTextPreference(pfLonOffset);

        EditTextPreference pfPosHisValid = findPreference(AppSettings.HISTORY_EXPIRATION);
        setupDecimalEditTextPreference(pfPosHisValid);

        // 设置版本号
        String verName;
        verName = GoUtils.getVersionName(FragmentSettings.this.getContext());
        Preference pfVersion = findPreference("setting_version");
        if (pfVersion != null) {
            pfVersion.setSummary(verName);
        }
    }
}
