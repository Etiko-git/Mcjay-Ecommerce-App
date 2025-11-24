import kotlinx.serialization.Serializable

@Serializable
data class Admin(
    val id: String? = null,
    val username: String,
    val password: String,
    val email: String? = null,
    val created_at: String? = null
)