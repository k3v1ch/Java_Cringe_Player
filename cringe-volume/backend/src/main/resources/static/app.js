/* ====== Cringe Volume Player — Web Client JS ====== */

const API = window.location.origin + '/api';

/* ---------- state ---------- */
let currentTrack = null;
let audioEl = null;
let currentVolume = 50; // 0..100, меняется ТОЛЬКО через оплату

/* ---------- DOM refs ---------- */
let $tracks, $nowPlaying, $volumeIn, $status, $uploadFile,
    $playPauseBtn, $seekBar, $currentTime, $duration, $currentVolume;

/* ====== error parsing ====== */

/**
 * Парсит {error,code,message,details} из Response.
 * Возвращает Promise<{code,message,details,httpStatus}>.
 */
async function parseApiError(resp) {
    const status = resp.status;
    let code = 'HTTP_' + status;
    let message = 'Сервер вернул ' + status;
    let details = null;
    try {
        const data = await resp.json();
        if (data) {
            if (data.code)    code    = data.code;
            if (data.message) message = data.message;
            if (data.details) details = data.details;
        }
    } catch (e) {
        try {
            const text = await resp.text();
            if (text && text.length) details = text.slice(0, 200);
        } catch (_) {}
    }
    return { code, message, details, httpStatus: status };
}

/**
 * Готовит текст для показа пользователю.
 */
function formatApiError(err) {
    let msg = 'Ошибка ' + err.code + ': ' + err.message;
    if (err.details) msg += '\n(' + err.details + ')';
    return msg;
}

/**
 * Унифицированный fetch с обработкой ошибок.
 */
async function apiFetch(url, opts) {
    let resp;
    try {
        resp = await fetch(url, opts);
    } catch (netErr) {
        console.error('Network error:', netErr, 'URL:', url);
        const err = {
            code: 'SRV_001',
            message: 'Сервер недоступен. Проверьте подключение.',
            details: netErr.message,
            httpStatus: 0
        };
        throw err;
    }
    if (!resp.ok) {
        const err = await parseApiError(resp);
        console.error('API error:', err, 'URL:', url);
        throw err;
    }
    return resp;
}

/* ====== tracks ====== */

async function loadTracks() {
    const url = API + '/audio/tracks';
    console.log('loadTracks: fetching', url);
    try {
        const resp = await apiFetch(url, { cache: 'no-store' });
        const tracks = await resp.json();
        console.log('loadTracks: получено', tracks.length, 'треков:', tracks);
        renderTracks(tracks);
        setStatus('Треков на сервере: ' + tracks.length);
    } catch (err) {
        $tracks.innerHTML = '<div class="empty-msg">'
            + escHtml(formatApiError(err)) + '</div>';
        setStatus(formatApiError(err));
    }
}

function renderTracks(tracks) {
    if (!tracks || !tracks.length) {
        $tracks.innerHTML = '<div class="empty-msg">'
            + 'Нет треков в MUSIC_DIR. Загрузите файл ниже или положите .mp3/.wav на сервер.'
            + '</div>';
        return;
    }
    $tracks.innerHTML = tracks.map(t => {
        const sizeMB = (t.sizeBytes / 1048576).toFixed(1);
        const active = currentTrack === t.filename ? ' active' : '';
        return `<div class="track-item${active}" data-filename="${escHtml(t.filename)}">
                    <span class="name">${escHtml(t.filename)}</span>
                    <span class="size">${sizeMB} MB</span>
                </div>`;
    }).join('');

    $tracks.querySelectorAll('.track-item').forEach(item => {
        item.addEventListener('click', () => selectTrack(item.dataset.filename));
    });
}

function selectTrack(filename) {
    currentTrack = filename;
    const url = API + '/audio/tracks/' + encodeURIComponent(filename) + '/stream';
    console.log('selectTrack:', filename, '→', url);

    audioEl.src = url;
    audioEl.load();
    audioEl.volume = currentVolume / 100;

    $nowPlaying.textContent = filename;
    setStatus('Трек выбран: ' + filename);

    $playPauseBtn.disabled = false;
    $seekBar.disabled = false;
    $playPauseBtn.textContent = '▶';

    document.querySelectorAll('.track-item').forEach(el => {
        el.classList.toggle('active', el.dataset.filename === filename);
    });
}

/* ====== upload ====== */

async function uploadTrack() {
    const file = $uploadFile.files[0];
    if (!file) return;

    setStatus('Загрузка файла...');
    console.log('upload:', file.name, file.size, file.type);
    const form = new FormData();
    form.append('file', file);

    try {
        const resp = await apiFetch(API + '/audio/upload', {
            method: 'POST', body: form
        });
        const data = await resp.json();
        setStatus('Файл загружен: ' + data.fileName);
        $uploadFile.value = '';
        await loadTracks();
        selectTrack(data.fileName);
    } catch (err) {
        setStatus(formatApiError(err));
        alert(formatApiError(err));
    }
}

