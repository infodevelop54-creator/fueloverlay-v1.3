package com.infodevelop54.fueloverlay

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private var appearance = WidgetAppearance()
    private lateinit var previewRoot: LinearLayout
    private lateinit var fuelRepo: FuelStateRepository

    private val palette = listOf(
        "#FFFFFF", "#000000", "#CC000000", "#EE1A1A1A", "#EEFFFFFF",
        "#00E5FF", "#39FF14", "#FF1744", "#FFD600",
        "#2962FF", "#AA00FF", "#FF6D00", "#FFDB4D"
    )

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use {
                it.write(FuelBackup.export(this).toByteArray())
            }
            toast("БД экспортирована")
        } catch (e: Exception) { toast("Ошибка экспорта: ${e.message}") }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: return@registerForActivityResult
            FuelBackup.import(this, text)
            toast("БД импортирована")
            recreate()
        } catch (e: Exception) { toast("Ошибка импорта: ${e.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fuelRepo = FuelStateRepository(this)
        appearance = AppearanceRepository.load(this)

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#1A1A1A")) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        scroll.addView(content)

        content.addView(sectionTitle("Данные поездки"))
        val etOdo = addLabeledInput(content, "Текущий одометр, км",
            fuelRepo.currentOdometerKm.toString())
        val etTank = addLabeledInput(content, "Объём бака, л",
            fuelRepo.tankCapacityLiters.toString())
        val etCons = addLabeledInput(content, "Ручной средний расход, л/100 км",
            fuelRepo.manualAverageConsumptionL100.toString())
        val etRefuelOdo = addLabeledInput(content, "Одометр на последней заправке, км",
            fuelRepo.refuelOdometerKm.toString())
        content.addView(Button(this).apply {
            text = "Сохранить данные"
            setOnClickListener {
                parseFloat(etOdo)?.let { fuelRepo.syncOdometer(it) }
                parseFloat(etTank)?.let { fuelRepo.tankCapacityLiters = it }
                parseFloat(etCons)?.let {
                    fuelRepo.manualAverageConsumptionL100 = it
                    if (AdaptiveConsumption.loadCycles(this@SettingsActivity).isEmpty()) {
                        fuelRepo.averageConsumptionL100 = it
                    }
                }
                parseFloat(etRefuelOdo)?.let { fuelRepo.refuelOdometerKm = it }
                toast("Сохранено")
            }
        })

        content.addView(sectionTitle("Разделы"))
        content.addView(Button(this).apply {
            text = getString(R.string.menu_journal)
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, RefuelJournalActivity::class.java))
            }
        })
        content.addView(Button(this).apply {
            text = getString(R.string.menu_adaptive)
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, AdaptiveConsumptionActivity::class.java))
            }
        })

        content.addView(sectionTitle("Импорт\\Экспорт"))
        content.addView(TextView(this).apply {
            text = "Путь: ${FuelDatabase.baseDir(this@SettingsActivity).absolutePath}"
            textSize = 12f
            setTextColor(Color.WHITE)
        })
        content.addView(Button(this).apply {
            text = getString(R.string.menu_export)
            setOnClickListener { exportLauncher.launch("fuel_overlay_backup.json") }
        })
        content.addView(Button(this).apply {
            text = getString(R.string.menu_import)
            setOnClickListener { importLauncher.launch(arrayOf("application/json", "*/*")) }
        })

        content.addView(sectionTitle("Предпросмотр"))
        val previewWrap = FrameLayout(this).apply { setPadding(0, dp(16), 0, dp(16)) }
        previewRoot = LayoutInflater.from(this)
            .inflate(R.layout.overlay_layout, previewWrap, false) as LinearLayout
        previewWrap.addView(previewRoot)
        content.addView(previewWrap)

        content.addView(sectionTitle("Пресеты"))
        content.addView(makePresetRow())
        content.addView(sectionTitle("Цвет фона"))
        content.addView(makeColorRow { update(appearance.copy(backgroundColor = it)) })
        content.addView(sectionTitle("Цвет шкалы"))
        content.addView(makeColorRow { update(appearance.copy(scaleColor = it)) })
        content.addView(sectionTitle("Цвет делений"))
        content.addView(makeColorRow { update(appearance.copy(dividerColor = it)) })
        content.addView(sectionTitle("Цвет текста и иконок"))
        content.addView(makeColorRow { update(appearance.copy(textColor = it)) })

        content.addView(sectionTitle("Размер текста, sp"))
        content.addView(makeSlider(appearance.textSizeSp, 8f, 32f, 1f) {
            update(appearance.copy(textSizeSp = it))
        })
        content.addView(sectionTitle("Ширина шкалы, dp"))
        content.addView(makeSlider(appearance.scaleWidthDp.toFloat(), 100f, 500f, 10f) {
            update(appearance.copy(scaleWidthDp = it.toInt()))
        })
        content.addView(sectionTitle("Толщина шкалы, dp"))
        content.addView(makeSlider(appearance.scaleHeightDp.toFloat(), 2f, 30f, 1f) {
            update(appearance.copy(scaleHeightDp = it.toInt()))
        })
        content.addView(sectionTitle("Толщина делений, dp"))
        content.addView(makeSlider(appearance.dividerThicknessDp.toFloat(), 1f, 10f, 1f) {
            update(appearance.copy(dividerThicknessDp = it.toInt()))
        })
        content.addView(sectionTitle("Радиус углов, dp"))
        content.addView(makeSlider(appearance.cornerRadiusDp.toFloat(), 0f, 40f, 1f) {
            update(appearance.copy(cornerRadiusDp = it.toInt()))
        })
        content.addView(sectionTitle("Отступы, dp"))
        content.addView(makeSlider(appearance.paddingDp.toFloat(), 0f, 40f, 1f) {
            update(appearance.copy(paddingDp = it.toInt()))
        })

        content.addView(Button(this).apply {
            text = "Сбросить к стандартному виду"
            setOnClickListener { update(WidgetAppearance()); recreate() }
        })

        setContentView(scroll)
        refreshPreview()
    }

    private fun update(newAppearance: WidgetAppearance) {
        appearance = newAppearance
        AppearanceRepository.save(this, newAppearance)
        refreshPreview()
    }

    private fun refreshPreview() {
        AppearanceApplier.applyStatic(previewRoot, appearance, resources.displayMetrics.density)
        AppearanceApplier.applyScale(
            previewRoot, appearance,
            remainingLiters = fuelRepo.tankCapacityLiters,
            tankLiters = fuelRepo.tankCapacityLiters,
            density = resources.displayMetrics.density
        )
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.WHITE)
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun addLabeledInput(parent: LinearLayout, label: String, initial: String): EditText {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(4))
        })
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(initial)
            setTextColor(Color.WHITE)
        }
        parent.addView(et)
        return et
    }

    private fun parseFloat(et: EditText): Float? =
        et.text.toString().replace(',', '.').trim().toFloatOrNull()

    private fun makeColorRow(onPick: (String) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START
        }
        palette.forEach { hex ->
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(6) }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(hex))
                    cornerRadius = dp(4).toFloat()
                    setStroke(dp(2), Color.parseColor("#888888"))
                }
                setOnClickListener { onPick(hex) }
            })
        }
        return row
    }

    private fun makeSlider(
        initial: Float, min: Float, max: Float, step: Float,
        onChanged: (Float) -> Unit
    ): SeekBar {
        val steps = ((max - min) / step).toInt().coerceAtLeast(1)
        return SeekBar(this).apply {
            this.max = steps
            progress = ((initial - min) / step).toInt().coerceIn(0, steps)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) onChanged(min + p * step)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    private fun makePresetRow(): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        WidgetAppearance.PRESETS.forEach { (name, preset) ->
            row.addView(Button(this).apply {
                text = name; textSize = 12f
                setOnClickListener { update(preset); recreate() }
            })
        }
        return row
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}