import { defineRailway, project, service } from "railway/iac";

// Configuracion del servicio pocket-api en Railway. Railway no lee este archivo
// al desplegar: se aplica a mano con `railway config plan` y
// `railway config apply`, y queda guardada en el servicio.
//
// Administra solo este servicio (partial): la MySQL compartida del proyecto
// demos, las otras APIs, los dominios y las variables siguen en el panel.
export const partial = "pocket-api";

export default defineRailway(() => {
  const api = service("pocket-api", {
    // Sin fuente a proposito: este servicio se sube con `railway up`.
    // Explicito: el servicio tiene guardado RAILPACK y, sin esto, el proximo
    // deploy volveria a la autodeteccion en lugar de la imagen del Dockerfile.
    build: { builder: "DOCKERFILE", dockerfilePath: "Dockerfile" },
    // La sonda de Actuator, sin detalles. Railway no da por bueno un deploy
    // hasta que responde UP.
    healthcheck: "/actuator/health",
    healthcheckTimeout: 180,
    deploy: {
      // La politica ya es ON_FAILURE por defecto (Railway la guarda vacia);
      // solo se acota la cantidad de reintentos.
      restartPolicyMaxRetries: 3,
      // Railway lo duerme tras 10 minutos sin trafico de salida y lo despierta
      // con el proximo pedido: casi nada pasa el filtro de Cloudflare, y una JVM
      // ociosa las 24 horas era casi toda la factura. Necesita DB_POOL_MIN=0 en
      // las variables; con una conexion abierta, los keepalive de Hikari a MySQL
      // cuentan como trafico y el servicio no llega a dormirse nunca.
      sleepApplication: true,
    },
  });
  return project("demos", {
    // Las variables son secretos y se cargan en el panel: este archivo nunca
    // las lee ni las escribe.
    variables: { managed: false },
    resources: [api],
  });
});
