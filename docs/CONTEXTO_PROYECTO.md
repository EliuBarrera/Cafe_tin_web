# Contexto del proyecto — Gestión Cafetín

> Pega este texto en **Instrucciones del proyecto** al crear el proyecto en Claude.

---

## Qué es el proyecto

Aplicación para la gestión de pedidos y stock de las cafeterías de la **Universidad César Vallejo (UCV)**.
Los estudiantes piden y pagan desde una app Android y recogen en el mostrador sin hacer fila;
el personal de la cafetería despacha los pedidos y administra el inventario desde un panel web.

**Curso:** Ingeniería de Software
**Periodo:** 14/09/2026 – 18/12/2026
**Gerente del proyecto:** Maria Elizabeth Ruiz Díaz
**Equipo:** José Daniel Lizca, Eliú Alejandro Barrera Martínez, Maria Elizabeth Ruiz Díaz

## Problema que resuelve

En hora punta se forman filas largas porque los estudiantes esperan para pedir y pagar en el mismo
mostrador, y el descanso entre clases es muy corto. Además, el inventario se lleva a mano, así que
el personal no sabe en tiempo real cuánto stock queda y los productos se agotan sin aviso.

## Actores

| Actor | Rol |
|---|---|
| Estudiante | Realiza, paga y recoge sus pedidos |
| Trabajador | Confirma, prepara, verifica y entrega los pedidos |
| Dueño | Administra productos, stock, proveedores e ingresos |

## Stack técnico

El sistema tiene **dos clientes** (app Android para estudiantes y panel web para la cafetería)
que consumen **una sola API REST**. Esa es la razón de separar backend de frontend.

| Capa | Tecnología | Por qué se eligió |
|---|---|---|
| App móvil | **Kotlin + Jetpack Compose** | Kotlin es el lenguaje oficial de Android desde 2019 y Compose es el toolkit de UI actual de Google. Menos código y menos verboso que Java con XML. |
| API REST | **Spring Boot 3 (Java 17+)** | Una sola API sirve a la app y al panel. Arquitectura en capas explícita (controller / service / repository), que es lo que el curso pide sustentar. |
| Persistencia | **Spring Data JPA + Hibernate** | Mapea las 14 entidades del diagrama de clases sin escribir SQL a mano para el CRUD. |
| Base de datos | **PostgreSQL 16** | Relacional, gratuito, soporta los ENUM, triggers y vistas del modelo físico. |
| Panel web | **React 18** o **Thymeleaf** | React si se quiere un frontend independiente; Thymeleaf si se prefiere simplificar y servirlo desde el mismo Spring Boot. |
| Notificaciones push | **Firebase Cloud Messaging (FCM)** | Es el único canal oficial de notificaciones push en Android (RF-08, RF-17). |
| Autenticación | **Spring Security + JWT** | Control de acceso por rol: el dueño ve inventario e ingresos, el trabajador no (RNF-05). |
| Control de versiones | **Git + GitHub** | Trabajo en paralelo de los tres integrantes. |
| Entorno de desarrollo | **Android Studio** (app) e **IntelliJ IDEA** o **VS Code** (API) | |

### Alternativa evaluada y descartada

**Flutter + Firebase (Firestore).** Se desarrolla más rápido y resuelve solo el tiempo real y las
notificaciones, pero usa una base NoSQL: no habría modelo relacional, ni integridad referencial,
ni arquitectura en capas que mostrar. Como el curso es Ingeniería de Software y el entregable
incluye diagrama de clases y modelo físico, se optó por el stack relacional en capas.

### Orden de construcción acordado

1. Modelo físico de la base de datos *(hecho)*
2. Estructura del proyecto Spring Boot y entidades JPA
3. API por paquetes: cuentas → productos e inventario → pedidos y pagos → despacho → reportes
4. App Android consumiendo la API
5. Panel web de la cafetería

## Requisitos funcionales por paquete

**Paquete 1 — Cuentas y acceso**
- RF-01 Registro e inicio de sesión de estudiantes
- RF-02 Registro e inicio de sesión de trabajadores y dueño, con roles diferenciados
- RF-03 Recuperar o restablecer la contraseña

**Paquete 2 — Pedidos**
- RF-04 Consultar el catálogo con disponibilidad en tiempo real
- RF-05 Armar un pedido (carrito) con uno o varios productos
- RF-06 Pagar el pedido desde la aplicación
- RF-07 Generar recibo o comprobante digital
- RF-08 Notificar al estudiante el estado de su pedido
- RF-09 Consultar el historial de pedidos

