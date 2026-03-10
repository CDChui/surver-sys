package com.surver.sys.houduan.module.settings.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Profile("nodeps")
public class LocalSettingsService implements SettingsServiceApi {

    private static final Path SETTINGS_STORE_FILE =
            Path.of(".nodeps-data", "settings.json").toAbsolutePath().normalize();
    private static final Path LEGACY_SETTINGS_STORE_FILE =
            Path.of("..", ".nodeps-data", "settings.json").toAbsolutePath().normalize();

    private final ObjectMapper objectMapper;
    private final AtomicReference<Map<String, Object>> settingsRef = new AtomicReference<>(defaultSettings());

    public LocalSettingsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        loadSettings();
    }

    @Override
    public Map<String, Object> getSettings() {
        return deepCopy(settingsRef.get());
    }

    @Override
    public void saveSettings(Map<String, Object> settings) {
        Map<String, Object> current = deepCopy(settingsRef.get());
        mergeMap(current, settings);
        settingsRef.set(mergeWithDefaults(current));
        saveSettingsToFile();
    }

    private void loadSettings() {
        Path readPath = resolveSettingsStoreReadPath();
        if (!Files.exists(readPath)) {
            return;
        }

        try {
            Map<String, Object> loaded = objectMapper.readValue(
                    readPath.toFile(),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            settingsRef.set(mergeWithDefaults(loaded));

            if (!Objects.equals(readPath, SETTINGS_STORE_FILE)) {
                saveSettingsToFile();
            }
        } catch (Exception ignored) {
            settingsRef.set(defaultSettings());
        }
    }

    private synchronized void saveSettingsToFile() {
        try {
            Files.createDirectories(SETTINGS_STORE_FILE.getParent());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(SETTINGS_STORE_FILE.toFile(), settingsRef.get());
        } catch (Exception ignored) {
            // nodeps mode: persistence failure should not block request flow
        }
    }

    private static Path resolveSettingsStoreReadPath() {
        if (Files.exists(SETTINGS_STORE_FILE)) {
            return SETTINGS_STORE_FILE;
        }
        if (Files.exists(LEGACY_SETTINGS_STORE_FILE)) {
            return LEGACY_SETTINGS_STORE_FILE;
        }
        return SETTINGS_STORE_FILE;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeWithDefaults(Map<String, Object> loaded) {
        Map<String, Object> merged = defaultSettings();
        if (loaded == null) {
            return merged;
        }
        mergeMap(merged, loaded);
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> defaultSettings() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("systemName", "问卷调查系统");
        root.put("systemDomain", "");
        root.put("defaultPageSize", 20);
        root.put("enableLog", true);
        root.put("enableResumeDraft", true);
        root.put("allowDuplicateSubmit", false);
        root.put("adminLogo", "");
        root.put("userHomeLogo", "");

        Map<String, Object> oauth = new LinkedHashMap<>();
        oauth.put("enabled", false);
        oauth.put("environment", "CUSTOM");
        oauth.put("authDomain", "");
        oauth.put("clientId", "");
        oauth.put("clientSecret", "");
        oauth.put("redirectUri", "");
        oauth.put("logoutRedirectUri", "");
        root.put("oauth", oauth);

        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("id", "iam-default");
        provider.put("name", "校园统一身份认证(IAM)");
        provider.put("protocol", "IAM_TEMPLATE");
        provider.put("enabled", false);
        provider.put("priority", 1);
        provider.put("environment", "CUSTOM");
        provider.put("authDomain", "");
        provider.put("clientId", "");
        provider.put("clientSecret", "");
        provider.put("scope", "openid profile");
        provider.put("redirectUri", "");
        provider.put("logoutRedirectUri", "");
        provider.put("authorizeUrl", "");
        provider.put("tokenUrl", "");
        provider.put("userInfoUrl", "");
        provider.put("refreshUrl", "");
        provider.put("revokeUrl", "");
        provider.put("userIdField", "employeeNumber");
        provider.put("realNameField", "displayName");
        provider.put("emailField", "mail");

        Map<String, Object> authIntegration = new LinkedHashMap<>();
        authIntegration.put("loginMode", "LOCAL_ONLY");
        authIntegration.put("defaultProviderId", "iam-default");
        authIntegration.put("autoCreateUser", true);
        authIntegration.put("defaultRole", "ROLE1");
        List<Map<String, Object>> providers = new ArrayList<>();
        providers.add(provider);
        authIntegration.put("providers", providers);
        root.put("authIntegration", authIntegration);

        return root;
    }

    @SuppressWarnings("unchecked")
    private static void mergeMap(Map<String, Object> target, Map<String, Object> incoming) {
        if (incoming == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            Object incomingValue = entry.getValue();
            Object existingValue = target.get(entry.getKey());

            if (incomingValue instanceof Map<?, ?> incomingMap &&
                    existingValue instanceof Map<?, ?> existingMap) {
                Map<String, Object> nestedTarget = deepCopy((Map<String, Object>) existingMap);
                mergeMap(nestedTarget, (Map<String, Object>) incomingMap);
                target.put(entry.getKey(), nestedTarget);
                continue;
            }

            if (incomingValue instanceof Map<?, ?> incomingMapOnly) {
                target.put(entry.getKey(), deepCopy((Map<String, Object>) incomingMapOnly));
                continue;
            }

            if (incomingValue instanceof List<?> incomingList) {
                target.put(entry.getKey(), deepCopyList(incomingList));
                continue;
            }

            target.put(entry.getKey(), incomingValue);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> mapValue) {
                result.put(entry.getKey(), deepCopy((Map<String, Object>) mapValue));
            } else if (value instanceof List<?> listValue) {
                result.put(entry.getKey(), deepCopyList(listValue));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> deepCopyList(List<?> source) {
        List<Object> result = new ArrayList<>();
        for (Object item : source) {
            if (item instanceof Map<?, ?> mapItem) {
                result.add(deepCopy((Map<String, Object>) mapItem));
            } else if (item instanceof List<?> listItem) {
                result.add(deepCopyList(listItem));
            } else {
                result.add(item);
            }
        }
        return result;
    }
}
