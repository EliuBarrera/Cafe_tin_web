#!/usr/bin/env python3
"""Carga los datos de la demo (Fase 7) a través de la API: catálogo, dos trabajadores y estudiantes.

Usa la API y no SQL para que todo pase por las mismas validaciones y el BCrypt de la API. Se puede
correr varias veces: lo que ya existe (mismo nombre, correo o código) no se duplica.

    python3 api/scripts/datos_demo.py https://<servicio>.onrender.com --dueno correo@dominio.pe
    python3 api/scripts/datos_demo.py http://localhost:8080 --dueno ... --ventas 12

Pide la contraseña del dueño y una contraseña para las cuentas de demo, sin mostrarlas. El dueño debe
existir (docs/despliegue.md, paso 3). Solo usa la biblioteca estándar de Python.
"""

import argparse
import getpass
import json
import random
import sys
import urllib.error
import urllib.request

CATEGORIAS = [
    ("Menú", "Platos de fondo del día"),
    ("Bebidas", "Bebidas frías y calientes"),
    ("Snacks", "Para llevar entre clases"),
    ("Postres", "Dulces de la casa"),
]

PROVEEDORES = [
    ("Distribuidora Nor-Sur", "044123456", "ventas@norsur.example"),
    ("Panadería San Martín", "044654321", "pedidos@sanmartin.example"),
]

# nombre, categoría, proveedor (None = preparación propia), precio, stock inicial, stock mínimo
PRODUCTOS = [
    ("Menú del día", "Menú", None, "9.50", 60, 15),
    ("Arroz con pollo", "Menú", None, "10.00", 40, 10),
    ("Tallarines rojos", "Menú", None, "9.00", 35, 10),
    ("Café pasado", "Bebidas", None, "2.50", 80, 20),
    ("Chicha morada", "Bebidas", None, "2.00", 50, 12),
    ("Agua sin gas 625 ml", "Bebidas", "Distribuidora Nor-Sur", "1.50", 48, 12),
    ("Gaseosa 500 ml", "Bebidas", "Distribuidora Nor-Sur", "2.50", 36, 10),
    ("Pan con pollo", "Snacks", "Panadería San Martín", "4.50", 30, 8),
    ("Empanada de carne", "Snacks", "Panadería San Martín", "4.00", 25, 8),
    ("Chifles 50 g", "Snacks", "Distribuidora Nor-Sur", "2.00", 30, 10),
    ("Galletas de avena", "Snacks", "Distribuidora Nor-Sur", "1.50", 6, 10),  # empieza bajo el mínimo
    ("Arroz con leche", "Postres", None, "3.00", 20, 6),
    ("Gelatina", "Postres", None, "2.00", 0, 5),  # agotado: el catálogo lo muestra AGOTADO
]

TRABAJADORES = [
    ("Carmen", "Díaz Morales", "trabajador.demo1@cafetin.pe", "TRB-D01", "MANANA"),
    ("Luis", "Quispe Rojas", "trabajador.demo2@cafetin.pe", "TRB-D02", "TARDE"),
]

ESTUDIANTES = [
    ("Ana", "Torres Vega", "Ingeniería de Sistemas", 6),
    ("Bruno", "Salazar Ríos", "Administración", 4),
    ("Camila", "Mendoza Cruz", "Psicología", 2),
    ("Diego", "Paredes León", "Ingeniería Civil", 8),
    ("Elena", "Ramos Flores", "Derecho", 5),
]


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")

    def llamar(self, metodo, ruta, cuerpo=None, token=None):
        datos = None if cuerpo is None else json.dumps(cuerpo).encode("utf-8")
        peticion = urllib.request.Request(self.base + ruta, data=datos, method=metodo)
        peticion.add_header("Accept", "application/json")
        if datos is not None:
            peticion.add_header("Content-Type", "application/json")
        if token:
            peticion.add_header("Authorization", "Bearer " + token)
        try:
            # Render gratis puede tardar minutos en despertar la API: espera generosa.
            with urllib.request.urlopen(peticion, timeout=300) as respuesta:
                texto = respuesta.read().decode("utf-8")
                return respuesta.status, (json.loads(texto) if texto else None)
        except urllib.error.HTTPError as e:
            texto = e.read().decode("utf-8")
            try:
                return e.code, json.loads(texto)
            except ValueError:
                return e.code, {"codigo": "?", "mensaje": texto[:200]}

    def exigir(self, esperados, metodo, ruta, cuerpo=None, token=None):
        estado, datos = self.llamar(metodo, ruta, cuerpo, token)
        if estado not in esperados:
            mensaje = datos.get("mensaje") if isinstance(datos, dict) else datos
            sys.exit(f"Error: {metodo} {ruta} respondió {estado}: {mensaje}")
        return estado, datos


def pedir_contrasena(texto):
    clave = getpass.getpass(texto)
    if len(clave) < 8:
        sys.exit("La contraseña debe tener al menos 8 caracteres (la API exige lo mismo).")
    return clave


def iniciar_sesion(api, correo, clave):
    _, datos = api.exigir({200}, "POST", "/api/auth/login", {"correo": correo, "contrasena": clave})
    return datos["token"], datos["usuario"]["rol"]


