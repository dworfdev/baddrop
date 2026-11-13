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
    const topbarAvatarBtn = document.getElementById('topbarAvatarBtn');

    // DOM Elements - Games & Cases Marketplace View
    const liveActivityBar = document.getElementById('liveActivityBar');
    const liveTickerTrack = document.getElementById('liveTickerTrack');
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
    const profileDepositBtn = document.getElementById('profileDepositBtn');
    const profileWithdrawBtn = document.getElementById('profileWithdrawBtn');
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
    const walletStep1Actions = document.getElementById('walletStep1Actions');
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
    const caseRouletteReelsStack = document.getElementById('caseRouletteReelsStack');
    const caseRouletteViewport = document.getElementById('caseRouletteViewport');
    const caseRouletteTrack = document.getElementById('caseRouletteTrack');
    const rouletteSkipBtn = document.getElementById('rouletteSkipBtn');

    const chooseAmountModal = document.getElementById('chooseAmountModal');
    const closeChooseAmountModalBtn = document.getElementById('closeChooseAmountModalBtn');
    const chooseAmountOptionsRow = document.getElementById('chooseAmountOptionsRow');

    const caseInspectModal = document.getElementById('caseInspectModal');
    const caseInspectTitle = document.getElementById('caseInspectTitle');
    const closeCaseInspectModalBtn = document.getElementById('closeCaseInspectModalBtn');
    const caseInspectTableBody = document.getElementById('caseInspectTableBody');

    const caseOutcomeModal = document.getElementById('caseOutcomeModal');
    const caseOutcomeBgVideo = document.getElementById('caseOutcomeBgVideo');
    const caseOutcomeTitleImg = document.getElementById('caseOutcomeTitleImg');
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

    let liveFeedPollIntervalId = null;

    function resolveCaseDisplayName(rawCaseNameOrSlug) {
        const raw = String(rawCaseNameOrSlug || '').trim();
        if (!raw) return 'CASE';
        const match = casesCatalog.find(
            c => c && (c.slug === raw || String(c.name || '').toUpperCase() === raw.toUpperCase())
        );
        const fullTitle = match && match.name ? String(match.name).trim() : raw;
        const stripped = fullTitle.replace(/\s+CASE$/i, '').trim();
        return stripped || fullTitle;
    }

    function formatLiveFeedEventText(ev) {
        if (!ev || !ev.username) return '';
        const username = String(ev.username).trim();
        const rawAmount = ev.wonAmount !== undefined && ev.wonAmount !== null ? ev.wonAmount : ev.amount;
        const amountStr = (Number(rawAmount) || 0).toFixed(2);
        const caseName = resolveCaseDisplayName(ev.caseName || ev.openedCase);
        return `${username} won $${amountStr} from ${caseName}`;
    }

    function ensureLiveFeedPolling() {
        if (!liveFeedPollIntervalId) {
            liveFeedPollIntervalId = setInterval(() => {
                if (playerScreen && playerScreen.classList.contains('active')) {
                    syncTopRichLeaderboard();
                }
            }, 12000);
        }
    }

    async function syncTopRichLeaderboard() {
        ensureLiveFeedPolling();
        if (!liveActivityBar || !liveTickerTrack) return;
        try {
            const events = await callApi('GET', '/api/live-feed', null, false);
            if (!Array.isArray(events) || events.length === 0) {
                liveTickerTrack.innerHTML = '';
                liveActivityBar.style.display = 'none';
                return;
            }
            liveTickerTrack.innerHTML = '';
            let renderedCount = 0;
            events.slice(0, 2).forEach(ev => {
                const text = formatLiveFeedEventText(ev);
                if (!text) return;
                const tierMeta = classifySlotTier(ev.outcomeTier || '');
                const pill = document.createElement('div');
                pill.className = `live-event-pill ${tierMeta.className}`;
                pill.textContent = text;
                liveTickerTrack.appendChild(pill);
                renderedCount++;
            });
            liveActivityBar.style.display = renderedCount > 0 ? 'flex' : 'none';
        } catch (_) {
            if (!liveTickerTrack.children.length) {
                liveActivityBar.style.display = 'none';
            }
        }
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

    if (topbarAvatarBtn) {
        topbarAvatarBtn.addEventListener('click', () => {
            const profileNavBtn = document.querySelector('.nav-tab[data-target="tab-profile"]');
            if (profileNavBtn) {
                profileNavBtn.click();
            }
        });
    }

    // =========================================================================
    // TAB 1 & TAB 1B: GAMES & DYNAMIC CASES MARKETPLACE INTERACTION
    // =========================================================================
    let casesCatalog = [];

    async function loadCasesCatalog() {
        try {
            const [data, mathData] = await Promise.all([
                callApi('GET', '/api/cases'),
                callApi('GET', '/api/cases/math', null, false).catch(() => [])
            ]);
            const paytableBySlug = {};
            if (Array.isArray(mathData)) {
                mathData.forEach(item => {
                    if (item && item.slug && Array.isArray(item.paytable)) {
                        paytableBySlug[item.slug] = item.paytable.map(o => ({
                            multiplier: Number(o.multiplier) || 0,
                            probability: Number(o.probability) || 0,
                            label: o.tierLabel || o.label || ''
                        }));
                    }
                });
            }
            if (Array.isArray(data)) {
                casesCatalog = data.map(c => ({
                    id: c.id,
                    slug: c.slug,
                    name: c.name || c.slug,
                    price: Number(c.price) || 0,
                    imageUrl: c.imageUrl || c.image_url || '/imgs/cases/case-fallback.png',
                    createdAt: c.createdAt || c.created_at,
                    paytable: Array.isArray(c.paytable) ? c.paytable : (paytableBySlug[c.slug] || [])
                }));
            }
        } catch (_) {}
    }

    function formatInspectMultiplier(multiplier) {
        const num = Number(multiplier);
        if (!Number.isFinite(num) || num <= 0) return '0.0x';
        const formatted = Number.isInteger(num) ? num.toFixed(1) : String(Number(num.toFixed(2)));
        return `${formatted}x`;
    }

    function formatInspectChance(probability) {
        const pct = (Number(probability) || 0) * 100;
        if (!Number.isFinite(pct) || pct <= 0) return '0%';
        const cleaned = Number(pct.toFixed(4));
        return `${cleaned}%`;
    }

    async function openCaseInspectModal(crate) {
        if (!crate || !caseInspectModal) return;

        let paytable = Array.isArray(crate.paytable) && crate.paytable.length > 0 ? crate.paytable : [];
        if (paytable.length === 0) {
            try {
                const mathData = await callApi('GET', '/api/cases/math', null, false);
                if (Array.isArray(mathData)) {
                    const match = mathData.find(m => m && m.slug === crate.slug);
                    if (match && Array.isArray(match.paytable)) {
                        paytable = match.paytable.map(o => ({
                            multiplier: Number(o.multiplier) || 0,
                            probability: Number(o.probability) || 0,
                            label: o.tierLabel || o.label || ''
                        }));
                        crate.paytable = paytable;
                    }
                }
            } catch (_) {}
        }

        if (caseInspectTitle) {
            caseInspectTitle.textContent = crate.name || crate.slug;
        }

        if (caseInspectTableBody) {
            caseInspectTableBody.innerHTML = '';
            const cratePrice = Number(crate.price) || 0;

            paytable.forEach(tier => {
                const mult = Number(tier.multiplier) || 0;
                const prob = Number(tier.probability) || 0;
                const rawLabel = (tier.label || tier.tierLabel || '').replace(/\s*\([^)]*\)\s*$/, '').trim();
                const tierMeta = classifySlotTier(rawLabel);
                const labelText = rawLabel || tierMeta.tag;
                const payoutDollars = (cratePrice * mult).toFixed(2);

                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><span class="inspect-tier-label ${tierMeta.className}">${labelText}</span></td>
                    <td class="inspect-multiplier-cell">${formatInspectMultiplier(mult)}</td>
                    <td class="inspect-payout-cell">$${payoutDollars}</td>
                    <td class="inspect-chance-cell">${formatInspectChance(prob)}</td>
                `;
                caseInspectTableBody.appendChild(tr);
            });
        }

        caseInspectModal.classList.add('open');
    }

    function closeCaseInspectModal() {
        if (caseInspectModal) {
            caseInspectModal.classList.remove('open');
        }
    }

    if (closeCaseInspectModalBtn) {
        closeCaseInspectModalBtn.addEventListener('click', closeCaseInspectModal);
    }
    if (caseInspectModal) {
        caseInspectModal.addEventListener('click', (e) => {
            if (e.target === caseInspectModal) {
                closeCaseInspectModal();
            }
        });
    }

    // =========================================================================
    // QUANTITY SELECTOR STATE & "CHOOSE AMOUNT" MINI-MODAL (x1 .. x5)
    // =========================================================================
    const marketplaceQuantities = {};
    const inventoryQuantities = {};
    let activeQuantityModalContext = null; // { slug, contextType: 'MARKETPLACE' | 'INVENTORY', maxAvailable }

    function getMarketplaceQuantity(slug) {
        const q = Number(marketplaceQuantities[slug]) || 1;
        return Math.min(5, Math.max(1, q));
    }

    function getInventoryQuantity(slug, ownedCount) {
        const maxAllowed = Math.min(5, Math.max(1, Number(ownedCount) || 1));
        const q = Number(inventoryQuantities[slug]) || 1;
        const clamped = Math.min(maxAllowed, Math.max(1, q));
        inventoryQuantities[slug] = clamped;
        return clamped;
    }

    function openChooseAmountModal(slug, contextType = 'MARKETPLACE', maxAvailable = 5) {
        if (!chooseAmountModal || !slug) return;
        const clampedMax = contextType === 'INVENTORY'
            ? Math.min(5, Math.max(1, Number(maxAvailable) || 1))
            : 5;
        const currentQty = contextType === 'INVENTORY'
            ? getInventoryQuantity(slug, clampedMax)
            : getMarketplaceQuantity(slug);

        activeQuantityModalContext = {
            slug,
            contextType,
            maxAvailable: clampedMax
        };

        if (chooseAmountOptionsRow) {
            chooseAmountOptionsRow.querySelectorAll('[data-qty-option]').forEach(btn => {
                const optQty = Number(btn.dataset.qtyOption) || 1;
                const isAllowed = optQty <= clampedMax;
                btn.classList.toggle('active', optQty === currentQty);
                btn.classList.toggle('disabled', !isAllowed);
            });
        }

        chooseAmountModal.classList.add('open');
    }

    function closeChooseAmountModal() {
        if (chooseAmountModal) {
            chooseAmountModal.classList.remove('open');
        }
        activeQuantityModalContext = null;
    }

    if (closeChooseAmountModalBtn) {
        closeChooseAmountModalBtn.addEventListener('click', closeChooseAmountModal);
    }

    if (chooseAmountModal) {
        chooseAmountModal.addEventListener('click', (e) => {
            if (e.target === chooseAmountModal) {
                closeChooseAmountModal();
            }
        });
    }

    if (chooseAmountOptionsRow) {
        chooseAmountOptionsRow.querySelectorAll('[data-qty-option]').forEach(btn => {
            btn.addEventListener('click', () => {
                if (!activeQuantityModalContext) return;
                const optQty = Number(btn.dataset.qtyOption) || 1;
                const { slug, contextType, maxAvailable } = activeQuantityModalContext;

                if (contextType === 'INVENTORY' && optQty > maxAvailable) {
                    showToast(`You only own x${maxAvailable} of this case in your inventory`, true);
                    return;
                }

                if (contextType === 'INVENTORY') {
                    inventoryQuantities[slug] = optQty;
                    renderInventoryGrid();
                } else {
                    marketplaceQuantities[slug] = optQty;
                    renderCasesMarketplaceGrid();
                }

                closeChooseAmountModal();
            });
        });
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
            const selectedQty = getMarketplaceQuantity(crate.slug);
            const totalPrice = Number((crate.price * selectedQty).toFixed(2));

            card.innerHTML = `
                <div class="case-image-slot">
                    <img src="${crate.imageUrl}" alt="${displayLabel}" class="case-art-img" />
                </div>
                <div class="case-bottom-footer">
                    <div class="case-title">${displayLabel}</div>
                    <div class="case-buy-row">
                        <span class="case-price">$${totalPrice}</span>
                        <div class="case-action-group">
                            <button type="button" class="case-view-btn" data-view-crate="${crate.slug}">View</button>
                            <button type="button" class="case-qty-btn" data-qty-crate="${crate.slug}">x${selectedQty}</button>
                            <button type="button" class="case-buy-btn" data-buy-crate="${crate.slug}">BUY</button>
                            <button type="button" class="case-instant-open-btn" data-instant-open-crate="${crate.slug}">Instant Open</button>
                        </div>
                    </div>
                </div>
            `;

            card.querySelector('[data-view-crate]').addEventListener('click', () => {
                openCaseInspectModal(crate);
            });

            card.querySelector('[data-qty-crate]').addEventListener('click', () => {
                openChooseAmountModal(crate.slug, 'MARKETPLACE', 5);
            });

            card.querySelector('[data-instant-open-crate]').addEventListener('click', () => {
                const qty = getMarketplaceQuantity(crate.slug);
                startInteractiveCaseOpening(crate, { instant: true, quantity: qty });
            });

            card.querySelector('[data-buy-crate]').addEventListener('click', async () => {
                try {
                    const qty = getMarketplaceQuantity(crate.slug);
                    const cost = Number((crate.price * qty).toFixed(2));
                    await callApi('POST', '/api/users/items/buy', {
                        username: currentUser.username,
                        password: currentUser.password,
                        item: crate.slug,
                        quantity: qty
                    });
                    await syncBalanceFromGet();
                    await syncItemsFromGet();
                    await syncTopRichLeaderboard();
                    renderCasesMarketplaceGrid();
                    showToast(
                        qty > 1
                            ? `Bought x${qty} ${displayLabel} for $${cost.toFixed(2)}! Moved to inventory.`
                            : `Bought ${displayLabel} for $${crate.price}! Moved to inventory.`
                    );
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
            const ownedCount = counts[crate.slug] || 0;
            const selectedQty = getInventoryQuantity(crate.slug, ownedCount);

            card.innerHTML = `
                <div class="case-image-slot">
                    <img src="${crate.imageUrl}" alt="${displayLabel}" class="case-art-img" />
                </div>
                <div class="case-bottom-footer">
                    <div class="case-title">${displayLabel}</div>
                    <div class="case-buy-row">
                        <span class="case-count-inline">x${ownedCount}</span>
                        <div class="case-action-group">
                            <button type="button" class="case-view-btn" data-view-crate="${crate.slug}">View</button>
                            <button type="button" class="case-qty-btn" data-qty-crate="${crate.slug}">x${selectedQty}</button>
                            <button type="button" class="case-buy-btn" data-open-crate="${crate.slug}">Open</button>
                        </div>
                    </div>
                </div>
            `;

            card.querySelector('[data-view-crate]').addEventListener('click', () => {
                openCaseInspectModal(crate);
            });

            card.querySelector('[data-qty-crate]').addEventListener('click', () => {
                openChooseAmountModal(crate.slug, 'INVENTORY', ownedCount);
            });

            card.querySelector('[data-open-crate]').addEventListener('click', () => {
                const qty = getInventoryQuantity(crate.slug, ownedCount);
                startInteractiveCaseOpening(crate, { instant: false, quantity: qty });
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
    const SLOT_TICK_SOUND_URL = '/audio/slot-key-sound.mp3';
    const SLOT_AUDIO_POOL_SIZE = 8;

    let isResolvingCaseOpen = false;
    let slotAudioCtx = null;
    let slotAudioBuffer = null;
    let slotAudioBufferLoading = false;
    const slotAudioPool = [];
    let slotAudioPoolIdx = 0;

    function initSlotTickAudio() {
        if (slotAudioPool.length === 0) {
            for (let i = 0; i < SLOT_AUDIO_POOL_SIZE; i++) {
                const audio = new Audio(SLOT_TICK_SOUND_URL);
                audio.preload = 'auto';
                audio.volume = 0.75;
                slotAudioPool.push(audio);
            }
        }
        const AudioCtx = window.AudioContext || window.webkitAudioContext;
        if (AudioCtx && !slotAudioCtx) {
            try {
                slotAudioCtx = new AudioCtx();
            } catch (_) {}
        }
        if (slotAudioCtx && slotAudioCtx.state === 'suspended') {
            slotAudioCtx.resume().catch(() => {});
        }
        if (slotAudioCtx && !slotAudioBuffer && !slotAudioBufferLoading) {
            slotAudioBufferLoading = true;
            fetch(SLOT_TICK_SOUND_URL)
                .then(r => r.arrayBuffer())
                .then(buf => slotAudioCtx.decodeAudioData(buf))
                .then(decoded => {
                    slotAudioBuffer = decoded;
                })
                .catch(() => {})
                .finally(() => {
                    slotAudioBufferLoading = false;
                });
        }
    }

    function playSlotTickSound() {
        try {
            if (slotAudioCtx && slotAudioBuffer) {
                if (slotAudioCtx.state === 'suspended') {
                    slotAudioCtx.resume().catch(() => {});
                }
                const source = slotAudioCtx.createBufferSource();
                const gainNode = slotAudioCtx.createGain();
                gainNode.gain.value = 0.75;
                source.buffer = slotAudioBuffer;
                source.connect(gainNode);
                gainNode.connect(slotAudioCtx.destination);
                source.start(0);
                return;
            }
        } catch (_) {}

        try {
            if (slotAudioPool.length === 0) {
                initSlotTickAudio();
            }
            const audio = slotAudioPool[slotAudioPoolIdx];
            slotAudioPoolIdx = (slotAudioPoolIdx + 1) % slotAudioPool.length;
            audio.currentTime = 0;
            const playPromise = audio.play();
            if (playPromise && typeof playPromise.catch === 'function') {
                playPromise.catch(() => {});
            }
        } catch (_) {}
    }

    function getTrackTranslateX(trackEl) {
        const style = window.getComputedStyle(trackEl);
        const transform = style.transform || style.webkitTransform || 'none';
        if (!transform || transform === 'none') return 0;
        if (typeof DOMMatrixReadOnly === 'function') {
            try {
                return new DOMMatrixReadOnly(transform).m41;
            } catch (_) {}
        }
        const match = transform.match(/matrix\(([^)]+)\)/);
        if (match && match[1]) {
            const parts = match[1].split(',').map(s => parseFloat(s.trim()));
            if (parts.length >= 6 && Number.isFinite(parts[4])) {
                return parts[4];
            }
        }
        return 0;
    }

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

    const OUTCOME_TIER_META = {
        'COMMON SCRAP': {
            rank: 1,
            label: 'COMMON SCRAP',
            image: '/imgs/win-titles/trash.png',
            className: 'tier-floor',
            isHighTier: false
        },
        'MINOR RETURN': {
            rank: 2,
            label: 'MINOR RETURN',
            image: '/imgs/win-titles/pittance.png',
            className: 'tier-common',
            isHighTier: false
        },
        'NEAR EVEN': {
            rank: 3,
            label: 'NEAR EVEN',
            image: '/imgs/win-titles/almost.png',
            className: 'tier-even',
            isHighTier: false
        },
        'SMALL WIN': {
            rank: 4,
            label: 'SMALL WIN',
            image: '/imgs/win-titles/fine.png',
            className: 'tier-profit',
            isHighTier: true
        },
        'NICE WIN': {
            rank: 5,
            label: 'NICE WIN',
            image: '/imgs/win-titles/nice.png',
            className: 'tier-rare',
            isHighTier: true
        },
        'TOP PRIZE': {
            rank: 6,
            label: 'TOP PRIZE',
            image: '/imgs/win-titles/jackpot.png',
            className: 'tier-jackpot',
            isHighTier: true
        }
    };

    // Preload all 6 outcome title graphics so entrance animations start without network delay
    Object.values(OUTCOME_TIER_META).forEach(meta => {
        const preloadImg = new Image();
        preloadImg.src = meta.image;
    });

    function resolveOutcomeTierMetaFromSpin(spin) {
        if (!spin) return OUTCOME_TIER_META['COMMON SCRAP'];
        const rawLabel = typeof spin.outcomeTier === 'string' ? spin.outcomeTier : '';
        const normalized = rawLabel.replace(/\s*\([^)]*\)\s*$/, '').trim().toUpperCase();
        if (spin.jackpot || normalized === 'TOP PRIZE' || normalized === 'JACKPOT CAP') {
            return OUTCOME_TIER_META['TOP PRIZE'];
        }
        if (OUTCOME_TIER_META[normalized]) {
            return OUTCOME_TIER_META[normalized];
        }
        return OUTCOME_TIER_META['COMMON SCRAP'];
    }

    function resolveHighestOutcomeTierMeta(spins) {
        if (!Array.isArray(spins) || spins.length === 0) {
            return OUTCOME_TIER_META['COMMON SCRAP'];
        }
        let highest = OUTCOME_TIER_META['COMMON SCRAP'];
        for (let i = 0; i < spins.length; i++) {
            const candidate = resolveOutcomeTierMetaFromSpin(spins[i]);
            if (candidate.rank > highest.rank) {
                highest = candidate;
            }
        }
        return highest;
    }

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
            '[data-open-crate], [data-instant-open-crate], [data-buy-crate], [data-view-crate], [data-qty-crate], #inventorySearchInput, #inventoryFilterBtn, #casesSearchInput, #casesFilterBtn, #backToGamesBtn, .nav-tab, #openBalanceModalBtn, #topbarAvatarBtn, #profileDepositBtn, #profileWithdrawBtn'
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

    let activeSkipSpinHandler = null;
    let outcomeStage2TimeoutId = null;
    let outcomeDismissUnlockAtMs = 0;

    function resetCaseOutcomeOverlayState() {
        if (outcomeStage2TimeoutId !== null) {
            clearTimeout(outcomeStage2TimeoutId);
            outcomeStage2TimeoutId = null;
        }
        if (caseOutcomeBgVideo) {
            try {
                caseOutcomeBgVideo.pause();
                caseOutcomeBgVideo.currentTime = 0;
            } catch (_) {}
            caseOutcomeBgVideo.classList.remove('active', 'fade-out');
        }
        if (caseOutcomeTitleImg) {
            caseOutcomeTitleImg.classList.remove(
                'anim-low-tier',
                'anim-high-tier',
                'tier-floor',
                'tier-common',
                'tier-even',
                'tier-profit',
                'tier-rare',
                'tier-jackpot'
            );
        }
        if (caseOutcomeUsdtAmount) {
            caseOutcomeUsdtAmount.classList.remove('anim-payout-reveal', 'jackpot');
        }
    }

    function closeCaseOutcomeOverlay() {
        resetCaseOutcomeOverlayState();
        if (caseOutcomeModal) {
            caseOutcomeModal.classList.remove('open');
        }
        if (caseRouletteModal) {
            caseRouletteModal.classList.remove('open');
        }
    }

    if (rouletteSkipBtn) {
        rouletteSkipBtn.addEventListener('click', () => {
            if (typeof activeSkipSpinHandler === 'function') {
                activeSkipSpinHandler();
            }
        });
    }

    async function startInteractiveCaseOpening(crate, options = {}) {
        if (isResolvingCaseOpen || !crate) return;
        const isInstant = Boolean(options && options.instant);
        const requestedQty = Math.min(5, Math.max(1, Number(options && options.quantity) || 1));

        isResolvingCaseOpen = true;
        setSpinningLockState(true);
        initSlotTickAudio();
        closeCaseOutcomeOverlay();

        try {
            // 1. Backend execution: deduct crate(s) (or crate price * quantity on instant open) & select winning outcomes
            const endpoint = isInstant ? '/api/cases/instant-open' : '/api/users/items/open';
            const res = await callApi('POST', endpoint, {
                username: currentUser.username,
                password: currentUser.password,
                item: crate.slug,
                quantity: requestedQty
            });

            const paytable = Array.isArray(res.paytable) ? res.paytable : [];
            const rawSpins = Array.isArray(res.spins) && res.spins.length > 0
                ? res.spins
                : [{
                    wonAmount: Number(res.wonAmount) || 0,
                    wonMultiplier: res.wonMultiplier !== undefined && res.wonMultiplier !== null
                        ? Number(res.wonMultiplier)
                        : (crate.price > 0 ? (Number(res.wonAmount) || 0) / crate.price : 1),
                    outcomeTier: res.outcomeTier || '',
                    jackpot: Boolean(res.jackpot)
                }];

            const reelCount = Math.min(5, Math.max(1, rawSpins.length));
            const spins = rawSpins.slice(0, reelCount).map(s => {
                const amt = Number(s.wonAmount) || 0;
                const mult = s.wonMultiplier !== undefined && s.wonMultiplier !== null
                    ? Number(s.wonMultiplier)
                    : (crate.price > 0 ? amt / crate.price : 1);
                const tier = s.outcomeTier || '';
                const isJp = Boolean(s.jackpot) || tier === 'TOP PRIZE' || tier === 'Jackpot Cap';
                return { wonAmount: amt, wonMultiplier: mult, outcomeTier: tier, jackpot: isJp };
            });

            const totalWonAmount = res.totalWonAmount !== undefined && res.totalWonAmount !== null
                ? Number(res.totalWonAmount)
                : spins.reduce((sum, s) => sum + s.wonAmount, 0);
            const anyJackpot = Boolean(res.jackpot) || spins.some(s => s.jackpot);

            if (isInstant) {
                // Dynamically deduct total crate cost from top balance display while reels spin
                const unitCost = Number(res.caseCost !== undefined ? res.caseCost : crate.price) || 0;
                const totalCost = unitCost * reelCount;
                const postDeductBalance = Math.max(0, (Number(currentUser.balance) || 0) - totalCost);
                updateBalanceDisplay(postDeductBalance);
            } else {
                // Update inventory count immediately so opened crates are consumed visually
                currentUser.items = Array.isArray(res.remainingItems) ? res.remainingItems : [];
                renderInventoryGrid();
            }
            setSpinningLockState(true);

            if (rouletteCaseName) {
                const baseTitle = crate.name || crate.slug;
                rouletteCaseName.textContent = reelCount > 1 ? `${baseTitle} (x${reelCount})` : baseTitle;
            }

            if (caseRouletteStage) {
                caseRouletteStage.setAttribute('data-reel-count', String(reelCount));
            }
            if (caseRouletteReelsStack) {
                caseRouletteReelsStack.setAttribute('data-reel-count', String(reelCount));
                caseRouletteReelsStack.innerHTML = '';
            }

            // 2. Construct N independent 100-slot reel viewports stacked vertically
            const activeReels = [];
            for (let r = 0; r < reelCount; r++) {
                const spin = spins[r];
                const tape = buildRouletteTape(paytable, spin.wonMultiplier, spin.outcomeTier);

                const viewportEl = document.createElement('div');
                viewportEl.className = 'case-roulette-viewport';
                if (r === 0) {
                    viewportEl.id = 'caseRouletteViewport';
                }

                const trackEl = document.createElement('div');
                trackEl.className = 'case-roulette-track';
                if (r === 0) {
                    trackEl.id = 'caseRouletteTrack';
                }
                trackEl.style.transition = 'none';
                trackEl.style.transform = 'translateX(0px)';

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
                trackEl.appendChild(fragment);

                viewportEl.innerHTML = `
                    <div class="roulette-edge-fade left" aria-hidden="true"></div>
                    <div class="roulette-edge-fade right" aria-hidden="true"></div>
                    <div class="roulette-center-marker" aria-hidden="true"></div>
                `;
                viewportEl.appendChild(trackEl);
                caseRouletteReelsStack.appendChild(viewportEl);

                activeReels.push({ viewportEl, trackEl, spin, targetX: 0 });
            }

            if (rouletteSkipBtn) {
                rouletteSkipBtn.disabled = false;
                rouletteSkipBtn.style.display = '';
            }

            if (caseRouletteModal) {
                caseRouletteModal.classList.add('open');
            }

            // 3. Measure unscaled container width, size 5 visible cards per reel & compute each reel's exact targetX
            const firstViewport = activeReels[0].viewportEl;
            void firstViewport.offsetWidth;

            const W = WINNING_SLOT_INDEX;
            const rouletteContainerWidth = firstViewport.clientWidth;
            const computedStyle = window.getComputedStyle(activeReels[0].trackEl);
            const gap = parseFloat(computedStyle.gap || computedStyle.columnGap || '8') || 8;
            const cardWidth = (rouletteContainerWidth - (4 * gap)) / 5;
            const itemTotalWidth = cardWidth + gap;
            const cardCenterPosition = (W * itemTotalWidth) + (cardWidth / 2);
            const viewportCenter = rouletteContainerWidth / 2;

            activeReels.forEach(reel => {
                reel.trackEl.style.setProperty('--roulette-card-width', `${cardWidth}px`);
                void reel.trackEl.offsetWidth;
                const jitter = (Math.random() * 0.4 - 0.2) * cardWidth;
                reel.targetX = cardCenterPosition - viewportCenter + jitter;
            });

            // 4. Start simultaneous 6s cubic-bezier(0.15, 0.9, 0.25, 1) spin across all reels + support Skip button
            let wasSkipped = false;
            const primaryTrackEl = activeReels[0].trackEl;

            activeReels.forEach(reel => {
                reel.trackEl.style.transition = 'transform 6s cubic-bezier(0.15, 0.9, 0.25, 1)';
                reel.trackEl.style.transform = `translateX(-${reel.targetX}px)`;
            });

            await new Promise((resolve) => {
                let settled = false;
                let rafId = null;
                let timeoutId = null;
                let lastSlotUnderCursor = Math.floor(viewportCenter / itemTotalWidth);

                const trackSlotTicks = () => {
                    if (settled) return;
                    const currentTx = getTrackTranslateX(primaryTrackEl);
                    const scrollOffset = Math.max(0, -currentTx);
                    const cursorTrackX = scrollOffset + viewportCenter;
                    const currentSlotIdx = Math.floor(cursorTrackX / itemTotalWidth);

                    if (currentSlotIdx > lastSlotUnderCursor) {
                        lastSlotUnderCursor = currentSlotIdx;
                        playSlotTickSound();
                    }
                    rafId = requestAnimationFrame(trackSlotTicks);
                };

                rafId = requestAnimationFrame(trackSlotTicks);

                const finish = () => {
                    if (settled) return;
                    settled = true;
                    activeSkipSpinHandler = null;
                    if (rafId !== null) {
                        cancelAnimationFrame(rafId);
                    }
                    if (timeoutId !== null) {
                        clearTimeout(timeoutId);
                    }
                    primaryTrackEl.removeEventListener('transitionend', onTransitionEnd);
                    resolve();
                };

                const onTransitionEnd = (e) => {
                    if (e.target === primaryTrackEl && e.propertyName === 'transform') {
                        finish();
                    }
                };

                activeSkipSpinHandler = () => {
                    if (settled) return;
                    wasSkipped = true;
                    if (rouletteSkipBtn) {
                        rouletteSkipBtn.disabled = true;
                    }
                    activeReels.forEach(reel => {
                        reel.trackEl.style.transition = 'none';
                        reel.trackEl.style.transform = `translateX(-${reel.targetX}px)`;
                        void reel.trackEl.offsetWidth;
                    });
                    playSlotTickSound();
                    finish();
                };

                primaryTrackEl.addEventListener('transitionend', onTransitionEnd);
                timeoutId = setTimeout(finish, ROULETTE_SPIN_DURATION_MS + 80);
            });

            // 5. Highlight winning slot 96 across all reels, disable Skip button, and pause (1000ms on Skip, 280ms normal)
            if (rouletteSkipBtn) {
                rouletteSkipBtn.disabled = true;
            }
            activeReels.forEach(reel => {
                const winningSlotEl = reel.trackEl.children[WINNING_SLOT_INDEX];
                if (winningSlotEl) {
                    winningSlotEl.classList.add('winning-slot-highlight');
                }
            });

            updateBalanceDisplay(res.newBalance);
            if (isInstant) {
                renderCasesMarketplaceGrid();
            }
            await syncTopRichLeaderboard();
            await new Promise(r => setTimeout(r, wasSkipped ? 1000 : 280));

            if (caseRouletteModal) {
                caseRouletteModal.classList.remove('open');
            }
            setSpinningLockState(false);

            // 6. Display full-screen borderless cinematic outcome overlay with two-stage entrance animation
            if (caseOutcomeModal) {
                resetCaseOutcomeOverlayState();
                const highestTierMeta = resolveHighestOutcomeTierMeta(spins);
                const isJackpotHighlight = anyJackpot || highestTierMeta.rank === 6;

                if (caseOutcomeTitleImg) {
                    caseOutcomeTitleImg.src = highestTierMeta.image;
                    caseOutcomeTitleImg.alt = highestTierMeta.label;
                    caseOutcomeTitleImg.classList.add(highestTierMeta.className);
                }

                if (caseOutcomeUsdtAmount) {
                    caseOutcomeUsdtAmount.textContent = totalWonAmount > 0
                        ? `+$${totalWonAmount.toFixed(2)} USDT`
                        : `$0.00 USDT`;
                    caseOutcomeUsdtAmount.classList.toggle('jackpot', isJackpotHighlight);
                }

                // 750ms interaction lock so spamming 'Skip' does not immediately dismiss the reward screen
                outcomeDismissUnlockAtMs = performance.now() + 750;
                caseOutcomeModal.classList.add('open');

                if (caseOutcomeBgVideo) {
                    caseOutcomeBgVideo.loop = false;
                    if (highestTierMeta.isHighTier) {
                        caseOutcomeBgVideo.classList.remove('fade-out');
                        caseOutcomeBgVideo.classList.add('active');
                        try {
                            caseOutcomeBgVideo.currentTime = 0;
                        } catch (_) {}
                        const playPromise = caseOutcomeBgVideo.play();
                        if (playPromise && typeof playPromise.catch === 'function') {
                            playPromise.catch(() => {});
                        }
                    } else {
                        try {
                            caseOutcomeBgVideo.pause();
                        } catch (_) {}
                        caseOutcomeBgVideo.classList.remove('active', 'fade-out');
                    }
                }

                if (caseOutcomeTitleImg) {
                    void caseOutcomeTitleImg.offsetWidth;
                    caseOutcomeTitleImg.classList.add(
                        highestTierMeta.isHighTier ? 'anim-high-tier' : 'anim-low-tier'
                    );
                }

                outcomeStage2TimeoutId = setTimeout(() => {
                    outcomeStage2TimeoutId = null;
                    if (caseOutcomeUsdtAmount) {
                        caseOutcomeUsdtAmount.classList.add('anim-payout-reveal');
                    }
                }, 420);
            }
        } catch (_) {
            activeSkipSpinHandler = null;
            if (caseRouletteModal) {
                caseRouletteModal.classList.remove('open');
            }
            setSpinningLockState(false);
        } finally {
            activeSkipSpinHandler = null;
            isResolvingCaseOpen = false;
        }
    }

    if (caseOutcomeBgVideo) {
        caseOutcomeBgVideo.loop = false;
        caseOutcomeBgVideo.addEventListener('timeupdate', () => {
            if (!caseOutcomeBgVideo.classList.contains('active')) return;
            const dur = caseOutcomeBgVideo.duration;
            if (Number.isFinite(dur) && dur > 0 && dur - caseOutcomeBgVideo.currentTime <= 0.45) {
                caseOutcomeBgVideo.classList.add('fade-out');
            }
        });
        caseOutcomeBgVideo.addEventListener('ended', () => {
            caseOutcomeBgVideo.classList.add('fade-out');
            setTimeout(() => {
                if (caseOutcomeBgVideo.classList.contains('fade-out')) {
                    caseOutcomeBgVideo.classList.remove('active', 'fade-out');
                }
            }, 400);
        });
    }

    if (caseOutcomeModal) {
        const handleOutcomeDismiss = (e) => {
            if (!caseOutcomeModal.classList.contains('open')) return;
            if (performance.now() < outcomeDismissUnlockAtMs) return;
            e.preventDefault();
            closeCaseOutcomeOverlay();
        };
        caseOutcomeModal.addEventListener('pointerdown', handleOutcomeDismiss);
        caseOutcomeModal.addEventListener('click', handleOutcomeDismiss);
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
    let activeWalletModalMode = 'DEPOSIT';

    function applyWalletStep1ActionMode(mode) {
        activeWalletModalMode = mode === 'WITHDRAW' ? 'WITHDRAW' : 'DEPOSIT';
        if (walletStep1Actions) {
            walletStep1Actions.classList.add('single');
        }
        if (modalDepositBtn) {
            modalDepositBtn.style.display = activeWalletModalMode === 'DEPOSIT' ? '' : 'none';
        }
        if (modalWithdrawBtn) {
            modalWithdrawBtn.style.display = activeWalletModalMode === 'WITHDRAW' ? '' : 'none';
        }
    }

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

    function setWalletStep(stepNumber, mode = activeWalletModalMode) {
        [walletStep1, walletStep2, walletStep3].forEach((el, idx) => {
            if (el) {
                el.classList.toggle('active', idx + 1 === stepNumber);
            }
        });

        if (stepNumber === 1) {
            applyWalletStep1ActionMode(mode);
            balanceModalTitle.textContent = activeWalletModalMode === 'WITHDRAW' ? 'Withdraw Wallet' : 'Deposit Wallet';
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

    async function openWalletModal(mode = 'DEPOSIT') {
        applyWalletStep1ActionMode(mode);
        if (mode === 'DEPOSIT') {
            const hasActive = await checkAndRestoreActiveDeposit(false);
            if (!hasActive) {
                modalBalanceAmount.value = '0';
                setWalletStep(1, 'DEPOSIT');
            }
        } else {
            modalBalanceAmount.value = '0';
            setWalletStep(1, 'WITHDRAW');
        }
        balanceModal.classList.add('open');
    }

    if (openBalanceModalBtn) {
        openBalanceModalBtn.addEventListener('click', () => {
            openWalletModal('DEPOSIT');
        });
    }

    if (profileDepositBtn) {
        profileDepositBtn.addEventListener('click', () => {
            openWalletModal('DEPOSIT');
        });
    }

    if (profileWithdrawBtn) {
        profileWithdrawBtn.addEventListener('click', () => {
            openWalletModal('WITHDRAW');
        });
    }

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