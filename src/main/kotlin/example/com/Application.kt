package example.com


import com.auth0.jwt.JWT
import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import com.auth0.jwt.algorithms.Algorithm
import example.com.controller.*
import example.com.model.UserPrincipal
import example.com.repository.ClassroomRepository
import example.com.repository.GitHubRepository
import example.com.repository.UserRepository
import example.com.service.ClassroomService
import example.com.service.GitHubService
import example.com.service.UserService
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.websocket.*
import java.time.Duration


fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(CORS) {
        anyHost()  // Specific frontend domain
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("ngrok-skip-browser-warning")  // Add any custom headers you're sending
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }

    intercept(ApplicationCallPipeline.Call) {
        val origin = call.request.headers[HttpHeaders.Origin]
        val requestHeaders = call.request.headers.entries().joinToString(", ")
        println("CORS request from origin: $origin, headers: $requestHeaders")
    }

        initDatabase()
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respondText(text = "500: $cause" , status = HttpStatusCode.InternalServerError)
            }
        }

        install(Authentication) {
            jwt("auth-jwt") {
                verifier(JWT.require(Algorithm.HMAC256("your-secret")).build())
                validate { credential ->
                    val username = credential.payload.getClaim("username").asString()
                    val userId = credential.payload.getClaim("id").asInt()
                    if (username != null && userId != null) {
                        UserPrincipal(username, userId)
                    } else {
                        null
                    }
                }
            }
        }
        install(WebSockets) {
            pingPeriod = Duration.ofMinutes(1)
            timeout = Duration.ofSeconds(15)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
    val userRepository = UserRepository()
    val userService = UserService(userRepository)
    val classroomRepository = ClassroomRepository()
    val classroomService = ClassroomService(classroomRepository)
    val gitHubRepository = GitHubRepository()
    val githubService = GitHubService(gitHubRepository)

    routing {
            get("/") {
                call.respondText("Server is Up and Running Just fine")
            }
            classroomRoutes(classroomService)
            userRoutes(userService)
            githubWebhook(githubService)
            webSocket("/ws") {
                WebSocketHandler.handle(this)
            }

        }

}

