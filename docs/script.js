/* nullLauncher Mobile — сайт: тема, меню, живые цифры GitHub */
(function () {
  'use strict';

  /* ---------- тема ---------- */
  var themeBtn = document.getElementById('themeBtn');
  if (themeBtn) {
    themeBtn.addEventListener('click', function () {
      var cur = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', cur);
      try { localStorage.setItem('nlm-site-theme', cur); } catch (e) {}
    });
  }

  /* ---------- бургер ---------- */
  var burger = document.getElementById('burger');
  var pillbar = document.getElementById('navPills');
  if (burger && pillbar) {
    burger.addEventListener('click', function () { pillbar.classList.toggle('open'); });
    pillbar.addEventListener('click', function (e) {
      if (e.target.closest('a')) pillbar.classList.remove('open');
    });
  }

  /* ---------- активный пункт шапки при скролле ---------- */
  var pills = [].slice.call(document.querySelectorAll('.h-pill[href^="#"]'));
  var sections = pills
    .map(function (p) { return document.querySelector(p.getAttribute('href')); })
    .filter(Boolean);
  function syncPill() {
    var y = window.scrollY + 120;
    var act = sections[0];
    sections.forEach(function (s) { if (s.offsetTop <= y) act = s; });
    pills.forEach(function (p) {
      var on = act && p.getAttribute('href') === '#' + act.id;
      if (on) p.setAttribute('aria-current', 'page');
      else p.removeAttribute('aria-current');
    });
  }
  window.addEventListener('scroll', syncPill, { passive: true });
  syncPill();

  /* ---------- живые цифры: релизы GitHub (реальные, без выдумок) ---------- */
  function fmt(n) { return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, '\u2009'); }

  fetch('https://api.github.com/repos/rusty-prdc/NullLauncherMobile')
    .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
    .then(function (d) {
      var el = document.getElementById('statStars');
      if (el && typeof d.stargazers_count === 'number') el.textContent = fmt(d.stargazers_count);
    })
    .catch(function () {});

  fetch('https://api.github.com/repos/rusty-prdc/NullLauncherMobile/releases')
    .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
    .then(function (d) {
      if (Array.isArray(d)) {
        var el = document.getElementById('statReleases');
        if (el) el.textContent = String(d.length);
      }
    })
    .catch(function () {});

  /* ---------- появление блоков при скролле ---------- */
  var reveals = [].slice.call(document.querySelectorAll('.reveal'));
  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) { en.target.classList.add('in'); io.unobserve(en.target); }
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: .05 });
    reveals.forEach(function (el) { io.observe(el); });
  } else {
    reveals.forEach(function (el) { el.classList.add('in'); });
  }
})();
