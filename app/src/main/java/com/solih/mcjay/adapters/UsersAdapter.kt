package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.solih.mcjay.R
import com.solih.mcjay.models.User

class UsersAdapter(
    private val users: List<User>,
    private val onDeleteClick: (User) -> Unit
) : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_admin, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivProfile: ImageView = itemView.findViewById(R.id.ivProfile)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
        private val tvCompletion: TextView = itemView.findViewById(R.id.tvCompletion)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)

        fun bind(user: User) {
            // Load profile image
            if (!user.profile_image.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(user.profile_image)
                    .placeholder(R.drawable.ic_profile)
                    .into(ivProfile)
            } else {
                ivProfile.setImageResource(R.drawable.ic_profile)
            }

            tvName.text = user.name ?: "No Name"
            tvUsername.text = "@${user.username}"
            tvEmail.text = user.email ?: "No Email"
            tvCompletion.text = "${user.getCompletionPercentage()}% Complete"

            btnDelete.setOnClickListener {
                onDeleteClick(user)
            }
        }
    }
}