package com.example.app7

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CardAdapter(private val cards: MutableList<CardData>) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    private lateinit var videoStorage: VideoStorage

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_display, parent, false)
        videoStorage = VideoStorage(parent.context)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        holder.pos1.text = "x position: ${card.intValue1}"
        holder.pos2.text = "y position: ${card.intValue2}"
        holder.text.text = "Text: ${card.textValue}"
        holder.videoView.setVideoURI(card.videoUri)

        // Play the video if clicked
        holder.videoView.setOnClickListener {
            val intent = Intent(holder.itemView.context, VideoPlayerActivity::class.java).apply {
                putExtra("VIDEO_URI", card.videoUri.toString()) // Pass video URI
                putExtra("TEXT_TO_SPEAK", card.textValue) // Replace with actual text
            }
            holder.itemView.context.startActivity(intent)
        }

        holder.btnDelete.setOnClickListener{
            videoStorage.deleteVideo(card.videoId)
            removeCardAtPosition(position)
        }
    }




    override fun getItemCount(): Int {
        return cards.size
    }

    private fun removeCardAtPosition(position: Int) {
        // Remove the card from the list
        cards.removeAt(position)
        // Notify the adapter about the removal
        notifyItemRemoved(position)
    }

    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val pos1: MaterialTextView = itemView.findViewById(R.id.xdisplay)
        val pos2: MaterialTextView = itemView.findViewById(R.id.ydisplay)
        val text: MaterialTextView = itemView.findViewById(R.id.textDisplay)
        val videoView: VideoView = itemView.findViewById(R.id.videoView)
        val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)
    }
}

data class CardData(val intValue1: Int, val intValue2: Int, val textValue: String, val videoUri: Uri, val videoId: String)