package com.example.myapplication

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_phrase, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = phrasesList[position]

        // Set alternating background colors
        val cardView = holder.itemView as androidx.cardview.widget.CardView
        if (position % 2 == 0) {
            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#D2DCB6"))
        } else {
            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#E2EAD0"))
        }
        
        val sourceLang = currentFilter
        val targetLang = "Cuyonon"
        
        holder.textSourceLang.text = sourceLang
        holder.textTargetLang.text = targetLang
        
        if (currentFilter == "English") {
            holder.textSourcePhrase.text = item.english
        } else {
            holder.textSourcePhrase.text = item.filipino
        }
        holder.textTargetPhrase.text = item.cuyonon

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val targetText = holder.textTargetPhrase.text.toString()
            val sourceText = holder.textSourcePhrase.text.toString()
            
            // Go to home page and put text to second box
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("sourceText", sourceText)
                putExtra("targetText", targetText)
                putExtra("sourceLang", sourceLang)
                putExtra("targetLang", targetLang)
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
            if (context is android.app.Activity) {
                context.finish()
            }
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