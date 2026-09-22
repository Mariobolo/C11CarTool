package com.c11.cartool;

import android.content.Context;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 纯 Java ADB 客户端（无外部依赖）
 *
 * 用于连接车机本地 adbd (127.0.0.1:5555)，
 * 通过 adb shell 执行命令，获得 shell uid(2000) 权限。
 *
 * 特性：
 * - 稳定 RSA 密钥对（持久化存储，避免每次弹新指纹）
 * - 标准 ADB 认证流程（token → signature → public key）
 * - 命令间隔防流冲突
 * - 自动重试
 *
 * ADB 协议参考: https://android.googlesource.com/platform/system/adb/+/master/protocol.txt
 */
public final class AdbClient {

    private static final String TAG = "AdbClient";

    // ADB 命令常量（小端序）
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;

    private static final int A_VERSION = 0x01000000;
    private static final int A_MAX_DATA = 4096;

    private static final int AUTH_TYPE_TOKEN = 1;
    private static final int AUTH_TYPE_SIGNATURE = 2;
    private static final int AUTH_TYPE_RSA_PUBLIC = 3;

    // 命令之间的最小间隔（毫秒），防止 adbd 流冲突
    private static final long MIN_COMMAND_INTERVAL_MS = 100;
    private static final long WARMUP_MS = 300;

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private boolean connected = false;
    private long connectedAt = 0;
    private int consecutiveFailures = 0;
    private String host = "127.0.0.1";
    private int port = 5555;

    private final AtomicInteger nextLocalId = new AtomicInteger(1);
    private long lastCommandTime = 0;

    // RSA 密钥
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private byte[] adbPublicKeyBytes; // ADB 格式的公钥（base64 + 注释）

    public AdbClient() {}

    public AdbClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean isConnected() { return connected; }

