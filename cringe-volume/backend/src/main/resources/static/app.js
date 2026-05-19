/* ====== Cringe Volume Player — Web Client JS ====== */

const API = window.location.origin + '/api';

/* ---------- state ---------- */
let currentTrack = null;
let audioEl = null;
let currentVolume = 50; // 0..100, меняется ТОЛЬКО через оплату

/* ---------- DOM refs ---------- */
const $tracks         = document.getElementById('trackList');
const $nowPlaying     = document.getElementById('nowPlaying');
const $volumeIn       = document.getElementById('volumeInput');
const $status         = document.getElementById('statusBar');
const $uploadFile     = document.getElementById('uploadFile');
const $playPauseBtn   = document.getElementById('playPauseBtn');
const $seekBar        = document.getElementById('seekBar');
const $currentTime    = document.getElementById('currentTime');
const $duration       = document.getElementById('duration');
const $currentVolume  = document.getElementById('currentVolume');

/* ====== tracks ====== */

async function loadTracks() {
    try {
        const res = await fetch(API + '/audio/tracks', { cache: 'no-store' });
        if (!res.ok) throw new Error(res.statusText);
        const tracks = await res.json();
        renderTracks(tracks);
        setStatus('Треков на сервере: ' + tracks.length);
    } catch (e) {
        $tracks.innerHTML = '<div class="empty-msg">Ошибка загрузки треков: ' + escHtml(e.message) + '</div>';
    }
}

function renderTracks(tracks) {
    if (!tracks.length) {
        $tracks.innerHTML = '<div class="empty-msg">Нет треков. Загрузите файл ниже.</div>';
        return;
    }
    $tracks.innerHTML = tracks.map(t => {
        const sizeMB = (t.sizeBytes / 1048576).toFixed(1);
        const active = currentTrack === t.filename ? ' active' : '';
        // dataset избегает проблем с кавычками в имени файла
        return `<div class="track-item${active}" data-filename="${escHtml(t.filename)}">
                    <span class="name">${escHtml(t.filename)}</span>
                    <span class="size">${sizeMB} MB</span>
                </div>`;
    }).join('');

    // делегирование клика
    $tracks.querySelectorAll('.track-item').forEach(item => {
        item.addEventListener('click', () => selectTrack(item.dataset.filename));
    });
}

function selectTrack(filename) {
    currentTrack = filename;
    // правильное URL-кодирование, чтобы кириллица/пробелы работали
    const url = API + '/audio/tracks/' + encodeURIComponent(filename) + '/stream';

    audioEl.src = url;
    audioEl.load();
    audioEl.volume = currentVolume / 100;

    $nowPlaying.textContent = filename;
    setStatus('Трек выбран: ' + filename);

    $playPauseBtn.disabled = false;
    $seekBar.disabled = false;
    $playPauseBtn.textContent = '▶';

    // обновить active в списке без перезапроса
    document.querySelectorAll('.track-item').forEach(el => {
        el.classList.toggle('active', el.dataset.filename === filename);
    });
}

/* ====== upload ====== */

async function uploadTrack() {
    const file = $uploadFile.files[0];
    if (!file) return;

    setStatus('Загрузка файла...');
    const form = new FormData();
    form.append('file', file);

    try {
        const res = await fetch(API + '/audio/upload', {
            method: 'POST', body: form
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || res.statusText);
        }
        const data = await res.json();
        setStatus('Файл загружен: ' + data.fileName);
        $uploadFile.value = '';
        await loadTracks();
        selectTrack(data.fileName);
    } catch (e) {
        setStatus('Ошибка загрузки: ' + e.message);
        alert('Ошибка загрузки: ' + e.message);
    }
}

/* ====== custom player controls ====== */

function togglePlay() {
    if (!audioEl.src) return;
    if (audioEl.paused) {
        audioEl.play().catch(err => {
            console.error('play error:', err);
            setStatus('Ошибка воспроизведения: ' + err.message);
        });
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
        setStatus('Громкость должна быть от 0 до 100');
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
        const res = await fetch(API + '/payments/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ targetVolume })
        });
        if (!res.ok) {
            const errBody = await res.text();
            throw new Error('HTTP ' + res.status + ': ' + errBody);
        }
        const payment = await res.json();

        $mTitle.textContent  = 'Оплата изменения громкости';
        $mDesc.textContent   = payment.description;
        $mPrice.textContent  = payment.totalAmount + ' ₽';
        $mDetail.textContent = 'Базовая: ' + payment.basePrice + ' ₽ + Комиссия: ' + payment.commission + ' ₽';
        $mBody.innerHTML     = '';
        // attribute escaping для url + token
        const safeUrl = escHtml(payment.payUrl);
        const safeToken = escHtml(payment.token);
        const safeVolume = String(targetVolume);
        $mActions.innerHTML  =
            '<button class="btn btn-accent btn-sm" data-payurl="' + safeUrl +
            '" data-token="' + safeToken + '" data-vol="' + safeVolume +
            '" onclick="payInBrowser(this.dataset.payurl, this.dataset.token, parseInt(this.dataset.vol))">Оплатить в браузере</button>' +
            '<button class="btn btn-outline btn-sm" onclick="closeModal()">Отмена</button>';
    } catch (e) {
        console.error('createPayment error:', e);
        $mTitle.textContent = 'Ошибка';
        $mBody.innerHTML    = '<p style="color:var(--accent)">' + escHtml(e.message) + '</p>';
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
        } catch (e) { /* keep polling */ }
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

    // применяем громкость локально — это ЕДИНСТВЕННОЕ место, где она меняется
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
    $mBody.innerHTML    = '<p style="color:var(--accent)">Создайте новый платёж</p>';
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
    audioEl = document.getElementById('audioPlayer');
    audioEl.volume = currentVolume / 100;

    // ЗАЩИТА: любая попытка изменить volume извне (например, через DevTools)
    // приведёт к откату к currentVolume. Свойство volume — не настоящий ключ объекта,
    // это accessor; перехватить его 100% надёжно нельзя, но мы сбрасываем при volumechange.
    audioEl.addEventListener('volumechange', () => {
        const expected = currentVolume / 100;
        // допускаем малую погрешность из-за float
        if (Math.abs(audioEl.volume - expected) > 0.01) {
            audioEl.volume = expected;
        }
    });

    // обновление прогресса
    audioEl.addEventListener('loadedmetadata', () => {
        $duration.textContent = formatTime(audioEl.duration);
        $seekBar.max = audioEl.duration || 0;
    });
    audioEl.addEventListener('timeupdate', () => {
        $currentTime.textContent = formatTime(audioEl.currentTime);
        $seekBar.value = audioEl.currentTime;
    });
    audioEl.addEventListener('play', () => { $playPauseBtn.textContent = '⏸'; });
    audioEl.addEventListener('pause', () => { $playPauseBtn.textContent = '▶'; });
    audioEl.addEventListener('ended', () => { $playPauseBtn.textContent = '▶'; });
    audioEl.addEventListener('error', (e) => {
        console.error('audio error:', e, audioEl.error);
        setStatus('Ошибка воспроизведения трека');
    });

    $uploadFile.addEventListener('change', uploadTrack);

    $currentVolume.textContent = currentVolume + '%';

    loadTracks();
    setStatus('Готов к работе');
});
