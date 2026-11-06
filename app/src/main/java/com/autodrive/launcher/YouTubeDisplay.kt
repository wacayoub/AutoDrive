package com.autodrive.launcher

/** Small viewer-only adjustment to the mobile site's bottom navigation, not its player. */
internal object YouTubeDisplay {
    fun accepts(url: String?): Boolean = runCatching {
        val uri = java.net.URI(url ?: return false)
        uri.scheme == "https" && uri.host == "m.youtube.com" && uri.rawUserInfo == null && uri.port in setOf(-1, 443)
    }.getOrDefault(false)

    val compactNavigation = """
        (() => {
          if(window.top!==window || location.origin!=='https://m.youtube.com')return false;
          const url=new URL(location.href);if(url.username || url.password)return false;
          if(document.getElementById('autodrive-youtube-navigation'))return true;
          const style=document.createElement('style');style.id='autodrive-youtube-navigation';
          style.textContent=`
            ytm-pivot-bar-renderer { height:48px!important; }
            ytm-pivot-bar-renderer ytm-pivot-bar-item-renderer,
            ytm-pivot-bar-renderer .pivot-bar-item-tab { height:48px!important; min-height:48px!important; }
            ytm-pivot-bar-renderer .pivot-bar-item-tab c3-icon,
            ytm-pivot-bar-renderer .pivot-bar-item-tab img { width:18px!important; height:18px!important; }
            ytm-pivot-bar-renderer .pivot-bar-item-title,
            ytm-pivot-bar-renderer .pivot-bar-item-title * { font-size:10px!important; line-height:12px!important; }
          `;
          (document.head||document.documentElement).appendChild(style);return true;
        })();
    """.trimIndent()
}
