# -*- coding: utf-8 -*-
# v0.3.14: 语音 vr 控制收敛为 controlVr()，新增 startvr 唤起；releaseVoice 复用
import io

p = r'D:\Stu\Android\C11_TEST\projects\C11CarTool\app\src\main\java\com\c11\cartool\vehicle\VehicleController.java'
s = io.open(p, encoding='utf-8').read()

old = '''    // ═══ 语音释放：释放语音助手对麦克风的占用（stopvr），便于其它应用录音 ═══
    private static final String VOICE_RELEASE_PKG = "com.iflytek.cutefly.speechclient.hmi";
    private static final String VOICE_RELEASE_CLS = "com.iflytek.auto.speechclient.sdk.SpeechClientService";

    public boolean releaseVoice() {
        try {
            Log.i(TAG, "语音释放 stopvr");
            if (Sh.isAdbConnected()) {
                String cmd = "am startservice -n " + VOICE_RELEASE_PKG + "/" + VOICE_RELEASE_CLS
                        + " --ez stopvr true";
                Sh.Result r = Sh.run(cmd);
                setLastResult(r);
                Logger.cmd(cmd, r);
                return r.exit == 0;
            } else {
                Intent i = new Intent();
                i.setComponent(new ComponentName(VOICE_RELEASE_PKG, VOICE_RELEASE_CLS));
                i.putExtra("stopvr", true);
                if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
                else context.startService(i);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "语音释放失败: " + e.getMessage(), e);
            return false;
        }
    }
'''

new = '''    // ═══ 语音 vr 控制（SpeechClientService 管理麦克风/交互态）═══
    //  startvr 唤起小零；stopvr 释放录音器（便于其它应用录音）；exitvr 结束交互；resumevr 恢复
    private static final String VOICE_PKG = "com.iflytek.cutefly.speechclient.hmi";
    private static final String VOICE_CLS = "com.iflytek.auto.speechclient.sdk.SpeechClientService";

    /**
     * 向 SpeechClientService 发一条 vr 控制指令。
     * 服务端 onStartCommand 只用 intent.hasExtra(key) 判断、不校验 extra 值类型，
     * 故统一用 String extra（与官方 controlIflyView 一致），值传本应用包名标识来源。
     */
    private boolean controlVr(String vrKey) {
        try {
            Log.i(TAG, "语音 vr 控制: " + vrKey);
            if (Sh.isAdbConnected()) {
                String cmd = "am startservice -n " + VOICE_PKG + "/" + VOICE_CLS
                        + " --es " + vrKey + " " + context.getPackageName();
                Sh.Result r = Sh.run(cmd);
                setLastResult(r);
                Logger.cmd(cmd, r);
                return r.exit == 0;
            } else {
                Intent i = new Intent();
                i.setComponent(new ComponentName(VOICE_PKG, VOICE_CLS));
                i.putExtra(vrKey, context.getPackageName());
                if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
                else context.startService(i);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "语音 vr 控制失败 " + vrKey + ": " + e.getMessage(), e);
            return false;
        }
    }

    /** 唤起小零（进入语音交互态）。 */
    public boolean invokeVoice() { return controlVr("startvr"); }

    /** 释放小零对麦克风的占用（stopvr），便于其它应用录音。 */
    public boolean releaseVoice() { return controlVr("stopvr"); }
'''

n = s.count(old)
print('match=%d' % n)
if n != 1:
    raise SystemExit('old block not found uniquely')
io.open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new))
print('VehicleController controlVr done')
