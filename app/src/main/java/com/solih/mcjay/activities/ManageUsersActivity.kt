package com.solih.mcjay.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.adapters.UsersAdapter
import com.solih.mcjay.databinding.ActivityManageUsersBinding
import com.solih.mcjay.models.User
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class ManageUsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageUsersBinding
    private val supabase = SupabaseClientInstance.client
    private lateinit var adapter: UsersAdapter
    private val usersList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadUsers()
    }

    private fun setupRecyclerView() {
        adapter = UsersAdapter(usersList) { user ->
            showDeleteConfirmation(user)
        }
        binding.rvUsers.layoutManager = LinearLayoutManager(this)
        binding.rvUsers.adapter = adapter
    }

    private fun loadUsers() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.tvEmpty.visibility = android.view.View.GONE

        lifecycleScope.launch {
            try {
                val users = supabase.postgrest["users"]
                    .select()
                    .decodeList<User>()

                usersList.clear()
                usersList.addAll(users)

                runOnUiThread {
                    binding.tvTotalUsers.text = "${users.size} Users"
                    adapter.notifyDataSetChanged()
                    updateEmptyState()
                    binding.progressBar.visibility = android.view.View.GONE
                }

            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this@ManageUsersActivity, "Error loading users: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateEmptyState() {
        binding.tvEmpty.visibility = if (usersList.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showDeleteConfirmation(user: User) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete User")
            .setMessage("Are you sure you want to delete user '${user.name ?: user.username}'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteUser(user)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteUser(user: User) {
        lifecycleScope.launch {
            try {
                supabase.postgrest["users"]
                    .delete {
                        filter {
                            eq("id", user.id)
                        }
                    }

                runOnUiThread {
                    usersList.remove(user)
                    adapter.notifyDataSetChanged()
                    binding.tvTotalUsers.text = "${usersList.size} Users"
                    updateEmptyState()
                    Toast.makeText(this@ManageUsersActivity, "User deleted successfully", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ManageUsersActivity, "Error deleting user: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}