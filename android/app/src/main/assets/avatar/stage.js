(function () {
  'use strict';

  var query = new URLSearchParams(window.location.search);
  var DEFAULT_MODEL = query.get('model') || 'whalegirl';

  var bridge = window.VtuberAvatar || null;

  var MOODS = {
    idle:      { angleX: 0,   angleY: 0,    angleZ: 0,   eyeX: 0,    eyeY: 0,    browL: 0,    browR: 0,    browForm: 0,   mouthForm: 0.35, smile: 0,  mouth: 0.55, hue: 0 },
    listening: { angleX: 0,   angleY: -5,   angleZ: 2,   eyeX: 0,    eyeY: 0.35, browL: 0.7,  browR: 0.7,  browForm: 0,   mouthForm: 0.55, smile: 0,  mouth: 0.70, hue: 0.04 },
    thinking:  { angleX: -9,  angleY: -9,   angleZ: -6,  eyeX: -0.6, eyeY: -0.45,browL: -0.2, browR: 0.5,  browForm: 0,   mouthForm: 0,    smile: 0,  mouth: 0.42, hue: -0.05 },
    speaking:  { angleX: 0,   angleY: 0,    angleZ: 0,   eyeX: 0,    eyeY: 0,    browL: 0.3,  browR: 0.3,  browForm: 0,   mouthForm: 0.65, smile: 0,  mouth: 0.85, hue: 0.02 },
    sleep:     { angleX: 0,   angleY: 11,   angleZ: -8,  eyeX: 0,    eyeY: 0.3,  browL: -0.3, browR: -0.3, browForm: 0,   mouthForm: 0.1,  smile: 0,  mouth: 0.22, hue: -0.10 },
    happy:     { angleX: 0,   angleY: -6,   angleZ: 7,   eyeX: 0,    eyeY: 0,    browL: 1,    browR: 1,    browForm: 0,   mouthForm: 1,    smile: 1,  mouth: 0.95, hue: 0.08 },
    surprised: { angleX: -4,  angleY: -13,  angleZ: 5,   eyeX: 0,    eyeY: -0.1, browL: 1,    browR: 1,    browForm: 0,   mouthForm: -0.2, smile: 0,  mouth: 0.5,  hue: 0.12 },
    concerned: { angleX: 3,   angleY: 4,    angleZ: -5,  eyeX: 0.2,  eyeY: 0.25, browL: -0.8, browR: 0.9,  browForm: 1,   mouthForm: -0.6, smile: 0,  mouth: 0.45, hue: -0.08 }
  };

  var STATUS = {
    idle: '',
    listening: '🎤 слушаю…',
    thinking: '💭 думаю…',
    speaking: '💬 говорю',
    sleep: '😴 тишина',
    happy: '✨ радость',
    surprised: '😮 ой!',
    concerned: '😟 тревога'
  };

  var state = {
    app: null,
    model: null,
    modelName: DEFAULT_MODEL,
    ready: false,
    mood: 'idle',
    from: MOODS.idle,
    to: MOODS.idle,
    changedAt: 0,
    groups: { eyeBlink: ['ParamEyeLOpen', 'ParamEyeROpen'], lipSync: ['ParamMouthOpenY'] },
    motions: [],
    speaking: false,
    level: 0,
    levelSmooth: 0,
    env: 0,
    breath: 0,
    time: 0,
    blink: { next: 2.4, state: 'wait', t: 0, double: false },
    eyeClosed: 0,
    gaze: { x: 0, y: 0, tx: 0, ty: 0 },
    tilt: 0,
    tiltTarget: 0,
    modelScale: 1,
    caption: '',
    captionAlpha: 0,
    statusText: '',
    statusAlpha: 0,
    flash: 0,
    dim: 0,
    dimTarget: 0,
    seed: 20260927,
    w: 300,
    h: 300
  };

  var els = {
    base: null,
    gradient: null,
    dimmer: null,
    beams: null,
    stars: [],
    hearts: [],
    city: null,
    holder: null,
    bars: [],
    frame: null,
    flashOverlay: null,
    caption: null,
    status: null
  };

  function rand() {
    state.seed = (state.seed * 1664525 + 1013904223) % 4294967296;
    return state.seed / 4294967296;
  }

  function range(a, b) {
    return a + rand() * (b - a);
  }

  function num(v, fallback) {
    return typeof v === 'number' && isFinite(v) ? v : fallback;
  }

  function clamp(v, a, b) {
    v = num(v, a);
    return v < a ? a : v > b ? b : v;
  }

  function lerp(a, b, t) {
    return a + (b - a) * num(t, 0);
  }

  function hue(h, s, l) {
    h = ((h % 1) + 1) % 1;
    function f(n) {
      var k = (n + h * 12) % 12;
      var a = s * Math.min(l, 1 - l);
      return Math.round(255 * (l - a * Math.max(-1, Math.min(k - 3, Math.min(9 - k, 1)))));
    }
    return (f(0) << 16) | (f(8) << 8) | f(4);
  }

  function native(name, args) {
    if (bridge && typeof bridge[name] === 'function') {
      try {
        bridge[name].apply(null, args || []);
      } catch (e) {}
    }
  }

  function core() {
    return state.model && state.model.internalModel ? state.model.internalModel.coreModel : null;
  }

  function hasParam(id) {
    var c = core();
    if (!c) return false;
    try {
      return c.getParameterIndex(id) < c.getParameterCount();
    } catch (e) {
      return false;
    }
  }

  function setParam(candidates, value) {
    var c = core();
    if (!c) return;
    var ids = Array.isArray(candidates) ? candidates : [candidates];
    for (var i = 0; i < ids.length; i++) {
      if (hasParam(ids[i])) {
        try {
          c.setParameterValueById(ids[i], value);
          return;
        } catch (e) {
          return;
        }
      }
    }
  }

  function lockParams() {
    var c = core();
    if (c && c.saveParameters) {
      try {
        c.saveParameters();
      } catch (e) {}
    }
  }

  function makeGlowTexture(size, stops) {
    var canvas = document.createElement('canvas');
    canvas.width = canvas.height = size;
    var ctx = canvas.getContext('2d');
    var g = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
    stops.forEach(function (stop) {
      g.addColorStop(stop[0], stop[1]);
    });
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, size, size);
    return PIXI.Texture.from(canvas);
  }

  function makeBaseTexture() {
    var size = 512;
    var canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    var ctx = canvas.getContext('2d');
    var g = ctx.createLinearGradient(0, 0, 0, size);
    g.addColorStop(0, '#1a0432');
    g.addColorStop(0.42, '#2d0a4a');
    g.addColorStop(0.78, '#1d0536');
    g.addColorStop(1, '#100222');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, size, size);
    var glow = ctx.createRadialGradient(size * 0.5, size * 0.36, 0, size * 0.5, size * 0.36, size * 0.62);
    glow.addColorStop(0, 'rgba(255,70,190,0.30)');
    glow.addColorStop(0.55, 'rgba(170,40,220,0.14)');
    glow.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = glow;
    ctx.fillRect(0, 0, size, size);
    return PIXI.Texture.from(canvas);
  }

  function makeHeartTexture(size) {
    var canvas = document.createElement('canvas');
    canvas.width = canvas.height = size;
    var ctx = canvas.getContext('2d');
    var s = size / 32;
    ctx.clearRect(0, 0, size, size);
    ctx.fillStyle = '#ffffff';
    ctx.beginPath();
    ctx.moveTo(16 * s, 28 * s);
    ctx.bezierCurveTo(4 * s, 19 * s, 2 * s, 10 * s, 8 * s, 6 * s);
    ctx.bezierCurveTo(13 * s, 3 * s, 16 * s, 8 * s, 16 * s, 10 * s);
    ctx.bezierCurveTo(16 * s, 8 * s, 19 * s, 3 * s, 24 * s, 6 * s);
    ctx.bezierCurveTo(30 * s, 10 * s, 28 * s, 19 * s, 16 * s, 28 * s);
    ctx.closePath();
    ctx.fill();
    ctx.lineWidth = 1.6 * s;
    ctx.strokeStyle = 'rgba(255,255,255,0.9)';
    ctx.stroke();
    return PIXI.Texture.from(canvas);
  }

  function stage() {
    return state.app.stage;
  }

  function radius() {
    return Math.min(state.w, state.h) * 0.11;
  }

  function addBackdrop() {
    els.base = new PIXI.Sprite(makeBaseTexture());
    stage().addChild(els.base);

    els.gradient = new PIXI.Sprite(
      makeGlowTexture(512, [
        [0, 'rgba(255,64,190,0.95)'],
        [0.32, 'rgba(150,30,190,0.5)'],
        [1, 'rgba(0,0,0,0)']
      ])
    );
    els.gradient.anchor.set(0.5);
    els.gradient.blendMode = PIXI.BLEND_MODES.ADD;
    stage().addChild(els.gradient);

    els.beams = new PIXI.Container();
    var colors = [0xff3ec8, 0x9b5cff, 0x2fe6ff, 0xff7ae0, 0x7c4dff];
    for (var i = 0; i < 7; i++) {
      var color = colors[i % colors.length];
      var width = range(state.w * 0.04, state.w * 0.12);
      var beam = new PIXI.Container();
      var shape = new PIXI.Graphics();
      shape.beginFill(color, 0.1);
      shape.moveTo(0, 0);
      shape.lineTo(-width, state.h * 2.6);
      shape.lineTo(width, state.h * 2.6);
      shape.endFill();
      shape.blendMode = PIXI.BLEND_MODES.ADD;
      beam.addChild(shape);
      beam.x = state.w * 0.5 + range(-state.w * 0.42, state.w * 0.42);
      beam.y = -state.h * 0.12;
      beam.baseX = beam.x;
      beam.baseRot = range(-0.3, 0.3);
      beam.rotation = beam.baseRot;
      beam.speed = range(0.06, 0.2) * (rand() > 0.5 ? 1 : -1);
      beam.phase = range(0, 6.28);
      els.beams.addChild(beam);
    }
    stage().addChild(els.beams);

    var starTex = makeGlowTexture(64, [
      [0, 'rgba(255,255,255,1)'],
      [0.3, 'rgba(255,190,240,0.5)'],
      [1, 'rgba(0,0,0,0)']
    ]);
    for (var s = 0; s < 46; s++) {
      var star = new PIXI.Sprite(starTex);
      star.anchor.set(0.5);
      star.fx = range(0, 1);
      star.fy = range(0, 0.94);
      star.size = range(4, 18);
      star.phase = range(0, 6.28);
      star.speed = range(0.6, 2.2);
      star.baseAlpha = range(0.25, 0.95);
      stage().addChild(star);
      els.stars.push(star);
    }

    var heartTex = makeHeartTexture(64);
    for (var hI = 0; hI < 7; hI++) {
      var heart = new PIXI.Sprite(heartTex);
      heart.anchor.set(0.5);
      heart.fx = range(0.08, 0.92);
      heart.fy = range(0.15, 0.95);
      heart.size = range(10, 26);
      heart.blendMode = PIXI.BLEND_MODES.ADD;
      heart.tint = rand() > 0.5 ? 0xff7ad4 : 0xffb3ec;
      heart.phase = range(0, 6.28);
      heart.speed = range(6, 16);
      heart.sway = range(6, 20);
      heart.baseAlpha = range(0.25, 0.7);
      stage().addChild(heart);
      els.hearts.push(heart);
    }

    els.city = new PIXI.Graphics();
    stage().addChild(els.city);
  }

  function buildCity() {
    var g = els.city;
    g.clear();
    var baseY = state.h * 0.995;
    var x = -20;
    while (x < state.w + 20) {
      var bw = range(18, 46);
      var bh = range(state.h * 0.05, state.h * 0.19);
      g.beginFill(0x12041f, 0.72);
      g.drawRect(x, baseY - bh, bw, bh);
      g.endFill();
      var lights = Math.floor(bh / 12);
      for (var l = 0; l < lights; l++) {
        if (rand() > 0.55) {
          g.beginFill(rand() > 0.5 ? 0xff6ad5 : 0x66e0ff, range(0.25, 0.8));
          g.drawRect(x + 4 + rand() * Math.max(1, bw - 9), baseY - bh + 6 + l * 12, 3, 4);
          g.endFill();
        }
      }
      x += bw + range(3, 12);
    }
  }

  function buildEqualizer() {
    var count = Math.max(14, Math.round(state.w / 14));
    var slot = state.w / count;
    var barWidth = Math.max(2, slot * 0.55);
    var colors = [0x2fe6ff, 0x5ac8ff, 0x9b5cff, 0xe14bff, 0xff3ec8, 0xff6ad5];
    for (var i = 0; i < count; i++) {
      var bar = new PIXI.Graphics();
      bar.beginFill(colors[Math.floor((i / count) * colors.length)], 0.95);
      bar.drawRoundedRect(0, 0, barWidth, 4, barWidth / 2);
      bar.endFill();
      bar.blendMode = PIXI.BLEND_MODES.ADD;
      bar.x = i * slot + (slot - barWidth) / 2;
      bar.y = state.h - 3;
      bar.pivot.set(0, 4);
      bar.seed = range(0, 6.28);
      bar.speed = range(6, 15);
      stage().addChild(bar);
      els.bars.push(bar);
    }
  }

  function buildOverlay() {
    els.flashOverlay = new PIXI.Graphics();
    els.flashOverlay.beginFill(0xffffff, 1);
    els.flashOverlay.drawRoundedRect(0, 0, state.w, state.h, radius());
    els.flashOverlay.endFill();
    els.flashOverlay.blendMode = PIXI.BLEND_MODES.ADD;
    els.flashOverlay.alpha = 0;
    stage().addChild(els.flashOverlay);

    els.dimmer = new PIXI.Graphics();
    stage().addChild(els.dimmer);

    els.captionPlate = new PIXI.Graphics();
    stage().addChild(els.captionPlate);

    els.caption = new PIXI.Text('', {
      fontFamily: 'sans-serif',
      fontSize: 15,
      fontWeight: '600',
      fill: 0xffffff,
      stroke: 0x2a0418,
      strokeThickness: 4,
      dropShadow: true,
      dropShadowColor: 0xff2fb0,
      dropShadowBlur: 10,
      dropShadowDistance: 0,
      align: 'center',
      wordWrap: true,
      wordWrapWidth: state.w * 0.78,
      lineHeight: 18
    });
    els.caption.anchor.set(0.5, 1);
    els.caption.alpha = 0;
    stage().addChild(els.caption);

    els.status = new PIXI.Text('', {
      fontFamily: 'sans-serif',
      fontSize: 12,
      fontWeight: '700',
      fill: 0xffffff,
      stroke: 0x2a0418,
      strokeThickness: 3
    });
    els.status.anchor.set(0, 0.5);
    els.status.alpha = 0;
    stage().addChild(els.status);

    els.frame = new PIXI.Graphics();
    stage().addChild(els.frame);
  }

  function drawFrame(glow) {
    var r = radius();
    var g = els.frame;
    g.clear();
    g.lineStyle({ width: Math.max(6, state.w * 0.022), color: 0xff2fb0, alpha: 0.18 * glow, alignment: 0 });
    g.drawRoundedRect(0, 0, state.w, state.h, r);
    g.lineStyle({ width: Math.max(1.5, state.w * 0.006), color: 0xffb3ec, alpha: 0.6 * glow, alignment: 0 });
    g.drawRoundedRect(0, 0, state.w, state.h, r);
    g.lineStyle(0);
  }

  function drawDimmer() {
    var g = els.dimmer;
    g.clear();
    g.beginFill(0x0a0118, clamp(state.dim, 0, 1) * 0.82);
    g.drawRoundedRect(0, 0, state.w, state.h, radius());
    g.endFill();
  }

  function placeStatic() {
    els.base.width = state.w;
    els.base.height = state.h;
    els.gradient.x = state.w * 0.5;
    els.gradient.y = state.h * 0.42;
    els.gradient.scale.set(Math.max(state.w, state.h) / 300);
    els.flashOverlay.clear();
    els.flashOverlay.beginFill(0xffffff, 1);
    els.flashOverlay.drawRoundedRect(0, 0, state.w, state.h, radius());
    els.flashOverlay.endFill();

    els.stars.forEach(function (star) {
      star.x = star.fx * state.w;
      star.y = star.fy * state.h;
    });
    els.hearts.forEach(function (heart) {
      heart.x = heart.fx * state.w;
      heart.y = heart.fy * state.h;
    });

    els.bars.forEach(function (bar, i) {
      var slot = state.w / els.bars.length;
      bar.x = i * slot + (slot - bar.width) / 2;
      bar.y = state.h - 3;
    });

    els.caption.style.fontSize = Math.max(12, Math.round(state.w * 0.045));
    els.caption.style.strokeThickness = Math.max(3, state.w * 0.012);
    els.caption.style.wordWrapWidth = state.w * 0.78;
    els.caption.style.lineHeight = Math.max(14, Math.round(state.w * 0.055));
    els.caption.x = state.w / 2;
    els.caption.y = state.h - Math.max(34, state.h * 0.17);
    els.status.style.fontSize = Math.max(10, Math.round(state.w * 0.032));
    els.status.x = Math.max(8, state.w * 0.04);
    els.status.y = Math.max(12, state.h * 0.05);

    buildCity();
    drawFrame(1);
    drawDimmer();
    fitModel();
  }

  function anchorX() {
    return state.w * 0.5;
  }

  function anchorY() {
    return state.h * 0.5;
  }

  function fitModel() {
    var model = state.model;
    els.holder.pivot.set(anchorX(), anchorY());
    els.holder.position.set(anchorX(), anchorY());
    if (!model) return;
    model.scale.set(1);
    model.position.set(0, 0);
    var nw = model.width;
    var nh = model.height;
    if (!nw || !nh) return;
    var s = Math.min((state.w * 1.2) / nw, (state.h * 1.12) / nh);
    state.modelScale = s;
    model.scale.set(s);
    model.x = anchorX() - nw * s / 2;
    model.y = anchorY() - nh * s / 2;
  }

  function readModelJson(json) {
    (json.Groups || []).forEach(function (group) {
      if (group.Name === 'EyeBlink' && group.Ids && group.Ids.length) state.groups.eyeBlink = group.Ids;
      if (group.Name === 'LipSync' && group.Ids && group.Ids.length) state.groups.lipSync = group.Ids;
    });
    state.motions = Object.keys(((json.FileReferences || {}).Motions) || {});
  }

  function loadModel(name) {
    state.modelName = name;
    var url = '../models/' + name + '/' + name + '.model3.json';
    return fetch(url)
      .then(function (r) {
        if (!r.ok) throw new Error('model3.json http ' + r.status);
        return r.json();
      })
      .then(function (json) {
        readModelJson(json);
        return PIXI.live2d.Live2DModel.from(url, { autoInteract: false });
      })
      .then(function (model) {
        if (state.model) {
          els.holder.removeChild(state.model);
          state.model.destroy();
        }
        state.model = model;
        els.holder.addChild(model);
        model.anchor.set(0, 0);
        model.internalModel.autoUpdate = true;
        if (state.motions.indexOf('Idle') >= 0 && model.internalModel.motionManager) {
          model.internalModel.motionManager.groups.idle = 'Idle';
          model.motion('Idle');
        }
        fitModel();
        state.ready = true;
        native('onReady', [name]);
      })
      .catch(function (e) {
        state.ready = false;
        native('onError', [String((e && e.message) || e)]);
      });
  }

  function playMotion(name) {
    if (!state.model || state.motions.indexOf(name) < 0) return;
    try {
      state.model.motion(name);
    } catch (e) {}
  }

  function setMood(mood) {
    if (!MOODS[mood]) mood = 'idle';
    if (state.mood === mood) return;
    state.from = currentMood();
    state.to = MOODS[mood];
    state.mood = mood;
    state.changedAt = state.time;
    state.statusText = STATUS[mood] || '';
    state.statusAlpha = state.statusText ? 1 : 0;
    els.status.text = state.statusText;
    if (mood === 'happy') playMotion('Nod');
    if (mood === 'surprised') playMotion('Shake');
    if (mood === 'speaking' && state.motions.indexOf('Nod') >= 0 && rand() > 0.6) playMotion('Nod');
  }

  function currentMood() {
    var t = clamp((state.time - state.changedAt) / 0.45, 0, 1);
    var e = t * t * (3 - 2 * t);
    var a = state.from;
    var b = state.to;
    var m = {};
    Object.keys(b).forEach(function (key) {
      m[key] = lerp(a[key] === undefined ? b[key] : a[key], b[key], e);
    });
    return m;
  }

  function updateBlink(dt) {
    var b = state.blink;
    if (b.state === 'wait') {
      b.next -= dt;
      if (b.next <= 0) {
        b.state = 'closing';
        b.t = 0;
        b.double = rand() > 0.75;
      }
    } else if (b.state === 'closing') {
      b.t += dt;
      if (b.t >= 0.09) { b.state = 'closed'; b.t = 0; }
    } else if (b.state === 'closed') {
      b.t += dt;
      if (b.t >= 0.07) { b.state = 'opening'; b.t = 0; }
    } else if (b.state === 'opening') {
      b.t += dt;
      if (b.t >= 0.13) {
        b.state = 'wait';
        b.t = 0;
        b.next = b.double ? 0.12 : range(2.2, 5.4);
        b.double = false;
      }
    }
    var value = 1;
    if (b.state === 'closing') value = 1 - clamp(b.t / 0.09, 0, 1);
    else if (b.state === 'closed') value = 0;
    else if (b.state === 'opening') value = clamp(b.t / 0.13, 0, 1);
    state.eyeClosed = 1 - value;
  }

  function updateEnvelope(dt) {
    state.time += dt;
    state.breath = (Math.sin(state.time * 1.15) + 1) / 2;
    var wobble =
      Math.sin(state.time * 13.7) * 0.34 +
      Math.sin(state.time * 7.1 + 1.3) * 0.28 +
      Math.sin(state.time * 21.3 + 0.7) * 0.16;
    var muted = 1 - state.eyeClosed * 0.55;
    var target = state.speaking
      ? clamp(0.5 + wobble, 0, 1) * 0.8 + state.levelSmooth * 0.4
      : 0;
    target = clamp(target, 0, 1) * muted;
    state.env = lerp(state.env, target, clamp(dt * 18, 0, 1));
    state.levelSmooth = lerp(state.levelSmooth, clamp(state.level, 0, 1), clamp(dt * 10, 0, 1));
    state.gaze.x = lerp(state.gaze.x, state.gaze.tx, clamp(dt * 6, 0, 1));
    state.gaze.y = lerp(state.gaze.y, state.gaze.ty, clamp(dt * 6, 0, 1));
    state.tilt = lerp(state.tilt, state.tiltTarget, clamp(dt * 8, 0, 1));
    state.dim = lerp(state.dim, state.dimTarget, clamp(dt * 6, 0, 1));
  }

  function applyParams() {
    var m = currentMood();
    setParam(['ParamAngleX', 'ParamHeadAngleX'], m.angleX + state.gaze.x * 6);
    setParam(['ParamAngleY', 'ParamHeadAngleY'], m.angleY + state.gaze.y * 4);
    setParam(['ParamAngleZ', 'ParamHeadAngleZ'], m.angleZ);
    setParam(['ParamBodyAngleX'], m.angleX * 0.4);
    setParam(['ParamBodyAngleZ'], m.angleZ * 0.4);
    setParam(['ParamEyeBallX'], clamp(m.eyeX + state.gaze.x * 0.6, -1, 1));
    setParam(['ParamEyeBallY'], clamp(m.eyeY + state.gaze.y * 0.5, -1, 1));
    setParam(['ParamBrowLY'], m.browL);
    setParam(['ParamBrowRY'], m.browR);
    setParam(['ParamBrowLForm'], m.browForm);
    setParam(['ParamBrowRForm'], m.browForm);
    setParam(['ParamMouthForm'], m.mouthForm);
    setParam(['ParamCheek'], state.env * 0.4);

    var open = state.mood === 'sleep' ? 0 : 1 - state.eyeClosed;
    state.groups.eyeBlink.forEach(function (id) {
      setParam(id, open);
    });
    setParam(['ParamEyeLSmile'], m.smile);
    setParam(['ParamEyeRSmile'], m.smile);

    var mouth = clamp(m.mouth * (1 - state.env * 0.3) + state.env * 0.95, 0, 1.25);
    state.groups.lipSync.forEach(function (id) {
      setParam(id, mouth);
    });

    setParam(['ParamBreath'], 1 + state.breath * 0.5);
    lockParams();
  }

  function updateVisuals(dt) {
    var m = currentMood();
    var t = state.time;

    els.gradient.tint = hue(0.88 + m.hue, 0.85, 0.5);
    els.gradient.alpha = (0.7 + state.env * 0.3) * (1 - state.dim * 0.8);
    els.gradient.scale.set(Math.max(state.w, state.h) / 300 * (1 + state.env * 0.03));

    els.beams.children.forEach(function (beam, i) {
      beam.rotation = beam.baseRot + Math.sin(t * beam.speed + beam.phase) * 0.06;
      beam.x = beam.baseX + Math.sin(t * beam.speed * 0.7 + beam.phase) * state.w * 0.03;
      beam.alpha = 1 - state.dim * 0.7;
    });

    els.stars.forEach(function (star) {
      var tw = 0.55 + 0.45 * Math.sin(t * star.speed + star.phase);
      star.alpha = star.baseAlpha * tw * (1 - state.dim * 0.6);
      star.scale.set((star.size / 32) * (0.85 + tw * 0.3));
    });

    els.hearts.forEach(function (heart) {
      heart.y -= heart.speed * dt;
      heart.x += Math.sin(t * 1.3 + heart.phase) * heart.sway * dt;
      if (heart.y < -20) {
        heart.y = state.h + 20;
        heart.fx = range(0.08, 0.92);
        heart.x = heart.fx * state.w;
      }
      var tw = 0.6 + 0.4 * Math.sin(t * 2 + heart.phase);
      heart.alpha = heart.baseAlpha * tw * (1 - state.dim * 0.6);
      heart.scale.set((heart.size / 32) * (0.9 + tw * 0.2));
    });

    els.bars.forEach(function (bar, i) {
      var wave = 0.5 + 0.5 * Math.sin(t * bar.speed + bar.seed + i * 0.4);
      var height = state.h * 0.17 * (0.1 + wave * 0.09 + state.env * (0.35 + wave * 0.65));
      bar.scale.y = Math.max(0.06, height * (1 + state.flash * 0.6));
      bar.alpha = 0.5 + state.env * 0.5;
    });

    var emphasis = 1 + state.breath * 0.005 + state.env * 0.004;
    els.holder.scale.set(emphasis);
    els.holder.rotation = state.tilt * 0.02;
    els.holder.pivot.set(anchorX(), anchorY());
    els.holder.position.set(anchorX(), anchorY());

    if (els.caption.text !== state.caption) els.caption.text = state.caption;
    state.captionAlpha = state.caption
      ? Math.min(1, state.captionAlpha + dt * 6)
      : Math.max(0, state.captionAlpha - dt * 2.5);
    els.caption.alpha = state.captionAlpha;
    els.caption.scale.set(1 + (1 - state.captionAlpha) * 0.04);
    var plate = els.captionPlate;
    plate.clear();
    if (state.captionAlpha > 0.02) {
      var bounds = els.caption.getLocalBounds();
      plate.beginFill(0x1a0326, 0.62 * state.captionAlpha);
      plate.drawRoundedRect(
        bounds.x - state.w * 0.04,
        bounds.y - state.h * 0.012,
        bounds.width + state.w * 0.08,
        bounds.height + state.h * 0.024,
        state.h * 0.03
      );
      plate.endFill();
    }

    els.status.alpha = state.statusAlpha;
    els.status.visible = state.statusAlpha > 0.01;
    els.flashOverlay.alpha = Math.max(0, state.flash - dt * 2.2);

    drawFrame(0.6 + state.env * 0.6 + state.flash);
    drawDimmer();
  }

  function tick(ticker) {
    var dt = clamp(num(ticker.deltaMS, 16.6) / 1000, 0.001, 0.05);
    updateEnvelope(dt);
    updateBlink(dt);
    applyParams();
    updateVisuals(dt);
  }

  function onPointer(x, y) {
    state.gaze.tx = clamp((x / state.w - 0.5) * 2, -1, 1);
    state.gaze.ty = clamp((y / state.h - 0.5) * 2, -1, 1);
    state.flash = Math.max(state.flash, 0.3);
    if (state.model && state.model.internalModel) {
      var group = null;
      try {
        group = state.model.internalModel.hitTest(x, y);
      } catch (e) {}
      if (group && state.motions.indexOf(group) >= 0) playMotion(group);
      else playMotion(rand() > 0.5 ? 'Nod' : 'Shake');
    }
    native('onTap', [x, y]);
  }

  function bindInput() {
    var surface = state.app.view;
    surface.style.touchAction = 'none';
    var send = function (e) {
      var rect = surface.getBoundingClientRect();
      var point = e.touches && e.touches[0] ? e.touches[0] : e;
      onPointer(point.clientX - rect.left, point.clientY - rect.top);
    };
    surface.addEventListener('touchstart', send, { passive: true });
    surface.addEventListener('touchmove', send, { passive: true });
    surface.addEventListener('mousedown', send);
  }

  function resize() {
    var w = window.innerWidth;
    var h = window.innerHeight;
    if (w < 8 || h < 8) return;
    state.app.renderer.resize(w, h);
    state.w = w;
    state.h = h;
    els.beams.pivot.set(w * 0.5, 0);
    els.beams.position.set(-w * 0.5, -h * 0.12);
    placeStatic();
  }

  function boot() {
    if (typeof PIXI === 'undefined' || !PIXI.live2d) {
      native('onError', ['PIXI/live2d runtime not loaded']);
      return;
    }
    state.w = window.innerWidth;
    state.h = window.innerHeight;

    state.app = new PIXI.Application({
      width: state.w,
      height: state.h,
      backgroundAlpha: 0,
      transparent: true,
      antialias: true,
      resolution: Math.min(2, window.devicePixelRatio || 1),
      autoDensity: true,
      powerPreference: 'high-performance'
    });
    document.body.appendChild(state.app.view);

    addBackdrop();
    els.holder = new PIXI.Container();
    stage().addChild(els.holder);
    buildEqualizer();
    buildOverlay();
    bindInput();
    state.app.ticker.add(tick);

    window.addEventListener('resize', resize);
    window.addEventListener('orientationchange', function () {
      setTimeout(resize, 120);
    });
    [60, 200, 500, 1000, 2000].forEach(function (ms) {
      setTimeout(resize, ms);
    });
    resize();

    loadModel(DEFAULT_MODEL);
  }

  window.vtuberApi = {
    mood: function (mood) { setMood(mood); },
    speak: function (on) {
      state.speaking = !!on;
      if (!on) state.level = 0;
    },
    level: function (v) { state.level = clamp(parseFloat(v) || 0, 0, 1); },
    caption: function (text) { state.caption = text ? String(text) : ''; },
    status: function (text) {
      state.statusText = text ? String(text) : '';
      state.statusAlpha = text ? 1 : 0;
      els.status.text = state.statusText;
    },
    gaze: function (x, y) {
      state.gaze.tx = clamp(parseFloat(x) || 0, -1, 1);
      state.gaze.ty = clamp(parseFloat(y) || 0, -1, 1);
    },
    tilt: function (deg) { state.tiltTarget = clamp(parseFloat(deg) || 0, -30, 30); },
    pulse: function () { state.flash = 0.6; },
    dim: function (v) { state.dimTarget = clamp(parseFloat(v) || 0, 0, 1); },
    model: function (name) {
      if (name && name !== state.modelName) {
        state.ready = false;
        loadModel(name);
      }
    },
    isReady: function () { return state.ready; }
  };

  if (document.readyState === 'loading') {
    window.addEventListener('DOMContentLoaded', boot);
  } else {
    setTimeout(boot, 0);
  }
})();
