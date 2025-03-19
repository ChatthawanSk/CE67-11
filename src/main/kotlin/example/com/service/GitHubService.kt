package example.com.service

import example.com.model.PushEventPayload
import example.com.model.SubmitterGithub
import example.com.repository.GitHubRepository
import kotlinx.serialization.json.Json

class GitHubService(private val gitHubRepository: GitHubRepository){
    fun verifyStudent(payload: String): SubmitterGithub? {
        try {
            // Deserialize the payload into a PushEventPayload object
            val json = Json { ignoreUnknownKeys = true }
            val pushEvent = json.decodeFromString<PushEventPayload>(payload)
            // Extract the repository full name (e.g., "username/repo")
            val repo = pushEvent.repository.full_name
            println(repo)
//            MAD-2566-1/lab2-1-ChatthawanSk
            val classroomName = repo.substringBeforeLast("-")
            val labName = repo.split("-")
            println(classroomName)
            // Check if any commit message contains the word "submit" (case-insensitive)
            val hasSubmitMessage = pushEvent.commits.any { it.message.contains("submit", ignoreCase = true) }
            // If a commit contains "submit", check if the repo is in the database
            if (hasSubmitMessage) {
                val submitterInfo = gitHubRepository.isStudentInDb(labName.last()) //
                if (submitterInfo != null) {
                    // Print the submitter information (if needed for debugging)
//                    println(submitterInfo)
                    val submitterGithub = SubmitterGithub(submitterInfo.type, submitterInfo.id,repo,classroomName)
                    return submitterGithub
                }
            }
        } catch (e: Exception) {
            // Log the exception or handle the error (e.g., print stack trace for debugging)
            println("Error verifying student: ${e.message}")
            e.printStackTrace()
        }

        // Return null if no valid submitter is found or an error occurs
        return null
    }
}