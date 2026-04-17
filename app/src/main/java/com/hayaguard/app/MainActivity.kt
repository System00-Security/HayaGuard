package com.hayaguard.app

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.hayaguard.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var btnUnblur: ImageButton
    private lateinit var btnGoTop: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnAbout: ImageButton
    private lateinit var btnHome: ImageButton
    private var contentFilterPipeline: ContentFilterPipeline? = null
    private var webViewClient: NSFWWebViewClient? = null

    private lateinit var fileUploadHandler: FileUploadHandler
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private val facebookUrl = "https://m.facebook.com/?sk=h_chr"
    
    private val timeLimitHandler = Handler(Looper.getMainLooper())
    private var timeLimitDialog: Dialog? = null
    private var bedtimeDialog: Dialog? = null
    private val timeLimitCheckInterval = 30000L
    private var cameFromSettings = false
    private var previousHideReelsSetting = false
    private var previousHayaModeSetting = false
    private var previousQuickLensSetting = false
    private var previousGenderSetting = ""
    private var previousFriendsOnlySetting = false

    private val timeLimitRunnable = object : Runnable {
        override fun run() {
            checkTimeLimitAndBedtime()
            timeLimitHandler.postDelayed(this, timeLimitCheckInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        DnsWarmer.warmUp()
        AdaptivePerformanceEngine.initialize(applicationContext)
        StatsTracker.initialize(applicationContext)
        SettingsManager.initialize(applicationContext)
        StatsTracker.startSession()
        FeedAnalyzer.initialize(applicationContext)
        
        checkBedtimeOnLaunch()
        
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webView = binding.webView
        progressBar = binding.progressBar
        btnUnblur = binding.btnUnblur
        btnGoTop = binding.btnGoTop
        btnSettings = binding.btnSettings
        btnAbout = binding.btnAbout
        btnHome = binding.btnHome

        setupFileUploadHandler()
        
        setupHeaderButtons()
        initializeDetector()
        setupWebView()
        setupUnblurButton()
        setupScrollListener()
        
        val urlToLoad = handleIncomingIntent(intent)
        webView.loadUrl(urlToLoad ?: facebookUrl)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let {
            val url = handleIncomingIntent(it)
            url?.let { webView.loadUrl(it) }
        }
    }
    
    private fun handleIncomingIntent(intent: Intent): String? {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) {
                    val url = uri.toString()
                    if (isFacebookUrl(url)) {
                        return convertToMobileUrl(url)
                    }
                }
            }
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (sharedText != null) {
                    val url = extractFacebookUrl(sharedText)
                    if (url != null) {
                        return convertToMobileUrl(url)
                    }
                }
            }
        }
        return null
    }
    
    private fun isFacebookUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("facebook.com") ||
                lowerUrl.contains("fb.com") ||
                lowerUrl.contains("fb.watch") ||
                lowerUrl.startsWith("fb://")
    }
    
    private fun extractFacebookUrl(text: String): String? {
        val patterns = listOf(
            "https?://[\\w.]*facebook\\.com[^\\s]*",
            "https?://[\\w.]*fb\\.com[^\\s]*",
            "https?://fb\\.watch[^\\s]*"
        )
        for (pattern in patterns) {
            val regex = Regex(pattern)
            val match = regex.find(text)
            if (match != null) {
                return match.value
            }
        }
        return null
    }
    
    private fun convertToMobileUrl(url: String): String {
        var result = url
        if (result.startsWith("fb://")) {
            val path = result.removePrefix("fb://")
            result = "https://m.facebook.com/$path"
        }
        result = result.replace("://www.facebook.com", "://m.facebook.com")
        result = result.replace("://facebook.com", "://m.facebook.com")
        return result
    }

    /**
     * Initialize the file upload handler with Activity Result launchers
     */
    private fun setupFileUploadHandler() {
        fileUploadHandler = FileUploadHandler(this)
        
        // Launcher for file chooser result
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            fileUploadHandler.onActivityResult(result.resultCode, result.data)
        }
        
        // Launcher for permission requests
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            fileUploadHandler.onPermissionsResult(allGranted, fileChooserLauncher)
        }
    }

    private fun setupHeaderButtons() {
        btnHome.setOnClickListener {
            webView.loadUrl(facebookUrl)
        }

        btnGoTop.setOnClickListener {
            webView.evaluateJavascript("window.scrollTo({top: 0, behavior: 'smooth'});", null)
        }

        btnSettings.setOnClickListener {
            cameFromSettings = true
            previousHideReelsSetting = SettingsManager.isHideReelsEnabled()
            previousHayaModeSetting = SettingsManager.isHayaModeEnabled()
            previousQuickLensSetting = SettingsManager.isQuickLensEnabled()
            previousGenderSetting = SettingsManager.getUserGender()
            previousFriendsOnlySetting = SettingsManager.isFriendsOnlyEnabled()
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        btnAbout.setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }
    }

    private fun setupUnblurButton() {
        btnUnblur.setOnClickListener {
            val urls = webViewClient?.getLowConfidenceUrls() ?: emptySet()
            if (urls.isEmpty()) {
                btnUnblur.visibility = View.GONE
                return@setOnClickListener
            }
            
            AlertDialog.Builder(this)
                .setTitle("Unblur Images")
                .setMessage("${urls.size} image(s) with low confidence detection. Unblur all?")
                .setPositiveButton("Unblur All") { _, _ ->
                    webViewClient?.unblurAll(webView)
                    btnUnblur.visibility = View.GONE
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateUnblurButton() {
        val count = webViewClient?.getLowConfidenceUrls()?.size ?: 0
        btnUnblur.visibility = if (count > 0) View.VISIBLE else View.GONE
    }

    private fun initializeDetector() {
        try {
            contentFilterPipeline = ContentFilterPipeline(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to init pipeline: ${e.message}", e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings = webView.settings
        
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        webSettings.databaseEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.allowFileAccess = false
        webSettings.allowContentAccess = true
        @Suppress("DEPRECATION")
        webSettings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        webSettings.allowUniversalAccessFromFileURLs = false
        webSettings.loadsImagesAutomatically = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.setSupportZoom(true)
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.userAgentString = getRealDeviceUserAgent()
        webSettings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        webSettings.setGeolocationEnabled(false)
        webSettings.setSupportMultipleWindows(false)
        webSettings.blockNetworkImage = false
        webSettings.javaScriptCanOpenWindowsAutomatically = false
        webSettings.safeBrowsingEnabled = true
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH)
        webSettings.setEnableSmoothTransition(true)
        
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.isScrollbarFadingEnabled = true
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_ALWAYS
        webView.isNestedScrollingEnabled = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        val feedScraperInterface = FeedScraperInterface { postText, hasFollow, hasJoin, isSponsored, posterName ->
            val result = FeedAnalyzer.analyzePost(postText, hasFollow, hasJoin, isSponsored, posterName)
            result.isSponsored
        }
        webView.addJavascriptInterface(feedScraperInterface, FeedScraperJS.INTERFACE_NAME)
        webView.addJavascriptInterface(QuickLensInterface { imageUrl ->
            runOnUiThread {
                if (SettingsManager.isQuickLensEnabled()) {
                    val intent = Intent(this, LensActivity::class.java)
                    intent.putExtra(LensActivity.EXTRA_IMAGE_URL, imageUrl)
                    startActivity(intent)
                }
            }
        }, "QuickLens")

        if (contentFilterPipeline != null) {
            webViewClient = NSFWWebViewClient(
                applicationContext, 
                contentFilterPipeline!!, 
                lifecycleScope
            )
            webViewClient!!.navigationListener = object : NSFWWebViewClient.NavigationListener {
                override fun onPageChanged(url: String?) {
                    updateHomeButtonVisibility(url)
                    if (isAuthPage(url)) return
                    webView.evaluateJavascript(FeedScraperJS.SCRAPER_SCRIPT, null)
                    if (SettingsManager.isQuickLensEnabled()) {
                        webView.evaluateJavascript(QuickLensJS.LONG_PRESS_SCRIPT, null)
                    }
                    if (SettingsManager.isHideReelsEnabled()) {
                        webView.evaluateJavascript(HideReelsJS.HIDE_REELS_SCRIPT, null)
                    }
                    if (SettingsManager.isFriendsOnlyEnabled()) {
                        webView.evaluateJavascript(FriendsOnlyJS.FRIENDS_ONLY_SCRIPT, null)
                    }
                }
            }
            webView.webViewClient = webViewClient!!
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                    updateUnblurButton()
                }
            }
            
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                updateHomeButtonVisibility(view?.url)
            }

            /**
             * Handle file upload requests from the WebView (Photo/Video, Add to Story, etc.)
             */
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback == null || fileChooserParams == null) {
                    return false
                }
                
                return fileUploadHandler.onShowFileChooser(
                    filePathCallback,
                    fileChooserParams,
                    fileChooserLauncher,
                    permissionLauncher
                )
            }
        }

        registerForContextMenu(webView)
    }

    override fun onCreateContextMenu(
        menu: android.view.ContextMenu?,
        v: View?,
        menuInfo: android.view.ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (!SettingsManager.isQuickLensEnabled()) return
        val hitTestResult = webView.hitTestResult
        when (hitTestResult.type) {
            WebView.HitTestResult.IMAGE_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val imageUrl = hitTestResult.extra
                if (!imageUrl.isNullOrEmpty()) {
                    menu?.setHeaderTitle("Quick Lens")
                    menu?.add(0, 1, 0, "Extract Text (OCR)")
                }
            }
        }
    }

    override fun onContextItemSelected(item: android.view.MenuItem): Boolean {
        if (!SettingsManager.isQuickLensEnabled()) return super.onContextItemSelected(item)
        if (item.itemId == 1) {
            val hitTestResult = webView.hitTestResult
            val imageUrl = hitTestResult.extra
            if (!imageUrl.isNullOrEmpty()) {
                val intent = android.content.Intent(this, LensActivity::class.java)
                intent.putExtra(LensActivity.EXTRA_IMAGE_URL, imageUrl)
                startActivity(intent)
                return true
            }
        }
        return super.onContextItemSelected(item)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun updateHomeButtonVisibility(url: String?) {
        val currentUrl = url ?: webView.url ?: ""
        val isHome = currentUrl.contains("?sk=h_chr") || currentUrl == facebookUrl || currentUrl == "https://m.facebook.com/" || currentUrl == "https://m.facebook.com"
        btnHome.visibility = if (isHome) View.GONE else View.VISIBLE
    }

    private fun isAuthPage(url: String?): Boolean {
        if (url == null) return false
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("login") ||
                lowerUrl.contains("checkpoint") ||
                lowerUrl.contains("/auth") ||
                lowerUrl.contains("two_step") ||
                lowerUrl.contains("password") ||
                lowerUrl.contains("verify") ||
                lowerUrl.contains("confirmation") ||
                lowerUrl.contains("code_entry") ||
                lowerUrl.contains("approvals") ||
                lowerUrl.contains("recover") ||
                lowerUrl.contains("identify")
    }

    private var lastPlaceholderCheckTime = 0L
    private val placeholderCheckInterval = 2000L

    private fun setupScrollListener() {
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > 500) {
                if (btnGoTop.visibility != View.VISIBLE) {
                    btnGoTop.visibility = View.VISIBLE
                }
            } else {
                if (btnGoTop.visibility != View.GONE) {
                    btnGoTop.visibility = View.GONE
                }
            }
            
            val now = System.currentTimeMillis()
            if (now - lastPlaceholderCheckTime > placeholderCheckInterval) {
                lastPlaceholderCheckTime = now
                checkAndReloadPlaceholders()
            }
        }
    }

    private fun checkAndReloadPlaceholders() {
        webView.evaluateJavascript("""
            (function() {
                var reloaded = 0;
                var imgs = document.querySelectorAll('img');
                var ts = Date.now();
                imgs.forEach(function(img) {
                    if (!img.src) return;
                    var rect = img.getBoundingClientRect();
                    if (rect.top < window.innerHeight * 2 && rect.bottom > -window.innerHeight) {
                        var w = img.naturalWidth;
                        var h = img.naturalHeight;
                        if ((w > 0 && h > 0 && w === h && w < 200) || img.src.indexOf('_processed=') === -1) {
                            if (img.dataset.clReloadAttempt) {
                                var lastAttempt = parseInt(img.dataset.clReloadAttempt);
                                if (ts - lastAttempt < 3000) return;
                            }
                            img.dataset.clReloadAttempt = ts;
                            var newSrc = img.src.replace(/[&?]_reload=\d+/g, '');
                            newSrc = newSrc + (newSrc.indexOf('?') > -1 ? '&' : '?') + '_reload=' + ts;
                            img.src = '';
                            img.src = newSrc;
                            reloaded++;
                        }
                    }
                });
                return reloaded;
            })();
        """.trimIndent(), null)
    }

    private fun getRealDeviceUserAgent(): String {
        return WebSettings.getDefaultUserAgent(this)
    }

    private fun getWebViewVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo("com.google.android.webview", 0)
            val versionName = packageInfo.versionName
            versionName?.split(".")?.take(3)?.joinToString(".") ?: "120.0.0"
        } catch (e: Exception) {
            try {
                val packageInfo = packageManager.getPackageInfo("com.android.chrome", 0)
                val versionName = packageInfo.versionName
                versionName?.split(".")?.take(3)?.joinToString(".") ?: "120.0.0"
            } catch (e: Exception) {
                "120.0.0"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        ContentFilterPipeline.resume()
        StatsTracker.resumeSession()
        timeLimitHandler.post(timeLimitRunnable)
        if (cameFromSettings) {
            cameFromSettings = false
            val currentHideReelsSetting = SettingsManager.isHideReelsEnabled()
            val currentHayaModeSetting = SettingsManager.isHayaModeEnabled()
            val currentQuickLensSetting = SettingsManager.isQuickLensEnabled()
            val currentGenderSetting = SettingsManager.getUserGender()
            val currentFriendsOnlySetting = SettingsManager.isFriendsOnlyEnabled()
            val settingsChanged = currentHideReelsSetting != previousHideReelsSetting ||
                    currentHayaModeSetting != previousHayaModeSetting ||
                    currentQuickLensSetting != previousQuickLensSetting ||
                    currentGenderSetting != previousGenderSetting ||
                    currentFriendsOnlySetting != previousFriendsOnlySetting
            if (settingsChanged) {
                previousHideReelsSetting = currentHideReelsSetting
                previousHayaModeSetting = currentHayaModeSetting
                previousQuickLensSetting = currentQuickLensSetting
                previousGenderSetting = currentGenderSetting
                previousFriendsOnlySetting = currentFriendsOnlySetting
                webView.reload()
                return
            }
        }
        if (SettingsManager.isHideReelsEnabled()) {
            webView.evaluateJavascript(HideReelsJS.HIDE_REELS_SCRIPT, null)
        }
        if (SettingsManager.isFriendsOnlyEnabled()) {
            webView.evaluateJavascript(FriendsOnlyJS.FRIENDS_ONLY_SCRIPT, null)
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        StatsTracker.pauseSession()
        timeLimitHandler.removeCallbacks(timeLimitRunnable)
        ContentFilterPipeline.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        timeLimitHandler.removeCallbacks(timeLimitRunnable)
        timeLimitDialog?.dismiss()
        bedtimeDialog?.dismiss()
        StatsTracker.endSession()
        FeedAnalyzer.saveSession()
        fileUploadHandler.cancelPendingCallback()
        webView.destroy()
        webViewClient?.close()
        contentFilterPipeline?.close()
        TFLiteInterpreterFactory.close()
        CronetHelper.shutdown()
        AdaptivePerformanceEngine.shutdown()
        DnsWarmer.shutdown()
        BitmapPool.clear()
    }

    private fun checkBedtimeOnLaunch() {
        if (SettingsManager.isBedtimeNow()) {
            showBedtimeWarningDialog()
        }
    }

    private fun checkTimeLimitAndBedtime() {
        val currentTimeSpent = StatsTracker.getTimeSpentMs()
        if (SettingsManager.isTimeLimitExceeded(currentTimeSpent)) {
            if (timeLimitDialog == null || !timeLimitDialog!!.isShowing) {
                showTimeLimitWarningDialog()
            }
        }
        if (SettingsManager.isBedtimeNow()) {
            if (bedtimeDialog == null || !bedtimeDialog!!.isShowing) {
                if (timeLimitDialog == null || !timeLimitDialog!!.isShowing) {
                    showBedtimeWarningDialog()
                }
            }
        }
    }

    private fun showTimeLimitWarningDialog() {
        timeLimitDialog?.dismiss()
        
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_time_warning)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnSnooze = dialog.findViewById<Button>(R.id.btnSnooze)
        val btnOverride = dialog.findViewById<Button>(R.id.btnOverride)
        val btnClose = dialog.findViewById<Button>(R.id.btnClose)

        btnSnooze.setOnClickListener {
            SettingsManager.snoozeFor5Minutes()
            dialog.dismiss()
            timeLimitDialog = null
        }

        btnOverride.setOnClickListener {
            dialog.dismiss()
            timeLimitDialog = null
            showSetNewLimitDialog()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
            timeLimitDialog = null
            finishAffinity()
        }

        timeLimitDialog = dialog
        dialog.show()
    }

    private fun showSetNewLimitDialog() {
        val options = arrayOf("30 minutes", "1 hour", "1.5 hours", "2 hours", "3 hours", "4 hours", "Unlimited")
        val values = intArrayOf(30, 60, 90, 120, 180, 240, -1)
        
        AlertDialog.Builder(this)
            .setTitle("Set New Time Limit")
            .setItems(options) { _, which ->
                val selectedValue = values[which]
                if (selectedValue == -1) {
                    SettingsManager.setUnlimitedOverride()
                } else {
                    SettingsManager.setTimeLimitMinutes(selectedValue)
                    StatsTracker.resetDailyTime()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBedtimeWarningDialog() {
        bedtimeDialog?.dismiss()
        
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_bedtime_warning)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnOverride = dialog.findViewById<Button>(R.id.btnBedtimeOverride)
        val btnClose = dialog.findViewById<Button>(R.id.btnBedtimeClose)

        btnOverride.setOnClickListener {
            SettingsManager.setBedtimeOverride()
            dialog.dismiss()
            bedtimeDialog = null
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
            bedtimeDialog = null
            finishAffinity()
        }

        bedtimeDialog = dialog
        dialog.show()
    }
}
