package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.Spinner
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView

class BasicPhrasesActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PhrasesAdapter
    private var allPhrases = mutableListOf<PhraseItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.basic_phrases)

        loadPhrasesFromCsv()

        drawerLayout = findViewById(R.id.drawer_layout_basic_phrases)
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)

            val header = findViewById<View>(R.id.header_layout)
            header?.setPadding(0, systemBars.top, 0, 0)

            insets
        }

        val btnMenu: ImageButton = findViewById(R.id.btn_menu_basic_phrases)
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        recyclerView = findViewById(R.id.recycler_view_phrases)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PhrasesAdapter(allPhrases)
        recyclerView.adapter = adapter

        val spinnerFilter: Spinner = findViewById(R.id.spinner_filter)
        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLang = parent?.getItemAtPosition(position).toString()
                // The CSV has: English (0), Filipino (1), Cuyonon (2)
                // The spinner has: Filipino (0), English (1)
                val filterLang = if (selectedLang == "Filipino") "Filipino" else "English"
                adapter.updateFilter(filterLang)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
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
                    val intent = Intent(this@BasicPhrasesActivity, HistoryActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                R.id.nav_basic_phrases -> {
                    // Already here
                }
                R.id.nav_about -> {
                    val intent = Intent(this@BasicPhrasesActivity, AboutActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                R.id.nav_how_to_use -> {
                    val intent = Intent(this@BasicPhrasesActivity, HowToUseActivity::class.java)
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

    private fun loadPhrasesFromCsv() {
        try {
            val inputStream = assets.open("wordlist.csv")
            val reader = inputStream.bufferedReader()
            val lines = reader.readLines()
            
            // Skip header (index 0) and take lines 2 to 30 (indices 1 to 29)
            for (i in 1 until minOf(lines.size, 30)) {
                val tokens = lines[i].split(",")
                if (tokens.size >= 3) {
                    val english = tokens[0].trim()
                    val filipino = tokens[1].trim()
                    val cuyonon = tokens[2].trim()
                    allPhrases.add(PhraseItem(english, filipino, cuyonon))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}