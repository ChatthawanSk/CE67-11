package example.com.repository

import example.com.*
import example.com.SubAssignments.assignmentId
import example.com.SubAssignments.subAssignmentNumber
import example.com.model.AssignmentWithScores
import example.com.model.StudentScoreRow
import example.com.model.SubAssignmentScore
import example.com.model.SubAssignmentStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.RoundingMode
import kotlin.NoSuchElementException

class AssignmentRepository {
    fun saveOrUpdateSubAssignmentScore(
        subAssignmentId: Int,
        referenceType: String,
        referenceId: Int,
        score: Int,
        pass: String,
        timestamp: String?=null
    ): Int {
        val scoreId = transaction {
            val updatedRows = SubAssignmentScores.update({
                (SubAssignmentScores.subAssignmentId eq subAssignmentId) and
                        (SubAssignmentScores.referenceType eq referenceType) and
                        (SubAssignmentScores.referenceId eq referenceId)
            }) {
                it[this.score] = score.toBigDecimal()
                it[this.error] = pass
                if (timestamp != null) {
                    it[this.timestamp] = timestamp // Directly assigning the string value
                }
            }
            println("Updated rows = $updatedRows")
            if (updatedRows == 0) { // No existing row, insert a new one
                SubAssignmentScores.insertAndGetId { row ->
                    row[this.subAssignmentId] = subAssignmentId
                    row[this.referenceType] = referenceType
                    row[this.referenceId] = referenceId
                    row[this.score] = score.toBigDecimal()
                    row[this.error] = pass
                    if (timestamp != null) {
                        row[this.timestamp] = timestamp // Directly assigning the string value
                    }
                }.value
            } else {
                // Return the existing ID
                SubAssignmentScores
                    .select {
                        (SubAssignmentScores.subAssignmentId eq subAssignmentId) and
                                (SubAssignmentScores.referenceType eq referenceType) and
                                (SubAssignmentScores.referenceId eq referenceId)
                    }
                    .map { it[SubAssignmentScores.id].value }
                    .first()
            }
        }

        // Call another function after the transaction completes
        formatStudentScore(subAssignmentId,referenceType,referenceId)

        return scoreId
    }

