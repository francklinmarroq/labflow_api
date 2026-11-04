# Changelog

All notable changes to this project will be documented in this file. See [commit-and-tag-version](https://github.com/absolute-version/commit-and-tag-version) for commit guidelines.

## [1.15.1](https://github.com/francklinmarroq/labflow_api/compare/v1.15.0...v1.15.1) (2026-11-04)

### Funcionalidades

* catálogo inicial con cuatro grupos de edad sin solaparse ([3163b21](https://github.com/francklinmarroq/labflow_api/commit/3163b21ec24bbc5be1cb474df7e8f6864abcbc6f))

### Correcciones

* la exportación de órdenes a Excel fallaba en la imagen nativa ([0c4b784](https://github.com/francklinmarroq/labflow_api/commit/0c4b784db9b267b2fb5c227ce73d00f7599d78af))
## [1.15.0](https://github.com/francklinmarroq/labflow_api/compare/v1.14.1...v1.15.0) (2026-11-03)

### Funcionalidades

* cotizaciones con vigencia, edición, recotización y conversión a orden ([663fe90](https://github.com/francklinmarroq/labflow_api/commit/663fe907ab6ec679215dbd79d0026c337c90c94d))
* exportar a Excel las órdenes filtradas ([a6dc3f0](https://github.com/francklinmarroq/labflow_api/commit/a6dc3f024281c61844472bef290c30fc02462eb2))
* mejorar el catálogo inicial de exámenes ([c2c0107](https://github.com/francklinmarroq/labflow_api/commit/c2c01071dba4ea973fc0a674e1dae217a4df5d3e))
## [1.14.1](https://github.com/francklinmarroq/labflow_api/compare/v1.14.0...v1.14.1) (2026-11-02)

### Rendimiento

* el estado de cuenta trae los pagos en una sola consulta (corta un N+1) ([93be02b](https://github.com/francklinmarroq/labflow_api/commit/93be02b4b7487f0382af19611335da11fcb7739c))
## [1.14.0](https://github.com/francklinmarroq/labflow_api/compare/v1.12.0...v1.14.0) (2026-10-28)

### Funcionalidades

* compras a proveedores con cuentas por pagar ([171e607](https://github.com/francklinmarroq/labflow_api/commit/171e6076e753b8e19cef3fd0064574eb49171943))
* estados financieros y cierre de período contable ([12b8b87](https://github.com/francklinmarroq/labflow_api/commit/12b8b879a11b7b0f26c63e84fdad1261d9d08515))
* facturar sin CAI y tamaño de papel de la factura ([d95eb39](https://github.com/francklinmarroq/labflow_api/commit/d95eb3914dd91600fb87dbaf34ecd65a53cc1cc2))
* facturas de varias órdenes o sin orden y registro de ventas detallado ([e623f81](https://github.com/francklinmarroq/labflow_api/commit/e623f81851069ab57d544adc20ef2dac5bd6c6e1))

### Rendimiento

* batchear adjuntos de corrida e items de remisión (cierra N+1 del detalle de orden) ([2ee0d97](https://github.com/francklinmarroq/labflow_api/commit/2ee0d970003adfe61bd81deacbde0b15f5500985))
## [1.12.0](https://github.com/francklinmarroq/labflow_api/compare/v1.11.0...v1.12.0) (2026-09-21)

### Funcionalidades

* el método del examen pasa al catálogo de su perfil ([f4cca6a](https://github.com/francklinmarroq/labflow_api/commit/f4cca6a8211b2d683794aad636c5c6542326af07))
* los médicos solicitantes pasan a un catálogo reutilizable ([f99c452](https://github.com/francklinmarroq/labflow_api/commit/f99c452a7567a260ed9d79c8f92a0e322ddd528a))
## [1.11.0](https://github.com/francklinmarroq/labflow_api/compare/v1.10.2...v1.11.0) (2026-09-21)
## [1.10.2](https://github.com/francklinmarroq/labflow_api/compare/v1.10.1...v1.10.2) (2026-09-15)

### Correcciones

* leer una factura a nombre de empresa reventaba en la imagen nativa ([4a43eae](https://github.com/francklinmarroq/labflow_api/commit/4a43eae9a51775a56548c2590e9b87d83e9ab1ae))
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
