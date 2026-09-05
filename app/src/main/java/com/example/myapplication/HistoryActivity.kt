package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import org.json.JSONArray

class HistoryActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HistoryAdapter
    private lateinit var emptyText: TextView
    private lateinit var btnDeleteAll: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        drawerLayout = findViewById(R.id.drawer_layout_history)
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            
            val header = findViewById<View>(R.id.header_layout)
            header.setPadding(0, systemBars.top, 0, 0)
            
            insets
        }

        val btnMenu: ImageButton = findViewById(R.id.btn_menu_history)
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        recyclerView = findViewById(R.id.recycler_view_history)
        emptyText = findViewById(R.id.text_empty_history)
        btnDeleteAll = findViewById(R.id.btn_delete_all)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter(emptyList()) { item ->
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("sourceText", item.sourceText)
                putExtra("targetText", item.targetText)
                putExtra("sourceLang", item.sourceLang)
                putExtra("targetLang", item.targetLang)
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
            finish()
        }
        recyclerView.adapter = adapter

        btnDeleteAll.setOnClickListener {
            clearHistory()
        }

        loadHistory()

        val navView: NavigationView = findViewById(R.id.nav_view_history)
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    finish()
                }
                R.id.nav_history -> {
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
                R.id.nav_basic_phrases -> {
                    val intent = Intent(this@HistoryActivity, BasicPhrasesActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                R.id.nav_update -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("checkUpdate", true)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences("translation_history", MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", "[]")
        val historyArray = JSONArray(historyJson)
        val historyList = mutableListOf<HistoryItem>()

        for (i in 0 until historyArray.length()) {
            val obj = historyArray.optJSONObject(i) ?: continue
            historyList.add(
                HistoryItem(
                    obj.optString("sourceText", ""),
                    obj.optString("targetText", ""),
                    obj.optString("sourceLang", "English"),
                    obj.optString("targetLang", "Filipino"),
                    obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }

        if (historyList.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            btnDeleteAll.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            btnDeleteAll.visibility = View.VISIBLE
            adapter.updateData(historyList)
        }
    }

    private fun clearHistory() {
        getSharedPreferences("translation_history", MODE_PRIVATE).edit().clear().apply()
        loadHistory()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}