package com.voicechanger.dsp;

import java.util.ArrayList;
import java.util.List;

/**
 * 调音链路：按顺序执行开启的模块，关闭的模块被直接跳过。
 */
public final class TransformChain {

    private final List<ProcessorState> states = new ArrayList<>();
    private final List<AudioProcessor> processors = new ArrayList<>();
    private int sampleRate = 48000;

    public TransformChain(ProcessorCatalog catalog, List<ProcessorState> states) {
        this.states.addAll(states);
        for (ProcessorState state : states) {
            processors.add(catalog.create(state)); // 允许 null 占位，保证与 states 等长对齐
        }
    }

    public List<ProcessorState> states() {
        return states;
    }

    public void prepare(int sampleRate, int maxFrames) {
        this.sampleRate = sampleRate;
        for (AudioProcessor processor : processors) {
            if (processor != null) {
                processor.prepare(sampleRate, maxFrames);
            }
        }
    }

    /** 参数变化后调用，避免残留尾音。 */
    public void reset() {
        for (AudioProcessor processor : processors) {
            if (processor != null) {
                processor.reset();
            }
        }
    }

    /** 只重置指定模块，主链路其他模块不中断。 */
    public void reset(String id) {
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).processorId().equals(id)) {
                AudioProcessor processor = processors.get(i);
                if (processor != null) {
                    processor.reset();
                }
            }
        }
    }

    public void process(float[] buffer, int length) {
        for (int i = 0; i < processors.size(); i++) {
            ProcessorState state = states.get(i);
            if (!state.enabled()) {
                continue;
            }
            AudioProcessor processor = processors.get(i);
            if (processor != null) {
                processor.process(buffer, length);
            }
        }
    }

    public int sampleRate() {
        return sampleRate;
    }
}
