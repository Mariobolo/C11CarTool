package com.c11.cartool;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 纯 Java ADB 客户端（无外部依赖）
 *
 * 用于连接车机本地 adbd (127.0.0.1:5555)，
 * 通过 adb shell 执行命令，获得 shell uid(2000) 权限。
 *
 * ADB 协议参考: https://android.googlesource.com/platform/system/adb/+/master/protocol.txt
 *
 * 消息结构 (24 字节头 + 数据):
 *   command (4)  - 命令标识 (CNXN/AUTH/OPEN/OKAY/CLSE/WRTE)
 *   arg0 (4)     - 参数1
 *   arg1 (4)     - 参数2
 *   data_length (4) - 数据长度
 *   data_check (4)  - 数据校验和
 *   magic (4)    - command ^ 0xffffffff
 *
 * 所有多字节整数使用小端序。
 */
public final class AdbClient {

    private static final String TAG = "AdbClient";

    // ADB 命令常量（小端序）
    private static final int A_CNXN = 0x4e584e43; // "CNXN"
    private static final int A_AUTH = 0x48545541; // "AUTH"
    private static final int A_OPEN = 0x4e45504f; // "OPEN"
    private static final int A_OKAY = 0x59414b4f; // "OKAY"
    private static final int A_CLSE = 0x45534c43; // "CLSE"
    private static final int A_WRTE = 0x45545257; // "WRTE"

    // ADB 版本
    private static final int A_VERSION = 0x01000000;
    private static final int A_MAX_DATA = 4096;

    // AUTH 类型
    private static final int AUTH_TYPE_TOKEN = 1;
    private static final int AUTH_TYPE_SIGNATURE = 2;
    private static final int AUTH_TYPE_RSA_PUBLIC = 3;

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private boolean connected = false;
    private String host = "127.0.0.1";
    private int port = 5555;

    public AdbClient() {}

    public AdbClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean isConnected() { return connected; }

    /**
     * 连接到 adbd 并完成握手
     */
    public synchronized boolean connect(int timeoutMs) {
        try {
            Logger.info(TAG, "正在连接 " + host + ":" + port + "...");
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            socket.setTcpNoDelay(true);
            in = socket.getInputStream();
            out = socket.getOutputStream();

            // 发送 CNXN 握手
            String identity = "host::";
            byte[] identityBytes = (identity + "\0").getBytes("UTF-8");
            sendMessage(A_CNXN, A_VERSION, A_MAX_DATA, identityBytes);

            // 读取响应
            AdbMessage msg = readMessage();
            if (msg == null) {
                Logger.error(TAG, "握手失败: 无响应");
                close();
                return false;
            }

            if (msg.command == A_CNXN) {
                connected = true;
                Logger.ok(TAG, "ADB 连接成功 (不需要认证)");
                return true;
            }

            if (msg.command == A_AUTH) {
                // 服务器要求认证
                int authType = msg.arg0;
                Logger.info(TAG, "服务器要求认证 (type=" + authType + ")");

                if (authType == AUTH_TYPE_TOKEN) {
                    // 对于本地连接，尝试发送空的 RSA public key（表示不使用认证）
                    // 某些 adbd 配置允许本地连接跳过认证
                    byte[] emptyKey = new byte[]{0};
                    sendMessage(A_AUTH, AUTH_TYPE_RSA_PUBLIC, 0, emptyKey);
                    msg = readMessage();
                    if (msg != null && msg.command == A_CNXN) {
                        connected = true;
                        Logger.ok(TAG, "ADB 连接成功 (跳过认证)");
                        return true;
                    }
                }

                Logger.error(TAG, "ADB 认证失败，需要在车机上允许 USB 调试");
                close();
                return false;
            }

            Logger.error(TAG, "握手失败: 未知命令 0x" + Integer.toHexString(msg.command));
            close();
            return false;

        } catch (Exception e) {
            Logger.error(TAG, "连接异常: " + e.getMessage());
            close();
            return false;
        }
    }

