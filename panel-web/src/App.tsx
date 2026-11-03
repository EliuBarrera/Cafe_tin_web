import { Navigate, Route, Routes } from 'react-router'

import { Shell } from './componentes/Shell'
import { RUTA_INICIO, SECCIONES } from './navegacion'
import { Login } from './paginas/Login'
import { Recuperar } from './paginas/Recuperar'
import { Restablecer } from './paginas/Restablecer'
import { RutaProtegida } from './sesion/RutaProtegida'

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/recuperar" element={<Recuperar />} />
      <Route path="/restablecer" element={<Restablecer />} />

      <Route element={<RutaProtegida />}>
        <Route element={<Shell />}>
          <Route index element={<Navigate to={RUTA_INICIO} replace />} />
          {SECCIONES.map((s) => (
            <Route key={s.ruta} element={<RutaProtegida roles={s.roles} />}>
              <Route path={s.ruta} element={s.pagina} />
            </Route>
          ))}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Route>
    </Routes>
  )
}
