// Actualizador del tag de imagen del contenedor en wrangler.jsonc, para
// commit-and-tag-version.
//
// Reemplazo de texto, nunca JSON.parse + JSON.stringify: el archivo es JSONC y
// sus comentarios explican la colocacion del contenedor, las migraciones de los
// Durable Objects y el caveat de la imagen de develop. Un round-trip por JSON los
// borraria todos.
//
// Mientras develop no tenga imagen propia reutiliza la de produccion (lo dice el
// propio wrangler.jsonc), asi que las entradas de la serie vX.Y.Z son dos y se
// suben juntas: la release se prueba primero en develop con esa misma imagen.
// Una imagen develop-<sha> no entra en el patron (la comilla de cierre exige que
// el tag termine en X.Y.Z) y no se toca. Si las entradas vX.Y.Z apuntan a
// versiones distintas no hay forma segura de saber cual es la de produccion:
// se aborta.
const RELEASE_IMAGE =
  /("image":\s*"docker\.io\/luciaelabs\/labflow_backend:v)(\d+\.\d+\.\d+)(")/g;

function sharedVersionOrThrow(contents) {
  const versions = new Set([...contents.matchAll(RELEASE_IMAGE)].map(m => m[2]));
  if (versions.size !== 1) {
    throw new Error(
      `wrangler.jsonc: se esperaba una sola version de imagen vX.Y.Z y se encontraron ${versions.size} ` +
        `(${[...versions].join(', ') || 'ninguna'}). Fallar es preferible a bumpear la entrada equivocada en silencio.`,
    );
  }
  return [...versions][0];
}

module.exports.readVersion = function readVersion(contents) {
  return sharedVersionOrThrow(contents);
};

module.exports.writeVersion = function writeVersion(contents, version) {
  sharedVersionOrThrow(contents);
  return contents.replace(RELEASE_IMAGE, `$1${version}$3`);
};
