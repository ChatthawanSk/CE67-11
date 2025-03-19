package example.com



import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.date


// UserCredentials Table
object Users : IntIdTable() {
    val username = varchar("username", 100).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val role = varchar("role", 10)
    val firstName = varchar("first_name", 30)
    val lastName = varchar("last_name", 30)
}

object ProfileImages : Table() {
    val userId = reference("UserId", Users, onDelete = ReferenceOption.CASCADE)
    val imageData = blob("image_data") // Binary data for the image
    override val primaryKey = PrimaryKey(userId)
}
// Classroom Table
object Classrooms : IntIdTable() {
    val name = varchar("name", 255)
    val code = varchar("code", 50).uniqueIndex()
    val description = text("description")
    val url = text("url")
    val semester = integer("semester")
    val year = integer("year")

}

// Student Table
object Students : Table() {
    val credentialsId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val studentId = varchar("student_id", 255).uniqueIndex() // Add unique constraint to student_id
    val year = integer("year")
    override val primaryKey = PrimaryKey(credentialsId) // Define primary key here
}

// Teacher Table
object Teachers : Table() {
    val credentialsId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val email = varchar("email", 255)
    override val primaryKey = PrimaryKey(credentialsId) // Define primary key here
}

// Assignment Table
object Assignments : IntIdTable() {
    val title = varchar("title", 255)
    val description = text("description")
    val isGroup = bool("is_group")
    val score = integer("score")
    val classroomId = reference("classroom_id", Classrooms, onDelete = ReferenceOption.CASCADE)
}
object AssignmentFiles : IntIdTable() {
    val assignmentId = reference("assignment_id", Assignments, onDelete = ReferenceOption.CASCADE)
    val pdfFile = blob("pdf_file") // Binary data for the PDF file
}
object SubAssignments : IntIdTable() {
    val subAssignmentNumber = integer("sub_assignment_number") // Sub-assignment number
    val description = text("description") // Description of the sub-assignment
    val url = varchar("url" , 100)
    val score = integer("score")
    val assignmentId = reference("assignment_id", Assignments, onDelete = ReferenceOption.CASCADE) // Foreign key to Assignments
}
object SubAssignmentTestScripts : Table() {
    val subAssignmentId = reference("sub_assignment_id", SubAssignments, onDelete = ReferenceOption.CASCADE) // Foreign key to SubAssignments
    val testScript = blob("script_file") // Binary data for the test script file
    override val primaryKey = PrimaryKey(subAssignmentId)
}
object SubAssignmentConfig : Table() {
    val subAssignmentId = reference("sub_assignment_id", SubAssignments, onDelete = ReferenceOption.CASCADE) // Foreign key to SubAssignments
    val configFile = blob("config_file") // Binary data for the test script file
    override val primaryKey = PrimaryKey(subAssignmentId)
}
object SubAssignmentInput : Table() {
    val subAssignmentId = reference("sub_assignment_id", SubAssignments, onDelete = ReferenceOption.CASCADE) // Foreign key to SubAssignments
    val InputFile = blob("input_file") // Binary data for the test script file
    override val primaryKey = PrimaryKey(subAssignmentId)
}

// Group Table
object Groups : IntIdTable() {
    val name = varchar("name", 255)
    val sect = reference("sect_id",Sections, onDelete = ReferenceOption.CASCADE) // Assuming sect is an integer, adjust if needed
}
object GroupStudents : Table() {
    val groupId = integer("group_id").references(Groups.id)
    val studentId = integer("student_id").references(Students.credentialsId)
    override val primaryKey = PrimaryKey(studentId, groupId, name = "PK_GroupStudent")
}
// ClassroomStudent Table

object ClassroomTeachers : Table() {
    val classroomId = integer("classroom_id").references(Classrooms.id)
    val teacherId = integer("teacher_id").references(Teachers.credentialsId)
    override val primaryKey = PrimaryKey(classroomId, teacherId, name = "PK_ClassroomTeacher")
}
object Sections : IntIdTable() {
    val name = varchar("name", 255)
    val classroomId = reference("classroom_id", Classrooms, onDelete = ReferenceOption.CASCADE)
}
object AssignmentPermissions :IntIdTable() {
    val assignmentId = integer("assignment_id").references(Assignments.id)
    val sectId = integer("sect_id").references(Sections.id)
    val dueDate = date("due_date")
    val isVisible = bool("is_visible").default(true)
    init {
        SectionStudents.uniqueIndex("UQ_permission", sectId, assignmentId)
    }

}

object SectionStudents : Table() {
    val sectionId = reference("section_id", Sections, onDelete = ReferenceOption.CASCADE)
    val studentId = reference("student_id", Students.credentialsId, onDelete = ReferenceOption.CASCADE)

    // To ensure that a student can belong to only one section per classroom,
    // we'll add a classroomId reference and create a composite unique index.
    val classroomId = reference("classroom_id", Classrooms, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(sectionId, studentId, name = "PK_SectionStudent")

    init {
        uniqueIndex("UQ_StudentClassroom", studentId, classroomId)
    }


}

object StudentScores : Table() {
    val studentId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val classroomId = reference("classroom_id",Classrooms, onDelete = ReferenceOption.CASCADE)
    val assignmentId = reference("assignment_id", Assignments, onDelete = ReferenceOption.CASCADE)
    val score = decimal("score", precision = 5, scale = 2) // Score for the assignment
    override val primaryKey = PrimaryKey(studentId, classroomId, assignmentId)
}

object SubAssignmentScores : IntIdTable() {
    val subAssignmentId = reference("sub_assignment_id", SubAssignments, onDelete = ReferenceOption.CASCADE) // FK to SubAssignments
    val referenceType = varchar("reference_type", 20) // 'student' or 'group'
    val referenceId = integer("reference_id") // Student ID or Group ID
    val score = decimal("score", precision = 5, scale = 2) // Score for the sub-assignment
    val error = varchar("error", 10)
    val timestamp = text("timestamp" )
}
object SubAssignmentScoreFiles : Table() {
    val subAssignmentScoreId = reference("sub_assignment_score_id", SubAssignmentScores, onDelete = ReferenceOption.CASCADE) // FK to SubAssignments
    val outputFile = blob("output_file")
}




// Initialize database connection
fun Application.initDatabase() {
    Database.connect(
        url = System.getenv("Db_url") ?: "default_value",
        user = System.getenv("Db_user") ?: "default_user",
        password = System.getenv("Db_password") ?: "default_password",
        driver = "org.postgresql.Driver"
    )

    // Create tables inside a transaction
    transaction {
        SchemaUtils.create(
            Users,
            Classrooms,
            Students,
            Teachers,
            Assignments,
            AssignmentFiles,
            Groups,
            GroupStudents,
            ClassroomTeachers,
            ProfileImages,
            Sections,
            AssignmentPermissions,
            SectionStudents,
            StudentScores,
            SubAssignments,
            SubAssignmentTestScripts,
            SubAssignmentConfig,
            SubAssignmentInput,
            SubAssignmentScores,
            SubAssignmentScoreFiles
            )
        try {
            exec("CREATE INDEX IF NOT EXISTS idx_classroom_name ON Classrooms (LOWER(name));")
            exec("CREATE INDEX IF NOT EXISTS idx_assignment_title ON Assignments (LOWER(title));")
        } catch (e: Exception) {
            println("Index creation failed: ${e.message}")
        }
    }
}