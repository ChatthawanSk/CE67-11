package example.com.model

import kotlinx.serialization.Serializable

@Serializable
data class Assignment (
    val id: Int,
    val title: String,
    val description: String ,
    val classroomId: Int,
    val isGroup:Boolean,
    val score: Int
)
@Serializable
data class CreateAssignment (
    val title: String,
    val description: String ,
    val classroomId:Int,
    val isGroup:Boolean,
    val score: Int,
)
@Serializable
data class CreateAssignmentRequest(
    val createAssignment: CreateAssignment,
    val pdfFiles: List<ByteArray>? = null
)
@Serializable
data class AssignmentPermissionRequest(
    val assignmentId: Int,
    val sectId: Int,
    val dueDate: String,
    val isVisible: Boolean
)
@Serializable
data class CreateGroupRequest(
    val name: String,
    val sectId: Int,
)
@Serializable
data class FullSubAssignmentRequest(
    val subAssignments: List<SubAssignmentRequest>,
    val csvFiles: List<ByteArray>,
    val configFiles: List<ByteArray>,
    val inputFiles: List<ByteArray>,
    val inputList: List<Int>
)
@Serializable
data class SubAssignmentRequest(
    val subAssignmentNumber: Int,
    val description: String,
    val assignmentId: Int,
    val score: Int,
    val url: String
)
@Serializable
data class SubAssignment(
    val id: Int?,
    val number: Int?,
    val description: String?,
    val score: Int?,
    val url: String?
)
@Serializable
data class AssignmentWithInformation(
    val assignment: Assignment,
    val subAssignments: List<SubAssignment>,
    val assignmentPermission: List<AssignmentPermissionData>
)

@Serializable
data class AssignmentPermissionData(
    val assignmentId: Int? = null,
    val sectId: Int?= null,
    val sectName: String?= null,
    val dueDate: String?= null,
    val isVisible: Boolean?= null
)
@Serializable
data class SubAssignmentScore(
    val subAssignmentScoreId: Int,
    val subAssignmentId: Int,
    val subAssignmentNumber: Int,
    val subAssignmentDescription: String,
    val subAssignmentScore: Int?, // Nullable for missing scores
    val status: SubAssignmentStatus?= null
)
@Serializable
data class AssignmentWithScores(
    val assignmentId: Int,
    val assignmentTitle: String,
    val assignmentDescription: String,
    val score: Int,
    val subAssignments: List<SubAssignmentScore>, // List of sub-assignments with scores
    val totalScore: Int // Total score from sub-assignments
)
@Serializable
data class FileData(
    val id: Int,
    val fileContent: ByteArray
)
@Serializable
data class StudentScoreRow(
    val studentName: String,
    val studentId: String,
    val classroomName: String,
    val scores: Map<String, Double> // Assignment Name -> Score
)

@Serializable
data class SubAssignmentStatus(
    val status: String,
    val timestamp: String? = null,
)