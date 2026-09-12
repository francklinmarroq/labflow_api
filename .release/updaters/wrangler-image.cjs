// Actualizador del tag de imagen del contenedor de PRODUCCION en wrangler.jsonc,
// para commit-and-tag-version.
//
// Reemplazo de texto, nunca JSON.parse + JSON.stringify: el archivo es JSONC y
// sus comentarios explican la colocacion del contenedor, las migraciones de los
// Durable Objects y el caveat de la imagen de develop. Un round-trip por JSON los
// borraria todos.
//
// Solo la entrada de produccion. La de develop es
// "labflow_backend:vX.Y.Z-develop" y el propio wrangler.jsonc dice que las
// builds de develop deben usar tags develop-<sha>, no la serie vX.Y.Z. La comilla
// de cierre en el patron es lo que la excluye: sin ella, "v1.7.0" tambien haria
// match dentro de "v1.7.0-develop" y se tocarian las dos.
const PROD_IMAGE =
  /("image":\s*"docker\.io\/luciaelabs\/labflow_backend:v)(\d+\.\d+\.\d+)(")/g;

function singleMatchOrThrow(contents) {
  const matches = [...contents.matchAll(PROD_IMAGE)];
  if (matches.length !== 1) {
    throw new Error(
      `wrangler.jsonc: se esperaba exactamente 1 imagen de produccion y se encontraron ${matches.length}. ` +
        'Fallar es preferible a bumpear la entrada equivocada (o ninguna) en silencio.',
    );
  }
  return matches[0];
}

module.exports.readVersion = function readVersion(contents) {
  return singleMatchOrThrow(contents)[2];
};

module.exports.writeVersion = function writeVersion(contents, version) {
  singleMatchOrThrow(contents);
  return contents.replace(PROD_IMAGE, `$1${version}$3`);
};
