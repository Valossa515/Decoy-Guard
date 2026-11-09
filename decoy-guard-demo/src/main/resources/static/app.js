'use strict';

/*
 * Decoy Guard demo front end.
 *
 * mountPhone() is the whole bank app. The page calls it once per phone slot with no other
 * argument, so both phones run exactly the same code. Which session a phone gets depends only
 * on the PIN typed on its keypad, and only the server knows the answer.
 */

const TIME_ZONE = 'America/Sao_Paulo';
const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dayMonth = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', timeZone: 'UTC' });
const dateTime = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: TIME_ZONE
});
const timeOnly = new Intl.DateTimeFormat('pt-BR', {
  hour: '2-digit', minute: '2-digit', second: '2-digit', timeZone: TIME_ZONE
});

const STATUS_LABELS = { CONCLUIDO: 'Concluído' };

function formatMoney(value) {
  return money.format(Number(value));
}

function formatSignedMoney(value) {
  const n = Number(value);
  return (n < 0 ? '- ' : '+ ') + money.format(Math.abs(n));
}

/** LocalDate "2026-11-07" to "07/11" without shifting the day through a time zone. */
function formatDay(isoDate) {
  return dayMonth.format(new Date(isoDate + 'T00:00:00Z'));
}

/** Tokens of every phone that logged in. The fraud desk polls all of them. */
const knownSessions = new Set();

function mountPhone(slot) {
  const template = document.getElementById('phone-template');
  slot.appendChild(template.content.cloneNode(true));
  const root = slot.querySelector('.phone');
  const $ = (selector) => root.querySelector(selector);

  const state = { token: null, pin: '', balance: null, balanceHidden: false, busy: false };

  // --- navigation -------------------------------------------------------------------------

  function show(name) {
    root.querySelectorAll('[data-screen]').forEach((screen) => {
      screen.hidden = screen.dataset.screen !== name;
    });
  }

  function logout() {
    state.token = null;
    state.pin = '';
    state.balance = null;
    renderPin();
    $('[data-login-error]').textContent = '';
    show('login');
  }

  // --- API --------------------------------------------------------------------------------

  async function api(path, options = {}) {
    const headers = { 'Content-Type': 'application/json' };
    if (state.token) {
      headers.Authorization = 'Bearer ' + state.token;
    }
    const response = await fetch(path, { ...options, headers });
    if (response.status === 401 && state.token) {
      logout();
      throw new Error('session');
    }
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(body.error || 'Não foi possível concluir. Tente novamente.');
      error.status = response.status;
      throw error;
    }
    return body;
  }

  // --- login ------------------------------------------------------------------------------

  function renderPin() {
    root.querySelectorAll('[data-pin-dots] i').forEach((dot, index) => {
      dot.classList.toggle('filled', index < state.pin.length);
    });
  }

  async function submitPin() {
    state.busy = true;
    try {
      const { token } = await api('/login', { method: 'POST', body: JSON.stringify({ pin: state.pin }) });
      state.token = token;
      knownSessions.add(token);
      state.pin = '';
      renderPin();
      await openHome();
    } catch (error) {
      state.pin = '';
      renderPin();
      $('[data-login-error]').textContent = 'PIN incorreto. Tente novamente.';
      const dots = $('[data-pin-dots]');
      dots.classList.remove('shake');
      void dots.offsetWidth;
      dots.classList.add('shake');
    } finally {
      state.busy = false;
    }
  }

  $('[data-keypad]').addEventListener('click', (event) => {
    const key = event.target.closest('button')?.dataset.key;
    if (!key || state.busy) {
      return;
    }
    $('[data-login-error]').textContent = '';
    if (key === 'back') {
      state.pin = state.pin.slice(0, -1);
    } else if (state.pin.length < 4) {
      state.pin += key;
    }
    renderPin();
    if (state.pin.length === 4) {
      submitPin();
    }
  });

  // --- home -------------------------------------------------------------------------------

  function renderBalance() {
    const text = state.balance === null || state.balanceHidden ? 'R$ ••••' : formatMoney(state.balance);
    $('[data-balance]').textContent = text;
    $('[data-pix-available]').textContent = text;
  }

  function renderStatement(entries) {
    const list = $('[data-statement]');
    list.replaceChildren();
    entries
      .map((entry, index) => ({ entry, index }))
      .sort((a, b) => b.entry.date.localeCompare(a.entry.date) || b.index - a.index)
      .forEach(({ entry }) => {
        const item = document.createElement('li');
        const amount = Number(entry.amount);
        item.innerHTML = '<span class="st-icon"></span><span class="st-text"><strong></strong><small></small></span><span class="st-amount"></span>';
        item.querySelector('.st-icon').textContent = amount < 0 ? '↑' : '↓';
        item.querySelector('strong').textContent = entry.description;
        item.querySelector('small').textContent = formatDay(entry.date);
        const amountEl = item.querySelector('.st-amount');
        amountEl.textContent = formatSignedMoney(amount);
        amountEl.classList.add(amount < 0 ? 'out' : 'in');
        list.appendChild(item);
      });
  }

  async function openHome() {
    const [balance, statement] = await Promise.all([api('/account/balance'), api('/account/statement')]);
    state.balance = balance.available;
    renderBalance();
    renderStatement(statement);
    show('home');
  }

  // --- pix --------------------------------------------------------------------------------

  const keyInput = $('[data-pix-key]');
  const amountInput = $('[data-pix-amount]');
  let amountCents = 0;

  function renderAmount() {
    amountInput.value = formatMoney(amountCents / 100);
    $('[data-pix-submit]').disabled = amountCents <= 0 || keyInput.value.trim() === '';
  }

  function openPix() {
    keyInput.value = '';
    amountCents = 0;
    $('[data-pix-error]').textContent = '';
    renderAmount();
    show('pix');
  }

  // Amount typed as digits, shown as cents (R$ 0,00 -> R$ 2,00 -> R$ 200,00), like Brazilian bank apps.
  amountInput.addEventListener('keydown', (event) => {
    if (/^[0-9]$/.test(event.key)) {
      event.preventDefault();
      if (amountCents < 1e10) {
        amountCents = amountCents * 10 + Number(event.key);
      }
    } else if (event.key === 'Backspace') {
      event.preventDefault();
      amountCents = Math.floor(amountCents / 10);
    } else if (event.key !== 'Tab' && event.key !== 'Enter') {
      event.preventDefault();
      return;
    } else {
      return;
    }
    $('[data-pix-error]').textContent = '';
    renderAmount();
  });
  amountInput.addEventListener('input', () => {
    amountCents = Number(amountInput.value.replace(/\D/g, '')) || 0;
    renderAmount();
  });
  keyInput.addEventListener('input', renderAmount);

  $('[data-pix-form]').addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = $('[data-pix-submit]');
    if (submit.disabled) {
      return;
    }
    submit.disabled = true;
    try {
      const receipt = await api('/account/pix', {
        method: 'POST',
        body: JSON.stringify({ key: keyInput.value.trim(), amount: (amountCents / 100).toFixed(2) })
      });
      $('[data-receipt-amount]').textContent = formatMoney(receipt.amount);
      $('[data-receipt-key]').textContent = receipt.key;
      $('[data-receipt-at]').textContent = dateTime.format(new Date(receipt.at));
      $('[data-receipt-status]').textContent = STATUS_LABELS[receipt.status] || receipt.status;
      $('[data-receipt-id]').textContent = receipt.endToEndId;
      show('receipt');
    } catch (error) {
      if (error.message !== 'session') {
        $('[data-pix-error]').textContent = error.message;
      }
      renderAmount();
    }
  });

  // --- actions ----------------------------------------------------------------------------

  root.addEventListener('click', (event) => {
    const action = event.target.closest('[data-action]')?.dataset.action;
    if (action === 'logout') {
      logout();
    } else if (action === 'toggle-balance') {
      state.balanceHidden = !state.balanceHidden;
      renderBalance();
    } else if (action === 'open-pix') {
      openPix();
    } else if (action === 'go-home') {
      openHome().catch(() => {});
    }
  });

  show('login');
}

