package example.com.model

import kotlinx.serialization.Serializable

@Serializable
data class Classroom (
    val id : Int,
    val name : String,
    val code : String,
    val description : String,
    val url : String,
    val semester :Int,
    val year :Int
)
@Serializable
data class CreateClassroomRequest (
    val name : String,
    val code : String? = null,
    val description : String,
    val url : String,
    val semester :Int,
    val year :Int
    )
@Serializable
data class UpdateClassroomRequest (
    val name : String? = null,
    val code : String? = null,
    val description : String? = null,
    val url : String? = null,
    val semester :Int? = null,
    val year :Int? = null
)
@Serializable
data class Section(
    val id: Int,
    val name: String,
    val classroomId: Int
)
@Serializable
data class GroupEntity(
    val id: Int,
    val name: String,
    val sectId: Int,
    val sectName:String? = null,
)