/* ====== custom player controls ====== */

function togglePlay() {
    console.log('togglePlay: src=', audioEl.src, 'paused=', audioEl.paused);
    if (!audioEl.src) {
        setStatus('Сначала выберите трек');
        return;
    }
    if (audioEl.paused) {
        // play() — синхронно в обработчике клика → автоплей разрешён
        const p = audioEl.play();
        if (p && typeof p.then === 'function') {
            p.catch(err => {
                console.error('audio.play() rejected:', err);
                setStatus('Ошибка воспроизведения: ' + (err.message || err.name));
                alert('Не удалось запустить воспроизведение\n' + (err.message || err.name));
            });
        }
    } else {
        audioEl.pause();
    }
}

function onSeek(value) {
    if (!isFinite(audioEl.duration)) return;
    audioEl.currentTime = parseFloat(value);
}

function formatTime(sec) {
    if (!isFinite(sec) || sec < 0) return '0:00';
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return m + ':' + String(s).padStart(2, '0');
}

/* ====== volume (cringe payment) ====== */

async function applyVolume() {
    const vol = parseInt($volumeIn.value, 10);
    if (isNaN(vol) || vol < 0 || vol > 100) {
        setStatus('[VOLUME_001] Громкость должна быть от 0 до 100');
        return;
    }
    openPaymentModal(vol);
}

/* ---------- payment modal ---------- */

async function openPaymentModal(targetVolume) {
    const $modal     = document.getElementById('paymentModal');
    const $mTitle    = document.getElementById('modalTitle');
    const $mDesc     = document.getElementById('modalDesc');
    const $mPrice    = document.getElementById('modalPrice');
    const $mDetail   = document.getElementById('modalPriceDetail');
    const $mBody     = document.getElementById('modalBody');
    const $mActions  = document.getElementById('modalActions');

    $mTitle.textContent = 'Создание платежа...';
    $mDesc.textContent  = '';
    $mPrice.textContent = '...';
    $mDetail.textContent = '';
    $mBody.innerHTML     = '<div class="spinner"></div>';
    $mActions.innerHTML  = '<button class="btn btn-outline btn-sm" onclick="closeModal()">Отмена</button>';
    $modal.classList.add('open');

    try {
        const resp = await apiFetch(API + '/payments/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ targetVolume })
        });
        const payment = await resp.json();
        console.log('payment created:', payment);

        $mTitle.textContent  = 'Оплата изменения громкости';
        $mDesc.textContent   = payment.description;
        $mPrice.textContent  = payment.totalAmount + ' ₽';
        $mDetail.textContent = 'Базовая: ' + payment.basePrice + ' ₽ + Комиссия: ' + payment.commission + ' ₽';
        $mBody.innerHTML     = '';

        const safeUrl = escHtml(payment.payUrl);
        const safeToken = escHtml(payment.token);
        const safeVolume = String(targetVolume);
        $mActions.innerHTML  =
            '<button class="btn btn-accent btn-sm" data-payurl="' + safeUrl +
            '" data-token="' + safeToken + '" data-vol="' + safeVolume +
            '" onclick="payInBrowser(this.dataset.payurl, this.dataset.token, parseInt(this.dataset.vol))">Оплатить в браузере</button>' +
            '<button class="btn btn-outline btn-sm" onclick="closeModal()">Отмена</button>';
    } catch (err) {
        $mTitle.textContent = 'Ошибка';
        $mBody.innerHTML    = '<p style="color:var(--accent);white-space:pre-line">'
            + escHtml(formatApiError(err)) + '</p>';
    }
}

function payInBrowser(payUrl, token, targetVolume) {
    window.open(payUrl, '_blank');

    const $mBody    = document.getElementById('modalBody');
    const $mActions = document.getElementById('modalActions');
    $mBody.innerHTML   = '<div class="spinner"></div><p style="font-size:13px;color:var(--muted);margin-top:8px">Ожидание оплаты...</p>';
    $mActions.innerHTML = '<button class="btn btn-outline btn-sm" onclick="closeModal()">Отмена</button>';

    pollPayment(token, targetVolume);
}

async function pollPayment(token, targetVolume) {
    const poll = async () => {
        try {
            const res = await fetch(API + '/payments/' + token + '/status');
            if (res.ok) {
                const data = await res.json();
                if (data.status === 'paid') {
                    onPaymentSuccess(targetVolume);
                    return;
                }
                if (data.status === 'expired') {
                    onPaymentExpired();
                    return;
                }
            }
        } catch (e) {
            console.warn('poll attempt failed:', e);
        }
        setTimeout(poll, 2000);
    };
    setTimeout(poll, 2000);
}

