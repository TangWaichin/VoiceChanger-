package com.voicechanger.audio;

import com.voicechanger.logging.LogService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 自定义调音接口：一对本地回环 TCP 音频 API，用于在外部程序中绕过音频调节模块处理音频。
 * <ul>
 *   <li>实体麦克风 API（端口 {@link #MIC_API_PORT}）：把音频拦截模块采集到的麦克风数据
 *       以 48kHz / 单声道 / PCM16LE 流式发送给已连接的外部程序。</li>
 *   <li>虚拟麦克风 API（端口 {@link #VIRTUAL_MIC_API_PORT}）：接收外部程序写回的
 *       48kHz / 单声道 / PCM16LE 数据，交给音频发放模块送出。</li>
 * </ul>
 * 仅在 127.0.0.1 监听，单个客户端；连接断开后自动等待下一次连接。
 */
public final class CustomAudioBridge implements AutoCloseable {

    /** 实体麦克风 API 端口：外部程序从这里读取拦截到的麦克风音频。 */
    public static final int MIC_API_PORT = 47801;
    /** 虚拟麦克风 API 端口：外部程序把处理后的音频写入这里。 */
    public static final int VIRTUAL_MIC_API_PORT = 47802;
    /** 每块音频 480 帧（48kHz 下 10ms），PCM16LE 单声道即 960 字节。 */
    public static final int BLOCK_FRAMES = 480;
    public static final int BLOCK_BYTES = BLOCK_FRAMES * 2;

    /** 接收虚拟麦克风 API 音频数据的下游（音频发放模块 / 耳返）。 */
    public interface Sink {
        void onFrames(float[] mono, int length);
    }

    /** 队列中的音频块：数组可复用，length 表示有效帧数。 */
    private static final class Block {
        private final float[] data;
        private int length;

        Block(int capacity) {
            data = new float[capacity];
        }
    }

    private static final int QUEUE_BLOCKS = 8;

    private final BlockingQueue<Block> micQueue = new ArrayBlockingQueue<>(QUEUE_BLOCKS);
    /** 已发送块的复用池，避免每 10ms 分配一次 float[480]。 */
    private final BlockingQueue<Block> freeBlocks = new ArrayBlockingQueue<>(QUEUE_BLOCKS);
    private volatile boolean running;
    private volatile Sink sink;
    private volatile Socket micClient;
    private volatile Socket virtualClient;
    private ServerSocket micServer;
    private ServerSocket virtualServer;
    private Thread micAcceptThread;
    private Thread micWriterThread;
    private Thread virtualAcceptThread;

    /** 启动两个 API 服务；端口被占用时记录错误并返回 false。 */
    public synchronized boolean start(Sink sink) {
        if (running) {
            return true;
        }
        this.sink = sink;
        try {
            micServer = bind(MIC_API_PORT);
            virtualServer = bind(VIRTUAL_MIC_API_PORT);
        } catch (IOException e) {
            LogService.error("自定义调音接口启动失败（端口可能被占用）", e);
            closeServers();
            this.sink = null;
            return false;
        }
        running = true;
        micAcceptThread = daemon(this::acceptMicrophone, "custom-mic-api-accept");
        micWriterThread = daemon(this::writeMicrophone, "custom-mic-api-write");
        virtualAcceptThread = daemon(this::acceptVirtualMicrophone, "custom-virtual-mic-accept");
        LogService.info("自定义调音接口已就绪：实体麦克风 API 127.0.0.1:" + MIC_API_PORT
                + "，虚拟麦克风 API 127.0.0.1:" + VIRTUAL_MIC_API_PORT
                + "（48kHz / 单声道 / PCM16LE）");
        return true;
    }

    private static ServerSocket bind(int port) throws IOException {
        return new ServerSocket(port, 4, InetAddress.getLoopbackAddress());
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * 把拦截到的麦克风音频送入实体麦克风 API。
     * 在音频线程调用：只做拷贝与入队，网络发送由独立线程完成。
     */
    public void pushMicrophone(float[] mono, int length) {
        if (!running || length <= 0 || micClient == null) {
            return;
        }
        Block block = freeBlocks.poll();
        if (block == null || block.data.length < length) {
            block = new Block(Math.max(length, BLOCK_FRAMES));
        }
        System.arraycopy(mono, 0, block.data, 0, length);
        block.length = length;
        if (!micQueue.offer(block)) {
            recycle(micQueue.poll());
            if (!micQueue.offer(block)) {
                // 队列仍满时回收当前块，避免复用池被逐步耗尽
                recycle(block);
            }
        }
    }

    private void recycle(Block block) {
        if (block != null) {
            freeBlocks.offer(block);
        }
    }

    private void acceptMicrophone() {
        // 快照 server 引用：stop() 可能并发置空字段，直接用字段会在检查后 NPE
        ServerSocket server = micServer;
        if (server == null) {
            return;
        }
        while (running) {
            try {
                Socket socket = server.accept();
                socket.setTcpNoDelay(true);
                Socket old = micClient;
                micClient = socket;
                if (old != null) {
                    LogService.warn("实体麦克风 API 已有连接，断开旧连接并接受新客户端");
                    closeQuietly(old);
                }
                LogService.info("实体麦克风 API 已连接: " + socket.getRemoteSocketAddress());
            } catch (IOException e) {
                if (!running) {
                    return;
                }
                LogService.warn("实体麦克风 API 接受连接失败: " + e.getMessage());
                sleepBriefly();
            }
        }
    }

    private void writeMicrophone() {
        byte[] pcm = new byte[BLOCK_BYTES];
        // 不足 480 帧的尾部保留到下一块，保证每次写入严格为 BLOCK_BYTES
        byte[] pending = new byte[BLOCK_BYTES];
        int pendingBytes = 0;
        Socket lastClient = null;
        while (running) {
            Block block;
            try {
                block = micQueue.poll(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (block == null) {
                continue;
            }
            Socket client = micClient;
            if (client == null) {
                pendingBytes = 0;
                lastClient = null;
                recycle(block);
                continue;
            }
            if (client != lastClient) {
                pendingBytes = 0;
                lastClient = client;
            }
            int frames = block.length;
            int bytes = frames * 2;
            if (pcm.length < bytes) {
                pcm = new byte[bytes];
            }
            AudioConverter.monoToPcm16(block.data, frames, pcm);
            recycle(block);
            try {
                OutputStream out = client.getOutputStream();
                int offset = 0;
                while (offset < bytes) {
                    int take = Math.min(BLOCK_BYTES - pendingBytes, bytes - offset);
                    System.arraycopy(pcm, offset, pending, pendingBytes, take);
                    pendingBytes += take;
                    offset += take;
                    if (pendingBytes == BLOCK_BYTES) {
                        out.write(pending, 0, BLOCK_BYTES);
                        pendingBytes = 0;
                    }
                }
                out.flush();
            } catch (IOException e) {
                LogService.warn("实体麦克风 API 连接已断开: " + e.getMessage());
                closeQuietly(client);
                if (micClient == client) {
                    micClient = null;
                }
            }
        }
    }

    private void acceptVirtualMicrophone() {
        // 同 acceptMicrophone：先取局部引用，避免 stop() 并发置空导致 NPE
        ServerSocket server = virtualServer;
        if (server == null) {
            return;
        }
        while (running) {
            try {
                Socket socket = server.accept();
                socket.setTcpNoDelay(true);
                Socket old = virtualClient;
                virtualClient = socket;
                if (old != null) {
                    LogService.warn("虚拟麦克风 API 已有连接，断开旧连接并接受新客户端");
                    closeQuietly(old);
                }
                LogService.info("虚拟麦克风 API 已连接: " + socket.getRemoteSocketAddress());
                daemon(() -> readVirtualMicrophone(socket), "custom-virtual-mic-read");
            } catch (IOException e) {
                if (!running) {
                    return;
                }
                LogService.warn("虚拟麦克风 API 接受连接失败: " + e.getMessage());
                sleepBriefly();
            }
        }
    }

    private void readVirtualMicrophone(Socket socket) {
        byte[] buffer = new byte[BLOCK_BYTES];
        float[] mono = new float[BLOCK_FRAMES];
        try (InputStream in = socket.getInputStream()) {
            int filled = 0;
            while (running && virtualClient == socket) {
                int read = in.read(buffer, filled, buffer.length - filled);
                if (read < 0) {
                    break;
                }
                filled += read;
                if (filled == buffer.length) {
                    int frames = AudioConverter.pcmToMono(buffer, 0, buffer.length, 1, mono);
                    Sink target = sink;
                    if (frames > 0 && target != null) {
                        target.onFrames(mono, frames);
                    }
                    filled = 0;
                }
            }
        } catch (IOException e) {
            if (running) {
                LogService.warn("虚拟麦克风 API 连接已断开: " + e.getMessage());
            }
        } finally {
            closeQuietly(socket);
            if (virtualClient == socket) {
                virtualClient = null;
            }
            LogService.info("虚拟麦克风 API 已断开");
        }
    }

    /** 停止两个 API 服务并断开所有客户端。 */
    public synchronized void stop() {
        boolean wasRunning = running;
        running = false;
        sink = null;
        closeServers();
        closeQuietly(micClient);
        micClient = null;
        closeQuietly(virtualClient);
        virtualClient = null;
        micQueue.clear();
        join(micAcceptThread);
        join(micWriterThread);
        join(virtualAcceptThread);
        micAcceptThread = null;
        micWriterThread = null;
        virtualAcceptThread = null;
        if (wasRunning) {
            LogService.info("自定义调音接口已停止");
        }
    }

    private void closeServers() {
        closeQuietly(micServer);
        micServer = null;
        closeQuietly(virtualServer);
        virtualServer = null;
    }

    @Override
    public void close() {
        stop();
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void join(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // 关闭失败不影响主流程
        }
    }
}
