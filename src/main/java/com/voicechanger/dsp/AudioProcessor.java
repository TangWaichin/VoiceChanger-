package com.voicechanger.dsp;

import java.util.List;
import java.util.Locale;

/**
 * 一个调音模块（扩展点）。实现类只需处理单声道 float 帧，可原地修改。
 */
public interface AudioProcessor {

    /** 全局唯一标识，用于存档与界面绑定。 */
    String id();

    /** 界面显示名称。 */
    String displayName();

    /** 一句话说明。 */
    String description();

    /** 该模块暴露给界面/存档的参数。 */
    List<ParamSpec> params();

    /** 引擎启动或格式变化时调用。 */
    void prepare(int sampleRate, int maxFrames);

    /** 原地处理，length 之前的数据有效。 */
    void process(float[] buffer, int length);

    /** 参数变化或重启时清空内部状态。 */
    default void reset() {
    }

    /**
     * 默认参数表，方便实现类复用。
     */
    static String format(ParamSpec spec, float value) {
        String text = String.format(Locale.ROOT, "%." + spec.decimals() + "f", value);
        return spec.unit() == null || spec.unit().isEmpty() ? text : text + spec.unit();
    }
}
