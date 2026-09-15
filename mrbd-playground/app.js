/**
 * MRBD Playground — a portfolio-style demo of what a Meta Ray-Ban Display
 * web app can do: D-pad focus navigation, EMG pinch/drag, IMU sensors,
 * geolocation, on-glasses text composer, Canvas rendering, localStorage,
 * network requests and offline support.
 *
 * Architecture follows the official template from the Meta Wearables Web App
 * AI Toolkit (BSD license): https://github.com/facebookincubator/meta-wearables-webapp
 */

(function () {
  'use strict';

  // ==================== CONFIG ====================
  var CONFIG = {
    appName: 'MRBD Playground',
    storageKey: 'mrbd_playground',
    api: {
      // Open-Meteo: free, no API key, CORS-friendly
      weatherUrl: 'https://api.open-meteo.com/v1/forecast' +
        '?latitude=31.2304&longitude=121.4737&current_weather=true&timezone=Asia%2FShanghai',
      cacheDuration: 5 * 60 * 1000,
    },
  };

  // ==================== STATE ====================
  var state = {
    currentScreen: 'home',
    screenHistory: [],
    data: { counter: 0, note: '', progress: 30 },
    cache: {},
  };

  // ==================== DOM REFS ====================
  var screens = {};

  function collectScreens() {
    document.querySelectorAll('.screen').forEach(function (s) {
      if (s.id) screens[s.id] = s;
    });
  }

  function $(id) { return document.getElementById(id); }

  // ==================== NAVIGATION ====================
  function navigateTo(screenId, options) {
    options = options || {};
    var addToHistory = options.addToHistory !== false;

    onScreenExit(state.currentScreen);

    if (addToHistory && state.currentScreen) {
      state.screenHistory.push(state.currentScreen);
    }

    Object.keys(screens).forEach(function (id) { screens[id].classList.add('hidden'); });
    if (screens[screenId]) {
      screens[screenId].classList.remove('hidden');
      state.currentScreen = screenId;
      onScreenEnter(screenId);
      focusFirst(screens[screenId]);
    }
  }

  function navigateBack() {
    if (state.screenHistory.length > 0) {
      navigateTo(state.screenHistory.pop(), { addToHistory: false });
    }
  }

  // ==================== FOCUS MANAGEMENT ====================
  function focusFirst(container) {
    var el = container.querySelector('.focusable:not([disabled]):not(.hidden)');
    if (el) el.focus();
  }

  function moveFocus(direction) {
    var container = screens[state.currentScreen];
    if (!container) return;

    var focusables = Array.prototype.slice.call(
      container.querySelectorAll('.focusable:not([disabled]):not(.hidden)')
    );
    if (focusables.length === 0) return;

    var current = document.activeElement;
    var idx = focusables.indexOf(current);

    if (idx === -1) { focusFirst(container); return; }

    var nextIdx;
    if (direction === 'up' || direction === 'left') {
      nextIdx = idx > 0 ? idx - 1 : focusables.length - 1;
    } else {
      nextIdx = idx < focusables.length - 1 ? idx + 1 : 0;
    }
    focusables[nextIdx].focus();
    focusables[nextIdx].scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }

  // ==================== API LAYER (cache + loading/error UI) ====================
  function apiGet(url, options) {
    options = options || {};
    var cacheKey = options.cacheKey || url;

    if (!options.noCache && state.cache[cacheKey]) {
      var cached = state.cache[cacheKey];
      if (Date.now() - cached.timestamp < CONFIG.api.cacheDuration) {
        return Promise.resolve({ data: cached.data, fromCache: true });
      }
    }

    return fetch(url)
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        state.cache[cacheKey] = { data: data, timestamp: Date.now() };
        return { data: data, fromCache: false };
      });
  }

  // ==================== UI HELPERS ====================
  function showToast(message, type) {
    var toast = $('toast');
    if (!toast) {
      toast = document.createElement('div');
      toast.id = 'toast';
      toast.className = 'toast';
      document.body.appendChild(toast);
    }
    toast.textContent = message;
    toast.className = 'toast' + (type ? ' ' + type : '');
    void toast.offsetHeight; // restart transition
    toast.classList.add('visible');
    clearTimeout(showToast._t);
    showToast._t = setTimeout(function () { toast.classList.remove('visible'); }, 2500);
  }

  // ==================== DATA PERSISTENCE ====================
  function loadData() {
    try {
      var saved = localStorage.getItem(CONFIG.storageKey);
      if (saved) Object.assign(state.data, JSON.parse(saved));
    } catch (e) { /* storage unavailable — keep defaults */ }
  }

  function saveData() {
    try {
      localStorage.setItem(CONFIG.storageKey, JSON.stringify(state.data));
    } catch (e) { /* best effort */ }
  }

  // ==================== DEMO 1: UI COMPONENTS ====================
  function uiSelectTab(tab, element) {
    var panels = { cards: 'ui-cards', feedback: 'ui-feedback', loading: 'ui-loading' };
    Object.keys(panels).forEach(function (k) {
      $(panels[k]).classList.toggle('hidden', k !== tab);
    });
    var tabs = screens.ui.querySelectorAll('.tab-item');
    Array.prototype.forEach.call(tabs, function (t) { t.classList.remove('active'); });
    if (element) element.classList.add('active');
  }

  // ==================== DEMO 2: D-PAD / KEY LOG ====================
  function logKey(key) {
    var el = $('key-log');
    if (el && state.currentScreen === 'dpad') {
      el.textContent = 'keydown: "' + key + '"  @' + new Date().toLocaleTimeString();
      var meta = $('dpad-last');
      if (meta) meta.textContent = key;
    }
  }

  // ==================== DEMO 3: DRAW (continuous drag) ====================
  var draw = { ctx: null, last: null, brush: 6 };

  function initDraw() {
    var canvas = $('draw-canvas');
    draw.ctx = canvas.getContext('2d');
    draw.ctx.lineCap = 'round';
    draw.ctx.lineJoin = 'round';

    canvas.addEventListener('pointerdown', function (e) {
      draw.last = canvasPoint(canvas, e);
      canvas.setPointerCapture(e.pointerId);
    });
    canvas.addEventListener('pointermove', function (e) {
      if (!draw.last) return;
      var p = canvasPoint(canvas, e);
      draw.ctx.strokeStyle = '#00d4ff';
      draw.ctx.lineWidth = draw.brush;
      draw.ctx.beginPath();
      draw.ctx.moveTo(draw.last.x, draw.last.y);
      draw.ctx.lineTo(p.x, p.y);
      draw.ctx.stroke();
      draw.last = p;
    });
    canvas.addEventListener('pointerup', function () { draw.last = null; });
    canvas.addEventListener('pointercancel', function () { draw.last = null; });

    // Brush slider (drag anywhere on the track)
    var track = $('brush-slider');
    var sliding = false;
    function setBrush(clientX) {
      var rect = track.getBoundingClientRect();
      var pct = Math.max(0.05, Math.min(1, (clientX - rect.left) / rect.width));
      draw.brush = Math.round(pct * 20);
      $('brush-fill').style.width = (pct * 100) + '%';
      $('brush-size').textContent = draw.brush + 'px';
    }
    track.addEventListener('pointerdown', function (e) {
      sliding = true;
      track.setPointerCapture(e.pointerId);
      setBrush(e.clientX);
    });
    track.addEventListener('pointermove', function (e) { if (sliding) setBrush(e.clientX); });
    track.addEventListener('pointerup', function () { sliding = false; });
  }

  function canvasPoint(canvas, e) {
    var rect = canvas.getBoundingClientRect();
    return { x: e.clientX - rect.left, y: e.clientY - rect.top };
  }

  function clearDrawing() {
    var canvas = $('draw-canvas');
    draw.ctx.clearRect(0, 0, canvas.width, canvas.height);
    showToast('画板已清空', 'success');
  }

  // ==================== DEMO 4: IMU SENSORS ====================
  var sensors = {
    listening: false,
    gotRealData: false,
    demoTimer: null,
    demoAngle: 0,
  };

  function onDeviceOrientation(e) {
    if (e.alpha === null && e.beta === null) return;
    sensors.gotRealData = true;
    renderOrientation(e.alpha || 0, e.beta || 0, e.gamma || 0);
  }

  function onDeviceMotion(e) {
    var a = e.accelerationIncludingGravity;
    var r = e.rotationRate;
    if (a && a.x !== null) {
      sensors.gotRealData = true;
      var mag = Math.sqrt(a.x * a.x + a.y * a.y + a.z * a.z);
      $('v-accel').textContent = mag.toFixed(2) + ' m/s²';
    }
    if (r && r.alpha !== null) {
      var w = Math.sqrt(r.alpha * r.alpha + r.beta * r.beta + r.gamma * r.gamma);
      $('v-gyro').textContent = w.toFixed(1) + ' °/s';
    }
  }

  function renderOrientation(alpha, beta, gamma) {
    $('v-heading').textContent = Math.round(alpha) + '°';
    $('v-tilt').textContent = Math.round(beta) + '°';
    $('compass-needle').style.transform = 'rotate(' + (-alpha) + 'deg)';
    var bx = Math.max(-48, Math.min(48, gamma * 2));
    var by = Math.max(-48, Math.min(48, beta * 2));
    $('level-bubble').style.transform =
      'translate(calc(-50% + ' + bx + 'px), calc(-50% + ' + by + 'px))';
  }

  function startSensors() {
    var chain = Promise.resolve('granted');
    if (typeof DeviceOrientationEvent !== 'undefined' &&
        typeof DeviceOrientationEvent.requestPermission === 'function') {
      chain = DeviceOrientationEvent.requestPermission();
    }
    chain.then(function (perm) {
      if (perm !== 'granted') {
        showToast('传感器权限被拒绝', 'error');
        return;
      }
      window.addEventListener('deviceorientation', onDeviceOrientation);
      window.addEventListener('devicemotion', onDeviceMotion);
      sensors.listening = true;
      sensors.gotRealData = false;
      $('sensor-status').textContent = '监听中…';
      showToast('传感器已启动', 'success');

      // Fallback: no real events within 1.5s => synthetic demo data
      setTimeout(function () {
        if (sensors.listening && !sensors.gotRealData) {
          $('sensor-status').textContent = '演示模式（无真实传感器）';
          sensors.demoTimer = setInterval(function () {
            sensors.demoAngle = (sensors.demoAngle + 3) % 360;
            var t = sensors.demoAngle * Math.PI / 180;
            renderOrientation(sensors.demoAngle, Math.sin(t) * 20, Math.cos(t) * 15);
            $('v-accel').textContent = (9.81 + Math.sin(t * 2) * 0.4).toFixed(2) + ' m/s²';
            $('v-gyro').textContent = (Math.abs(Math.cos(t)) * 40).toFixed(1) + ' °/s';
          }, 100);
        } else if (sensors.listening) {
          $('sensor-status').textContent = '实时数据';
        }
      }, 1500);
    }).catch(function () {
      showToast('传感器权限请求失败', 'error');
    });
  }

  function stopSensors() {
    if (!sensors.listening && !sensors.demoTimer) return;
    window.removeEventListener('deviceorientation', onDeviceOrientation);
    window.removeEventListener('devicemotion', onDeviceMotion);
    if (sensors.demoTimer) { clearInterval(sensors.demoTimer); sensors.demoTimer = null; }
    sensors.listening = false;
    var st = $('sensor-status');
    if (st) st.textContent = '已停止';
  }

  // ==================== DEMO 5: GEOLOCATION ====================
  function getLocation() {
    if (!('geolocation' in navigator)) {
      $('loc-status').textContent = '不支持';
      showToast('此环境不支持 Geolocation', 'error');
      return;
    }
    $('loc-status').textContent = '定位中…';
    navigator.geolocation.getCurrentPosition(
      function (pos) {
        $('loc-lat').textContent = pos.coords.latitude.toFixed(5);
        $('loc-lon').textContent = pos.coords.longitude.toFixed(5);
        $('loc-acc').textContent = Math.round(pos.coords.accuracy) + ' m';
        $('loc-status').textContent = '成功';
        showToast('定位成功', 'success');
      },
      function (err) {
        // 1 = PERMISSION_DENIED, 2 = POSITION_UNAVAILABLE, 3 = TIMEOUT
        $('loc-status').textContent = '失败 (code ' + err.code + ')';
        showToast('定位失败: ' + err.message, 'error');
      },
      { timeout: 15000 }
    );
  }

  // ==================== DEMO 6: TEXT INPUT ====================
  var SEARCH_POOL = [
    { icon: '☀', title: '天气 Weather', meta: 'sunny / cloudy / rain' },
    { icon: '♫', title: '音乐 Music', meta: 'play / pause / next' },
    { icon: '✈', title: '航班 Flights', meta: 'MU583 / CA980' },
    { icon: '☕', title: '咖啡 Coffee', meta: 'latte / americano' },
    { icon: '⚽', title: '比分 Scores', meta: 'live match scores' },
    { icon: '❤', title: '健康 Health', meta: 'steps / heart rate' },
  ];

  function renderSearchResults(query) {
    var box = $('search-results');
    var q = (query || '').trim().toLowerCase();
    var items = SEARCH_POOL.filter(function (it) {
      return !q || (it.title + ' ' + it.meta).toLowerCase().indexOf(q) !== -1;
    });
    box.innerHTML = '';
    if (items.length === 0) {
      box.innerHTML = '<div class="hint-text">没有匹配的结果</div>';
      return;
    }
    items.forEach(function (it) {
      var html = '<button class="list-item focusable" data-action="search-pick" data-title="' +
        it.title + '"><span class="list-item-icon">' + it.icon +
        '</span><div class="list-item-content"><div class="list-item-title">' + it.title +
        '</div><div class="list-item-meta">' + it.meta + '</div></div></button>';
      box.insertAdjacentHTML('beforeend', html);
    });
  }

  function initTextInput() {
    var search = $('search-box');
    // Composer commits fire standard 'input' events — read value there, never keydown
    search.addEventListener('input', function () { renderSearchResults(search.value); });

    var note = $('note-box');
    note.addEventListener('input', function () {
      state.data.note = note.value;
      saveData();
    });
  }

  // ==================== DEMO 7: CANVAS / STARFIELD + FPS ====================
  var stars = { running: false, raf: null, field: [], frames: 0, lastFps: 0 };

  function initStars() {
    var canvas = $('star-canvas');
    for (var i = 0; i < 140; i++) {
      stars.field.push({
        x: (Math.random() - 0.5) * canvas.width,
        y: (Math.random() - 0.5) * canvas.height,
        z: Math.random() * canvas.width,
      });
    }
  }

  function starsLoop() {
    if (!stars.running) return;
    var canvas = $('star-canvas');
    var ctx = canvas.getContext('2d');
    var w = canvas.width, h = canvas.height, cx = w / 2, cy = h / 2;

    ctx.fillStyle = '#0a0a0f';
    ctx.fillRect(0, 0, w, h);

    for (var i = 0; i < stars.field.length; i++) {
      var s = stars.field[i];
      s.z -= 4;
      if (s.z <= 1) {
        s.x = (Math.random() - 0.5) * w;
        s.y = (Math.random() - 0.5) * h;
        s.z = w;
      }
      var k = 128 / s.z;
      var px = s.x * k + cx;
      var py = s.y * k + cy;
      if (px < 0 || px >= w || py < 0 || py >= h) continue;
      var size = Math.max(0.5, (1 - s.z / w) * 3.5);
      var alpha = Math.min(1, 1.2 - s.z / w);
      ctx.fillStyle = 'rgba(0, 212, 255, ' + alpha.toFixed(2) + ')';
      ctx.fillRect(px, py, size, size);
    }

    stars.frames++;
    var now = performance.now();
    if (now - stars.lastFps >= 1000) {
      $('fps-meter').textContent = stars.frames + ' fps';
      stars.frames = 0;
      stars.lastFps = now;
    }
    stars.raf = requestAnimationFrame(starsLoop);
  }

  function startStars() {
    if (stars.running) return;
    stars.running = true;
    stars.lastFps = performance.now();
    stars.frames = 0;
    starsLoop();
  }

  function stopStars() {
    stars.running = false;
    if (stars.raf) { cancelAnimationFrame(stars.raf); stars.raf = null; }
    var meter = $('fps-meter');
    if (meter) meter.textContent = '-- fps';
  }

  // ==================== DEMO 8: STORAGE ====================
  function renderStorage() {
    $('counter-value').textContent = state.data.counter;
    $('saved-note').textContent = state.data.note || '（暂无）';
  }

  function bumpCounter(delta) {
    state.data.counter = delta === 0 ? 0 : state.data.counter + delta;
    saveData();
    renderStorage();
  }

  // ==================== DEMO 9: NETWORK ====================
  function fetchWeather(force) {
    $('net-loading').classList.remove('hidden');
    $('net-error').classList.add('hidden');
    $('net-result').classList.add('hidden');

    apiGet(CONFIG.api.weatherUrl, { cacheKey: 'weather', noCache: !!force })
      .then(function (res) {
        var wx = res.data.current_weather;
        $('wx-city').textContent = '上海';
        $('wx-temp').textContent = wx.temperature + '°C';
        $('wx-wind').textContent = wx.windspeed + ' km/h';
        $('wx-time').textContent = (wx.time || '').replace('T', ' ');
        $('net-loading').classList.add('hidden');
        $('net-result').classList.remove('hidden');
        $('net-hint').textContent = res.fromCache
          ? '⚡ 来自缓存（5 分钟内不重复请求）'
          : '☁ 来自网络（已写入缓存）';
      })
      .catch(function (err) {
        $('net-loading').classList.add('hidden');
        $('net-error').classList.remove('hidden');
        $('net-error-msg').textContent = '请求失败：' + err.message +
          '（离线或网络受限时属正常现象）';
      });
  }

  // ==================== DEMO 10: ABOUT / RUNTIME ====================
  function renderAbout() {
    var gl = false;
    try {
      var c = document.createElement('canvas');
      gl = !!(c.getContext('webgl') || c.getContext('experimental-webgl'));
    } catch (e) { gl = false; }

    var ls = false;
    try { localStorage.setItem('__t', '1'); localStorage.removeItem('__t'); ls = true; } catch (e) {}

    var rows = [
      ['视口 Viewport', window.innerWidth + ' × ' + window.innerHeight],
      ['像素比 DPR', String(window.devicePixelRatio || 1)],
      ['WebGL', gl ? '✓ 支持' : '✗ 不支持'],
      ['localStorage', ls ? '✓ 可用' : '✗ 不可用'],
      ['Service Worker', 'serviceWorker' in navigator ? '✓ 支持' : '✗ 不支持'],
      ['Geolocation', 'geolocation' in navigator ? '✓ 支持' : '✗ 不支持'],
      ['在线状态', navigator.onLine ? '✓ 在线' : '✗ 离线'],
      ['UA', navigator.userAgent],
    ];
    var box = $('about-list');
    box.innerHTML = '';
    rows.forEach(function (r) {
      box.insertAdjacentHTML('beforeend',
        '<div class="card"><div class="card-subtitle">' + r[0] +
        '</div><div class="note-text">' + r[1] + '</div></div>');
    });
  }

  // ==================== SCREEN LIFECYCLE ====================
  function onScreenEnter(screenId) {
    switch (screenId) {
      case 'storage': renderStorage(); break;
      case 'about': renderAbout(); break;
      case 'textinput':
        $('note-box').value = state.data.note;
        renderSearchResults('');
        break;
    }
  }

  function onScreenExit(screenId) {
    // Performance rule: stop loops & sensors the moment their screen goes away
    if (screenId === 'sensors') stopSensors();
    if (screenId === 'canvas') stopStars();
  }

  // ==================== ACTION HANDLING ====================
  function handleAction(action, element) {
    switch (action) {
      case 'back': navigateBack(); break;
      case 'goto': navigateTo(element.dataset.target); break;

      // UI demos
      case 'ui-tab': uiSelectTab(element.dataset.tab, element); break;
      case 'demo-toast': showToast('这是一条 Toast 通知 ✨', 'success'); break;
      case 'demo-progress':
        state.data.progress = state.data.progress >= 100 ? 0 : state.data.progress + 10;
        $('ui-progress').style.width = state.data.progress + '%';
        break;
      case 'card-tap': showToast('卡片被 Pinch 激活了', 'success'); break;

      // D-pad demo
      case 'dpad-tap': showToast('激活了格子 ' + element.dataset.idx, 'success'); break;

      // Draw
      case 'draw-clear': clearDrawing(); break;

      // Sensors
      case 'sensors-start': startSensors(); break;
      case 'sensors-stop': stopSensors(); showToast('传感器已停止'); break;

      // Location
      case 'loc-get': getLocation(); break;

      // Text input
      case 'search-pick': showToast('选择了：' + element.dataset.title, 'success'); break;

      // Canvas
      case 'stars-start': startStars(); break;
      case 'stars-stop': stopStars(); break;

      // Storage
      case 'counter-inc': bumpCounter(1); break;
      case 'counter-dec': bumpCounter(-1); break;
      case 'counter-reset': bumpCounter(0); break;

      // Network
      case 'net-fetch': fetchWeather(false); break;
      case 'net-refresh': fetchWeather(true); break;
    }
  }

  // ==================== EVENT LISTENERS ====================
  function setupEvents() {
    document.addEventListener('click', function (e) {
      var actionEl = e.target.closest('[data-action]');
      if (actionEl) handleAction(actionEl.dataset.action, actionEl);
    });

    document.addEventListener('keydown', function (e) {
      logKey(e.key);

      var isInput = document.activeElement &&
        (document.activeElement.tagName === 'INPUT' ||
         document.activeElement.tagName === 'TEXTAREA');
      if (isInput && ['Escape', 'Enter', 'ArrowUp', 'ArrowDown'].indexOf(e.key) === -1) {
        return; // let the field receive text; composer commits arrive via 'input'
      }

      switch (e.key) {
        case 'ArrowUp': moveFocus('up'); e.preventDefault(); break;
        case 'ArrowDown': moveFocus('down'); e.preventDefault(); break;
        case 'ArrowLeft': moveFocus('left'); e.preventDefault(); break;
        case 'ArrowRight': moveFocus('right'); e.preventDefault(); break;
        case 'Enter':
          if (!isInput && document.activeElement &&
              document.activeElement.classList.contains('focusable')) {
            document.activeElement.click();
          }
          e.preventDefault();
          break;
        case 'Escape': navigateBack(); e.preventDefault(); break;
      }
    });

    // Online/offline indicator on home header
    function renderOnline() {
      var el = $('net-status');
      if (el) el.textContent = navigator.onLine ? '在线' : '离线';
    }
    window.addEventListener('online', renderOnline);
    window.addEventListener('offline', renderOnline);
    renderOnline();
  }

  // ==================== INITIALIZATION ====================
  function init() {
    collectScreens();
    setupEvents();
    loadData();
    initDraw();
    initStars();
    initTextInput();

    // Offline support: cache the app shell
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('sw.js').catch(function () { /* http/file: dev mode */ });
    }

    setTimeout(function () {
      navigateTo('home', { addToHistory: false });
    }, 100);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
