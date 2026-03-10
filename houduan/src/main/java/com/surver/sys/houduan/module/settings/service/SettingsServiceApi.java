package com.surver.sys.houduan.module.settings.service;

import java.util.Map;

public interface SettingsServiceApi {

    Map<String, Object> getSettings();

    void saveSettings(Map<String, Object> settings);
}
