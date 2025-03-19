package example.com.utils

import example.com.controller.WebSocketHandler
import example.com.model.SubmitterGithub
import io.ktor.server.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WebhookQueue {
    // Task queue: Holds the tasks that need to be processed
    private val queue = ArrayDeque<SubmitterGithub>()

    // Track connected WebSocket clients and their availability
    private val clients = mutableSetOf<DefaultWebSocketServerSession>()
    private val clientTasks = mutableMapOf<DefaultWebSocketServerSession, ClientState>()

    // Mutex to ensure that only one task is assigned at a time
    private val taskAssignmentMutex = Mutex()


    // Enqueue a new task
    suspend fun enqueue(submitter: SubmitterGithub) {
        queue.addLast(submitter)
        distributeTasks() // Try to assign tasks to available clients
    }

    // Add a new WebSocket client
    suspend fun addClient(client: DefaultWebSocketServerSession) {
        clients.add(client)
        clientTasks[client] = ClientState.Available
        distributeTasks() // Try to assign tasks
    }

//     Remove a disconnected WebSocket client
    fun removeClient(client: DefaultWebSocketServerSession) {
        clients.remove(client)
        clientTasks.remove(client)
    }

    // Mark a client as available after completing a task
    private suspend fun markClientAvailable(client: DefaultWebSocketServerSession) {
        clientTasks[client] = ClientState.Available
        distributeTasks() // Assign remaining tasks
    }

    // Distribute tasks to available clients
    private suspend fun distributeTasks() {
        // Lock to ensure task distribution is synchronized
        taskAssignmentMutex.withLock {
            while (queue.isNotEmpty()) {
                // Find an available client
                val availableClient = clients.find { clientTasks[it] == ClientState.Available }
                println("distribute queue")
                // If there's at least one available client and more than one task in the queue
                if (availableClient != null && queue.size >= 2) {
                    // Get the next two tasks from the queue
                    val submitter1 = queue.removeFirst()
                    val submitter2 = queue.removeFirst()

                    // Assign both tasks to the same client
                    assignTask(availableClient, submitter1,submitter2)
                } else if (availableClient != null) {
                    // Otherwise, assign a single task to the available client
                    val submitter = queue.removeFirst()
                    assignTask(availableClient, submitter)
                } else {
                    break // No available clients, exit the loop
                }
            }
        }
    }


    // Assign a task to a specific client
    private fun assignTask(client: DefaultWebSocketServerSession, submitter1: SubmitterGithub, submitter2: SubmitterGithub? = null) {
        println("assign task")
        GlobalScope.launch {
            if (client.isActive) {
                try {
                    // Mark client as busy
                    clientTasks[client] = ClientState.Busy
                    println("Tasks sent to client: $submitter1, $submitter2")

                    // Perform the work with one or two tasks
                    WebSocketHandler.work(client, submitter1, submitter2)
                } catch (e: Exception) {
                    // Log failure and requeue the tasks if they failed
                    println("Failed to send tasks: ${e.message}")
                    queue.addFirst(submitter1)
                    submitter2?.let { queue.addFirst(it) } // Requeue the second task if it exists
                } finally {
                    // Mark client as available after tasks are processed
                    markClientAvailable(client)
                }
            }
        }
    }

    // Enum representing the client's state
    private sealed class ClientState {
        data object Available : ClientState()
        data object Busy : ClientState()
    }
}
