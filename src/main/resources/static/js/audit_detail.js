(() => {
  const tracker = document.getElementById('audit-tracker');
  if (!tracker) return;

  const initialStatus = (tracker.dataset.initialStatus || '').toUpperCase();
  const statusUrl = tracker.dataset.statusUrl;
  const detailUrl = tracker.dataset.detailUrl;
  const TERMINAL = new Set(['COMPLETED', 'FAILED', 'CANCELLED']);

  if (!statusUrl || !detailUrl || TERMINAL.has(initialStatus)) {
    return;
  }

  let stopped = false;
  let timerId = null;
  let inFlight = false;
  let pollCount = 0;

  const setNote = (text) => {
    const note = document.getElementById('poll-status-note');
    if (note) note.textContent = text;
  };

  const updateActiveStatus = (data) => {
    if (!data || !data.status) return;
    const st = String(data.status).toUpperCase();
    const badge = document.getElementById('audit-status-badge');
    if (badge && badge.textContent !== st) {
      badge.textContent = st;
      badge.className = 'badge ' + (st === 'RUNNING' ? 'b-warn' : 'b-na');
    }
    const line = document.getElementById('audit-status-line');
    if (line && st === 'RUNNING' && !line.textContent.includes('RUNNING')) {
      line.textContent = 'Status: RUNNING · Worker processing in background';
    }
    const copy = document.getElementById('audit-progress-copy');
    if (copy && st === 'RUNNING') {
      copy.textContent = 'Integrity checks and security analysis are currently running in the background. Metrics and findings will appear automatically once complete.';
    }
  };

  const applyCompletedHtml = (html) => {
    const doc = new DOMParser().parseFromString(html, 'text/html');
    const nextMain = doc.getElementById('audit-main');
    const curMain = document.getElementById('audit-main');
    const nextTracker = doc.getElementById('audit-tracker');
    const nextStatus = (nextTracker && nextTracker.dataset.initialStatus || '').toUpperCase();

    if (!nextMain || !curMain || !TERMINAL.has(nextStatus)) {
      return false;
    }

    curMain.replaceWith(nextMain);

    const nextData = doc.getElementById('__DATA__');
    const curData = document.getElementById('__DATA__');
    if (nextData && curData) {
      curData.textContent = nextData.textContent;
    }
    if (doc.title) {
      document.title = doc.title;
    }
    return true;
  };

  const refreshDetailView = async () => {
    const res = await fetch(detailUrl, {
      headers: { Accept: 'text/html' },
      cache: 'no-store',
    });
    if (!res.ok) {
      throw new Error(`Detail fetch returned HTTP ${res.status}`);
    }
    const html = await res.text();
    if (applyCompletedHtml(html)) {
      stopped = true;
      return true;
    }
    return false;
  };

  const pollOnce = async () => {
    if (stopped || inFlight) return;
    inFlight = true;
    pollCount += 1;

    try {
      const res = await fetch(statusUrl, {
        headers: { Accept: 'application/json' },
        cache: 'no-store',
      });
      if (!res.ok) {
        throw new Error(`Status check returned HTTP ${res.status}`);
      }
      const data = await res.json();
      const status = String(data.status || '').toUpperCase();

      if (TERMINAL.has(status)) {
        setNote(`Audit reached ${status}. Loading persisted results…`);
        const swapped = await refreshDetailView();
        if (!swapped) {
          // Persisted terminal row not yet reflected in detail response; retry shortly
          scheduleNext(500);
        }
        return;
      }

      updateActiveStatus(data);
      setNote(`Checking live audit status automatically (check #${pollCount}: ${status})…`);
      scheduleNext(900);
    } catch (err) {
      setNote(`Temporary status check error (${err.message}); retrying…`);
      scheduleNext(1500);
    } finally {
      inFlight = false;
    }
  };

  const scheduleNext = (delayMs) => {
    if (stopped) return;
    if (timerId) clearTimeout(timerId);
    timerId = setTimeout(pollOnce, delayMs);
  };

  const refreshBtn = document.getElementById('audit-refresh-btn');
  if (refreshBtn) {
    refreshBtn.addEventListener('click', () => {
      if (timerId) clearTimeout(timerId);
      pollOnce();
    });
  }

  scheduleNext(350);
})();

