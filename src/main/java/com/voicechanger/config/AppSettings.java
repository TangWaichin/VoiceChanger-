package com.voicechanger.config;

import com.voicechanger.audio.DeviceInfo;
import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorCatalog;
import com.voicechanger.dsp.ProcessorState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 软件的全部可保存设置：调音选项、设备选择、耳返、日志地址。
 */
public final class AppSettings {

    private final ProcessorCatalog catalog;
    private final List<ProcessorState> states = new ArrayList<>();

    private String logDirectory = "";
    private String sourceDeviceName = "";
    private String outputDeviceName = "";
    private String monitorDeviceName = "";
    private boolean monitorEnabled;
    private float monitorVolume = 0.8f;
    private String language = "zh";

    public AppSettings(ProcessorCatalog catalog) {
        this.catalog = catalog;
        for (ProcessorCatalog.Entry entry : catalog.entries()) {
            states.add(new ProcessorState(entry.id(), entry.params()));
        }
    }

    public ProcessorCatalog catalog() {
        return catalog;
    }

    public List<ProcessorState> states() {
        return states;
    }

    public ProcessorState state(String processorId) {
        for (ProcessorState state : states) {
            if (state.processorId().equals(processorId)) {
                return state;
            }
        }
        return null;
    }

    public Map<String, Float> snapshotValues() {
        Map<String, Float> snapshot = new LinkedHashMap<>();
        for (ProcessorState state : states) {
            for (Map.Entry<String, Float> entry : state.values().entrySet()) {
                snapshot.put(state.processorId() + "." + entry.getKey(), entry.getValue());
            }
        }
        return snapshot;
    }

    public void applyValue(String processorId, ParamSpec spec, float value) {
        ProcessorState state = state(processorId);
        if (state != null) {
            state.setValue(spec, value);
        }
    }

    /** 把存档中的值套用到当前模块上，未知模块/参数会被忽略。 */
    public void restoreValue(String processorId, String paramId, float value) {
        ProcessorState state = state(processorId);
        if (state == null) {
            return;
        }
        for (ParamSpec spec : specsOf(processorId)) {
            if (spec.id().equals(paramId)) {
                state.setValue(spec, value);
                return;
            }
        }
    }

    public void restoreEnabled(String processorId, boolean enabled) {
        ProcessorState state = state(processorId);
        if (state != null) {
            state.setEnabled(enabled);
        }
    }

    public List<ParamSpec> specsOf(String processorId) {
        ProcessorCatalog.Entry entry = catalog.find(processorId);
        return entry == null ? List.of() : entry.params();
    }

    public String logDirectory() {
        return logDirectory;
    }

    public void setLogDirectory(String logDirectory) {
        this.logDirectory = logDirectory == null ? "" : logDirectory.trim();
    }

    public String sourceDeviceName() {
        return sourceDeviceName;
    }

    public void setSourceDeviceName(String name) {
        this.sourceDeviceName = name == null ? "" : name;
    }

    public String outputDeviceName() {
        return outputDeviceName;
    }

    public void setOutputDeviceName(String name) {
        this.outputDeviceName = name == null ? "" : name;
    }

    public String monitorDeviceName() {
        return monitorDeviceName;
    }

    public void setMonitorDeviceName(String name) {
        this.monitorDeviceName = name == null ? "" : name;
    }

    public boolean monitorEnabled() {
        return monitorEnabled;
    }

    public void setMonitorEnabled(boolean monitorEnabled) {
        this.monitorEnabled = monitorEnabled;
    }

    public float monitorVolume() {
        return monitorVolume;
    }

    public void setMonitorVolume(float monitorVolume) {
        this.monitorVolume = Math.max(0f, Math.min(1f, monitorVolume));
    }

    /** 界面语言：zh（默认）/ en。 */
    public String language() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = "en".equalsIgnoreCase(language == null ? "" : language.trim()) ? "en" : "zh";
    }

    public static DeviceInfo resolve(List<DeviceInfo> devices, String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return null;
        }
        for (DeviceInfo info : devices) {
            if (info.mixerName().equals(storedName) || info.name().equals(storedName)) {
                return info;
            }
        }
        return null;
    }

    public static DeviceInfo pickDefault(List<DeviceInfo> devices, String preferredKeyword) {
        if (devices.isEmpty()) {
            return null;
        }
        if (preferredKeyword != null) {
            for (DeviceInfo info : devices) {
                if ((info.name() + " " + String.valueOf(info.detail())).toLowerCase()
                        .contains(preferredKeyword)) {
                    return info;
                }
            }
        }
        return devices.get(0);
    }
}
