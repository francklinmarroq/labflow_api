package marroquinsoftware.labflowapi.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.Customer;
import marroquinsoftware.labflowapi.model.LabTest;
import marroquinsoftware.labflowapi.model.Parameter;
import marroquinsoftware.labflowapi.model.ReferenceRange;
import marroquinsoftware.labflowapi.model.Sex;
import marroquinsoftware.labflowapi.model.TestResult;
import marroquinsoftware.labflowapi.model.TestRun;
import marroquinsoftware.labflowapi.model.TestRunAttachment;
import marroquinsoftware.labflowapi.payload.ReferenceRangeDTO;
import marroquinsoftware.labflowapi.payload.TestResultDTO;
import marroquinsoftware.labflowapi.payload.TestRunAttachmentDTO;
import marroquinsoftware.labflowapi.payload.TestRunDTO;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.LabTestRepository;
import marroquinsoftware.labflowapi.repositories.ParameterRepository;
import marroquinsoftware.labflowapi.repositories.ReferenceRangeRepository;
import marroquinsoftware.labflowapi.repositories.TestResultRepository;
import marroquinsoftware.labflowapi.repositories.TestRunAttachmentRepository;
import marroquinsoftware.labflowapi.repositories.TestRunRepository;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class TestRunServiceImp implements TestRunService {

    /** Cuánto dura la URL firmada de un adjunto. Alcanza de sobra para abrir e imprimir un reporte. */
    private static final Duration ATTACHMENT_URL_TTL = Duration.ofHours(6);

    /** 8 MB: fotos de un reporte impreso pesan más que un logo de membrete. */
    private static final long MAX_ATTACHMENT_BYTES = 8L * 1024 * 1024;

    /** Formatos que los navegadores imprimen sin problema. */
    private static final Map<String, String> ALLOWED_ATTACHMENT_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp");

    /** Suficiente para un reporte de varias páginas sin permitir subidas descontroladas. */
    private static final int MAX_ATTACHMENTS_PER_RUN = 4;

    @Autowired
    private LabTestRepository labTestRepository;

    @Autowired
    private LabOrderRepository labOrderRepository;

    @Autowired
    private TestRunRepository testRunRepository;

    @Autowired
    private TestResultRepository testResultRepository;

    @Autowired
    private TestRunAttachmentRepository testRunAttachmentRepository;

    @Autowired
    private ParameterRepository parameterRepository;

    @Autowired
    private ReferenceRangeRepository referenceRangeRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<TestRunDTO> getRunsByTest(Long testId) {
        if (!labTestRepository.existsById(testId)) {
            throw new ResourceNotFoundException("LabTest", "testId", testId);
        }
        return testRunRepository.findByTest_IdOrderByRunNumberAsc(testId).stream().map(this::toDTO).toList();
    }

    // Devuelve las corridas de todos los exámenes de la orden en una sola consulta.
    // Equivale a llamar getRunsByTest por cada examen, pero colapsa N round-trips
    // HTTP (y N invocaciones de Worker/Durable Object/contenedor) en uno solo. Cada
    // TestRunDTO ya lleva su testId, así que el cliente puede agruparlas por examen.
    @Override
    public List<TestRunDTO> getRunsByOrder(Long orderId) {
        if (!labOrderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("LabOrder", "orderId", orderId);
        }
        return testRunRepository.findByOrderIdWithResults(orderId).stream().map(this::toDTO).toList();
    }

    /**
     * La corrida y sus resultados se guardan en una sola transacción: si un
     * parámetro no existe o falla el snapshot de rangos, no queda una corrida
     * huérfana sin resultados.
     */
    @Override
    @Transactional
    public TestRunDTO addRunToTest(Long testId, TestRunDTO dto) {
        LabTest test = labTestRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "testId", testId));

        int nextRunNumber = testRunRepository.findTopByTest_IdOrderByRunNumberDesc(testId)
                .map(r -> r.getRunNumber() + 1)
                .orElse(1);

        TestRun run = new TestRun();
        run.setTest(test);
        run.setRunNumber(nextRunNumber);
        run.setPerformedAt(dto.getPerformedAt() != null ? dto.getPerformedAt() : Instant.now());
        run.setIsVerified(false);
        TestRun savedRun = testRunRepository.save(run);

        // Datos del paciente para resolver qué rangos de referencia aplican y
        // congelarlos en cada resultado. La cadena LabTest -> LabOrder -> Customer
        // es @ManyToOne (EAGER), así que ya viene cargada.
        Customer customer = test.getOrder() != null ? test.getOrder().getCustomer() : null;
        Sex sex = customer != null ? customer.getSex() : null;
        Integer ageDays = customer != null ? customer.getAgeInDays() : null;

        List<TestResult> results = dto.getResults().stream().map(rdto -> {
            Parameter parameter = parameterRepository.findById(rdto.getParameterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parameter", "parameterId", rdto.getParameterId()));
            TestResult result = new TestResult();
            result.setTestRun(savedRun);
            result.setParameter(parameter);
            result.setValue(rdto.getValue());
            result.setReferenceRangesSnapshot(buildSnapshot(rdto.getParameterId(), sex, ageDays));
            return result;
        }).toList();
        testResultRepository.saveAll(results);
        savedRun.setResults(results);
        return toDTO(savedRun);
    }

    /**
     * Variante de {@link #addRunToTest} para exámenes con
     * TestConfig.allowResultAttachments: la corrida son una o más imágenes (foto o
     * escaneo del reporte que ya imprime el equipo) en vez de parámetros. No lleva
     * TestResult alguno; es independiente de {@link #addRunToTest}, así que el
     * mismo examen puede tener corridas con resultados y corridas con adjuntos.
     */
    @Override
    @Transactional
    public TestRunDTO addAttachmentRunToTest(Long testId, List<MultipartFile> files) {
        LabTest test = labTestRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("LabTest", "testId", testId));
        if (files == null || files.isEmpty()) {
            throw new APIException("Seleccione al menos una imagen del reporte.");
        }
        if (files.size() > MAX_ATTACHMENTS_PER_RUN) {
            throw new APIException("No se pueden adjuntar más de %d imágenes por corrida.".formatted(MAX_ATTACHMENTS_PER_RUN));
        }

        int nextRunNumber = testRunRepository.findTopByTest_IdOrderByRunNumberDesc(testId)
                .map(r -> r.getRunNumber() + 1)
                .orElse(1);

        TestRun run = new TestRun();
        run.setTest(test);
        run.setRunNumber(nextRunNumber);
        run.setPerformedAt(Instant.now());
        run.setIsVerified(false);
        TestRun savedRun = testRunRepository.save(run);

        Long laboratoryId = TenantContext.getLaboratoryId();
        List<TestRunAttachment> attachments = new ArrayList<>();
        int displayOrder = 0;
        for (MultipartFile file : files) {
            attachments.add(uploadAttachment(savedRun, laboratoryId, file, displayOrder++));
        }
        testRunAttachmentRepository.saveAll(attachments);
        savedRun.setAttachments(attachments);
        savedRun.setResults(Collections.emptyList());
        return toDTO(savedRun);
    }

    private TestRunAttachment uploadAttachment(TestRun run, Long laboratoryId, MultipartFile file, int displayOrder) {
        if (file == null || file.isEmpty()) {
            throw new APIException("Uno de los archivos seleccionados está vacío.");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new APIException("Cada imagen debe pesar como máximo 8 MB.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = ALLOWED_ATTACHMENT_TYPES.get(contentType);
        if (extension == null) {
            throw new APIException("Las imágenes deben ser PNG, JPG o WEBP.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new APIException("No se pudo leer una de las imágenes. Intente de nuevo.");
        }

        String key = "resultados/%d/examen-%d/corrida-%d/%s.%s".formatted(
                laboratoryId, run.getTest().getId(), run.getRunNumber(),
                UUID.randomUUID().toString().substring(0, 8), extension);
        fileStorageService.upload(key, bytes, contentType);

        TestRunAttachment attachment = new TestRunAttachment();
        attachment.setTestRun(run);
        attachment.setObjectKey(key);
        attachment.setContentType(contentType);
        attachment.setDisplayOrder(displayOrder);
        return attachment;
    }

    @Override
    @Transactional
    public TestRunDTO deleteAttachment(Long testId, Long runId, Long attachmentId) {
        TestRun run = testRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", "runId", runId));
        if (!run.getTest().getId().equals(testId)) {
            throw new APIException("La corrida no pertenece al examen indicado. Recargue la página e intente de nuevo.");
        }
        TestRunAttachment attachment = testRunAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("TestRunAttachment", "attachmentId", attachmentId));
        if (!attachment.getTestRun().getId().equals(runId)) {
            throw new APIException("El adjunto no pertenece a la corrida indicada.");
        }
        String key = attachment.getObjectKey();
        testRunAttachmentRepository.delete(attachment);
        run.getAttachments().removeIf(a -> a.getId().equals(attachmentId));
        fileStorageService.delete(key);
        return toDTO(run);
    }

    // Desmarca todas las corridas y marca la elegida en una sola transacción,
    // para que nunca queden dos verificadas (o ninguna) si algo falla a medias.
    @Override
    @Transactional
    public TestRunDTO verifyRun(Long testId, Long runId) {
        if (!labTestRepository.existsById(testId)) {
            throw new ResourceNotFoundException("LabTest", "testId", testId);
        }
        List<TestRun> allRuns = testRunRepository.findByTest_IdOrderByRunNumberAsc(testId);
        TestRun target = allRuns.stream()
                .filter(r -> r.getId().equals(runId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", "runId", runId));
        allRuns.forEach(r -> r.setIsVerified(false));
        target.setIsVerified(true);
        testRunRepository.saveAll(allRuns);
        return toDTO(target);
    }

    @Override
    @Transactional
    public TestRunDTO deleteRun(Long testId, Long runId) {
        TestRun run = testRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", "runId", runId));
        if (!run.getTest().getId().equals(testId)) {
            throw new APIException("La corrida de resultados no pertenece al examen indicado. Recargue la página e intente de nuevo.");
        }
        // Se capturan las llaves antes de borrar: el cascade se lleva las filas de
        // test_run_attachments, pero los objetos en R2 hay que borrarlos aparte.
        List<String> attachmentKeys = run.getAttachments() != null
                ? run.getAttachments().stream().map(TestRunAttachment::getObjectKey).toList()
                : Collections.emptyList();
        testRunRepository.delete(run);
        attachmentKeys.forEach(fileStorageService::delete);
        return toDTO(run);
    }

    private TestRunDTO toDTO(TestRun run) {
        TestRunDTO dto = new TestRunDTO();
        dto.setId(run.getId());
        dto.setTestId(run.getTest().getId());
        dto.setRunNumber(run.getRunNumber());
        dto.setPerformedAt(run.getPerformedAt());
        dto.setIsVerified(run.getIsVerified());
        List<TestResultDTO> results = run.getResults() != null
                ? run.getResults().stream().map(this::toResultDTO).toList()
                : Collections.emptyList();
        dto.setResults(results);
        List<TestRunAttachmentDTO> attachments = run.getAttachments() != null
                ? run.getAttachments().stream()
                    .sorted(Comparator.comparing(a -> a.getDisplayOrder() == null ? 0 : a.getDisplayOrder()))
                    .map(this::toAttachmentDTO).toList()
                : Collections.emptyList();
        dto.setAttachments(attachments);
        return dto;
    }

    private TestRunAttachmentDTO toAttachmentDTO(TestRunAttachment attachment) {
        TestRunAttachmentDTO dto = new TestRunAttachmentDTO();
        dto.setId(attachment.getId());
        dto.setContentType(attachment.getContentType());
        dto.setDisplayOrder(attachment.getDisplayOrder());
        dto.setUrl(fileStorageService.signedUrl(attachment.getObjectKey(), ATTACHMENT_URL_TTL));
        return dto;
    }

    private TestResultDTO toResultDTO(TestResult result) {
        TestResultDTO dto = new TestResultDTO();
        dto.setId(result.getId());
        dto.setTestRunId(result.getTestRun().getId());
        dto.setParameterId(result.getParameter().getId());
        dto.setValue(result.getValue());
        dto.setReferenceRanges(parseSnapshot(result.getReferenceRangesSnapshot()));
        return dto;
    }

    // Calcula los rangos aplicables al paciente y los serializa a JSON para
    // congelarlos en el resultado. Devuelve null si no hay rangos aplicables.
    private String buildSnapshot(Long parameterId, Sex sex, Integer ageDays) {
        List<ReferenceRange> applicable = referenceRangeRepository.findApplicable(parameterId, sex, ageDays);
        if (applicable.isEmpty()) return null;
        List<ReferenceRangeDTO> snapshot = applicable.stream().map(this::toRangeDTO).toList();
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException e) {
            throw new APIException("No se pudo serializar el snapshot de rangos de referencia: " + e.getMessage());
        }
    }

    private List<ReferenceRangeDTO> parseSnapshot(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<ReferenceRangeDTO>>() {});
        } catch (JacksonException e) {
            // Snapshot corrupto: se omite en lugar de romper la lectura del resultado.
            return null;
        }
    }

    private ReferenceRangeDTO toRangeDTO(ReferenceRange r) {
        ReferenceRangeDTO d = new ReferenceRangeDTO();
        d.setId(r.getId());
        d.setParameterId(r.getParameter().getId());
        d.setSex(r.getSex());
        d.setAgeRangeId(r.getAgeRange() != null ? r.getAgeRange().getId() : null);
        d.setMinAgeDays(r.getMinAgeDays());
        d.setMaxAgeDays(r.getMaxAgeDays());
        d.setLowerLimit(r.getLowerLimit());
        d.setLowerExclusive(r.isLowerExclusive());
        d.setUpperLimit(r.getUpperLimit());
        d.setUpperExclusive(r.isUpperExclusive());
        d.setCriticalLow(r.getCriticalLow());
        d.setCriticalHigh(r.getCriticalHigh());
        d.setInterpretationText(r.getInterpretationText());
        d.setContextKind(r.getContextKind());
        d.setContextLabel(r.getContextLabel());
        d.setContextMin(r.getContextMin());
        d.setContextMax(r.getContextMax());
        return d;
    }
}
