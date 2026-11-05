package com.autodrive.launcher

/** Apply the owner's explicit preference by using Maps' existing Stay on web action. */
internal object MapWebPreference {
    val script = """
        (() => {
          if (window.top !== window || location.protocol !== 'https:') return false;
          const onMaps = location.hostname === 'maps.google.com' ||
            ((location.hostname === 'www.google.com' || location.hostname === 'google.com') &&
             (location.pathname === '/maps' || location.pathname.startsWith('/maps/')));
          if (!onMaps) return false;
          if (window.__autoDriveWebChoiceApplied) return true;
          if (window.__autoDriveWebChoiceTimer) clearInterval(window.__autoDriveWebChoiceTimer);
          const labels = new Set(['stay on web', 'stay on the web',
            'rester sur le web', 'rester sur le site web']);
          const choose = () => {
            const button = Array.from(document.querySelectorAll('button,a,[role="button"]')).find(el =>
              labels.has((el.textContent || '').replace(/\s+/g, ' ').trim().toLowerCase()) &&
              !el.disabled && el.getAttribute('aria-hidden') !== 'true' && el.getClientRects().length > 0);
            if (!button) return false;
            button.click();
            window.__autoDriveWebChoiceApplied = true;
            return true;
          };
          if (choose()) return true;
          let remaining = 60;
          window.__autoDriveWebChoiceTimer = setInterval(() => {
            if (choose() || --remaining <= 0) {
              clearInterval(window.__autoDriveWebChoiceTimer);
              window.__autoDriveWebChoiceTimer = null;
            }
          }, 250);
          return false;
        })();
    """.trimIndent()
}
