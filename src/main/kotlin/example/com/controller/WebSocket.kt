package example.com.controller

import example.com.model.Command
import example.com.model.ParseSubmitter
import example.com.model.SubmitterGithub
import example.com.repository.AssignmentRepository
import example.com.utils.WebhookQueue
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.*
import java.nio.charset.StandardCharsets
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.sqrt

object WebSocketHandler {
    private val assignmentRepository = AssignmentRepository()
    suspend fun handle(session: DefaultWebSocketServerSession) {

        while (session.isActive) {
            try {
                // Add the session to a queue or manage the client
                WebhookQueue.addClient(session)

                delay(Long.MAX_VALUE) // questionable line of code but whatever
            } catch (e: Exception) {
                println("Error in WebSocket session: ${e.message}")
            } finally {
                WebhookQueue.removeClient(session)
                session.close()
            }
        }
    }

    suspend fun work(
        session: DefaultWebSocketServerSession,
        submitter: SubmitterGithub,
        submitter2: SubmitterGithub? = null
    ) {


        val dirName1 = "/${submitter.classroomName}-${submitter.id}"
        println(dirName1)
        prepareRepository(submitter.repo, dirName1)
        processCommandsPart1(session, "A", submitter)

        if (submitter2 != null) {
            val dirName2 = "/${submitter2.classroomName}-${submitter2.id}"
            prepareRepository(submitter2.repo,dirName2)

            processCommandsPart1(session, "B", submitter2)
            processCommandsPart2(session, 2, submitter, submitter2)
        }

        processCommandsPart2(session, 1, submitter)
        println("end of work")


    }

    private suspend fun processCommandsPart1(
        session: DefaultWebSocketServerSession,
        board: String,
        submitter: SubmitterGithub
    ) {
        var continueFlag = true

        println("part1 $board start")
        session.send(Command("upload_board_${board}").toJson())

        while (continueFlag && session.isActive) {
            when (val frame = session.incoming.receive()) {
                is Frame.Text -> {
                    continueFlag = handleUpload(session, frame, submitter) // Update loop state based on handleUpload
                }

                is Frame.Close -> handleCloseFrame(session, frame)
                else -> println("Unsupported frame type received.")
            }
        }
        println("part1 $board end")
    }

    private suspend fun processCommandsPart2(
        session: DefaultWebSocketServerSession,
        boardCount: Int, boardASubmitter: SubmitterGithub,
        boardBSubmitter: SubmitterGithub? = null
    ) {
        var completeSubmit = ""
        println("part2 start")
        var complete: Int = boardCount
        val dirName = "/${boardASubmitter.classroomName}-${boardASubmitter.id}"
        val dirName2 = "/${boardBSubmitter?.classroomName}-${boardBSubmitter?.id}"

        while (complete > 0 && session.isActive) {
            delay(2000)
            session.send(Command("check_status_board").toJson())
            when (val frame = session.incoming.receive()) {
                is Frame.Text -> {
                    // Capture the result from handleDownload
                    completeSubmit = handleDownload(session, frame)
                }

                is Frame.Close -> handleCloseFrame(session, frame)
                else -> println("Unsupported frame type received.")
            }
            if (completeSubmit != "") {
                when (val frame = session.incoming.receive()) {
                    is Frame.Binary -> {
                        if (completeSubmit == "A") {
                            handleBinaryFrame(frame, boardASubmitter)
                            complete--
                            removeRepository(dirName)
                        } else if (completeSubmit == "B") {
                            if (boardBSubmitter != null) {
                                handleBinaryFrame(frame, boardBSubmitter)
                                complete--
                                removeRepository(dirName2)
                            }
                        }

                    }

                    is Frame.Close -> handleCloseFrame(session, frame)
                    else -> println("Unsupported frame type received.")
                }
            }
        }
        println("part2 end")
    }


    private fun handleBinaryFrame(frame: Frame.Binary, submitter: SubmitterGithub) {
        //save received binary frame (output.csv)
        println("enter handle bin frame")
        val fileData = frame.readBytes()
        saveReceivedCSV(fileData, submitter)
    }

