package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var inputText: EditText
    private lateinit var outputText: TextView
    private lateinit var spinnerSource: Spinner
    private lateinit var spinnerTarget: Spinner

    private val handler = Handler(Looper.getMainLooper())
    private var translationRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_drawer)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)

        val btnMenu: ImageButton = findViewById(R.id.btnMenu)
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        spinnerSource = findViewById(R.id.spinnerSource)
        spinnerTarget = findViewById(R.id.spinnerTarget)
        val btnSwitch: ImageView = findViewById(R.id.btnSwitch)

        btnSwitch.setOnClickListener {
            val sourcePos = spinnerSource.selectedItemPosition
            val targetPos = spinnerTarget.selectedItemPosition
            spinnerSource.setSelection(targetPos)
            spinnerTarget.setSelection(sourcePos)
        }

        inputText = findViewById(R.id.inputText)
        outputText = findViewById(R.id.outputText)

        inputText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                translationRunnable?.let { handler.removeCallbacks(it) }
                translationRunnable = Runnable {
                    val text = s.toString().trim()
                    if (text.isNotEmpty()) {
                        performTranslation(text)
                    } else {
                        outputText.text = ""
                    }
                }
                handler.postDelayed(translationRunnable!!, 1000) // 1 second debounce
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val btnScan: ImageButton = findViewById(R.id.btnScan)
        val btnHistory: ImageButton = findViewById(R.id.btnHistory)
        val btnSpeak: ImageButton = findViewById(R.id.btnSpeak)
        val btnCopy: ImageButton = findViewById(R.id.btnCopy)
        val btnMic: ImageButton = findViewById(R.id.btnMic)
        val btnCopyInput: ImageButton = findViewById(R.id.btnCopyInput)

        btnScan.setOnClickListener {
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            startActivity(intent)
        }

        btnHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        var isSpeaking = false
        btnSpeak.setOnClickListener {
            isSpeaking = !isSpeaking
            if (isSpeaking) {
                btnSpeak.setImageResource(R.drawable.ic_speak_active)
            } else {
                btnSpeak.setImageResource(R.drawable.ic_speak)
            }
        }

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Translated Text", outputText.text)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
        }

        btnCopyInput.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Input Text", inputText.text)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
        }

        btnMic.setOnClickListener {
            android.widget.Toast.makeText(this, "Voice input not implemented", android.widget.Toast.LENGTH_SHORT).show()
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            val sourceText = it.getStringExtra("sourceText")
            val targetText = it.getStringExtra("targetText")
            val sourceLang = it.getStringExtra("sourceLang")
            val targetLang = it.getStringExtra("targetLang")

            if (sourceText != null && targetText != null && sourceLang != null && targetLang != null) {
                inputText.setText(sourceText)
                outputText.text = targetText
                
                // Set spinners to matching languages
                setSpinnerSelection(spinnerSource, sourceLang)
                setSpinnerSelection(spinnerTarget, targetLang)
            }
        }
    }

    private fun setSpinnerSelection(spinner: Spinner, value: String) {
        for (i in 0 until spinner.count) {
            if (spinner.getItemAtPosition(i).toString().equals(value, ignoreCase = true)) {
                spinner.setSelection(i)
                break
            }
        }
    }

    private fun performTranslation(text: String) {
        val sourceLang = spinnerSource.selectedItem.toString()
        val targetLang = spinnerTarget.selectedItem.toString()
        
        val translated = translateOffline(text, sourceLang, targetLang)
        outputText.text = translated
        saveToHistory(text, translated)
    }

    private fun translateOffline(text: String, source: String, target: String): String {
        val dictionary = mapOf(
            "English-Filipino" to mapOf(
                "hello" to "kumusta",
                "good morning" to "magandang umaga",
                "good afternoon" to "magandang hapon",
                "good evening" to "magandang gabi",
                "thank you" to "salamat",
                "thank you very much" to "maraming salamat",
                "goodbye" to "paalam",
                "how much" to "magkano",
                "where is" to "nasaan ang",
                "water" to "tubig",
                "food" to "pagkain",
                "eat" to "kain",
                "yes" to "oo",
                "no" to "hindi",
                "i love you" to "mahal kita",
                "help" to "tulong",
                "friend" to "kaibigan",
                "beautiful" to "maganda",
                "happy" to "masaya",
                "sorry" to "patawad",
                "what" to "ano",
                "this" to "ito",
                "what is this" to "ano ito"
            ),
            "Filipino-English" to mapOf(
                "kumusta" to "hello",
                "magandang umaga" to "good morning",
                "magandang hapon" to "good afternoon",
                "magandang gabi" to "good evening",
                "salamat" to "thank you",
                "maraming salamat" to "thank you very much",
                "paalam" to "goodbye",
                "magkano" to "how much",
                "nasaan ang" to "where is",
                "tubig" to "water",
                "pagkain" to "food",
                "kain" to "eat",
                "oo" to "yes",
                "hindi" to "no",
                "mahal kita" to "i love you",
                "tulong" to "help",
                "kaibigan" to "friend",
                "maganda" to "beautiful",
                "masaya" to "happy",
                "patawad" to "sorry",
                "ano" to "what",
                "ito" to "this",
                "ano ito" to "what is this?"
            ),
            "English-Cuyonon" to mapOf(
                "hello" to "kumusta",
                "good morning" to "maayad nga timprano",
                "thank you" to "salamat",
                "goodbye" to "paalam",
                "how much" to "pila",
                "where" to "adiman",
                "beautiful" to "maitlo",
                "friend" to "kabalay"
            ),
            "Cuyonon-English" to mapOf(
                "kumusta" to "hello",
                "maayad nga timprano" to "good morning",
                "salamat" to "thank you",
                "pila" to "how much",
                "adiman" to "where",
                "maitlo" to "beautiful",
                "kabalay" to "friend"
            )
        )

        val key = "$source-$target"
        val langDict = dictionary[key] ?: return text
        val lowerText = text.lowercase().trim()
        
        // 1. Try to find exact match of the entire phrase (best match)
        langDict[lowerText]?.let { return it }
        
        // 2. Word-by-word fallback if no full phrase match
        val words = lowerText.split("\\s+".toRegex())
        if (words.size > 1) {
            val translatedWords = words.map { word ->
                langDict[word] ?: word
            }
            return translatedWords.joinToString(" ")
        }
        
        return text // Return original text if no translation found
    }

    private fun saveToHistory(source: String, target: String) {
        val prefs = getSharedPreferences("translation_history", MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", "[]")
        val historyArray = JSONArray(historyJson)

        val newItem = JSONObject().apply {
            put("sourceText", source)
            put("targetText", target)
            put("sourceLang", spinnerSource.selectedItem.toString())
            put("targetLang", spinnerTarget.selectedItem.toString())
            put("timestamp", System.currentTimeMillis())
        }

        // Add to the beginning of the list
        val newList = JSONArray()
        newList.put(newItem)
        for (i in 0 until historyArray.length()) {
            if (i < 49) { // Keep last 50 items
                newList.put(historyArray.get(i))
            }
        }

        prefs.edit().putString("history_list", newList.toString()).apply()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_history -> {
                startActivity(Intent(this, HistoryActivity::class.java))
            }
            R.id.nav_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
            }
            R.id.nav_basic_phrases -> {
                startActivity(Intent(this, BasicPhrasesActivity::class.java))
            }
            R.id.nav_how_to_use -> {
                startActivity(Intent(this, HowToUseActivity::class.java))
            }
            R.id.nav_home -> {
                // Already on Home
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}