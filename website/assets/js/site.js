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
      navLinks.classList.toggle('open');
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
});
