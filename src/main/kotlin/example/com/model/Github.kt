package example.com.model

import kotlinx.serialization.Serializable


@Serializable
data class Commit(
    val id: String,
    val message: String
)

@Serializable
data class Repository(
    val name: String,
    val full_name: String
)
@Serializable
data class Submitter(
    val type: String,
    val id: Int,
)
@Serializable
data class SubmitterGithub(
    val type: String,
    val id: Int,
    val repo:String,
    val classroomName:String
)
data class ParseSubmitter(
    val course: String,
    val labName:String,
    val subAssignmentNumber:Int

)


@Serializable
data class PushEventPayload(
    val repository: Repository,
    val commits: List<Commit>
)