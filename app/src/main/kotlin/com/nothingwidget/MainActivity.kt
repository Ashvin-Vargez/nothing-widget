package com.nothingwidget

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerStart: Spinner
    private lateinit var spinnerEnd: Spinner
    private lateinit var btnSave: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerStart = findViewById(R.id.spinnerStart)
        spinnerEnd   = findViewById(R.id.spinnerEnd)
        btnSave      = findViewById(R.id.btnSave)
        tvStatus     = findViewById(R.id.tvStatus)

        val hours = (0..23).map { h ->
            String.format("%02d:00", h)
        }.toTypedArray()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, hours)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerStart.adapter = adapter
        spinnerEnd.adapter   = ArrayAdapter(this, android.R.layout.simple_spinner_item, hours)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Load saved settings
        val s = WidgetSettings.load(this)
        spinnerStart.setSelection(s.startHour)
        spinnerEnd.setSelection(s.endHour)

        btnSave.setOnClickListener {
            val newStart = spinnerStart.selectedItemPosition
            val newEnd   = spinnerEnd.selectedItemPosition
            if (newEnd <= newStart) {
                tvStatus.text = "End time must be after start time."
                return@setOnClickListener
            }
            WidgetSettings.save(this, WidgetSettings(newStart, newEnd))
            tvStatus.text = "Saved! Day runs ${hours[newStart]} → ${hours[newEnd]}"

            // Restart service so it picks up new settings
            stopService(Intent(this, WidgetUpdateService::class.java))
            startForegroundService(Intent(this, WidgetUpdateService::class.java))
        }

        tvStatus.text = "Add the widget to your home screen, then configure here."
    }
}
