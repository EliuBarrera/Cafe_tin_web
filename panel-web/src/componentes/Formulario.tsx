import type { ReactNode } from 'react'

/** Etiqueta con su control dentro: el texto de la etiqueta es el nombre accesible del campo. */
export function Campo({ etiqueta, ancho, children }: { etiqueta: string; ancho?: boolean; children: ReactNode }) {
  return (
    <label className={ancho ? 'campo-ancho' : undefined}>
      {etiqueta}
      {children}
    </label>
  )
}

/** Texto vacío = campo ausente: no viaja en el JSON y la API lo guarda nulo. */
export const opcional = (texto: string) => (texto.trim() === '' ? undefined : texto.trim())
