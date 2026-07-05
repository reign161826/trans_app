package com.example.myapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class HistoryAdapter(
    private var historyList: List<HistoryItem>,
    private val tts: TextToSpeech?,
    private val onItemClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sourceLang: TextView = view.findViewById(R.id.text_source_lang)
        val targetLang: TextView = view.findViewById(R.id.text_target_lang)
        val sourceContent: TextView = view.findViewById(R.id.text_source_content)
        val targetContent: TextView = view.findViewById(R.id.text_target_content)
        val btnSpeak: ImageButton = view.findViewById(R.id.btn_speak_history)
        val btnCopy: ImageButton = view.findViewById(R.id.btn_copy_history)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = historyList[position]

        // Set alternating background colors
        val cardView = holder.itemView as androidx.cardview.widget.CardView
        if (position % 2 == 0) {
            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#D2DCB6"))
        } else {
            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#E2EAD0"))
        }

        holder.sourceLang.text = item.sourceLang
        holder.targetLang.text = item.targetLang
        holder.sourceContent.text = item.sourceText
        holder.targetContent.text = item.targetText
        
        holder.btnSpeak.setOnClickListener {
            val locale = when (item.targetLang) {
                "English" -> Locale.US
                "Filipino" -> Locale("fil", "PH")
                else -> Locale.US
            }
            tts?.language = locale
            tts?.speak(item.targetText, TextToSpeech.QUEUE_FLUSH, null, null)
        }

        holder.btnCopy.setOnClickListener {
            val clipboard = holder.itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Translated Text", item.targetText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(holder.itemView.context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = historyList.size

    fun updateData(newList: List<HistoryItem>) {
        historyList = newList
        notifyDataSetChanged()
    }
}