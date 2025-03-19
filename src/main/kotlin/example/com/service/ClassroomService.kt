package example.com.service

import example.com.model.*
import example.com.repository.AssignmentRepository
import example.com.repository.ClassroomRepository
import example.com.repository.UserRepository
import example.com.utils.RandomString
import io.ktor.http.content.*
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ClassroomService(private val classroomRepository: ClassroomRepository) {

    fun getClassroomsByUserId(userId: Int): List<Classroom> {
        val userRepository = UserRepository()

        val role = userRepository.getUserRole(userId)
        return if (role == "teacher"){
            classroomRepository.findClassroomsByTeacherId(userId)
        }else{classroomRepository.findClassroomsByStudentId(userId)}

    }



    fun createClassroom(request: CreateClassroomRequest, teacherId: Int): Boolean {
        return try {
            val (name, code, description, url, semester, year) = request

            if (code == null) {
                val finalCode = RandomString.generateRandomString(6)
                classroomRepository.createClassroom(name, finalCode, description, url, semester, year, teacherId)
            }
            else classroomRepository.createClassroom(name, code, description, url, semester, year, teacherId)
            // Call the repository method to create the classroom

            true
        } catch (e: Exception) {
            println("Error creating classroom: ${e.message}")
            false
        }
    }
    fun createGroup(request: CreateGroupRequest): Boolean{
        val (name, sectId) = request
        return try {
            classroomRepository.createGroup(name,sectId)
            true
        }
        catch (e : Exception) {
            false
        }
    }

    fun createAssignmentPermission(request: AssignmentPermissionRequest): Boolean {
        return try {

            val (assignmentId, sectId, dueDate, isVisible) = request
            val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
            val date = LocalDate.parse(dueDate,formatter)
            classroomRepository.createAssignmentPermission(assignmentId, sectId, date, isVisible)

            true
        } catch (e: Exception) {
            println("Error creating AssignmentPermission: ${e.message}")
            false
        }
    }

    fun updateClassroom(id: Int, request: UpdateClassroomRequest): Boolean {
        return try {
            val (name, code, description, url, semester, year) = request

            classroomRepository.updateClassroom(id, name, code, description , url, semester, year)

            true
        } catch (e: Exception) {
            false
        }
    }


    fun createSection(id: Int, name: String): Boolean {
        return try {
            classroomRepository.createSection(id, name)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getSectionsByClassroomId(classroomId: Int): List<Section> {
        return classroomRepository.findSectionsByClassroomId(classroomId)
    }

    suspend fun parseMultipartData(multipart: MultiPartData): CreateAssignmentRequest? {
        var createAssignment: CreateAssignment? = null
        val pdfFiles = mutableListOf<ByteArray>()

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    when (part.name) {
                        "jsonData" -> {
                            val jsonObject = Json.parseToJsonElement(part.value).jsonObject
                            val title = jsonObject["title"]?.jsonPrimitive?.content
                            val description = jsonObject["description"]?.jsonPrimitive?.content
                            val classroomId = jsonObject["classroomId"]?.jsonPrimitive?.int
                            val isGroup = jsonObject["group"]?.jsonPrimitive?.boolean
                            val score = jsonObject["score"]?.jsonPrimitive?.int

                            if (title != null && description != null  && classroomId != null && isGroup != null && score!= null) {
                                createAssignment = CreateAssignment(title, description, classroomId,isGroup,score)
                            }
                        }
                    }
                }

                is PartData.FileItem -> {
                    if (part.name == "pdfFile") {
                        val pdfBytes = part.streamProvider().readBytes()
                        pdfFiles.add(pdfBytes)
                    }
                }

                else -> Unit
            }
            part.dispose()
        }

        return if (createAssignment != null) {
            CreateAssignmentRequest(createAssignment!!, pdfFiles)
        } else {
            null
        }
    }
    suspend fun parseMultipartSubAssignmentData(multipart: MultiPartData): FullSubAssignmentRequest? {
        val subAssignmentRequests = mutableListOf<SubAssignmentRequest>()
        val csvFiles = mutableListOf<ByteArray>()
        val configFiles = mutableListOf<ByteArray>()
        val inputFiles = mutableListOf<ByteArray>()
        val inputList = mutableListOf<Int>()

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    if (part.name == "jsonData") {
                        try {
                            // Parse each JSON object from the part value
                            val jsonArray = Json.parseToJsonElement(part.value).jsonArray
                            jsonArray.forEach { jsonElement ->
                                val jsonObject = jsonElement.jsonObject
                                val subAssignmentNumber = jsonObject["subAssignmentNumber"]?.jsonPrimitive?.intOrNull
                                val description = jsonObject["description"]?.jsonPrimitive?.contentOrNull
                                val assignmentId = jsonObject["assignmentId"]?.jsonPrimitive?.intOrNull
                                val score = jsonObject["score"]?.jsonPrimitive?.intOrNull
                                val url = jsonObject["url"]?.jsonPrimitive?.jsonPrimitive?.contentOrNull

                                if (subAssignmentNumber != null && description != null && assignmentId != null && score != null && url != null) {
                                    val subAssignmentRequest = SubAssignmentRequest(
                                        subAssignmentNumber,
                                        description,
                                        assignmentId,
                                        score,
                                        url
                                    )
                                    subAssignmentRequests.add(subAssignmentRequest)
                                } else {
                                    // Log or handle missing fields here
                                    println("Missing fields in jsonData")
                                }
                            }
                        } catch (e: Exception) {
                            // Log parsing error
                            println("Error parsing jsonData: ${e.message}")
                        }
                    }
                    if (part.name == "inputList") {
                        inputList.addAll(part.value.split(",").mapNotNull { it.toIntOrNull() })
                        println(inputList)
                    }
                }

                is PartData.FileItem -> {
                    if (part.name == "csvFile") {
                        // Read bytes for each CSV file
                        csvFiles.add(part.streamProvider().readBytes())
                    }
                    if (part.name == "configFile") {
                        // Read bytes for each CSV file
                        configFiles.add(part.streamProvider().readBytes())
                    }
                    if (part.name == "inputFile") {
                        // Read bytes for each CSV file
                        inputFiles.add(part.streamProvider().readBytes())
                    }
                }
                else -> Unit
            }
            part.dispose()
        }

        return if (subAssignmentRequests.isNotEmpty() && csvFiles.isNotEmpty() && configFiles.isNotEmpty()) {
            FullSubAssignmentRequest(subAssignmentRequests, csvFiles,configFiles,inputFiles,inputList)
        } else {
            null
        }
    }

    fun createAssignment(request: CreateAssignmentRequest): Boolean {
        return try {
            val (title, description,classroomId,isGroup,score) = request.createAssignment
            val pdfFile = request.pdfFiles
            val assignmentId = classroomRepository.createAssignment(classroomId, title, description,isGroup,score)
            if (pdfFile != null)
            {
                classroomRepository.savePdfFile(assignmentId, pdfFile)
            }

            true
        } catch (e: Exception) {
            false
        }
    }
    fun createSubAssignment(request: FullSubAssignmentRequest): Boolean {
        return try {
            val subAssignmentId = classroomRepository.createSubAssignments(request.subAssignments)
            classroomRepository.saveTestScriptFiles(subAssignmentId, request.csvFiles) // Pass the list of CSV files
            classroomRepository.saveConfigFiles(subAssignmentId, request.configFiles)
            println(request.inputList)
            if(request.inputFiles.isNotEmpty() && request.inputList.isNotEmpty()){
                val filteredSubAssignmentIds = request.inputList.mapNotNull { index ->
                    subAssignmentId.getOrNull(index)
                }
                classroomRepository.saveInputFiles(filteredSubAssignmentIds, request.inputFiles)
            }
            true
        }catch (e: Exception) {
            false
        }
    }
    fun getAssignmentByClassroomId(classroomId: Int):List<AssignmentWithInformation>{
        val assignmentsWithSubAssignments = classroomRepository.getAssignmentsWithInformationByClassroom(classroomId)

        // Directly return the result from the query, which includes assignments and their information
        return assignmentsWithSubAssignments

    }
    fun getListOfPdfInAssignment(assignmentId:Int):List<Int>{
        return  classroomRepository.getPdfFileIdsByAssignmentId(assignmentId)
    }
    fun getPdfFile(pdfId:Int):ByteArray?{
        return  classroomRepository.getPdfFileById(pdfId)
    }
    fun getTestScriptBySubAssignment(subAssignmentId:Int):ByteArray?{
        return  classroomRepository.getTestScriptFileBySubAssignmentId(subAssignmentId)
    }
    fun getTestScriptByAssignment(assignmentId:Int):List<FileData>?{
        return  classroomRepository.getTestScriptFileByAssignmentId(assignmentId)
    }
    fun getConfigBySubAssignment(subAssignmentId:Int):ByteArray?{
        return  classroomRepository.getConfigFileBySubAssignmentId(subAssignmentId)
    }
    fun getConfigByAssignment(assignmentId:Int):List<FileData>?{
        return  classroomRepository.getConfigFileByAssignmentId(assignmentId)
    }
    fun joinClassroom(code:String,section:String,studentId:Int):Boolean{
        val classroomId = classroomRepository.checkCodeToClassroom(code)
        return if(classroomId != null) {
            classroomRepository.createSectionStudent(classroomId,section,studentId)
        } else {
            false
        }

    }
    fun getGroupBySect(sectId: Int): List<GroupEntity>? {
        return try {
            classroomRepository.getGroupsBySectId(sectId)
        } catch (e: Exception) {
            // Handle the exception (log, return null or empty list, etc.)
            println("Error occurred while fetching groups by sect ID: ${e.message}")
            emptyList()
        }
    }
    fun joinGroup(groupId:Int,studentId:Int): Boolean {
        val joinGroup = classroomRepository.joinGroup(groupId, studentId)
        return joinGroup
    }
    fun getGroupByUser(studentId: Int): List<GroupEntity>? {
        return try {
            classroomRepository.getGroupsByUserId(studentId)
        } catch (e: Exception) {
            // Handle the exception (log, return null or empty list, etc.)
            println("Error occurred while fetching groups by sect ID: ${e.message}")
            emptyList()
        }
    }
    fun getOutputFileBySubAssignmentId(studentId: Int,subAssignmentId: Int): ByteArray? {
        return try {
            classroomRepository.getFileBySubAssignmentId(subAssignmentId,studentId)
        } catch (e: Exception) {
            println("Error occurred while fetching groups by sect ID: ${e.message}")
            null
        }
    }
    fun getAllStudentScoresAsJsonByClassroomId(classroomId: Int):String?{
        val assignmentRepository = AssignmentRepository()
        return try {
            assignmentRepository.getAllStudentScoresAsJsonByClassroomId(classroomId)
        } catch (e: Exception) {
            println("Error occurred while fetching groups by sect ID: ${e.message}")
            null
        }
    }
}


