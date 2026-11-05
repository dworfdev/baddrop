(() => {
    const ADMIN_KEY_STORAGE = 'adminKey';
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
    const metricFloorBuffer = document.getElementById('metricFloorBuffer');
    const metricHouseProfit = document.getElementById('metricHouseProfit');

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
            headers: {
                'Accept': 'application/json',
                'X-Admin-Key': sessionStorage.getItem(ADMIN_KEY_STORAGE) || ''
            }
        };
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
            sessionStorage.removeItem(ADMIN_KEY_STORAGE);
            showToast('Admin access denied', true);
            setTimeout(() => { window.location.href = '/'; }, 800);
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
    // 0) BANKROLL METRICS: PROFIT POOL / FLOOR BUFFER / HOUSE PROFIT
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
            metricFloorBuffer.textContent = bankroll.floorBufferRate || '33%';
            metricHouseProfit.textContent = formatMoney(bankroll.houseProfit);
        } catch (_) {}
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
                ? `<span class="tx-user-link" data-open-user="${tx.username}" title="Click to open ${tx.username}">${tx.username}</span>`
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
                    await callApi('POST', `/api/admin/transactions/${tx.txHash}/approve`);
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
                    await callApi('POST', `/api/admin/transactions/${tx.txHash}/reject`);
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

    // 3) Exit button - leaves admin page and returns to logged in user page
    exitAdminBtn.addEventListener('click', () => {
        sessionStorage.removeItem(ADMIN_KEY_STORAGE);
        window.location.href = '/';
    });

    // Initial load: bankroll metrics + default view with all users' pending transactions
    refreshDashboard();
})();