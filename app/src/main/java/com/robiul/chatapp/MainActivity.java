package com.robiul.chatapp;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private TextView tokenTextView, userInfoTextView;
    private Button usersButton, logoutButton, notificationsButton;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Check if user is logged in, if not redirect to login
        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initViews();
        setupClickListeners();
        updateUserInfo();
        getFCMToken();
        handleNotificationData(getIntent());

        // FCM related setup
        checkBatteryOptimization();
        subscribeToTopics();
        registerMessageReceiver();
        checkPendingToken();
    }

    private void initViews() {
        tokenTextView = findViewById(R.id.tokenTextView);
        userInfoTextView = findViewById(R.id.userInfoTextView);
        usersButton = findViewById(R.id.usersButton);
        logoutButton = findViewById(R.id.logoutButton);
        notificationsButton = findViewById(R.id.notificationsButton);
    }

    private void setupClickListeners() {
        usersButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, UsersActivity.class);
            startActivity(intent);
        });

        logoutButton.setOnClickListener(v -> {
            logoutUser();
        });

        notificationsButton.setOnClickListener(v -> {
            // You can implement NotificationActivity or use for testing
            // For now, let's use it to refresh token and show info
            refreshFCMToken();
        });
    }

    private void updateUserInfo() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            String userInfo = "Email: " + currentUser.getEmail() +
                    "\nUser ID: " + currentUser.getUid().substring(0, 8) + "..." +
                    "\nVerified: " + (currentUser.isEmailVerified() ? "Yes" : "No");
            userInfoTextView.setText(userInfo);
        }
    }

    private void getFCMToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                            tokenTextView.setText("Failed to get FCM token. Check console for details.");
                            return;
                        }

                        String token = task.getResult();
                        Log.d(TAG, "FCM Token: " + token);

                        // Display shortened token for better UI
                        String displayToken = token;
                        if (token.length() > 50) {
                            displayToken = token.substring(0, 50) + "...\n\nTap 'View Notifications' to copy full token";
                        }

                        tokenTextView.setText(displayToken);
                        saveTokenToFirestore(token);

                        Toast.makeText(MainActivity.this, "FCM Token Updated", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void refreshFCMToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Refresh FCM token failed", task.getException());
                            Toast.makeText(MainActivity.this, "Failed to refresh token", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String token = task.getResult();

                        // Copy token to clipboard
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
                            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            clipboard.setText(token);
                        } else {
                            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            android.content.ClipData clip = android.content.ClipData.newPlainText("FCM Token", token);
                            clipboard.setPrimaryClip(clip);
                        }

                        tokenTextView.setText(token);
                        saveTokenToFirestore(token);

                        Toast.makeText(MainActivity.this, "FCM Token Copied to Clipboard!", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveTokenToFirestore(String token) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid())
                    .update("fcmToken", token)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "FCM token saved to Firestore");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to save FCM token to Firestore", e);
                        // Try to create/update full user document
                        updateUserDocumentWithToken(token, currentUser);
                    });
        }
    }

    private void updateUserDocumentWithToken(String token, FirebaseUser user) {
        java.util.Map<String, Object> userData = new java.util.HashMap<>();
        userData.put("fcmToken", token);
        userData.put("email", user.getEmail());
        userData.put("name", user.getDisplayName() != null ? user.getDisplayName() : user.getEmail());
        userData.put("userId", user.getUid());
        userData.put("lastSeen", com.google.firebase.Timestamp.now());
        userData.put("isOnline", true);

        db.collection("users").document(user.getUid())
                .set(userData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User document created/updated with FCM token");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create/update user document: " + e.getMessage());
                });
    }

    private void logoutUser() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        performLogout();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        // Clear online status and FCM token
        updateUserOnlineStatus(false);
        clearFCMTokenOnLogout();

        auth.signOut();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void clearFCMTokenOnLogout() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid())
                    .update("fcmToken", "", "isOnline", false)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "FCM token cleared on logout");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to clear FCM token on logout", e);
                    });
        }
    }

    private void handleNotificationData(Intent intent) {
        if (intent != null && intent.getExtras() != null) {
            Bundle extras = intent.getExtras();
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                Log.d(TAG, "Notification data - Key: " + key + " Value: " + value);
            }

            // Handle chat notification deep link
            if (intent.hasExtra("type") && "chat".equals(intent.getStringExtra("type"))) {
                String senderId = intent.getStringExtra("senderId");
                String senderName = intent.getStringExtra("senderName");

                if (senderId != null && senderName != null) {
                    Intent chatIntent = new Intent(this, ChatActivity.class);
                    chatIntent.putExtra("otherUserId", senderId);
                    chatIntent.putExtra("otherUserName", senderName);
                    startActivity(chatIntent);
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationData(intent);
    }

    // FCM Related Methods
    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                showBatteryOptimizationDialog();
            }
        }
    }

    private void showBatteryOptimizationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Battery Optimization")
                .setMessage("For reliable chat notifications, please disable battery optimization for this app. This ensures you receive messages even when the app is in background.")
                .setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private void subscribeToTopics() {
        // Subscribe to general topic
        FirebaseMessaging.getInstance().subscribeToTopic("all")
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(Task<Void> task) {
                        String msg = task.isSuccessful() ? "Subscribed to notifications" : "Subscription failed";
                        Log.d(TAG, "Topic subscription: " + msg);
                    }
                });

        // Subscribe to user-specific topic
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            String userTopic = "user_" + currentUser.getUid().substring(0, 8);
            FirebaseMessaging.getInstance().subscribeToTopic(userTopic)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(Task<Void> task) {
                            Log.d(TAG, "User topic subscription: " + (task.isSuccessful() ? "Success" : "Failed"));
                        }
                    });
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerMessageReceiver() {
        IntentFilter filter = new IntentFilter("NEW_CHAT_MESSAGE");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(chatMessageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(chatMessageReceiver, filter);
        }
    }

    private BroadcastReceiver chatMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("NEW_CHAT_MESSAGE".equals(intent.getAction())) {
                String senderId = intent.getStringExtra("senderId");
                String senderName = intent.getStringExtra("senderName");
                String message = intent.getStringExtra("message");

                Log.d(TAG, "Foreground message received from: " + senderName);
                showInAppNotification(senderName, message);
            }
        }
    };

    private void showInAppNotification(String senderName, String message) {
        String notificationText = "💬 " + senderName + ": " +
                (message.length() > 30 ? message.substring(0, 30) + "..." : message);

        Toast.makeText(this, notificationText, Toast.LENGTH_LONG).show();
    }

    private void checkPendingToken() {
        SharedPreferences prefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE);
        String pendingToken = prefs.getString("pending_token", null);
        if (pendingToken != null && auth.getCurrentUser() != null) {
            saveTokenToFirestore(pendingToken);
            prefs.edit().remove("pending_token").apply();
            Log.d(TAG, "Pending FCM token updated after login");
        }
    }

    private void updateUserOnlineStatus(boolean isOnline) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            java.util.Map<String, Object> statusData = new java.util.HashMap<>();
            statusData.put("isOnline", isOnline);
            statusData.put("lastSeen", com.google.firebase.Timestamp.now());

            db.collection("users").document(currentUser.getUid())
                    .update(statusData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "User status updated: " + (isOnline ? "Online" : "Offline"));
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update user status", e);
                    });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUserOnlineStatus(true);
        // Refresh user info when returning to app
        updateUserInfo();
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateUserOnlineStatus(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(chatMessageReceiver);
        } catch (Exception e) {
            Log.d(TAG, "Receiver already unregistered");
        }
        updateUserOnlineStatus(false);
    }
}