    private suspend fun handleUpload(
        session: DefaultWebSocketServerSession,
        frame: Frame.Text,
        submitter: SubmitterGithub
    ): Boolean {
        var value = true
        val text = frame.readText()
        val commandMessage = Json.decodeFromString<Command>(text)

        when (commandMessage.command) {
            "ready_to_receive_input_csv" -> uploadInput(session, submitter.classroomName)
            "ready_to_receive_csv" -> uploadCSV(session, submitter.classroomName)
            "ready_to_receive_bin" -> handleBIN(session, "/${submitter.classroomName}-${submitter.id}")

            else -> {
                val regex = Regex("Successfully config board ([AB]) start capture")
                val matchResult = regex.matchEntire(commandMessage.command)
                if (matchResult != null) {
//                    val currentBoard = matchResult.groupValues[1] // Extract `current_board`
                    value = false
                    // Additional logic for configuring the board
                } else {
                    println("Unknown command: ${commandMessage.command}")
                }
            }
        }
        return value
    }

    private suspend fun handleDownload(
        session: DefaultWebSocketServerSession,
        frame: Frame.Text
    ): String {
        var board = ""
        val text = frame.readText()
        val commandMessage = Json.decodeFromString<Command>(text)
        println(commandMessage.command)

        // Regex to match the patterns "0" : "0", "0" : "1", "1" : "0", or "1" : "1"
        val boardValueRegex = Regex("\\s*([01])\\s*:\\s*([01])")
        val boardValueMatch = boardValueRegex.matchEntire(commandMessage.command)

        // Regex to match the command format for boards A or B (get data capture)
//        val boardCaptureRegex = Regex("Successfully get data Capture from board ([AB])")
//        val boardCaptureMatch = boardCaptureRegex.matchEntire(commandMessage.command)


        // Handle the case where the command is in the {B_A} : {B_B} format
        if (boardValueMatch != null) {
            val boardA = boardValueMatch.groupValues[1]  // Extracts B_A
            val boardB = boardValueMatch.groupValues[2]  // Extracts B_B

            println("Board A value: $boardA, Board B value: $boardB")
            if (boardA == "1") {
                captureCSV(session, "A")
                board = "A"
                return board
            } else if (boardB == "1") {
                captureCSV(session, "B")
                board = "B"
                return board
            }
        } else {
            println("Unknown command: ${commandMessage.command}")
        }
        return board
    }

    private suspend fun handleCloseFrame(
        session: DefaultWebSocketServerSession,
        frame: Frame.Close
    ) {
        WebhookQueue.removeClient(session)
        println("Connection closed by peer: ${frame.readReason()}")
        session.close(CloseReason(CloseReason.Codes.NORMAL, "Closed by peer"))
    }

    private suspend fun uploadCSV(session: DefaultWebSocketServerSession, labInfo: String) {
//        val file = File("src/main/resources/data/config.csv")
        println("Preparing to send CSV...")
        val (course, assignmentName, subAssignmentNumber) = parseRepo(labInfo)
        val fileBlob = assignmentRepository.getConfigFile(course, assignmentName, subAssignmentNumber)
        if (fileBlob != null) {
            val fileBytes = fileBlob.bytes
            session.send(Frame.Binary(true, fileBytes))
            println("Sent Input file for Classroom: $course, Assignment: $assignmentName, Sub-Assignment: $subAssignmentNumber")
        } else {
            println("Input file not found for Classroom: $course, Assignment: $assignmentName, Sub-Assignment: $subAssignmentNumber")
        }
    }

    private suspend fun uploadInput(session: DefaultWebSocketServerSession, labInfo: String) {
        //        val file = File("src/main/resources/data/input.csv")
        println("Preparing to send CSV...")
        val (course, assignmentName, subAssignmentNumber) = parseRepo(labInfo)
        val fileBlob = assignmentRepository.getInputFile(course, assignmentName, subAssignmentNumber)

        if (fileBlob != null) {
            val fileBytes = fileBlob.bytes // Convert ExposedBlob to byte array
            println(fileBytes)
            session.send(Frame.Binary(true, fileBytes))
            println("Sent Input file for Classroom: $course, Assignment: $assignmentName, Sub-Assignment: $subAssignmentNumber")
        } else {
            println("Input file not found for Classroom: $course, Assignment: $assignmentName, Sub-Assignment: $subAssignmentNumber")
        }
    }

