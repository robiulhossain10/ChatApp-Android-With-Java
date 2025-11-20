package com.robiul.chatapp.service;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.robiul.chatapp.models.ChatMessage;
import com.robiul.chatapp.models.User;

import java.util.*;

public class ChatManager {
    private static final String TAG = "ChatManager";
    private FirebaseFirestore db;
    private String currentUserId;

    public ChatManager() {
        db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
            Log.d(TAG, "Current user: " + currentUserId);
        } else {
            Log.e(TAG, "No user logged in");
        }
    }

    public void sendMessage(String receiverId, String message) {
        if (currentUserId == null) {
            Log.e(TAG, "Cannot send message: User not logged in");
            return;
        }

        String chatId = generateChatId(currentUserId, receiverId);
        String messageId = db.collection("messages").document().getId();

        // Create message data
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("messageId", messageId);
        messageData.put("senderId", currentUserId);
        messageData.put("receiverId", receiverId);
        messageData.put("message", message);
        messageData.put("timestamp", System.currentTimeMillis());
        messageData.put("messageType", "text");
        messageData.put("isSeen", false);
        messageData.put("chatId", chatId);

        // Save message to Firestore
        db.collection("messages").document(messageId)
                .set(messageData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Message sent successfully");
                    updateChatRoom(chatId, currentUserId, receiverId, message);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send message: " + e.getMessage());
                });
    }

    private void updateChatRoom(String chatId, String senderId, String receiverId, String lastMessage) {
        Map<String, Object> chatData = new HashMap<>();
        chatData.put("chatId", chatId);
        chatData.put("participants", Arrays.asList(senderId, receiverId));
        chatData.put("lastMessage", lastMessage);
        chatData.put("lastMessageTime", System.currentTimeMillis());
        chatData.put("lastMessageSender", senderId);

        db.collection("chats").document(chatId)
                .set(chatData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Chat room updated"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update chat room: " + e.getMessage()));
    }

    public void getMessages(String otherUserId, MessagesCallback callback) {
        if (currentUserId == null) {
            callback.onError("User not logged in");
            return;
        }

        String chatId = generateChatId(currentUserId, otherUserId);
        Log.d(TAG, "Getting messages for chat: " + chatId);

        // Query without ordering first (no index needed)
        db.collection("messages")
                .whereEqualTo("chatId", chatId)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed: " + error);
                        callback.onError(error.getMessage());
                        return;
                    }

                    List<ChatMessage> messages = new ArrayList<>();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            Map<String, Object> data = doc.getData();
                            ChatMessage message = mapToChatMessage(data);
                            messages.add(message);
                        }

                        // Sort messages by timestamp manually
                        Collections.sort(messages, (m1, m2) ->
                                Long.compare(m1.getTimestamp(), m2.getTimestamp()));
                    }
                    callback.onMessagesReceived(messages);
                });
    }

    private ChatMessage mapToChatMessage(Map<String, Object> data) {
        ChatMessage message = new ChatMessage();

        // Set basic fields
        message.setMessageId((String) data.get("messageId"));
        message.setSenderId((String) data.get("senderId"));
        message.setReceiverId((String) data.get("receiverId"));
        message.setMessage((String) data.get("message"));
        message.setChatId((String) data.get("chatId"));
        message.setMessageType((String) data.get("messageType"));

        // Handle timestamp (could be Long or Double from Firestore)
        Object timestamp = data.get("timestamp");
        if (timestamp instanceof Long) {
            message.setTimestamp((Long) timestamp);
        } else if (timestamp instanceof Double) {
            message.setTimestamp(((Double) timestamp).longValue());
        } else {
            message.setTimestamp(System.currentTimeMillis());
        }

        // Handle isSeen field
        Boolean isSeen = (Boolean) data.get("isSeen");
        message.setSeen(isSeen != null ? isSeen : false);

        return message;
    }

    public void getAllUsers(UsersCallback callback) {
        if (currentUserId == null) {
            callback.onError("User not logged in");
            return;
        }

        Log.d(TAG, "Fetching all users except: " + currentUserId);

        db.collection("users")
                .whereNotEqualTo("userId", currentUserId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<User> users = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> data = document.getData();
                            User user = new User();
                            user.setUserId((String) data.get("userId"));
                            user.setEmail((String) data.get("email"));
                            user.setName((String) data.get("name"));
                            user.setFcmToken((String) data.get("fcmToken"));
                            users.add(user);
                            Log.d(TAG, "Found user: " + user.getName());
                        }
                        callback.onUsersReceived(users);
                    } else {
                        Log.e(TAG, "Error getting users: ", task.getException());
                        callback.onError(task.getException().getMessage());
                    }
                });
    }

    public void markMessageAsSeen(String messageId) {
        if (currentUserId == null) return;

        db.collection("messages").document(messageId)
                .update("isSeen", true)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Message marked as seen"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to mark message as seen: " + e.getMessage()));
    }

    private String generateChatId(String user1, String user2) {
        // Generate consistent chat ID regardless of user order
        return user1.compareTo(user2) < 0 ? user1 + "_" + user2 : user2 + "_" + user1;
    }

    // Callback interfaces
    public interface MessagesCallback {
        void onMessagesReceived(List<ChatMessage> messages);
        void onError(String error);
    }

    public interface UsersCallback {
        void onUsersReceived(List<User> users);
        void onError(String error);
    }
}