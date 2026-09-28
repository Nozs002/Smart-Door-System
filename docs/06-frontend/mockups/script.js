document.addEventListener('DOMContentLoaded', () => {
  const themeToggleBtn = document.getElementById('theme-toggle');
  const body = document.documentElement;

  // Initialize theme from localStorage or default to light
  let isDark = localStorage.getItem('theme') === 'dark';
  if (isDark) {
    body.setAttribute('data-theme', 'dark');
  } else {
    body.removeAttribute('data-theme');
  }

  function updateThemeIcon() {
    if (themeToggleBtn) {
      if (isDark) {
        themeToggleBtn.innerHTML = `
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="5" />
            <line x1="12" y1="1" x2="12" y2="3" /><line x1="12" y1="21" x2="12" y2="23" />
            <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" /><line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
            <line x1="1" y1="12" x2="3" y2="12" /><line x1="21" y1="12" x2="23" y2="12" />
            <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" /><line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
          </svg>`;
      } else {
        themeToggleBtn.innerHTML = `
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z" />
          </svg>`;
      }
    }
  }

  updateThemeIcon();

  if (themeToggleBtn) {
    themeToggleBtn.addEventListener('click', () => {
      isDark = !isDark;
      if (isDark) {
        body.setAttribute('data-theme', 'dark');
        localStorage.setItem('theme', 'dark');
      } else {
        body.removeAttribute('data-theme');
        localStorage.setItem('theme', 'light');
      }
      updateThemeIcon();
    });
  }

  // --- Navigation handlers ---
  const navItems = document.querySelectorAll('.nav-item');
  navItems.forEach(item => {
    item.addEventListener('click', () => {
      const target = item.dataset.target;
      if (target) {
        window.location.href = target;
      }
    });
  });

  // --- Login handler ---
  const loginBtn = document.getElementById('login-btn');
  if (loginBtn) {
    loginBtn.addEventListener('click', () => {
      const originalText = loginBtn.innerHTML;
      loginBtn.innerHTML = 'Đang đăng nhập...';
      loginBtn.style.opacity = '0.7';
      
      setTimeout(() => {
        window.location.href = 'dashboard.html';
      }, 1000);
    });
  }

  // --- Dashboard Door unlock Modal ---
  const doorCard = document.getElementById('door-card');
  const doorStatus = document.getElementById('door-status');
  const modal = document.getElementById('unlock-modal');
  const closeModalBtn = document.getElementById('close-modal-btn');
  const closeXBtn = document.getElementById('close-x-btn');
  
  if (doorCard && modal) {
    // Open modal
    doorCard.addEventListener('click', () => {
      modal.classList.add('open');
    });
  }
  
  if (closeModalBtn) {
    closeModalBtn.addEventListener('click', () => modal.classList.remove('open'));
  }
  if (closeXBtn) {
    closeXBtn.addEventListener('click', () => modal.classList.remove('open'));
  }
  // Click outside to close
  if (modal) {
    modal.addEventListener('click', (e) => {
      if (e.target === modal) {
        modal.classList.remove('open');
      }
    });
  }

  // --- Swipe logic ---
  const swipeSlider = document.getElementById('swipe-slider');
  const swipeHandle = document.getElementById('swipe-handle');
  const modalLockStatus = document.getElementById('modal-lock-status');
  const modalLockSub = document.getElementById('modal-lock-sub');
  const handleIcon = document.getElementById('handle-icon');

  if (swipeHandle && swipeSlider) {
    let isDragging = false;
    let startX = 0;
    let currentTranslate = 0;
    let isUnlocked = false;
    let autoLockTimeout = null;
    
    const setLockedUI = () => {
      isUnlocked = false;
      swipeHandle.style.transition = 'transform 0.3s ease';
      swipeHandle.style.transform = `translateX(0px)`;
      swipeSlider.classList.remove('unlocked');
      currentTranslate = 0;
      
      if(modalLockStatus) modalLockStatus.innerText = 'Đang Khóa';
      if(modalLockSub) modalLockSub.innerText = 'Vuốt để mở khóa';
      if(handleIcon) handleIcon.innerHTML = `<rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path>`;
      
      if(doorCard) {
        doorCard.classList.add('locked');
        doorCard.classList.remove('unlocked');
        const span = doorStatus.querySelector('span');
        if (span) span.innerText = 'Đang Khóa';
        const svg = doorStatus.querySelector('svg');
        if (svg) svg.innerHTML = `<rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path>`;
      }
      if(autoLockTimeout) {
        clearTimeout(autoLockTimeout);
        autoLockTimeout = null;
      }
    };

    const setUnlockedUI = () => {
      isUnlocked = true;
      const maxScroll = swipeSlider.clientWidth - swipeHandle.clientWidth - 8;
      swipeHandle.style.transition = 'transform 0.3s ease';
      swipeHandle.style.transform = `translateX(${maxScroll}px)`;
      swipeSlider.classList.add('unlocked');
      currentTranslate = maxScroll;
      
      if(modalLockStatus) modalLockStatus.innerText = 'Đã Mở';
      if(modalLockSub) modalLockSub.innerText = 'Cửa sẽ tự khóa sau 30 giây';
      if(handleIcon) handleIcon.innerHTML = `<rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 9.9-1"></path>`;
      
      if(doorCard) {
        doorCard.classList.remove('locked');
        doorCard.classList.add('unlocked');
        const span = doorStatus.querySelector('span');
        if (span) span.innerText = 'Đã Mở';
        const svg = doorStatus.querySelector('svg');
        if (svg) svg.innerHTML = `<rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 9.9-1"></path>`;
      }

      // Auto relock after 30 seconds
      if (autoLockTimeout) clearTimeout(autoLockTimeout);
      autoLockTimeout = setTimeout(() => {
        if (isUnlocked) setLockedUI();
      }, 30000);
    };

    const startDrag = (e) => {
      isDragging = true;
      startX = e.type.includes('mouse') ? e.clientX : e.touches[0].clientX;
      swipeHandle.style.transition = 'none';
      if (autoLockTimeout) clearTimeout(autoLockTimeout);
    };

    const drag = (e) => {
      if (!isDragging) return;
      const maxScroll = swipeSlider.clientWidth - swipeHandle.clientWidth - 8;
      let clientX = e.type.includes('mouse') ? e.clientX : e.touches[0].clientX;
      let deltaX = clientX - startX;
      
      let newTranslate = currentTranslate + deltaX;
      
      if (newTranslate < 0) newTranslate = 0;
      if (newTranslate > maxScroll) newTranslate = maxScroll;
      
      swipeHandle.style.transform = `translateX(${newTranslate}px)`;
    };

    const stopDrag = () => {
      if (!isDragging) return;
      isDragging = false;
      const maxScroll = swipeSlider.clientWidth - swipeHandle.clientWidth - 8;
      
      const transformStyle = swipeHandle.style.transform;
      let translateVal = currentTranslate;
      if (transformStyle) {
        const match = transformStyle.match(/translateX\((.+?)px\)/);
        if (match) translateVal = parseFloat(match[1]);
      }
      
      if (!isUnlocked) {
        // Dragging to unlock
        if (translateVal > maxScroll * 0.6) {
          setUnlockedUI();
        } else {
          setLockedUI();
        }
      } else {
        // Dragging to lock
        if (translateVal < maxScroll * 0.4) {
          setLockedUI();
        } else {
          setUnlockedUI();
        }
      }
    };

    swipeHandle.addEventListener('mousedown', startDrag);
    swipeHandle.addEventListener('touchstart', startDrag, {passive: true});
    
    document.addEventListener('mousemove', drag);
    document.addEventListener('touchmove', drag, {passive: true});
    
    document.addEventListener('mouseup', stopDrag);
    document.addEventListener('touchend', stopDrag);
  }

  // --- Segment Tabs Logic (for Access and Alerts) ---
  const segmentTabs = document.querySelectorAll('.segment-tab');
  segmentTabs.forEach(tab => {
    tab.addEventListener('click', (e) => {
      const parentScreen = e.target.closest('.screen-content') || e.target.closest('.screen');
      if (!parentScreen) return;

      const tabs = parentScreen.querySelectorAll('.segment-tab');
      const contents = parentScreen.querySelectorAll('.tab-content');

      tabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');

      const targetId = tab.dataset.tabTarget;
      contents.forEach(c => {
        if (c.id === targetId) {
          c.classList.add('active');
        } else {
          c.classList.remove('active');
        }
      });
    });
  });

  // --- Toggle Buttons in Settings ---
  const toggleBtns = document.querySelectorAll('.toggle-btn');
  toggleBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      if (btn.classList.contains('on')) {
        btn.classList.remove('on');
        btn.classList.add('off');
      } else {
        btn.classList.remove('off');
        btn.classList.add('on');
      }
    });
  });
});

  // --- Security Mode Toggle ---
  const securityToggle = document.getElementById('security-toggle');
  const securityTitle = document.getElementById('security-title');
  const securitySubtitle = document.getElementById('security-subtitle');
  const securityIcon = document.getElementById('security-icon');

  if (securityToggle && securityTitle && securitySubtitle && securityIcon) {
    securityToggle.addEventListener('click', () => {
      // The toggle class on/off is already handled by the generic .toggle-btn listener.
      // We just need to check the state AFTER a short delay or check its new class.
      setTimeout(() => {
        if (securityToggle.classList.contains('on')) {
          securityTitle.innerText = 'Chế độ bảo mật';
          securitySubtitle.innerText = 'Chỉ người có khóa';
          securityIcon.innerHTML = '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>';
        } else {
          securityTitle.innerText = 'Chế độ tự động';
          securitySubtitle.innerText = 'Bất kỳ ai cũng mở được';
          securityIcon.innerHTML = '<rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 9.9-1"></path>'; // unlock icon
        }
      }, 50);
    });
  }

  // --- Alarm Toggle ---
  const alarmToggle = document.getElementById('alarm-toggle');
  const alarmSubtitle = document.getElementById('alarm-subtitle');
  const alarmIcon = document.getElementById('alarm-icon');

  if (alarmToggle && alarmSubtitle && alarmIcon) {
    alarmToggle.addEventListener('click', () => {
      setTimeout(() => {
        if (alarmToggle.classList.contains('on')) {
          alarmSubtitle.innerText = 'Đang bật';
          alarmIcon.setAttribute('stroke', '#e74c3c'); // Red when active
        } else {
          alarmSubtitle.innerText = 'Đã tắt';
          alarmIcon.setAttribute('stroke', '#111'); // Dark when inactive
        }
      }, 50);
    });
  }