**Paquete 3 — Despacho**
- RF-10 Visualizar los pedidos entrantes en tiempo real
- RF-11 Confirmar la recepción de un pedido
- RF-12 Marcar un pedido como listo para recoger
- RF-13 Verificar el recibo del estudiante para la entrega
- RF-14 Marcar un pedido como entregado

**Paquete 4 — Inventario**
- RF-15 Registrar productos y su cantidad inicial en stock
- RF-16 Descontar el stock automáticamente en cada venta
- RF-17 Generar alerta al llegar al nivel mínimo de stock
- RF-18 Actualizar o recargar manualmente el stock
- RF-19 Dar de alta, editar o deshabilitar productos

**Paquete 5 — Ingresos**
- RF-20 Registrar cada venta realizada con su monto
- RF-21 Consultar ingresos por día, semana o mes
- RF-22 Consultar reportes de productos más vendidos

## Requisitos no funcionales

- RNF-01 Interfaz intuitiva: completar un pedido en pocos pasos
- RNF-02 Confirmar pedido y pago en menos de 5 segundos
- RNF-03 Soportar pedidos simultáneos en hora punta
- RNF-04 Cifrar datos de pago y datos personales
- RNF-05 Restringir inventario e ingresos solo al rol de dueño
- RNF-06 Disponible durante todo el horario de atención (99% uptime)
- RNF-07 Accesible desde Android y navegador web
- RNF-08 Escalable en usuarios y productos sin rediseño

## Modelo de datos

14 tablas derivadas del diagrama de clases. La herencia se implementa con especialización
(tabla por subtipo):

- `usuario` → `estudiante`, `trabajador`, `dueno`
- `notificacion` → `alerta_stock`
- `categoria`, `proveedor`, `producto`, `inventario` (1:1 con producto)
- `pedido`, `detalle_pedido` (composición), `pago` (1:1), `recibo` (1:1)

**Reglas automáticas implementadas como triggers:**
- `trg_calcular_total_pedido` — recalcula el total al cambiar el detalle (RF-05)
- `trg_descontar_stock` — descuenta el stock al confirmarse el pago (RF-16)
- `trg_alerta_stock_minimo` — genera alerta y oculta el producto agotado (RF-17, RF-19)

**Vistas de consulta:** `v_catalogo_disponible`, `v_pedidos_en_curso`, `v_ingresos_diarios`,
`v_productos_mas_vendidos`, `v_alertas_reposicion`.

## Cronograma (SDLC)

| Fase | Periodo |
|---|---|
| 1. Planificación e inicio | 15/09 – 25/09 |
| 2. Análisis de requerimientos | 28/09 – 16/10 |
| 3. Diseño del sistema | 19/10 – 06/11 |
| 4. Desarrollo e implementación | 09/11 – 04/12 |
| 5. Pruebas | 23/11 – 11/12 |
| 6. Despliegue y cierre | 07/12 – 18/12 |

## Convenciones acordadas

- Nombres de tablas y columnas en **snake_case** y en español
- Estados de pedido: `PENDIENTE`, `CONFIRMADO`, `EN_PREPARACION`, `LISTO`, `ENTREGADO`, `CANCELADO`
- Métodos de pago: `YAPE`, `PLIN`, `TARJETA`, `EFECTIVO`
- Los precios se guardan como `NUMERIC(8,2)`; `detalle_pedido` conserva el precio histórico
- Las contraseñas se almacenan como hash BCrypt, nunca en texto plano

## Archivos del proyecto

Subir a la base de conocimiento del proyecto:

- `Acta_Inicio_Cafetin_UCV.docx` — acta de constitución
- `Analisis_del_Problema_Gestion_Cafetin.docx` — requisitos, arquitectura y casos de uso
- `Instrumentos_Levantamiento_Requisitos_Gestion_Cafetin.docx` — encuesta y entrevista
- `Cronograma_Gestion_Cafetin.gan` — cronograma de GanttProject
- `Diagrama_Clases_Gestion_Cafetin.png` — diagrama de clases
- `Gestion_Cafetin.puml` / `.mdj` — diagrama de clases en PlantUML y StarUML
- `01_gestion_cafetin_schema.sql` — modelo físico de la base de datos
- `02_gestion_cafetin_datos.sql` — datos de prueba
