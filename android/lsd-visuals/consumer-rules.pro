# The web visuals' bridge (window.LSDHost): the WebView calls these by name.
-keepclassmembers class org.liquidlsd.visuals.HostBridge {
    @android.webkit.JavascriptInterface <methods>;
}
