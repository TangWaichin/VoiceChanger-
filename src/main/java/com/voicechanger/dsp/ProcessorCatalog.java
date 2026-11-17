package com.voicechanger.dsp;

import com.voicechanger.logging.LogService;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 调音模块注册表：内置模块按固定顺序注册，同时支持 ext/ 目录中的 jar 扩展。
 */
public final class ProcessorCatalog {

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Entry> byId = new LinkedHashMap<>();

    public record Entry(String id, String name, String description, List<ParamSpec> params,
                        Function<ProcessorState, AudioProcessor> factory) {
    }

    public static ProcessorCatalog load(Path appDir) {
        ProcessorCatalog catalog = new ProcessorCatalog();
        BuiltinProcessors.register(catalog);
        catalog.loadPlugins(appDir);
        return catalog;
    }

    private void loadPlugins(Path appDir) {
        List<ProcessorPlugin> plugins = new ArrayList<>();
        try {
            for (ProcessorPlugin plugin : ServiceLoader.load(ProcessorPlugin.class)) {
                plugins.add(plugin);
            }
        } catch (Throwable t) {
            LogService.warn("内置扩展加载失败: " + t.getMessage());
        }
        Path extDir = appDir.resolve("ext");
        if (Files.isDirectory(extDir)) {
            List<URL> jars = new ArrayList<>();
            try (Stream<Path> stream = Files.list(extDir)) {
                stream.filter(p -> p.toString().toLowerCase().endsWith(".jar")).forEach(p -> {
                    try {
                        jars.add(p.toUri().toURL());
                    } catch (Exception e) {
                        LogService.warn("忽略无法读取的扩展: " + p);
                    }
                });
            } catch (IOException e) {
                LogService.warn("读取 ext 目录失败: " + e.getMessage());
            }
            if (!jars.isEmpty()) {
                URLClassLoader loader = new URLClassLoader(jars.toArray(new URL[0]),
                        ProcessorCatalog.class.getClassLoader());
                try {
                    for (ProcessorPlugin plugin : ServiceLoader.load(ProcessorPlugin.class, loader)) {
                        plugins.add(plugin);
                    }
                } catch (Throwable t) {
                    LogService.warn("扩展 jar 加载失败: " + t.getMessage());
                }
            }
        }
        for (ProcessorPlugin plugin : plugins) {
            if (byId.containsKey(plugin.id())) {
                LogService.warn("扩展模块 id 冲突被忽略: " + plugin.id());
                continue;
            }
            LogService.info("加载扩展调音模块: " + plugin.id() + " (" + plugin.displayName() + ")");
            register(plugin.id(), plugin.displayName(), plugin.description(), plugin.params(), plugin::create);
        }
    }

    public void register(String id, String name, String description, List<ParamSpec> params,
                         Function<ProcessorState, AudioProcessor> factory) {
        Entry entry = new Entry(id, name, description, List.copyOf(params), factory);
        entries.add(entry);
        byId.put(id, entry);
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public Entry find(String id) {
        return byId.get(id);
    }

    public ProcessorState newState(String id) {
        Entry entry = byId.get(id);
        if (entry == null) {
            return null;
        }
        return new ProcessorState(id, entry.params());
    }

    public AudioProcessor create(ProcessorState state) {
        Entry entry = byId.get(state.processorId());
        if (entry == null) {
            return null;
        }
        return entry.factory().apply(state);
    }
}
