// Actualizador de la version del proyecto en pom.xml para commit-and-tag-version.
//
// Se escribe a mano en vez de usar el actualizador `maven` que trae la
// herramienta: ese reparsea el XML con fast-xml-parser y lo vuelve a serializar,
// lo que reformatea TODO el archivo (verificado: 379 lineas de diff sobre un
// pom.xml de 199, se pierden 14 lineas, se destruyen la declaracion <?xml?>, los
// atributos xmlns repartidos en varias lineas y el comentario en linea
// "<!-- lookup parent from repository -->"). Acá solo se toca la linea de la
// version.
//
// El ancla es el artifactId del propio proyecto, que aparece una sola vez en el
// archivo. Anclar solo en <version> tocaria la del <parent> (Spring Boot 4.0.6) o
// la de cualquier dependencia, y romper el build de una forma que parece un
// problema de dependencias y no un error de versionado.
const PROJECT_VERSION = /(<artifactId>labflowapi<\/artifactId>\s*<version>)([^<]+)(<\/version>)/;

function matchOrThrow(contents) {
  const match = contents.match(PROJECT_VERSION);
  if (!match) {
    throw new Error(
      'pom.xml: no se encontro <artifactId>labflowapi</artifactId> seguido de <version>. ' +
        'Si se renombro el artifactId, actualice este actualizador; fallar es preferible ' +
        'a dejar la version sin bumpear en silencio.',
    );
  }
  return match;
}

module.exports.readVersion = function readVersion(contents) {
  return matchOrThrow(contents)[2];
};

module.exports.writeVersion = function writeVersion(contents, version) {
  matchOrThrow(contents);
  return contents.replace(PROJECT_VERSION, `$1${version}$3`);
};
