package example.com.controller


import example.com.model.*
import example.com.repository.AssignmentRepository
import example.com.service.ClassroomService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*


fun Route.classroomRoutes(classroomService: ClassroomService) {
     authenticate("auth-jwt") {
         get("/getClassroomByUserId") {
             val principal = call.principal<UserPrincipal>()
             val userId = principal?.userId
             if (userId != null) {
                 try {
                     val result = classroomService.getClassroomsByUserId(userId)
                     if (result.isNotEmpty()) {
                         call.respond(HttpStatusCode.OK, result)
                     } else {
                         call.respond(HttpStatusCode.NotFound, "Classroom not found")
                     }
                 } catch (e: Exception) {
                     call.respond(HttpStatusCode.BadRequest, "Classroom creation failed: ${e.message}")
                 }
             }
         }


         post("/createClassroom") {
             val request = call.receive<CreateClassroomRequest>()
             val principal = call.principal<UserPrincipal>()
             val teacherId = principal?.userId
             if (teacherId != null) {
                 try {
                     val result = classroomService.createClassroom(request, teacherId)
                     if (result) {
                         val response = ApiResponse(
                             success = true,
                             message = "Classroom create success"
                         )

                         call.respond(HttpStatusCode.OK, response)
                     } else {
                         call.respond(HttpStatusCode.BadRequest, "Classroom creation failed l0ol")
                     }
                 } catch (e: Exception) {
                     call.respond(HttpStatusCode.BadRequest, "Classroom creation failed: ${e.message}")
                 }
             }
         }



         put("/updateClassroom/{id}") {
             val id = call.parameters["id"]?.toIntOrNull()
             val request = call.receive<UpdateClassroomRequest>()
             if (id != null) {
                 try {
                     val result = classroomService.updateClassroom(id, request)
                     if (result) {
                         call.respond(HttpStatusCode.OK, "Classroom update successfully")
                     } else {
                         call.respond(HttpStatusCode.BadRequest, "Classroom update failed")
                     }
                 } catch (e: Exception) {
                     call.respond(HttpStatusCode.BadRequest, "Classroom update failed: ${e.message}")
                 }
             }

         }


         get("/getSectionInClassroom/{classroomId}") {
             val id = call.parameters["classroomId"]?.toIntOrNull()
             if (id != null) {
                 try {

                     val result = classroomService.getSectionsByClassroomId(id)
                     if (result.isNotEmpty()) {
                         call.respond(HttpStatusCode.OK, result)
                     } else {
                         call.respond(HttpStatusCode.BadRequest, "Classroom not found")
                     }
                 } catch (e: Exception) {
                     call.respond(HttpStatusCode.BadRequest, "failed: ${e.message}")
                 }
             }
         }

         post("/createSection") {
             // Receive the request body as a JSON string
             val jsonString = call.receiveText()

             try {
                 val jsonElement = Json.parseToJsonElement(jsonString).jsonObject

                 val id = jsonElement["classroomId"]?.jsonPrimitive?.int
                 val name = jsonElement["name"]?.jsonPrimitive?.content

                 if (id != null && name != null) {
                     val result = classroomService.createSection(id, name)

                     if (result) {
                         val response = ApiResponse(success = true, message = "Section created successfully")
                         call.respond(
                             HttpStatusCode.Created,
                             response
                         )
                     } else {
                         // Respond with failure message
                         call.respond(HttpStatusCode.BadRequest, "Section creation failed")
                     }
                 } else {
                     // Respond with a bad request message if 'id' or 'name' are missing
                     call.respond(HttpStatusCode.BadRequest, "Invalid input: 'classroomId' and 'name' are required")
                 }
             } catch (e: Exception) {
                 // Respond with an error message if an exception occurs
                 call.respond(HttpStatusCode.InternalServerError, "Error processing request: ${e.message}")
             }
         }

         post("/createAssignment") {
             val multipart = call.receiveMultipart()
             val createAssignmentRequest = classroomService.parseMultipartData(multipart)

             if (createAssignmentRequest == null) {
                 call.respond(HttpStatusCode.BadRequest, "Missing form data or PDF file")
                 return@post
             }

             try {
                 val result = classroomService.createAssignment(createAssignmentRequest)
                 val response = ApiResponse(success = true, message = "Assignment created successfully")
                 if (result) {
                     call.respond(HttpStatusCode.Created, response)
                 } else {
                     call.respond(HttpStatusCode.BadRequest, "Assignment creation failed")
                 }
             } catch (e: Exception) {
                 call.respond(HttpStatusCode.InternalServerError, "Assignment creation failed: ${e.message}")
             }
         }

         post("/createSubAssignment") {
             val multipart = call.receiveMultipart()
             val createSubAssignmentRequest = classroomService.parseMultipartSubAssignmentData(multipart)

             if (createSubAssignmentRequest == null) {
                 call.respond(HttpStatusCode.BadRequest, "Missing FormData")
                 return@post
             }
             try {
                 // Call the service to create a sub-assignment
                 val isCreated = classroomService.createSubAssignment(createSubAssignmentRequest)

                 if (isCreated) {
                     val response = ApiResponse(
                         success = true,
                         message = "sub-assignment create success"
                     )
                     call.respond(HttpStatusCode.Created, response)
                 } else {
                     call.respond(HttpStatusCode.InternalServerError, "Failed to create sub-assignment.")
                 }
             } catch (e: Exception) {
                 call.respond(HttpStatusCode.BadRequest, "Failed to create sub-assignment: ${e.message}")
             }
         }

         post("/createAssignmentPermissions") {
             val request = call.receive<AssignmentPermissionRequest>()
             try {
                 val createAssignmentPermission = classroomService.createAssignmentPermission(request)

                 if (createAssignmentPermission) {
                     val response = ApiResponse(
                         success = true,
                         message = "AssignmentPermission create success"
                     )
                     call.respond(HttpStatusCode.Created, response)
                 } else {
                     call.respond(HttpStatusCode.InternalServerError, "Failed to create AssignmentPermission.")
                 }
             } catch (e: Exception) {
                 call.respond(HttpStatusCode.BadRequest, "Failed to create AssignmentPermission: ${e.message}")
             }
         }

         get("/getAssignmentByClassroomId/{classroomId}") {
             val classroomId = call.parameters["classroomId"]?.toIntOrNull()

             if (classroomId == null) {
                 call.respond(HttpStatusCode.BadRequest, "Invalid classroom ID")
                 return@get
             }
             try {
                 val result = classroomService.getAssignmentByClassroomId(classroomId)
                 if (result.isNotEmpty()) {
                     call.respond(HttpStatusCode.OK, result)
                 } else {
                     call.respond(HttpStatusCode.BadRequest, "Assignment not found")
                 }
             } catch (e: Exception) {
                 call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
             }
         }
         get("/getListOfPdfId/{AssignmentId}") {
             val assignmentId = call.parameters["AssignmentId"]?.toIntOrNull()

             if (assignmentId == null) {
                 call.respond(HttpStatusCode.BadRequest, "Invalid Assignment ID")
                 return@get
             }
             try {
                 val result = classroomService.getListOfPdfInAssignment(assignmentId)
                 if (result.isNotEmpty()) {
                     call.respond(HttpStatusCode.Created, result)
                 } else {
                     call.respond(HttpStatusCode.BadRequest, "Assignment not found")
                 }
             } catch (e: Exception) {
                 call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
             }
         }
         get("/getPdfFileById/{pdfId}") {
             val pdfId = call.parameters["pdfId"]?.toIntOrNull()

             if (pdfId == null) {
                 call.respond(HttpStatusCode.BadRequest, "Invalid Pdf ID")
                 return@get
             }
             try {
                 val result = classroomService.getPdfFile(pdfId)
                 if (result != null) {
                     call.respond(HttpStatusCode.OK, result)
                 } else {
                     call.respond(HttpStatusCode.BadRequest, "Pdf not found")
                 }
             } catch (e: Exception) {
                 call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
             }

         }
         get("/getTestScriptBySubAssignment/{subAssignmentId}") {
             val subAssignmentID = call.parameters["subAssignmentId"]?.toIntOrNull()

             if (subAssignmentID == null) {
                 call.respond(HttpStatusCode.BadRequest, "Invalid subAssignment ID")
                 return@get
             }
             try {
                 val result = classroomService.getTestScriptBySubAssignment(subAssignmentID)
                 if (result != null) {
                     call.respond(HttpStatusCode.OK, result)
                 } else {
                     call.respond(HttpStatusCode.BadRequest, "file not found")
                 }
             } catch (e: Exception) {
                 call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
             }

         }
         get("/getTestScriptByAssignment/{assignmentId}") {
             val assignmentID = call.parameters["assignmentId"]?.toIntOrNull()

             if (assignmentID == null) {
                 call.respond(HttpStatusCode.BadRequest, "Invalid Assignment ID")
                 return@get
             }
             try {
                 val result = classroomService.getTestScriptByAssignment(assignmentID)
                 if (result != null) {
                     call.respond(HttpStatusCode.OK, result)
                 } else {
                     call.respond(HttpStatusCode.BadRequest, "files not found")
                 }
             } catch (e: Exception) {
                 call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
             }

         }
         get("/getConfigBySubAssignment/{subAssignmentId}") {
             val subAssignmentID = call.parameters["subAssignmentId"]?.toIntOrNull()

             if (subAssignmentID == null) {
                 call.respond(HttpStatusCode.BadRequest, "Invalid subAssignment ID")
                 return@get
             }
             try {
                 val result = classroomService.getConfigBySubAssignment(subAssignmentID)
                 if (result != null) {
                     call.respond(HttpStatusCode.OK, result)
                 } else {
                     call.respond(HttpStatusCode.BadRequest, "file not found")
                 }
             } catch (e: Exception) {
                 call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
             }

         }
         get("/getConfigByAssignment/{assignmentId}") {
             val assignmentID = call.parameters["assignmentId"]?.toIntOrNull()

             if (assignmentID == null) {
                 call.respond(HttpStatusCode.BadRequest, "Invalid Assignment ID")
                 return@get
             }
             try {
                 val result = classroomService.getConfigByAssignment(assignmentID)
                 if (result != null) {
                     call.respond(HttpStatusCode.OK, result)
                 } else {
                     call.respond(HttpStatusCode.BadRequest, "files not found")
                 }
             } catch (e: Exception) {
                 call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
             }

         }
         post("/joinClassroom") {
             val principal = call.principal<UserPrincipal>()
             val studentId = principal?.userId

             if (studentId != null) {
                 try {
                     val jsonElement = call.receive<JsonObject>()
                     val code = jsonElement["code"]?.jsonPrimitive?.content
                     val section = jsonElement["section"]?.jsonPrimitive?.content

                     if (code != null && section != null) {
                         val result = classroomService.joinClassroom(code, section, studentId)
                         if (result) {
                             call.respond(HttpStatusCode.OK, ApiResponse(success = true, message = "Classroom join success"))
                         } else {
                             call.respond(HttpStatusCode.BadRequest, "Classroom join failed")
                         }
                     } else {
                         call.respond(HttpStatusCode.BadRequest, "Invalid data")
                     }
                 } catch (e: ContentTransformationException) {
                     // Catch any issues with parsing JSON and respond with 400
                     call.respond(HttpStatusCode.BadRequest, "Malformed request body: ${e.message}")
                 } catch (e: Exception) {
                     // Catch any other exceptions
                     call.respond(HttpStatusCode.BadRequest, "Classroom join failed: ${e.message}")
                 }
             } else {
                 call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
             }
         }
         post("/createGroup") {
             val request = call.receive<CreateGroupRequest>()
             try {
                 val createGroup = classroomService.createGroup(request)
                 if (createGroup) {
                     val response = ApiResponse(
                         success = true,
                         message = "Group create success"
                     )
                     call.respond(HttpStatusCode.Created, response)
                 } else {
                     call.respond(HttpStatusCode.InternalServerError, "Failed to create Group.")
                 }
             } catch (e: Exception) {
                 call.respond(HttpStatusCode.BadRequest, "Failed to create Group: ${e.message}")
             }
         }
         post("/joinGroup") {
             val principal = call.principal<UserPrincipal>()
             val studentId = principal?.userId

             if (studentId != null) {
                 try {
                     val jsonElement = call.receive<JsonObject>()
                     val groupId = jsonElement["groupId"]?.jsonPrimitive?.int

                     if (groupId!=null) {
                         val result = classroomService.joinGroup(groupId, studentId)
                         if (result) {
                             call.respond(HttpStatusCode.OK, ApiResponse(success = true, message = "Classroom join success"))
                         } else {
                             call.respond(HttpStatusCode.BadRequest, "Classroom join failed")
                         }
                     } else {
                         call.respond(HttpStatusCode.BadRequest, "Invalid data")
                     }
                 } catch (e: ContentTransformationException) {
                     call.respond(HttpStatusCode.BadRequest, "Malformed request body: ${e.message}")
                 } catch (e: Exception) {
                     call.respond(HttpStatusCode.BadRequest, "Classroom join failed: ${e.message}")
                 }
             } else {
                 call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
             }
         }
         get("/groupBySect/{sectId}") {
             val sectId = call.parameters["sectId"]?.toIntOrNull()
             try {
                 if (sectId != null) {
                     val groupList = classroomService.getGroupBySect(sectId)

                     if (!groupList.isNullOrEmpty()) {
                         call.respond(HttpStatusCode.OK, groupList)
                     } else {
                         val response = ApiResponse(
                             success = false,
                             message = "No groups found for the given section."
                         )
                         call.respond(HttpStatusCode.BadRequest, response)
                     }
                 } else {
                     val response = ApiResponse(
                         success = false,
                         message = "Section ID is required."
                     )
                     call.respond(HttpStatusCode.InternalServerError, response)
                 }
             } catch (e: Exception) {
                 call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
             }
         }

         get ("/GetScoreFileBySubAssignmentId/{subAssignmentId}"){
             val principal = call.principal<UserPrincipal>()
             val subAssignmentId = call.parameters["subAssignmentId"]?.toIntOrNull()
             val studentId = principal?.userId
             if (studentId != null && subAssignmentId != null) {
                 val fileByteA = classroomService.getOutputFileBySubAssignmentId(studentId,subAssignmentId)
                 if(fileByteA!=null){
                     call.respond(HttpStatusCode.OK,fileByteA)
                 }else{
                     val response = ApiResponse(
                         success = false,
                         message = "no file."
                     )
                     call.respond(HttpStatusCode.BadRequest, response)
                 }
             }else{
                 val response = ApiResponse(
                     success = false,
                     message = "no token. or subAssignmentId in parameter"
                 )
                 call.respond(HttpStatusCode.BadRequest, response)
             }
         }

         get("/groupByUser") {
             try {
                 val principal = call.principal<UserPrincipal>()
                 val studentId = principal?.userId
                 if (studentId != null) {
                     val groupList = classroomService.getGroupByUser(studentId)

                     if (!groupList.isNullOrEmpty()) {
                         call.respond(HttpStatusCode.OK, groupList)
                     } else {
                         val response = ApiResponse(
                             success = false,
                             message = "No groups found for you."
                         )
                         call.respond(HttpStatusCode.BadRequest, response)
                     }
                 }
             } catch (e: Exception) {
                 call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
             }
         }
         get ("/GetScore"){
             val principal = call.principal<UserPrincipal>()
             val studentId = principal?.userId
             val assignmentRepository = AssignmentRepository()
             if (studentId != null) {
                 val first = assignmentRepository.getAssignmentsWithScores(studentId)
                 call.respond(HttpStatusCode.OK, first)
             }else{
                 val response = ApiResponse(
                     success = false,
                     message = "no token."
                 )
                 call.respond(HttpStatusCode.BadRequest, response)
             }
         }
         get ("/GetScoreFile/{ScoreId}"){
             val scoreId = call.parameters["ScoreId"]?.toIntOrNull()
             val assignmentRepository = AssignmentRepository()
             if (scoreId != null) {
                 val first = assignmentRepository.getScoreOutputCsv(scoreId)
                 call.respond(HttpStatusCode.OK, first)
             }else{
                 val response = ApiResponse(
                     success = false,
                     message = "no parameter received."
                 )
                 call.respond(HttpStatusCode.BadRequest, response)
             }

         }
         get("/getAllStudentScoresByClassroomId/{classroomId}"){
             try {
                 val classroomId = call.parameters["classroomId"]?.toIntOrNull()
                 if (classroomId != null) {
                     val studentScores = classroomService.getAllStudentScoresAsJsonByClassroomId(classroomId)

                     if (studentScores != null) {
                         call.respond(HttpStatusCode.OK, studentScores)
                     } else {
                         val response = ApiResponse(
                             success = false,
                             message = "No Score found for you."
                         )
                         call.respond(HttpStatusCode.BadRequest, response)
                     }
                 }
             } catch (e: Exception) {
                 call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
             }
         }
     }
}