    fun saveOrUpdateSubAssignmentScoreFile(
        subAssignmentScoreId: Int,
        data: ByteArray
    ): Boolean {
        return try {
            transaction {
                val updatedRows = SubAssignmentScoreFiles.update({ SubAssignmentScoreFiles.subAssignmentScoreId eq subAssignmentScoreId }) {
                    it[outputFile] = ExposedBlob(data)
                }

                if (updatedRows == 0) { // If no row was updated, insert a new one
                    SubAssignmentScoreFiles.insert {
                        it[this.subAssignmentScoreId] = subAssignmentScoreId
                        it[outputFile] = ExposedBlob(data)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getScoreOutputCsv(subAssignmentScoreId: Int): ByteArray {
        return transaction {
            // Fetch the binary data for the CSV file using the subAssignmentScoreId
            SubAssignmentScoreFiles
                .select { SubAssignmentScoreFiles.subAssignmentScoreId eq subAssignmentScoreId }
                .firstNotNullOfOrNull { row ->
                    row[SubAssignmentScoreFiles.outputFile].bytes
                } ?: throw NoSuchElementException("No CSV data found for id: $subAssignmentScoreId")
        }
    }
    fun getAssignmentsWithScores(referenceId: Int): List<AssignmentWithScores> {
        return transaction {
            // Fetch assignments with sub-assignments and scores
            val query = (Assignments innerJoin SubAssignments)
                .leftJoin(SubAssignmentScores)
                .select {
                    (SubAssignmentScores.referenceId eq referenceId) and
                            (SubAssignmentScores.referenceType eq "student")
                }

            // Grouping results by assignment
            query.groupBy { row ->
                row[Assignments.id].value to row[Assignments.title]
            }.map { (assignmentKey, rows) ->
                val (assignmentId, assignmentTitle) = assignmentKey

                // Calculate total score and prepare sub-assignments
                val subAssignments = rows.map { row ->
                    SubAssignmentScore(
                        subAssignmentScoreId = row[SubAssignmentScores.id].value,
                        subAssignmentId = row[SubAssignments.id].value,
                        subAssignmentNumber = row[SubAssignments.subAssignmentNumber],
                        subAssignmentDescription = row[SubAssignments.description],
                        subAssignmentScore = row[SubAssignmentScores.score].setScale(0, RoundingMode.HALF_UP).toInt(), // Convert BigDecimal to Int
                        status = SubAssignmentStatus(
                            status = row[SubAssignmentScores.error],
                            timestamp = row[SubAssignmentScores.timestamp]
                        )
                    )
                }

                // Extract assignment score from Assignments table
                val assignmentScore = rows.first()[Assignments.score]

                // Calculate total score
                val totalScore = subAssignments.sumOf { it.subAssignmentScore ?: 0 } // Summing scores, treating null as 0

                AssignmentWithScores(
                    assignmentId = assignmentId,
                    assignmentTitle = assignmentTitle,
                    assignmentDescription = rows.first()[Assignments.description],
                    score = assignmentScore, // Include assignment score here
                    subAssignments = subAssignments,
                    totalScore = totalScore // Add assignment score to the total score
                )
            }
        }
    }

    fun getConfigFile(classroomName: String, assignmentTitle: String, subAssignmentNumber: Int): ExposedBlob? {
        return transaction {
            // Fetch the file (config_file) from SubAssignmentConfig
            SubAssignmentConfig
                .innerJoin(SubAssignments,{ subAssignmentId }, { SubAssignments.id })
                .innerJoin(Assignments, { SubAssignments.assignmentId }, { Assignments.id })
                .innerJoin(Classrooms, { Assignments.classroomId }, { Classrooms.id })
                .slice(SubAssignmentConfig.configFile)
                .select {
                    (Classrooms.name iLike  classroomName) and  // Classroom name filter
                            (Assignments.title iLike  assignmentTitle) and
                            (SubAssignments.subAssignmentNumber eq subAssignmentNumber)  // Subassignment number filter
                }
                .singleOrNull()?.get(SubAssignmentConfig.configFile)  // Get the config_file blob (or null if not found)
        }
    }

    fun getInputFile(classroomName: String, assignmentTitle: String, subAssignmentNumber: Int): ExposedBlob? {
        return transaction {
            // Fetch the file input from SubAssignmentInput
            SubAssignmentInput
                .innerJoin(SubAssignments, { subAssignmentId }, { SubAssignments.id })
                .innerJoin(Assignments, { SubAssignments.assignmentId }, { Assignments.id })
                .innerJoin(Classrooms, { Assignments.classroomId }, { Classrooms.id })
                .slice(SubAssignmentInput.InputFile)
                .select {
                    (Classrooms.name iLike  classroomName) and  // Classroom name filter
                            (Assignments.title iLike  assignmentTitle) and  // Assignment title filter
                            (SubAssignments.subAssignmentNumber eq subAssignmentNumber)  // Subassignment number filter
                }
                .singleOrNull()?.get(SubAssignmentInput.InputFile)  // Get the input blob (or null if not found)
        }
    }
    fun getTestScriptFile(classroomName: String, assignmentTitle: String, subAssignmentNumber: Int): ExposedBlob? {
        return transaction {
            // Fetch the file input from SubAssignmentInput
            SubAssignmentTestScripts
                .innerJoin(SubAssignments, { subAssignmentId }, { SubAssignments.id })
                .innerJoin(Assignments, { SubAssignments.assignmentId }, { Assignments.id })
                .innerJoin(Classrooms, { Assignments.classroomId }, { Classrooms.id })
                .slice(SubAssignmentTestScripts.testScript)
                .select {
                    (Classrooms.name iLike  classroomName) and  // Classroom name filter
                            (Assignments.title iLike  assignmentTitle) and  // Assignment title filter
                            (SubAssignments.subAssignmentNumber eq subAssignmentNumber)  // Subassignment number filter
                }
                .singleOrNull()?.get(SubAssignmentTestScripts.testScript)  // Get the input blob (or null if not found)
        }
    }



    fun getSubAssignmentId(classroomName: String, assignmentTitle: String, subAssignmentNumber: Int): Int? {
        return transaction {
            // Fetch the file (config_file) from SubAssignmentConfig
            SubAssignments
                .innerJoin(Assignments,{assignmentId }, { Assignments.id })
                .innerJoin(Classrooms,{Assignments.classroomId }, { Classrooms.id } )
                .slice(SubAssignments.id)  // Select subAssignmentId from SubAssignments
                .select {
                    (Classrooms.name.lowerCase() eq classroomName.lowercase()) and  // Classroom name filter
                            (Assignments.title.lowerCase() eq assignmentTitle.lowercase()) and  // Assignment title filter
                            (SubAssignments.subAssignmentNumber eq subAssignmentNumber)  // Subassignment number filter
                }
                .singleOrNull()?.get(SubAssignments.id)?.value    // Retrieve subAssignmentId from SubAssignments
        }
    }
    fun getSubAssignmentIdScore(subAssignmentId: Int): Int? {
        return transaction {
            // Query to find the score for the given subAssignmentId
            SubAssignments
                .slice(SubAssignments.score)
                .select { SubAssignments.id eq subAssignmentId }
                .mapNotNull { it[SubAssignments.score] }
                .singleOrNull()  // Returns a single score or null if not found
        }
    }


    private fun formatStudentScore(subAssignmentId: Int, referenceType:String, referenceId: Int) {
        val (assignmentId, subAssignmentIds, classroomId) = getAllSubAssignmentsBySubAssignmentId(subAssignmentId)

        // Check if the returned values are the default ones
        if (assignmentId == -1 && subAssignmentIds.isEmpty() && classroomId == -1) {
            // Handle the error when the function returns default values
            println("Error: No data found for the provided subAssignmentId.")
        }
        if (referenceType.lowercase() == "student") {
            calculateAndInsertTotalScore(
                subAssignmentIds = subAssignmentIds,
                referenceId = referenceId,
                classroomId = classroomId,
                assignmentId = assignmentId
            )
        }else {
            val userIdsInGroup = GroupStudents
                .slice(GroupStudents.studentId)
                .select { GroupStudents.groupId eq referenceId } // Group ID is the referenceId
                .map { it[GroupStudents.studentId] }

            // Loop through each userId and calculate their total score for the subAssignments
            userIdsInGroup.forEach { userId ->
                calculateAndInsertTotalScore(
                    subAssignmentIds = subAssignmentIds,
                    referenceId = userId,
                    classroomId = classroomId,
                    assignmentId = assignmentId
                )
            }
        }
    }



    fun getAllStudentScoresAsJsonByClassroomId(classroomId: Int): String {
        val result = transaction {
            (StudentScores
                .innerJoin(Students, { Students.credentialsId }, { StudentScores.studentId })
                .innerJoin(Users, { Users.id }, { StudentScores.studentId })
                .innerJoin(Classrooms, { Classrooms.id }, { StudentScores.classroomId })
                .innerJoin(Assignments, { Assignments.id }, { StudentScores.assignmentId }))
                .slice(
                    Users.firstName,
                    Users.lastName,
                    Students.studentId,
                    Classrooms.name,
                    Assignments.title, // Assignment Name
                    StudentScores.score
                )
                .select(Classrooms.id eq classroomId) // Filter by classroomId
                .map {
                    Triple(
                        "${it[Users.firstName]} ${it[Users.lastName]}" to it[Students.studentId], // Student Info (Full Name)
                        it[Classrooms.name], // Classroom Info
                        it[Assignments.title] to it[StudentScores.score].toDouble() // Assignment Info
                    )
                }
        }

        // Pivot Data to JSON format
        val pivotedData = result
            .groupBy { it.first } // Group by Student (Full Name, ID)
            .map { (studentInfo, records) ->
                val studentName = studentInfo.first
                val studentId = studentInfo.second
                val classroomName = records.first().second // Classroom (Same for all records)
                val scores = records.associate { it.third } // Map Assignment Name -> Score

                StudentScoreRow(studentName, studentId, classroomName, scores)
            }

        return Json.encodeToString(pivotedData)
    }

    private fun getAllSubAssignmentsBySubAssignmentId(subAssignmentId: Int): Triple<Int, List<Int>, Int> {
        return transaction {
            // Query the SubAssignment table to get the assignmentId from the subAssignmentId
            val assignmentId = SubAssignments
                .slice(SubAssignments.assignmentId)
                .select { SubAssignments.id eq subAssignmentId }
                .mapNotNull { it[SubAssignments.assignmentId] }
                .singleOrNull() // Return the assignmentId if found, null otherwise

            // If assignmentId is found, fetch all subAssignmentIds with the same assignmentId
            (assignmentId?.let {
                // Query the SubAssignments table to get all subAssignmentIds with the same assignmentId
                val subAssignmentIds = SubAssignments
                    .slice(SubAssignments.id) // Selecting the subAssignmentId (ID field)
                    .select { SubAssignments.assignmentId eq it } // Filter by assignmentId
                    .map { it[SubAssignments.id].value } // Return the list of subAssignmentIds

                // Query the Assignments table to get the classroomId associated with the assignmentId
                val classroomId = Assignments
                    .slice(Assignments.classroomId)
                    .select { Assignments.id eq it }
                    .mapNotNull { it[Assignments.classroomId] }
                    .singleOrNull()?.value // Get the classroomId associated with the assignment

                // Return the assignmentId, subAssignmentIds, and classroomId
                Triple(assignmentId.value, subAssignmentIds, classroomId ?: -1) // Default classroomId as -1 if not found
            } ?: Triple(-1, emptyList(), -1)) // Default values if no assignmentId is found
        }
    }



    private fun calculateAndInsertTotalScore(
        subAssignmentIds: List<Int>,
        referenceId: Int,
        classroomId: Int,
        assignmentId: Int
    ) {
        transaction {
            // Query the SubAssignmentScores table, filter by subAssignmentIds and referenceId, then sum the scores
            val totalScore = SubAssignmentScores
                .slice(SubAssignmentScores.score.sum()) // Summing the scores
                .select {
                    // Filter by the provided list of subAssignmentIds and referenceId
                    (SubAssignmentScores.subAssignmentId inList subAssignmentIds) and
                            (SubAssignmentScores.referenceId eq referenceId)
                }
                .mapNotNull { it[SubAssignmentScores.score.sum()]?.toDouble() }
                .sum() // Sum up all the scores for the filtered subAssignmentIds

            // Insert the total score into the StudentScores table
            val existingScore = StudentScores
                .select {
                    (StudentScores.studentId eq referenceId) and
                            (StudentScores.classroomId eq classroomId) and
                            (StudentScores.assignmentId eq assignmentId)
                }
                .singleOrNull()

            if (existingScore != null) {
                // Update existing record
                StudentScores.update({
                    (StudentScores.studentId eq referenceId) and
                            (StudentScores.classroomId eq classroomId) and
                            (StudentScores.assignmentId eq assignmentId)
                }) {
                    it[this.score] = totalScore.toBigDecimal() // Update the score
                }
            } else {
                // Insert new record
                StudentScores.insert {
                    it[this.studentId] = referenceId
                    it[this.classroomId] = classroomId
                    it[this.assignmentId] = assignmentId
                    it[this.score] = totalScore.toBigDecimal() // Insert the total score
                }
            }
        }
    }
    class ILikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "ILIKE")

    private infix fun<T:String?> ExpressionWithColumnType<T>.iLike(pattern: String): Op<Boolean> = ILikeOp(this, QueryParameter(pattern, columnType))




}

