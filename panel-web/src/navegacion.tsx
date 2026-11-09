import type { ReactNode } from 'react'

import type { Rol } from './api/tipos'
import { Alertas } from './paginas/Alertas'
import { Categorias } from './paginas/Categorias'
import { Despacho } from './paginas/Despacho'
import { Productos } from './paginas/Productos'
import { Proveedores } from './paginas/Proveedores'
import { Reportes } from './paginas/Reportes'
import { Trabajadores } from './paginas/Trabajadores'

interface Seccion {
  ruta: string
  titulo: string
  roles: Rol[]
  pagina: ReactNode
}

const SOLO_DUENO: Rol[] = ['DUENO']

/**
 * Menú y rutas salen de esta lista. Para agregar una pantalla, cambia `pagina` por el componente real.
 * El rol de cada sección debe coincidir con las reglas de la API (SecurityConfig).
 */
export const SECCIONES: Seccion[] = [
  { ruta: 'despacho', titulo: 'Despacho', roles: ['TRABAJADOR', 'DUENO'], pagina: <Despacho /> },
  { ruta: 'productos', titulo: 'Productos', roles: SOLO_DUENO, pagina: <Productos /> },
  { ruta: 'categorias', titulo: 'Categorías', roles: SOLO_DUENO, pagina: <Categorias /> },
  { ruta: 'proveedores', titulo: 'Proveedores', roles: SOLO_DUENO, pagina: <Proveedores /> },
  { ruta: 'alertas', titulo: 'Alertas', roles: SOLO_DUENO, pagina: <Alertas /> },
  { ruta: 'reportes', titulo: 'Reportes', roles: SOLO_DUENO, pagina: <Reportes /> },
  { ruta: 'trabajadores', titulo: 'Trabajadores', roles: SOLO_DUENO, pagina: <Trabajadores /> },
]

export const RUTA_INICIO = '/despacho'
