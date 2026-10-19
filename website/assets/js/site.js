// Liquid LSD Static Site & Docs JavaScript
document.addEventListener('DOMContentLoaded', () => {
  // 1. Dynamic Year in Footer
  const yearEl = document.getElementById('year');
  if (yearEl) {
    yearEl.textContent = new Date().getFullYear();
  }

  // 2. Mobile Header Nav Toggle
  const mobileToggle = document.querySelector('.mobile-toggle');
  const navLinks = document.querySelector('.nav-links');
  if (mobileToggle && navLinks) {
    mobileToggle.addEventListener('click', () => {
      const open = navLinks.classList.toggle('open');
      mobileToggle.setAttribute('aria-expanded', String(open));
    });
  }

  // 3. Documentation Sidebar Toggle (Mobile / Tablet)
  const sidebarToggle = document.querySelector('.sidebar-toggle');
  const docsSidebar = document.querySelector('.docs-sidebar');
  const sidebarBackdrop = document.querySelector('.sidebar-backdrop');
  if (sidebarToggle && docsSidebar) {
    sidebarToggle.addEventListener('click', () => {
      docsSidebar.classList.toggle('open');
      if (sidebarBackdrop) sidebarBackdrop.classList.toggle('open');
    });

    if (sidebarBackdrop) {
      sidebarBackdrop.addEventListener('click', () => {
        docsSidebar.classList.remove('open');
        sidebarBackdrop.classList.remove('open');
      });
    }
  }

  // 4. Lightbox Modal for Screenshots
  const screenshotCards = document.querySelectorAll('.screenshot-card');
  const modal = document.getElementById('lightbox-modal');
  const modalImg = document.getElementById('lightbox-img');
  const modalCaption = document.getElementById('lightbox-caption');
  const modalClose = document.querySelector('.lightbox-close');
  const modalBackdrop = document.querySelector('.lightbox-backdrop');

  if (modal && screenshotCards.length > 0) {
    screenshotCards.forEach(card => {
      card.addEventListener('click', () => {
        const fullSrc = card.getAttribute('data-full') || card.querySelector('img').src;
        const title = card.querySelector('h4') ? card.querySelector('h4').textContent : '';
        const desc = card.querySelector('p') ? card.querySelector('p').textContent : '';
        
        modalImg.src = fullSrc;
        modalCaption.textContent = title ? `${title} — ${desc}` : desc;
        modal.classList.add('open');
        modal.setAttribute('aria-hidden', 'false');
      });
    });

    const closeModal = () => {
      modal.classList.remove('open');
      modal.setAttribute('aria-hidden', 'true');
    };

    if (modalClose) modalClose.addEventListener('click', closeModal);
    if (modalBackdrop) modalBackdrop.addEventListener('click', closeModal);
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && modal.classList.contains('open')) {
        closeModal();
      }
    });
  }

  // 5. Code Block Copy Buttons in Markdown
  const codeBlocks = document.querySelectorAll('.markdown-body pre');
  codeBlocks.forEach(pre => {
    const code = pre.querySelector('code');
    if (!code) return;

    const copyBtn = document.createElement('button');
    copyBtn.className = 'copy-btn';
    copyBtn.textContent = 'Copy';
    copyBtn.setAttribute('aria-label', 'Copy code to clipboard');

    copyBtn.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(code.innerText);
        copyBtn.textContent = 'Copied!';
        copyBtn.style.color = 'var(--accent-cyan)';
        setTimeout(() => {
          copyBtn.textContent = 'Copy';
          copyBtn.style.color = '';
        }, 2000);
      } catch (err) {
        console.error('Failed to copy: ', err);
      }
    });

    pre.appendChild(copyBtn);
  });

  // 6. Highlight the download for the visitor's OS
  const ua = navigator.userAgent;
  const os = /Windows/.test(ua) ? 'windows' : /Mac/.test(ua) ? 'macos' : /Linux|X11/.test(ua) ? 'linux' : null;
  if (os) {
    document.querySelectorAll('.download-tile[data-os="' + os + '"]').forEach(t => t.classList.add('recommended'));
  }

  // 7. Show the latest release number (falls back to the static text if GitHub is unreachable)
  const versionEls = document.querySelectorAll('[data-version]');
  if (versionEls.length) {
    fetch('https://api.github.com/repos/greenjon/liquid-lsd/releases/latest')
      .then(r => (r.ok ? r.json() : Promise.reject()))
      .then(rel => versionEls.forEach(el => { el.textContent = rel.tag_name; }))
      .catch(() => {});
  }

  // 4. Click-to-load video players (poster only until clicked)
  document.querySelectorAll('.video-facade').forEach((el) => {
    el.querySelector('button').addEventListener('click', () => {
      const v = document.createElement('video');
      v.controls = true;
      v.autoplay = true;
      v.playsInline = true;
      v.src = el.dataset.src;
      const t = document.createElement('track');
      t.kind = 'captions';
      t.label = 'English';
      t.srclang = 'en';
      t.src = el.dataset.vtt;
      t.default = true;
      v.appendChild(t);
      el.replaceChildren(v);
      el.classList.add('loaded');
    });
  });

  // 5. Muted preview loops: play only while on screen; posters only on phones, reduced motion or data saver
  const loops = document.querySelectorAll('video[data-loop]');
  const conn = navigator.connection || {};
  const still = window.matchMedia('(prefers-reduced-motion: reduce)').matches ||
    window.matchMedia('(max-width: 700px)').matches || conn.saveData;
  if (loops.length && !still && 'IntersectionObserver' in window) {
    const io = new IntersectionObserver((entries) => {
      entries.forEach((e) => {
        const v = e.target;
        if (e.isIntersecting) {
          if (!v.getAttribute('src')) v.src = v.dataset.src;
          v.play().catch(() => {});
        } else {
          v.pause();
        }
      });
    }, { threshold: 0.4 });
    loops.forEach((v) => io.observe(v));
  }
});
