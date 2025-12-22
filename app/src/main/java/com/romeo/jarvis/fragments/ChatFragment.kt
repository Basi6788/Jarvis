package com.romeo.jarvis.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.romeo.jarvis.R
import com.romeo.jarvis.adapters.ChatAdapter
import com.romeo.jarvis.adapters.ChatMessage
import com.romeo.jarvis.utils.ChatRequest
import com.romeo.jarvis.utils.ChatResponse
import com.romeo.jarvis.utils.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChatFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var inputMessage: EditText
    private lateinit var btnSend: ImageButton
    
    private lateinit var adapter: ChatAdapter
    private val messageList = ArrayList<ChatMessage>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        // Init Views
        recyclerView = view.findViewById(R.id.chatRecyclerView)
        inputMessage = view.findViewById(R.id.inputMessage)
        btnSend = view.findViewById(R.id.btnSend)

        // Setup Adapter
        adapter = ChatAdapter(messageList)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Add Initial Greeting
        addMessage("Secure Channel Established. How can I help?", false)

        // Send Button Click
        btnSend.setOnClickListener {
            val msg = inputMessage.text.toString().trim()
            if (msg.isNotEmpty()) {
                sendMessageToJarvis(msg)
                inputMessage.setText("") // Clear input
            }
        }

        return view
    }

    private fun sendMessageToJarvis(msg: String) {
        // 1. User ka message list me dalo
        addMessage(msg, true)

        // 2. Jarvis Backend Call
        val request = ChatRequest(message = msg, mode = "chat")
        
        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val reply = response.body()!!.reply
                    addMessage(reply, false) // AI ka reply add karo
                } else {
                    addMessage("Error: Systems Unresponsive.", false)
                }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                addMessage("Connection Failed: Offline Mode.", false)
            }
        })
    }

    private fun addMessage(text: String, isUser: Boolean) {
        messageList.add(ChatMessage(text, isUser))
        // Naya message ane par list update karo aur neeche scroll karo
        adapter.notifyItemInserted(messageList.size - 1)
        recyclerView.scrollToPosition(messageList.size - 1)
    }
}
