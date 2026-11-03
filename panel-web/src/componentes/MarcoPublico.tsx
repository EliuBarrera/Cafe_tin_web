import type { ReactNode } from 'react'

/** Tarjeta centrada de las pantallas sin sesión. */
export function MarcoPublico({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <main className="marco-publico">
      <div className="tarjeta">
        <p className="marca">Cafetín</p>
        <h1>{titulo}</h1>
        {children}
      </div>
    </main>
  )
}
