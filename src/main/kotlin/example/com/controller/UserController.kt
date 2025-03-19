package example.com.controller

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import example.com.model.*
import example.com.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import example.com.model.UserPrincipal
import io.ktor.http.content.*
import java.util.concurrent.atomic.AtomicBoolean


fun Route.userRoutes(userService: UserService) {
        route("/register") {
            post("/student") {
                val request = call.receive<RegisterStudentRequest>()
                try {
                    userService.registerStudent(
                        username = request.username,
                        password = request.password,
                        firstName = request.firstName,
                        lastName = request.lastName,
                        studentId = request.studentId,
                        year = request.year
                    )
                    val response = ApiResponse(
                        success = true,
                        message = "User created successfully"
                    )
                    call.respond(HttpStatusCode.Created, response
                    )
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "User registration failed: ${e.message}")
                }

            }
            post("/teacher") {
                val request = call.receive<RegisterTeacherRequest>()
                try {
                   userService.registerTeacher(
                        username = request.username,
                        password = request.password,
                        firstName = request.firstName,
                        lastName = request.lastName,
                        email = request.email
                    )
                    val response = ApiResponse(
                        success = true,
                        message = "User created successfully"
                    )
                    call.respond(HttpStatusCode.Created, response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "User registration failed: ${e.message}")
                }

            }
        }
        post("/login") {
            val loginRequest = call.receive<LoginRequest>()
            val username = loginRequest.username
            val password = loginRequest.password

            val user = userService.authenticate(username, password) ?: return@post call.respond(
                HttpStatusCode.Unauthorized,
                "Invalid username or password"
            )
            val token = JWT.create()
                .withClaim("username", user.username)
                .withClaim("id", user.id)
                .withClaim("role",user.role)
                .sign(Algorithm.HMAC256("your-secret"))

            val response = TokenResponse(
                success = true,
                message = "Login successfully",
                token = token
            )
            call.respond(response)
        }




    authenticate("auth-jwt") {
            route("/image") {
                get("/profile") {
                    val principal = call.principal<UserPrincipal>()
                    val userId = principal?.userId
                    if (userId != null) {
                        val imageBytes = userService.getProfileImage(userId)
                        if (imageBytes != null) {
                            call.respond(ByteArrayContent(imageBytes, ContentType.Image.JPEG))
                        } else {
                            call.respond(HttpStatusCode.NotFound, "Image not found")
                        }
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    }
                }



                post("/uploadProfile") {
                    val principal = call.principal<UserPrincipal>()
                    val userId = principal?.userId
                    val multipart = call.receiveMultipart()
                    val result = AtomicBoolean(false)
                    if (userId != null) {
                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem && part.name == "file") {
                                val fileBytes = part.streamProvider().readBytes()
                                result.set(userService.saveImage(userId, fileBytes))
                            }
                            part.dispose()
                        }

                        if (result.get()) {
                            call.respond(HttpStatusCode.OK, "Image uploaded and saved")
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Failed to upload file")
                        }
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    }
                }
            }



            get("/profile") {

                val userId = call.principal<UserPrincipal>()?.userId
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid User ID")
                    return@get
                }

                val userProfile = userService.getUserProfile(userId)
                if (userProfile == null) {
                    call.respond(HttpStatusCode.NotFound, "User not found")
                    return@get
                }

                call.respond(HttpStatusCode.OK, userProfile)
            }
            put("/profile") {
                val principal = call.principal<UserPrincipal>()
                val userId = principal?.userId

                if (userId != null) {
                    try {
                        // Receive and validate the request body
                        val updateRequest = call.receive<UpdateUserProfileRequest>()

                        // Log the incoming request for debugging
                        println("Received update request: $updateRequest")

                        // Call the service to update the profile
                        val updateResult = userService.updateProfile(userId, updateRequest)
                        val response = ApiResponse(
                            success = true,
                            message = "update successfully",
                        )
                        if (updateResult) {
                            call.respond(HttpStatusCode.OK, response)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, "Failed to update profile")
                        }
                    } catch (e: ContentTransformationException) {
                        // Handle bad request format errors
                        call.respond(HttpStatusCode.BadRequest, "Invalid request format: ${e.localizedMessage}")
                    } catch (e: Exception) {

                        call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred: ${e.localizedMessage}")
                    }
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized access")
                }
            }
        }

}




