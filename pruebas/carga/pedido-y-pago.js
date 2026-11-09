// Prueba de carga de la hora punta (RNF-02, RNF-03): muchos estudiantes a la vez ven el catálogo,
// crean un pedido de una unidad y lo pagan. Cómo correrla: pruebas/carga/README.md.
//
//   RNF-02: pedido + pago en menos de 5 s (p95 de la métrica pedido_y_pago).
//   RNF-03: con concurrencia, el stock nunca queda negativo; el que llega tarde recibe 409.
//
// Crea estudiantes y pedidos reales: úsala contra una base de prueba, nunca contra producción.

import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const ESTUDIANTES = Number(__ENV.ESTUDIANTES || 40);
const METODOS = ['YAPE', 'PLIN', 'TARJETA'];

const pedidoYPago = new Trend('pedido_y_pago', true);
const pagados = new Counter('pedidos_pagados');
const sinStock = new Counter('pedidos_sin_stock');
const inesperadas = new Rate('respuestas_inesperadas');

// 409 STOCK_INSUFICIENTE es una respuesta correcta cuando se acaba el stock: no cuenta como fallo.
http.setResponseCallback(http.expectedStatuses(200, 201, 409));

export const options = {
	setupTimeout: '3m',
	scenarios: {
		hora_punta: {
			executor: 'ramping-vus',
			startVUs: 0,
			stages: [
				{ duration: '30s', target: ESTUDIANTES },
				{ duration: '1m', target: ESTUDIANTES },
				{ duration: '15s', target: 0 },
			],
			gracefulRampDown: '10s',
		},
	},
	thresholds: {
		pedido_y_pago: ['p(95)<5000'],
		respuestas_inesperadas: ['rate<0.01'],
		http_req_failed: ['rate<0.01'],
	},
};

export function setup() {
	if (/onrender\.com|vercel\.app|neon\.tech/.test(BASE) && __ENV.CONFIRMO_NO_ES_PRODUCCION !== 'si') {
		fail(`BASE_URL=${BASE} parece producción. Esta prueba crea estudiantes y pedidos reales.`);
	}
	const sello = Date.now();
	const tokens = [];
	for (let i = 0; i < ESTUDIANTES; i++) {
		const r = http.post(`${BASE}/api/auth/registro`, JSON.stringify({
			nombre: 'Carga',
			apellidos: `Estudiante ${i}`,
			correo: `carga.${sello}.${i}@ucvvirtual.edu.pe`,
			contrasena: 'Carga-2026',
			codigo: `K${String(sello).slice(-8)}${String(i).padStart(3, '0')}`,
			carrera: 'Ingeniería de Sistemas',
			ciclo: 6,
		}), { headers: { 'Content-Type': 'application/json' } });
		if (r.status !== 201) {
			fail(`No se pudo registrar al estudiante ${i}: ${r.status} ${r.body}`);
		}
		tokens.push(r.json('token'));
	}
	return { tokens };
}

export default function (datos) {
	const token = datos.tokens[(exec.vu.idInTest - 1) % datos.tokens.length];
	const json = { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };

	const catalogo = http.get(`${BASE}/api/catalogo`, json);
	if (!check(catalogo, { 'catálogo 200': (r) => r.status === 200 })) {
		inesperadas.add(1);
		return;
	}
	const disponibles = catalogo.json().filter((p) => p.estadoStock !== 'AGOTADO' && p.cantidadDisponible > 0);
	if (disponibles.length === 0) {
		sinStock.add(1);
		sleep(1);
		return;
	}
	const producto = disponibles[Math.floor(Math.random() * disponibles.length)];

	const inicio = Date.now();
	const pedido = http.post(`${BASE}/api/pedidos`, JSON.stringify({
		horaRecojo: '12:30',
		items: [{ idProducto: producto.idProducto, cantidad: 1 }],
	}), json);
	if (pedido.status === 409) { // otro estudiante se llevó la última unidad entre el catálogo y el pedido
		sinStock.add(1);
		inesperadas.add(0);
		return;
	}
	if (!check(pedido, { 'pedido 201': (r) => r.status === 201 })) {
		inesperadas.add(1);
		return;
	}

	const metodo = METODOS[Math.floor(Math.random() * METODOS.length)];
	const pago = http.post(`${BASE}/api/pedidos/${pedido.json('id')}/pago`, JSON.stringify({ metodoPago: metodo }), json);
	if (pago.status === 409) { // RNF-03: la base rechazó dejar el stock negativo
		check(pago, { '409 dice STOCK_INSUFICIENTE': (r) => r.json('codigo') === 'STOCK_INSUFICIENTE' });
		sinStock.add(1);
		inesperadas.add(0);
		return;
	}
	const ok = check(pago, {
		'pago 200': (r) => r.status === 200,
		'pago con recibo': (r) => Boolean(r.json('recibo.codigoVerificacion')),
	});
	inesperadas.add(!ok);
	if (ok) {
		pedidoYPago.add(Date.now() - inicio);
		pagados.add(1);
	}
	sleep(1 + Math.random() * 2); // el estudiante no vuelve a pedir al instante
}
