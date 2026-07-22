import { Container, getContainer } from "@cloudflare/containers";

interface Env {
  API_CONTAINER: DurableObjectNamespace<LabflowApiContainer>;

  // Secrets (`wrangler secret put <NOMBRE>`).
  DB_URL: string;
  DB_USERNAME: string;
  DB_PASSWORD: string;
  JWT_SECRET: string;
  RESEND_API_KEY: string;

  // Vars de wrangler.jsonc.
  MAIL_FROM: string;
  FRONTEND_BASE_URL: string;
}

export class LabflowApiContainer extends Container<Env> {
  // Tiene que coincidir con SERVER_PORT del Dockerfile.
  defaultPort = 8080;

  // Arrancar la JVM y que Hibernate valide el esquema cuesta bastante mas que
  // arrancar un proceso Node, asi que conviene que el contenedor no se duerma
  // apenas hay una pausa: cada siesta se paga con un arranque en frio completo
  // en el siguiente request.
  sleepAfter = "30m";

  // Spring lee estas variables desde application.properties. Van por aca y no
  // en el Dockerfile para que las credenciales no queden dentro de la imagen.
  envVars = {
    DB_URL: this.env.DB_URL,
    DB_USERNAME: this.env.DB_USERNAME,
    DB_PASSWORD: this.env.DB_PASSWORD,
    JWT_SECRET: this.env.JWT_SECRET,
    RESEND_API_KEY: this.env.RESEND_API_KEY,
    MAIL_FROM: this.env.MAIL_FROM,
    FRONTEND_BASE_URL: this.env.FRONTEND_BASE_URL,
  };

  // El `fetch` que trae Container espera 20 s a que el puerto abra y despues se
  // rinde. La JVM tarda bastante mas que eso, asi que hay que ampliar la espera
  // a mano o el primer request siempre falla con "error connecting to the port".
  override async fetch(request: Request): Promise<Response> {
    await this.startAndWaitForPorts(this.defaultPort, {
      instanceGetTimeoutMS: 30_000,
      portReadyTimeoutMS: 300_000,
    });
    return this.containerFetch(request, this.defaultPort);
  }

  override onStart() {
    console.log("labflow-api: contenedor arriba");
  }

  override onStop() {
    console.log("labflow-api: contenedor detenido");
  }

  override onError(error: unknown) {
    console.error("labflow-api: error del contenedor", error);
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    // Instancia unica: todos los requests van al mismo contenedor, que conserva
    // su pool de conexiones a Postgres entre llamadas.
    return getContainer(env.API_CONTAINER).fetch(request);
  },
} satisfies ExportedHandler<Env>;