function onPaymentSuccess(targetVolume) {
    const $mTitle   = document.getElementById('modalTitle');
    const $mBody    = document.getElementById('modalBody');
    const $mActions = document.getElementById('modalActions');
    const $mPrice   = document.getElementById('modalPrice');

    $mTitle.textContent = 'Оплата прошла!';
    $mPrice.textContent = '✅';
    $mBody.innerHTML    = '<p style="color:var(--green);font-weight:700">Громкость изменена</p>';
    $mActions.innerHTML = '<button class="btn btn-accent btn-sm" onclick="closeModal()">Закрыть</button>';

    applyVolumeNow(targetVolume);
    setTimeout(closeModal, 2000);
}

function applyVolumeNow(vol) {
    currentVolume = Math.max(0, Math.min(100, vol));
    audioEl.volume = currentVolume / 100;
    $currentVolume.textContent = currentVolume + '%';
    setStatus('Громкость: ' + currentVolume + '%');
}

function onPaymentExpired() {
    const $mTitle   = document.getElementById('modalTitle');
    const $mBody    = document.getElementById('modalBody');
    const $mActions = document.getElementById('modalActions');

    $mTitle.textContent = 'Время истекло';
    $mBody.innerHTML    = '<p style="color:var(--accent)">[PAY_002] Создайте новый платёж</p>';
    $mActions.innerHTML = '<button class="btn btn-outline btn-sm" onclick="closeModal()">Закрыть</button>';
}

function closeModal() {
    document.getElementById('paymentModal').classList.remove('open');
}

/* ====== helpers ====== */

function setStatus(msg) {
    $status.textContent = msg;
}

function escHtml(s) {
    if (s == null) return '';
    const d = document.createElement('div');
    d.textContent = String(s);
    return d.innerHTML;
}

/* ====== init ====== */
document.addEventListener('DOMContentLoaded', () => {
    // DOM-refs инициализируем тут, чтобы они точно существовали
    $tracks         = document.getElementById('trackList');
    $nowPlaying     = document.getElementById('nowPlaying');
    $volumeIn       = document.getElementById('volumeInput');
    $status         = document.getElementById('statusBar');
    $uploadFile     = document.getElementById('uploadFile');
    $playPauseBtn   = document.getElementById('playPauseBtn');
    $seekBar        = document.getElementById('seekBar');
    $currentTime    = document.getElementById('currentTime');
    $duration       = document.getElementById('duration');
    $currentVolume  = document.getElementById('currentVolume');
    audioEl         = document.getElementById('audioPlayer');

    if (!audioEl) {
        console.error('audioPlayer element not found in DOM');
        return;
    }

    audioEl.volume = currentVolume / 100;

    // Защита от обхода: при попытке изменить volume извне — откатываем
    audioEl.addEventListener('volumechange', () => {
        const expected = currentVolume / 100;
        if (Math.abs(audioEl.volume - expected) > 0.01) {
            audioEl.volume = expected;
        }
    });

    audioEl.addEventListener('loadedmetadata', () => {
        console.log('audio: loadedmetadata, duration=', audioEl.duration);
        $duration.textContent = formatTime(audioEl.duration);
        $seekBar.max = audioEl.duration || 0;
    });
    audioEl.addEventListener('timeupdate', () => {
        $currentTime.textContent = formatTime(audioEl.currentTime);
        $seekBar.value = audioEl.currentTime;
    });
    audioEl.addEventListener('play',  () => { $playPauseBtn.textContent = '⏸'; });
    audioEl.addEventListener('pause', () => { $playPauseBtn.textContent = '▶'; });
    audioEl.addEventListener('ended', () => { $playPauseBtn.textContent = '▶'; });
    audioEl.addEventListener('error', (e) => {
        const err = audioEl.error;
        const code = err ? err.code : '?';
        const map = { 1: 'ABORTED', 2: 'NETWORK', 3: 'DECODE', 4: 'SRC_NOT_SUPPORTED' };
        console.error('audio error code=' + code + ' (' + (map[code] || 'UNKNOWN')
                + ')  src=' + audioEl.src, e);
        setStatus('[AUDIO_005] Ошибка воспроизведения (' + (map[code] || code)
                + '). См. консоль (F12).');
    });

    $uploadFile.addEventListener('change', uploadTrack);
    $currentVolume.textContent = currentVolume + '%';

    loadTracks();
    setStatus('Готов к работе');
});
