package androidapp

import KotlinFunctionLibrary.parameters
import kotlinx.serialization.Serializable
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.getID
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

@Serializable
open class Shiur(
    @androidx.room.Ignore
    var baseId: String?,//Not called id, title, etc. because of JVM override issues.
    @androidx.room.Ignore
    var baseTitle: String?,
    @androidx.room.Ignore
    var baseLength: String?,
    @androidx.room.Ignore
    var baseSpeaker: String?,
) {
    override fun toString(): String {
//        return "baseSpeaker=$baseSpeaker,baseLength=$baseLength,baseTitle=$baseTitle" //for testing
        return "baseId=$baseId,baseTitle=$baseTitle,baseLength=$baseLength,baseSpeaker=$baseSpeaker"
    }

    override fun equals(other: Any?): Boolean {
        return other is Shiur && getID(other) == getID(this)
    }

    override fun hashCode(): Int {
        return getID(this).hashCode()
    }

    protected inline fun <reified T : Shiur> serializedString(clazz: KClass<T>): String {
        val string = StringBuilder(clazz.simpleName ?: "")
        val params = clazz.parameters
        println("Parameters: $params")
        println("Member properties: ${clazz.memberProperties}")
        clazz.memberProperties.toMutableList().filter { params!!.contains(it.name) }.sortedBy {
            params!!.indexOf(it.name)
        }.forEach {
            string.append("~" + it.get(this as T))
        }
        return string.toString()// this::class.simpleName + this::class.memberProperties.map{"~" + it.get(this@Shiur) } //"Shiur~$baseId~$baseTitle~$baseLength~$sizeInBytes"
    }

    open fun getShiurSerializedString(): String {
        return serializedString(Shiur::class)
    }
}
