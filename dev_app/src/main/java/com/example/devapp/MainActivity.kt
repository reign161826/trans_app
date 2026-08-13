package com.example.devapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: SuggestionAdapter
    private var itemList = mutableListOf<Suggestion>()
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        val mainView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)

            val header = findViewById<View>(R.id.header)
            header?.setPadding(0, systemBars.top, 0, 0)

            insets
        }

        db = Firebase.firestore
        
        val headerTitle: TextView = findViewById(R.id.header_title)
        val btnSync: ImageButton = findViewById(R.id.btn_sync)
        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        val swipeRefresh: SwipeRefreshLayout = findViewById(R.id.swipe_refresh)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = SuggestionAdapter(itemList, 
            onApprove = { item, t1, t2, desc -> 
                approveSuggestion(item, t1, t2, desc) 
            },
            onDecline = { item -> 
                declineSuggestion(item) 
            }
        )
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            fetchData("suggestions")
            swipeRefresh.isRefreshing = false
        }

        btnSync.setOnClickListener {
            seedFromCsv(manual = true)
        }

        // Suggestion mode only
        headerTitle.text = "Translation Requests"
        btnSync.visibility = View.GONE
        adapter.setDictionaryMode(false)
        fetchData("suggestions")
    }

    private fun seedFromCsv(manual: Boolean) {
        try {
            val inputStream: InputStream = assets.open("wordlist.csv")
            val lines = inputStream.bufferedReader().readLines()
            if (lines.size <= 1) return

            val wordsToSync = lines.drop(1)
            val totalWords = wordsToSync.size
            if (manual) Toast.makeText(this, "Syncing $totalWords words...", Toast.LENGTH_SHORT).show()

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
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CSV", "Error: ${e.message}")
        }
    }

    private fun fetchData(collection: String) {
        listenerRegistration?.remove()
        itemList.clear()
        adapter.updateList(itemList)
        
        val emptyState: TextView = findViewById(R.id.text_empty_state)
        emptyState.visibility = View.GONE
        
        listenerRegistration = db.collection(collection)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.e("Firestore", "Listen failed.", e)
                emptyState.text = "Connection Error"
                emptyState.visibility = View.VISIBLE
                return@addSnapshotListener
            }
            
            val newItems = mutableListOf<Suggestion>()
            snapshots?.forEach { doc ->
                newItems.add(Suggestion(
                    id = doc.id,
                    word = doc.getString("word") ?: "",
                    sourceLang = doc.getString("sourceLang") ?: "English",
                    targetLang = doc.getString("targetLang") ?: "",
                    userTranslation = doc.getString("translation") ?: "",
                    translation1 = doc.getString("translation1") ?: "",
                    translation2 = doc.getString("translation2") ?: "",
                    description = doc.getString("description") ?: "",
                    status = "pending",
                    timestamp = doc.getLong("timestamp") ?: 0L
                ))
            }
            
            // Sort client-side to avoid needing a Firestore composite index
            itemList = newItems.sortedByDescending { it.timestamp }.toMutableList()
            adapter.updateList(itemList)
            emptyState.visibility = if (itemList.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun clearPendingSuggestions() {
        db.collection("suggestions").whereEqualTo("status", "pending").get()
            .addOnSuccessListener { snapshots ->
                if (snapshots.isEmpty) {
                    Toast.makeText(this, "No pending suggestions", Toast.LENGTH_SHORT).show()
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

    private fun approveSuggestion(suggestion: Suggestion, t1: String, t2: String, desc: String) {
        val verifiedWord = hashMapOf(
            "word" to suggestion.word,
            "sourceLang" to suggestion.sourceLang,
            "translation1" to t1,
            "translation2" to t2,
            "description" to desc,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("verified_words").document(suggestion.word.lowercase()).set(verifiedWord).addOnSuccessListener {
            db.collection("suggestions").document(suggestion.id).update("status", "approved")
            Toast.makeText(this, "Approved!", Toast.LENGTH_SHORT).show()
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
