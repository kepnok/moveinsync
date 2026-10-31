package com.moveinsync.config.core.exception;

public final class UnknownSettingException extends RuntimeException {
    private final String settingKey;

    public UnknownSettingException(String settingKey) {
        super("Unknown setting: " + settingKey);
        this.settingKey = settingKey;
    }

    public String settingKey() {
        return settingKey;
    }
}