    private suspend fun captureCSV(session: DefaultWebSocketServerSession, board: String) {
        session.send(Command("spi_getdatacapture_board_$board").toJson())
    }
//
//    private suspend fun getCSV(session: DefaultWebSocketServerSession, board: String) {
//        println("get CSV $board")
//        session.send(Command("send_csv_board_$board").toJson())
//    }

    private suspend fun handleBIN(session: DefaultWebSocketServerSession, dir: String) {
        println("Preparing to send binary file")
        val directoryPath = "$dir/build/"
        val directory = File(directoryPath)
//        val file = File("src/main/resources/bin/please.bin")
//        val file = File("src/main/resources/bin/fire.bin")
        val file = directory.listFiles()?.find { it.isFile && it.name.endsWith(".bin") }

        if (file != null && file.exists()) {
            val fileBytes = file.readBytes() // Read the file's bytes
            session.send(Frame.Binary(true, fileBytes)) // Send as a binary frame
            println("Sent BIN file: ${file.name}")
        } else {
//            println("BIN file not found in directory: $directoryPath")
        }
    }


    private fun saveReceivedCSV(dataWs: ByteArray, submitter: SubmitterGithub) {
        val parsedData = parseRepo(submitter.repo)
        val (course, labName, subAssignmentNumber) = parsedData
        val (type, id) = submitter
        var signalType:String = "GPIO"
        var passFlag = false
        //get file from DB
        val dataDb = assignmentRepository.getTestScriptFile(course,labName,subAssignmentNumber)?.bytes
        val inputFile = assignmentRepository.getInputFile(course, labName, subAssignmentNumber)?.bytes
        val configFile = assignmentRepository.getConfigFile(course, labName, subAssignmentNumber)?.bytes

        var TimeFlag = false
        //decode to string
        val decodedResult = String(dataWs, StandardCharsets.UTF_8)
        val decodedTestScript = dataDb?.let { String(it, StandardCharsets.UTF_8) }
        val decodedInput = inputFile?.let { String(it, StandardCharsets.UTF_8) }
        val decodedConfig = configFile?.let { String(it, StandardCharsets.UTF_8) }



        //check lab type
        if(decodedConfig!= null){
            if (decodedConfig.contains("PWM")) {
                signalType = "PWM"
            }
        }
        else if(decodedInput != null) {
            signalType = if (decodedInput.contains("ee")) {
                "GPIO"
            } else {
                "IO"
            }
        }
        println(signalType)
        val outputTimestamp = extractColumns(decodedResult, 4)
        val expectedTimestamp = decodedTestScript?.let { extractColumns(it, 4) }
        if (expectedTimestamp != null) {
            TimeFlag = checkTimestamp(outputTimestamp, expectedTimestamp)
        }



        val subAssignmentId = assignmentRepository.getSubAssignmentId(course, labName, subAssignmentNumber)
        val subAssignmentScore = subAssignmentId?.let { assignmentRepository.getSubAssignmentIdScore(it) }
        var score = 0

        if (TimeFlag) {
            if (subAssignmentScore != null) {
                score = subAssignmentScore.toInt()
                passFlag = true
            }
        }
        val subAssignmentScoreId:Int?
        if (subAssignmentId != null ) {
            if (passFlag) {
                subAssignmentScoreId =
                assignmentRepository.saveOrUpdateSubAssignmentScore(subAssignmentId, type, id, score,"pass")
                println("save complete")
            }
            else{
                val debugTimestamp = decodedTestScript?.let { debugOutput(decodedResult, it) }
                subAssignmentScoreId =
                assignmentRepository.saveOrUpdateSubAssignmentScore(subAssignmentId, type, id, score,"fail",(debugTimestamp))
            }
            assignmentRepository.saveOrUpdateSubAssignmentScoreFile(subAssignmentScoreId, dataWs)
        }
    }


