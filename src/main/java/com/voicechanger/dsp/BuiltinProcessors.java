package com.voicechanger.dsp;

import com.voicechanger.logging.LogService;

/**
 * 内置调音模块注册（顺序即信号处理顺序）。
 */
final class BuiltinProcessors {

    private BuiltinProcessors() {
    }

    static void register(ProcessorCatalog catalog) {
        catalog.register("gate", "噪声门", "低于阈值的底噪被压下去，说话时才放行，可减少房间噪音。",
                com.voicechanger.dsp.processors.NoiseGateProcessor.PARAMS,
                com.voicechanger.dsp.processors.NoiseGateProcessor::new);
        catalog.register("pitch", "变调", "整体升降音高，颗粒越长越平滑，越短越灵敏。",
                com.voicechanger.dsp.processors.PitchShiftProcessor.PARAMS,
                com.voicechanger.dsp.processors.PitchShiftProcessor::new);
        catalog.register("formant", "共振峰", "移动人声共振峰：向上更纤细，向下更浑厚，音高保持不变。",
                com.voicechanger.dsp.processors.FormantShiftProcessor.PARAMS,
                com.voicechanger.dsp.processors.FormantShiftProcessor::new);
        catalog.register("robot", "机器人", "环形调制制造机械感，方波比例越高越硬朗。",
                com.voicechanger.dsp.processors.RobotProcessor.PARAMS,
                com.voicechanger.dsp.processors.RobotProcessor::new);
        catalog.register("distortion", "失真", "过载削波带来沙哑、嘶吼的质感，驱动越大越脏。",
                com.voicechanger.dsp.processors.DistortionProcessor.PARAMS,
                com.voicechanger.dsp.processors.DistortionProcessor::new);
        catalog.register("eq", "均衡", "调整低、中、高频比例：低频厚度、中频清晰度、高频空气感。",
                com.voicechanger.dsp.processors.EqualizerProcessor.PARAMS,
                com.voicechanger.dsp.processors.EqualizerProcessor::new);
        catalog.register("compressor", "压缩", "压低过大的音量峰值并可用补偿增益抬回整体响度，声音更贴耳。",
                com.voicechanger.dsp.processors.CompressorProcessor.PARAMS,
                com.voicechanger.dsp.processors.CompressorProcessor::new);
        catalog.register("chorus", "合唱", "叠加轻微失谐的副本，声音更宽、更有厚度。",
                com.voicechanger.dsp.processors.ChorusProcessor.PARAMS,
                com.voicechanger.dsp.processors.ChorusProcessor::new);
        catalog.register("vibrato", "颤音", "让音高周期性轻微摆动，唱歌与播音都更生动。",
                com.voicechanger.dsp.processors.VibratoProcessor.PARAMS,
                com.voicechanger.dsp.processors.VibratoProcessor::new);
        catalog.register("echo", "回声", "可调延迟与反馈的回声，适合营造空间感或特效。",
                com.voicechanger.dsp.processors.EchoProcessor.PARAMS,
                com.voicechanger.dsp.processors.EchoProcessor::new);
        catalog.register("reverb", "混响", "模拟房间反射，房间越大尾音越长，阻尼越大高频衰减越快。",
                com.voicechanger.dsp.processors.ReverbProcessor.PARAMS,
                com.voicechanger.dsp.processors.ReverbProcessor::new);
        catalog.register("output", "输出", "整体电平放大，前视限幅器提前压住超过阈值的峰值，高增益下也不破音。",
                com.voicechanger.dsp.processors.OutputGainProcessor.PARAMS,
                com.voicechanger.dsp.processors.OutputGainProcessor::new);
        LogService.info("已注册调音模块: " + catalog.entries().size() + " 个");
    }
}
