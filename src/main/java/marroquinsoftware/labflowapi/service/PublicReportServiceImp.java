package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.LabOrder;
import marroquinsoftware.labflowapi.model.OrderStatus;
import marroquinsoftware.labflowapi.model.ReferenceRange;
import marroquinsoftware.labflowapi.model.Sex;
import marroquinsoftware.labflowapi.payload.CustomerDTO;
import marroquinsoftware.labflowapi.payload.LabOrderDTO;
import marroquinsoftware.labflowapi.payload.LabTestDTO;
import marroquinsoftware.labflowapi.payload.LaboratoryDTO;
import marroquinsoftware.labflowapi.payload.ParameterDTO;
import marroquinsoftware.labflowapi.payload.PublicReportDTO;
import marroquinsoftware.labflowapi.payload.ReferenceRangeDTO;
import marroquinsoftware.labflowapi.payload.TestConfigDTO;
import marroquinsoftware.labflowapi.payload.TestDTO;
import marroquinsoftware.labflowapi.payload.TestRunDTO;
import marroquinsoftware.labflowapi.payload.UnitDTO;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.ReferenceRangeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reporte público de una orden (endpoint sin autenticación). El TenantContext ya
 * viene fijado por el {@code AuthTokenFilter} con el laboratorio dueño del token,
 * así que aquí se reutilizan los servicios normales (filtrados por @TenantId) para
 * armar el reporte, exponiendo solo lo que se muestra (sin precios ni fiscales).
 *
 * <p>Solo se entrega el detalle cuando la orden está verificada o entregada; antes
 * de eso el paciente vería resultados sin validar, así que se devuelve
 * {@code ready=false} con únicamente la identidad del laboratorio.
 */
@Service
public class PublicReportServiceImp implements PublicReportService {

    private final LabOrderRepository labOrderRepository;
    private final LabOrderService labOrderService;
    private final LabTestService labTestService;
    private final TestRunService testRunService;
    private final CustomerService customerService;
    private final LaboratoryService laboratoryService;
    private final TestService testService;
    private final ParameterService parameterService;
    private final UnitService unitService;
    private final TestConfigService testConfigService;
    private final ReferenceRangeRepository referenceRangeRepository;

    public PublicReportServiceImp(LabOrderRepository labOrderRepository,
                                  LabOrderService labOrderService,
                                  LabTestService labTestService,
                                  TestRunService testRunService,
                                  CustomerService customerService,
                                  LaboratoryService laboratoryService,
                                  TestService testService,
                                  ParameterService parameterService,
                                  UnitService unitService,
                                  TestConfigService testConfigService,
                                  ReferenceRangeRepository referenceRangeRepository) {
        this.labOrderRepository = labOrderRepository;
        this.labOrderService = labOrderService;
        this.labTestService = labTestService;
        this.testRunService = testRunService;
        this.customerService = customerService;
        this.laboratoryService = laboratoryService;
        this.testService = testService;
        this.parameterService = parameterService;
        this.unitService = unitService;
        this.testConfigService = testConfigService;
        this.referenceRangeRepository = referenceRangeRepository;
    }

    // Estados en los que el reporte ya se puede mostrar al paciente.
    private static boolean isReady(OrderStatus status) {
        return status == OrderStatus.VERIFIED || status == OrderStatus.DELIVERED;
    }

    @Override
    @Transactional(readOnly = true)
    public PublicReportDTO getPublicReport(String token) {
        LabOrder order = labOrderRepository.findByPublicToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("", "LabOrder", "publicToken", token));

        PublicReportDTO report = new PublicReportDTO();
        report.setLaboratory(toLab(laboratoryService.getLaboratory()));

        if (!isReady(order.getStatus())) {
            // Aún no está lista: solo la identidad del laboratorio, sin datos del
            // paciente ni resultados.
            report.setReady(false);
            return report;
        }
        report.setReady(true);

        CustomerDTO customer = customerService.getCustomerById(order.getCustomer().getId());
        report.setPatient(toPatient(customer));
        report.setOrder(toOrder(labOrderService.getOrderById(order.getId())));

        List<LabTestDTO> tests = labTestService.getTestsByOrder(order.getId());
        report.setTests(tests);