    private fun Command.toJson(): String = Json.encodeToString(this)

     private fun mkDir(dir: String) {
        val commandMkDir = "mkdir -p $dir"  // -p ensures the parent directories are created if they don't exist
        // Run the mkdir command using ProcessBuilder
        val processMkDir = ProcessBuilder(*commandMkDir.split(" ").toTypedArray()).start()
        // Wait for the process to complete and get the exit code
        val exitCodeMkDir = processMkDir.waitFor()
        if (exitCodeMkDir == 0) {
            println("Directory created successfully")
        } else {
            println("Directory creation failed with exit code: $exitCodeMkDir")
        }
    }

     private fun cloneRepo(repoUrl: String, cloneDirectory: String) {
         println(repoUrl)
         println(cloneDirectory)
        val pat = System.getenv("Github_PAT") ?: throw IllegalStateException("Github_PAT is not set")
        // Run the git clone command without capturing output
        val commandClone = "git clone https://$pat@github.com/$repoUrl.git $cloneDirectory"
         println(commandClone)
        val processClone = ProcessBuilder(*commandClone.split(" ").toTypedArray()).start()
        // Wait for the process to complete
        val exitCodeClone = processClone.waitFor()
        if (exitCodeClone == 0) {
            println("Git clone successful")
        } else {
            println("Git clone failed with exit code: $exitCodeClone")
        }
    }

    private fun compile(cloneDirectory: String) {
        try {
            val repoDir = File(cloneDirectory)
            // Remove all files in the build folder
            val rmProcess = ProcessBuilder("rm", "-rf", "build")
                .directory(repoDir)
                .start()
            rmProcess.waitFor()

            // Run cmake command
            println(repoDir)
            val cmakeProcess = ProcessBuilder(
                "cmake",
                "-G", "Unix Makefiles",
                "-B", "build",
                "-DCMAKE_TOOLCHAIN_FILE=gcc-arm-none-eabi.cmake"
            ).directory(repoDir).start()
            cmakeProcess.waitFor()

            // Run make command
            val makeProcess = ProcessBuilder("make", "-C", "build")
                .directory(repoDir)
                .start()
            makeProcess.waitFor()
            println("Commands executed successfully.")
        } catch (e: IOException) {
            e.printStackTrace()
            println("Error executing commands.")
        } catch (e: InterruptedException) {
            e.printStackTrace()
            println("Process was interrupted.")
        }
    }

    private fun prepareRepository(repoUrl: String, cloneDirectory: String) {
        try {
            mkDir(cloneDirectory)
            cloneRepo(repoUrl, cloneDirectory)
            compile(cloneDirectory)
        } catch (e: IOException) {
            "Error executing git clone: ${e.localizedMessage}"
        }
    }

    private fun removeRepository(cloneDirectory: String) {
        try {
            val repoDir = File(cloneDirectory)

            if (repoDir.exists()) {
                val rmProcess = ProcessBuilder("rm", "-rf", repoDir.absolutePath)
                    .start()

                val exitCode = rmProcess.waitFor()
                if (exitCode == 0) {
                    println("Repository at $cloneDirectory has been successfully removed.")
                } else {
                    println("Failed to remove repository at $cloneDirectory. Exit code: $exitCode")
                }
            } else {
                println("Directory $cloneDirectory does not exist.")
            }
        } catch (e: IOException) {
            println("Error executing rm command: ${e.localizedMessage}")
        }
    }

    private fun parseRepo(repo: String): ParseSubmitter {
        val (course, labPart) = repo.split("/", limit = 2)
        val (labName, subAssignmentNumberAndName) = labPart.split("-", limit = 2)
        val (subAssignmentNumber, _) = subAssignmentNumberAndName.split("-", limit = 2)
        return ParseSubmitter(course, labName, subAssignmentNumber.toInt())
    }

