package com.example.myapplication

import android.os.Bundle
import android.widget.ImageView
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val spinnerSource: Spinner = findViewById(R.id.spinnerSource)
        val spinnerTarget: Spinner = findViewById(R.id.spinnerTarget)
        val btnSwitch: ImageView = findViewById(R.id.btnSwitch)

        btnSwitch.setOnClickListener {
            // Get current positions
            val sourcePos = spinnerSource.selectedItemPosition
            val targetPos = spinnerTarget.selectedItemPosition

            // Swap positions
            spinnerSource.setSelection(targetPos)
            spinnerTarget.setSelection(sourcePos)
        }
    }
}