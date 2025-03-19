package example.com.model


import RoleDataSerializer
import io.ktor.server.auth.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int,
    val username: String,
    val passwordHash: String?,
    val firstName: String ,
    val lastName: String,
    val role: String
)
@Serializable
data class UserProfile(
    val id: Int,
    val username: String,
    val firstName: String,
    val lastName: String,
    val role: String,
)
@Serializable
sealed class RoleData

@Serializable
data class FullUserProfile(
    val userProfile: UserProfile,
    @Serializable(RoleDataSerializer::class) val roleData: RoleData?
)

@Serializable
data class Teacher(
    val userId: Int,
    val email: String
) : RoleData()

@Serializable
data class Student(
    val userId: Int,
    val studentId: String,
    val year: Int,
) : RoleData()

data class UserPrincipal(
    val username: String,
    val userId: Int) : Principal

@Serializable
data class ApiResponse(
    val success: Boolean,
    val message: String
)
@Serializable
data class TokenResponse(
    val success: Boolean,
    val message: String,
    val token: String
)


@Serializable
data class UpdateUserProfileRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val studentId: String? = null,
    val year: Int? = null,
    val email: String? = null
)

@Serializable
data class RegisterStudentRequest(
    val username: String,
    val password: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val studentId: String,
    val year: Int,
)
@Serializable
data class RegisterTeacherRequest(
    val username: String,
    val password: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val email: String,
    )


@Serializable
data class LoginRequest(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String
)
