package example.com.service

import example.com.model.*
import example.com.repository.UserRepository
import example.com.utils.PasswordUtils
import example.com.utils.PasswordUtils.verifyPassword
import java.util.Base64



class UserService(private val userRepository: UserRepository) {
    fun authenticate(username: String, password: String): User? {
        val user = userRepository.findByUsername(username) ?: return null

        val passwordHash = user.passwordHash ?: return null

        // Split the stored password hash into salt and hash components
        val (saltBase64, storedHash) = passwordHash.split(":", limit = 2)

        // Decode the Base64-encoded salt
        val salt = Base64.getDecoder().decode(saltBase64)

        // Verify the password using the extracted salt and stored hash
        return if (verifyPassword(password, storedHash, salt)) user else null
    }

    fun registerStudent(username: String, password: String, firstName: String, lastName: String, studentId: String, year: Int): Student {

        val combinedHash = generateSaltedHash(password)

        // Create the user in the repository with the combined hash
        val user = userRepository.createUser(username, combinedHash, "student", firstName, lastName)

        // Create the student record in the repository
        return userRepository.createStudent(user.id, studentId, year)
    }
    fun registerTeacher(username: String, password: String, firstName: String, lastName: String, email: String): Teacher {

        val combinedHash = generateSaltedHash(password)

        // Create the user in the repository with the combined hash
        val user = userRepository.createUser(username, combinedHash, "teacher", firstName, lastName)

        // Create the student record in the repository
        return userRepository.createTeacher(user.id,email)
    }

    fun getUserProfile(userId: Int): FullUserProfile? {
        // Retrieve user data
        val user = userRepository.findUserById(userId) ?: return null

        // First check if the user is a student
        val student = userRepository.findStudentByUserId(userId)
        if (student != null) {
            return FullUserProfile(userProfile = user, roleData = student)
        }

        // Then check if the user is a teacher
        val teacher = userRepository.findTeacherByUserId(userId)
        if (teacher != null) {
            return FullUserProfile(userProfile = user, roleData = teacher)
        }

        // If no student or teacher data is found, return the profile with null roleData
        return FullUserProfile(userProfile = user,null)
    }
    fun updateProfile(userId: Int ,updateRequest : UpdateUserProfileRequest): Boolean{
        // Call the repository method to update the user profile
        return userRepository.updateUserProfile(userId, updateRequest)
    }
    fun saveImage(userId : Int,fileBytes: ByteArray): Boolean {
        return if (userRepository.getImageData(userId) != null) {
            // Image exists, so update it
            userRepository.updateImageData(userId, fileBytes)
        } else {
            // Image does not exist, so save it
            userRepository.saveImageData(userId, fileBytes)
        }
    }
    fun getProfileImage(userId: Int): ByteArray? {
        return userRepository.getImageData(userId)
    }
    private fun generateSaltedHash(password: String): String {
        val salt = PasswordUtils.generateSalt()
        val hashedPassword = PasswordUtils.hashPassword(password, salt)
        return generateCombinedHash(salt, hashedPassword)
    }

    private fun generateCombinedHash(salt: ByteArray, hashedPassword: ByteArray): String {
        val saltBase64 = Base64.getEncoder().encodeToString(salt)
        val hashedPasswordBase64 = Base64.getEncoder().encodeToString(hashedPassword)
        return "$saltBase64:$hashedPasswordBase64"
    }
}