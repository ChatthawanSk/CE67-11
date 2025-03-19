package example.com.repository
import example.com.*
import example.com.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

class ClassroomRepository {
    fun createAssignmentPermission(assignmentId: Int, sectId: Int, dueDate: LocalDate, isVisible: Boolean): Boolean {

        return try {
            transaction {
                AssignmentPermissions.insert {
                    it[this.assignmentId] = assignmentId
                    it[this.sectId] = sectId
                    it[this.dueDate] = dueDate
                    it[this.isVisible] = isVisible
                }
            }
            true
        } catch (e: Exception) {
            println(e.message)
            false
        }

    }


    fun createClassroom(
        name: String, code: String, description: String, url: String, semester: Int, year: Int, teacherId: Int
    ): Boolean {
        return try {
            transaction {
                val classroomId = Classrooms.insertAndGetId {
                    it[this.name] = name
                    it[this.code] = code
                    it[this.description] = description
                    it[this.url] = url
                    it[this.semester] = semester
                    it[this.year] = year
                }
                ClassroomTeachers.insert {
                    it[this.classroomId] = classroomId.value
                    it[this.teacherId] = teacherId
                }
            }
            true
        } catch (e: Exception) {
            false
        }

    }

    fun updateClassroom(
        id: Int,
        name: String?,
        code: String?,
        description: String?,
        url: String?,
        semester: Int?,
        year: Int?
    ): Boolean {
        return try {
            transaction {
                Classrooms.update({ Classrooms.id eq id }) {
                    if (name != null) it[this.name] = name
                    if (code != null) it[this.code] = code
                    if (description != null) it[this.description] = description
                    if (url != null) it[this.url] = url
                    if (semester != null) it[this.semester] = semester
                    if (year != null) it[this.year] = year
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun checkCodeToClassroom(code: String): Int? {
        return try {
            transaction {
                // Select the classroom with the given code and return its ID
                Classrooms
                    .select { Classrooms.code eq code }
                    .singleOrNull()?.get(Classrooms.id)?.value // Get the classroomId from the record
            }
        } catch (e: Exception) {
            // Optionally log the exception
            null // Return null in case of an exception
        }
    }

    fun createSectionStudent(classroomId: Int, section: String, studentId: Int): Boolean {
        return try {
            val result = transaction {
                val sectionId = Sections
                    .select { (Sections.name eq section) and (Sections.classroomId eq classroomId) }
                    .singleOrNull()?.get(Sections.id)

                if (sectionId == null) {
                    return@transaction false  // Return false from the transaction block
                }

                SectionStudents.insert {
                    it[this.sectionId] = sectionId
                    it[this.classroomId] = classroomId
                    it[this.studentId] = studentId
                }
                true  // Return true if insertion is successful
            }
            result  // Return the result of the transaction
        } catch (e: Exception) {
            false  // Return false if an exception occurs
        }
    }

    fun createSection(id: Int, name: String): Boolean {
        return try {
            transaction {
                Sections.insert {
                    it[this.classroomId] = id
                    it[this.name] = name
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun findClassroomsByTeacherId(teacherId: Int): List<Classroom> = transaction {
        (Classrooms innerJoin ClassroomTeachers)
            .select { ClassroomTeachers.teacherId eq teacherId }
            .map { rowToClassroom(it) }
    }

    fun findClassroomsByStudentId(studentId: Int): List<Classroom> = transaction {
        (Classrooms innerJoin SectionStudents)
            .select { SectionStudents.studentId eq studentId }
            .map { rowToClassroom(it) }
    }

    fun findSectionsByClassroomId(classroomId: Int): List<Section> = transaction {
        Sections
            .select { Sections.classroomId eq classroomId }
            .map { rowToSection(it) }
    }

    fun findSectionById(sectionId: Int): Section? = transaction {
        Sections
            .select { Sections.id eq sectionId }
            .mapNotNull { rowToSection(it) }
            .singleOrNull()
    }

    fun createAssignment(
        classroomId: Int,
        title: String,
        description: String,
        isGroup: Boolean,
        score: Int
    ): Int {
        return transaction {
            Assignments.insertAndGetId {
                it[this.title] = title
                it[this.description] = description
                it[this.classroomId] = classroomId
                it[this.isGroup] = isGroup
                it[this.score] = score
            }.value
        }
    }

    fun createSubAssignments(subAssignments: List<SubAssignmentRequest>): List<Int> {
        return transaction {
            subAssignments.map { subAssignment ->
                SubAssignments.insertAndGetId {
                    it[subAssignmentNumber] = subAssignment.subAssignmentNumber
                    it[description] = subAssignment.description
                    it[assignmentId] = subAssignment.assignmentId
                    it[score] = subAssignment.score
                    it[url] = subAssignment.url
                }.value
            }
        }
    }

    fun saveTestScriptFiles(subAssignmentIds: List<Int>, csvFiles: List<ByteArray>) {
        transaction {
            // Ensure we have the same number of IDs and files
            if (subAssignmentIds.size != csvFiles.size) {
                throw IllegalArgumentException("Number of sub-assignment IDs must match number of CSV files")
            }

            for (i in subAssignmentIds.indices) {
                SubAssignmentTestScripts.insert {
                    it[this.subAssignmentId] = subAssignmentIds[i]
                    it[this.testScript] = ExposedBlob(csvFiles[i]) // Save ByteArray as an ExposedBlob
                }
            }
        }
    }
    fun saveConfigFiles(subAssignmentIds: List<Int>, csvFiles: List<ByteArray>) {
        transaction {
            // Ensure we have the same number of IDs and files
            if (subAssignmentIds.size != csvFiles.size) {
                throw IllegalArgumentException("Number of sub-assignment IDs must match number of CSV files")
            }

            for (i in subAssignmentIds.indices) {
                SubAssignmentConfig.insert {
                    it[this.subAssignmentId] = subAssignmentIds[i]
                    it[this.configFile] = ExposedBlob(csvFiles[i]) // Save ByteArray as an ExposedBlob
                }
            }
        }
    }
    fun saveInputFiles(subAssignmentIds: List<Int>, csvFiles: List<ByteArray>) {
        transaction {
            // Ensure we have the same number of IDs and files
            if (subAssignmentIds.size != csvFiles.size) {
                throw IllegalArgumentException("Number of sub-assignment IDs must match number of CSV files")
            }

            for (i in subAssignmentIds.indices) {
                SubAssignmentInput.insert {
                    it[this.subAssignmentId] = subAssignmentIds[i]
                    it[this.InputFile] = ExposedBlob(csvFiles[i]) // Save ByteArray as an ExposedBlob
                }
            }
        }
    }

    fun savePdfFile(assignmentId: Int, pdfFiles: List<ByteArray>) {
        transaction {
            pdfFiles.forEach { pdfFile ->
                AssignmentFiles.insert {
                    it[this.assignmentId] = assignmentId
                    it[this.pdfFile] = ExposedBlob(pdfFile) // Convert ByteArray to ExposedBlob
                }
            }
        }
    }

    fun getPdfFileById(fileId: Int): ByteArray? {
        return transaction {
            AssignmentFiles.select { AssignmentFiles.id eq fileId }
                .singleOrNull()?.let {
                    it[AssignmentFiles.pdfFile].bytes // Retrieve BLOB as ByteArray
                }
        }
    }
    fun getTestScriptFileBySubAssignmentId(subAssignmentId: Int): ByteArray? {
        return transaction {
            SubAssignmentTestScripts.select { SubAssignmentTestScripts.subAssignmentId eq subAssignmentId }
                .singleOrNull()?.let {
                    it[SubAssignmentTestScripts.testScript].bytes // Retrieve BLOB as ByteArray
                }
        }
    }
    fun getTestScriptFileByAssignmentId(assignmentId: Int): List<FileData> {
        return transaction {
            SubAssignments
                .innerJoin(
                    SubAssignmentTestScripts,
                    onColumn = { SubAssignments.id },
                    otherColumn = { subAssignmentId })
                .select { SubAssignments.assignmentId eq assignmentId }
                .map {
                    FileData(
                        id = it[SubAssignmentTestScripts.subAssignmentId].value,
                        fileContent = it[SubAssignmentTestScripts.testScript].bytes
                    )
                }
        }
    }
    fun getConfigFileBySubAssignmentId(subAssignmentId: Int): ByteArray? {
        return transaction {
            SubAssignmentConfig.select { SubAssignmentConfig.subAssignmentId eq subAssignmentId }
                .singleOrNull()?.let {
                    it[SubAssignmentConfig.configFile].bytes // Retrieve BLOB as ByteArray
                }
        }
    }
    fun getConfigFileByAssignmentId(assignmentId: Int): List<FileData> {
        return transaction {
            SubAssignments
                .innerJoin(
                    SubAssignmentConfig,
                    onColumn = { SubAssignments.id },
                    otherColumn = { subAssignmentId })
                .select { SubAssignments.assignmentId eq assignmentId }
                .map {
                    FileData(
                        id = it[SubAssignmentConfig.subAssignmentId].value,
                        fileContent = it[SubAssignmentConfig.configFile].bytes
                    )
                }
        }
    }


    fun getAssignmentById(id: Int): ResultRow? {
        return transaction {
            Assignments.select { Assignments.id eq id }.singleOrNull()
        }
    }

    fun getAssignmentsWithInformationByClassroom(classroomId: Int): List<AssignmentWithInformation> {
        return transaction {
            val result = (Assignments
                .leftJoin(SubAssignments) // Join SubAssignments
                .leftJoin(AssignmentPermissions) // Join AssignmentPermissions
                .leftJoin(Sections, { AssignmentPermissions.sectId }, { Sections.id }) // Join Sect
                    )
                .select { Assignments.classroomId eq classroomId } // Filter by classroomId

            val assignmentMap = mutableMapOf<Int, MutableList<SubAssignment>>()
            val permissionsMap = mutableMapOf<Int, MutableList<AssignmentPermissionData>>()

            result.forEach { row ->
                val assignmentId = row[Assignments.id].value

                // Handle sub-assignments
                val subAssignment = row.getOrNull(SubAssignments.subAssignmentNumber)?.let { number ->
                    SubAssignment(
                        id = row[SubAssignments.id].value,
                        number = number,
                        description = row[SubAssignments.description],
                        score = row[SubAssignments.score],
                        url = row[SubAssignments.url]
                    )
                }
                subAssignment?.let {
                    assignmentMap.getOrPut(assignmentId) { mutableListOf() }
                        .apply { if (!contains(it)) add(it) }
                }

                // Handle permissions
                if (row.getOrNull(AssignmentPermissions.assignmentId) != null) {
                    val permissionData = AssignmentPermissionData(
                        assignmentId = row[AssignmentPermissions.assignmentId],
                        sectId = row[AssignmentPermissions.sectId],
                        sectName = row[Sections.name],
                        dueDate = row[AssignmentPermissions.dueDate].toString(),
                        isVisible = row[AssignmentPermissions.isVisible]
                    )
                    permissionsMap.getOrPut(assignmentId) { mutableListOf() }
                        .apply { if (!contains(permissionData)) add(permissionData) }
                }
            }

            // Fetch assignments
            val assignments = Assignments
                .select { Assignments.classroomId eq classroomId }
                .map {
                    val assignmentId = it[Assignments.id].value

                    Assignment(
                        id = assignmentId,
                        title = it[Assignments.title],
                        description = it[Assignments.description],
                        classroomId = it[Assignments.classroomId].value,
                        isGroup = it[Assignments.isGroup],
                        score = it[Assignments.score]
                    )
                }

            // Combine assignments with their sub-assignments and permissions
            assignments.map { assignment ->
                AssignmentWithInformation(
                    assignment = assignment,
                    subAssignments = (assignmentMap[assignment.id]
                        ?: emptyList()).sortedBy { it.id },
                    assignmentPermission = (permissionsMap[assignment.id]
                        ?: emptyList()).sortedBy { it.sectId }
                )
            }.distinctBy { it.assignment.id }
        }
    }

    fun getAssignmentFile(assignmentId: Int): ByteArray? {
        return transaction {
            AssignmentFiles.select { AssignmentFiles.assignmentId eq assignmentId }
                .singleOrNull()?.get(AssignmentFiles.pdfFile)?.bytes
        }

    }

    private fun rowToSection(row: ResultRow): Section {
        return Section(
            id = row[Sections.id].value,
            name = row[Sections.name],
            classroomId = row[Sections.classroomId].value
        )
    }

    private fun rowToClassroom(row: ResultRow): Classroom {
        return Classroom(
            id = row[Classrooms.id].value,
            name = row[Classrooms.name],
            code = row[Classrooms.code],
            description = row[Classrooms.description],
            url = row[Classrooms.url],
            semester = row[Classrooms.semester],
            year = row[Classrooms.year]
        )
    }

    fun getPdfFileIdsByAssignmentId(assignmentId: Int): List<Int> {
        return transaction {
            AssignmentFiles.select { AssignmentFiles.assignmentId eq assignmentId }
                .map { it[AssignmentFiles.id].value } // Fetch the ID of each row
        }
    }

    fun createGroup(name: String, sectId: Int): GroupEntity {
        return transaction {
            val groupId = Groups.insertAndGetId {
                it[Groups.name] = name
                it[sect] = sectId
            }
            GroupEntity(groupId.value, name, sectId)
        }
    }

    fun getGroupsBySectId(sectId: Int): List<GroupEntity> {
        return transaction {
            (Groups innerJoin Sections)
                .slice(Groups.columns + Sections.name)
                .select { Groups.sect eq sectId }
                .map { rowToGroup(it, it[Sections.name]) }
        }
    }
    // Helper function to map ResultRow to GroupEntity
    private fun rowToGroup(row: ResultRow,sectName: String): GroupEntity {
        return GroupEntity(
            id = row[Groups.id].value,
            name = row[Groups.name],
            sectId = row[Groups.sect].value,
            sectName = sectName
        )
    }

    fun joinGroup(groupId: Int, studentId: Int): Boolean {
        return transaction {
            try {
                GroupStudents.insert {
                    it[GroupStudents.groupId] = groupId
                    it[GroupStudents.studentId] = studentId
                }
                true
            } catch (e: Exception) {
                println("Error occurred while inserting GroupStudent: ${e.message}")
                false
            }
        }
    }

    fun getGroupsByUserId(studentId: Int): List<GroupEntity> {
        return transaction {
            (GroupStudents innerJoin Groups innerJoin Sections)
                .slice(Groups.columns + Sections.name)
                .select { GroupStudents.studentId eq studentId }
                .map { rowToGroup(it, it[Sections.name]) }
        }
    }
    fun getFileBySubAssignmentId(subAssignmentId: Int, referenceId: Int): ByteArray? {
        return transaction {
            val subAssignmentScore = SubAssignmentScores
                .select {
                    (SubAssignmentScores.subAssignmentId eq subAssignmentId) and
                            (SubAssignmentScores.referenceId eq referenceId)
                }
                .map { it[SubAssignmentScores.id].value }
                .firstOrNull()

            subAssignmentScore?.let { scoreId ->
                // Fetch file linked to the subAssignmentScoreId
                SubAssignmentScoreFiles
                    .select { SubAssignmentScoreFiles.subAssignmentScoreId eq scoreId }
                    .map { it[SubAssignmentScoreFiles.outputFile].bytes } // Get file as ByteArray
                    .firstOrNull()
            }
        }
    }
}