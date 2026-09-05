package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, TextToSpeech.OnInitListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var inputText: EditText
    private lateinit var outputText: TextView
    private lateinit var suggestionText: TextView
    private lateinit var spinnerSource: Spinner
    private lateinit var spinnerTarget: Spinner

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private val SPEECH_REQUEST_CODE = 100
    private val RECORD_AUDIO_REQUEST_CODE = 101
    private val CAMERA_REQUEST_CODE = 102
    private var photoUri: Uri? = null
    private var photoFile: File? = null

    private val handler = Handler(Looper.getMainLooper())
    private var translationRunnable: Runnable? = null
    private var hideSuggestionRunnable: Runnable? = null
    private var isDialogShowing = false

    private var englishToFilipino = mutableMapOf<String, String>()
    private var englishToCuyonon = mutableMapOf<String, String>()
    private var filipinoToEnglish = mutableMapOf<String, String>()
    private var filipinoToCuyonon = mutableMapOf<String, String>()
    private var cuyononToEnglish = mutableMapOf<String, String>()
    private var cuyononToFilipino = mutableMapOf<String, String>()
    
    private var englishWords = mutableSetOf<String>()
    private var filipinoWords = mutableSetOf<String>()
    private var cuyononWords = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_drawer)

        loadDictionaryFromCsv()
        listenToVerifiedWords()

        drawerLayout = findViewById(R.id.drawer_layout)
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)

            val header = findViewById<View>(R.id.headerLayout)
            header.setPadding(0, systemBars.top, 0, 0)

            insets
        }
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)

        val btnMenu: ImageButton = findViewById(R.id.btnMenu)
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        spinnerSource = findViewById(R.id.spinnerSource)
        spinnerTarget = findViewById(R.id.spinnerTarget)
        val btnSwitch: ImageView = findViewById(R.id.btnSwitch)

        // Set default selection: English (2) to Cuyonon (1) based on the array
        spinnerSource.setSelection(2) // English
        spinnerTarget.setSelection(1) // Cuyonon

        val languageListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (spinnerSource.selectedItemPosition == spinnerTarget.selectedItemPosition) {
                    // Prevent same language selection by switching the other spinner
                    val otherPos = (position + 1) % 3
                    if (parent == spinnerSource) {
                        spinnerTarget.setSelection(otherPos)
                    } else {
                        spinnerSource.setSelection(otherPos)
                    }
                }
                
                // Re-translate and update suggestions if there's text
                val text = inputText.text.toString()
                if (text.trim().isNotEmpty()) {
                    performTranslation(text.trim())
                    updateSuggestion(text)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        spinnerSource.onItemSelectedListener = languageListener
        spinnerTarget.onItemSelectedListener = languageListener

        btnSwitch.setOnClickListener {
            val sourcePos = spinnerSource.selectedItemPosition
            val targetPos = spinnerTarget.selectedItemPosition

            val currentInput = inputText.text.toString().trim()
            val currentOutput = outputText.text.toString().trim()

            if (currentInput.isNotEmpty() && currentOutput.isNotEmpty() && currentOutput != "Translated text here...") {
                inputText.setText(currentOutput)
            }

            spinnerSource.setSelection(targetPos)
            spinnerTarget.setSelection(sourcePos)
        }

        inputText = findViewById(R.id.inputText)
        outputText = findViewById(R.id.outputText)
        suggestionText = findViewById(R.id.suggestionText)

        // Hide suggestion when focus is lost
        inputText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) suggestionText.text = ""
        }

        // Accept suggestion when tapping the input area
        inputText.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val suggestion = suggestionText.text.toString()
                if (suggestion.isNotEmpty() && suggestion.length > inputText.text.length) {
                    inputText.setText(suggestion)
                    inputText.setSelection(suggestion.length)
                    suggestionText.text = ""
                    return@setOnTouchListener true
                }
            }
            false
        }

        inputText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s.toString()
                updateSuggestion(text)

                // Hide suggestion if user stops typing (shorter 1.5s timeout)
                hideSuggestionRunnable?.let { handler.removeCallbacks(it) }
                hideSuggestionRunnable = Runnable {
                    suggestionText.text = ""
                }
                handler.postDelayed(hideSuggestionRunnable!!, 1500)

                translationRunnable?.let { handler.removeCallbacks(it) }
                translationRunnable = Runnable {
                    val trimmedText = text.trim()
                    if (trimmedText.isNotEmpty()) {
                        performTranslation(trimmedText, isManualTrigger = false)
                    } else {
                        outputText.text = ""
                    }
                }
                handler.postDelayed(translationRunnable!!, 1500) // Increased debounce for auto-translation
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        inputText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                val text = inputText.text.toString().trim()
                if (text.isNotEmpty()) {
                    performTranslation(text, isManualTrigger = true)
                }
                true
            } else {
                false
            }
        }

        val btnScan: ImageButton = findViewById(R.id.btnScan)
        val btnSpeak: ImageButton = findViewById(R.id.btnSpeak)
        val btnSpeakInput: ImageButton = findViewById(R.id.btnSpeakInput)
        val btnCopy: ImageButton = findViewById(R.id.btnCopy)
        val btnMic: ImageButton = findViewById(R.id.btnMic)
        val btnCopyInput: ImageButton = findViewById(R.id.btnCopyInput)

        btnScan.setOnClickListener {
            checkCameraPermissionAndScan()
        }

        btnSpeak.setOnClickListener {
            val text = outputText.text.toString()
            if (text.isNotEmpty() && text != "Translated text here...") {
                speakText(text, isTarget = true)
                btnSpeak.setImageResource(R.drawable.ic_speak_active)
                handler.postDelayed({
                    btnSpeak.setImageResource(R.drawable.ic_speak)
                }, 2000)
            }
        }

        btnSpeakInput.setOnClickListener {
            val text = inputText.text.toString()
            if (text.isNotEmpty()) {
                speakText(text, isTarget = false)
                btnSpeakInput.setImageResource(R.drawable.ic_speak_active)
                handler.postDelayed({
                    btnSpeakInput.setImageResource(R.drawable.ic_speak)
                }, 2000)
            }
        }

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Translated Text", outputText.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnCopyInput.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Input Text", inputText.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnMic.setOnClickListener {
            checkPermissionAndListen()
        }

        tts = TextToSpeech(this, this)

        handleIntent(intent)
        checkShowGuidelines()
        checkForUpdates()

        if (savedInstanceState != null) {
            savedInstanceState.getString("photo_file_path")?.let { photoFile = File(it) }
            savedInstanceState.getString("photo_uri")?.let { photoUri = Uri.parse(it) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        photoFile?.let { outState.putString("photo_file_path", it.absolutePath) }
        photoUri?.let { outState.putString("photo_uri", it.toString()) }
    }

    private fun checkShowGuidelines() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val dontShowAgain = prefs.getBoolean("dont_show_guidelines", false)

        if (!dontShowAgain) {
            showGuidelinesDialog()
        }
    }

    private fun showGuidelinesDialog() {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_guidelines)
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Set dialog width to 90% of screen width to prevent "squished" look
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val btnClose = dialog.findViewById<ImageButton>(R.id.btn_close_dialog)
        val cbDontShow = dialog.findViewById<android.widget.CheckBox>(R.id.cb_dont_show_again)

        btnClose.setOnClickListener {
            if (cbDontShow.isChecked) {
                val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
                prefs.edit().putBoolean("dont_show_guidelines", true).apply()
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun checkForUpdates(isManual: Boolean = false) {
        val updateUrl = "https://raw.githubusercontent.com/reign161826/trans-app-updates/refs/heads/main/version.json"
        
        Thread {
            try {
                val connection = java.net.URL(updateUrl).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)
                val latestVersionCode = json.getInt("versionCode")
                val downloadUrl = json.getString("downloadUrl")
                val releaseNotes = json.optString("releaseNotes", "A new version of the app is available.")

                val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageManager.getPackageInfo(packageName, 0).longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
                }

                if (latestVersionCode > currentVersionCode) {
                    handler.post {
                        showUpdateDialog(downloadUrl, releaseNotes)
                    }
                } else if (isManual) {
                    handler.post {
                        Toast.makeText(this, "App is up to date", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (isManual) {
                    handler.post {
                        Toast.makeText(this, "Failed to check for updates", Toast.LENGTH_SHORT).show()
                    }
                }
                e.printStackTrace()
            }
        }.start()
    }

    private fun showUpdateDialog(downloadUrl: String, notes: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage(notes)
            .setCancelable(true)
            .setPositiveButton("Download") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        } else {
            Toast.makeText(this, "TTS Initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
        } else {
            listen()
        }
    }

    private fun checkCameraPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
        } else {
            dispatchTakePictureIntent()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                listen()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech recognition not supported", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            photoFile = try {
                createImageFile()
            } catch (ex: IOException) {
                Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
                null
            }
            photoFile?.also {
                photoUri = FileProvider.getUriForFile(
                    this,
                    "com.example.myapplication.fileprovider",
                    it
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE)
            }
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!result.isNullOrEmpty()) {
                inputText.setText(result[0])
                performTranslation(result[0], isManualTrigger = true)
            }
        } else if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            photoUri?.let {
                recognizeTextFromImage(it)
            } ?: photoFile?.let {
                val bitmap = BitmapFactory.decodeFile(it.absolutePath)
                if (bitmap != null) {
                    recognizeTextFromImage(bitmap)
                }
            }
        }
    }

    private fun recognizeTextFromImage(uri: Uri) {
        val image: InputImage = try {
            InputImage.fromFilePath(this, uri)
        } catch (e: IOException) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            return
        }
        processImage(image)
    }

    private fun recognizeTextFromImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        processImage(image)
    }

    private fun processImage(image: InputImage) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val resultText = visionText.text
                if (resultText.isNotEmpty()) {
                    inputText.setText(resultText)
                    performTranslation(resultText, isManualTrigger = true)
                } else {
                    Toast.makeText(this, "No text found in image", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Text recognition failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun speakText(text: String, isTarget: Boolean = true) {
        // 1. Prepare filename: lowercase, replace spaces with underscores, remove special chars
        val cleanFileName = text.lowercase().trim()
            .replace(" ", "_")
            .replace(Regex("[^a-z0-9_]"), "")

        // 2. Check if a recording exists in res/raw
        val resId = resources.getIdentifier(cleanFileName, "raw", packageName)

        if (resId != 0) {
            // 3. Play the human recording
            playRecording(resId)
        } else {
            // 4. Fallback to robotic TTS if no recording found
            val lang = if (isTarget) spinnerTarget.selectedItem.toString() else spinnerSource.selectedItem.toString()
            val locale = when (lang) {
                "English" -> Locale.US
                "Filipino" -> Locale("fil", "PH")
                else -> Locale.US
            }
            tts?.language = locale
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun playRecording(resId: Int) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, resId)
            mediaPlayer?.setOnCompletionListener { 
                it.release()
                mediaPlayer = null
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        tts?.let {
            it.stop()
            it.shutdown()
        }
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
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

            if (it.getBooleanExtra("checkUpdate", false)) {
                checkForUpdates(isManual = true)
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

    private fun performTranslation(text: String, isManualTrigger: Boolean = true) {
        val sourceLang = spinnerSource.selectedItem.toString()
        val targetLang = spinnerTarget.selectedItem.toString()
        
        // Auto-correct / Add word logic for single words
        val trimmed = text.trim()
        val suggestion = suggestionText.text.toString()
        
        val lower = trimmed.lowercase()
        val wordList = when (sourceLang) {
            "English" -> englishWords
            "Filipino" -> filipinoWords
            "Cuyonon" -> cuyononWords
            else -> emptySet()
        }

        if (wordList.contains(lower)) {
            val translated = translateOffline(text, sourceLang, targetLang)
            outputText.text = translated
            saveToHistory(text, translated)
            return
        }

        // Only show dialogs if it's a single word, not already covered by an autocomplete suggestion,
        // and if it's either a manual trigger (like clicking a list item) or a long enough pause.
        if (trimmed.isNotEmpty() && !trimmed.contains(" ") && !isDialogShowing && suggestion.isEmpty()) {
            val closest = findClosestWord(lower, wordList)
            if (closest != null) {
                showCorrectionDialog(lower, closest, sourceLang)
            } else {
                // Show "Add to Dictionary" when the user stops typing or triggers it manually
                showAddWordDialog(lower, sourceLang)
            }
        }

        val translated = translateOffline(text, sourceLang, targetLang)

        outputText.text = translated
        saveToHistory(text, translated)
    }

    private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[m][n]
    }

    private fun findClosestWord(word: String, wordList: Set<String>): String? {
        if (wordList.isEmpty()) return null
        var closest: String? = null
        var minDistance = Int.MAX_VALUE
        for (w in wordList) {
            val distance = calculateLevenshteinDistance(word, w)
            if (distance < minDistance) {
                minDistance = distance
                closest = w
            }
        }
        return if (minDistance > 0 && minDistance <= 2) closest else null
    }

    private fun showCorrectionDialog(wrongWord: String, suggestion: String, lang: String) {
        if (isFinishing || isDestroyed) return
        isDialogShowing = true
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Typo Detected")
            .setMessage("It looks like you typed '$wrongWord'. Did you mean '$suggestion'?")
            .setPositiveButton("Yes, use '$suggestion'") { _, _ ->
                inputText.setText(suggestion)
                inputText.setSelection(suggestion.length)
                isDialogShowing = false
            }
            .setNegativeButton("No, suggest '$wrongWord'") { _, _ ->
                isDialogShowing = false
                showAddWordDialog(wrongWord, lang)
            }
            .setNeutralButton("Ignore") { _, _ -> isDialogShowing = false }
            .setOnDismissListener { isDialogShowing = false }
            .show()
    }

    private fun showAddWordDialog(word: String, lang: String) {
        if (isFinishing || isDestroyed) return
        isDialogShowing = true

        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_suggest_word)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val editWord = dialog.findViewById<EditText>(R.id.edit_word)
        val labelSource = dialog.findViewById<TextView>(R.id.label_source_language)
        val labelT1 = dialog.findViewById<TextView>(R.id.label_translation_1)
        val editT1 = dialog.findViewById<EditText>(R.id.edit_translation_1)
        val labelT2 = dialog.findViewById<TextView>(R.id.label_translation_2)
        val editT2 = dialog.findViewById<EditText>(R.id.edit_translation_2)
        val editDescription = dialog.findViewById<EditText>(R.id.edit_description)
        val btnSubmit = dialog.findViewById<android.widget.Button>(R.id.btn_submit_suggestion)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btn_close_dialog)

        // Determine labels based on source language
        labelSource.text = getString(R.string.label_language_source, lang)
        val targets = when (lang) {
            "English" -> Pair("Filipino", "Cuyonon")
            "Filipino" -> Pair("English", "Cuyonon")
            "Cuyonon" -> Pair("English", "Filipino")
            else -> Pair("Translation 1", "Translation 2")
        }

        labelT1.text = getString(R.string.label_translation_optional).replace("Translation", targets.first)
        labelT2.text = getString(R.string.label_translation_optional).replace("Translation", targets.second)
        editT1.hint = getString(R.string.hint_translation_optional).replace("translation", targets.first.lowercase())
        editT2.hint = getString(R.string.hint_translation_optional).replace("translation", targets.second.lowercase())
        
        editWord.setText(word)
        // Ensure word field is non-editable in code as well
        editWord.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            isLongClickable = false
        }

        btnSubmit.setOnClickListener {
            val suggestedWord = editWord.text.toString().trim()
            val t1 = editT1.text.toString().trim()
            val t2 = editT2.text.toString().trim()
            val description = editDescription.text.toString().trim()

            if (suggestedWord.isEmpty()) {
                Toast.makeText(this, "Please fill in the word to suggest", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendWordToDeveloper(lang, suggestedWord, t1, t2, description)
            dialog.dismiss()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener { isDialogShowing = false }
        dialog.show()
    }

    private fun sendWordToDeveloper(sourceLang: String, word: String, t1: String, t2: String, description: String) {
        val db = Firebase.firestore
        val suggestion = hashMapOf(
            "sourceLang" to sourceLang,
            "word" to word.trim(),
            "translation1" to t1,
            "translation2" to t2,
            "description" to description,
            "timestamp" to System.currentTimeMillis(),
            "status" to "pending"
        )

        db.collection("suggestions")
            .add(suggestion)
            .addOnSuccessListener {
                Toast.makeText(this, "Success! '$word' sent to Moderator.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Connection Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateSuggestion(text: String) {
        if (text.isEmpty()) {
            suggestionText.text = ""
            return
        }

        val sourceLang = spinnerSource.selectedItem.toString()
        val wordList = when (sourceLang) {
            "English" -> englishWords
            "Filipino" -> filipinoWords
            "Cuyonon" -> cuyononWords
            else -> emptySet<String>()
        }

        val lowerText = text.lowercase()
        // Find the first word/phrase that starts with the input but isn't identical
        val suggestion = wordList.find { it.startsWith(lowerText) && it != lowerText }

        if (suggestion != null) {
            // Match the case: use the user's typed text + the remaining part of the suggestion
            val result = text + suggestion.substring(text.length)
            suggestionText.text = result
        } else {
            suggestionText.text = ""
        }
    }

    private fun loadDictionaryFromCsv() {
        try {
            val inputStream = assets.open("wordlist.csv")
            val reader = inputStream.bufferedReader()
            reader.useLines { lines ->
                lines.toList().drop(1).forEach { line ->
                    val tokens = if (line.contains("\t")) line.split("\t") else line.split(",")
                    if (tokens.size >= 3) {
                        addTrilingualEntry(tokens[0], tokens[1], tokens[2])
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addTrilingualEntry(eng: String, fil: String, cuy: String) {
        val e = eng.lowercase().trim()
        val f = fil.lowercase().trim()
        val c = cuy.lowercase().trim()

        if (e.isNotEmpty()) {
            englishWords.add(e)
            if (f.isNotEmpty()) englishToFilipino[e] = f
            if (c.isNotEmpty()) englishToCuyonon[e] = c
        }
        if (f.isNotEmpty()) {
            filipinoWords.add(f)
            if (e.isNotEmpty()) filipinoToEnglish[f] = e
            if (c.isNotEmpty()) filipinoToCuyonon[f] = c
        }
        if (c.isNotEmpty()) {
            cuyononWords.add(c)
            if (e.isNotEmpty()) cuyononToEnglish[c] = e
            if (f.isNotEmpty()) cuyononToFilipino[c] = f
        }
    }

    private fun listenToVerifiedWords() {
        val db = Firebase.firestore
        db.collection("verified_words").addSnapshotListener { snapshots, e ->
            if (e != null) return@addSnapshotListener
            
            snapshots?.forEach { doc ->
                val word = doc.getString("word")?.lowercase()?.trim() ?: ""
                val sourceLang = doc.getString("sourceLang") ?: ""
                val t1 = doc.getString("translation1")?.trim() ?: ""
                val t2 = doc.getString("translation2")?.trim() ?: ""
                
                if (word.isNotEmpty()) {
                    when (sourceLang) {
                        "English" -> addTrilingualEntry(word, t1, t2)
                        "Filipino" -> addTrilingualEntry(t1, word, t2)
                        "Cuyonon" -> addTrilingualEntry(t1, t2, word)
                    }
                }
            }
        }
    }

    private fun translateOffline(text: String, source: String, target: String): String {
        val lowerText = text.lowercase().trim()
        
        val map = when (source) {
            "English" -> if (target == "Filipino") englishToFilipino else englishToCuyonon
            "Filipino" -> if (target == "English") filipinoToEnglish else filipinoToCuyonon
            "Cuyonon" -> if (target == "English") cuyononToEnglish else cuyononToFilipino
            else -> emptyMap<String, String>()
        }

        // 1. Try to find exact match
        map[lowerText]?.let { return it }

        // 2. Apply Grammar Rules (General patterns)
        val grammarResult = applyGrammarRules(lowerText, source, target, map)
        if (grammarResult != null) return grammarResult
        
        // 3. Word-by-word fallback
        return translateByWords(lowerText, map)
    }

    private fun applyGrammarRules(text: String, source: String, target: String, dict: Map<String, String>): String? {
        val words = text.split("\\s+".toRegex())

        // --- TO ENGLISH RULES ---
        if (target == "English") {
            // Rule 1: Negation "Hindi/Indi [Adj] [Pronoun]" -> "[Pronoun] is/are not [Adj]"
            // Example: "Hindi panget mo" -> "You are not ugly"
            if (words.size == 3 && (words[0] == "hindi" || words[0] == "indi")) {
                val adj = dict[words[1]]
                val subject = dict[words[2]]
                if (adj != null && subject != null) {
                    return "${subject.replaceFirstChar { it.uppercase() }} ${getVerb(subject)} not $adj"
                }
            }

            // Rule 2: "Ang [Adj] [Pronoun]" (Filipino) -> "[Pronoun] is/are [Adj]"
            if (words.size == 3 && words[0] == "ang") {
                val adj = dict[words[1]]
                val subject = dict[words[2]]
                if (adj != null && subject != null) {
                    return "${subject.replaceFirstChar { it.uppercase() }} ${getVerb(subject)} $adj"
                }
            }

            // Rule 3: "Mga [Noun]" -> "[Noun]s" (Simple Plurality)
            if (words.size == 2 && words[0] == "mga") {
                val noun = dict[words[1]]
                if (noun != null) return "${noun}s"
            }

            // Rule 4: "[Adj] [Pronoun]" (Cuyonon/Filipino style) -> "[Pronoun] is/are [Adj]"
            if (words.size == 2) {
                val adj = dict[words[0]]
                val subject = dict[words[1]]
                if (adj != null && subject != null && isPronoun(subject)) {
                    return "${subject.replaceFirstChar { it.uppercase() }} ${getVerb(subject)} $adj"
                }
            }

            // Rule 7: "Saan/Sadin ang [Noun]" -> "Where is the [Noun]"
            if (words.size >= 2 && (words[0] == "saan" || words[0] == "sadin")) {
                val remainingWords = if (words.size > 1 && words[1] == "ang") words.drop(2) else words.drop(1)
                val translatedNoun = remainingWords.map { dict[it] ?: it }.joinToString(" ")
                if (translatedNoun.isNotEmpty()) {
                    return "Where is the $translatedNoun"
                }
            }
        }

        // --- FROM ENGLISH RULES ---
        if (source == "English") {
            // Rule 5: "[Pronoun] [am/is/are] [Adj]" -> "[Adj] [Pronoun]" or "Ako ay [Adj]"
            if (words.size == 3 && (words[1] == "am" || words[1] == "is" || words[1] == "are")) {
                val subject = dict[words[0]]
                val adj = dict[words[2]]
                if (subject != null && adj != null) {
                    return if (target == "Filipino") "$subject ay $adj" else "$adj $subject"
                }
            }

            // Rule 6: "Where is/are (the) [Noun]" -> "Saan (ang) [Noun]"
            // Example: "Where is the pharmacy" -> "Saan ang botika"
            if (words.size >= 3 && words[0] == "where" && (words[1] == "is" || words[1] == "are")) {
                val translatedRemaining = words.drop(2).map { dict[it] ?: it }.joinToString(" ")
                val where = dict["where"] ?: if (target == "Filipino") "saan" else "sadin"
                return "${where.replaceFirstChar { it.uppercase() }} $translatedRemaining"
            }
        }

        return null
    }

    private fun getVerb(subject: String): String {
        val s = subject.lowercase()
        return when {
            s == "i" -> "am"
            s == "you" || s == "we" || s == "they" -> "are"
            else -> "is"
        }
    }

    private fun isPronoun(word: String): Boolean {
        val pronouns = listOf("i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us", "them")
        return pronouns.contains(word.lowercase())
    }

    private fun translateByWords(text: String, dict: Map<String, String>): String {
        val words = text.split("\\s+".toRegex())
        if (words.size > 1) {
            val translatedWords = words.map { word ->
                dict[word] ?: word
            }
            return translatedWords.joinToString(" ")
        }
        return text
    }

    private fun saveToHistory(source: String, target: String) {
        if (source.isBlank() || target.isBlank() || target == "Translated text here...") return

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
            R.id.nav_update -> {
                checkForUpdates(isManual = true)
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