// --- fraud desk (outside the phones) ----------------------------------------------------------

const TRIGGER_LABELS = {
  DURESS_CREDENTIAL: 'Senha de coação',
  RISK_SIGNAL: 'Sinal de risco',
  MANUAL: 'Acionamento manual'
};

function shortSession(sessionId) {
  return sessionId.slice(0, 8);
}

function renderList(list, items, emptyText, fill) {
  list.replaceChildren();
  if (items.length === 0) {
    const empty = document.createElement('li');
    empty.className = 'empty';
    empty.textContent = emptyText;
    list.appendChild(empty);
    return;
  }
  items.forEach((item) => {
    const li = document.createElement('li');
    fill(li, item);
    list.appendChild(li);
  });
}

async function fetchAll(path) {
  const results = await Promise.all([...knownSessions].map((session) =>
    fetch(path + '?session=' + encodeURIComponent(session)).then((r) => (r.ok ? r.json() : [])).catch(() => [])));
  return results.flat();
}

async function refreshFraudDesk() {
  const [alerts, held] = await Promise.all([fetchAll('/fraud-desk/alerts'), fetchAll('/fraud-desk/held')]);

  alerts.sort((a, b) => b.raisedAt.localeCompare(a.raisedAt));
  renderList(document.querySelector('[data-alerts]'), alerts, 'Nenhum alerta até agora.', (li, alert) => {
    li.className = 'alert';
    li.innerHTML = '<strong>Sessão em coação</strong><span></span><small></small>';
    li.querySelector('span').textContent = 'Gatilho: ' + (TRIGGER_LABELS[alert.trigger] || alert.trigger);
    li.querySelector('small').textContent = timeOnly.format(new Date(alert.raisedAt)) + ' · sessão ' + shortSession(alert.sessionId);
  });

  held.sort((a, b) => b.heldAt.localeCompare(a.heldAt));
  renderList(document.querySelector('[data-held]'), held, 'Nenhuma transação retida.', (li, tx) => {
    li.className = 'held';
    li.innerHTML = '<div><strong></strong><b></b></div><span></span><small></small>';
    li.querySelector('strong').textContent = tx.type + ' para ' + tx.counterparty;
    li.querySelector('b').textContent = formatMoney(tx.amount);
    li.querySelector('span').textContent = 'Retida. O dinheiro não saiu da conta.';
    li.querySelector('small').textContent = timeOnly.format(new Date(tx.heldAt)) + ' · ' + tx.id;
  });
}

function tickClock() {
  const now = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit', timeZone: TIME_ZONE }).format(new Date());
  document.querySelectorAll('[data-clock]').forEach((el) => { el.textContent = now; });
}

document.querySelectorAll('[data-phone]').forEach((slot) => mountPhone(slot));
tickClock();
setInterval(tickClock, 15000);
refreshFraudDesk();
setInterval(refreshFraudDesk, 2000);
