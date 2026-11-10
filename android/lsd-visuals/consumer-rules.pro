# The web visuals' bridge (window.LSDHost): the WebView calls these by name.
-keepclassmembers class lsd.liquid.visuals.HostBridge, lsd.liquid.visuals.StateBridge {
    @android.webkit.JavascriptInterface <methods>;
}
