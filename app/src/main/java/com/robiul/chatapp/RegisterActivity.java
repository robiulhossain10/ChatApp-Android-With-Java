package com.robiul.chatapp;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.robiul.chatapp.models.User;

public class RegisterActivity extends AppCompatActivity {

    TextView fullname,emails,phones,Editpassword,Editconfirmpass;


    Button SingUpBTN;

    TextView loginBtn;

    FirebaseAuth auth;
    FirebaseFirestore db;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);



        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        loginBtn = findViewById(R.id.tvLogin);
        SingUpBTN = findViewById(R.id.btnRegister);

        fullname = findViewById(R.id.etFullname);
        emails = findViewById(R.id.etEmail);
        phones = findViewById(R.id.etPhone);
        Editpassword = findViewById(R.id.etPassword);
        Editconfirmpass = findViewById(R.id.etConfirmPassword);



        SingUpBTN.setOnClickListener(v->{
            registerUser();
        });

        loginBtn.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });






        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void registerUser() {

        String name = fullname.getText().toString().trim();
        String email = emails.getText().toString().trim();
        String phone = phones.getText().toString().trim();
        String password = Editpassword.getText().toString().trim();
        String confirmPassword = Editconfirmpass.getText().toString().trim();

        // Validate name
        if (name.isEmpty()) {
            fullname.setError("Full name is required");
            fullname.requestFocus();
            return;
        }

        // Validate email
        if (email.isEmpty()) {
            emails.setError("Email is required");
            emails.requestFocus();
            return;
        }

        // Validate phone
        if (phone.isEmpty()) {
            phones.setError("Phone number is required");
            phones.requestFocus();
            return;
        }

        // Validate password
        if (password.isEmpty()) {
            Editpassword.setError("Password is required");
            Editpassword.requestFocus();
            return;
        }

        if (password.length() < 5) {
            Editpassword.setError("Password must be at least 5 characters");
            Editpassword.requestFocus();
            return;
        }

        // Validate confirm password
        if (!password.equals(confirmPassword)) {
            Editconfirmpass.setError("Passwords do not match");
            Editconfirmpass.requestFocus();
            return;
        }

        SingUpBTN.setEnabled(false);
        SingUpBTN.setText("Creating Account...");

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Registration success
                            Log.d(TAG, "createUserWithEmail:success");
                            FirebaseUser firebaseUser = auth.getCurrentUser();

                            // Update user profile with name
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();

                            firebaseUser.updateProfile(profileUpdates)
                                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                                        @Override
                                        public void onComplete(Task<Void> task) {
                                            if (task.isSuccessful()) {
                                                Log.d(TAG, "User profile updated.");
                                            }
                                        }
                                    });

                            // Save user to Firestore
                            saveUserToFirestore(firebaseUser, name);

                            // Redirect to MainActivity
                            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();

                        } else {
                            // Registration failed
                            SingUpBTN.setEnabled(true);
                            SingUpBTN.setText("Register");
                            Log.w(TAG, "createUserWithEmail:failure", task.getException());
                            Toast.makeText(RegisterActivity.this, "Registration failed: " +
                                    task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }



    private void saveUserToFirestore(FirebaseUser firebaseUser, String name) {
        User user = new User(
                firebaseUser.getUid(),
                firebaseUser.getEmail(),
                name
        );

        db.collection("users").document(firebaseUser.getUid())
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User saved to Firestore: " + name);
                    Toast.makeText(RegisterActivity.this, "Registration successful!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save user to Firestore", e);
                    Toast.makeText(RegisterActivity.this, "Error saving user data", Toast.LENGTH_SHORT).show();
                });
    }
    }


