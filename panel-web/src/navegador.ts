/** Navegación fuera del panel (p. ej. a Mercado Pago). Aparte para poder simularla en los tests. */
export function irA(url: string) {
  window.location.assign(url)
}