    /**
     * 初始化 RSA 密钥对（从文件加载或生成新的）
     * 必须在 connect 之前调用
     */
    public synchronized boolean initKeys(Context ctx) {
        try {
            File keyDir = new File(ctx.getFilesDir(), "adb_auth");
            if (!keyDir.exists()) keyDir.mkdirs();
            File privFile = new File(keyDir, "adb_private.key");
            File pubFile = new File(keyDir, "adb_public.key");

            if (privFile.exists() && pubFile.exists()) {
                // 加载已有密钥
                byte[] privBytes = readFile(privFile);
                byte[] pubBytes = readFile(pubFile);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
                publicKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
                Logger.info(TAG, "已加载 ADB 密钥");
            } else {
                // 生成新密钥
                Logger.info(TAG, "生成新的 ADB RSA 密钥对...");
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                KeyPair kp = kpg.generateKeyPair();
                privateKey = kp.getPrivate();
                publicKey = kp.getPublic();
                // 保存
                writeFile(privFile, privateKey.getEncoded());
                writeFile(pubFile, publicKey.getEncoded());
                Logger.ok(TAG, "ADB 密钥对已生成并保存");
            }

            // 构造 ADB 格式的公钥
            adbPublicKeyBytes = buildAdbPublicKey((RSAPublicKey) publicKey);
            Logger.ok(TAG, "ADB 公钥指纹 " + keyFingerprint()
                    + "（同一安装应恒定；若每次重连变化，说明密钥未被持久化复用）");
            return true;
        } catch (Exception e) {
            Logger.error(TAG, "密钥初始化失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 构造 ADB 格式的公钥（base64(二进制) + " user@host\0"）
     */
    private byte[] buildAdbPublicKey(RSAPublicKey rsaPub) throws Exception {
        BigInteger modulus = rsaPub.getModulus();
        BigInteger exponent = rsaPub.getPublicExponent();

        // 二进制格式: 3字节长度 + 'A' + 256字节modulus + 3字节exponent
        byte[] modBytes = modulus.toByteArray();
        // modulus 可能是 257 字节（带符号位），需要去掉前导 0
        if (modBytes.length > 256) {
            byte[] tmp = new byte[256];
            System.arraycopy(modBytes, modBytes.length - 256, tmp, 0, 256);
            modBytes = tmp;
        } else if (modBytes.length < 256) {
            byte[] tmp = new byte[256];
            System.arraycopy(modBytes, 0, tmp, 256 - modBytes.length, modBytes.length);
            modBytes = tmp;
        }

        byte[] expBytes = exponent.toByteArray();
        // 总长度 = 1(tag) + 256(mod) + expBytes.length
        int totalLen = 1 + 256 + expBytes.length;
        ByteBuffer buf = ByteBuffer.allocate(3 + totalLen);
        buf.put((byte) ((totalLen >> 16) & 0xff));
        buf.put((byte) ((totalLen >> 8) & 0xff));
        buf.put((byte) (totalLen & 0xff));
        buf.put((byte) 'A');
        buf.put(modBytes);
        buf.put(expBytes);

        String b64 = Base64.encodeToString(buf.array(), Base64.NO_WRAP);
        String keyStr = b64 + " c11cartool@android\0";
        return keyStr.getBytes("UTF-8");
    }

    /** ADB 公钥指纹（SHA-256 前 8 字节，冒号分隔），用于核对每次连接是否同一密钥 */
    /** 当前密钥对的 ADB 公钥指纹（MD5 风格），供诊断报告与车机授权弹窗比对 */
    public synchronized String keyFingerprint() {
        try {
            byte[] dg = java.security.MessageDigest.getInstance("SHA-256").digest(adbPublicKeyBytes);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i > 0) sb.append(':');
                sb.append(Character.forDigit((dg[i] >> 4) & 0xf, 16));
                sb.append(Character.forDigit(dg[i] & 0xf, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "(指纹计算失败)";
        }
    }

    /**
     * 连接到 adbd 并完成握手和认证
     */
    public synchronized boolean connect(int timeoutMs) {
        try {
            Logger.info(TAG, "正在连接 " + host + ":" + port + "...");
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            socket.setTcpNoDelay(true);
            socket.setReceiveBufferSize(65536);
            socket.setSendBufferSize(65536);
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
                connectedAt = System.currentTimeMillis();
                try { socket.setSoTimeout(30000); } catch (Exception ignored) {}
                Logger.ok(TAG, "ADB 连接成功 (不需要认证)");
                try { Thread.sleep(300); } catch (Exception ignored) {}
                return true;
            }

            if (msg.command == A_AUTH) {
                int authType = msg.arg0;
                Logger.info(TAG, "服务器要求认证 (type=" + authType + ")");

                if (authType == AUTH_TYPE_TOKEN && privateKey != null) {
                    // 用私钥签名 token
                    byte[] token = msg.data;
                    try {
                        Signature sig = Signature.getInstance("SHA1withRSA");
                        sig.initSign(privateKey);
                        sig.update(token);
                        byte[] signature = sig.sign();
                        sendMessage(A_AUTH, AUTH_TYPE_SIGNATURE, 0, signature);
                        msg = readMessage();
                        if (msg != null && msg.command == A_CNXN) {
                            connected = true;
                            connectedAt = System.currentTimeMillis();
                            try { socket.setSoTimeout(30000); } catch (Exception ignored) {}
                            Logger.ok(TAG, "ADB 连接成功 (签名认证)");
                            try { Thread.sleep(300); } catch (Exception ignored) {}
                            return true;
                        }
                        Logger.info(TAG, "签名认证未通过，尝试发送公钥...");
                    } catch (Exception e) {
                        Logger.warn(TAG, "签名失败: " + e.getMessage());
                    }
                }

                // 发送公钥（让用户在车机上确认）
                if (adbPublicKeyBytes != null) {
                    sendMessage(A_AUTH, AUTH_TYPE_RSA_PUBLIC, 0, adbPublicKeyBytes);
                } else {
                    // 没有密钥，发送空 key 尝试跳过认证
                    sendMessage(A_AUTH, AUTH_TYPE_RSA_PUBLIC, 0, new byte[]{0});
                }
                msg = readMessage();
                if (msg != null && msg.command == A_CNXN) {
                    connected = true;
                    connectedAt = System.currentTimeMillis();
                    try { socket.setSoTimeout(30000); } catch (Exception ignored) {}
                    Logger.ok(TAG, "ADB 连接成功 (公钥认证)");
                    try { Thread.sleep(300); } catch (Exception ignored) {}
                    return true;
                }

                Logger.error(TAG, "ADB 认证失败，请在车机上允许 USB 调试");
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
     * 执行 shell 命令，返回完整结果（带自动重试）。
     * 仅 IO 级错误（连接断开）才放弃；命令级失败有限重试，不误判断连。
     */
    public synchronized Sh.Result shell(String cmd, long timeoutMs) {
        long sinceConnect = System.currentTimeMillis() - connectedAt;
        if (connectedAt > 0 && sinceConnect < WARMUP_MS) {
            try { Thread.sleep(WARMUP_MS - sinceConnect); } catch (Exception ignored) {}
        }

        Sh.Result result = shellInternal(cmd, timeoutMs);
        if (result.exit != -1) {
            consecutiveFailures = 0;
            return result;
        }
        if (!connected) {
            Logger.warn(TAG, "连接已断开，放弃命令: " + cmd);
            return result;
        }

        // 命令级失败（上一流残留消息/瞬时错误）：有限重试
        for (int attempt = 1; attempt <= 2; attempt++) {
            consecutiveFailures++;
            Logger.warn(TAG, "命令失败，重试" + attempt + ": " + cmd);
            try { Thread.sleep(300 + attempt * 200L); } catch (Exception ignored) {}
            result = shellInternal(cmd, timeoutMs);
            if (result.exit != -1) {
                consecutiveFailures = 0;
                return result;
            }
            if (!connected) {
                Logger.warn(TAG, "重试中断开连接，放弃: " + cmd);
                return result;
            }
        }
        return result;
    }

    private Sh.Result shellInternal(String cmd, long timeoutMs) {
        if (!connected) {
            return new Sh.Result(-1, "", "ADB 未连接", 0, false);
        }

        long now0 = System.currentTimeMillis();
        long waitMs = MIN_COMMAND_INTERVAL_MS - (now0 - lastCommandTime);
        if (waitMs > 0) {
            try { Thread.sleep(waitMs); } catch (Exception ignored) {}
        }
        lastCommandTime = System.currentTimeMillis();

        long start = System.currentTimeMillis();
        int localId = nextLocalId.getAndIncrement();
        if (localId > 1000000) nextLocalId.set(1);

        StringBuilder stdout = new StringBuilder();
        int remoteId = 0;
        boolean receivedOkay = false;

        try {
            byte[] shellCmd = ("shell:" + cmd + "\0").getBytes("UTF-8");
            sendMessage(A_OPEN, localId, 0, shellCmd);

            // 握手阶段：只处理属于本流(arg1==localId)的消息，忽略上一流残留
            long handshakeDeadline = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < handshakeDeadline) {
                AdbMessage msg = readMessage();
                if (msg == null) {
                    return streamFail(start, stdout, "无响应(握手)");
                }
                if (msg.arg1 != localId) {
                    Logger.info(TAG, "忽略非本流消息 cmd=0x" + Integer.toHexString(msg.command)
                            + " arg0=" + msg.arg0 + " arg1=" + msg.arg1 + " 本流=" + localId);
                    continue;
                }
                if (msg.command == A_OKAY) {
                    remoteId = msg.arg0;
                    receivedOkay = true;
                    break;
                } else if (msg.command == A_WRTE) {
                    if (msg.data != null) stdout.append(new String(msg.data, "UTF-8"));
                    sendMessage(A_OKAY, localId, msg.arg0, null);
                } else if (msg.command == A_CLSE) {
                    sendMessage(A_CLSE, localId, msg.arg0, null);
                    return streamFail(start, stdout, "流被关闭(立即)");
                }
            }

            if (!receivedOkay) {
                return streamFail(start, stdout, "握手超时");
            }

            // 数据阶段：读到本流 CLSE 干净结束
            long deadline = System.currentTimeMillis() + timeoutMs;
            boolean cleanClose = false;
            while (System.currentTimeMillis() < deadline) {
                AdbMessage msg = readMessage();
                if (msg == null) break;
                if (msg.arg1 != localId) {
                    continue; // 忽略其他流残留，不回 ACK，避免污染 adbd 流状态
                }
                if (msg.command == A_WRTE) {
                    if (msg.data != null && msg.data.length > 0) {
                        stdout.append(new String(msg.data, "UTF-8"));
                    }
                    sendMessage(A_OKAY, localId, remoteId, null);
                } else if (msg.command == A_CLSE) {
                    sendMessage(A_CLSE, localId, remoteId, null);
                    cleanClose = true;
                    break;
                } else if (msg.command == A_OKAY) {
                    continue;
                }
            }

            long duration = System.currentTimeMillis() - start;
            if (!cleanClose) {
                Logger.warn(TAG, "命令未在 " + timeoutMs + "ms 内结束(按超时处理): " + cmd);
                try { sendMessage(A_CLSE, localId, remoteId, null); } catch (Exception ignored) {}
            }
            return new Sh.Result(0, stdout.toString(), "", duration, !cleanClose);

        } catch (java.io.IOException e) {
            long duration = System.currentTimeMillis() - start;
            Logger.error(TAG, "shell IO异常(判定连接断开): " + cmd + " -> " + e, e);
            connected = false;
            return new Sh.Result(-1, stdout.toString(), "IO:" + e.getMessage(), duration, false);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            Logger.error(TAG, "shell 命令异常(不判定断连): " + cmd + " -> " + e, e);
            return new Sh.Result(-1, stdout.toString(), String.valueOf(e.getMessage()), duration, false);
        }
    }

    /** 流级失败出口（不断连，交给重试） */
    private Sh.Result streamFail(long start, StringBuilder stdout, String reason) {
        long duration = System.currentTimeMillis() - start;
        Logger.warn(TAG, "shell 流失败: " + reason + " (" + duration + "ms)");
        return new Sh.Result(-1, stdout.toString(), reason, duration, false);
    }

    public String shellOut(String cmd) {
        return shell(cmd, 10000).out.trim();
    }

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
            for (byte b : data) dataCheck += (b & 0xff);
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
        if (data != null) buf.put(data);

        out.write(buf.array());
        out.flush();
    }

    private AdbMessage readMessage() throws Exception {
        byte[] header = new byte[24];
        int read = readFully(in, header);
        if (read < 24) return null;

        ByteBuffer buf = ByteBuffer.wrap(header);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int command = buf.getInt();
        int arg0 = buf.getInt();
        int arg1 = buf.getInt();
        int dataLen = buf.getInt();
        int dataCheck = buf.getInt();
        int magic = buf.getInt();

        if (magic != (command ^ 0xffffffff)) {
            Logger.error(TAG, "magic 校验失败");
            return null;
        }
        if (dataLen < 0 || dataLen > 1024 * 1024) {
            Logger.error(TAG, "数据长度异常: " + dataLen);
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

    private static byte[] readFile(File f) throws Exception {
        FileInputStream fis = new FileInputStream(f);
        byte[] data = new byte[(int) f.length()];
        readFully(fis, data);
        fis.close();
        return data;
    }

    private static void writeFile(File f, byte[] data) throws Exception {
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(data);
        fos.close();
    }
}