    private fun extractDebug(data: String): Map<Int, List<Int>> {
        val result = mutableMapOf<Int, MutableList<Int>>()

        data.lines().forEach { line ->
            val columns = line.split(",")

            // Extracting columns and grouping them into the result map
            columns.forEachIndexed { index, value ->
                val numValue = value.toIntOrNull() ?: return@forEachIndexed // Skip invalid numbers

                // Calculate the column index (2, 6, 10, etc. for the first group, 6, 10, 14, etc. for the second group)
                val columnIndex = (index + 1) % 4

                if (columnIndex == 2 || columnIndex == 0) {
                    // For columns 2, 6, 10... (index 1, 5, 9, ...) and column 6 (index 3, 7, 11, ...)
                    result.computeIfAbsent(index / 4 + 1) { mutableListOf() }.add(numValue)
                }
            }
        }
        return result
    }


    private fun extractColumns(data: String, interval: Int): List<List<Int>> {
        val extractedColumns = mutableListOf<MutableList<Int>>()

        data.lines().forEach { line ->
            val columns = line.split(",")
            columns.forEachIndexed { index, value ->
                if ((index + 1) % interval == 0) {  // Adjust to get every `interval`-th column (3rd, 6th, 9th, etc.)
                    while (extractedColumns.size <= (index / interval)) {
                        extractedColumns.add(mutableListOf()) // Ensure list size
                    }
                    value.toIntOrNull()?.let { extractedColumns[index / interval].add(it) }
                }
            }
        }
        println(extractedColumns)
        return extractedColumns
    }

    private fun crossCorrelation(signalType: String, outputSignal: List<Int>, expectedSignal: List<Int>): Pair<Int, Double> {
        return when (signalType) {
            "PWM" -> crossCorrelationPWM(outputSignal, expectedSignal)
            "GPIO" -> {
                crossCorrelationGPIO(outputSignal, expectedSignal)
            }
            "IO" -> crossCorrelationIO(outputSignal, expectedSignal)
            else -> throw IllegalArgumentException("Invalid Signal Type")
        }
    }

    private fun crossCorrelationPWM(outputSignal: List<Int>, expectedSignal: List<Int>): Pair<Int, Double> {
        val correlation = IntArray(outputSignal.size + expectedSignal.size - 1) { 0 }

        for (i in outputSignal.indices) {
            for (j in expectedSignal.indices) {
                correlation[i + j] += outputSignal[i] * expectedSignal[j]
            }
        }

        val maxCorrelation = correlation.max().toDouble()
        val bestMatch = correlation.indexOf(correlation.max())

        // Calculate normalized accuracy (range 0 - 1)
        val expectedEnergy = expectedSignal.sumOf { it * it }.toDouble()
        val outputEnergy = outputSignal.sumOf { it * it }.toDouble()
        val normalizationFactor = sqrt(expectedEnergy * outputEnergy)

        val accuracy = if (normalizationFactor != 0.0) (maxCorrelation / normalizationFactor) else 0.0

        println("PWM Pin Result ✅")
        println("Best Match Lag: $bestMatch ms")
        println("Max Correlation: $maxCorrelation")
        println("Accuracy: %.4f".format(accuracy))

        return Pair(bestMatch, accuracy)
    }


    private fun crossCorrelationGPIO(outputSignal: List<Int>, expectedSignal: List<Int>): Pair<Int, Double> {
        val matches = outputSignal.zip(expectedSignal).count { (out, exp) -> out == exp }
        val accuracy = matches.toDouble() / expectedSignal.size
        println("GPIO Pin Result ✅")
        println("Match Percentage: ${accuracy * 100}%\n")
        return Pair(0, accuracy)
    }

