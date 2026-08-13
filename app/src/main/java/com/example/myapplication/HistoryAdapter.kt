package com.example.myapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(
    private var historyList: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sourceLang: TextView = view.findViewById(R.id.text_source_lang)
        val targetLang: TextView = view.findViewById(R.id.text_target_lang)
        val sourceContent: TextView = view.findViewById(R.id.text_source_content)
        val targetContent: TextView = view.findViewById(R.id.text_target_content)
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
            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#E4EFDB"))
        } else {
            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#D2DCB6"))
        }

        holder.sourceLang.text = item.sourceLang
        holder.targetLang.text = item.targetLang
        holder.sourceContent.text = item.sourceText
        holder.targetContent.text = item.targetText

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("sourceText", item.sourceText)
                putExtra("targetText", item.targetText)
                putExtra("sourceLang", item.sourceLang)
                putExtra("targetLang", item.targetLang)
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
            if (context is android.app.Activity) {
                context.finish()
            }
        }
    }

    override fun getItemCount() = historyList.size

    fun updateData(newList: List<HistoryItem>) {
        historyList = newList
        notifyDataSetChanged()
    }
}