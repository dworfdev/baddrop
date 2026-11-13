(() => {
    // Admin auth = HttpOnly session cookie (set by /admin-login.html). Only the CSRF token lives in JS memory.
    let csrfToken = '';
    let selectedUsername = null;

    const userSearchInput = document.getElementById('userSearchInput');
    const findUserBtn = document.getElementById('findUserBtn');
    const exitAdminBtn = document.getElementById('exitAdminBtn');
    const refreshPendingBtn = document.getElementById('refreshPendingBtn');
    const backToDefaultBtn = document.getElementById('backToDefaultBtn');

    const defaultPendingView = document.getElementById('defaultPendingView');
    const userDetailView = document.getElementById('userDetailView');

    const allPendingList = document.getElementById('allPendingList');
    const adminDetailUsername = document.getElementById('adminDetailUsername');
    const adminDetailBalance = document.getElementById('adminDetailBalance');
    const userPendingList = document.getElementById('userPendingList');
    const userStatsSummaryRow = document.getElementById('userStatsSummaryRow');
    const userStatsTimeline = document.getElementById('userStatsTimeline');
    const toastContainer = document.getElementById('toastContainer');
    const metricProfitPool = document.getElementById('metricProfitPool');
    const metricHouseProfit = document.getElementById('metricHouseProfit');

    // Bankroll Reset Modal Elements
    const resetBankrollBtn = document.getElementById('resetBankrollBtn');
    const bankrollResetModal = document.getElementById('bankrollResetModal');
    const closeResetModalBtn = document.getElementById('closeResetModalBtn');
    const cancelResetModalBtn = document.getElementById('cancelResetModalBtn');
    const confirmResetSubmitBtn = document.getElementById('confirmResetSubmitBtn');
    const resetConfirmInput = document.getElementById('resetConfirmInput');
    const resetModalError = document.getElementById('resetModalError');

    function showToast(message, isError = false) {
        const toast = document.createElement('div');
        toast.className = `toast ${isError ? 'error' : ''}`;
        toast.textContent = message;
        toastContainer.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transition = 'opacity 0.25s ease';
            setTimeout(() => toast.remove(), 250);
        }, 2600);
    }

    async function callApi(method, url, body = null) {
        const options = {
            method,
            credentials: 'same-origin',
            headers: {
                'Accept': 'application/json'
            }
        };
        if (method !== 'GET' && csrfToken) {
            options.headers['X-CSRF-Token'] = csrfToken;
        }
        if (body !== null) {
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(body);
        }

        const response = await fetch(url, options);
        let data = null;
        try {
            data = await response.json();
        } catch (_) {
            data = {};
        }

        if (response.status === 401) {
            showToast('Admin session expired', true);
            setTimeout(() => { window.location.href = '/admin-login.html'; }, 800);
            throw new Error('Unauthorized');
        }

        if (!response.ok) {
            const msg = data && data.message ? data.message : `HTTP ${response.status}`;
            showToast(msg, true);
            throw new Error(msg);
        }
        return data;
    }

    // =========================================================================
    // 0) BANKROLL METRICS: PROFIT POOL / HOUSE PROFIT & DESTRUCTIVE RESET
    // =========================================================================
    function formatMoney(value) {
        const n = Number(value);
        return `$${(Number.isFinite(n) ? n : 0).toLocaleString('en-US', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        })}`;
    }

    async function loadBankroll() {
        try {
            const bankroll = await callApi('GET', '/api/admin/bankroll');
            metricProfitPool.textContent = formatMoney(bankroll.profitPool);
            metricHouseProfit.textContent = formatMoney(bankroll.houseProfit);
        } catch (_) {}
    }

    function openResetModal() {
        if (!bankrollResetModal) return;
        resetConfirmInput.value = '';
        resetConfirmInput.classList.remove('input-error');
        resetModalError.style.display = 'none';
        bankrollResetModal.style.display = 'flex';
        setTimeout(() => resetConfirmInput.focus(), 60);
    }

    function closeResetModal() {
        if (!bankrollResetModal) return;
        bankrollResetModal.style.display = 'none';
        resetConfirmInput.value = '';
        resetConfirmInput.classList.remove('input-error');
        resetModalError.style.display = 'none';
    }

    async function submitBankrollReset() {
        const phrase = resetConfirmInput.value.trim();
        if (phrase !== 'RESET BANKROLL AS ADMIN') {
            resetConfirmInput.classList.add('input-error');
            resetModalError.style.display = 'block';
            resetConfirmInput.focus();
            return;
        }

        resetConfirmInput.classList.remove('input-error');
        resetModalError.style.display = 'none';
        confirmResetSubmitBtn.disabled = true;

        try {
            const result = await callApi('POST', '/api/admin/bankroll/reset', {
                confirmation: 'RESET BANKROLL AS ADMIN'
            });
            const newProfitPool = result.prizePool !== undefined ? result.prizePool : (result.profitPool ?? 0);
            const newHouseProfit = result.houseProfit !== undefined ? result.houseProfit : 0;
            metricProfitPool.textContent = formatMoney(newProfitPool);
            metricHouseProfit.textContent = formatMoney(newHouseProfit);
            closeResetModal();
            showToast('Bankroll metrics reset successfully');
        } catch (err) {
            resetConfirmInput.classList.add('input-error');
        } finally {
            confirmResetSubmitBtn.disabled = false;
        }
    }

    // Refresh = bankroll metrics + pending transactions list
    async function refreshDashboard() {
        await Promise.all([loadBankroll(), loadAllPendingTransactions()]);
    }

    // =========================================================================
    // 1) DEFAULT VIEW: LOAD ALL USERS' PENDING TRANSACTIONS
    // =========================================================================
    async function loadAllPendingTransactions() {
        selectedUsername = null;
        userDetailView.classList.remove('active');
        defaultPendingView.classList.add('active');

        try {
            const txs = await callApi('GET', '/api/admin/transactions/pending');
            renderTransactionsList(allPendingList, txs, true);
        } catch (_) {}
    }

    function renderTransactionsList(container, txs, showUsernameLink) {
        container.innerHTML = '';
        if (!txs || txs.length === 0) {
            container.innerHTML = '<div class="empty-state">No pending transactions right now.</div>';
            return;
        }

        txs.forEach(tx => {
            const card = document.createElement('div');
            card.className = 'tx-card';
            const timeStr = tx.createdAt ? new Date(tx.createdAt).toLocaleTimeString() : '';
            const typeClass = tx.type === 'DEPOSIT' ? 'tx-type-deposit' : 'tx-type-withdraw';

            card.innerHTML = `
                <div class="tx-info">
                    <div class="tx-title">
                        ${showUsernameLink
                ? `<span class="tx-user-link" data-open-user></span>`
                : ''
            }
                        <span class="tx-badge ${typeClass}">${tx.type}</span>
                        <span class="tx-badge">${tx.status}</span>
                    </div>
                    <div class="tx-meta">
                        Amount: <strong>$${Number(tx.amount).toFixed(2)}</strong> ${timeStr ? `&middot; ${timeStr}` : ''}
                    </div>
                    ${tx.type === 'WITHDRAW' && tx.walletAddress ? `
                        <div class="tx-wallet-box" data-copy-wallet title="Click to copy TON address">
                            <span class="tx-wallet-label">TON Address:</span>
                            <span class="tx-wallet-code"></span>
                            <button type="button" class="tx-wallet-copy-btn">Copy</button>
                        </div>
                    ` : ''}
                </div>
                <div class="tx-actions">
                    <button type="button" class="btn-approve" data-approve>APPROVE</button>
                    <button type="button" class="btn-reject" data-reject>REJECT</button>
                </div>
            `;

            const userLabel = card.querySelector('[data-open-user]');
            if (userLabel) {
                // textContent, never innerHTML: usernames are user-controlled data
                userLabel.textContent = tx.username;
                userLabel.title = 'Click to open ' + tx.username;
            }

            const walletBox = card.querySelector('[data-copy-wallet]');
            if (walletBox) {
                walletBox.querySelector('.tx-wallet-code').textContent = tx.walletAddress;
                walletBox.addEventListener('click', async () => {
                    const copyBtn = walletBox.querySelector('.tx-wallet-copy-btn');
                    try {
                        if (navigator.clipboard && navigator.clipboard.writeText) {
                            await navigator.clipboard.writeText(tx.walletAddress);
                        } else {
                            const temp = document.createElement('textarea');
                            temp.value = tx.walletAddress;
                            document.body.appendChild(temp);
                            temp.select();
                            document.execCommand('copy');
                            document.body.removeChild(temp);
                        }
                        copyBtn.textContent = 'Copied!';
                        setTimeout(() => { copyBtn.textContent = 'Copy'; }, 1500);
                        showToast('TON address copied to clipboard');
                    } catch (_) {
                        showToast('Failed to copy TON address', true);
                    }
                });
            }

            const userLink = card.querySelector('[data-open-user]');
            if (userLink) {
                userLink.addEventListener('click', () => {
                    userSearchInput.value = tx.username;
                    openSpecificUser(tx.username);
                });
            }

            card.querySelector('[data-approve]').addEventListener('click', async () => {
                try {
                    let externalTxHash = null;
                    if (tx.type === 'DEPOSIT') {
                        externalTxHash = window.prompt('Paste the ON-CHAIN transaction hash you verified for this deposit ($' + Number(tx.amount).toFixed(2) + ')');
                        if (!externalTxHash || !externalTxHash.trim()) {
                            showToast('On-chain tx hash is required to approve a deposit', true);
                            return;
                        }
                    } else {
                        externalTxHash = window.prompt('Payout tx hash (optional)') || null;
                    }
                    await callApi('POST', `/api/admin/transactions/${encodeURIComponent(tx.txHash)}/approve`,
                        { externalTxHash: externalTxHash ? externalTxHash.trim() : null });
                    showToast(`Approved ${tx.type} ($${Number(tx.amount).toFixed(2)}) for ${tx.username}`);
                    if (selectedUsername) {
                        await openSpecificUser(selectedUsername);
                    } else {
                        await loadAllPendingTransactions();
                    }
                } catch (_) {}
            });

            card.querySelector('[data-reject]').addEventListener('click', async () => {
                try {
                    await callApi('POST', `/api/admin/transactions/${encodeURIComponent(tx.txHash)}/reject`);
                    showToast(`Rejected ${tx.type} ($${Number(tx.amount).toFixed(2)}) for ${tx.username}`);
                    if (selectedUsername) {
                        await openSpecificUser(selectedUsername);
                    } else {
                        await loadAllPendingTransactions();
                    }
                } catch (_) {}
            });

            container.appendChild(card);
        });
    }

    // =========================================================================
    // 2) SPECIFIC USER ADMIN VIEW: USERNAME, BALANCE, PENDING & STATS TIMELINE
    // =========================================================================
    function formatTimelineDate(isoString) {
        if (!isoString) return { datePart: '-', timePart: '' };
        const d = new Date(isoString);
        const day = String(d.getDate()).padStart(2, '0');
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const year = d.getFullYear();
        const hours = String(d.getHours()).padStart(2, '0');
        const mins = String(d.getMinutes()).padStart(2, '0');
        return {
            datePart: `${day}.${month}.${year}`,
            timePart: `${hours}:${mins}`
        };
    }

    function renderUserStatsTimeline(history) {
        const list = Array.isArray(history) ? history : [];

        let totalDeposited = 0;
        let totalWithdrawn = 0;
        list.forEach(tx => {
            const amt = Number(tx.amount) || 0;
            if (tx.type === 'DEPOSIT') {
                totalDeposited += amt;
            } else if (tx.type === 'WITHDRAW') {
                totalWithdrawn += amt;
            }
        });

        userStatsSummaryRow.innerHTML = `
            <div class="stats-summary-pill">Transactions: <strong class="blue">${list.length}</strong></div>
            <div class="stats-summary-pill">Total Deposited: <strong class="green">+$${totalDeposited.toFixed(2)}</strong></div>
            <div class="stats-summary-pill">Total Withdrawn: <strong class="red">&minus;$${totalWithdrawn.toFixed(2)}</strong></div>
        `;

        if (list.length === 0) {
            userStatsTimeline.innerHTML = '<div class="empty-state">No approved deposit or withdrawal history for this user yet.</div>';
            return;
        }

        const maxAmount = Math.max(...list.map(tx => Number(tx.amount) || 0), 1);
        const midAmount = maxAmount / 2;

        const barsHtml = list.map((tx, idx) => {
            const amt = Number(tx.amount) || 0;
            const isDeposit = tx.type === 'DEPOSIT';
            const cls = isDeposit ? 'deposit' : 'withdraw';
            const sign = isDeposit ? '+' : '\u2212';
            const heightPx = Math.max(16, Math.round((amt / maxAmount) * 180));
            const { datePart, timePart } = formatTimelineDate(tx.createdAt);

            return `
                <div class="timeline-bar-column" title="${tx.type}: $${amt.toFixed(2)} (${datePart} ${timePart})">
                    <div class="timeline-bar-plot">
                        <div class="timeline-bar-amount ${cls}">${sign}$${amt.toFixed(2)}</div>
                        <div class="timeline-bar-fill ${cls}" style="height: ${heightPx}px;"></div>
                    </div>
                    <div class="timeline-x-meta">
                        <span class="timeline-x-index">#${idx + 1}</span>
                        <span class="timeline-x-date">${datePart}<br/>${timePart}</span>
                    </div>
                </div>
            `;
        }).join('');

        userStatsTimeline.innerHTML = `
            <div class="timeline-chart-layout">
                <div class="timeline-y-axis">
                    <span>$${maxAmount.toFixed(2)}</span>
                    <span>$${midAmount.toFixed(2)}</span>
                    <span>$0.00</span>
                </div>
                <div class="timeline-x-scroll">
                    <div class="timeline-bars-stage">
                        ${barsHtml}
                    </div>
                </div>
            </div>
        `;
    }

    async function openSpecificUser(username) {
        const trimmed = (username || '').trim();
        if (!trimmed) {
            await loadAllPendingTransactions();
            return;
        }

        try {
            const overview = await callApi('GET', `/api/admin/users/${encodeURIComponent(trimmed)}`);
            selectedUsername = overview.username;

            defaultPendingView.classList.remove('active');
            userDetailView.classList.add('active');

            adminDetailUsername.textContent = overview.username;
            adminDetailBalance.textContent = Number(overview.balance).toFixed(2);

            renderTransactionsList(userPendingList, overview.pendingTransactions, false);
            renderUserStatsTimeline(overview.transactionHistory);
        } catch (_) {}
    }

    // =========================================================================
    // EVENT LISTENERS (SEARCH, BACK, EXIT)
    // =========================================================================
    findUserBtn.addEventListener('click', () => {
        openSpecificUser(userSearchInput.value);
    });

    userSearchInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            openSpecificUser(userSearchInput.value);
        }
    });

    userSearchInput.addEventListener('input', () => {
        if (userSearchInput.value.trim() === '' && selectedUsername !== null) {
            loadAllPendingTransactions();
        }
    });

    backToDefaultBtn.addEventListener('click', () => {
        userSearchInput.value = '';
        loadAllPendingTransactions();
    });

    refreshPendingBtn.addEventListener('click', refreshDashboard);

    // 3) Exit button - calls POST /api/admin/logout, leaves admin page and returns to logged in user page
    exitAdminBtn.addEventListener('click', async () => {
        try {
            await callApi('POST', '/api/admin/auth/logout', {});
        } catch (_) {}
        window.location.href = '/';
    });

    // 4) Bankroll Reset Modal Event Listeners
    if (resetBankrollBtn) {
        resetBankrollBtn.addEventListener('click', openResetModal);
    }
    if (closeResetModalBtn) {
        closeResetModalBtn.addEventListener('click', closeResetModal);
    }
    if (cancelResetModalBtn) {
        cancelResetModalBtn.addEventListener('click', closeResetModal);
    }
    if (bankrollResetModal) {
        bankrollResetModal.addEventListener('click', (e) => {
            if (e.target === bankrollResetModal) {
                closeResetModal();
            }
        });
    }
    if (confirmResetSubmitBtn) {
        confirmResetSubmitBtn.addEventListener('click', submitBankrollReset);
    }
    if (resetConfirmInput) {
        resetConfirmInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                submitBankrollReset();
            } else if (e.key === 'Escape') {
                closeResetModal();
            }
        });
        resetConfirmInput.addEventListener('input', () => {
            resetConfirmInput.classList.remove('input-error');
            if (resetModalError) {
                resetModalError.style.display = 'none';
            }
        });
    }

    // Initial load: fetch the CSRF token for this admin session, then bankroll metrics + pending transactions
    (async function init() {
        try {
            const me = await callApi('GET', '/api/admin/auth/me');
            csrfToken = me.csrfToken || '';
        } catch (_) {
            return;
        }
        refreshDashboard();
    })();
})();