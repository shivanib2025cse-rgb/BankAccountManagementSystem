/**
 * Bank Account Management System - Modern Client Application
 * Clean Vanilla ES6+ Implementation with Enterprise Banking Capabilities
 */

const App = (() => {
    // Application State
    const state = {
        accounts: [],
        customers: [],
        branches: [],
        loans: [],
        fds: [],
        transactions: [],
        stats: null,
        branchReport: [],
        activeTab: 'dashboard',
        accountFilter: 'all',
        theme: localStorage.getItem('novabank_theme') || 'dark',
        token: null,
        inactivityTimer: null,
        logoutTimer: null,
        activityListenersAttached: false
    };

    const API_BASE = '/api';

    // Format helpers
    const formatCurrency = (num) => {
        const val = Number(num) || 0;
        return 'Rs. ' + val.toLocaleString('en-IN', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    };

    const formatDate = (dateStr) => {
        if (!dateStr) return '-';
        try {
            const d = new Date(dateStr);
            if (isNaN(d.getTime())) return dateStr;
            return d.toLocaleDateString('en-IN', {
                year: 'numeric',
                month: 'short',
                day: 'numeric'
            });
        } catch {
            return dateStr;
        }
    };

    const formatDateTime = (tsStr) => {
        if (!tsStr) return '-';
        try {
            const d = new Date(tsStr);
            if (isNaN(d.getTime())) return tsStr;
            return d.toLocaleDateString('en-IN', {
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
        } catch {
            return tsStr;
        }
    };

    // Generic API requester
    async function apiRequest(endpoint, options = {}) {
        const url = `${API_BASE}${endpoint}`;
        const defaultHeaders = {
            'Accept': 'application/json'
        };
        if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
            defaultHeaders['Content-Type'] = 'application/json';
            options.body = JSON.stringify(options.body);
        }
        options.headers = { ...defaultHeaders, ...options.headers };
        // Security: Bearer authentication prevents anonymous callers from reaching banking APIs.
        if (state.token) options.headers.Authorization = `Bearer ${state.token}`;

        try {
            const res = await fetch(url, options);
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                const errMsg = data.error || data.message || `HTTP ${res.status}: ${res.statusText}`;
                throw new Error(errMsg);
            }
            return data;
        } catch (err) {
            console.error(`[API Error] ${endpoint}:`, err);
            throw err;
        }
    }

    async function authenticate() {
        const gate = document.getElementById('authGate');
        const form = document.getElementById('loginForm');
        const pinSetup = document.getElementById('firstPinSetup');
        const message = document.getElementById('authMessage');
        form?.addEventListener('submit', async (e) => {
            e.preventDefault(); message.textContent = '';
            try {
                const result = await apiRequest('/auth/login', { method: 'POST', body: {
                    username: document.getElementById('loginUsername').value,
                    password: document.getElementById('loginPassword').value
                }});
                state.token = result.token;
                const loginLine = document.getElementById('lastLoginInfo');
                if (loginLine) loginLine.textContent = result.lastLoginAt ? `Last login: ${result.lastLoginAt} · ${result.lastLoginIp || 'unknown IP'}` : 'This is your first recorded login.';
                const pin = await apiRequest('/auth/pin-status');
                if (!pin.hasPin) { form.hidden = true; pinSetup.hidden = false; }
                else { gate.style.display = 'none'; startInactivityTimer(); checkHealth(false); loadAllData(); }
            } catch (err) { message.textContent = err.message || 'Invalid credentials'; }
        });
        document.getElementById('registerForm')?.addEventListener('submit', async (e) => {
            e.preventDefault(); message.textContent = '';
            try {
                await apiRequest('/auth/register', { method: 'POST', body: {
                    username: document.getElementById('registerUsername').value,
                    password: document.getElementById('registerPassword').value,
                    customerId: Number(document.getElementById('registerCustomer').value)
                }});
                message.style.color = '#86efac'; message.textContent = 'Registration successful. Sign in above.';
            } catch (err) { message.style.color = '#fca5a5'; message.textContent = err.message; }
        });
        document.getElementById('pinSetupForm')?.addEventListener('submit', async (e) => {
            e.preventDefault();
            try {
                await apiRequest('/auth/set-pin', { method: 'POST', body: { pin: document.getElementById('setupPin').value }});
                gate.style.display = 'none'; startInactivityTimer(); checkHealth(false); loadAllData();
            } catch (err) { message.textContent = err.message; }
        });
    }

    // Security: A 60-second inactivity warning lets account owners end abandoned sessions before automatic logout.
    function startInactivityTimer() {
        const warning = document.getElementById('sessionWarning');
        const reset = () => {
            clearTimeout(state.inactivityTimer);
            clearTimeout(state.logoutTimer);
            warning.hidden = true;
            state.inactivityTimer = setTimeout(() => {
                warning.hidden = false;
                state.logoutTimer = setTimeout(signOut, 60 * 1000);
            }, 9 * 60 * 1000);
        };
        if (!state.activityListenersAttached) {
            ['click','keydown','mousemove','touchstart'].forEach(name => document.addEventListener(name, reset, { passive: true }));
            document.getElementById('staySignedIn').addEventListener('click', () => { apiRequest('/health').catch(() => {}); reset(); });
            state.activityListenersAttached = true;
        }
        reset();
    }

    async function signOut() {
        if (!state.token) return;
        try { await apiRequest('/auth/logout', { method: 'POST' }); } catch (_) {}
        state.token = null; clearTimeout(state.inactivityTimer); clearTimeout(state.logoutTimer);
        document.getElementById('sessionWarning').hidden = true;
        document.getElementById('authGate').style.display = 'flex';
        document.getElementById('loginForm').hidden = false;
        document.getElementById('firstPinSetup').hidden = true;
    }

    // Toast Notifications
    function showToast(message, type = 'info') {
        const container = document.getElementById('toastContainer');
        if (!container) return;

        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;

        const icon = type === 'success' ? '✅' : type === 'error' ? '❌' : 'ℹ️';
        toast.innerHTML = `<span>${icon}</span><span style="flex:1;">${message}</span>`;

        container.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(100%)';
            setTimeout(() => toast.remove(), 250);
        }, 4000);
    }

    // Clock
    function startClock() {
        const clockEl = document.getElementById('systemClock');
        if (!clockEl) return;
        const update = () => {
            const now = new Date();
            clockEl.textContent = now.toLocaleDateString('en-IN', {
                weekday: 'short',
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
        };
        update();
        setInterval(update, 1000);
    }

    // Theme Management
    function initTheme() {
        const body = document.body;
        if (state.theme === 'light') {
            body.classList.remove('theme-dark');
            body.classList.add('theme-light');
        } else {
            body.classList.remove('theme-light');
            body.classList.add('theme-dark');
        }

        const toggleBtn = document.getElementById('themeToggle');
        if (toggleBtn) {
            toggleBtn.onclick = () => {
                const isLight = body.classList.toggle('theme-light');
                if (isLight) {
                    body.classList.remove('theme-dark');
                    state.theme = 'light';
                } else {
                    body.classList.add('theme-dark');
                    state.theme = 'dark';
                }
                localStorage.setItem('novabank_theme', state.theme);
            };
        }
    }

    // Health & DB Connection Check
    async function checkHealth(reconnect = false) {
        const pill = document.getElementById('dbStatusPill');
        const text = document.getElementById('dbStatusText');
        try {
            const res = await apiRequest('/health', {
                method: reconnect ? 'POST' : 'GET'
            });
            if (res.isMySQL) {
                pill.className = 'db-status-pill online';
                text.textContent = 'MySQL 8 (bankdb)';
                if (reconnect) showToast('Connected to MySQL bankdb database!', 'success');
            } else {
                pill.className = 'db-status-pill fallback';
                text.textContent = 'InMemory (Demo Mode)';
                if (reconnect) showToast('Operating in InMemory Fallback (MySQL offline)', 'info');
            }
        } catch {
            pill.className = 'db-status-pill fallback';
            text.textContent = 'Server Offline';
        }
    }

    // Navigation Tab Switching
    function switchTab(tabId) {
        state.activeTab = tabId;

        // Update Nav Menu
        document.querySelectorAll('.nav-item').forEach(btn => {
            btn.classList.toggle('active', btn.getAttribute('data-tab') === tabId);
        });

        // Update Tab Panes
        document.querySelectorAll('.tab-pane').forEach(pane => {
            pane.classList.toggle('active', pane.id === `pane-${tabId}`);
        });

        // Specific Tab hooks
        if (tabId === 'transfer') {
            populateTransferDropdowns();
        } else if (tabId === 'transactions') {
            populateStatementDropdown();
        } else if (tabId === 'loans') {
            calculateEMI();
        } else if (tabId === 'fds') {
            calculateFDProjection();
        }
    }

    // Data Loaders
    async function loadAllData() {
        try {
            await Promise.all([
                loadStats(),
                loadAccounts(),
                loadCustomers(),
                loadBranches(),
                loadLoans(),
                loadFixedDeposits(),
                loadTransactions(),
                loadBranchReport()
            ]);
            populateAllDropdowns();
        } catch (err) {
            console.error('Failed to load initial data:', err);
        }
    }

    async function loadStats() {
        try {
            const data = await apiRequest('/stats');
            state.stats = data;
            renderDashboardStats();
        } catch (err) {
            console.error(err);
        }
    }

    async function loadAccounts() {
        try {
            state.accounts = await apiRequest('/accounts');
            renderAccountsTable();
            populateAllDropdowns();
        } catch (err) {
            console.error(err);
        }
    }

    async function loadCustomers() {
        try {
            state.customers = await apiRequest('/customers');
            renderCustomersTable();
            populateAllDropdowns();
        } catch (err) {
            console.error(err);
        }
    }

    async function loadBranches() {
        try {
            state.branches = await apiRequest('/branches');
            renderBranchesTable();
            populateAllDropdowns();
        } catch (err) {
            console.error(err);
        }
    }

    async function loadLoans() {
        try {
            state.loans = await apiRequest('/loans');
            renderLoansTable();
        } catch (err) {
            console.error(err);
        }
    }

    async function loadFixedDeposits() {
        try {
            state.fds = await apiRequest('/fds');
            renderFixedDepositsTable();
        } catch (err) {
            console.error(err);
        }
    }

    async function loadTransactions() {
        try {
            state.transactions = await apiRequest('/transactions?limit=50');
            renderDashboardTransactions();
            renderGlobalTransactions();
        } catch (err) {
            console.error(err);
        }
    }

    async function loadBranchReport() {
        try {
            state.branchReport = await apiRequest('/branches/report');
            renderBranchReport();
        } catch (err) {
            console.error(err);
        }
    }

    // Render Dashboard
    function renderDashboardStats() {
        if (!state.stats) return;
        document.getElementById('statDeposits').textContent = formatCurrency(state.stats.totalDeposits);
        document.getElementById('statAccounts').textContent = state.stats.totalAccounts;
        document.getElementById('statCustomers').textContent = state.stats.totalCustomers;
        document.getElementById('statBranches').textContent = state.stats.totalBranches;
        document.getElementById('statLoans').textContent = state.stats.totalLoans;
    }

    function renderDashboardTransactions() {
        const tbody = document.getElementById('dashboardTxnBody');
        if (!tbody) return;

        if (!state.transactions || state.transactions.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No transactions found.</td></tr>';
            return;
        }

        const recent = state.transactions.slice(0, 7);
        tbody.innerHTML = recent.map(t => {
            const isCredit = ['DEPOSIT', 'TRANSFER_IN', 'INTEREST', 'FD_MATURE'].includes(t.txnType);
            const badgeClass = isCredit ? 'badge-credit' : 'badge-debit';
            return `
                <tr>
                    <td><strong>#${t.txnId}</strong></td>
                    <td><code>${t.accountNo}</code></td>
                    <td><span class="badge ${badgeClass}">${t.txnType}</span></td>
                    <td><strong style="color: ${isCredit ? 'var(--success)' : 'var(--danger)'};">
                        ${isCredit ? '+' : '-'}${formatCurrency(t.amount)}
                    </strong></td>
                    <td>${formatCurrency(t.balanceAfter)}</td>
                    <td><small class="text-muted">${formatDateTime(t.txnDate)}</small></td>
                </tr>
            `;
        }).join('');
    }

    function renderBranchReport() {
        const tbody = document.getElementById('reportTableBody');
        const dashBars = document.getElementById('dashboardBranchBars');
        const repBars = document.getElementById('reportBranchBars');

        if (!state.branchReport || state.branchReport.length === 0) {
            if (tbody) tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No report data.</td></tr>';
            return;
        }

        if (tbody) {
            tbody.innerHTML = state.branchReport.map(b => `
                <tr>
                    <td><strong>#${b.branchId}</strong></td>
                    <td><strong>${b.branchName}</strong></td>
                    <td><code>${b.ifscCode}</code></td>
                    <td>${b.city || '-'}</td>
                    <td><span class="badge badge-accent">${b.totalAccounts} Accounts</span></td>
                    <td><strong>${formatCurrency(b.totalBalance)}</strong></td>
                </tr>
            `).join('');
        }

        const maxBal = Math.max(...state.branchReport.map(b => b.totalBalance || 0), 1);
        const barsHtml = state.branchReport.map(b => {
            const pct = Math.round(((b.totalBalance || 0) / maxBal) * 100);
            return `
                <div class="branch-bar-item">
                    <div class="branch-bar-header">
                        <strong>${b.branchName} (${b.city || ''})</strong>
                        <span>${formatCurrency(b.totalBalance)} (${b.totalAccounts} accs)</span>
                    </div>
                    <div class="branch-bar-bg">
                        <div class="branch-bar-fill" style="width: ${pct}%;"></div>
                    </div>
                </div>
            `;
        }).join('');

        if (dashBars) dashBars.innerHTML = barsHtml;
        if (repBars) repBars.innerHTML = barsHtml;
    }

    // Render Accounts Table
    function renderAccountsTable() {
        const tbody = document.getElementById('accountsTableBody');
        if (!tbody) return;

        const search = (document.getElementById('accountSearchInput')?.value || '').toLowerCase().trim();

        let filtered = state.accounts.filter(a => {
            if (state.accountFilter !== 'all' && a.accountType !== state.accountFilter) {
                return false;
            }
            if (search) {
                const matchAcc = String(a.accountNo).includes(search);
                const matchCust = (a.customerName || '').toLowerCase().includes(search);
                const matchIfsc = (a.ifscCode || '').toLowerCase().includes(search);
                const matchBranch = (a.branchName || '').toLowerCase().includes(search);
                return matchAcc || matchCust || matchIfsc || matchBranch;
            }
            return true;
        });

        if (filtered.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">No accounts match the criteria.</td></tr>';
            return;
        }

        tbody.innerHTML = filtered.map(a => {
            const isSavings = a.accountType === 'SAVINGS';
            const badgeClass = isSavings ? 'badge-savings' : 'badge-current';
            const typeLabel = isSavings ? 'SAVINGS (4%)' : 'CURRENT (OD)';
            const isFrozen = (a.status || 'ACTIVE') === 'FROZEN';
            const statusBadge = isFrozen ? '<span class="badge badge-frozen" style="margin-left:4px;">FROZEN</span>' : '';

            return `
                <tr>
                    <td><strong>#${a.accountNo}</strong></td>
                    <td>
                        <strong>${a.customerName}</strong> ${statusBadge}
                        <div style="font-size: 0.72rem; color: var(--text-muted);">${a.phone || ''}</div>
                    </td>
                    <td>${a.branchName}</td>
                    <td><code>${a.ifscCode}</code></td>
                    <td><span class="badge ${badgeClass}">${typeLabel}</span></td>
                    <td><strong style="color: ${a.balance >= 0 ? 'var(--text-primary)' : 'var(--danger)'};">${formatCurrency(a.balance)}</strong></td>
                    <td>${formatDate(a.openedOn)}</td>
                    <td>
                        <div style="display: flex; gap: 6px; flex-wrap: wrap;">
                            <button class="btn btn-secondary btn-sm" onclick="App.quickDeposit(${a.accountNo})" title="Deposit">📥</button>
                            <button class="btn btn-secondary btn-sm" onclick="App.quickWithdraw(${a.accountNo})" title="Withdraw">📤</button>
                            <button class="btn btn-secondary btn-sm" onclick="App.applyInterest(${a.accountNo})" title="Add Yearly Interest">📈</button>
                            <button class="btn btn-secondary btn-sm" onclick="App.toggleAccountStatus(${a.accountNo}, '${a.status || 'ACTIVE'}')" title="${isFrozen ? 'Unfreeze Account' : 'Freeze Account'}">${isFrozen ? '🔥 Unfreeze' : '❄️ Freeze'}</button>
                            <button class="btn btn-secondary btn-sm" onclick="App.viewPassbook(${a.accountNo})" title="View Details">📖</button>
                            <button class="btn btn-secondary btn-sm" onclick="App.viewAccountStatement(${a.accountNo})" title="Mini Statement">📜</button>
                        </div>
                    </td>
                </tr>
            `;
        }).join('');
    }

    function filterAccounts(type) {
        state.accountFilter = type;
        document.querySelectorAll('.filter-pill').forEach(pill => {
            pill.classList.toggle('active', pill.getAttribute('data-filter') === type);
        });
        renderAccountsTable();
    }

    // Render Loans Table
    function renderLoansTable() {
        const tbody = document.getElementById('loansTableBody');
        if (!tbody) return;

        if (!state.loans || state.loans.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">No loan applications on record.</td></tr>';
            return;
        }

        tbody.innerHTML = state.loans.map(l => {
            const statusClass = l.status === 'APPROVED' ? 'badge-approved' : l.status === 'CLOSED' ? 'badge-closed' : 'badge-pending';
            let actions = '-';

            if (l.status === 'PENDING') {
                actions = `
                    <div style="display:flex; gap:4px;">
                        <button class="btn btn-secondary btn-sm" onclick="App.updateLoanStatus(${l.loanId}, 'APPROVED')">✅ Approve</button>
                        <button class="btn btn-secondary btn-sm" onclick="App.updateLoanStatus(${l.loanId}, 'REJECTED')">❌ Reject</button>
                    </div>
                `;
            } else if (l.status === 'APPROVED') {
                actions = `
                    <button class="btn btn-primary btn-sm" onclick="App.openPayLoanModal(${l.loanId}, ${l.amount})">💳 Pay EMI</button>
                `;
            }

            return `
                <tr>
                    <td><strong>#${l.loanId}</strong></td>
                    <td><strong>${l.customerName || 'Customer #' + l.customerId}</strong></td>
                    <td>${l.branchName || 'Branch #' + l.branchId}</td>
                    <td><span class="badge badge-accent">${l.loanType}</span></td>
                    <td><strong>${formatCurrency(l.amount)}</strong></td>
                    <td>${l.interestRate}% p.a.</td>
                    <td><span class="badge ${statusClass}">${l.status}</span></td>
                    <td>${actions}</td>
                </tr>
            `;
        }).join('');
    }

    // Render Fixed Deposits Table
    function renderFixedDepositsTable() {
        const tbody = document.getElementById('fdsTableBody');
        if (!tbody) return;

        if (!state.fds || state.fds.length === 0) {
            tbody.innerHTML = '<tr><td colspan="10" class="text-center text-muted">No fixed deposits currently registered.</td></tr>';
            return;
        }

        tbody.innerHTML = state.fds.map(fd => {
            const statusClass = fd.status === 'ACTIVE' ? 'badge-approved' : fd.status === 'MATURED' ? 'badge-matured' : 'badge-premature';
            const action = fd.status === 'ACTIVE'
                ? `<button class="btn btn-secondary btn-sm" onclick="App.closeFixedDeposit(${fd.fdId})" title="Break/Liquidate FD">🔓 Liquidate</button>`
                : '-';

            return `
                <tr>
                    <td><strong>#${fd.fdId}</strong></td>
                    <td><code>Account #${fd.accountNo}</code></td>
                    <td><strong>${fd.customerName || 'Customer #' + fd.customerId}</strong></td>
                    <td><strong>${formatCurrency(fd.principalAmount)}</strong></td>
                    <td><span class="badge badge-accent">${fd.interestRate}%</span></td>
                    <td>${fd.tenureMonths} Months</td>
                    <td>${formatDate(fd.maturityDate)}</td>
                    <td><strong style="color:var(--success);">${formatCurrency(fd.maturityAmount)}</strong></td>
                    <td><span class="badge ${statusClass}">${fd.status}</span></td>
                    <td>${action}</td>
                </tr>
            `;
        }).join('');
    }

    // Render Customers Table
    function renderCustomersTable() {
        const tbody = document.getElementById('customersTableBody');
        if (!tbody) return;

        if (!state.customers || state.customers.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No customers registered.</td></tr>';
            return;
        }

        tbody.innerHTML = state.customers.map(c => `
            <tr>
                <td><strong>#${c.customerId}</strong></td>
                <td><strong>${c.name}</strong></td>
                <td><code>${c.phone}</code></td>
                <td>${c.email || '-'}</td>
                <td>${c.address || '-'}</td>
                <td>
                    <div style="display:flex; gap:6px;">
                        <button class="btn btn-secondary btn-sm" onclick="App.viewCustomerPortfolio(${c.customerId})">👤 360° Profile</button>
                        <button class="btn btn-secondary btn-sm" onclick="App.openNewAccountForCustomer(${c.customerId})">+ Open Account</button>
                    </div>
                </td>
            </tr>
        `).join('');
    }

    // Render Branches Table
    function renderBranchesTable() {
        const tbody = document.getElementById('branchesTableBody');
        if (!tbody) return;

        if (!state.branches || state.branches.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No branches found.</td></tr>';
            return;
        }

        tbody.innerHTML = state.branches.map(b => {
            const rep = (state.branchReport || []).find(r => r.branchId === b.branchId);
            const totalAcc = rep ? rep.totalAccounts : 0;
            const totalBal = rep ? rep.totalBalance : 0.0;

            return `
                <tr>
                    <td><strong>#${b.branchId}</strong></td>
                    <td><strong>${b.branchName}</strong></td>
                    <td><code>${b.ifscCode}</code></td>
                    <td>${b.city || '-'}</td>
                    <td><span class="badge badge-accent">${totalAcc}</span></td>
                    <td><strong>${formatCurrency(totalBal)}</strong></td>
                </tr>
            `;
        }).join('');
    }

    // Render Global Transactions Table
    function renderGlobalTransactions() {
        const tbody = document.getElementById('transactionsTableBody');
        if (!tbody) return;

        if (!state.transactions || state.transactions.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No transaction logs available.</td></tr>';
            return;
        }

        tbody.innerHTML = state.transactions.map(t => {
            const isCredit = ['DEPOSIT', 'TRANSFER_IN', 'INTEREST', 'FD_MATURE'].includes(t.txnType);
            const badgeClass = isCredit ? 'badge-credit' : 'badge-debit';
            return `
                <tr>
                    <td><strong>#${t.txnId}</strong></td>
                    <td><code>Account #${t.accountNo}</code></td>
                    <td><span class="badge ${badgeClass}">${t.txnType}</span></td>
                    <td><strong style="color: ${isCredit ? 'var(--success)' : 'var(--danger)'};">
                        ${isCredit ? '+' : '-'}${formatCurrency(t.amount)}
                    </strong></td>
                    <td>${formatCurrency(t.balanceAfter)}</td>
                    <td>${formatDateTime(t.txnDate)}</td>
                </tr>
            `;
        }).join('');
    }

    // Dropdown Populators
    function populateAllDropdowns() {
        populateCustomerDropdowns();
        populateBranchDropdowns();
        populateAccountDropdowns();
        populateTransferDropdowns();
        populateStatementDropdown();
        populateFDDropdown();
    }

    function populateCustomerDropdowns() {
        const selects = [
            document.getElementById('accCustomerId'),
            document.getElementById('loanCustId')
        ];
        selects.forEach(sel => {
            if (!sel) return;
            const curVal = sel.value;
            sel.innerHTML = '<option value="">-- Choose Customer --</option>' +
                state.customers.map(c => `<option value="${c.customerId}">${c.name} (#${c.customerId} - ${c.phone})</option>`).join('');
            if (curVal) sel.value = curVal;
        });
    }

    function populateBranchDropdowns() {
        const selects = [
            document.getElementById('accBranchId'),
            document.getElementById('loanBranchId')
        ];
        selects.forEach(sel => {
            if (!sel) return;
            const curVal = sel.value;
            sel.innerHTML = '<option value="">-- Choose Branch --</option>' +
                state.branches.map(b => `<option value="${b.branchId}">${b.branchName} (${b.ifscCode} - ${b.city})</option>`).join('');
            if (curVal) sel.value = curVal;
        });
    }

    function populateAccountDropdowns() {
        const selects = [
            document.getElementById('depositAccountSelect'),
            document.getElementById('withdrawAccountSelect'),
            document.getElementById('payLoanAccount')
        ];
        selects.forEach(sel => {
            if (!sel) return;
            const curVal = sel.value;
            sel.innerHTML = '<option value="">-- Select Account --</option>' +
                state.accounts.map(a => `<option value="${a.accountNo}">#${a.accountNo} - ${a.customerName} (${a.accountType} | Bal: ${formatCurrency(a.balance)})</option>`).join('');
            if (curVal) sel.value = curVal;
        });
    }

    function populateFDDropdown() {
        const sel = document.getElementById('fdSourceAccount');
        if (!sel) return;
        const curVal = sel.value;
        sel.innerHTML = '<option value="">-- Select Source Account --</option>' +
            state.accounts.map(a => `<option value="${a.accountNo}">#${a.accountNo} - ${a.customerName} (Bal: ${formatCurrency(a.balance)})</option>`).join('');
        if (curVal) sel.value = curVal;
    }

    function populateTransferDropdowns() {
        const fromSel = document.getElementById('transferFrom');
        const toSel = document.getElementById('transferTo');
        if (!fromSel || !toSel) return;

        const options = state.accounts.map(a =>
            `<option value="${a.accountNo}">#${a.accountNo} - ${a.customerName} (${a.accountType} | Bal: ${formatCurrency(a.balance)})</option>`
        ).join('');

        const fromVal = fromSel.value;
        const toVal = toSel.value;

        fromSel.innerHTML = '<option value="">Select source account...</option>' + options;
        toSel.innerHTML = '<option value="">Select destination account...</option>' + options;

        if (fromVal) fromSel.value = fromVal;
        if (toVal) toSel.value = toVal;
        updateTransferSourceBalance();
    }

    function populateStatementDropdown() {
        const sel = document.getElementById('statementAccountSelect');
        if (!sel) return;
        const curVal = sel.value;
        sel.innerHTML = '<option value="">-- All Bank Transactions (Global Ledger) --</option>' +
            state.accounts.map(a => `<option value="${a.accountNo}">Account #${a.accountNo} (${a.customerName} - ${a.accountType})</option>`).join('');
        if (curVal) sel.value = curVal;
    }

    function updateTransferSourceBalance() {
        const fromAccNo = parseInt(document.getElementById('transferFrom')?.value);
        const text = document.getElementById('transferSourceBalanceText');
        if (!text) return;
        if (!fromAccNo) {
            text.textContent = 'Available Balance: -';
            return;
        }
        const acc = state.accounts.find(a => a.accountNo === fromAccNo);
        if (acc) {
            text.innerHTML = `Available Balance: <strong>${formatCurrency(acc.balance)}</strong> (${acc.accountType})`;
        }
    }

    function setTransferAmount(amt) {
        const input = document.getElementById('transferAmount');
        if (input) input.value = amt;
    }

    function updateDepositPreview() {
        const accNo = parseInt(document.getElementById('depositAccountSelect')?.value);
        const text = document.getElementById('depositCurrentBal');
        if (!text) return;
        if (!accNo) {
            text.textContent = 'Current Balance: -';
            return;
        }
        const acc = state.accounts.find(a => a.accountNo === accNo);
        text.innerHTML = acc ? `Current Balance: <strong>${formatCurrency(acc.balance)}</strong>` : '-';
    }

    function updateWithdrawPreview() {
        const accNo = parseInt(document.getElementById('withdrawAccountSelect')?.value);
        const text = document.getElementById('withdrawCurrentBal');
        const note = document.getElementById('withdrawRuleNote');
        if (!text) return;
        if (!accNo) {
            text.textContent = 'Current Balance: -';
            return;
        }
        const acc = state.accounts.find(a => a.accountNo === accNo);
        if (acc) {
            text.innerHTML = `Current Balance: <strong>${formatCurrency(acc.balance)}</strong>`;
            if (acc.accountType === 'SAVINGS') {
                note.innerHTML = `⚠️ <strong>Savings Account:</strong> Minimum balance of Rs. 1000.00 required. Max withdrawable now: <strong>${formatCurrency(Math.max(0, acc.balance - 1000))}</strong>.`;
            } else {
                note.innerHTML = `ℹ️ <strong>Current Account:</strong> Overdraft allowed up to Rs. 10,000.00. Max withdrawable now: <strong>${formatCurrency(acc.balance + 10000)}</strong>.`;
            }
        }
    }

    function handleAccTypeChange() {
        const type = document.getElementById('accType')?.value;
        const help = document.getElementById('accTypeRuleHelp');
        const initBal = document.getElementById('accInitialBalance');
        if (type === 'SAVINGS') {
            if (help) help.textContent = 'Minimum opening deposit: Rs. 1000.00 (4% yearly interest)';
            if (initBal && Number(initBal.value) < 1000) initBal.value = '1000.00';
        } else {
            if (help) help.textContent = 'No minimum balance required (Overdraft up to Rs. 10,000, 0% interest)';
        }
    }

    // Modal Control
    function openModal(id) {
        const el = document.getElementById(id);
        if (el) el.classList.add('open');
    }

    function closeModal(id) {
        const el = document.getElementById(id);
        if (el) el.classList.remove('open');
    }

    // --- INTERACTIVE FINANCIAL TOOLS (NEW FEATURES) ---

    // 1. EMI Calculator (Reducing Balance Formula: E = P * r * (1+r)^n / ((1+r)^n - 1))
    function calculateEMI() {
        const amountEl = document.getElementById('emiAmount');
        const rateEl = document.getElementById('emiRate');
        const tenureEl = document.getElementById('emiTenure');

        if (!amountEl || !rateEl || !tenureEl) return;

        const P = parseFloat(amountEl.value);
        const annualRate = parseFloat(rateEl.value);
        const n = parseInt(tenureEl.value);

        document.getElementById('emiAmountLabel').textContent = formatCurrency(P);
        document.getElementById('emiRateLabel').textContent = annualRate.toFixed(2) + '%';
        document.getElementById('emiTenureLabel').textContent = `${n} Months (${(n / 12).toFixed(1)} Yrs)`;

        const r = (annualRate / 12.0) / 100.0;
        const factor = Math.pow(1.0 + r, n);
        const emi = (P * r * factor) / (factor - 1.0);
        const totalPayment = emi * n;
        const totalInterest = totalPayment - P;

        document.getElementById('emiMonthlyPayment').textContent = formatCurrency(emi);
        document.getElementById('emiTotalInterest').textContent = formatCurrency(totalInterest);
        document.getElementById('emiTotalPayable').textContent = formatCurrency(totalPayment);
    }

    // 2. Fixed Deposit Calculator (Quarterly Compounding Formula: A = P * (1 + r/(4*100))^(4 * t/12))
    function calculateFDProjection() {
        const principalEl = document.getElementById('fdCalcPrincipal');
        const tenureEl = document.getElementById('fdCalcTenure');
        if (!principalEl || !tenureEl) return;

        const P = parseFloat(principalEl.value) || 0;
        const months = parseInt(tenureEl.value);
        const selectedOpt = tenureEl.options[tenureEl.selectedIndex];
        const annualRate = parseFloat(selectedOpt.getAttribute('data-rate')) || 6.5;

        const quarterlyRate = (annualRate / 100.0) / 4.0;
        const quarters = (months / 12.0) * 4.0;
        const maturity = P * Math.pow(1.0 + quarterlyRate, quarters);
        const interest = maturity - P;

        document.getElementById('fdProjPrincipal').textContent = formatCurrency(P);
        document.getElementById('fdProjInterest').textContent = formatCurrency(interest);
        document.getElementById('fdProjMaturity').textContent = formatCurrency(maturity);
    }

    function updateFDSourceBalance() {
        const accNo = parseInt(document.getElementById('fdSourceAccount')?.value);
        const text = document.getElementById('fdSourceBalText');
        if (!text) return;
        if (!accNo) {
            text.textContent = 'Available Balance: -';
            return;
        }
        const acc = state.accounts.find(a => a.accountNo === accNo);
        text.innerHTML = acc ? `Available Balance: <strong>${formatCurrency(acc.balance)}</strong>` : '-';
    }

    function updateFDModalProjection() {
        const principal = parseFloat(document.getElementById('fdPrincipal')?.value) || 0;
        const tenureSel = document.getElementById('fdTenure');
        if (!tenureSel) return;
        const months = parseInt(tenureSel.value);
        const rate = parseFloat(tenureSel.options[tenureSel.selectedIndex].getAttribute('data-rate')) || 6.5;

        const quarterlyRate = (rate / 100.0) / 4.0;
        const quarters = (months / 12.0) * 4.0;
        const maturity = principal * Math.pow(1.0 + quarterlyRate, quarters);

        const d = new Date();
        d.setMonth(d.getMonth() + months);

        const box = document.getElementById('fdModalProjection');
        if (box) {
            box.innerHTML = `💎 Estimated Maturity Value: <strong>${formatCurrency(maturity)}</strong> @ ${rate}% p.a. (Maturity: ${formatDate(d.toISOString())})`;
        }
    }

    // --- ACTION HANDLERS ---

    async function handleOpenAccountSubmit(e) {
        e.preventDefault();
        const customerId = parseInt(document.getElementById('accCustomerId').value);
        const branchId = parseInt(document.getElementById('accBranchId').value);
        const accountType = document.getElementById('accType').value;
        const initialBalance = parseFloat(document.getElementById('accInitialBalance').value) || 0;

        try {
            const res = await apiRequest('/accounts', {
                method: 'POST',
                body: { customerId, branchId, accountType, initialBalance }
            });
            showToast(res.message || 'Account opened successfully!', 'success');
            closeModal('modalOpenAccount');
            e.target.reset();
            await loadAllData();
        } catch (err) {
            showToast(err.message, 'error');
        }
    }

    async function handleOpenFDSubmit(e) {
        e.preventDefault();
        const accountNo = parseInt(document.getElementById('fdSourceAccount').value);
        const principalAmount = parseFloat(document.getElementById('fdPrincipal').value);
        const tenureSel = document.getElementById('fdTenure');
        const tenureMonths = parseInt(tenureSel.value);
        const interestRate = parseFloat(tenureSel.options[tenureSel.selectedIndex].getAttribute('data-rate')) || 6.5;

        try {
            const res = await apiRequest('/fds', {
                method: 'POST',
                body: { accountNo, principalAmount, interestRate, tenureMonths }
            });
            showToast(res.message || 'Fixed deposit certificate issued!', 'success');
            closeModal('modalOpenFD');
            e.target.reset();
            await loadAllData();
        } catch (err) {
            showToast(err.message, 'error');
        }
    }

    async function closeFixedDeposit(fdId) {
        if (!confirm(`Are you sure you want to liquidate Fixed Deposit #${fdId}? If closed prematurely, a 1% penalty on interest applies.`)) {
            return;
        }

        try {
            const res = await apiRequest(`/fds/${fdId}/close`, { method: 'POST' });
            showToast(res.message, 'success');
            await loadAllData();
        } catch (err) {
            showToast(err.message, 'error');
        }
    }

    async function toggleAccountStatus(accNo, currentStatus) {
        const newStatus = (currentStatus === 'FROZEN') ? 'ACTIVE' : 'FROZEN';
        const actionName = (newStatus === 'FROZEN') ? 'Freeze' : 'Unfreeze';

        if (!confirm(`Are you sure you want to ${actionName} Account #${accNo}?`)) {
            return;
        }

        try {
            const res = await apiRequest(`/accounts/${accNo}/status`, {
                method: 'POST',
                body: { status: newStatus }
            });
            showToast(res.message, 'success');
            await loadAccounts();
        } catch (err) {
            showToast(err.message, 'error');
        }
    }

    async function updateLoanStatus(loanId, status) {
        if (!confirm(`Set status of Loan #${loanId} to ${status}?`)) return;

        try {
            const res = await apiRequest(`/loans/${loanId}/status`, {
                method: 'POST',
                body: { status }
            });
            showToast(res.message, 'success');
            await loadLoans();
            await loadStats();
        } catch (err) {
            showToast(err.message, 'error');
        }
    }

    function openPayLoanModal(loanId, remainingAmount) {
        const loan = state.loans.find(l => l.loanId === loanId);
        document.getElementById('payLoanId').value = loanId;
        document.getElementById('payLoanAmount').value = Math.min(remainingAmount, 5000);
        document.getElementById('payLoanMeta').innerHTML = `
            <div><strong>Loan #${loanId}</strong> (${loan ? loan.loanType : ''} LOAN)</div>
            <div style="font-size:0.82rem; color:#94a3b8; margin-top:4px;">
                Customer: <strong>${loan ? loan.customerName : ''}</strong> | Outstanding: <strong>${formatCurrency(remainingAmount)}</strong>
            </div>
        `;
        openModal('modalPayLoan');
    }

    async function handlePayLoanSubmit(e) {
        e.preventDefault();
        const loanId = parseInt(document.getElementById('payLoanId').value);
        const accountNo = parseInt(document.getElementById('payLoanAccount').value);
        const amount = parseFloat(document.getElementById('payLoanAmount').value);

        try {
            const res = await apiRequest(`/loans/${loanId}/pay`, {
                method: 'POST',
                body: { accountNo, amount }
            });
            showToast(res.message || 'Payment debited successfully!', 'success');
            closeModal('modalPayLoan');
            await loadAllData();
        } catch (err) {
            showToast(err.message, 'error');
        }
    }

    async function viewCustomerPortfolio(customerId) {
        const content = document.getElementById('customerPortfolioContent');
        openModal('modalCustomerPortfolio');
        content.innerHTML = '<div class="text-center">Loading 360° financial portfolio...</div>';

        try {
            const data = await apiRequest(`/customers/${customerId}/portfolio`);
            const c = data.customer;
            const accs = data.accounts || [];
            const fds = data.fixedDeposits || [];
            const loans = data.loans || [];

            content.innerHTML = `
                <div class="passbook-card" style="margin-bottom: 20px;">
                    <div class="card-top">
                        <div>
                            <h2>${c.name}</h2>
                            <span style="color:#94a3b8; font-size:0.8rem;">Customer #${c.customerId}</span>
                        </div>
                        <div style="text-align:right;">
                            <span style="font-size:0.75rem; color:#94a3b8;">CONTACT</span>
                            <div><strong>${c.phone}</strong></div>
                            <div style="font-size:0.78rem; color:#38bdf8;">${c.email || 'No email registered'}</div>
                        </div>
                    </div>
                    <div style="font-size:0.82rem; color:#cbd5e1; margin-top:8px;">
                        📍 ${c.address || 'No address registered'}
                    </div>
                </div>

                <div class="portfolio-kpis">
                    <div class="portfolio-kpi-item">
                        <span>Liquid Deposits</span>
                        <strong style="color:var(--success);">${formatCurrency(data.totalDeposits)}</strong>
                    </div>
                    <div class="portfolio-kpi-item">
                        <span>FD Investments</span>
                        <strong style="color:#38bdf8;">${formatCurrency(data.totalInvestments)}</strong>
                    </div>
                    <div class="portfolio-kpi-item">
                        <span>Loan Liabilities</span>
                        <strong style="color:var(--danger);">${formatCurrency(data.totalDebt)}</strong>
                    </div>
                    <div class="portfolio-kpi-item">
                        <span>Net Bank Worth</span>
                        <strong style="color:var(--primary);">${formatCurrency(data.netWorth)}</strong>
                    </div>
                </div>

                <h4 style="margin: 16px 0 8px 0; font-size: 0.92rem;">Linked Bank Accounts (${accs.length})</h4>
                <div class="table-responsive" style="margin-bottom: 16px;">
                    <table class="data-table">
                        <thead>
                            <tr><th>Account #</th><th>Type</th><th>Branch</th><th>Balance</th><th>Status</th></tr>
                        </thead>
                        <tbody>
                            ${accs.length ? accs.map(a => `
                                <tr>
                                    <td><strong>#${a.accountNo}</strong></td>
                                    <td><span class="badge ${a.accountType === 'SAVINGS' ? 'badge-savings' : 'badge-current'}">${a.accountType}</span></td>
                                    <td>${a.branchName}</td>
                                    <td><strong>${formatCurrency(a.balance)}</strong></td>
                                    <td><span class="badge ${a.status === 'ACTIVE' ? 'badge-approved' : 'badge-frozen'}">${a.status}</span></td>
                                </tr>
                            `).join('') : '<tr><td colspan="5" class="text-center text-muted">No accounts opened.</td></tr>'}
                        </tbody>
                    </table>
                </div>

                <h4 style="margin: 16px 0 8px 0; font-size: 0.92rem;">Fixed Deposits (${fds.length})</h4>
                <div class="table-responsive" style="margin-bottom: 16px;">
                    <table class="data-table">
                        <thead>
                            <tr><th>FD #</th><th>Principal</th><th>Rate</th><th>Maturity Date</th><th>Maturity Value</th><th>Status</th></tr>
                        </thead>
                        <tbody>
                            ${fds.length ? fds.map(f => `
                                <tr>
                                    <td><strong>#${f.fdId}</strong></td>
                                    <td><strong>${formatCurrency(f.principalAmount)}</strong></td>
                                    <td>${f.interestRate}%</td>
                                    <td>${formatDate(f.maturityDate)}</td>
                                    <td><strong style="color:var(--success);">${formatCurrency(f.maturityAmount)}</strong></td>
                                    <td><span class="badge ${f.status === 'ACTIVE' ? 'badge-approved' : 'badge-premature'}">${f.status}</span></td>
                                </tr>
                            `).join('') : '<tr><td colspan="6" class="text-center text-muted">No fixed deposits.</td></tr>'}
                        </tbody>
                    </table>
                </div>

                <h4 style="margin: 16px 0 8px 0; font-size: 0.92rem;">Loan Facilities (${loans.length})</h4>
                <div class="table-responsive">
                    <table class="data-table">
                        <thead>
                            <tr><th>Loan #</th><th>Facility</th><th>Remaining Principal</th><th>Interest</th><th>Status</th></tr>
                        </thead>
                        <tbody>
                            ${loans.length ? loans.map(l => `
                                <tr>
                                    <td><strong>#${l.loanId}</strong></td>
                                    <td><span class="badge badge-accent">${l.loanType}</span></td>
                                    <td><strong>${formatCurrency(l.amount)}</strong></td>
                                    <td>${l.interestRate}%</td>
                                    <td><span class="badge ${l.status === 'APPROVED' ? 'badge-approved' : l.status === 'CLOSED' ? 'badge-closed' : 'badge-pending'}">${l.status}</span></td>
                                </tr>
                            `).join('') : '<tr><td colspan="5" class="text-center text-muted">No active loans.</td></tr>'}
                        </tbody>
                    </table>
                </div>
            `;
        } catch (err) {
            content.innerHTML = `<div class="text-center" style="color:var(--danger);">${err.message}</div>`;
        }
    }

    function exportTransactionsCSV() {
        const accNo = parseInt(document.getElementById('statementAccountSelect')?.value) || 0;
        const url = `${API_BASE}/transactions/csv${accNo > 0 ? '?accountNo=' + accNo : ''}`;
        window.open(url, '_blank');
        showToast('Exporting transaction ledger to CSV...', 'info');
    }

    async function handleDepositSubmit(e) {
        e.preventDefault();
        const accNo = parseInt(document.getElementById('depositAccountSelect').value);
        const amount = parseFloat(document.getElementById('depositAmount').value);

        try {
            const res = await apiRequest(`/accounts/${accNo}/deposit`, {
                method: 'POST',
                body: { amount }
            });
            showToast(res.message || 'Deposit completed!', 'success');
            closeModal('modalDeposit');
            e.target.reset();
            await loadAllData();
        } catch (err) {
            showToast(err.message, 'error');
        }
    }

    async function handleWithdrawSubmit(e) {
        e.preventDefault();
        const accNo = parseInt(document.getElementById('withdrawAccountSelect').value);
        const amount = parseFloat(document.getElementById('withdrawAmount').value);
        const transactionPin = document.getElementById('withdrawtransactionPin').value;

        try {
            const res = await apiRequest(`/accounts/${accNo}/withdraw`, {
                method: 'POST',
                body: { amount, transactionPin }
            });
            showToast(res.message || 'Withdrawal completed!', 'success');
            closeModal('modalWithdraw');
            e.target.reset();
            await loadAllData();
        } catch (err) {
            showToast(err.message, 'error');
        }
    }

    async function handleTransferSubmit(e) {
        e.preventDefault();
        const fromAccount = parseInt(document.getElementById('transferFrom').value);
        const toAccount = parseInt(document.getElementById('transferTo').value);
        const amount = parseFloat(document.getElementById('transferAmount').value);
        const transactionPin = document.getElementById('transferPin').value;

        const btn = document.getElementById('btnSubmitTransfer');
        const resultBox = document.getElementById('transferResultBox');
        const resultText = document.getElementById('transferResultText');

        btn.disabled = true;
        btn.textContent = 'Executing sp_transfer_funds...';

        try {
            const res = await apiRequest('/transfer', {
                method: 'POST',
                body: { fromAccount, toAccount, amount, transactionPin }
            });

            resultBox.style.display = 'block';
            resultText.innerHTML = `<span style="color:var(--success); font-weight:700;">[RESULT] ${res.message}</span>`;
            showToast(res.message, 'success');
            document.getElementById('transferAmount').value = '';
            document.getElementById('transferPin').value = '';
            await loadAllData();
        } catch (err) {
            resultBox.style.display = 'block';
            resultText.innerHTML = `<span style="color:var(--danger); font-weight:700;">[ERROR] ${err.message}</span>`;
            showToast(err.message, 'error');
        } finally {
            btn.disabled = false;
            btn.textContent = 'Execute Stored Procedure Transfer';
        }
    }

    async function handleAddCustomerSubmit(e) {
        e.preventDefault();
        const name = document.getElementById('custName').value.trim();
        const phone = document.getElementById('custPhone').value.trim();
        const email = document.getElementById('custEmail').value.trim();
        const address = document.getElementById('custAddress').value.trim();

        try {
            const res = await apiRequest('/customers', {
                method: 'POST',
                body: { name, phone, email, address }
            });
            showToast(res.message || 'Customer registered!', 'success');
            closeModal('modalAddCustomer');
            e.target.reset();
            await loadCustomers();
            await loadStats();
        } catch (err) {
            showToast(err.message, 'error');
        }
    }

    async function handleAddBranchSubmit(e) {
        e.preventDefault();
        const branchName = document.getElementById('brName').value.trim();
        const ifscCode = document.getElementById('brIfsc').value.trim().toUpperCase();
        const city = document.getElementById('brCity').value.trim();

        try {
            const res = await apiRequest('/branches', {
                method: 'POST',
                body: { branchName, ifscCode, city }
            });
            showToast(res.message || 'Branch registered!', 'success');
            closeModal('modalAddBranch');
            e.target.reset();
            await loadBranches();
            await loadStats();
        } catch (err) {
            showToast(err.message, 'error');
        }
    }

    async function handleApplyLoanSubmit(e) {
        e.preventDefault();
        const customerId = parseInt(document.getElementById('loanCustId').value);
        const branchId = parseInt(document.getElementById('loanBranchId').value);
        const loanType = document.getElementById('loanType').value;
        const amount = parseFloat(document.getElementById('loanAmount').value);
        const interestRate = parseFloat(document.getElementById('loanInterestRate').value);

        try {
            const res = await apiRequest('/loans', {
                method: 'POST',
                body: { customerId, branchId, loanType, amount, interestRate }
            });
            showToast(res.message || 'Loan application submitted!', 'success');
            closeModal('modalApplyLoan');
            e.target.reset();
            await loadLoans();
            await loadStats();
        } catch (err) {
            showToast(`[Trigger Rejection] ${err.message}`, 'error');
        }
    }

    function quickDeposit(accNo) {
        const sel = document.getElementById('depositAccountSelect');
        if (sel) sel.value = accNo;
        updateDepositPreview();
        openModal('modalDeposit');
    }

    function quickWithdraw(accNo) {
        const sel = document.getElementById('withdrawAccountSelect');
        if (sel) sel.value = accNo;
        updateWithdrawPreview();
        openModal('modalWithdraw');
    }

    async function applyInterest(accNo) {
        const acc = state.accounts.find(a => a.accountNo === accNo);
        const isSavings = acc && acc.accountType === 'SAVINGS';
        const confirmMsg = isSavings
            ? `Calculate and credit 4% annual interest to Savings Account #${accNo}?`
            : `Apply interest calculation for Current Account #${accNo}? (Current accounts earn 0% interest).`;

        if (!confirm(confirmMsg)) return;

        try {
            const res = await apiRequest(`/accounts/${accNo}/interest`, {
                method: 'POST'
            });
            showToast(res.message, 'success');
            await loadAllData();
        } catch (err) {
            showToast(err.message, 'error');
        }
    }

    async function viewPassbook(accNo) {
        const content = document.getElementById('accountDetailsContent');
        openModal('modalAccountDetails');
        content.innerHTML = '<div class="text-center">Loading passbook...</div>';

        try {
            const ad = await apiRequest(`/accounts/${accNo}`);
            content.innerHTML = `
                <div class="passbook-card">
                    <div class="card-top">
                        <div>
                            <span class="badge ${ad.accountType === 'SAVINGS' ? 'badge-savings' : 'badge-current'}">${ad.accountType} ACCOUNT</span>
                            <span class="badge ${ad.status === 'ACTIVE' ? 'badge-approved' : 'badge-frozen'}" style="margin-left:6px;">${ad.status || 'ACTIVE'}</span>
                            <h2 style="margin-top: 6px;">#${ad.accountNo}</h2>
                        </div>
                        <div style="text-align: right;">
                            <span style="font-size: 0.72rem; color: #94a3b8;">BRANCH</span>
                            <div style="font-weight: 700;">${ad.branchName}</div>
                            <code style="font-size: 0.75rem; color: #38bdf8;">${ad.ifscCode}</code>
                        </div>
                    </div>
                    <div class="acc-balance">
                        <span style="font-size: 0.75rem; color: #94a3b8; display: block; font-weight: normal;">CURRENT BALANCE</span>
                        ${formatCurrency(ad.balance)}
                    </div>
                    <div class="card-grid">
                        <div>
                            <span>Account Holder</span>
                            <strong>${ad.customerName}</strong>
                        </div>
                        <div>
                            <span>Customer ID</span>
                            <strong>#${ad.customerId}</strong>
                        </div>
                        <div>
                            <span>Phone Number</span>
                            <strong>${ad.phone}</strong>
                        </div>
                        <div>
                            <span>Email Address</span>
                            <strong>${ad.email || 'N/A'}</strong>
                        </div>
                        <div>
                            <span>Date Opened</span>
                            <strong>${formatDate(ad.openedOn)}</strong>
                        </div>
                        <div>
                            <span>Interest Rate</span>
                            <strong>${ad.accountType === 'SAVINGS' ? '4.00% p.a.' : '0.00% (Overdraft Rs. 10,000)'}</strong>
                        </div>
                    </div>
                </div>
                <div style="text-align: right; margin-top: 14px; display:flex; gap:8px; justify-content:flex-end;">
                    <button class="btn btn-secondary btn-sm" onclick="App.viewAccountStatement(${ad.accountNo})">📜 Mini Statement</button>
                    <button class="btn btn-secondary btn-sm" onclick="App.exportTransactionsCSV()">📥 Export CSV</button>
                    <button class="btn btn-secondary btn-sm" onclick="window.print()">🖨️ Print Passbook</button>
                </div>
            `;
        } catch (err) {
            content.innerHTML = `<div class="text-center" style="color:var(--danger);">${err.message}</div>`;
        }
    }

    async function viewAccountStatement(accNo) {
        closeModal('modalAccountDetails');
        switchTab('transactions');
        const sel = document.getElementById('statementAccountSelect');
        if (sel) {
            sel.value = accNo;
            loadAccountMiniStatement();
        }
    }

    async function loadAccountMiniStatement() {
        const accNo = parseInt(document.getElementById('statementAccountSelect').value);
        const banner = document.getElementById('statementBanner');
        const meta = document.getElementById('statementMeta');
        const tbody = document.getElementById('transactionsTableBody');

        if (!accNo) {
            if (banner) banner.style.display = 'none';
            renderGlobalTransactions();
            return;
        }

        const acc = state.accounts.find(a => a.accountNo === accNo);
        if (banner && meta && acc) {
            banner.style.display = 'block';
            meta.innerHTML = `
                Showing the last 10 transactions for <strong>Account #${acc.accountNo}</strong> (${acc.customerName} | ${acc.accountType} | Current Balance: <strong>${formatCurrency(acc.balance)}</strong>):
            `;
        }

        tbody.innerHTML = '<tr><td colspan="6" class="text-center">Loading mini-statement...</td></tr>';

        try {
            const txns = await apiRequest(`/accounts/${accNo}/statement`);
            if (!txns || txns.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No transactions found for this account.</td></tr>';
                return;
            }

            tbody.innerHTML = txns.map(t => {
                const isCredit = ['DEPOSIT', 'TRANSFER_IN', 'INTEREST', 'FD_MATURE'].includes(t.txnType);
                const badgeClass = isCredit ? 'badge-credit' : 'badge-debit';
                return `
                    <tr>
                        <td><strong>#${t.txnId}</strong></td>
                        <td><code>Account #${t.accountNo}</code></td>
                        <td><span class="badge ${badgeClass}">${t.txnType}</span></td>
                        <td><strong style="color: ${isCredit ? 'var(--success)' : 'var(--danger)'};">
                            ${isCredit ? '+' : '-'}${formatCurrency(t.amount)}
                        </strong></td>
                        <td>${formatCurrency(t.balanceAfter)}</td>
                        <td>${formatDateTime(t.txnDate)}</td>
                    </tr>
                `;
            }).join('');
        } catch (err) {
            tbody.innerHTML = `<tr><td colspan="6" class="text-center" style="color:var(--danger);">${err.message}</td></tr>`;
        }
    }

    function openNewAccountForCustomer(customerId) {
        const sel = document.getElementById('accCustomerId');
        if (sel) sel.value = customerId;
        openModal('modalOpenAccount');
    }

    function refreshTransactions() {
        const sel = document.getElementById('statementAccountSelect');
        if (sel && sel.value) {
            loadAccountMiniStatement();
        } else {
            loadTransactions();
        }
        showToast('Transactions refreshed', 'info');
    }

    // Initialization
    function init() {
        initTheme();
        startClock();
        authenticate();

        document.querySelectorAll('.nav-item').forEach(btn => {
            btn.addEventListener('click', () => {
                const tab = btn.getAttribute('data-tab');
                if (tab) switchTab(tab);
            });
        });

        const globalSearch = document.getElementById('globalSearchInput');
        if (globalSearch) {
            globalSearch.addEventListener('input', (e) => {
                const val = e.target.value;
                const accSearch = document.getElementById('accountSearchInput');
                if (accSearch) accSearch.value = val;
                if (state.activeTab !== 'accounts' && val.trim().length > 0) {
                    switchTab('accounts');
                }
                renderAccountsTable();
            });
        }

        const btnRefreshDb = document.getElementById('btnRefreshDb');
        if (btnRefreshDb) {
            btnRefreshDb.onclick = (e) => {
                e.stopPropagation();
                checkHealth(true).then(() => loadAllData());
            };
        }

        const dbStatusPill = document.getElementById('dbStatusPill');
        if (dbStatusPill) {
            dbStatusPill.onclick = () => {
                checkHealth(true).then(() => loadAllData());
            };
        }

        document.querySelectorAll('.modal-backdrop').forEach(modal => {
            modal.addEventListener('click', (e) => {
                if (e.target === modal) modal.classList.remove('open');
            });
        });

        if (state.token) { checkHealth(false); loadAllData(); }
        calculateEMI();
        calculateFDProjection();
    }

    // Public API
    return {
        init,
        switchTab,
        openModal,
        closeModal,
        filterAccounts,
        renderAccountsTable,
        handleOpenAccountSubmit,
        handleDepositSubmit,
        handleWithdrawSubmit,
        handleTransferSubmit,
        handleAddCustomerSubmit,
        handleAddBranchSubmit,
        handleApplyLoanSubmit,
        handleOpenFDSubmit,
        closeFixedDeposit,
        handlePayLoanSubmit,
        toggleAccountStatus,
        updateLoanStatus,
        openPayLoanModal,
        viewCustomerPortfolio,
        exportTransactionsCSV,
        calculateEMI,
        calculateFDProjection,
        updateFDModalProjection,
        updateFDSourceBalance,
        handleAccTypeChange,
        quickDeposit,
        quickWithdraw,
        applyInterest,
        viewPassbook,
        viewAccountStatement,
        loadAccountMiniStatement,
        openNewAccountForCustomer,
        refreshTransactions,
        setTransferAmount,
        updateTransferSourceBalance,
        updateDepositPreview,
        updateWithdrawPreview
        ,signOut
    };
})();

// Boot Application
document.addEventListener('DOMContentLoaded', () => {
    App.init();
});
