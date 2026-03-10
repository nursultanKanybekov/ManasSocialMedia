/**
 * app.js — ManasMezun University Ecosystem
 * Sidebar collapse, mobile menu, dropdowns
 */
(function () {
  'use strict';

  /* ── SIDEBAR COLLAPSE ── */
  window.toggleSidebarCollapse = function () {
    const sidebar = document.getElementById('app-sidebar');
    if (!sidebar) return;
    const collapsed = sidebar.classList.toggle('collapsed');
    const mc = document.querySelector('.main-content');
    const footer = document.querySelector('.footer');
    const brand = document.getElementById('app-navbar-brand');
    const w = collapsed ? '64px' : 'var(--sidebar-w, 240px)';
    if (mc) mc.style.marginLeft = w;
    if (footer) footer.style.marginLeft = w;
    if (brand) { brand.style.width = w; brand.style.minWidth = w; }
    localStorage.setItem('sidebar_collapsed', collapsed ? '1' : '0');
  };

  /* ── MOBILE SIDEBAR ── */
  window.toggleMobileSidebar = function () {
    const sidebar = document.getElementById('app-sidebar');
    const overlay = document.getElementById('sidebar-overlay');
    if (sidebar) sidebar.classList.toggle('mobile-open');
    if (overlay) overlay.classList.toggle('show');
  };

  window.closeMobileSidebar = function () {
    const sidebar = document.getElementById('app-sidebar');
    const overlay = document.getElementById('sidebar-overlay');
    if (sidebar) sidebar.classList.remove('mobile-open');
    if (overlay) overlay.classList.remove('show');
  };

  /* ── DROPDOWN TOGGLE ── */
  window.toggleDropdown = function (id) {
    document.querySelectorAll('.dropdown').forEach(function (d) {
      if (d.id !== id) d.classList.remove('open');
    });
    const dd = document.getElementById(id);
    if (dd) dd.classList.toggle('open');
  };

  /* ── LANGUAGE SWITCHER ── */
  window.setActiveLang = function (btn) {
    document.querySelectorAll('.lang-btn').forEach(function (b) { b.classList.remove('active'); });
    if (btn) btn.classList.add('active');
  };

  /* ── ACTIVE NAV LINK ── */
  function markActiveLink() {
    var path = window.location.pathname;
    document.querySelectorAll('.sidebar-link, .nav-item').forEach(function (a) {
      var href = a.getAttribute('href') || '';
      if (href && href !== '#' && path.startsWith(href) && href !== '/') {
        a.classList.add('active');
      } else if (href === '/main' && (path === '/' || path === '/main')) {
        a.classList.add('active');
      }
    });
  }

  /* ── CLOSE DROPDOWNS ON OUTSIDE CLICK ── */
  document.addEventListener('click', function (e) {
    if (!e.target.closest('.dropdown-wrap') && !e.target.closest('.notif-bell-wrap')) {
      document.querySelectorAll('.dropdown').forEach(function (d) { d.classList.remove('open'); });
    }
  });

  /* ── INIT ── */
  document.addEventListener('DOMContentLoaded', function () {
    // Restore collapsed state
    if (localStorage.getItem('sidebar_collapsed') === '1') {
      var sidebar = document.getElementById('app-sidebar');
      if (sidebar) {
        sidebar.classList.add('collapsed');
        var mc = document.querySelector('.main-content');
        var footer = document.querySelector('.footer');
        var brand = document.getElementById('app-navbar-brand');
        if (mc) mc.style.marginLeft = '64px';
        if (footer) footer.style.marginLeft = '64px';
        if (brand) { brand.style.width = '64px'; brand.style.minWidth = '64px'; }
      }
    }

    markActiveLink();

    // Handle resize
    window.addEventListener('resize', function () {
      if (window.innerWidth > 768) {
        closeMobileSidebar();
      }
    });
  });

})();

/* ── AVATAR UPLOAD ── */
/* Called from avatar-section fragment and edit.html file input onchange.
   Defined globally so it survives HTMX partial swaps (scripts in swapped
   HTML do NOT re-execute in HTMX 1.x). */
