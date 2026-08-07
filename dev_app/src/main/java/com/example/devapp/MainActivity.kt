package com.example.devapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: SuggestionAdapter
    private var itemList = mutableListOf<Suggestion>()
    private var isDictionaryMode = true // Default to Dictionary
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide the default Action Bar to match User App style
        supportActionBar?.hide()

        setContentView(R.layout.activity_main)

        db = Firebase.firestore
        
        val headerTitle: TextView = findViewById(R.id.header_title)
        val btnSync: ImageButton = findViewById(R.id.btn_sync)
        val btnClear: ImageButton = findViewById(R.id.btn_clear_suggestions)
        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = SuggestionAdapter(itemList, 
            onApprove = { item, t1, t2 -> 
                if (isDictionaryMode) updateWord(item, t1, t2) 
                else approveSuggestion(item, t1, t2) 
            },
            onDecline = { item -> 
                if (isDictionaryMode) deleteWord(item) 
                else declineSuggestion(item) 
            }
        )
        recyclerView.adapter = adapter

        btnSync.setOnClickListener {
            seedFromCsv(manual = true)
        }

        btnClear.setOnClickListener {
            clearPendingSuggestions()
        }

        val bottomNav: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.menu_dictionary // Set default selection in UI
        
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_suggestions -> {
                    isDictionaryMode = false
                    headerTitle.text = "Suggestions"
                    btnSync.visibility = View.GONE
                    btnClear.visibility = View.VISIBLE
                    adapter.setDictionaryMode(false)
                    fetchData("suggestions", true)
                    true
                }
                R.id.menu_dictionary -> {
                    isDictionaryMode = true
                    headerTitle.text = "Dictionary"
                    btnSync.visibility = View.VISIBLE
                    btnClear.visibility = View.GONE
                    adapter.setDictionaryMode(true)
                    fetchData("verified_words", false)
                    true
                }
                else -> false
            }
        }

        // Set initial UI state
        headerTitle.text = "Dictionary"
        btnSync.visibility = View.VISIBLE
        btnClear.visibility = View.GONE
        adapter.setDictionaryMode(true)

        // Check if database needs seeding, then fetch
        checkAndAutoSeed()
    }

    private fun checkAndAutoSeed() {
        db.collection("verified_words").limit(1).get().addOnSuccessListener { docs ->
            if (docs.isEmpty) {
                Log.d("Firestore", "Database empty, auto-seeding...")
                seedFromCsv(manual = false)
            } else {
                fetchData("verified_words", false)
            }
        }.addOnFailureListener {
            fetchData("verified_words", false)
        }
    }

    private fun seedFromCsv(manual: Boolean) {
        try {
            val inputStream: InputStream = assets.open("wordlist.csv")
            val lines = inputStream.bufferedReader().readLines()
            if (lines.size <= 1) return

            val wordsToSync = lines.drop(1)
            val totalWords = wordsToSync.size
            if (manual) Toast.makeText(this, "Syncing $totalWords words...", Toast.LENGTH_SHORT).show()

            // Firestore limit is 500 per batch. Sync in chunks of 450.
            val chunks = wordsToSync.chunked(450)
            var completedChunks = 0

            chunks.forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { line ->
                    val tokens = if (line.contains("\t")) line.split("\t") else line.split(",")
                    if (tokens.size >= 3) {
                        val english = tokens[0].trim()
                        val filipino = tokens[1].trim()
                        val cuyonon = tokens[2].trim()

                        if (english.isNotEmpty()) {
                            val docRef = db.collection("verified_words").document(english.lowercase())
                            val data = hashMapOf(
                                "word" to english,
                                "sourceLang" to "English",
                                "translation1" to filipino,
                                "translation2" to cuyonon,
                                "timestamp" to System.currentTimeMillis()
                            )
                            batch.set(docRef, data)
                        }
                    }
                }
                batch.commit().addOnSuccessListener {
                    completedChunks++
                    if (completedChunks == chunks.size) {
                        if (manual) Toast.makeText(this, "Fully Synced $totalWords words!", Toast.LENGTH_LONG).show()
                        fetchData("verified_words", false)
                    }
                }.addOnFailureListener { e ->
                    Log.e("Firebase", "Batch failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("CSV", "Error: ${e.message}")
        }
    }

    private fun fetchData(collection: String, isSuggestion: Boolean) {
        listenerRegistration?.remove()
        
        // Clear the list immediately to avoid showing stale data from the previous tab
        itemList.clear()
        adapter.updateList(itemList)
        
        val emptyState: TextView = findViewById(R.id.text_empty_state)
        emptyState.visibility = View.GONE // Hide until data returns
        
        listenerRegistration = db.collection(collection).addSnapshotListener { snapshots, e ->
            if (e != null) {
                emptyState.text = "Connection Error: Check Firebase JSON"
                emptyState.visibility = View.VISIBLE
                return@addSnapshotListener
            }
            
            val newItems = mutableListOf<Suggestion>()
            snapshots?.forEach { doc ->
                val status = doc.getString("status") ?: "pending"
                if (!isSuggestion || status == "pending") {
                    newItems.add(Suggestion(
                        id = doc.id,
                        word = doc.getString("word") ?: "",
                        sourceLang = doc.getString("sourceLang") ?: "English",
                        translation1 = doc.getString("translation1") ?: "",
                        translation2 = doc.getString("translation2") ?: "",
                        status = status
                    ))
                }
            }
            
            itemList = newItems
            adapter.updateList(itemList)
            emptyState.visibility = if (itemList.isEmpty()) View.VISIBLE else View.GONE
            emptyState.text = if (isSuggestion) "No pending suggestions" else "Dictionary is empty"
        }
    }

    private fun updateWord(item: Suggestion, t1: String, t2: String) {
        db.collection("verified_words").document(item.id)
            .update("translation1", t1, "translation2", t2)
            .addOnSuccessListener { Toast.makeText(this, "Updated!", Toast.LENGTH_SHORT).show() }
    }

    private fun deleteWord(item: Suggestion) {
        db.collection("verified_words").document(item.id).delete()
            .addOnSuccessListener { Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show() }
    }

    private fun clearPendingSuggestions() {
        db.collection("suggestions").whereEqualTo("status", "pending").get()
            .addOnSuccessListener { snapshots ->
                if (snapshots.isEmpty) {
                    Toast.makeText(this, "No pending suggestions to clear", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                
                val batch = db.batch()
                snapshots.forEach { doc ->
                    batch.delete(doc.reference)
                }
                batch.commit().addOnSuccessListener {
                    Toast.makeText(this, "Cleared ${snapshots.size()} suggestions", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun approveSuggestion(suggestion: Suggestion, t1: String, t2: String) {
        val verifiedWord = hashMapOf(
            "word" to suggestion.word,
            "sourceLang" to suggestion.sourceLang,
            "translation1" to t1,
            "translation2" to t2,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("verified_words").document(suggestion.word.lowercase()).set(verifiedWord).addOnSuccessListener {
            db.collection("suggestions").document(suggestion.id).update("status", "approved")
            Toast.makeText(this, "Approved and added to Dictionary", Toast.LENGTH_SHORT).show()
        }
    }

    private fun declineSuggestion(suggestion: Suggestion) {
        db.collection("suggestions").document(suggestion.id).update("status", "declined")
            .addOnSuccessListener { Toast.makeText(this, "Declined", Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        listenerRegistration?.remove()
        super.onDestroy()
    }
}
