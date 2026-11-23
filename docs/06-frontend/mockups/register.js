// Prototype only: passwords are never stored or transmitted.
document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('register-form');
  const password = document.getElementById('register-password');
  const confirmation = document.getElementById('confirm-password');
  const button = document.getElementById('register-btn');
  const status = document.getElementById('register-status');
  document.getElementById('show-register-password').addEventListener('change', event => {
    password.type = confirmation.type = event.target.checked ? 'text' : 'password';
  });
  [password, confirmation].forEach(input => input.addEventListener('input', () => confirmation.setCustomValidity('')));
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!document.getElementById('display-name').value.trim() || !document.getElementById('register-username').value.trim()) {
      status.textContent = 'Vui lòng nhập tên hiển thị và tên đăng nhập.';
      status.style.color = '#b42318'; return;
    }
    if (password.value !== confirmation.value) {
      confirmation.setCustomValidity('Mật khẩu xác nhận chưa khớp.');
      confirmation.reportValidity();
      status.textContent = 'Vui lòng nhập hai mật khẩu giống nhau.';
      status.style.color = '#b42318'; return;
    }
    button.disabled = true; button.style.opacity = '0.7';
    button.querySelector('span').textContent = 'Đang đăng ký…'; status.textContent = '';
    await new Promise(resolve => setTimeout(resolve, 700));
    form.reset(); password.type = confirmation.type = 'password';
    button.disabled = false; button.style.opacity = '';
    button.querySelector('span').textContent = 'Đăng ký'; status.style.color = '#166534';
    status.textContent = 'Đã hoàn tất đăng ký mô phỏng. Chọn Đăng nhập để quay lại; chưa tạo tài khoản trên server.';
  });
});
