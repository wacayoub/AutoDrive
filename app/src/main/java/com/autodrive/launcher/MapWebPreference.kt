package com.autodrive.launcher

/** Owner-requested browser preferences, limited to Maps pages and known launch-prompt labels. */
internal object MapWebPreference {
    val script = """
        (() => {
          if (window.top !== window || location.protocol !== 'https:') return false;
          const onMaps = location.hostname === 'maps.google.com' ||
            ((location.hostname === 'www.google.com' || location.hostname === 'google.com') &&
             (location.pathname === '/maps' || location.pathname.startsWith('/maps/')));
          if (!onMaps) return false;
          if (window.__autoDriveWebChoiceTimer) clearInterval(window.__autoDriveWebChoiceTimer);
          const labels = new Set(['stay on web', 'stay on the web',
            'rester sur le web', 'rester sur le site web']);
          const appLabels = new Set(['open app', 'open the app', "ouvrir l'appli", "ouvrir l'application"]);
          const normalize = text => (text || '').replace(/[’‘]/g, "'").replace(/\s+/g, ' ').trim().toLowerCase();
          const controls = () => Array.from(document.querySelectorAll('button,a,[role="button"]'));
          const hideLaunchPrompt = () => controls().forEach(el => {
            if (appLabels.has(normalize(el.textContent)) || appLabels.has(normalize(el.getAttribute('aria-label')))) {
              el.style.setProperty('display', 'none', 'important');
              el.hidden = true;
            }
          });
          const choose = () => {
            hideLaunchPrompt();
            if (window.__autoDriveWebChoiceApplied) return true;
            const button = controls().find(el =>
              labels.has(normalize(el.textContent)) &&
              !el.disabled && el.getAttribute('aria-hidden') !== 'true' && el.getClientRects().length > 0);
            if (!button) return false;
            button.click();
            window.__autoDriveWebChoiceApplied = true;
            return true;
          };
          choose();
          let remaining = 60;
          window.__autoDriveWebChoiceTimer = setInterval(() => {
            choose();
            if (--remaining <= 0) {
              clearInterval(window.__autoDriveWebChoiceTimer);
              window.__autoDriveWebChoiceTimer = null;
            }
          }, 250);
          return false;
        })();
    """.trimIndent()
}
