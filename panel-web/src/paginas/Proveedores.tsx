import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'

import { api } from '../api/cliente'
import type { Proveedor } from '../api/tipos'
import { Campo, opcional } from '../componentes/Formulario'
import { MensajeError } from '../componentes/MensajeError'

/** La misma lista alimenta el selector del formulario de productos. */
const CLAVE = ['proveedores'] as const

/** Misma regla que la API (ProveedorRequest): solo dígitos, de 6 a 15. */
const FORMATO_TELEFONO = /^[0-9]{6,15}$/

type Panel = { tipo: 'nuevo' } | { tipo: 'editar'; proveedor: Proveedor }

/**
 * Proveedores (RF-15). La API lista también los desactivados, así que desactivar se puede deshacer.
 * Un proveedor desactivado no se ofrece al crear productos, pero los que ya lo tienen lo conservan.
 */
export function Proveedores() {
  const [panel, setPanel] = useState<Panel | null>(null)
  const consulta = useQuery({ queryKey: CLAVE, queryFn: () => api<Proveedor[]>('/api/proveedores') })
  const proveedores = consulta.data

  return (
    <section>
      <div className="encabezado-pagina">
        <h1>Proveedores</h1>
        <button type="button" className="boton" onClick={() => setPanel({ tipo: 'nuevo' })}>
          Nuevo proveedor
        </button>
      </div>

      {panel && (
        <FormularioProveedor
          key={panel.tipo === 'editar' ? panel.proveedor.id : 'nuevo'}
          proveedor={panel.tipo === 'editar' ? panel.proveedor : undefined}
          alTerminar={() => setPanel(null)}
        />
      )}

      {consulta.isPending ? (
        <p className="estado-pagina texto-suave">Cargando proveedores…</p>
      ) : !proveedores ? (
        <div className="estado-pagina">
          <MensajeError error={consulta.error} />
          <button type="button" className="boton" onClick={() => consulta.refetch()}>
            Reintentar
          </button>
        </div>
      ) : proveedores.length === 0 ? (
        <p className="estado-pagina texto-suave">Todavía no hay proveedores.</p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th scope="col">Proveedor</th>
                <th scope="col">Teléfono</th>
                <th scope="col">Correo</th>
                <th scope="col">Dirección</th>
                <th scope="col" aria-label="Acciones" />
              </tr>
            </thead>
            <tbody>
              {proveedores.map((p) => (
                <FilaProveedor key={p.id} proveedor={p} alEditar={() => setPanel({ tipo: 'editar', proveedor: p })} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function FilaProveedor({ proveedor: p, alEditar }: { proveedor: Proveedor; alEditar: () => void }) {
  const cliente = useQueryClient()
  const cambiarEstado = useMutation({
    mutationFn: (activo: boolean) =>
      api<Proveedor>(`/api/proveedores/${p.id}/estado`, { metodo: 'PATCH', cuerpo: { activo } }),
    onSettled: () => cliente.invalidateQueries({ queryKey: CLAVE }),
  })

  return (
    <tr className={p.activo ? undefined : 'fila-inactiva'}>
      <td>
        <strong>{p.nombre}</strong>
        {!p.activo && (
          <>
            {' '}
            <span className="insignia insignia-apagada">Inactivo</span>
          </>
        )}
      </td>
      <td>
        <a href={`tel:${p.telefono}`}>{p.telefono}</a>
      </td>
      <td>{p.correo}</td>
      <td>{p.direccion}</td>
      <td>
        <div className="acciones">
          <button type="button" className="boton-texto" onClick={alEditar}>
            Editar
          </button>
          <button
            type="button"
            className="boton-texto"
            disabled={cambiarEstado.isPending}
            onClick={() => cambiarEstado.mutate(!p.activo)}
          >
            {p.activo ? 'Desactivar' : 'Activar'}
          </button>
        </div>
        <MensajeError error={cambiarEstado.error} />
      </td>
    </tr>
  )
}

function FormularioProveedor({ proveedor, alTerminar }: { proveedor?: Proveedor; alTerminar: () => void }) {
  const cliente = useQueryClient()
  const [nombre, setNombre] = useState(proveedor?.nombre ?? '')
  const [telefono, setTelefono] = useState(proveedor?.telefono ?? '')
  const [correo, setCorreo] = useState(proveedor?.correo ?? '')
  const [direccion, setDireccion] = useState(proveedor?.direccion ?? '')
  const [telefonoInvalido, setTelefonoInvalido] = useState(false)
  const titulo = proveedor ? `Editar ${proveedor.nombre}` : 'Nuevo proveedor'
  const idTitulo = useId()

  const guardar = useMutation({
    mutationFn: () => {
      const cuerpo = {
        nombre: nombre.trim(),
        telefono: telefono.trim(),
        correo: opcional(correo),
        direccion: opcional(direccion),
      }
      return proveedor
        ? api<Proveedor>(`/api/proveedores/${proveedor.id}`, { metodo: 'PUT', cuerpo })
        : api<Proveedor>('/api/proveedores', { metodo: 'POST', cuerpo })
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
        const valido = FORMATO_TELEFONO.test(telefono.trim())
        setTelefonoInvalido(!valido)
        if (valido) guardar.mutate()
      }}
    >
      <h2 id={idTitulo}>{titulo}</h2>
      <div className="rejilla-campos">
        <Campo etiqueta="Nombre">
          <input value={nombre} onChange={(e) => setNombre(e.target.value)} required maxLength={100} />
        </Campo>
        <Campo etiqueta="Teléfono">
          <input
            type="tel"
            inputMode="numeric"
            value={telefono}
            onChange={(e) => setTelefono(e.target.value)}
            required
            maxLength={15}
            aria-invalid={telefonoInvalido || undefined}
          />
        </Campo>
        <Campo etiqueta="Correo">
          <input type="email" value={correo} onChange={(e) => setCorreo(e.target.value)} maxLength={120} />
        </Campo>
        <Campo etiqueta="Dirección">
          <input value={direccion} onChange={(e) => setDireccion(e.target.value)} maxLength={200} />
        </Campo>
      </div>
      {telefonoInvalido && (
        <p className="mensaje-error" role="alert">
          El teléfono debe tener solo dígitos, de 6 a 15.
        </p>
      )}
      <MensajeError error={guardar.error} />
      <div className="acciones-formulario">
        <button type="submit" className="boton" disabled={guardar.isPending}>
          Guardar
        </button>
        <button type="button" className="boton boton-secundario" onClick={alTerminar}>
          Cancelar
        </button>
      </div>
    </form>
  )
}