    private fun crossCorrelationIO(outputSignal: List<Int>, expectedSignal: List<Int>): Pair<Int, Double> {
        val matches = outputSignal.zip(expectedSignal).count { (out, exp) -> out == exp }
        val accuracy = matches.toDouble() / expectedSignal.size
        println("IO Pin Result ✅")
        println("Match Percentage: ${accuracy * 100}%\n")
        return Pair(0, accuracy)
    }

//    private fun checkTimestamp(outputTimestamp: List<Int>, expectedTimestmap: List<Int>): Boolean {
//        val matches = outputTimestamp.zip(expectedTimestmap).count { (out, exp) -> Math.abs(out - exp) <= 200 }
//        val accuracy = matches.toDouble() / expectedTimestmap.size
//        println("Timestamp Result ✅")
//        println("Match Percentage: ${accuracy * 100}%\n")
//        if(accuracy >= 0.95){
//            return true
//        }
//        return false
//    }

    private fun checkTimestamp(outputTimestamp: List<List<Int>>,
                               expectedTimestamp: List<List<Int>>): Boolean {
        if (outputTimestamp.size != expectedTimestamp.size) {
            println("Error: Mismatched number of sublists.")
            return false
        }

        var allWithinThreshold = true

        for (i in outputTimestamp.indices) {
            val outputList = outputTimestamp[i]
            val expectedList = expectedTimestamp[i]

            if (outputList.size < 2 || expectedList.size < 2) {
                println("Skipping sublist $i: Not enough elements to compare.")
                continue
            }

            // Take the last 20 values (or all if fewer than 20)
            val outputSubset = outputList.takeLast(20)
            val expectedSubset = expectedList.takeLast(20)

            // Compute consecutive differences
            val outputDifferences = outputSubset.zipWithNext { a, b -> b - a }
            val expectedDifferences = expectedSubset.zipWithNext { a, b -> b - a }

            // Calculate averages
            val avgOutputDiff = outputDifferences.average()
            val avgExpectedDiff = expectedDifferences.average()

            val difference = avgOutputDiff - avgExpectedDiff

            // Print results for each sublist
            println("Timestamp Comparison for Sublist $i ✅")
            println("Output Differences (Last 20): $outputDifferences")
            println("Expected Differences (Last 20): $expectedDifferences")
            println("Average Output Difference: $avgOutputDiff ms")
            println("Average Expected Difference: $avgExpectedDiff ms")
            println("Final Difference: $difference ms\n")

            if (abs(difference) > 50) {
                allWithinThreshold = false
            }
        }

        return allWithinThreshold
    }





    private fun debugOutput(
        data: String,  // Main Dataset
        referenceData: String  // Reference Dataset
    ): String {

        val output = extractDebug(data)
        val ref = extractDebug(referenceData)
        val avgOutputDifferences = mutableMapOf<Int, Double>()
        val avgRefDifferences = mutableMapOf<Int, Double>()
        val failedColumns = mutableListOf<Int>()

        output.forEach { (column, values) ->
                if (values.size > 1) {
                    // Calculate the differences between consecutive elements
                    val differences = values.zipWithNext { a, b -> b - a }

                    // Calculate the average of the differences
                    val avgOutputDif = differences.average()

                    avgOutputDifferences[column] = avgOutputDif
                }
            }
        ref.forEach { (column, values) ->
            if (values.size > 1) {
                // Calculate the differences between consecutive elements
                val differences = values.zipWithNext { a, b -> b - a }

                // Calculate the average of the differences
                val avgRefDif = differences.average()

                avgRefDifferences[column] = avgRefDif
            }
        }
        avgOutputDifferences.forEach { (column, avgOutputDiff) ->
            val avgRefDiff = avgRefDifferences[column]

            if (avgRefDiff != null) {
                val difference = avgOutputDiff - avgRefDiff

                // Check if the difference exceeds the threshold
                if (abs(difference) > 50) {
                    failedColumns.add(column)
                    println("Pin $column: Output Avg Diff = $avgOutputDiff, Ref Avg Diff = $avgRefDiff, Difference = $difference (FAILED)")
                } else {
                    println("Pin $column: Output Avg Diff = $avgOutputDiff, Ref Avg Diff = $avgRefDiff, Difference = $difference (PASSED)")
                }
            } else {
                failedColumns.add(column)
                println("Pin $column: Reference data not available. (FAILED)")
            }
        }
        return failedColumns.toString()
    }



}