package example.com.repository
import example.com.model.*
import example.com.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction


// Repository Implementation
class UserRepository {
    fun findByUsername(username: String): User? {
        return transaction {
            Users.select { Users.username eq username }
                .mapNotNull { row ->
                    User(
                        id = row[Users.id].value,
                        username = row[Users.username],
                        passwordHash = row[Users.passwordHash],
                        firstName = row[Users.firstName],
                        lastName = row[Users.lastName],
                        role = row[Users.role]
                    )
                }
                .singleOrNull()
        }
    }

    fun findUserById(userId: Int): UserProfile? = transaction {
        Users.select { Users.id eq userId }
            .mapNotNull { row ->
                UserProfile(
                    id = row[Users.id].value,
                    username = row[Users.username],
                    firstName = row[Users.firstName],
                    lastName = row[Users.lastName],
                    role = row[Users.role]
                )
            }
            .singleOrNull()
    }

    fun findTeacherByUserId(userId: Int): Teacher? = transaction {
        Teachers.select { Teachers.credentialsId eq userId }
            .map { Teacher(it[Teachers.credentialsId].value, it[Teachers.email]) }
            .singleOrNull()
    }

    fun findStudentByUserId(userId: Int): Student? = transaction {
        Students.select { Students.credentialsId eq userId }
            .map {
                Student(
                    it[Students.credentialsId].value,
                    it[Students.studentId],
                    it[Students.year],
                )
            }
            .singleOrNull()
    }

    fun createUser(username: String, passwordHash: String, role: String, firstName: String, lastName: String): User {
        return transaction {
            val id = Users.insertAndGetId {
                it[Users.username] = username
                it[Users.passwordHash] = passwordHash
                it[Users.role] = role
                it[Users.firstName] = firstName
                it[Users.lastName] = lastName
            }
            User(id.value, username, passwordHash, role, firstName, lastName)
        }
    }


    fun createStudent(credentialsId: Int, studentId: String, year: Int): Student {
        return transaction {
            Students.insert {
                it[Students.credentialsId] = credentialsId // Assuming `credentialsId` is an integer
                it[Students.studentId] = studentId
                it[Students.year] = year
            }
            Student(credentialsId, studentId, year)
        }
    }


    fun createTeacher(credentialsId: Int, email: String): Teacher {
        return transaction {
            Teachers.insert {
                it[Teachers.credentialsId] = credentialsId // Assuming `credentialsId` is an integer
                it[Teachers.email] = email
            }
            Teacher(credentialsId, email)
        }
    }

    fun getUserRole(userId: Int): String? {
        return transaction {
            Users
                .slice(Users.role)
                .select { Users.id eq userId }
                .map { it[Users.role] }
                .singleOrNull()
        }
    }
    fun updateUserProfile(userId: Int, updateRequest: UpdateUserProfileRequest): Boolean {
        return transaction {
            // Check if the user exists
            val user = Users.select { Users.id eq userId }.singleOrNull()

            if (user != null) {
                if (updateRequest.firstName != null || updateRequest.lastName != null) {
                    Users.update({ Users.id eq userId }) {
                        updateRequest.firstName?.let { firstName -> it[Users.firstName] = firstName }
                        updateRequest.lastName?.let { lastName -> it[Users.lastName] = lastName }
                    }
                }
                if (user[Users.role].lowercase() == "student") {
                    if (updateRequest.studentId != null || updateRequest.year != null) {
                        Students.update({ Students.credentialsId eq userId }) {
                            updateRequest.studentId?.let { id -> it[studentId] = id }
                            updateRequest.year?.let { year -> it[Students.year] = year }
                        }
                    }
                } else if (user[Users.role].lowercase() == "teacher") {
                    if (updateRequest.email != null) {
                        Teachers.update({ Teachers.credentialsId eq userId }) {
                            updateRequest.email.let { email -> it[Teachers.email] = email }
                        }
                    }
                }
                true
            } else {
                false
            }
        }
    }

    fun saveImageData(userId: Int, fileBytes: ByteArray): Boolean {
        return try {
            transaction {
                // Insert the image data into the database
                ProfileImages.insert {
                    it[this.userId] = userId
                    it[this.imageData] = ExposedBlob(fileBytes)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getImageData(userId: Int): ByteArray? {
        return transaction {
            ProfileImages.select { ProfileImages.userId eq userId }
                .mapNotNull { it[ProfileImages.imageData].bytes }
                .singleOrNull()
        }
    }

    fun updateImageData(userId: Int, imageBytes: ByteArray): Boolean {
        return try {
            transaction {
                ProfileImages.update({ ProfileImages.userId eq userId }) {
                    it[this.imageData] = ExposedBlob(imageBytes)
                }
                true
            }
        }
        catch (e: Exception) {
            false
        }
    }
}

