package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class PhrasesAdapter(
    private var phrasesList: List<PhraseItem>,
    private var currentFilter: String = "Filipino"
) : RecyclerView.Adapter<PhrasesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textSourceLang: TextView = view.findViewById(R.id.text_source_lang)
        val textTargetLang: TextView = view.findViewById(R.id.text_target_lang)
        val textSourcePhrase: TextView = view.findViewById(R.id.text_source_phrase)
        val textTargetPhrase: TextView = view.findViewById(R.id.text_target_phrase)
        val btnSpeak: ImageButton = view.findViewById(R.id.btn_speak)
        val btnCopy: ImageButton = view.findViewById(R.id.btn_copy)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_phrase, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = phrasesList[position]
        
        val sourceLang = "English"
        val targetLang = currentFilter
        
        holder.textSourceLang.text = sourceLang
        holder.textTargetLang.text = targetLang
        
        holder.textSourcePhrase.text = item.english
        holder.textTargetPhrase.text = if (currentFilter == "Filipino") item.filipino else item.cuyonon

        holder.btnSpeak.setOnClickListener {
            Toast.makeText(holder.itemView.context, "Speaking: ${holder.textTargetPhrase.text}", Toast.LENGTH_SHORT).show()
        }

        holder.btnCopy.setOnClickListener {
            val clipboard = holder.itemView.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Translated Phrase", holder.textTargetPhrase.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(holder.itemView.context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount() = phrasesList.size

    fun updateFilter(filter: String) {
        currentFilter = filter
        notifyDataSetChanged()
    }

    fun updateData(newList: List<PhraseItem>) {
        phrasesList = newList
        notifyDataSetChanged()
    }
}