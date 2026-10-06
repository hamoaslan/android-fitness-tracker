package de.pbma.moa.fitnessapp;

import android.content.res.Resources;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class FoodSettingFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.foodpreferences,rootKey);
        restrictValueToNumeric();
    }
    private EditTextPreference.OnBindEditTextListener onBindEditTextListener=new EditTextPreference.OnBindEditTextListener() {
        @Override
        public void onBindEditText(@NonNull EditText editText) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
    };
    private void restrictValueToNumeric() {
        PreferenceManager pm = getPreferenceManager();
        Resources res = getResources();
        String keyValue1 = res.getString(R.string.key_calories);
        String keyValue2 = res.getString(R.string.key_protein);
        String keyValue3 = res.getString(R.string.key_carbohydrates);
        String keyValue4 = res.getString(R.string.key_fat);
        String keyValue5 = res.getString(R.string.key_height);
        String keyValue6 = res.getString(R.string.key_weight);
        String keyValue7 = res.getString(R.string.key_goal_weight);
        String keyValue8 = res.getString(R.string.key_age);
        EditTextPreference etValue1=pm.findPreference(keyValue1);
        EditTextPreference etValue2=pm.findPreference(keyValue2);
        EditTextPreference etValue3=pm.findPreference(keyValue3);
        EditTextPreference etValue4=pm.findPreference(keyValue4);
        EditTextPreference etValue5=pm.findPreference(keyValue5);
        EditTextPreference etValue6=pm.findPreference(keyValue6);
        EditTextPreference etValue7=pm.findPreference(keyValue7);
        EditTextPreference etValue8=pm.findPreference(keyValue8);
        assert etValue1 != null;
        etValue1.setOnBindEditTextListener(onBindEditTextListener);
        assert etValue2 != null;
        etValue2.setOnBindEditTextListener(onBindEditTextListener);
        assert etValue3 != null;
        etValue3.setOnBindEditTextListener(onBindEditTextListener);
        assert etValue4 != null;
        etValue4.setOnBindEditTextListener(onBindEditTextListener);
        assert etValue5 != null;
        etValue5.setOnBindEditTextListener(onBindEditTextListener);
        assert etValue6 != null;
        etValue6.setOnBindEditTextListener(onBindEditTextListener);
        assert etValue7 != null;
        etValue7.setOnBindEditTextListener(onBindEditTextListener);
        assert etValue8 != null;
        etValue8.setOnBindEditTextListener(onBindEditTextListener);
    }
}
