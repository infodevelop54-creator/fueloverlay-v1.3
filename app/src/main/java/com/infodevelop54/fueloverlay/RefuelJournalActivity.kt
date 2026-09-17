package com.infodevelop54.fueloverlay

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RefuelJournalActivity : AppCompatActivity() {

    private val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.menu_journal)
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val events = RefuelJournal.loadAll(this)
        val totalLiters = events.sumOf { it.liters.toDouble() }
        val totalMoney  = events.sumOf { it.totalPrice.toDouble() }

        root.addView(TextView(this).apply {
            text = "Записей: ${events.size}   Литров: %.1f   Сумма: %.2f"
                .format(totalLiters, totalMoney)
            textSize = 14f
        })

        if (events.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "Журнал пуст. Добавляйте заправки через виджет."
                setPadding(0, dp(16), 0, 0)
            })
        } else {
            events.forEach { root.addView(buildRow(it)) }
        }

        root.addView(Button(this).apply {
            text = "Очистить журнал"
            setOnClickListener {
                AlertDialog.Builder(this@RefuelJournalActivity)
                    .setTitle("Очистить журнал?")
                    .setMessage("Все записи о заправках будут удалены.")
                    .setPositiveButton(R.string.ok) { _, _ ->
                        RefuelJournal.saveAll(this@RefuelJournalActivity, emptyList())
                        RefuelJournal.recalculateFromJournal(this@RefuelJournalActivity)
                        render()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun buildRow(ev: RefuelEvent): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        row.addView(TextView(this).apply {
            text = df.format(Date(ev.timestamp)) + if (ev.fullTank) "  (100%)" else ""
            textSize = 13f
        })
        row.addView(TextView(this).apply {
            text = "Одометр: %.0f км   Литры: %.2f   Цена/л: %.2f   Итого: %.2f"
                .format(ev.odometerKm, ev.liters, ev.pricePerLiter, ev.totalPrice)
            textSize = 13f
        })
        row.addView(Button(this).apply {
            text = "Удалить"
            gravity = Gravity.END
            setOnClickListener {
                AlertDialog.Builder(this@RefuelJournalActivity)
                    .setMessage("Удалить запись?")
                    .setPositiveButton(R.string.ok) { _, _ ->
                        RefuelJournal.remove(this@RefuelJournalActivity, ev)
                        render()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        })
        return row
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}