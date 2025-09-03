import kotlinx.serialization.Serializable

@Serializable
data class ProfileData(
    val id: String,
    val name: String,
    val email: String,
    val mobile: String? = null,
    val address: String? = null,
    val profile_image_url: String? = null,
    val profile_complete: Boolean = false
)