    /**
     * 执行 shell 命令，返回完整结果
     */
    public synchronized Sh.Result shell(String cmd, long timeoutMs) {
        if (!connected) {
            return new Sh.Result(-1, "", "ADB 未连接", 0, false);
        }

        long start = System.currentTimeMillis();
        int localId = 1;
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        boolean isStderr = false;

        try {
            // 发送 OPEN 消息，打开 shell 流
            byte[] shellCmd = ("shell:" + cmd + "\0").getBytes("UTF-8");
            sendMessage(A_OPEN, localId, 0, shellCmd);

            // 读取响应
            AdbMessage msg = readMessage();
            if (msg == null) {
                return new Sh.Result(-1, "", "无响应", System.currentTimeMillis() - start, false);
            }

            if (msg.command == A_CLSE) {
                // 流被立即关闭（命令不存在等）
                long duration = System.currentTimeMillis() - start;
                return new Sh.Result(-1, "", "流被关闭", duration, false);
            }

            if (msg.command != A_OKAY) {
                long duration = System.currentTimeMillis() - start;
                return new Sh.Result(-1, "", "预期 OKAY，收到 0x" + Integer.toHexString(msg.command), duration, false);
            }

            int remoteId = msg.arg0;

            // 循环读取输出，直到收到 CLSE
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                msg = readMessage();
                if (msg == null) {
                    break;
                }

                if (msg.command == A_WRTE) {
                    // 收到数据，回复 OKAY
                    String data = new String(msg.data, "UTF-8");

                    // ADB shell 的 stdout 和 stderr 是分开的流
                    // 但通过 "shell:" 打开的是合并流，我们需要区分
                    // 实际上 adbd 会用两个流：stdout 和 stderr
                    // 但简单起见，我们都放到 stdout
                    if (isStderr) {
                        stderr.append(data);
                    } else {
                        stdout.append(data);
                    }

                    sendMessage(A_OKAY, localId, remoteId, null);

                } else if (msg.command == A_CLSE) {
                    // 流关闭，命令执行完成
                    sendMessage(A_CLSE, localId, remoteId, null);
                    break;

                } else if (msg.command == A_OKAY) {
                    // 对方确认我们的 OKAY，继续等待
                    continue;
                }
            }

            long duration = System.currentTimeMillis() - start;
            boolean timeout = System.currentTimeMillis() >= deadline;

            if (timeout) {
                Logger.warn(TAG, "命令超时(" + timeoutMs + "ms): " + cmd);
                // 尝试关闭流
                try { sendMessage(A_CLSE, localId, remoteId, null); } catch (Exception ignored) {}
            }

            return new Sh.Result(0, stdout.toString(), stderr.toString(), duration, timeout);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            Logger.error(TAG, "shell 命令异常: " + cmd + " -> " + e.getMessage());
            return new Sh.Result(-1, stdout.toString(), e.getMessage(), duration, false);
        }
    }

    /**
     * 便捷方法：执行命令并返回 stdout
     */
    public String shellOut(String cmd) {
        return shell(cmd, 10000).trim();
    }

    /**
     * 关闭连接
     */
    public synchronized void close() {
        connected = false;
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null;
        in = null;
        out = null;
        Logger.info(TAG, "连接已关闭");
    }

    // ═══════════════════════════════════════
    //  ADB 协议底层
    // ═══════════════════════════════════════

    private static class AdbMessage {
        int command;
        int arg0;
        int arg1;
        byte[] data;
    }

    private void sendMessage(int command, int arg0, int arg1, byte[] data) throws Exception {
        int dataLen = (data == null) ? 0 : data.length;
        int dataCheck = 0;
        if (data != null) {
            for (byte b : data) {
                dataCheck += (b & 0xff);
            }
        }
        int magic = command ^ 0xffffffff;

        ByteBuffer buf = ByteBuffer.allocate(24 + dataLen);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(command);
        buf.putInt(arg0);
        buf.putInt(arg1);
        buf.putInt(dataLen);
        buf.putInt(dataCheck);
        buf.putInt(magic);
        if (data != null) {
            buf.put(data);
        }

        out.write(buf.array());
        out.flush();
    }

    private AdbMessage readMessage() throws Exception {
        byte[] header = new byte[24];
        int read = readFully(in, header);
        if (read < 24) {
            return null;
        }

        ByteBuffer buf = ByteBuffer.wrap(header);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int command = buf.getInt();
        int arg0 = buf.getInt();
        int arg1 = buf.getInt();
        int dataLen = buf.getInt();
        int dataCheck = buf.getInt();
        int magic = buf.getInt();

        // 验证 magic
        if (magic != (command ^ 0xffffffff)) {
            Logger.error(TAG, "消息 magic 校验失败");
            return null;
        }

        byte[] data = null;
        if (dataLen > 0) {
            data = new byte[dataLen];
            readFully(in, data);
        }

        AdbMessage msg = new AdbMessage();
        msg.command = command;
        msg.arg0 = arg0;
        msg.arg1 = arg1;
        msg.data = data;
        return msg;
    }

    private static int readFully(InputStream in, byte[] buffer) throws Exception {
        int total = 0;
        while (total < buffer.length) {
            int n = in.read(buffer, total, buffer.length - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }
}