function uploadAvatar(input) {
    if (!input.files || !input.files[0]) return;
    var file = input.files[0];
    if (!file.type.startsWith('image/')) {
        alert('Please select an image file.');
        return;
    }
    if (file.size > 5 * 1024 * 1024) {
        alert('Image must be under 5 MB.');
        return;
    }

    var csrfToken  = document.querySelector('meta[name="_csrf"]') ?
                     document.querySelector('meta[name="_csrf"]').content : '';
    var csrfHeader = document.querySelector('meta[name="_csrf_header"]') ?
                     document.querySelector('meta[name="_csrf_header"]').content : 'X-CSRF-TOKEN';

    var formData = new FormData();
    formData.append('avatar', file);

    var headers = {};
    if (csrfToken) headers[csrfHeader] = csrfToken;

    var section = document.getElementById('avatarSection');
    if (section) section.style.opacity = '0.5';

    fetch('/profile/me/avatar', {
        method: 'POST',
        headers: headers,
        body: formData
    })
    .then(function(res) {
        if (!res.ok) throw new Error('Server error ' + res.status);
        return res.text();
    })
    .then(function(html) {
        var tmp = document.createElement('div');
        tmp.innerHTML = html;
        var newSection = tmp.querySelector('#avatarSection') || tmp.firstElementChild;
        if (newSection) {
            var old = document.getElementById('avatarSection');
            if (old) old.parentNode.replaceChild(newSection, old);
        }

        // Also update the navbar avatar immediately (it's server-rendered,
        // so we patch the DOM directly — SecurityContext is refreshed server-side
        // so the next full page load will also be correct).
        var newImg = tmp.querySelector('#avatarSection img');
        if (newImg && newImg.src) {
            var newSrc = newImg.src;
            // Update all avatar-circle images in the navbar
            document.querySelectorAll('.avatar-circle img, .av-big img').forEach(function(img) {
                img.src = newSrc;
                img.style.display = 'block';
            });
            // Hide the initials spans in the navbar
            document.querySelectorAll('.avatar-circle span, .av-big span').forEach(function(span) {
                span.style.display = 'none';
            });
        }
    })
    .catch(function(err) {
        console.error('Avatar upload error:', err);
        var s = document.getElementById('avatarSection');
        if (s) s.style.opacity = '1';
        alert('Upload failed: ' + err.message);
    });
}

/* ── LIKE TOGGLE (global, no HTMX needed) ── */
async function toggleLike(postId) {
    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

    try {
        const res = await fetch('/feed/post/' + postId + '/like', {
            method: 'POST',
            headers: { [csrfHeader]: csrfToken }
        });
        if (!res.ok) return;
        const html = await res.text();
        const wrapper = document.getElementById('like-' + postId);
        if (wrapper) {
            wrapper.outerHTML = html;
            // Re-attach htmx if loaded, otherwise our onclick handles it
            if (window.htmx) htmx.process(document.getElementById('like-' + postId));
        }
    } catch (e) {
        console.error('Like failed:', e);
    }
}

/* ── COMMENTS (global, no HTMX needed) ── */
function toggleComments(postId) {
    const section = document.getElementById('comments-' + postId);
    const row     = document.getElementById('comment-row-' + postId);
    if (!section) return;
    const opening = section.style.display === 'none';
    if (opening && !section.dataset.loaded) {
        fetch('/feed/post/' + postId + '/comments')
            .then(r => r.text())
            .then(html => { section.innerHTML = html; section.dataset.loaded = '1'; })
            .catch(console.error);
    }
    section.style.display = opening ? 'block' : 'none';
    if (row) row.style.display = opening ? 'flex' : 'none';
}

async function submitComment(postId) {
    const input   = document.getElementById('comment-input-' + postId);
    if (!input) return;
    const content = input.value.trim();
    if (!content) return;

    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

    try {
        const res = await fetch('/feed/post/' + postId + '/comment', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                [csrfHeader]: csrfToken
            },
            body: 'content=' + encodeURIComponent(content)
        });
        if (!res.ok) return;
        const html = await res.text();
        const section = document.getElementById('comments-' + postId);
        section.style.display = 'block';
        section.insertAdjacentHTML('beforeend', html);
        input.value = '';
        const cnt = document.getElementById('ccount-' + postId);
        if (cnt) cnt.textContent = parseInt(cnt.textContent || '0') + 1;
    } catch (e) {
        console.error('Comment failed:', e);
    }
}

/* ── DELETE POST ── */
async function deletePost(postId, btn) {
    if (!confirm('Delete this post?')) return;
    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
    try {
        const res = await fetch('/feed/post/' + postId, {
            method: 'DELETE',
            headers: { [csrfHeader]: csrfToken }
        });
        if (res.ok) {
            const card = btn.closest('.post-card');
            if (card) card.remove();
        }
    } catch (e) { console.error('Delete failed:', e); }
}

/* ── IMAGE MODAL ── */
function openImageModal(src) {
    let modal = document.getElementById('imgModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'imgModal';
        modal.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.88);z-index:9999;display:flex;align-items:center;justify-content:center;cursor:zoom-out;';
        modal.innerHTML = '<img style="max-width:94vw;max-height:94vh;border-radius:8px;"/>';
        modal.onclick = () => modal.style.display = 'none';
        document.body.appendChild(modal);
    }
    modal.querySelector('img').src = src;
    modal.style.display = 'flex';
}
