package com.hayaguard.app

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var cardStats: androidx.cardview.widget.CardView
    private lateinit var tvTimeLimitValue: TextView
    private lateinit var tvTimeLimitStatus: TextView
    private lateinit var etTimeValue: EditText
    private lateinit var spinnerTimeUnit: Spinner
    private lateinit var btnSaveTimeLimit: Button
    private lateinit var btnResetOverride: Button
    private lateinit var switchAutoClose: SwitchCompat
    private lateinit var layoutBedtimeTime: View
    private lateinit var tvBedtimeValue: TextView
    private lateinit var tvBedtimeStatus: TextView
    private lateinit var btnSetBedtime: Button
    private lateinit var btnResetBedtimeOverride: Button
    private lateinit var storagePieChart: StoragePieChartView
    private lateinit var tvCacheSize: TextView
    private lateinit var tvEssentialSize: TextView
    private lateinit var tvTotalStorage: TextView
    private lateinit var btnClearCache: Button
    private lateinit var switchHayaMode: SwitchCompat
    private lateinit var layoutGenderSelection: View
    private lateinit var radioGroupGender: RadioGroup
    private lateinit var radioMale: RadioButton
    private lateinit var radioFemale: RadioButton
    private lateinit var btnSaveGender: Button
    private lateinit var switchQuickLens: SwitchCompat
    private lateinit var switchHideReels: SwitchCompat
    private lateinit var switchFriendsOnly: SwitchCompat
    private lateinit var switchBatterySaver: SwitchCompat

    private val timeUnits = arrayOf("Minutes", "Hours")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        SettingsManager.initialize(applicationContext)

        initViews()
        setupSpinner()
        setupClickListeners()
        loadSettings()
        loadStorageInfo()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        cardStats = findViewById(R.id.cardStats)
        tvTimeLimitValue = findViewById(R.id.tvTimeLimitValue)
        tvTimeLimitStatus = findViewById(R.id.tvTimeLimitStatus)
        etTimeValue = findViewById(R.id.etTimeValue)
        spinnerTimeUnit = findViewById(R.id.spinnerTimeUnit)
        btnSaveTimeLimit = findViewById(R.id.btnSaveTimeLimit)
        btnResetOverride = findViewById(R.id.btnResetOverride)
        switchAutoClose = findViewById(R.id.switchAutoClose)
        layoutBedtimeTime = findViewById(R.id.layoutBedtimeTime)
        tvBedtimeValue = findViewById(R.id.tvBedtimeValue)
        tvBedtimeStatus = findViewById(R.id.tvBedtimeStatus)
        btnSetBedtime = findViewById(R.id.btnSetBedtime)
        btnResetBedtimeOverride = findViewById(R.id.btnResetBedtimeOverride)
        storagePieChart = findViewById(R.id.storagePieChart)
        tvCacheSize = findViewById(R.id.tvCacheSize)
        tvEssentialSize = findViewById(R.id.tvEssentialSize)
        tvTotalStorage = findViewById(R.id.tvTotalStorage)
        btnClearCache = findViewById(R.id.btnClearCache)
        switchHayaMode = findViewById(R.id.switchHayaMode)
        layoutGenderSelection = findViewById(R.id.layoutGenderSelection)
        radioGroupGender = findViewById(R.id.radioGroupGender)
        radioMale = findViewById(R.id.radioMale)
        radioFemale = findViewById(R.id.radioFemale)
        btnSaveGender = findViewById(R.id.btnSaveGender)
        switchQuickLens = findViewById(R.id.switchQuickLens)
        switchHideReels = findViewById(R.id.switchHideReels)
        switchFriendsOnly = findViewById(R.id.switchFriendsOnly)
        switchBatterySaver = findViewById(R.id.switchBatterySaver)
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, R.layout.spinner_item, timeUnits)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerTimeUnit.adapter = adapter
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }

        cardStats.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        btnSaveTimeLimit.setOnClickListener {
            saveTimeLimit()
        }

        btnResetOverride.setOnClickListener {
            showResetOverrideDialog()
        }

        switchAutoClose.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setAutoCloseEnabled(isChecked)
            layoutBedtimeTime.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateBedtimeStatus()
        }

        btnSetBedtime.setOnClickListener {
            showTimePickerDialog()
        }

        btnResetBedtimeOverride.setOnClickListener {
            showResetBedtimeOverrideDialog()
        }

        btnClearCache.setOnClickListener {
            showClearCacheDialog()
        }

        // Haya Mode listeners
        switchHayaMode.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setHayaModeEnabled(isChecked)
            layoutGenderSelection.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        btnSaveGender.setOnClickListener {
            val selectedGender = if (radioMale.isChecked) "MALE" else "FEMALE"
            SettingsManager.setUserGender(selectedGender)
            Toast.makeText(this, "Gender preference saved", Toast.LENGTH_SHORT).show()
        }

        switchQuickLens.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setQuickLensEnabled(isChecked)
        }

        switchHideReels.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setHideReelsEnabled(isChecked)
        }

        switchFriendsOnly.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setFriendsOnlyEnabled(isChecked)
        }

        switchBatterySaver.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setBatterySaverEnabled(isChecked)
        }
    }

    private fun loadSettings() {
        val limitMinutes = SettingsManager.getTimeLimitMinutes()
        updateTimeLimitDisplay(limitMinutes)

        if (limitMinutes >= 60 && limitMinutes % 60 == 0) {
            val hours = limitMinutes / 60
            etTimeValue.setText(hours.toString())
            spinnerTimeUnit.setSelection(1)
        } else {
            etTimeValue.setText(limitMinutes.toString())
            spinnerTimeUnit.setSelection(0)
        }

        updateOverrideStatus()

        switchAutoClose.isChecked = SettingsManager.isAutoCloseEnabled()
        layoutBedtimeTime.visibility = if (switchAutoClose.isChecked) View.VISIBLE else View.GONE
        tvBedtimeValue.text = SettingsManager.getFormattedAutoCloseTime()
        updateBedtimeStatus()

        // Load Haya Mode settings
        switchHayaMode.isChecked = SettingsManager.isHayaModeEnabled()
        layoutGenderSelection.visibility = if (switchHayaMode.isChecked) View.VISIBLE else View.GONE
        val savedGender = SettingsManager.getUserGender()
        if (savedGender == "MALE") {
            radioMale.isChecked = true
        } else {
            radioFemale.isChecked = true
        }

        switchQuickLens.isChecked = SettingsManager.isQuickLensEnabled()

        switchHideReels.isChecked = SettingsManager.isHideReelsEnabled()

        switchFriendsOnly.isChecked = SettingsManager.isFriendsOnlyEnabled()

        switchBatterySaver.isChecked = SettingsManager.isBatterySaverEnabled()
    }

    private fun updateTimeLimitDisplay(minutes: Int) {
        tvTimeLimitValue.text = when {
            minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60} hour${if (minutes / 60 > 1) "s" else ""}"
            minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
            else -> "$minutes minute${if (minutes > 1) "s" else ""}"
        }
    }

    private fun updateOverrideStatus() {
        if (SettingsManager.isUnlimitedOverrideActive()) {
            tvTimeLimitStatus.visibility = View.VISIBLE
            tvTimeLimitStatus.text = "Unlimited override active"
            tvTimeLimitStatus.setTextColor(0xFF34C759.toInt())
            btnResetOverride.visibility = View.VISIBLE
        } else {
            tvTimeLimitStatus.visibility = View.GONE
            btnResetOverride.visibility = View.GONE
        }
    }

    private fun updateBedtimeStatus() {
        if (SettingsManager.isBedtimeOverrideActive()) {
            tvBedtimeStatus.visibility = View.VISIBLE
            tvBedtimeStatus.text = "Override active until morning"
            btnResetBedtimeOverride.visibility = View.VISIBLE
        } else if (SettingsManager.isBedtimeNow()) {
            tvBedtimeStatus.visibility = View.VISIBLE
            tvBedtimeStatus.text = "Bedtime is now active"
            tvBedtimeStatus.setTextColor(0xFFFF9500.toInt())
            btnResetBedtimeOverride.visibility = View.GONE
        } else {
            tvBedtimeStatus.visibility = View.GONE
            btnResetBedtimeOverride.visibility = View.GONE
        }
    }

    private fun saveTimeLimit() {
        val valueStr = etTimeValue.text.toString()
        if (valueStr.isEmpty()) {
            Toast.makeText(this, "Please enter a value", Toast.LENGTH_SHORT).show()
            return
        }

        val value = valueStr.toIntOrNull()
        if (value == null || value <= 0) {
            Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
            return
        }

        val minutes = if (spinnerTimeUnit.selectedItemPosition == 1) {
            value * 60
        } else {
            value
        }

        if (minutes > 1440) {
            Toast.makeText(this, "Maximum limit is 24 hours", Toast.LENGTH_SHORT).show()
            return
        }

        SettingsManager.setTimeLimitMinutes(minutes)
        updateTimeLimitDisplay(minutes)
        Toast.makeText(this, "Time limit saved", Toast.LENGTH_SHORT).show()
    }

    private fun showResetOverrideDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Override")
            .setMessage("This will re-enable the time limit. Continue?")
            .setPositiveButton("Reset") { _, _ ->
                SettingsManager.resetOverride()
                updateOverrideStatus()
                Toast.makeText(this, "Override reset", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showResetBedtimeOverrideDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Bedtime Override")
            .setMessage("This will re-enable bedtime auto-close. Continue?")
            .setPositiveButton("Reset") { _, _ ->
                SettingsManager.resetBedtimeOverride()
                updateBedtimeStatus()
                Toast.makeText(this, "Bedtime override reset", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTimePickerDialog() {
        val currentHour = SettingsManager.getAutoCloseHour()
        val currentMinute = SettingsManager.getAutoCloseMinute()

        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                SettingsManager.setAutoCloseTime(hourOfDay, minute)
                tvBedtimeValue.text = SettingsManager.getFormattedAutoCloseTime()
                updateBedtimeStatus()
            },
            currentHour,
            currentMinute,
            false
        ).show()
    }

    private fun loadStorageInfo() {
        CoroutineScope(Dispatchers.IO).launch {
            val cacheInfo = CacheManager.getCacheInfo(applicationContext)
            val cachePercent = CacheManager.getCachePercentage(applicationContext)
            val essentialPercent = CacheManager.getEssentialPercentage(applicationContext)

            withContext(Dispatchers.Main) {
                storagePieChart.setData(cachePercent, essentialPercent)
                tvCacheSize.text = CacheManager.formatBytes(cacheInfo.cacheBytes)
                tvEssentialSize.text = CacheManager.formatBytes(cacheInfo.essentialBytes)
                tvTotalStorage.text = "Total: ${CacheManager.formatBytes(cacheInfo.totalBytes)}"
            }
        }
    }

    private fun showClearCacheDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Cache")
            .setMessage("This will clear cached data while preserving your login session and cookies. Continue?")
            .setPositiveButton("Clear") { _, _ ->
                clearCache()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearCache() {
        CoroutineScope(Dispatchers.IO).launch {
            val clearedBytes = CacheManager.clearCache(applicationContext)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@SettingsActivity,
                    "Cleared ${CacheManager.formatBytes(clearedBytes)}",
                    Toast.LENGTH_SHORT
                ).show()
                loadStorageInfo()
            }
        }
    }
}
