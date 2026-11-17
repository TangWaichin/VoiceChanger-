package com.voicechanger.audio;

import java.util.Objects;

/**
 * 一个可选的音频设备。id 为混音器序号，name 用于界面显示。
 */
public record DeviceInfo(int id, String name, String detail, String mixerName) {

    public DeviceInfo {
        Objects.requireNonNull(name, "name");
    }

    public boolean matches(DeviceInfo other) {
        return other != null && (mixerName.equals(other.mixerName) || name.equals(other.name));
    }

    @Override
    public String toString() {
        return name;
    }

    public String label() {
        return detail == null || detail.isBlank() || detail.equals(name)
                ? name : name + "  ·  " + detail;
    }
}
