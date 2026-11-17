"""自定义调音示例：把实体麦克风 API 收到的音频原样转发给虚拟麦克风 API。

数据格式（两个接口一致）：48kHz / 单声道 / PCM16LE，每块 480 帧共 960 字节。

使用步骤：
    1. 在软件中点击“切换自定义接口”，进入“自定义调音接口”面板；
    2. 点击“开始变声”，启动音频链路；
    3. 在“自定义调音文件”列表中选择本文件，软件会在后台自动运行它。

也可以在自己的终端里直接运行：
    python passthrough.py

接口说明：
    实体麦克风 API  127.0.0.1:47801  —— 从这里读取拦截到的麦克风音频
    虚拟麦克风 API  127.0.0.1:47802  —— 把处理后的音频写回这里
"""

import socket
import time

MIC_HOST, MIC_PORT = "127.0.0.1", 47801
OUT_HOST, OUT_PORT = "127.0.0.1", 47802
BLOCK_BYTES = 960        # 480 帧 × 2 字节（PCM16LE）
RETRY_SECONDS = 1.0


def connect(port, name):
    """连接接口；接口尚未就绪时每隔一秒重试，直到连接成功。"""
    while True:
        try:
            sock = socket.create_connection((MIC_HOST if port == MIC_PORT else OUT_HOST, port),
                                            timeout=2.0)
            sock.settimeout(None)
            sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            print(f"[{name}] 已连接 127.0.0.1:{port}", flush=True)
            return sock
        except OSError:
            print(f"[{name}] 等待接口 127.0.0.1:{port} ...", flush=True)
            time.sleep(RETRY_SECONDS)


def recv_block(sock):
    """读取完整的一块音频；对端断开时返回 None。"""
    data = bytearray(BLOCK_BYTES)
    view = memoryview(data)
    filled = 0
    while filled < BLOCK_BYTES:
        try:
            count = sock.recv_into(view[filled:], BLOCK_BYTES - filled)
        except OSError:
            return None
        if count == 0:
            return None
        filled += count
    return bytes(data)


def main():
    mic = None
    out = None
    try:
        while True:
            if mic is None:
                mic = connect(MIC_PORT, "实体麦克风")
            if out is None:
                out = connect(OUT_PORT, "虚拟麦克风")

            block = recv_block(mic)
            if block is None:
                print("[实体麦克风] 连接断开，重新等待接口", flush=True)
                mic.close()
                mic = None
                continue

            # 原样发送：不做任何处理，保证数据与接收时完全一致
            try:
                out.sendall(block)
            except OSError:
                print("[虚拟麦克风] 连接断开，重新等待接口", flush=True)
                out.close()
                out = None
    except KeyboardInterrupt:
        print("已停止", flush=True)
    finally:
        for sock in (mic, out):
            if sock is not None:
                sock.close()


if __name__ == "__main__":
    main()