def cargar_catalogo(api, token):
    _, existentes = api.exigir({200}, "GET", "/api/categorias", token=token)
    categorias = {c["nombre"]: c["id"] for c in existentes}
    for nombre, descripcion in CATEGORIAS:
        if nombre not in categorias:
            _, c = api.exigir({201}, "POST", "/api/categorias", {"nombre": nombre, "descripcion": descripcion}, token)
            categorias[nombre] = c["id"]
            print(f"  + categoría {nombre}")

    _, existentes = api.exigir({200}, "GET", "/api/proveedores", token=token)
    proveedores = {p["nombre"]: p["id"] for p in existentes}
    for nombre, telefono, correo in PROVEEDORES:
        if nombre not in proveedores:
            _, p = api.exigir({201}, "POST", "/api/proveedores",
                              {"nombre": nombre, "telefono": telefono, "correo": correo}, token)
            proveedores[nombre] = p["id"]
            print(f"  + proveedor {nombre}")

    _, existentes = api.exigir({200}, "GET", "/api/productos", token=token)
    productos = {p["nombre"] for p in existentes}
    for nombre, categoria, proveedor, precio, stock, minimo in PRODUCTOS:
        if nombre in productos:
            continue
        api.exigir({201}, "POST", "/api/productos", {
            "nombre": nombre,
            "idCategoria": categorias[categoria],
            "idProveedor": proveedores[proveedor] if proveedor else None,
            "precio": float(precio),
            "stockInicial": stock,
            "stockMinimo": minimo,
        }, token)
        print(f"  + producto {nombre} ({stock} u.)")


def cargar_trabajadores(api, token, clave):
    _, existentes = api.exigir({200}, "GET", "/api/trabajadores", token=token)
    correos = {t["correo"] for t in existentes}
    for nombre, apellidos, correo, codigo, turno in TRABAJADORES:
        if correo in correos:
            continue
        api.exigir({201}, "POST", "/api/trabajadores", {
            "nombre": nombre, "apellidos": apellidos, "correo": correo, "contrasena": clave,
            "codigoEmpleado": codigo, "turno": turno,
        }, token)
        print(f"  + trabajador {correo}")


def cargar_estudiantes(api, clave):
    tokens = []
    for i, (nombre, apellidos, carrera, ciclo) in enumerate(ESTUDIANTES, start=1):
        correo = f"estudiante.demo{i}@ucvvirtual.edu.pe"
        estado, datos = api.exigir({201, 409}, "POST", "/api/auth/registro", {
            "nombre": nombre, "apellidos": apellidos, "correo": correo, "contrasena": clave,
            "codigo": f"DEMO{i:06d}", "carrera": carrera, "ciclo": ciclo,
        })
        if estado == 201:
            print(f"  + estudiante {correo}")
            tokens.append(datos["token"])
        else:  # ya existía: entra con la contraseña de demo
            tokens.append(iniciar_sesion(api, correo, clave)[0])
    return tokens


def generar_ventas(api, tokens, cantidad):
    """Pedidos pagados para que los reportes (RF-20 a RF-22) tengan datos en la demo."""
    _, catalogo = api.exigir({200}, "GET", "/api/catalogo", token=tokens[0])
    disponibles = [p for p in catalogo if p["estadoStock"] != "AGOTADO"]
    pagados = 0
    for n in range(cantidad):
        producto = random.choice(disponibles)
        token = tokens[n % len(tokens)]
        estado, pedido = api.llamar("POST", "/api/pedidos", {
            "horaRecojo": random.choice(["11:30", "12:15", "12:50", "13:20"]),
            "items": [{"idProducto": producto["idProducto"], "cantidad": random.randint(1, 2)}],
        }, token)
        if estado != 201:  # sin stock suficiente: se intenta con otro en la siguiente vuelta
            continue
        metodo = random.choice(["YAPE", "PLIN", "TARJETA"])
        estado, _ = api.llamar("POST", f"/api/pedidos/{pedido['id']}/pago", {"metodoPago": metodo}, token)
        pagados += estado == 200
    print(f"  + {pagados} pedidos pagados de {cantidad} intentos")


def main():
    parser = argparse.ArgumentParser(description="Datos de demo de Gestión Cafetín a través de la API.")
    parser.add_argument("base_url", help="URL de la API, p. ej. https://cafetin-api.onrender.com")
    parser.add_argument("--dueno", required=True, help="correo del dueño creado en el paso 3 de docs/despliegue.md")
    parser.add_argument("--ventas", type=int, default=0, help="pedidos pagados de ejemplo para los reportes")
    parser.add_argument("--si", action="store_true", help="no pedir confirmación")
    args = parser.parse_args()

    api = Api(args.base_url)
    if not args.si:
        print(f"Se crearán catálogo, {len(TRABAJADORES)} trabajadores y {len(ESTUDIANTES)} estudiantes en {api.base}"
              + (f", y {args.ventas} pedidos pagados" if args.ventas else "") + ".")
        if input("Escribe SI para continuar: ").strip() != "SI":
            sys.exit("Cancelado.")

    clave_dueno = pedir_contrasena(f"Contraseña de {args.dueno}: ")
    clave_demo = pedir_contrasena("Contraseña para las cuentas de demo (trabajadores y estudiantes): ")

    print("Conectando (si la API dormía en Render, puede tardar unos minutos)…")
    token, rol = iniciar_sesion(api, args.dueno, clave_dueno)
    if rol != "DUENO":
        sys.exit(f"{args.dueno} no es dueño (rol {rol}).")

    print("Catálogo:")
    cargar_catalogo(api, token)
    print("Trabajadores:")
    cargar_trabajadores(api, token, clave_demo)
    print("Estudiantes:")
    tokens = cargar_estudiantes(api, clave_demo)
    if args.ventas:
        print("Ventas:")
        generar_ventas(api, tokens, args.ventas)

    print("\nListo. Cuentas de demo (todas con la contraseña que elegiste):")
    for _, _, correo, _, _ in TRABAJADORES:
        print(f"  trabajador  {correo}")
    for i in range(1, len(ESTUDIANTES) + 1):
        print(f"  estudiante  estudiante.demo{i}@ucvvirtual.edu.pe")


if __name__ == "__main__":
    main()
