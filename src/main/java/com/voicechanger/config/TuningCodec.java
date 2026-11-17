package com.voicechanger.config;

import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;

import java.util.Locale;

/**
 * 调音编码：把全部调音模块的开关与参数按固定顺序编码为一串十进制数字。
 * 格式与界面语言无关：版本(2位) + 每模块[开关(1位) + 每参数步进索引(4位)] + 校验(2位)。
 * 参数以步进索引记录，避免浮点精度与本地化数字格式问题。
 */
public final class TuningCodec {

    private static final String VERSION = "01";
    private static final int CHECK_MOD = 97;

    private TuningCodec() {
    }

    /** 按当前调音数据生成编码，只包含字符 0-9。 */
    public static String encode(AppSettings settings) {
        StringBuilder body = new StringBuilder(VERSION);
        for (ProcessorState state : settings.states()) {
            body.append(state.enabled() ? '1' : '0');
            for (ParamSpec spec : settings.specsOf(state.processorId())) {
                int steps = spec.steps();
                if (steps > 9999) {
                    throw new IllegalStateException("参数步进超出编码范围: " + spec.id());
                }
                int index = Math.round(spec.normalize(state.value(spec.id(), spec.defaultValue())) * steps);
                index = Math.max(0, Math.min(steps, index));
                body.append(String.format(Locale.ROOT, "%04d", index));
            }
        }
        return body.toString() + checksum(body);
    }

    /**
     * 把编码中的调音数据写回各模块状态。
     * 格式、长度、版本或校验不合法时抛出 IllegalArgumentException，不修改任何状态。
     */
    public static void decode(AppSettings settings, String code) {
        String text = code == null ? "" : code.trim();
        if (text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("编码必须为纯十进制数字");
        }
        int expected = 2 + 2;
        for (ProcessorState state : settings.states()) {
            expected += 1 + 4 * settings.specsOf(state.processorId()).size();
        }
        if (text.length() != expected) {
            throw new IllegalArgumentException("编码长度不匹配: " + text.length() + "，期望 " + expected);
        }
        if (!text.startsWith(VERSION)) {
            throw new IllegalArgumentException("编码版本不支持: " + text.substring(0, 2));
        }
        String body = text.substring(0, text.length() - 2);
        if (!checksum(body).equals(text.substring(text.length() - 2))) {
            throw new IllegalArgumentException("编码校验失败");
        }
        int[] pos = {VERSION.length()};
        for (ProcessorState state : settings.states()) {
            state.setEnabled(text.charAt(pos[0]++) == '1');
            for (ParamSpec spec : settings.specsOf(state.processorId())) {
                int index = Integer.parseInt(text.substring(pos[0], pos[0] + 4));
                pos[0] += 4;
                index = Math.max(0, Math.min(spec.steps(), index));
                state.setValue(spec, spec.denormalize((float) index / spec.steps()));
            }
        }
    }

    /** 数字和模 97 的两位校验，可检出绝大多数手误与错位。 */
    private static String checksum(CharSequence body) {
        int sum = 0;
        for (int i = 0; i < body.length(); i++) {
            sum += body.charAt(i) - '0';
        }
        return String.format(Locale.ROOT, "%02d", sum % CHECK_MOD);
    }
}
