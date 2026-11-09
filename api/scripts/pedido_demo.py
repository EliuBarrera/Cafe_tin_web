#!/usr/bin/env python3
"""Hace de estudiante en la demo mientras no exista la app: crea un pedido, lo paga e imprime el
código del pedido y el del recibo, que el trabajador pide para verificar y entregar (RF-13).

    python3 api/scripts/pedido_demo.py https://<servicio>.onrender.com
    python3 api/scripts/pedido_demo.py http://localhost:8080 --producto "Café pasado" --cantidad 2

Usa las cuentas que crea datos_demo.py (por defecto estudiante.demo1@ucvvirtual.edu.pe) y pide su
contraseña sin mostrarla. Solo usa la biblioteca estándar de Python.
"""

import argparse
import getpass
import sys

from datos_demo import Api, iniciar_sesion


def main():
    parser = argparse.ArgumentParser(description="Pedido pagado de un estudiante de demo.")
    parser.add_argument("base_url", help="URL de la API")
    parser.add_argument("--estudiante", default="estudiante.demo1@ucvvirtual.edu.pe")
    parser.add_argument("--producto", help="nombre exacto; por defecto el primero disponible")
    parser.add_argument("--cantidad", type=int, default=1)
    parser.add_argument("--hora", default="12:30", help="hora de recojo, HH:mm")
    parser.add_argument("--metodo", default="YAPE", choices=["YAPE", "PLIN", "TARJETA"])
    args = parser.parse_args()

    api = Api(args.base_url)
    token, rol = iniciar_sesion(api, args.estudiante, getpass.getpass(f"Contraseña de {args.estudiante}: "))
    if rol != "ESTUDIANTE":
        sys.exit(f"{args.estudiante} no es estudiante (rol {rol}).")

    _, catalogo = api.exigir({200}, "GET", "/api/catalogo", token=token)
    disponibles = [p for p in catalogo if p["estadoStock"] != "AGOTADO"]
    if args.producto:
        disponibles = [p for p in disponibles if p["nombre"] == args.producto]
    if not disponibles:
        sys.exit("No hay un producto disponible con ese nombre.")
    producto = disponibles[0]

    _, pedido = api.exigir({201}, "POST", "/api/pedidos", {
        "horaRecojo": args.hora,
        "items": [{"idProducto": producto["idProducto"], "cantidad": args.cantidad}],
    }, token)
    _, pagado = api.exigir({200}, "POST", f"/api/pedidos/{pedido['id']}/pago", {"metodoPago": args.metodo}, token)

    print(f"\n{args.cantidad} × {producto['nombre']} · recojo {args.hora} · {args.metodo} · S/ {pagado['total']}")
    print(f"  Pedido: {pagado['codigoPedido']}  (aparece en Pendientes del despacho)")
    print(f"  Recibo: {pagado['recibo']['codigoVerificacion']}  (el trabajador lo pide para entregar)")


if __name__ == "__main__":
    main()
