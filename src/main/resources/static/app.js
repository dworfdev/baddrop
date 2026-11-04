(() => {
    const SESSION_STORAGE_KEY = 'slotsActiveUser';

    // Active logged-in player state
    let currentUser = {
        username: '',
        password: '',
        balance: 0.0,
        items: []
    };

    let displayedBalance = 0.0;
    const INVENTORY_FILTER_MODES = [
        { code: 'ALL', label: 'Filter: All' },
        { code: 'PRICE_ASC', label: 'Filter: Price (Low \u2192 High)' },
        { code: 'PRICE_DESC', label: 'Filter: Price (High \u2192 Low)' }
    ];
    let currentFilterIdx = 0;

    const CASES_FILTER_MODES = [
        { code: 'PRICE_ASC', label: 'Filter: Price (Low \u2192 High)' },
        { code: 'PRICE_DESC', label: 'Filter: Price (High \u2192 Low)' },
        { code: 'AFFORDABLE', label: 'Filter: Affordable' }
    ];
    let currentCasesFilterIdx = 0;

    // DOM Elements - Screens
    const welcomeScreen = document.getElementById('welcomeScreen');
    const playerScreen = document.getElementById('playerScreen');

    // DOM Elements - Auth
    const authUsername = document.getElementById('authUsername');
    const authPassword = document.getElementById('authPassword');
    const createBtn = document.getElementById('createBtn');
    const loginBtn = document.getElementById('loginBtn');

    // DOM Elements - Top Bar
    const topUsername = document.getElementById('topUsername');
    const topBalance = document.getElementById('topBalance');
    const openBalanceModalBtn = document.getElementById('openBalanceModalBtn');

    // DOM Elements - Games & Cases Marketplace View
    const liveRichList = document.getElementById('liveRichList');
    const openCasesPageBtn = document.getElementById('openCasesPageBtn');
    const backToGamesBtn = document.getElementById('backToGamesBtn');
    const casesSearchInput = document.getElementById('casesSearchInput');
    const casesFilterBtn = document.getElementById('casesFilterBtn');
    const casesMarketplaceGrid = document.getElementById('casesMarketplaceGrid');

    // DOM Elements - Inventory View
    const inventorySearchInput = document.getElementById('inventorySearchInput');
    const inventoryFilterBtn = document.getElementById('inventoryFilterBtn');
    const inventoryGrid = document.getElementById('inventoryGrid');

    // DOM Elements - Profile View
    const profileUsername = document.getElementById('profileUsername');
    const profilePassword = document.getElementById('profilePassword');
    const togglePasswordVisBtn = document.getElementById('togglePasswordVisBtn');
    const changePasswordBtn = document.getElementById('changePasswordBtn');
    let isPasswordVisible = false;
    const logoutBtn = document.getElementById('logoutBtn');
    const deleteAccountBtn = document.getElementById('deleteAccountBtn');
    const promoInput = document.getElementById('promoInput');
    const promoSubmitBtn = document.getElementById('promoSubmitBtn');
    const promoFeedback = document.getElementById('promoFeedback');

    // DOM Elements - Modals
    const balanceModal = document.getElementById('balanceModal');
    const balanceModalTitle = document.getElementById('balanceModalTitle');
    const closeBalanceModalBtn = document.getElementById('closeBalanceModalBtn');
    const modalBalanceAmount = document.getElementById('modalBalanceAmount');
    const modalDepositBtn = document.getElementById('modalDepositBtn');
    const modalWithdrawBtn = document.getElementById('modalWithdrawBtn');

    const walletStep1 = document.getElementById('walletStep1');
    const walletStep2 = document.getElementById('walletStep2');
    const walletStep3 = document.getElementById('walletStep3');
    const step2DepositPanel = document.getElementById('step2DepositPanel');
    const step2WithdrawPanel = document.getElementById('step2WithdrawPanel');
    const step2DepositAmountText = document.getElementById('step2DepositAmountText');
    const step2WithdrawAmountText = document.getElementById('step2WithdrawAmountText');
    const depositAddressText = document.getElementById('depositAddressText');
    const copyAddressBox = document.getElementById('copyAddressBox');
    const copyDepositAddressBtn = document.getElementById('copyDepositAddressBtn');
    const depositTimerDisplay = document.getElementById('depositTimerDisplay');
    const depositSentBtn = document.getElementById('depositSentBtn');
    const withdrawWalletAddressInput = document.getElementById('withdrawWalletAddressInput');
    const withdrawSentBtn = document.getElementById('withdrawSentBtn');
    const walletDoneExitBtn = document.getElementById('walletDoneExitBtn');
    const walletProgressFill = document.getElementById('walletProgressFill');

    const passwordModal = document.getElementById('passwordModal');
    const closePasswordModalBtn = document.getElementById('closePasswordModalBtn');
    const currentPasswordInput = document.getElementById('currentPasswordInput');
    const newPasswordInput = document.getElementById('newPasswordInput');
    const confirmPasswordBtn = document.getElementById('confirmPasswordBtn');

    const logoutModal = document.getElementById('logoutModal');
    const closeLogoutModalBtn = document.getElementById('closeLogoutModalBtn');
    const cancelLogoutBtn = document.getElementById('cancelLogoutBtn');
    const confirmLogoutBtn = document.getElementById('confirmLogoutBtn');

    const deleteAccountModal = document.getElementById('deleteAccountModal');
    const closeDeleteAccountModalBtn = document.getElementById('closeDeleteAccountModalBtn');
    const cancelDeleteAccountBtn = document.getElementById('cancelDeleteAccountBtn');
    const confirmDeleteAccountBtn = document.getElementById('confirmDeleteAccountBtn');
    const deleteAccountConfirmInput = document.getElementById('deleteAccountConfirmInput');
    const DELETE_ACCOUNT_AGREEMENT_SENTENCE = 'I agree to permanently delete my account';

    const caseOpeningOverlay = document.getElementById('caseOpeningOverlay');
    const caseTapStage = document.getElementById('caseTapStage');
    const caseTapImage = document.getElementById('caseTapImage');

    const caseOutcomeModal = document.getElementById('caseOutcomeModal');
    const caseOutcomeTitle = document.getElementById('caseOutcomeTitle');
    const caseOutcomeOkBtn = document.getElementById('caseOutcomeOkBtn');
    const caseOutcomeUsdtAmount = document.getElementById('caseOutcomeUsdtAmount');

    const toastContainer = document.getElementById('toastContainer');

    // =========================================================================
    // UTILITIES & API HELPER
    // =========================================================================
    function showToast(message, isError = false) {
        const toast = document.createElement('div');
        toast.className = `toast ${isError ? 'error' : ''}`;
        toast.textContent = message;
        toastContainer.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transition = 'opacity 0.25s ease';
            setTimeout(() => toast.remove(), 250);
        }, 2800);
    }

    function animateValue(start, end, duration, onFrame) {
        const startTime = performance.now();
        function step(now) {
            const progress = Math.min((now - startTime) / duration, 1);
            const eased = 1 - Math.pow(1 - progress, 3);
            onFrame(start + (end - start) * eased);
            if (progress < 1) requestAnimationFrame(step);
        }
        requestAnimationFrame(step);
    }

    async function callApi(method, url, body = null, showToastOnError = true) {
        const options = {
            method,
            headers: { 'Accept': 'application/json' }
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

        if (!response.ok) {
            const msg = data && data.message ? data.message : `Error ${response.status}`;
            if (showToastOnError) {
                showToast(msg, true);
            }
            throw new Error(msg);
        }
        return data;
    }

    function saveSession() {
        sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({
            username: currentUser.username,
            password: currentUser.password
        }));
    }

    function clearSession() {
        sessionStorage.removeItem(SESSION_STORAGE_KEY);
    }

    // =========================================================================
    // UI SYNCHRONIZATION WITH BACKEND GET ENDPOINTS
    // =========================================================================
    function updateBalanceDisplay(newBal) {
        const target = Number(newBal) || 0;
        animateValue(displayedBalance, target, 380, (val) => {
            topBalance.textContent = val.toFixed(2);
        });
        displayedBalance = target;
        currentUser.balance = target;
    }

    function updateProfileUI() {
        topUsername.textContent = currentUser.username;
        profileUsername.textContent = currentUser.username;
        const rawPass = currentUser.password || '';
        profilePassword.textContent = isPasswordVisible
            ? rawPass
            : '\u2022'.repeat(Math.max(8, rawPass.length));
        if (togglePasswordVisBtn) {
            togglePasswordVisBtn.textContent = isPasswordVisible ? 'Hide' : 'Show';
        }
    }

    if (togglePasswordVisBtn) {
        togglePasswordVisBtn.addEventListener('click', () => {
            isPasswordVisible = !isPasswordVisible;
            updateProfileUI();
        });
    }

    async function syncBalanceFromGet() {
        const query = new URLSearchParams({
            username: currentUser.username,
            password: currentUser.password
        }).toString();
        const res = await callApi('GET', `/api/users/balance?${query}`);
        updateBalanceDisplay(res.balance);
    }

    async function syncItemsFromGet() {
        const query = new URLSearchParams({
            username: currentUser.username,
            password: currentUser.password
        }).toString();
        const res = await callApi('GET', `/api/users/items?${query}`);
        currentUser.items = Array.isArray(res.items) ? res.items : [];
        renderInventoryGrid();
    }

    async function syncTopRichLeaderboard() {
        const topUsers = await callApi('GET', '/api/users/top');
        liveRichList.innerHTML = '';
        if (!topUsers || topUsers.length === 0) {
            liveRichList.innerHTML = '<div class="rich-user-balance">No players yet</div>';
            return;
        }
        topUsers.slice(0, 3).forEach((u, index) => {
            const item = document.createElement('div');
            item.className = 'rich-user-item';
            item.innerHTML = `
                <div class="rich-user-name">${index + 1}. ${u.username}</div>
                <div class="rich-user-balance">$${Number(u.balance).toFixed(2)}</div>
            `;
            liveRichList.appendChild(item);
        });
    }

    // =========================================================================
    // SCREEN 1: CREATE USER & LOGIN FLOW
    // =========================================================================
    async function enterPlayerPortal(userResponse, rawPassword) {
        currentUser = {
            username: userResponse.username,
            password: rawPassword,
            balance: Number(userResponse.balance) || 0,
            items: Array.isArray(userResponse.items) ? userResponse.items : []
        };
        saveSession();
        isPasswordVisible = false;
        displayedBalance = 0;
        updateBalanceDisplay(currentUser.balance);
        updateProfileUI();
        renderInventoryGrid();
        await syncTopRichLeaderboard();

        welcomeScreen.classList.remove('active');
        playerScreen.classList.add('active');

        await checkAndRestoreActiveDeposit(true);
    }

    createBtn.addEventListener('click', async () => {
        try {
            const username = authUsername.value.trim();
            const password = authPassword.value;
            const created = await callApi('POST', '/api/users', { username, password });
            showToast(`Account '${created.username}' created!`);
            await enterPlayerPortal(created, password);
        } catch (_) {}
    });

    loginBtn.addEventListener('click', async () => {
        try {
            const username = authUsername.value.trim();
            const password = authPassword.value;
            const loggedIn = await callApi('POST', '/api/users/login', { username, password });
            showToast(`Welcome back, ${loggedIn.username}!`);
            await enterPlayerPortal(loggedIn, password);
        } catch (_) {}
    });

    // =========================================================================
    // BOTTOM NAVIGATION: game | inventory | profile
    // =========================================================================
    document.querySelectorAll('.nav-tab').forEach(tabBtn => {
        tabBtn.addEventListener('click', async () => {
            document.querySelectorAll('.nav-tab').forEach(b => b.classList.remove('active'));
            document.querySelectorAll('.tab-view').forEach(v => v.classList.remove('active'));

            tabBtn.classList.add('active');
            const targetId = tabBtn.dataset.target;
            document.getElementById(targetId).classList.add('active');

            try {
                if (targetId === 'tab-game') {
                    await syncBalanceFromGet();
                    await syncTopRichLeaderboard();
                } else if (targetId === 'tab-inventory') {
                    await syncBalanceFromGet();
                    await syncItemsFromGet();
                } else if (targetId === 'tab-profile') {
                    promoFeedback.textContent = '';
                    const query = new URLSearchParams({
                        username: currentUser.username,
                        password: currentUser.password
                    }).toString();
                    const freshUser = await callApi('GET', `/api/users/user?${query}`);
                    currentUser.balance = freshUser.balance;
                    updateBalanceDisplay(freshUser.balance);
                    updateProfileUI();
                }
            } catch (_) {}
        });
    });

    // =========================================================================
    // TAB 1 & TAB 1B: GAMES & CASES MARKETPLACE INTERACTION
    // =========================================================================
    const CRATE_META = [
        { key: 'IRON_CRATE', label: 'pepe-iron', tier: 'IRON', price: 2, img: '/imgs/cases/case-pepe-iron.png' },
        { key: 'SILVER_CRATE', label: 'shiba-inu-gold', tier: 'SILVER', price: 5, img: '/imgs/cases/case-shiba-inu-gold.png' },
        { key: 'GOLD_CRATE', label: 'doge-amethyst', tier: 'GOLD', price: 11, img: '/imgs/cases/case-doge-amethyst.png' }
    ];

    function openCasesMarketplace() {
        document.querySelectorAll('.tab-view').forEach(v => v.classList.remove('active'));
        const casesTab = document.getElementById('tab-cases');
        if (casesTab) {
            casesTab.classList.add('active');
        }
        renderCasesMarketplaceGrid();
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    if (backToGamesBtn) {
        backToGamesBtn.addEventListener('click', async () => {
            document.querySelectorAll('.tab-view').forEach(v => v.classList.remove('active'));
            document.getElementById('tab-game').classList.add('active');
            try {
                await syncBalanceFromGet();
                await syncTopRichLeaderboard();
            } catch (_) {}
        });
    }

    document.querySelectorAll('.game-mock-card').forEach(card => {
        card.addEventListener('click', () => {
            if (card.disabled || card.classList.contains('disabled-game')) {
                return;
            }
            document.querySelectorAll('.game-mock-card').forEach(c => c.classList.remove('active'));
            card.classList.add('active');
            const gameName = card.dataset.game || 'Game';
            if (gameName === 'Cases' || card.id === 'openCasesPageBtn') {
                openCasesMarketplace();
            } else {
                showToast(`Selected ${gameName}`);
            }
        });
    });

    function renderCasesMarketplaceGrid() {
        const searchQuery = casesSearchInput.value.trim().toLowerCase();
        const activeFilter = CASES_FILTER_MODES[currentCasesFilterIdx];

        let list = CRATE_META.filter(crate => {
            if (activeFilter.code === 'AFFORDABLE' && currentUser.balance < crate.price) {
                return false;
            }
            if (
                searchQuery &&
                !crate.label.toLowerCase().includes(searchQuery) &&
                !crate.key.toLowerCase().includes(searchQuery)
            ) {
                return false;
            }
            return true;
        });

        if (activeFilter.code === 'PRICE_DESC') {
            list = [...list].sort((a, b) => b.price - a.price);
        } else {
            list = [...list].sort((a, b) => a.price - b.price);
        }

        casesMarketplaceGrid.innerHTML = '';
        if (list.length === 0) {
            casesMarketplaceGrid.innerHTML = `<div class="empty-state">No marketplace cases match (${activeFilter.label}).</div>`;
            return;
        }

        list.forEach(crate => {
            const card = document.createElement('div');
            card.className = 'inventory-case-card';
            const displayLabel = crate.label;

            card.innerHTML = `
                <div class="case-image-slot">
                    <img src="${crate.img}" alt="${displayLabel}" class="case-art-img" />
                </div>
                <div class="case-bottom-footer">
                    <div class="case-title">${displayLabel}</div>
                    <div class="case-buy-row">
                        <span class="case-price">$${crate.price}</span>
                        <button type="button" class="case-buy-btn" data-buy-crate="${crate.key}">BUY</button>
                    </div>
                </div>
            `;

            card.querySelector('[data-buy-crate]').addEventListener('click', async () => {
                try {
                    await callApi('POST', '/api/users/items/buy', {
                        username: currentUser.username,
                        password: currentUser.password,
                        item: crate.key
                    });
                    await syncBalanceFromGet();
                    await syncItemsFromGet();
                    await syncTopRichLeaderboard();
                    renderCasesMarketplaceGrid();
                    showToast(`Bought ${displayLabel} for $${crate.price}! Moved to inventory.`);
                } catch (_) {}
            });

            casesMarketplaceGrid.appendChild(card);
        });
    }

    casesSearchInput.addEventListener('input', renderCasesMarketplaceGrid);

    casesFilterBtn.addEventListener('click', () => {
        currentCasesFilterIdx = (currentCasesFilterIdx + 1) % CASES_FILTER_MODES.length;
        casesFilterBtn.textContent = CASES_FILTER_MODES[currentCasesFilterIdx].label;
        renderCasesMarketplaceGrid();
    });

    // =========================================================================
    // TAB 2: INVENTORY RENDERING, SEARCH & FILTER (ONLY OWNED CASES DISPLAYED)
    // =========================================================================

    function renderInventoryGrid() {
        const counts = { IRON_CRATE: 0, SILVER_CRATE: 0, GOLD_CRATE: 0 };
        (currentUser.items || []).forEach(item => {
            if (counts[item] !== undefined) counts[item]++;
        });

        const searchQuery = inventorySearchInput.value.trim().toLowerCase();
        const activeFilter = INVENTORY_FILTER_MODES[currentFilterIdx];

        let filtered = CRATE_META.filter(crate => {
            const count = counts[crate.key] || 0;
            // Only display cases that the user actually owns
            if (count <= 0) return false;
            if (
                searchQuery &&
                !crate.label.toLowerCase().includes(searchQuery) &&
                !crate.key.toLowerCase().includes(searchQuery)
            ) {
                return false;
            }
            return true;
        });

        if (activeFilter.code === 'PRICE_ASC') {
            filtered = [...filtered].sort((a, b) => a.price - b.price);
        } else if (activeFilter.code === 'PRICE_DESC') {
            filtered = [...filtered].sort((a, b) => b.price - a.price);
        }

        inventoryGrid.innerHTML = '';
        if (filtered.length === 0) {
            inventoryGrid.innerHTML = `<div class="empty-state">No cases in inventory matching filter</div>`;
            return;
        }

        filtered.forEach(crate => {
            const card = document.createElement('div');
            card.className = 'inventory-case-card';
            const displayLabel = crate.label;
            card.innerHTML = `
                <div class="case-image-slot">
                    <img src="${crate.img}" alt="${displayLabel}" class="case-art-img" />
                </div>
                <div class="case-bottom-footer">
                    <div class="case-title">${displayLabel}</div>
                    <div class="case-buy-row">
                        <span class="case-count-inline">x${counts[crate.key]}</span>
                        <button type="button" class="case-buy-btn" data-open-crate="${crate.key}">Open</button>
                    </div>
                </div>
            `;

            card.querySelector('[data-open-crate]').addEventListener('click', () => {
                startInteractiveCaseOpening(crate);
            });

            inventoryGrid.appendChild(card);
        });
    }

    // =========================================================================
    // 5-TAP CASE OPENING SEQUENCE + FADE TRANSITION INTO OUTCOME WINDOW
    // =========================================================================
    let pendingOpenCrate = null;
    let currentTapCount = 0;
    let isResolvingCaseOpen = false;

    function startInteractiveCaseOpening(crate) {
        pendingOpenCrate = crate;
        currentTapCount = 0;
        isResolvingCaseOpen = false;

        caseTapImage.src = crate.img;
        caseTapImage.classList.remove('shake');
        caseTapStage.classList.add('active');
        caseOpeningOverlay.classList.remove('fading-out');
        caseOpeningOverlay.classList.add('open');
    }

    if (caseTapStage) {
        caseTapStage.addEventListener('click', async () => {
            if (!pendingOpenCrate || isResolvingCaseOpen) return;

            currentTapCount++;
            caseTapImage.classList.remove('shake');
            void caseTapImage.offsetWidth;
            caseTapImage.classList.add('shake');

            if (currentTapCount < 5) {
                return;
            }

            // 5th tap reached -> resolve backend case opening and fade transition to win window
            isResolvingCaseOpen = true;
            const crateToOpen = pendingOpenCrate;

            try {
                const [res] = await Promise.all([
                    callApi('POST', '/api/users/items/open', {
                        username: currentUser.username,
                        password: currentUser.password,
                        item: crateToOpen.key
                    }),
                    new Promise(r => setTimeout(r, 220))
                ]);

                caseOpeningOverlay.classList.add('fading-out');
                await new Promise(r => setTimeout(r, 300));
                caseOpeningOverlay.classList.remove('open', 'fading-out');

                const won = Number(res.wonAmount) || 0;
                const isJackpot = Boolean(res.jackpot) || res.outcomeTier === 'Jackpot Cap';
                currentUser.items = Array.isArray(res.remainingItems) ? res.remainingItems : [];
                updateBalanceDisplay(res.newBalance);
                renderInventoryGrid();
                await syncTopRichLeaderboard();

                if (caseOutcomeModal) {
                    if (caseOutcomeTitle) {
                        caseOutcomeTitle.textContent = isJackpot ? 'Jackpot!' : 'You won';
                        caseOutcomeTitle.classList.toggle('jackpot', isJackpot);
                    }
                    caseOutcomeUsdtAmount.textContent = `+$${won.toFixed(2)} USDT`;
                    caseOutcomeUsdtAmount.classList.toggle('jackpot', isJackpot);
                    caseOutcomeModal.classList.add('open');
                }
            } catch (_) {
                caseOpeningOverlay.classList.remove('open', 'fading-out');
            } finally {
                isResolvingCaseOpen = false;
                pendingOpenCrate = null;
            }
        });
    }

    if (caseOutcomeOkBtn) {
        caseOutcomeOkBtn.addEventListener('click', () => {
            caseOutcomeModal.classList.remove('open');
        });
    }

    inventorySearchInput.addEventListener('input', renderInventoryGrid);

    inventoryFilterBtn.addEventListener('click', () => {
        currentFilterIdx = (currentFilterIdx + 1) % INVENTORY_FILTER_MODES.length;
        inventoryFilterBtn.textContent = INVENTORY_FILTER_MODES[currentFilterIdx].label;
        renderInventoryGrid();
    });

    // =========================================================================
    // BALANCE MODAL (3-STEP DEPOSIT / WITHDRAWAL FLOW + 15M BACKEND TIMER)
    // =========================================================================
    let depositCountdownTimerId = null;
    let activeDepositExpiresAtMs = null;
    let activeDepositTxHash = null;
    let userHasPendingTransaction = false;
    let userHasApprovedDeposit = false;
    let pendingWithdrawAmount = 0;

    function stopDepositCountdown() {
        if (depositCountdownTimerId) {
            clearInterval(depositCountdownTimerId);
            depositCountdownTimerId = null;
        }
        activeDepositExpiresAtMs = null;
        activeDepositTxHash = null;
    }

    function formatRemainingTime(totalSeconds) {
        const clamped = Math.max(0, Math.floor(totalSeconds));
        const mins = String(Math.floor(clamped / 60)).padStart(2, '0');
        const secs = String(clamped % 60).padStart(2, '0');
        return `${mins}:${secs}`;
    }

    function startDepositCountdown(expiresAtIso, initialRemainingSeconds) {
        stopDepositCountdown();
        const parsedMs = expiresAtIso ? Date.parse(expiresAtIso) : NaN;
        if (!Number.isNaN(parsedMs)) {
            activeDepositExpiresAtMs = parsedMs;
        } else {
            activeDepositExpiresAtMs = Date.now() + (Number(initialRemainingSeconds || 900) * 1000);
        }

        const tick = () => {
            const remainingSec = Math.ceil((activeDepositExpiresAtMs - Date.now()) / 1000);
            if (remainingSec <= 0) {
                depositTimerDisplay.textContent = '00:00';
                stopDepositCountdown();
                showToast('15-minute deposit timer expired. Please start a new deposit.', true);
                setWalletStep(1);
                return;
            }
            depositTimerDisplay.textContent = formatRemainingTime(remainingSec);
        };

        tick();
        depositCountdownTimerId = setInterval(tick, 1000);
    }

    function setWalletStep(stepNumber, mode = 'DEPOSIT') {
        [walletStep1, walletStep2, walletStep3].forEach((el, idx) => {
            if (el) {
                el.classList.toggle('active', idx + 1 === stepNumber);
            }
        });

        if (stepNumber === 1) {
            balanceModalTitle.textContent = 'Balance Wallet';
        } else if (stepNumber === 2) {
            balanceModalTitle.textContent = mode === 'WITHDRAW' ? 'Withdraw (USDT / Ton)' : 'Deposit (USDT / Ton)';
            step2DepositPanel.classList.toggle('active', mode === 'DEPOSIT');
            step2WithdrawPanel.classList.toggle('active', mode === 'WITHDRAW');
        } else if (stepNumber === 3) {
            balanceModalTitle.textContent = 'Confirmation';
        }

        // Update mini-progress bar
        walletProgressFill.className = `wallet-progress-fill step-${stepNumber}`;
        document.querySelectorAll('[data-step-indicator]').forEach(ind => {
            const s = Number(ind.dataset.stepIndicator);
            ind.classList.toggle('active', s <= stepNumber);
        });
    }

    async function checkAndRestoreActiveDeposit(openModalIfActive = false) {
        if (!currentUser.username) return false;
        try {
            const query = new URLSearchParams({
                username: currentUser.username,
                password: currentUser.password
            }).toString();
            const session = await callApi('GET', `/api/users/balance/deposit/active?${query}`, null, false);
            userHasPendingTransaction = Boolean(session && session.hasPendingTransaction);
            userHasApprovedDeposit = Boolean(session && session.hasApprovedDeposit);
            if (session && session.active && session.remainingSeconds > 0) {
                activeDepositTxHash = session.txHash || null;
                step2DepositAmountText.textContent = `$${Number(session.amount).toFixed(2)}`;
                depositAddressText.textContent = session.address || 'UQCys13azAWTFuERWwjc_5sqKZxQEhJhbtGGaiyp2Sf55Hrg';
                setWalletStep(2, 'DEPOSIT');
                startDepositCountdown(session.expiresAt, session.remainingSeconds);
                if (openModalIfActive) {
                    balanceModal.classList.add('open');
                }
                return true;
            }
        } catch (_) {}
        return false;
    }

    openBalanceModalBtn.addEventListener('click', async () => {
        const hasActive = await checkAndRestoreActiveDeposit(false);
        if (!hasActive) {
            modalBalanceAmount.value = '0';
            setWalletStep(1);
        }
        balanceModal.classList.add('open');
    });

    closeBalanceModalBtn.addEventListener('click', () => {
        balanceModal.classList.remove('open');
    });

    document.querySelectorAll('.modal-chip').forEach(chip => {
        chip.addEventListener('click', () => {
            modalBalanceAmount.value = Number(chip.dataset.amt);
        });
    });

    // Copy Deposit Address helper
    async function copyDepositAddress() {
        const address = depositAddressText.textContent.trim();
        try {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                await navigator.clipboard.writeText(address);
            } else {
                const temp = document.createElement('textarea');
                temp.value = address;
                document.body.appendChild(temp);
                temp.select();
                document.execCommand('copy');
                document.body.removeChild(temp);
            }
            copyDepositAddressBtn.textContent = 'Copied!';
            setTimeout(() => {
                copyDepositAddressBtn.textContent = 'Copy';
            }, 1500);
            showToast('Address copied to clipboard');
        } catch (_) {
            showToast('Failed to copy address', true);
        }
    }

    copyAddressBox.addEventListener('click', copyDepositAddress);

    // STEP 1 -> STEP 2 (DEPOSIT): Starts 15-min timer on backend (min $5.00 USDT, blocked if user already has PENDING tx)
    modalDepositBtn.addEventListener('click', async () => {
        try {
            const amount = Number(modalBalanceAmount.value);
            if (!amount || amount < 5) {
                showToast('Minimum deposit amount is $5.00 USDT', true);
                return;
            }
            const session = await callApi('POST', '/api/users/balance/deposit/start', {
                username: currentUser.username,
                password: currentUser.password,
                amount
            });
            activeDepositTxHash = session.txHash || null;
            step2DepositAmountText.textContent = `$${Number(session.amount).toFixed(2)}`;
            depositAddressText.textContent = session.address || 'UQCys13azAWTFuERWwjc_5sqKZxQEhJhbtGGaiyp2Sf55Hrg';
            setWalletStep(2, 'DEPOSIT');
            startDepositCountdown(session.expiresAt, session.remainingSeconds);
        } catch (_) {}
    });

    // STEP 2 -> STEP 3 (DEPOSIT "Sent" button): Promotes deposit to PENDING for Admin
    depositSentBtn.addEventListener('click', async () => {
        try {
            const txHash = activeDepositTxHash;
            await callApi('POST', '/api/users/balance/deposit/confirm', {
                username: currentUser.username,
                password: currentUser.password,
                txHash
            });
            stopDepositCountdown();
            userHasPendingTransaction = true;
            await syncBalanceFromGet();
            setWalletStep(3);
            showToast('Deposit marked as Sent! Waiting for Admin approval.');
        } catch (_) {}
    });

    // STEP 1 -> STEP 2 (WITHDRAW): Validates min $10 USDT, min 1 approved deposit, & no PENDING tx before opening Withdraw Step 2
    modalWithdrawBtn.addEventListener('click', async () => {
        const amount = Number(modalBalanceAmount.value);
        if (!amount || amount < 10) {
            showToast('Minimum withdrawal amount is $10.00 USDT', true);
            return;
        }
        await checkAndRestoreActiveDeposit(false);
        if (!userHasApprovedDeposit) {
            showToast('You must have at least 1 approved deposit on your account before withdrawing', true);
            return;
        }
        if (userHasPendingTransaction) {
            showToast('You already have a pending transaction awaiting admin review', true);
            return;
        }
        if (amount > currentUser.balance) {
            showToast(`Insufficient balance ($${currentUser.balance.toFixed(2)})`, true);
            return;
        }
        pendingWithdrawAmount = amount;
        step2WithdrawAmountText.textContent = `$${amount.toFixed(2)}`;
        withdrawWalletAddressInput.value = '';
        withdrawWalletAddressInput.classList.remove('input-error');
        setWalletStep(2, 'WITHDRAW');
    });

    withdrawWalletAddressInput.addEventListener('input', () => {
        if (withdrawWalletAddressInput.value.trim()) {
            withdrawWalletAddressInput.classList.remove('input-error');
        }
    });

    // STEP 2 -> STEP 3 (WITHDRAW "Sent" button): Requires non-empty TON address, deducts balance immediately & creates PENDING withdrawal
    withdrawSentBtn.addEventListener('click', async () => {
        try {
            const walletAddress = withdrawWalletAddressInput.value.trim();
            if (!walletAddress) {
                withdrawWalletAddressInput.classList.add('input-error');
                withdrawWalletAddressInput.focus();
                showToast('Please enter your USDT (Ton) wallet address', true);
                return;
            }
            await callApi('POST', '/api/users/balance/withdraw', {
                username: currentUser.username,
                password: currentUser.password,
                amount: pendingWithdrawAmount,
                walletAddress
            });
            await syncBalanceFromGet();
            await syncTopRichLeaderboard();
            setWalletStep(3);
            showToast(`Withdrawal ($${pendingWithdrawAmount.toFixed(2)}) deducted & pending Admin approval`);
        } catch (_) {}
    });

    // STEP 3 ("Exit" button): Closes modal and resets to Step 1
    walletDoneExitBtn.addEventListener('click', () => {
        balanceModal.classList.remove('open');
        modalBalanceAmount.value = '0';
        setWalletStep(1);
    });

    // =========================================================================
    // TAB 3: PROFILE ACTIONS & PROMO FIELD ("admin1234")
    // =========================================================================
    async function handlePromoSubmit() {
        const code = promoInput.value.trim();
        promoFeedback.textContent = '';
        try {
            const res = await callApi('POST', '/api/users/promo', { promo: code }, false);
            if (res && res.valid) {
                saveSession();
                window.location.href = '/admin.html';
            }
        } catch (err) {
            promoFeedback.textContent = 'Promo is not found';
        }
    }

    promoSubmitBtn.addEventListener('click', handlePromoSubmit);
    promoInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            handlePromoSubmit();
        }
    });

    changePasswordBtn.addEventListener('click', () => {
        if (currentPasswordInput) {
            currentPasswordInput.value = '';
            currentPasswordInput.classList.remove('input-error');
        }
        newPasswordInput.value = '';
        newPasswordInput.classList.remove('input-error');
        passwordModal.classList.add('open');
    });

    closePasswordModalBtn.addEventListener('click', () => {
        passwordModal.classList.remove('open');
    });

    if (currentPasswordInput) {
        currentPasswordInput.addEventListener('input', () => {
            currentPasswordInput.classList.remove('input-error');
        });
    }

    newPasswordInput.addEventListener('input', () => {
        newPasswordInput.classList.remove('input-error');
    });

    confirmPasswordBtn.addEventListener('click', async () => {
        try {
            const currentPassword = currentPasswordInput ? currentPasswordInput.value : '';
            const newPassword = newPasswordInput.value;

            if (!currentPassword) {
                if (currentPasswordInput) {
                    currentPasswordInput.classList.add('input-error');
                    currentPasswordInput.focus();
                }
                showToast('Please enter your current password', true);
                return;
            }

            if (currentPassword !== currentUser.password) {
                if (currentPasswordInput) {
                    currentPasswordInput.classList.add('input-error');
                    currentPasswordInput.focus();
                }
                showToast('Current password does not match', true);
                return;
            }

            if (!newPassword || !newPassword.trim()) {
                newPasswordInput.classList.add('input-error');
                newPasswordInput.focus();
                showToast('Please enter a new password', true);
                return;
            }

            await callApi('POST', '/api/users/password', {
                username: currentUser.username,
                password: currentPassword,
                newPassword
            });
            currentUser.password = newPassword;
            authPassword.value = newPassword;
            saveSession();
            updateProfileUI();
            passwordModal.classList.remove('open');
            showToast('Password updated successfully');
        } catch (_) {}
    });

    function closeLogoutModal() {
        if (logoutModal) {
            logoutModal.classList.remove('open');
        }
    }

    logoutBtn.addEventListener('click', () => {
        if (logoutModal) {
            logoutModal.classList.add('open');
        }
    });

    if (closeLogoutModalBtn) {
        closeLogoutModalBtn.addEventListener('click', closeLogoutModal);
    }

    if (cancelLogoutBtn) {
        cancelLogoutBtn.addEventListener('click', closeLogoutModal);
    }

    if (confirmLogoutBtn) {
        confirmLogoutBtn.addEventListener('click', async () => {
            try {
                await callApi('POST', '/api/users/logout', {
                    username: currentUser.username,
                    password: currentUser.password
                });
                closeLogoutModal();
                stopDepositCountdown();
                balanceModal.classList.remove('open');
                clearSession();
                playerScreen.classList.remove('active');
                welcomeScreen.classList.add('active');
                showToast('Logged out');
            } catch (_) {}
        });
    }

    function closeDeleteAccountModal() {
        if (deleteAccountModal) {
            deleteAccountModal.classList.remove('open');
        }
        if (deleteAccountConfirmInput) {
            deleteAccountConfirmInput.value = '';
            deleteAccountConfirmInput.classList.remove('input-error');
        }
    }

    deleteAccountBtn.addEventListener('click', () => {
        if (deleteAccountConfirmInput) {
            deleteAccountConfirmInput.value = '';
            deleteAccountConfirmInput.classList.remove('input-error');
        }
        if (deleteAccountModal) {
            deleteAccountModal.classList.add('open');
        }
    });

    if (closeDeleteAccountModalBtn) {
        closeDeleteAccountModalBtn.addEventListener('click', closeDeleteAccountModal);
    }

    if (cancelDeleteAccountBtn) {
        cancelDeleteAccountBtn.addEventListener('click', closeDeleteAccountModal);
    }

    if (deleteAccountConfirmInput) {
        deleteAccountConfirmInput.addEventListener('input', () => {
            if (deleteAccountConfirmInput.value.trim() === DELETE_ACCOUNT_AGREEMENT_SENTENCE) {
                deleteAccountConfirmInput.classList.remove('input-error');
            }
        });
    }

    if (confirmDeleteAccountBtn) {
        confirmDeleteAccountBtn.addEventListener('click', async () => {
            const typed = deleteAccountConfirmInput ? deleteAccountConfirmInput.value.trim() : '';
            if (typed !== DELETE_ACCOUNT_AGREEMENT_SENTENCE) {
                if (deleteAccountConfirmInput) {
                    deleteAccountConfirmInput.classList.add('input-error');
                    deleteAccountConfirmInput.focus();
                }
                showToast(`Please type the exact agreement sentence: "${DELETE_ACCOUNT_AGREEMENT_SENTENCE}"`, true);
                return;
            }

            try {
                await callApi('POST', '/api/users/delete', {
                    username: currentUser.username,
                    password: currentUser.password
                });
                closeDeleteAccountModal();
                stopDepositCountdown();
                balanceModal.classList.remove('open');
                clearSession();
                authUsername.value = '';
                authPassword.value = '';
                playerScreen.classList.remove('active');
                welcomeScreen.classList.add('active');
                showToast('Account deleted');
            } catch (_) {}
        });
    }

    // =========================================================================
    // AUTO-RESTORE LOGGED-IN SESSION (e.g. WHEN EXITING FROM ADMIN PAGE)
    // =========================================================================
    (async function restoreSessionIfPresent() {
        const raw = sessionStorage.getItem(SESSION_STORAGE_KEY);
        if (!raw) return;
        try {
            const saved = JSON.parse(raw);
            if (saved && saved.username && saved.password) {
                const query = new URLSearchParams({
                    username: saved.username,
                    password: saved.password
                }).toString();
                const user = await callApi('GET', `/api/users/user?${query}`, null, false);
                await enterPlayerPortal(user, saved.password);
            }
        } catch (_) {
            clearSession();
        }
    })();
})();