        // Corridas por examen; de ahí salen los parámetros con resultado.
        Map<Long, List<TestRunDTO>> runsByLabTest = new LinkedHashMap<>();
        Set<Long> resultParameterIds = new HashSet<>();
        for (LabTestDTO test : tests) {
            List<TestRunDTO> runs = testRunService.getRunsByTest(test.getId());
            runsByLabTest.put(test.getId(), runs);
            for (TestRunDTO run : runs) {
                run.getResults().forEach(r -> resultParameterIds.add(r.getParameterId()));
            }
        }
        report.setRunsByLabTest(runsByLabTest);

        // Ids referenciados por la orden, para bajar solo lo necesario del catálogo.
        Set<Long> testIds = new HashSet<>();
        Set<Long> configIds = new HashSet<>();
        for (LabTestDTO test : tests) {
            if (test.getTestId() != null) testIds.add(test.getTestId());
            if (test.getTestConfigId() != null) configIds.add(test.getTestConfigId());
        }

        List<TestConfigDTO> configs = allTestConfigs().stream()
                .filter(c -> configIds.contains(c.getId()))
                .toList();
        report.setConfigs(configs);

        // Parámetros: los de los perfiles usados + los que ya tienen resultado.
        Set<Long> parameterIds = new HashSet<>(resultParameterIds);
        for (TestConfigDTO config : configs) {
            if (config.getParameterIds() != null) parameterIds.addAll(config.getParameterIds());
        }

        List<ParameterDTO> parameters = allParameters().stream()
                .filter(p -> parameterIds.contains(p.getId()))
                .toList();
        report.setParameters(parameters);

        Set<Long> unitIds = new HashSet<>();
        Sex sex = customer.getSex();
        for (ParameterDTO p : parameters) {
            if (p.getUnitId() != null) unitIds.add(p.getUnitId());
        }
        report.setUnits(allUnits().stream().filter(u -> unitIds.contains(u.getId())).toList());

        report.setTestDefs(allTests().stream()
                .filter(t -> testIds.contains(t.getId()))
                .map(this::toTestDef)
                .toList());

        // Rangos aplicables al paciente por parámetro (mismo criterio que el
        // snapshot que se congela al reportar). El reporte usa el snapshot del
        // resultado si existe y cae a estos como respaldo.
        Integer ageDays = customer.getAgeInDays();
        Map<Long, List<ReferenceRangeDTO>> rangesByParameter = new HashMap<>();
        for (Long parameterId : parameterIds) {
            List<ReferenceRange> applicable = referenceRangeRepository.findApplicable(parameterId, sex, ageDays);
            if (!applicable.isEmpty()) {
                rangesByParameter.put(parameterId, applicable.stream().map(this::toRangeDTO).toList());
            }
        }
        report.setRangesByParameter(rangesByParameter);

        return report;
    }

    private List<TestConfigDTO> allTestConfigs() {
        return testConfigService.getAllTestConfigs(0, Integer.MAX_VALUE, "id", "asc").getContent();
    }

    private List<ParameterDTO> allParameters() {
        return parameterService.getAllParameters(0, Integer.MAX_VALUE, "id", "asc").getContent();
    }

    private List<UnitDTO> allUnits() {
        return unitService.getAllUnits(0, Integer.MAX_VALUE, "id", "asc").getContent();
    }

    private List<TestDTO> allTests() {
        return testService.getAllTests(0, Integer.MAX_VALUE, "id", "asc").getContent();
    }

    private PublicReportDTO.Lab toLab(LaboratoryDTO lab) {
        if (lab == null) return null;
        return new PublicReportDTO.Lab(
                lab.getName(), lab.getAddress1(), lab.getAddress2(),
                lab.getPhone(), lab.getEmail(), lab.getRtn(), lab.getLogoUrl());
    }

    private PublicReportDTO.Patient toPatient(CustomerDTO customer) {
        return new PublicReportDTO.Patient(
                customer.getName(), customer.getSex(),
                customer.getAgeInDays(), customer.getNationalIdNumber());
    }

    private PublicReportDTO.Order toOrder(LabOrderDTO order) {
        return new PublicReportDTO.Order(
                order.getOrderNumber(), order.getRequestedAt(), order.getStatus(), order.getNotes(),
                order.getLmpDate(), order.isPregnant(), order.getGestationalWeeks(), order.isMenopausal());
    }

    private PublicReportDTO.TestDef toTestDef(TestDTO test) {
        return new PublicReportDTO.TestDef(test.getId(), test.getName(), test.getArea());
    }

    // Mismo mapeo que TestRunServiceImp usa para congelar el snapshot; se replica
    // aquí para no exponer el método privado de aquel servicio.
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
