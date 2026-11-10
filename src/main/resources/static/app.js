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
    const depositAdminUsernameText = document.getElementById('depositAdminUsernameText');
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

    const caseRouletteModal = document.getElementById('caseRouletteModal');
    const caseRouletteStage = document.getElementById('caseRouletteStage');
    const rouletteCaseName = document.getElementById('rouletteCaseName');
    const rouletteStatusBadge = document.getElementById('rouletteStatusBadge');
    const caseRouletteViewport = document.getElementById('caseRouletteViewport');
    const caseRouletteTrack = document.getElementById('caseRouletteTrack');

    const caseOutcomeModal = document.getElementById('caseOutcomeModal');
    const caseOutcomeTitle = document.getElementById('caseOutcomeTitle');
    const caseOutcomeOkBtn = document.getElementById('caseOutcomeOkBtn');
    const caseOutcomeUsdtAmount = document.getElementById('caseOutcomeUsdtAmount');
    const caseOutcomeBody = caseOutcomeModal ? caseOutcomeModal.querySelector('.case-outcome-body') : null;

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

    async function loadAppConfig() {
        try {
            const config = await callApi('GET', '/api/config', null, false);
            if (config && config.depositAdminUsername && depositAdminUsernameText) {
                depositAdminUsernameText.textContent = config.depositAdminUsername;
            }
        } catch (_) {}
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
        await loadAppConfig();
        await loadCasesCatalog();
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
                    await loadCasesCatalog();
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
    // TAB 1 & TAB 1B: GAMES & DYNAMIC CASES MARKETPLACE INTERACTION
    // =========================================================================
    let casesCatalog = [];

    async function loadCasesCatalog() {
        try {
            const data = await callApi('GET', '/api/cases');
            if (Array.isArray(data)) {
                casesCatalog = data.map(c => ({
                    id: c.id,
                    slug: c.slug,
                    name: c.name || c.slug,
                    price: Number(c.price) || 0,
                    imageUrl: c.imageUrl || c.image_url || '/imgs/cases/case-fallback.png',
                    createdAt: c.createdAt || c.created_at
                }));
            }
        } catch (_) {}
    }

    async function openCasesMarketplace() {
        document.querySelectorAll('.tab-view').forEach(v => v.classList.remove('active'));
        const casesTab = document.getElementById('tab-cases');
        if (casesTab) {
            casesTab.classList.add('active');
        }
        await loadCasesCatalog();
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

        let list = casesCatalog.filter(crate => {
            if (activeFilter.code === 'AFFORDABLE' && currentUser.balance < crate.price) {
                return false;
            }
            if (
                searchQuery &&
                !crate.name.toLowerCase().includes(searchQuery) &&
                !crate.slug.toLowerCase().includes(searchQuery)
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
            const displayLabel = crate.name;

            card.innerHTML = `
                <div class="case-image-slot">
                    <img src="${crate.imageUrl}" alt="${displayLabel}" class="case-art-img" />
                </div>
                <div class="case-bottom-footer">
                    <div class="case-title">${displayLabel}</div>
                    <div class="case-buy-row">
                        <span class="case-price">$${crate.price}</span>
                        <button type="button" class="case-buy-btn" data-buy-crate="${crate.slug}">BUY</button>
                    </div>
                </div>
            `;

            card.querySelector('[data-buy-crate]').addEventListener('click', async () => {
                try {
                    await callApi('POST', '/api/users/items/buy', {
                        username: currentUser.username,
                        password: currentUser.password,
                        item: crate.slug
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
        const counts = {};
        (currentUser.items || []).forEach(slug => {
            counts[slug] = (counts[slug] || 0) + 1;
        });

        const searchQuery = inventorySearchInput.value.trim().toLowerCase();
        const activeFilter = INVENTORY_FILTER_MODES[currentFilterIdx];

        let filtered = casesCatalog.filter(crate => {
            const count = counts[crate.slug] || 0;
            // Only display cases that the user actually owns
            if (count <= 0) return false;
            if (
                searchQuery &&
                !crate.name.toLowerCase().includes(searchQuery) &&
                !crate.slug.toLowerCase().includes(searchQuery)
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
            const displayLabel = crate.name;
            card.innerHTML = `
                <div class="case-image-slot">
                    <img src="${crate.imageUrl}" alt="${displayLabel}" class="case-art-img" />
                </div>
                <div class="case-bottom-footer">
                    <div class="case-title">${displayLabel}</div>
                    <div class="case-buy-row">
                        <span class="case-count-inline">x${counts[crate.slug]}</span>
                        <button type="button" class="case-buy-btn" data-open-crate="${crate.slug}">Open</button>
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
    // CS:GO / CSFAIL HORIZONTAL CAROUSEL REEL ROULETTE (100 SLOTS, INDEX 96 WIN)
    // =========================================================================
    const TOTAL_ROULETTE_SLOTS = 100;
    const WINNING_SLOT_INDEX = 96;
    const ROULETTE_SPIN_DURATION_MS = 6000;

    let isResolvingCaseOpen = false;

    function formatMultiplierLabel(multiplier) {
        const num = Number(multiplier);
        if (!Number.isFinite(num) || num <= 0) return '0x';
        const formatted = Number(num.toFixed(2));
        return `${formatted}x`;
    }

    const SLOT_TIER_BY_LABEL = {
        'COMMON SCRAP': { className: 'tier-floor', tag: 'COMMON SCRAP' },
        'MINOR RETURN': { className: 'tier-common', tag: 'MINOR RETURN' },
        'NEAR EVEN': { className: 'tier-even', tag: 'NEAR EVEN' },
        'SMALL WIN': { className: 'tier-profit', tag: 'SMALL WIN' },
        'NICE WIN': { className: 'tier-rare', tag: 'NICE WIN' },
        'TOP PRIZE': { className: 'tier-jackpot', tag: 'TOP PRIZE' }
    };

    function classifySlotTier(itemOrLabel) {
        const rawLabel = typeof itemOrLabel === 'string'
            ? itemOrLabel
            : (itemOrLabel && typeof itemOrLabel.label === 'string' ? itemOrLabel.label : '');
        const normalized = rawLabel.replace(/\s*\([^)]*\)\s*$/, '').trim().toUpperCase();
        if (SLOT_TIER_BY_LABEL[normalized]) {
            return SLOT_TIER_BY_LABEL[normalized];
        }
        return {
            className: 'tier-floor',
            tag: normalized || 'COMMON SCRAP'
        };
    }

    function drawWeightedPaytableItem(paytable) {
        const totalWeight = paytable.reduce((acc, item) => acc + (Number(item.probability) || 0), 0);
        if (totalWeight <= 0) {
            return paytable[Math.floor(Math.random() * paytable.length)];
        }
        let roll = Math.random() * totalWeight;
        for (let i = 0; i < paytable.length; i++) {
            roll -= Number(paytable[i].probability) || 0;
            if (roll <= 0) {
                return paytable[i];
            }
        }
        return paytable[paytable.length - 1];
    }

    function buildRouletteTape(rawPaytable, winningMultiplier, winningTierLabel) {
        const paytable = Array.isArray(rawPaytable) && rawPaytable.length > 0
            ? rawPaytable
            : [{ multiplier: winningMultiplier || 1, probability: 1.0, label: winningTierLabel || 'Win' }];

        // Build indexes 0..95 (96 slots) weighted by paytable probabilities, then shuffle
        const prefixCount = WINNING_SLOT_INDEX; // 96 slots (indices 0..95)
        const weightedPrefix = [];

        paytable.forEach(tier => {
            const prob = Math.max(0, Number(tier.probability) || 0);
            const exactCount = Math.floor(prob * prefixCount);
            for (let i = 0; i < exactCount && weightedPrefix.length < prefixCount; i++) {
                weightedPrefix.push({
                    multiplier: Number(tier.multiplier),
                    label: tier.label || ''
                });
            }
        });

        while (weightedPrefix.length < prefixCount) {
            const picked = drawWeightedPaytableItem(paytable);
            weightedPrefix.push({
                multiplier: Number(picked.multiplier),
                label: picked.label || ''
            });
        }

        // Fisher-Yates shuffle on indexes 0..95 for natural weighted flow
        for (let i = weightedPrefix.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            const tmp = weightedPrefix[i];
            weightedPrefix[i] = weightedPrefix[j];
            weightedPrefix[j] = tmp;
        }

        const tape = new Array(TOTAL_ROULETTE_SLOTS);
        for (let i = 0; i < WINNING_SLOT_INDEX; i++) {
            tape[i] = weightedPrefix[i];
        }

        // Strictly place the predetermined winning item at index 96 (97th slot)
        tape[WINNING_SLOT_INDEX] = {
            multiplier: Number(winningMultiplier),
            label: winningTierLabel || '',
            isWinner: true
        };

        // Fill indexes 97..99 with random paytable items so strip never cuts off past the marker
        for (let i = WINNING_SLOT_INDEX + 1; i < TOTAL_ROULETTE_SLOTS; i++) {
            const picked = drawWeightedPaytableItem(paytable);
            tape[i] = {
                multiplier: Number(picked.multiplier),
                label: picked.label || ''
            };
        }

        return tape;
    }

    function setSpinningLockState(locked) {
        const interactiveControls = document.querySelectorAll(
            '[data-open-crate], #inventorySearchInput, #inventoryFilterBtn, .nav-tab, #openBalanceModalBtn'
        );
        interactiveControls.forEach(el => {
            if (locked) {
                el.classList.add('spinning-disabled');
                if ('disabled' in el) el.disabled = true;
            } else {
                el.classList.remove('spinning-disabled');
                if ('disabled' in el) el.disabled = false;
            }
        });
    }

    async function startInteractiveCaseOpening(crate) {
        if (isResolvingCaseOpen || !crate) return;
        isResolvingCaseOpen = true;
        setSpinningLockState(true);

        if (caseOutcomeModal) {
            caseOutcomeModal.classList.remove('open');
        }

        try {
            // 1. Backend execution: deduct crate & deterministically select winning outcome from paytable
            const res = await callApi('POST', '/api/users/items/open', {
                username: currentUser.username,
                password: currentUser.password,
                item: crate.slug
            });

            const wonAmount = Number(res.wonAmount) || 0;
            const wonMultiplier = res.wonMultiplier !== undefined && res.wonMultiplier !== null
                ? Number(res.wonMultiplier)
                : (crate.price > 0 ? wonAmount / crate.price : 1);
            const isJackpot = Boolean(res.jackpot) || res.outcomeTier === 'TOP PRIZE' || res.outcomeTier === 'Jackpot Cap';
            const paytable = Array.isArray(res.paytable) ? res.paytable : [];

            // Update inventory count immediately so opened crate is consumed visually
            currentUser.items = Array.isArray(res.remainingItems) ? res.remainingItems : [];
            renderInventoryGrid();
            setSpinningLockState(true);

            // 2. Construct 100-slot tape with winning outcome strictly at index 96
            const tape = buildRouletteTape(paytable, wonMultiplier, res.outcomeTier);

            if (rouletteCaseName) {
                rouletteCaseName.textContent = crate.name || crate.slug;
            }
            if (rouletteStatusBadge) {
                rouletteStatusBadge.textContent = 'SPINNING...';
                rouletteStatusBadge.classList.remove('completed');
            }

            // 3. Mount 100 slot cards and open centered modal roulette overlay
            caseRouletteTrack.style.transition = 'none';
            caseRouletteTrack.style.transform = 'translateX(0px)';
            caseRouletteTrack.innerHTML = '';

            const fragment = document.createDocumentFragment();
            tape.forEach((slot, idx) => {
                const tierMeta = classifySlotTier(slot);
                const slotEl = document.createElement('div');
                slotEl.className = `roulette-slot-card ${tierMeta.className}`;
                slotEl.dataset.slotIndex = String(idx);
                slotEl.innerHTML = `
                    <div class="roulette-slot-multiplier">${formatMultiplierLabel(slot.multiplier)}</div>
                    <div class="roulette-slot-tag">${tierMeta.tag}</div>
                `;
                fragment.appendChild(slotEl);
            });
            caseRouletteTrack.appendChild(fragment);

            if (caseRouletteModal) {
                caseRouletteModal.classList.add('open');
            }

            // 4. Measure unscaled container width, size 5 visible cards, trigger reflow & compute exact targetX
            void caseRouletteViewport.offsetWidth;

            const W = WINNING_SLOT_INDEX;
            const rouletteContainerWidth = caseRouletteViewport.clientWidth;
            const computedStyle = window.getComputedStyle(caseRouletteTrack);
            const gap = parseFloat(computedStyle.gap || computedStyle.columnGap || '8') || 8;
            const cardWidth = (rouletteContainerWidth - (4 * gap)) / 5;

            caseRouletteTrack.style.setProperty('--roulette-card-width', `${cardWidth}px`);
            void caseRouletteTrack.offsetWidth;

            const itemTotalWidth = cardWidth + gap;
            const cardCenterPosition = (W * itemTotalWidth) + (cardWidth / 2);
            const viewportCenter = rouletteContainerWidth / 2;
            const jitter = (Math.random() * 0.4 - 0.2) * cardWidth;
            const targetX = cardCenterPosition - viewportCenter + jitter;

            // 5. Animate translateX(-targetX px) over 6s cubic-bezier(0.15, 0.9, 0.25, 1)
            caseRouletteTrack.style.transition = 'transform 6s cubic-bezier(0.15, 0.9, 0.25, 1)';
            caseRouletteTrack.style.transform = `translateX(-${targetX}px)`;

            await new Promise((resolve) => {
                let settled = false;
                const finish = () => {
                    if (settled) return;
                    settled = true;
                    caseRouletteTrack.removeEventListener('transitionend', onTransitionEnd);
                    resolve();
                };
                const onTransitionEnd = (e) => {
                    if (e.target === caseRouletteTrack && e.propertyName === 'transform') {
                        finish();
                    }
                };
                caseRouletteTrack.addEventListener('transitionend', onTransitionEnd);
                setTimeout(finish, ROULETTE_SPIN_DURATION_MS + 80);
            });

            // 6. Highlight winning slot 96, sync balance & leaderboard, unlock UI, and fade in win modal
            const winningSlotEl = caseRouletteTrack.children[WINNING_SLOT_INDEX];
            if (winningSlotEl) {
                winningSlotEl.classList.add('winning-slot-highlight');
            }
            if (rouletteStatusBadge) {
                rouletteStatusBadge.textContent = `WON ${formatMultiplierLabel(wonMultiplier)}`;
                rouletteStatusBadge.classList.add('completed');
            }

            updateBalanceDisplay(res.newBalance);
            await syncTopRichLeaderboard();
            await new Promise(r => setTimeout(r, 280));

            if (caseRouletteModal) {
                caseRouletteModal.classList.remove('open');
            }
            setSpinningLockState(false);

            if (caseOutcomeModal) {
                if (caseOutcomeTitle) {
                    caseOutcomeTitle.textContent = isJackpot ? 'Jackpot!' : 'You won';
                    caseOutcomeTitle.classList.toggle('jackpot', isJackpot);
                    caseOutcomeTitle.classList.remove('reveal-hidden');
                    caseOutcomeTitle.classList.add('reveal-visible');
                }
                if (caseOutcomeBody) {
                    caseOutcomeBody.classList.remove('reveal-hidden');
                    caseOutcomeBody.classList.add('reveal-visible');
                }
                caseOutcomeUsdtAmount.textContent = wonAmount > 0
                    ? `+$${wonAmount.toFixed(2)} USDT (${formatMultiplierLabel(wonMultiplier)})`
                    : `$0.00 USDT`;
                caseOutcomeUsdtAmount.classList.toggle('jackpot', isJackpot);
                caseOutcomeUsdtAmount.classList.remove('reveal-hidden');
                caseOutcomeUsdtAmount.classList.add('reveal-visible');

                if (caseOutcomeOkBtn) {
                    caseOutcomeOkBtn.classList.remove('ok-btn-hidden');
                    caseOutcomeOkBtn.classList.add('ok-btn-visible');
                }

                caseOutcomeModal.classList.add('open');
            }
        } catch (_) {
            if (caseRouletteModal) {
                caseRouletteModal.classList.remove('open');
            }
            setSpinningLockState(false);
        } finally {
            isResolvingCaseOpen = false;
        }
    }

    if (caseOutcomeOkBtn) {
        caseOutcomeOkBtn.addEventListener('click', () => {
            caseOutcomeModal.classList.remove('open');
            if (caseRouletteModal) {
                caseRouletteModal.classList.remove('open');
            }
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
            if (session && session.adminUsername && depositAdminUsernameText) {
                depositAdminUsernameText.textContent = session.adminUsername;
            }
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

    // STEP 1 -> STEP 2 (DEPOSIT): Starts 15-min timer on backend (min $1.00 USDT, blocked if user already has PENDING tx)
    modalDepositBtn.addEventListener('click', async () => {
        try {
            const amount = Number(modalBalanceAmount.value);
            if (!amount || amount < 1) {
                showToast('Minimum deposit amount is $1.00 USDT', true);
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
            if (session.adminUsername && depositAdminUsernameText) {
                depositAdminUsernameText.textContent = session.adminUsername;
            }
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

    // STEP 1 -> STEP 2 (WITHDRAW): Validates min $2.50 USDT, min 1 approved deposit, & no PENDING tx before opening Withdraw Step 2
    modalWithdrawBtn.addEventListener('click', async () => {
        const amount = Number(modalBalanceAmount.value);
        if (!amount || amount < 2.5) {
            showToast('Minimum withdrawal amount is $2.50 USDT', true);
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
    // TAB 3: PROFILE ACTIONS & PROMO FIELD
    // =========================================================================
    async function handlePromoSubmit() {
        const code = promoInput.value.trim();
        promoFeedback.textContent = '';
        try {
            // Attempt admin login via POST /api/admin/login to issue the 4-hour ADMIN_SESSION cookie
            const loginRes = await fetch('/api/admin/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ password: code })
            });

            if (loginRes.ok) {
                saveSession();
                sessionStorage.setItem('adminKey', code);
                window.location.href = '/admin';
                return;
            }

            // Fallback for promo verification
            const res = await callApi('POST', '/api/users/promo', { promo: code }, false);
            if (res && res.valid) {
                saveSession();
                sessionStorage.setItem('adminKey', code);
                window.location.href = '/admin';
                return;
            }
            promoFeedback.textContent = 'Promo is not found';
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
        await loadAppConfig();
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