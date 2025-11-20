package com.robiul.chatapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.robiul.chatapp.adapters.UsersAdapter;
import com.robiul.chatapp.models.User;
import com.robiul.chatapp.service.ChatManager;

import java.util.ArrayList;
import java.util.List;

public class UsersActivity extends AppCompatActivity {
    private RecyclerView usersRecyclerView;
    private List<User> users;
    private UsersAdapter usersAdapter;
    private ProgressBar progressBar;
    private ChatManager chatManager;
    private TextView emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_users);

        // Check if user is logged in
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        chatManager = new ChatManager();
        initViews();
        loadUsers();
    }

    private void initViews() {
        usersRecyclerView = findViewById(R.id.usersRecyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);

        users = new ArrayList<>();
        usersAdapter = new UsersAdapter(users, this::onUserClicked);
        usersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        usersRecyclerView.setAdapter(usersAdapter);
    }
    // In UsersActivity, update the loadUsers method:
    private void loadUsers() {
        progressBar.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);

        chatManager.getAllUsers(new ChatManager.UsersCallback() {
            @Override
            public void onUsersReceived(List<User> usersList) {
                progressBar.setVisibility(View.GONE);

                users.clear();
                users.addAll(usersList);
                usersAdapter.setUsers(users);

                if (users.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    emptyState.setText("No other users found");
                }
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                emptyState.setVisibility(View.VISIBLE);
                emptyState.setText("Error: " + error);
                Toast.makeText(UsersActivity.this, "Error loading users: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
//    private void loadUsers() {
//        progressBar.setVisibility(View.VISIBLE);
//        emptyState.setVisibility(View.GONE);
//
//        chatManager.getAllUsers(new ChatManager.UsersCallback() {
//            @Override
//            public void onUsersReceived(List<Map<String, Object>> usersList) {
//                progressBar.setVisibility(View.GONE);
//
//                users.clear();
//                for (Map<String, Object> userMap : usersList) {
//                    try {
//                        User user = new User();
//                        user.setUserId((String) userMap.get("userId"));
//                        user.setEmail((String) userMap.get("email"));
//                        user.setName((String) userMap.get("name"));
//                        user.setFcmToken((String) userMap.get("fcmToken"));
//                        users.add(user);
//                    } catch (Exception e) {
//                        Log.e("UsersActivity", "Error parsing user: " + e.getMessage());
//                    }
//                }
//                usersAdapter.setUsers(users);
//
//                if (users.isEmpty()) {
//                    emptyState.setVisibility(View.VISIBLE);
//                    emptyState.setText("No other users found");
//                }
//            }
//
//            @Override
//            public void onError(String error) {
//                progressBar.setVisibility(View.GONE);
//                emptyState.setVisibility(View.VISIBLE);
//                emptyState.setText("Error: " + error);
//                Toast.makeText(UsersActivity.this, "Error loading users: " + error, Toast.LENGTH_LONG).show();
//                Log.e("UsersActivity", "Error: " + error);
//            }
//        });
//    }

    private void onUserClicked(User user) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("otherUserId", user.getUserId());
        intent.putExtra("otherUserName", user.getName());
        startActivity(intent);
    }
}