package pe.edu.ucv.cafetin.app

import android.app.Application
import pe.edu.ucv.cafetin.app.datos.AlmacenSesionDataStore
import pe.edu.ucv.cafetin.app.datos.ClienteApi
import pe.edu.ucv.cafetin.app.datos.CuentasRepositorio

/** Dependencias de la app, armadas a mano: es una app pequeña y así no se suma un framework de DI. */
class CafetinApp : Application() {

    val almacen by lazy { AlmacenSesionDataStore(this) }
    val api by lazy { ClienteApi.crear(BuildConfig.API_URL, almacen::tokenActual) }
    val cuentas by lazy { CuentasRepositorio(api, almacen) }
}
