/* ====== Cringe Volume Player — Web Client JS ====== */

const API = window.location.origin + '/api';

/* ---------- state ---------- */
let currentTrack = null;
let audioEl = null;

/* ---------- DOM refs ---------- */
const $tracks     = document.getElementById('trackList');
const $nowPlaying = document.getElementById('nowPlaying');
const $volumeIn   = document.getElementById('volumeInput');
const $status     = document.getElementById('statusBar');
const $uploadFile = document.getElementById('uploadFile');

/* ====== tracks ====== */

async function loadTracks() {
    try {
        const res = await fetch(API + '/audio/tracks');
        if (!res.ok) throw new Error(res.statusText);
        const tracks = await res.json();
        renderTracks(tracks);
    } catch (e) {
        $tracks.innerHTML = '<div class="empty-msg">Ошибка загрузки треков</div>';
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
        return `<div class="track-item${active}" onclick="selectTrack('${escHtml(t.filename)}')">
                    <span class="name">${escHtml(t.filename)}</span>
                    <span class="size">${sizeMB} MB</span>
                </div>`;
    }).join('');
}

function selectTrack(filename) {
    currentTrack = filename;
    const url = API + '/audio/tracks/' + encodeURIComponent(filename) + '/stream';

    if (!audioEl) {
        audioEl = document.getElementById('audioPlayer');
    }
    audioEl.src = url;
    audioEl.load();

    $nowPlaying.textContent = filename;
    setStatus('Трек выбран: ' + filename);
    loadTracks();  // re-render active state
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
        loadTracks();
        selectTrack(data.fileName);
    } catch (e) {
        setStatus('Ошибка загрузки: ' + e.message);
    }
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
        if (!res.ok) throw new Error('Ошибка создания платежа');
        const payment = await res.json();

        $mTitle.textContent  = 'Оплата изменения громкости';
        $mDesc.textContent   = payment.description;
        $mPrice.textContent  = payment.totalAmount + ' ₽';
        $mDetail.textContent = 'Базовая: ' + payment.basePrice + ' ₽ + Комиссия: ' + payment.commission + ' ₽';
        $mBody.innerHTML     = '';
        $mActions.innerHTML  =
            '<button class="btn btn-accent btn-sm" onclick="payInBrowser(\'' + payment.payUrl + '\',\'' + payment.token + '\')">Оплатить в браузере</button>' +
            '<button class="btn btn-outline btn-sm" onclick="closeModal()">Отмена</button>';
    } catch (e) {
        $mTitle.textContent = 'Ошибка';
        $mBody.innerHTML    = '<p style="color:var(--accent)">' + escHtml(e.message) + '</p>';
    }
}

function payInBrowser(payUrl, token) {
    window.open(payUrl, '_blank');

    const $mBody    = document.getElementById('modalBody');
    const $mActions = document.getElementById('modalActions');
    $mBody.innerHTML   = '<div class="spinner"></div><p style="font-size:13px;color:var(--muted);margin-top:8px">Ожидание оплаты...</p>';
    $mActions.innerHTML = '<button class="btn btn-outline btn-sm" onclick="closeModal()">Отмена</button>';

    pollPayment(token);
}

async function pollPayment(token) {
    const poll = async () => {
        try {
            const res = await fetch(API + '/payments/' + token + '/status');
            if (!res.ok) return;
            const data = await res.json();

            if (data.status === 'paid') {
                onPaymentSuccess();
                return;
            }
            if (data.status === 'expired') {
                onPaymentExpired();
                return;
            }
        } catch (e) { /* keep polling */ }
        setTimeout(poll, 2000);
    };
    setTimeout(poll, 2000);
}

function onPaymentSuccess() {
    const $mTitle   = document.getElementById('modalTitle');
    const $mBody    = document.getElementById('modalBody');
    const $mActions = document.getElementById('modalActions');
    const $mPrice   = document.getElementById('modalPrice');

    $mTitle.textContent = 'Оплата прошла!';
    $mPrice.textContent = '✅';
    $mBody.innerHTML    = '<p style="color:var(--green);font-weight:700">Громкость изменена</p>';
    $mActions.innerHTML = '<button class="btn btn-accent btn-sm" onclick="closeModal()">Закрыть</button>';

    // apply volume locally
    const vol = parseInt($volumeIn.value, 10);
    if (audioEl && !isNaN(vol)) {
        audioEl.volume = vol / 100;
    }
    setStatus('Громкость: ' + vol + '%');

    setTimeout(closeModal, 2000);
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
    const d = document.createElement('div');
    d.textContent = s;
    return d.innerHTML;
}

/* ====== init ====== */
document.addEventListener('DOMContentLoaded', () => {
    loadTracks();
    audioEl = document.getElementById('audioPlayer');

    $uploadFile.addEventListener('change', uploadTrack);

    // sync volume with audio element
    audioEl.addEventListener('volumechange', () => {
        // no-op — volume is controlled via payment
    });

    setStatus('Готов к работе');
});
