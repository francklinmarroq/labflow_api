# Changelog

All notable changes to this project will be documented in this file. See [commit-and-tag-version](https://github.com/absolute-version/commit-and-tag-version) for commit guidelines.

## [1.10.1](https://github.com/francklinmarroq/labflow_api/compare/v1.9.0...v1.10.1) (2026-09-14)

### Funcionalidades

* emitir facturas a nombre de empresas y aseguradoras ([f5998e8](https://github.com/francklinmarroq/labflow_api/commit/f5998e84da25147f6a12fea29f78743d0c734162))

### Rendimiento

* **historial paciente:** cortar N+1 profundo con @BatchSize + memoria de rangos ([169660d](https://github.com/francklinmarroq/labflow_api/commit/169660d897180a534a76b83b7392b83938695b78))
* **orders:** embeber la identidad del paciente en LabOrderDTO (evita GET /customers/{id} en impresión/sobre) ([a753112](https://github.com/francklinmarroq/labflow_api/commit/a75311268cdd15fa5a7be870fca2fb5e65a94e8b))
## [1.9.0](https://github.com/francklinmarroq/labflow_api/compare/v1.8.0...v1.9.0) (2026-09-13)

### Funcionalidades

* bloquear los examenes de una orden ya facturada ([d978557](https://github.com/francklinmarroq/labflow_api/commit/d978557336fe9ab46809d74d6df59f7fdcb898c2))

### Correcciones

* registrar en schema.sql las columnas del perfil de examen ([5ef7013](https://github.com/francklinmarroq/labflow_api/commit/5ef701398efc9c63da8acb765e66a83524556dd0))
## [1.8.0](https://github.com/francklinmarroq/labflow_api/compare/v1.7.0...v1.8.0) (2026-09-12)

### Funcionalidades

* recordar por usuario la ultima version de novedades vista ([5a8db18](https://github.com/francklinmarroq/labflow_api/commit/5a8db1822e85b91cc3c40f6314df5bf14b37015c))

### Correcciones

* registrar el DDL de adjuntos por examen (editor de examenes caido en prod) ([b70b26a](https://github.com/francklinmarroq/labflow_api/commit/b70b26aa76592423fc13ea54f224d77a5d0ae30d))
