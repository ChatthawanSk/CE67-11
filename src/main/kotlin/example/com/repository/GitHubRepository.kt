package example.com.repository
import example.com.*
import example.com.model.Submitter
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class GitHubRepository {
    fun isStudentInDb(repoName: String): Submitter? {
        return transaction {
            // Check in the Groups table first
            val groupId = Groups.select { Groups.name eq repoName }
                .mapNotNull { it[Groups.id] }
                .singleOrNull()?.value

            if (groupId != null) {
                return@transaction Submitter("group", groupId) // If found in Groups, return a Submitter with type "Group"
            }

            // If not found in Groups, check in the Students table
            val studentId = Students.select { Students.studentId eq repoName }
                .mapNotNull { it[Students.credentialsId] }
                .singleOrNull()?.value

            return@transaction if (studentId != null) {
                Submitter("student", studentId)
            } else {
                null
            }
        }
    }
}