import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'

import { api } from '../api/cliente'
import type { Trabajador, TrabajadorAlta, Turno } from '../api/tipos'
import { Campo, opcional } from '../componentes/Formulario'
import { MensajeError } from '../componentes/MensajeError'

const CLAVE = ['trabajadores'] as const

const TURNOS: { valor: Turno; texto: string }[] = [
  { valor: 'MANANA', texto: 'Mañana' },
  { valor: 'TARDE', texto: 'Tarde' },
  { valor: 'NOCHE', texto: 'Noche' },
]
const textoTurno = (turno: Turno) => TURNOS.find((t) => t.valor === turno)?.texto ?? turno

/** Mismas reglas que la API (TrabajadorRequest): teléfono de 9 dígitos y contraseña de 8 a 72. */
const FORMATO_TELEFONO = /^[0-9]{9}$/
const CONTRASENA_MINIMA = 8

/**
 * Trabajadores (RF-02). El dueño los registra con una contraseña inicial y los activa o desactiva:
 * uno inactivo deja de poder entrar en el acto (la API revisa el estado en cada petición), y se
 * puede deshacer. La API no permite editarlos.
 */
export function Trabajadores() {
  const [formularioAbierto, setFormularioAbierto] = useState(false)
  const consulta = useQuery({ queryKey: CLAVE, queryFn: () => api<Trabajador[]>('/api/trabajadores') })
  const trabajadores = consulta.data

  return (
    <section>
      <div className="encabezado-pagina">
        <h1>Trabajadores</h1>
        <button type="button" className="boton" onClick={() => setFormularioAbierto(true)}>
          Nuevo trabajador
        </button>
      </div>

      {formularioAbierto && <FormularioTrabajador alTerminar={() => setFormularioAbierto(false)} />}

      {consulta.isPending ? (
        <p className="estado-pagina texto-suave">Cargando trabajadores…</p>
      ) : !trabajadores ? (
        <div className="estado-pagina">
          <MensajeError error={consulta.error} />
          <button type="button" className="boton" onClick={() => consulta.refetch()}>
            Reintentar
          </button>
        </div>
      ) : trabajadores.length === 0 ? (
        <p className="estado-pagina texto-suave">Todavía no hay trabajadores.</p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th scope="col">Trabajador</th>
                <th scope="col">Código</th>
                <th scope="col">Turno</th>
                <th scope="col">Correo</th>
                <th scope="col">Teléfono</th>
                <th scope="col">Ingreso</th>
                <th scope="col" aria-label="Acciones" />
              </tr>
            </thead>
            <tbody>
              {trabajadores.map((t) => (
                <FilaTrabajador key={t.id} trabajador={t} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function FilaTrabajador({ trabajador: t }: { trabajador: Trabajador }) {
  const cliente = useQueryClient()
  const cambiarEstado = useMutation({
    mutationFn: (activo: boolean) =>
      api<Trabajador>(`/api/trabajadores/${t.id}/estado`, { metodo: 'PATCH', cuerpo: { activo } }),
    onSettled: () => cliente.invalidateQueries({ queryKey: CLAVE }),
  })

  return (
    <tr className={t.activo ? undefined : 'fila-inactiva'}>
      <td>
        <strong>
          {t.nombre} {t.apellidos}
        </strong>
        {!t.activo && (
          <>
            {' '}
            <span className="insignia insignia-apagada">Inactivo</span>
          </>
        )}
      </td>
      <td>{t.codigoEmpleado}</td>
      <td>{textoTurno(t.turno)}</td>
      <td>{t.correo}</td>
      <td>{t.telefono && <a href={`tel:${t.telefono}`}>{t.telefono}</a>}</td>
      <td>{new Date(`${t.fechaIngreso}T00:00:00`).toLocaleDateString('es-PE')}</td>
      <td>
        <div className="acciones">
          <button
            type="button"
            className="boton-texto"
            disabled={cambiarEstado.isPending}
            onClick={() => cambiarEstado.mutate(!t.activo)}
          >
            {t.activo ? 'Desactivar' : 'Activar'}
          </button>
        </div>
        <MensajeError error={cambiarEstado.error} />
      </td>
    </tr>
  )
}

function FormularioTrabajador({ alTerminar }: { alTerminar: () => void }) {
  const cliente = useQueryClient()
  const [nombre, setNombre] = useState('')
  const [apellidos, setApellidos] = useState('')
  const [correo, setCorreo] = useState('')
  const [telefono, setTelefono] = useState('')
  const [codigoEmpleado, setCodigoEmpleado] = useState('')
  const [turno, setTurno] = useState<Turno>('MANANA')
  const [contrasena, setContrasena] = useState('')
  const [avisos, setAvisos] = useState<string[]>([])
  const idTitulo = useId()

  const registrar = useMutation({
    mutationFn: () => {
      const cuerpo: TrabajadorAlta = {
        nombre: nombre.trim(),
        apellidos: apellidos.trim(),
        correo: correo.trim(),
        telefono: opcional(telefono),
        codigoEmpleado: codigoEmpleado.trim(),
        turno,
        contrasena,
      }
      return api<Trabajador>('/api/trabajadores', { metodo: 'POST', cuerpo })
    },
    onSuccess: async () => {
      await cliente.invalidateQueries({ queryKey: CLAVE })
      alTerminar()
    },
  })

  return (
    <form
      className="panel-formulario formulario"
      aria-labelledby={idTitulo}
      onSubmit={(e) => {
        e.preventDefault()
        const encontrados: string[] = []
        if (telefono.trim() !== '' && !FORMATO_TELEFONO.test(telefono.trim())) {
          encontrados.push('El teléfono debe tener 9 dígitos.')
        }
        if (contrasena.length < CONTRASENA_MINIMA) {
          encontrados.push(`La contraseña debe tener al menos ${CONTRASENA_MINIMA} caracteres.`)
        }
        setAvisos(encontrados)
        if (encontrados.length === 0) registrar.mutate()
      }}
    >
      <h2 id={idTitulo}>Nuevo trabajador</h2>
      <div className="rejilla-campos">
        <Campo etiqueta="Nombre">
          <input value={nombre} onChange={(e) => setNombre(e.target.value)} required maxLength={60} />
        </Campo>
        <Campo etiqueta="Apellidos">
          <input value={apellidos} onChange={(e) => setApellidos(e.target.value)} required maxLength={80} />
        </Campo>
        <Campo etiqueta="Correo">
          <input type="email" value={correo} onChange={(e) => setCorreo(e.target.value)} required maxLength={120} />
        </Campo>
        <Campo etiqueta="Teléfono">
          <input
            type="tel"
            inputMode="numeric"
            value={telefono}
            onChange={(e) => setTelefono(e.target.value)}
            maxLength={9}
          />
        </Campo>
        <Campo etiqueta="Código de empleado">
          <input value={codigoEmpleado} onChange={(e) => setCodigoEmpleado(e.target.value)} required maxLength={15} />
        </Campo>
        <Campo etiqueta="Turno">
          <select value={turno} onChange={(e) => setTurno(e.target.value as Turno)}>
            {TURNOS.map((t) => (
              <option key={t.valor} value={t.valor}>
                {t.texto}
              </option>
            ))}
          </select>
        </Campo>
        <Campo etiqueta="Contraseña inicial">
          <input
            type="password"
            autoComplete="new-password"
            value={contrasena}
            onChange={(e) => setContrasena(e.target.value)}
            required
            maxLength={72}
          />
        </Campo>
      </div>
      <p className="texto-suave">Entrégale la contraseña al trabajador: luego puede cambiarla con "¿Olvidaste tu contraseña?".</p>
      {avisos.map((aviso) => (
        <p key={aviso} className="mensaje-error" role="alert">
          {aviso}
        </p>
      ))}
      <MensajeError error={registrar.error} />
      <div className="acciones-formulario">
        <button type="submit" className="boton" disabled={registrar.isPending}>
          Registrar
        </button>
        <button type="button" className="boton boton-secundario" onClick={alTerminar}>
          Cancelar
        </button>
      </div>
    </form>
  )
}
