package com.voicechanger.dsp;

import java.util.List;

/**
 * 第三方调音模块扩展接口。
 * 打包为 jar 放入软件目录的 ext/ 文件夹，并提供
 * META-INF/services/com.voicechanger.dsp.ProcessorPlugin 声明实现类即可被自动加载。
 */
public interface ProcessorPlugin {

    String id();

    String displayName();

    String description();

    List<ParamSpec> params();

    AudioProcessor create(ProcessorState state);
}
