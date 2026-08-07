package com.example.devapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SuggestionAdapter(
    private var items: List<Suggestion>,
    private val onApprove: (Suggestion, String, String) -> Unit,
    private val onDecline: (Suggestion) -> Unit
) : RecyclerView.Adapter<SuggestionAdapter.ViewHolder>() {

    private var isDictionaryMode = false

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textWord: TextView = view.findViewById(R.id.text_word)
        val textSourceLang: TextView = view.findViewById(R.id.text_source_lang)
        val labelTranslation1: TextView = view.findViewById(R.id.label_translation_1)
        val editTranslation1: EditText = view.findViewById(R.id.edit_translation_1)
        val labelTranslation2: TextView = view.findViewById(R.id.label_translation_2)
        val editTranslation2: EditText = view.findViewById(R.id.edit_translation_2)
        val btnApprove: Button = view.findViewById(R.id.btn_approve)
        val btnDecline: Button = view.findViewById(R.id.btn_decline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suggestion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textWord.text = item.word
        holder.textSourceLang.text = "Source: ${item.sourceLang}"
        
        val targets = when (item.sourceLang) {
            "English" -> Pair("Filipino", "Cuyonon")
            "Filipino" -> Pair("English", "Cuyonon")
            "Cuyonon" -> Pair("English", "Filipino")
            else -> Pair("Translation 1", "Translation 2")
        }

        holder.labelTranslation1.text = targets.first
        holder.labelTranslation2.text = targets.second
        
        if (isDictionaryMode) {
            holder.editTranslation1.setText(item.translation1)
            holder.editTranslation2.setText(item.translation2)
            holder.btnApprove.text = "Update"
            holder.btnDecline.text = "Delete"
        } else {
            holder.editTranslation1.setText("")
            holder.editTranslation2.setText("")
            holder.btnApprove.text = "Approve"
            holder.btnDecline.text = "Decline"
        }

        holder.btnApprove.setOnClickListener {
            val t1 = holder.editTranslation1.text.toString()
            val t2 = holder.editTranslation2.text.toString()
            if (t1.isNotEmpty() || t2.isNotEmpty()) {
                onApprove(item, t1, t2)
            }
        }
        
        holder.btnDecline.setOnClickListener {
            onDecline(item)
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newList: List<Suggestion>) {
        items = newList
        notifyDataSetChanged()
    }

    fun setDictionaryMode(enabled: Boolean) {
        isDictionaryMode = enabled
        notifyDataSetChanged()
    }
}