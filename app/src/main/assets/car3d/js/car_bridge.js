// C11CarTool v0.3.13 — 车机 3D 车模 HUD 数据桥
// Car3DActivity 通过 addJavascriptInterface("CarBridge") 注入 pushData(json) 推送车辆状态；
// 在官方 3D 车模演示之上叠加 HUD 数据面板，模型自身点按/滑动/长按交互全部保留。
(function () {
  'use strict';
  var hud = null, els = {};

  function make(tag, cls, text) {
    var e = document.createElement(tag || 'div');
    if (cls) e.className = cls;
    if (text !== undefined && text !== null) e.textContent = text;
    return e;
  }

  function buildHud() {
    if (hud) return;
    var style = document.createElement('style');
    style.textContent = [
      '#cartool-hud{position:fixed;left:0;top:0;right:0;bottom:0;z-index:9999;pointer-events:none;font-family:sans-serif;color:#fff;}',
      '#cartool-hud .p{position:absolute;background:rgba(10,14,24,.62);border:1px solid rgba(255,255,255,.14);border-radius:12px;padding:10px 14px;}',
      '#cartool-hud .num{font-weight:700;letter-spacing:.5px;}',
      '#cartool-hud .lbl{color:rgba(255,255,255,.55);font-size:12px;}',
      '#cartool-hud .spd{font-size:54px;line-height:1;font-weight:800;}',
      '#cartool-hud .gear{font-size:30px;font-weight:800;padding-left:10px;}',
      '#cartool-hud .row{display:flex;align-items:center;gap:8px;margin-top:6px;}',
      '#cartool-hud .chip{font-size:13px;padding:2px 8px;border-radius:6px;background:rgba(255,255,255,.12);white-space:nowrap;}',
      '#cartool-hud .chip.on{background:rgba(34,197,94,.85);color:#04120a;}',
      '#cartool-hud .chip.off{background:rgba(239,68,68,.85);color:#fff;}',
      '#cartool-hud .scenebtn{pointer-events:auto;cursor:pointer;background:rgba(255,255,255,.14);border:1px solid rgba(255,255,255,.25);border-radius:8px;color:#fff;font-size:14px;padding:6px 12px;margin-left:8px;}',
      '#cartool-hud .scenebtn.active{background:rgba(59,130,246,.9);border-color:#60a5fa;}'
    ].join('\n');
    document.head.appendChild(style);

    hud = document.createElement('div');
    hud.id = 'cartool-hud';
    document.body.appendChild(hud);

    // 左上：车速 + 挡位 + 电量/续航
    var tl = make('div', 'p');
    tl.style.cssText += 'left:16px;top:16px;min-width:220px;';
    var spd = make('div', 'spd num', '--');
    var gear = make('div', 'gear', '--');
    var r1 = make('div', 'row'); r1.appendChild(spd); r1.appendChild(gear);
    var soc = make('span', 'chip', '电量 --');
    var rng = make('span', 'chip', '续航 --');
    var ext = make('span', 'chip', '外温 --');
    var r2 = make('div', 'row'); r2.appendChild(soc); r2.appendChild(rng); r2.appendChild(ext);
    tl.appendChild(r1); tl.appendChild(r2);
    hud.appendChild(tl);

    // 左下：门/窗/空调
    var bl = make('div', 'p');
    bl.style.cssText += 'left:16px;bottom:16px;max-width:520px;';
    var doors = [], wins = [];
    var dr = make('div', 'row');
    ['左前','右前','左后','右后','后备','前盖'].forEach(function (n) {
      var c = make('span', 'chip', n + ' --'); doors.push(c); dr.appendChild(c);
    });
    var wr = make('div', 'row');
    ['左前窗','右前窗','左后窗','右后窗'].forEach(function (n) {
      var c = make('span', 'chip', n + ' --'); wins.push(c); wr.appendChild(c);
    });
    var acS = make('span', 'chip', '空调 --');
    var acM = make('span', 'chip', '模式 --');
    var fan = make('span', 'chip', '风量 --');
    var def = make('span', 'chip', '除霜 --');
    var ar = make('div', 'row'); ar.appendChild(acS); ar.appendChild(acM); ar.appendChild(fan); ar.appendChild(def);
    bl.appendChild(dr); bl.appendChild(wr); bl.appendChild(ar);
    hud.appendChild(bl);

    // 右上：ADB 状态 + 场景切换
    var tr = make('div', 'p');
    tr.style.cssText += 'right:16px;top:16px;display:flex;align-items:center;';
    var adb = make('span', 'chip', 'ADB --');
    var b1 = make('button', 'scenebtn active', '外观');
    var b2 = make('button', 'scenebtn', '智驾');
    var b3 = make('button', 'scenebtn', '内饰');
    b1.onclick = function () { setScene(1); };
    b2.onclick = function () { setScene(2); };
    b3.onclick = function () { setScene(3); };
    tr.appendChild(adb); tr.appendChild(b1); tr.appendChild(b2); tr.appendChild(b3);
    hud.appendChild(tr);

    // 右下：电压/电流/胎压
    var br = make('div', 'p');
    br.style.cssText += 'right:16px;bottom:16px;min-width:210px;';
    var vc = make('div', 'num', '电压 --');
    var cc = make('div', 'num', '电流 --');
    var tp = make('div', 'lbl', '胎压 -- / -- / -- / --');
    br.appendChild(vc); br.appendChild(cc); br.appendChild(tp);
    hud.appendChild(br);

    els = { spd: spd, gear: gear, soc: soc, rng: rng, ext: ext,
            doors: doors, wins: wins, ac: acS, mode: acM, fan: fan, def: def,
            adb: adb, vc: vc, cc: cc, tp: tp, b1: b1, b2: b2, b3: b3 };
  }

  function fmt(v, unit, dec) {
    if (v === null || v === undefined || isNaN(v) || v < 0) return '--';
    return (dec !== undefined ? v.toFixed(dec) : String(v)) + (unit || '');
  }

  function setScene(n) {
    // 模型状态枚举：State1=2 外观 / State2=3 智驾 / State3=4 内饰
    if (window.v) window.v.state = 1 + n;
    [els.b1, els.b2, els.b3].forEach(function (b, i) {
      b.className = 'scenebtn' + (i + 1 === n ? ' active' : '');
    });
  }
  window.setScene = setScene;

  window.updateCarData = function (d) {
    buildHud();
    if (!d) return;
    els.spd.textContent = fmt(d.speed);
    var g = d.gear || '';
    els.gear.textContent = g ? g : '--';
    els.gear.style.color = g === 'R' ? '#ef4444' : (g === 'D' ? '#22c55e' : (g === 'N' ? '#eab308' : '#fff'));
    els.soc.textContent = '电量 ' + fmt(d.soc, '%');
    els.rng.textContent = '续航 ' + fmt(d.rangeStd, 'km');
    els.ext.textContent = '外温 ' + fmt(d.outsideTemp, '°');
    var dr = d.doors || [];
    for (var i = 0; i < 6 && i < els.doors.length; i++) {
      var s = dr[i];
      if (s === undefined) continue;
      var c = els.doors[i];
      c.textContent = c.textContent.split(' ')[0] + ' ' + (s === 1 ? '开' : (s === 0 ? '关' : '--'));
      c.className = 'chip' + (s === 1 ? ' on' : (s === 0 ? ' off' : ''));
    }
    var ws = d.windows || [];
    for (var j = 0; j < 4 && j < els.wins.length; j++) {
      var w = ws[j];
      if (w === undefined) continue;
      var wc = els.wins[j];
      wc.textContent = wc.textContent.split(' ')[0] + ' ' + (w >= 0 ? w + '%' : '--');
    }
    els.ac.textContent = '空调 ' + (d.acSwitch === 1 ? '开' : (d.acSwitch === 0 ? '关' : '--'));
    els.ac.className = 'chip' + (d.acSwitch === 1 ? ' on' : (d.acSwitch === 0 ? ' off' : ''));
    var raw = d.acModeRaw;
    els.mode.textContent = '模式 ' + (raw === undefined || raw === '' ? '--' : raw);
    els.fan.textContent = '风量 ' + fmt(d.fanSpeed);
    els.def.textContent = '除霜 ' + (d.frontDefrost === 1 ? '开' : (d.frontDefrost === 0 ? '关' : '--'));
    els.adb.textContent = d.adbConnected ? 'ADB 已连' : 'ADB 未连';
    els.adb.className = 'chip' + (d.adbConnected ? ' on' : ' off');
    els.vc.textContent = '电压 ' + fmt(d.voltage, 'V', 1);
    els.cc.textContent = '电流 ' + fmt(d.current, 'A', 1);
    var tp = d.tireKpa || [];
    els.tp.textContent = '胎压 ' + tp.map(function (t) { return fmt(t); }).join(' / ');
  };

  // 模型就绪后挂载 HUD（canvas 出现即认为渲染完成）
  var iv = setInterval(function () {
    if (document.querySelector('canvas')) { clearInterval(iv); buildHud(); }
  }, 300);
})();
