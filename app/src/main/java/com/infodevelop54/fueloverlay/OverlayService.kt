package com.infodevelop54.fueloverlay

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.Locale

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var fuelRepo: FuelStateRepository

    private lateinit var frontSide: View
    private lateinit var backSide: View
    private lateinit var textSpent: TextView
    private lateinit var textRemaining: TextView
    private lateinit var textMileage: TextView
    private lateinit var textRange: TextView

    private var gpsTracker: GpsTracker? = null
    private var iconPickerOpen = false

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateTexts()
            FuelStateRepository.maybeFlush()
            handler.postDelayed(this, 1000L)
        }
    }

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyAppearance() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build())

        fuelRepo = FuelStateRepository(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = fuelRepo.overlayX
            y = fuelRepo.overlayY
        }

        windowManager.addView(floatingView, layoutParams)

        bindViews()
        setupButtons()
        setupIconLongPresses()
        setupTouchListener()

        applyAppearance()
        AppearanceRepository.getPrefs(this)
            .registerOnSharedPreferenceChangeListener(prefsListener)

        if (fuelRepo.gpsTrackingEnabled) startGps()
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        gpsTracker?.stop(); gpsTracker = null
        FuelStateRepository.flush()
        AppearanceRepository.getPrefs(this)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
            windowManager.removeView(floatingView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (gpsTracker != null) return
        gpsTracker = GpsTracker(this) { fuelRepo.addGpsDistanceMeters(it) }.also { it.start() }
    }

    private fun bindViews() {
        frontSide     = floatingView.findViewById(R.id.frontSide)
        backSide      = floatingView.findViewById(R.id.backSide)
        textSpent     = floatingView.findViewById(R.id.textSpent)
        textRemaining = floatingView.findViewById(R.id.textRemaining)
        textMileage   = floatingView.findViewById(R.id.textMileage)
        textRange     = floatingView.findViewById(R.id.textRange)
    }

    private fun setupButtons() {
        floatingView.findViewById<ImageButton>(R.id.btnFlipToBack).setOnClickListener {
            backSide.visibility = View.VISIBLE; frontSide.visibility = View.GONE
        }
        floatingView.findViewById<ImageButton>(R.id.btnFlipToFront).setOnClickListener {
            frontSide.visibility = View.VISIBLE; backSide.visibility = View.GONE
        }
        floatingView.findViewById<View>(R.id.btnRefuelFull).setOnClickListener {
            showRefuelFullDialog()
        }
        floatingView.findViewById<View>(R.id.btnRefuelPlus).setOnClickListener {
            showRefuelPlusDialog()
        }
        floatingView.findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        floatingView.post {
            val w = frontSide.width
            if (w > 0) {
                backSide.layoutParams = backSide.layoutParams.apply { width = w }
                backSide.requestLayout()
            }
        }
    }

    private fun setupIconLongPresses() {
        attachIconLongPress(floatingView.findViewById(R.id.icSpent), "spent")
        attachIconLongPress(floatingView.findViewById(R.id.icRemaining), "remaining")
        attachIconLongPress(floatingView.findViewById(R.id.icMileage), "mileage")
        attachIconLongPress(floatingView.findViewById(R.id.icRange), "range")
    }

    private fun attachIconLongPress(view: View?, category: String) {
        view ?: return
        val localHandler = Handler(Looper.getMainLooper())
        var pending: Runnable? = null

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (iconPickerOpen) return@setOnTouchListener true
                    pending = Runnable { pending = null; showIconPicker(category) }
                    localHandler.postDelayed(pending!!, 3000L)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pending?.let { localHandler.removeCallbacks(it) }
                    pending = null
                    true
                }
                else -> false
            }
        }
    }

    private fun showIconPicker(category: String) {
        if (iconPickerOpen) return
        iconPickerOpen = true

        val appearance = AppearanceRepository.load(this)
        val icons = when (category) {
            "spent"     -> VectorIconLibrary.SPENT
            "remaining" -> VectorIconLibrary.REMAINING
            "mileage"   -> VectorIconLibrary.MILEAGE
            "range"     -> VectorIconLibrary.RANGE
            else -> { iconPickerOpen = false; return }
        }
        val current = when (category) {
            "spent"     -> appearance.spentIconIndex
            "remaining" -> appearance.remainingIconIndex
            "mileage"   -> appearance.mileageIconIndex
            else        -> appearance.rangeIconIndex
        }

        val density = resources.displayMetrics.density
        val tc = try { Color.parseColor(appearance.textColor) } catch (_: Exception) { Color.WHITE }
        val selectedBg = 0x66FFFFFF
        val normalBg   = 0x00000000

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * density).toInt(), (16 * density).toInt(),
                       (16 * density).toInt(), (16 * density).toInt())
        }

        var chosen = current
        val thumbs = mutableListOf<ImageView>()

        icons.forEachIndexed { index, icon ->
            val size = (56 * density).toInt()
            val pad = (8 * density).toInt()
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (8 * density).toInt()
                }
                setPadding(pad, pad, pad, pad)
                setImageDrawable(VectorIconLibrary.createDrawable(icon.pathData, tc))
                setBackgroundColor(if (index == current) selectedBg else normalBg)
                setOnClickListener {
                    chosen = index
                    thumbs.forEachIndexed { i, v ->
                        v.setBackgroundColor(if (i == index) selectedBg else normalBg)
                    }
                }
            }
            thumbs.add(iv)
            row.addView(iv)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.ic_picker_title)
            .setView(row)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newAppearance = when (category) {
                    "spent"     -> appearance.copy(spentIconIndex = chosen)
                    "remaining" -> appearance.copy(remainingIconIndex = chosen)
                    "mileage"   -> appearance.copy(mileageIconIndex = chosen)
                    else        -> appearance.copy(rangeIconIndex = chosen)
                }
                AppearanceRepository.save(this, newAppearance)
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { iconPickerOpen = false }
            .create()
            .also { it.window?.setType(overlayWindowType()) }
            .show()
    }

    private fun updateTexts() {
        val current   = fuelRepo.currentOdometerKm
        val refuelOdo = fuelRepo.refuelOdometerKm
        val tank      = fuelRepo.tankCapacityLiters
        val cons      = fuelRepo.averageConsumptionL100
        val extra     = fuelRepo.extraFuelAddedL

        val mileage   = (current - refuelOdo).coerceAtLeast(0f)
        val spent     = mileage * cons / 100f
        val remaining = (tank - spent + extra).coerceIn(0f, tank)
        val rangeKm   = if (cons > 0f) remaining / cons * 100f else 0f

        textSpent.text     = String.format(Locale.US, "%.1f л", spent)
        textRemaining.text = String.format(Locale.US, "%.1f л", remaining)
        textMileage.text   = String.format(Locale.US, "%.1f км", mileage)
        textRange.text     = String.format(Locale.US, "%.1f км", rangeKm)

        val a = AppearanceRepository.load(this)
        AppearanceApplier.applyScale(
            floatingView, a, remaining, tank, resources.displayMetrics.density
        )
    }

    private fun showRefuelPlusDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_refuel_plus, null)
        val etLiters = view.findViewById<EditText>(R.id.etLiters)
        val etPrice  = view.findViewById<EditText>(R.id.etPrice)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_refuel_plus_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val liters = etLiters.text.toString().replace(',', '.').toFloatOrNull() ?: 0f
                val price  = etPrice.text.toString().replace(',', '.').toFloatOrNull() ?: 0f
                if (liters > 0f) {
                    RefuelJournal.add(this, RefuelEvent(
                        timestamp = System.currentTimeMillis(),
                        odometerKm = fuelRepo.currentOdometerKm,
                        liters = liters, pricePerLiter = price, fullTank = false
                    ))
                    fuelRepo.extraFuelAddedL = fuelRepo.extraFuelAddedL + liters
                    updateTexts()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .also { it.window?.setType(overlayWindowType()) }
            .show()
    }

    private fun showRefuelFullDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_refuel_full, null)
        val etLiters = view.findViewById<EditText>(R.id.etLiters)
        val etPrice  = view.findViewById<EditText>(R.id.etPrice)
        val etOdo    = view.findViewById<EditText>(R.id.etOdometer)

        etLiters.setText(String.format(Locale.US, "%.1f", fuelRepo.tankCapacityLiters))
        etOdo.setText(String.format(Locale.US, "%.1f", fuelRepo.currentOdometerKm))

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_refuel_full_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val liters  = etLiters.text.toString().replace(',', '.').toFloatOrNull() ?: 0f
                val price   = etPrice.text.toString().replace(',', '.').toFloatOrNull() ?: 0f
                val realOdo = etOdo.text.toString().replace(',', '.').toFloatOrNull()
                    ?: fuelRepo.currentOdometerKm
                handleRefuelFull(liters, price, realOdo)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .also { it.window?.setType(overlayWindowType()) }
            .show()
    }

    private fun handleRefuelFull(liters: Float, price: Float, realOdometerKm: Float) {
        val now = System.currentTimeMillis()
        val prevFull = RefuelJournal.previousFull(this, now)
        fuelRepo.syncOdometer(realOdometerKm)

        RefuelJournal.add(this, RefuelEvent(
            timestamp = now, odometerKm = realOdometerKm,
            liters = liters, pricePerLiter = price, fullTank = true
        ))

        if (prevFull != null) {
            val distance = (realOdometerKm - prevFull.odometerKm).coerceAtLeast(0f)
            val partial = RefuelJournal.litersBetween(this, prevFull.timestamp, now)
            val totalLiters = partial + liters
            if (distance > 20f && totalLiters > 5f) {
                AdaptiveConsumption.addCycle(this, ConsumptionCycle(
                    startTimestamp = prevFull.timestamp, endTimestamp = now,
                    startOdometerKm = prevFull.odometerKm, endOdometerKm = realOdometerKm,
                    distanceKm = distance, totalLiters = totalLiters,
                    avgConsumptionL100 = totalLiters / distance * 100f
                ))
            }
        }

        fuelRepo.refuelOdometerKm = realOdometerKm
        fuelRepo.extraFuelAddedL = 0f
        updateTexts()
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun applyAppearance() {
        val a = AppearanceRepository.load(this)
        AppearanceApplier.applyStatic(floatingView, a, resources.displayMetrics.density)
    }

    private fun setupTouchListener() {
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x; initialY = layoutParams.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    fuelRepo.overlayX = layoutParams.x
                    fuelRepo.overlayY = layoutParams.y
                    FuelStateRepository.flush()
                    true
                }
                else -> false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Fuel Overlay", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        }
    }

    companion object { private const val CHANNEL_ID = "fuel_overlay_channel" }
}