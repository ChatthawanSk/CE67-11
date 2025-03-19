import example.com.model.RoleData
import example.com.model.Student
import example.com.model.Teacher
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.SerializationException

object RoleDataSerializer : KSerializer<RoleData> {

    private val teacherSerializer = Teacher.serializer()
    private val studentSerializer = Student.serializer()

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoleData") {
        element("data", buildClassSerialDescriptor("Data"))
    }

    override fun serialize(encoder: Encoder, value: RoleData) {
        val compositeOutput = encoder.beginStructure(descriptor)
        when (value) {
            is Teacher -> {
                compositeOutput.encodeSerializableElement(descriptor, 0, teacherSerializer, value)
            }
            is Student -> {
                compositeOutput.encodeSerializableElement(descriptor, 0, studentSerializer, value)
            }
        }
        compositeOutput.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): RoleData {
        val input = decoder.beginStructure(descriptor)
        val result = when {
            input.decodeElementIndex(descriptor) == 0 -> {
                val data = input.decodeSerializableElement(descriptor, 0, Teacher.serializer())
                data
            }
            input.decodeElementIndex(descriptor) == 1 -> {
                val data = input.decodeSerializableElement(descriptor, 0, Student.serializer())
                data
            }
            else -> throw SerializationException("Unknown RoleData type")
        }
        input.endStructure(descriptor)
        return result
    }
}