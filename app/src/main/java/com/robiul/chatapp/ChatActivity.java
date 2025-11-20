package com.robiul.chatapp;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.robiul.chatapp.adapters.MessageAdapter;
import com.robiul.chatapp.models.ChatMessage;
import com.robiul.chatapp.service.ChatManager;

import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView messagesRecyclerView;
    private EditText messageEditText;
    private Button sendButton;
    private TextView chatTitle;
    private MessageAdapter messageAdapter;
    private ChatManager chatManager;
    private String otherUserId;
    private String otherUserName;
    private LinearLayoutManager layoutManager;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Get other user ID from intent
        otherUserId = getIntent().getStringExtra("otherUserId");
        otherUserName = getIntent().getStringExtra("otherUserName");

        if (otherUserId == null || otherUserName == null) {
            Toast.makeText(this, "Error: No user selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupChat();


    }

    private void initViews() {
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);
        chatTitle = findViewById(R.id.chatTitle);

        // Set chat title
        if (chatTitle != null) {
            chatTitle.setText("Chat with " + otherUserName);
        } else {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Chat with " + otherUserName);
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        // Setup back button
        Button backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> onSupportNavigateUp());
        }

        // Setup RecyclerView with auto-scroll
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // This is key for bottom alignment
        messagesRecyclerView.setLayoutManager(layoutManager);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
        messageAdapter = new MessageAdapter(currentUserId);
        messagesRecyclerView.setAdapter(messageAdapter);

        sendButton.setOnClickListener(v -> sendMessage());

        // Send on enter key
        messageEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    private void setupChat() {
        chatManager = new ChatManager();

        // Listen for messages
        chatManager.getMessages(otherUserId, new ChatManager.MessagesCallback() {
            @Override
            public void onMessagesReceived(List<ChatMessage> messages) {
                messageAdapter.setMessages(messages);

                // Auto-scroll to latest message
                if (!messages.isEmpty()) {
                    messagesRecyclerView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            messagesRecyclerView.smoothScrollToPosition(messages.size() - 1);
                        }
                    }, 100);
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendMessage() {
        String message = messageEditText.getText().toString().trim();
        if (!message.isEmpty()) {
            chatManager.sendMessage(otherUserId, message);
            messageEditText.setText("");

            // Auto-scroll after sending message
            messagesRecyclerView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (messageAdapter.getItemCount() > 0) {
                        messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
                    }
                }
            }, 200);
        } else {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // Optional: Auto-scroll when keyboard appears/disappears
    @Override
    protected void onResume() {
        super.onResume();
        // Scroll to bottom when activity resumes
        messagesRecyclerView.postDelayed(() -> {
            if (messageAdapter.getItemCount() > 0) {
                messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
            }
        }, 300);
    }
}