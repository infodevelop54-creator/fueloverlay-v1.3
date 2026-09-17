package com.infodevelop54.fueloverlay

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdaptiveConsumptionActivity : AppCompatActivity() {

    private val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private lateinit var fuelRepo: FuelStateRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.menu_adaptive)
        fuelRepo = FuelStateRepository(this)
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val cycles = AdaptiveConsumption.loadCycles(this)
        val adaptive = fuelRepo.averageConsumptionL100
        val manual = fuelRepo.manualAverageConsumptionL100

        root.addView(TextView(this).apply {
            text = "Текущий рабочий расход (используется виджетом):"
            textSize = 13f
        })
        root.addView(TextView(this).apply {
            text = "%.2f л / 100 км".format(adaptive)
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "Ручной (fallback): %.2f л / 100 км".format(manual)
            textSize = 13f
        })
        root.addView(TextView(this).apply {
            text = "Циклов в истории: ${cycles.size}"
            textSize = 13f
            setPadding(0, dp(8), 0, dp(8))
        })

        root.addView(Button(this).apply {
            text = "Задать ручной расход"
            setOnClickListener {
                val et = EditText(this@AdaptiveConsumptionActivity).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or
                                InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setText(manual.toString())
                }
                AlertDialog.Builder(this@AdaptiveConsumptionActivity)
                    .setTitle("Ручной средний расход, л/100 км")
                    .setView(et)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        et.text.toString().replace(',', '.').toFloatOrNull()?.let {
                            fuelRepo.manualAverageConsumptionL100 = it
                            if (cycles.isEmpty()) fuelRepo.averageConsumptionL100 = it
                            render()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        })

        root.addView(Button(this).apply {
            text = "Пересчитать адаптивный"
            setOnClickListener {
                AdaptiveConsumption.recompute(this@AdaptiveConsumptionActivity)
                render()
            }
        })

        root.addView(Button(this).apply {
            text = "Очистить историю циклов"
            setOnClickListener {
                AlertDialog.Builder(this@AdaptiveConsumptionActivity)
                    .setMessage("Удалить все циклы «полный→полный»?")
                    .setPositiveButton(R.string.ok) { _, _ ->
                        AdaptiveConsumption.clear(this@AdaptiveConsumptionActivity)
                        render()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        })

        root.addView(TextView(this).apply {
            text = "История циклов"
            textSize = 16f
            setPadding(0, dp(24), 0, dp(8))
        })

        if (cycles.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "Пока нет ни одного завершённого цикла. " +
                        "Сделайте две заправки «до полного»."
            })
        } else {
            cycles.forEach { c ->
                root.addView(TextView(this).apply {
                    text = "%s → %s\n%.0f км, %.1f л, %.2f л/100 км".format(
                        df.format(Date(c.startTimestamp)),
                        df.format(Date(c.endTimestamp)),
                        c.distanceKm, c.totalLiters, c.avgConsumptionL100
                    )
                    textSize = 13f
                    setPadding(0, dp(8), 0, dp(8))
                })
            }
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}