# Manual de usuario

Gestión Cafetín tiene tres tipos de usuario. El **dueño** y el **trabajador** usan el panel web en
https://cafe-tin-web.vercel.app; el **estudiante** usa la app Android.

> Borrador del 09/11/2026, escrito a partir del panel publicado. La parte del estudiante describe el
> flujo de la API: se completará con las pantallas cuando exista la app.

## Para todos: entrar y recuperar la contraseña

1. Abre el panel y escribe tu **Correo** y tu **Contraseña** en **Iniciar sesión**.
2. Si no la recuerdas, pulsa **¿Olvidaste tu contraseña?**, escribe tu correo y pulsa
   **Enviar enlace**. Llega un correo con un enlace que vale **30 minutos** y sirve **una sola vez**.
3. El enlace abre **Crear contraseña nueva**. Escribe la nueva (mínimo 8 caracteres) y pulsa
   **Guardar contraseña**.
4. **Cerrar sesión** está arriba a la derecha. La sesión dura 8 horas; después el panel vuelve a
   pedir la contraseña.

El menú solo muestra lo que tu rol puede usar: el trabajador ve **Despacho**; el dueño ve todo.

## Trabajador: despachar pedidos

**Despacho** muestra los pedidos **ya pagados**, en columnas por estado y ordenados por hora de
recojo. Se actualiza solo cada 5 segundos.

| Columna | Qué hacer | Botón |
|---|---|---|
| Pendientes | Aceptar el pedido. Quedas como responsable | **Confirmar** |
| Confirmados | Empezar a prepararlo | **Empezar a preparar** |
| En preparación | Cuando está listo para recoger | **Marcar listo** |
| Listos | Entregar al estudiante (ver abajo) | **Verificar** y luego **Entregar** |

Cada tarjeta muestra el código del pedido (`PED-…`), la hora de recojo, el estudiante, el método de
pago y los productos. Al confirmar, marcar listo y entregar, el estudiante recibe un aviso en el celular.

**Entregar un pedido (RF-13, RF-14):**

1. Pide al estudiante el **código de su recibo**: tiene la forma `DR-0000-0000` y lo ve en su app. El
   panel nunca lo muestra, a propósito.
2. Escríbelo en **Código del recibo** y pulsa **Verificar**. Si coincide aparece **Recibo verificado**.
3. Pulsa **Entregar**. Sin el recibo verificado el botón no se activa.

Si dos personas tocan el mismo pedido a la vez, el panel avisa arriba que el pedido ya cambió de
estado; la tarjeta se mueve sola en el siguiente refresco.

## Dueño

El dueño también puede despachar (como el trabajador) y además administra la cafetería.

### Productos (RF-15, RF-18, RF-19)

- **Nuevo producto**: nombre, categoría, precio, proveedor (o *Sin proveedor (preparación propia)*),
  **stock inicial** y **stock mínimo**. El stock mínimo decide cuándo llega una alerta.
- **Editar**: cambia datos y stock mínimo. El stock no se edita: se recarga.
- **Recargar stock**: suma las **Unidades a agregar** a las que hay.
- **Deshabilitar** / **Habilitar**: un producto deshabilitado deja de verse en el catálogo del
  estudiante, pero conserva su historial.
- La columna **Estado** dice *Disponible*, *Pocas unidades* o *Agotado*. Un producto agotado sigue
  habilitado: vuelve solo al recargarlo.

El stock baja **solo**, cuando un pago se confirma (RF-16). Si dos estudiantes pagan la última
unidad a la vez, solo uno lo logra; el otro recibe "stock insuficiente" y no se le cobra.

### Categorías y Proveedores

- **Nueva categoría** / **Nuevo proveedor**, **Editar**.
- Un proveedor se puede **Desactivar** y volver a **Activar**; desactivado, no se ofrece al crear
  productos.
- Una categoría desactivada **no se puede reactivar desde el panel**, por eso pide confirmación.

### Alertas (RF-17)

Cuando el stock de un producto **baja del mínimo** o **se agota**, llega una alerta (una sola vez por
cada cruce, no en cada venta). El número junto a **Alertas** en el menú son las pendientes.

- Cada alerta muestra el producto, *Stock bajo* o *Agotado*, el stock actual y el **teléfono del
  proveedor** (se puede llamar desde el celular).
- **Marcar como atendida** cuando ya pediste reposición. **Recargar stock** también las atiende.
- **Mostrar: Atendidas** para ver el historial.

### Reportes (RF-20, RF-21, RF-22)

- **Ingresos**: total, pedidos pagados y ticket promedio; **Agrupar** por día, semana o mes.
- **Más vendidos**: unidades e ingreso por producto, desde el inicio.
- **Ventas**: cada pago con fecha, pedido, estudiante y método.
- **Desde** / **Hasta** filtran por fechas. Sin fechas se muestran los últimos 30 días.

Solo cuentan los pagos confirmados.

### Trabajadores (RF-02)

- **Nuevo trabajador**: nombre, apellidos, correo, teléfono (opcional, 9 dígitos), código de
  empleado, turno y **Contraseña inicial** (mínimo 8 caracteres). Entrégale la contraseña: luego
  puede cambiarla con *¿Olvidaste tu contraseña?*.
- **Desactivar**: el trabajador deja de poder entrar **en el acto**, aunque tenga la sesión abierta.
  **Activar** lo deshace.
- Los trabajadores no se editan: si un dato está mal, desactívalo y registra uno nuevo.

## Estudiante (app Android)

> Pendiente: la app todavía no existe. Este es el flujo que la API ya permite.

1. **Registrarse** con nombre, apellidos, correo, código de estudiante, carrera, ciclo y contraseña.
2. Ver el **catálogo**: los productos agotados aparecen marcados y no se pueden pedir.
3. Armar el **pedido**, elegir la **hora de recojo** y pagar con **Yape**, **Plin** o **tarjeta**
   (la pasarela es simulada; no hay pago en efectivo en esta versión). Si el pago se rechaza, el
   pedido se conserva y se puede reintentar.
4. Tras pagar llega el **recibo** con su código `DR-…`: es lo que se muestra al recoger.
5. Avisos en el celular cuando el pedido se **confirma**, está **listo** y se **entrega**. Si el
   aviso no llega, la app los muestra igual al abrirse.
6. Un pedido **sin pagar** se puede cancelar; uno pagado, no (no hay reembolsos en esta versión).
7. **Historial** con todos sus pedidos.
