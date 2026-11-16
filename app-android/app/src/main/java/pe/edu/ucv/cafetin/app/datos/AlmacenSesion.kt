package pe.edu.ucv.cafetin.app.datos

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

data class Sesion(val token: String, val idUsuario: Int, val nombre: String, val rol: String)

/** Dónde vive la sesión. Interfaz para que los tests usen una en memoria. */
interface AlmacenSesion {
    /** Token para el interceptor de OkHttp: se lee en cada petición, sin suspender. */
    fun tokenActual(): String?
    suspend fun cargar(): Sesion?
    suspend fun guardar(sesion: Sesion)
    suspend fun borrar()
}

private val Context.datosSesion by preferencesDataStore(name = "sesion")

/** Sesión persistida en DataStore (decisión del proyecto) con el token en memoria para el interceptor. */
class AlmacenSesionDataStore(private val contexto: Context) : AlmacenSesion {

    @Volatile private var token: String? = null

    override fun tokenActual() = token

    override suspend fun cargar(): Sesion? {
        val p = contexto.datosSesion.data.first()
        val guardado = p[TOKEN] ?: return null
        return Sesion(guardado, p[ID] ?: 0, p[NOMBRE].orEmpty(), p[ROL].orEmpty()).also { token = it.token }
    }

    override suspend fun guardar(sesion: Sesion) {
        contexto.datosSesion.edit {
            it[TOKEN] = sesion.token
            it[ID] = sesion.idUsuario
            it[NOMBRE] = sesion.nombre
            it[ROL] = sesion.rol
        }
        token = sesion.token
    }

    override suspend fun borrar() {
        contexto.datosSesion.edit { it.clear() }
        token = null
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val ID = intPreferencesKey("id_usuario")
        val NOMBRE = stringPreferencesKey("nombre")
        val ROL = stringPreferencesKey("rol")
    }
}
