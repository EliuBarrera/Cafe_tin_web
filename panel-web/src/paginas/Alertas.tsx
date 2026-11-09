import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { Link } from 'react-router'

import { api } from '../api/cliente'
import type { Alerta } from '../api/tipos'
import { INTERVALO_ALERTAS_MS } from '../componentes/ContadorAlertas'
import { MensajeError } from '../componentes/MensajeError'

type Vista = 'pendientes' | 'atendidas'

/** Misma clave que el contador del menú: las pendientes se piden una sola vez y los dos se actualizan juntos. */
const CLAVE_PENDIENTES = ['alertas', 'pendientes'] as const

const formatoFecha = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' })

/**
 * Alertas de stock del dueño (RF-17). Las crea la base al cruzar el mínimo o agotarse; aquí se ven
 * con el contacto del proveedor y se marcan como atendidas. Recargar el stock (RF-18) también las atiende.
 */
export function Alertas() {
  const [vista, setVista] = useState<Vista>('pendientes')
  const idVista = useId()

  const pendientes = vista === 'pendientes'
  const consulta = useQuery({
    queryKey: pendientes ? CLAVE_PENDIENTES : ['alertas', 'atendidas'],
    queryFn: () => api<Alerta[]>(pendientes ? '/api/alertas' : '/api/alertas?atendida=true'),
    refetchInterval: pendientes ? INTERVALO_ALERTAS_MS : false,
  })
  const alertas = consulta.data

  return (
    <section>
      <div className="encabezado-pagina">
        <h1>Alertas de stock</h1>
      </div>

      <div className="filtros">
        <label htmlFor={idVista}>Mostrar</label>
        <select id={idVista} value={vista} onChange={(e) => setVista(e.target.value as Vista)}>
          <option value="pendientes">Pendientes</option>
          <option value="atendidas">Atendidas</option>
        </select>
      </div>

      {consulta.isPending ? (
        <p className="estado-pagina texto-suave">Cargando alertas…</p>
      ) : !alertas ? (
        <div className="estado-pagina">
          <MensajeError error={consulta.error} />
          <button type="button" className="boton" onClick={() => consulta.refetch()}>
            Reintentar
          </button>
        </div>
      ) : alertas.length === 0 ? (
        <p className="estado-pagina texto-suave">
          {pendientes ? 'No hay alertas pendientes. El stock está sobre el mínimo.' : 'No hay alertas atendidas.'}
        </p>
      ) : (
        <ul className="lista-alertas">
          {alertas.map((a) => (
            <li key={a.id}>
              <TarjetaAlerta alerta={a} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function TarjetaAlerta({ alerta: a }: { alerta: Alerta }) {
  const cliente = useQueryClient()
  const idTitulo = useId()
  const marcar = useMutation({
    mutationFn: () => api<Alerta>(`/api/alertas/${a.id}/atendida`, { metodo: 'PATCH' }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['alertas'] }),
  })
  const agotado = a.tipo === 'STOCK_AGOTADO'

  return (
    <article className="tarjeta-alerta" aria-labelledby={idTitulo}>
      <header className="tarjeta-alerta-cabecera">
        <h2 id={idTitulo}>{a.producto}</h2>
        <span className={`insignia ${agotado ? 'insignia-error' : 'insignia-aviso'}`}>
          {agotado ? 'Agotado' : 'Stock bajo'}
        </span>
        {a.atendida && <span className="insignia insignia-apagada">Atendida</span>}
      </header>
      <p>{a.mensaje}</p>
      <p className="texto-suave detalle">
        <span>{`Stock actual: ${a.cantidadDisponible} (mínimo ${a.stockMinimo})`}</span>
        {' · '}
        <time dateTime={a.fecha}>{formatoFecha.format(new Date(a.fecha))}</time>
      </p>
      <p className="detalle">
        {a.proveedor ? (
          <>
            {a.proveedor}
            {a.telefonoProveedor && (
              <>
                {' · '}
                <a href={`tel:${a.telefonoProveedor}`}>{a.telefonoProveedor}</a>
              </>
            )}
          </>
        ) : (
          <span className="texto-suave">Sin proveedor (preparación propia)</span>
        )}
      </p>
      {!a.atendida && (
        <div className="acciones-formulario">
          <button type="button" className="boton" disabled={marcar.isPending} onClick={() => marcar.mutate()}>
            Marcar como atendida
          </button>
          <Link to="/productos" className="enlace-accion">
            Recargar en Productos
          </Link>
        </div>
      )}
      <MensajeError error={marcar.error} />
    </article>
  )
}
