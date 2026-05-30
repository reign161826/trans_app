package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class BasicPhrasesActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.basic_phrases)

        drawerLayout = findViewById(R.id.drawer_layout_basic_phrases)
        val btnMenu: ImageButton = findViewById(R.id.btn_menu_basic_phrases)
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val navView: NavigationView = findViewById(R.id.nav_view_basic_phrases)
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    finish()
                }
                R.id.nav_history -> {
                    val intent = Intent(this, HistoryActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                R.id.nav_basic_phrases -> {
                    // Already here
                }
                R.id.nav_about -> {
                    val intent = Intent(this, AboutActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                R.id.nav_how_to_use -> {
                    val intent = Intent(this, HowToUseActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}