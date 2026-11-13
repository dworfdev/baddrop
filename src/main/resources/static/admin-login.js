(() => {
    const form = document.getElementById('adminLoginForm');
    const btn = document.getElementById('adminLoginBtn');
    const err = document.getElementById('error');

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        err.textContent = '';
        btn.disabled = true;
        try {
            const res = await fetch('/api/admin/auth/login', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                body: JSON.stringify({
                    username: document.getElementById('adminUsername').value.trim(),
                    password: document.getElementById('adminPassword').value,
                    totp: document.getElementById('adminTotp').value.trim()
                })
            });
            if (res.ok) {
                window.location.href = '/admin';
                return;
            }
            err.textContent = res.status === 429 ? 'Too many attempts. Try again later.' : 'Invalid credentials';
        } catch (_) {
            err.textContent = 'Network error';
        } finally {
            document.getElementById('adminPassword').value = '';
            btn.disabled = false;
        }
    });
})();
