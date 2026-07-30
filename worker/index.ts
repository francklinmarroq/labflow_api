import { Container, getContainer } from "@cloudflare/containers";

interface Env {
  API_CONTAINER: DurableObjectNamespace<LabflowApiContainer>;

  // Secrets (`wrangler secret put <NOMBRE>`).
  DB_URL: string;
  DB_USERNAME: string;
  DB_PASSWORD: string;
  JWT_SECRET: string;
  RESEND_API_KEY: string;
  R2_ACCESS_KEY_ID: string;
  R2_SECRET_ACCESS_KEY: string;

  // Vars de wrangler.jsonc.
  MAIL_FROM: string;
  FRONTEND_BASE_URL: string;
  R2_ENDPOINT: string;
  R2_BUCKET: string;
}

export class LabflowApiContainer extends Container<Env> {
  defaultPort = 8080;

  sleepAfter = "1m";

  envVars = {
    DB_URL: this.env.DB_URL,
    DB_USERNAME: this.env.DB_USERNAME,
    DB_PASSWORD: this.env.DB_PASSWORD,
    JWT_SECRET: this.env.JWT_SECRET,
    RESEND_API_KEY: this.env.RESEND_API_KEY,
    MAIL_FROM: this.env.MAIL_FROM,
    FRONTEND_BASE_URL: this.env.FRONTEND_BASE_URL,
    R2_ENDPOINT: this.env.R2_ENDPOINT,
    R2_BUCKET: this.env.R2_BUCKET,
    R2_ACCESS_KEY_ID: this.env.R2_ACCESS_KEY_ID,
    R2_SECRET_ACCESS_KEY: this.env.R2_SECRET_ACCESS_KEY,
  };

  override async fetch(request: Request): Promise<Response> {
    return this.containerFetch(request, this.defaultPort);
  }

  override onStart() {
    console.log("labflow-api: contenedor nativo iniciado correctamente");
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
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization",
        },
      });
    }

    return getContainer(env.API_CONTAINER).fetch(request);
  },
} satisfies ExportedHandler<Env>;