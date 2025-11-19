package com.robiul.chatapp.adapters;


import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.robiul.chatapp.R;
import com.robiul.chatapp.models.User;

import java.util.List;

public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.UserViewHolder> {
    private List<User> users;
    private OnUserClickListener onUserClickListener;
    private int[] avatarColors = {
            Color.parseColor("#FF5733"), Color.parseColor("#33FF57"),
            Color.parseColor("#3357FF"), Color.parseColor("#F333FF"),
            Color.parseColor("#FF33A1"), Color.parseColor("#33FFF3"),
            Color.parseColor("#FFD733"), Color.parseColor("#8D33FF")
    };

    public UsersAdapter(List<User> users, OnUserClickListener onUserClickListener) {
        this.users = users;
        this.onUserClickListener = onUserClickListener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = users.get(position);
        holder.bind(user, onUserClickListener, avatarColors);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public void setUsers(List<User> users) {
        this.users = users;
        notifyDataSetChanged();
    }

    public void addUser(User user) {
        users.add(user);
        notifyItemInserted(users.size() - 1);
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private TextView userName;
        private TextView userEmail;
        private TextView userStatus;
        private TextView userInitial;
        private View onlineIndicator;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            userName = itemView.findViewById(R.id.userName);
            userEmail = itemView.findViewById(R.id.userEmail);
            userStatus = itemView.findViewById(R.id.userStatus);
            userInitial = itemView.findViewById(R.id.userInitial);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
        }

        public void bind(User user, OnUserClickListener listener, int[] avatarColors) {
            userName.setText(user.getName());
            userEmail.setText(user.getEmail());

            // Set user initial for avatar
            if (user.getName() != null && !user.getName().isEmpty()) {
                String initial = user.getName().substring(0, 1).toUpperCase();
                userInitial.setText(initial);

                // Set random color for avatar based on user name
                int colorIndex = Math.abs(user.getName().hashCode()) % avatarColors.length;
                userInitial.setBackgroundColor(avatarColors[colorIndex]);
            }

            // Set online status
            boolean isOnline = user.getFcmToken() != null && !user.getFcmToken().isEmpty();
            if (isOnline) {
                userStatus.setText("Online");
                userStatus.setTextColor(itemView.getContext().getColor(android.R.color.holo_green_dark));
                onlineIndicator.setVisibility(View.VISIBLE);
            } else {
                userStatus.setText("Offline");
                userStatus.setTextColor(itemView.getContext().getColor(android.R.color.darker_gray));
                onlineIndicator.setVisibility(View.GONE);
            }

            // Last seen time (you can add this field to User model later)
            userStatus.setText(isOnline ? "Online" : "Last seen recently");

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onUserClick(user);
                }
            });
        }
    }

    public interface OnUserClickListener {
        void onUserClick(User user);
    }
}