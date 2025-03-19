package example.com.controller


import example.com.service.GitHubService
import example.com.utils.WebhookQueue
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import io.ktor.util.hex



fun Route.githubWebhook(githubService: GitHubService) {
        post("/webhook") {
            val secret = "moodeng007x"
            val receivedSignature = call.request.header("X-Hub-Signature-256") ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing signature")
            val payload = call.receiveText() // Get the raw payload as a string
            if (!verifyGitHubSignature(payload, receivedSignature, secret)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid signature")
                return@post
            }
            val submitterGithub = githubService.verifyStudent(payload)
            if(submitterGithub != null){
                println(submitterGithub)
                WebhookQueue.enqueue(submitterGithub)
            }

            call.respond(HttpStatusCode.OK, "Webhook received")
        }
    }


    private fun verifyGitHubSignature(payload: String, receivedSignature: String, secret: String): Boolean {
        // Define the algorithm for HMAC SHA256
        val algorithm = "HmacSHA256"

        // Create a SecretKeySpec using the secret and the algorithm
        val secretKeySpec = SecretKeySpec(secret.toByteArray(), algorithm)

        // Initialize the Mac instance with the SecretKeySpec
        val mac = Mac.getInstance(algorithm)
        mac.init(secretKeySpec)

        // Generate the HMAC SHA256 hash from the payload
        val hash = mac.doFinal(payload.toByteArray())

        // Convert the hash to a hexadecimal string
        val expectedSignature = "sha256=${hex(hash)}"

        // Compare the computed signature with the received signature
        return expectedSignature == receivedSignature
    }



