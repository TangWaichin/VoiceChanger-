package com.voicechanger.dsp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一个调音模块的运行状态：是否开启 + 各参数当前值。
 */
public final class ProcessorState {

    private final String processorId;
    private volatile boolean enabled;
    private final Map<String, Float> values = new ConcurrentHashMap<>();

    public ProcessorState(String processorId, List<ParamSpec> specs) {
        this.processorId = processorId;
        Map<String, Float> init = new LinkedHashMap<>();
        for (ParamSpec spec : specs) {
            init.put(spec.id(), spec.defaultValue());
        }
        values.putAll(init);
    }

    public String processorId() {
        return processorId;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Float> values() {
        return Collections.unmodifiableMap(values);
    }

    public float value(String paramId, float fallback) {
        Float v = values.get(paramId);
        return v == null ? fallback : v;
    }

    public void setValue(ParamSpec spec, float value) {
        values.put(spec.id(), spec.clamp(value));
    }

    public void resetToDefaults(List<ParamSpec> specs) {
        values.clear();
        for (ParamSpec spec : specs) {
            values.put(spec.id(), spec.defaultValue());
        }
    }

    public ProcessorState copy() {
        ProcessorState copy = new ProcessorState(processorId, List.of());
        copy.enabled = enabled;
        copy.values.putAll(values);
        return copy;
    }
}
