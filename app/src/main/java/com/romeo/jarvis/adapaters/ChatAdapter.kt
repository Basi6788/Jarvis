package com.romeo.jarvis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.romeo.jarvis.R

// Data Class for Message
data class ChatMessage(val message: String, val isUser: Boolean)

class ChatAdapter(private val messageList: ArrayList<ChatMessage>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_USER = 1
    private val TYPE_AI = 2

    // View Holders
    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtMsg: TextView = itemView.findViewById(R.id.textMessage)
    }

    class AIViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtMsg: TextView = itemView.findViewById(R.id.textMessage)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messageList[position].isUser) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_USER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_ai, parent, false)
            AIViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chat = messageList[position]

        // ANIMATION: Slide in from bottom
        setAnimation(holder.itemView)

        if (holder.itemViewType == TYPE_USER) {
            (holder as UserViewHolder).txtMsg.text = chat.message
        } else {
            (holder as AIViewHolder).txtMsg.text = chat.message
        }
    }

    private fun setAnimation(viewToAnimate: View) {
        // Animation file banane ki zarurat nahi, Android ki default use kar rahe hain
        val animation = AnimationUtils.loadAnimation(viewToAnimate.context, android.R.anim.slide_in_left)
        animation.duration = 300 // ms
        viewToAnimate.startAnimation(animation)
    }

    override fun getItemCount(): Int = messageList.size
}
