package example.com.model

import kotlinx.serialization.Serializable


@Serializable
data class Command(
    val command: String,
    val message: String? = null,
    val data: ByteArray? = null
)
@Serializable
data class ClientResponse
    (val command: String,
     val message: String?
    )
