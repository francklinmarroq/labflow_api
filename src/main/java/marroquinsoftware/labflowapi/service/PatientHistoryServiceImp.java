package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.*;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.ReferenceRangeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PatientHistoryServiceImp implements PatientHistoryService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private LabOrderRepository labOrderRepository;

    @Autowired
    private ReferenceRangeRepository referenceRangeRepository;

    @Override
    public List<PatientTestHistoryDTO> getPatientHistory(Long customerId, String testName, Instant dateFrom, Instant dateTo) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "customerId", customerId));

        List<LabOrder> orders = labOrderRepository.findByCustomer_Id(customerId);

        // Group entries by test id
        Map<Long, PatientTestHistoryDTO> byTest = new LinkedHashMap<>();

        for (LabOrder order : orders) {
            if (order.getTests() == null) continue;

            for (LabTest labTest : order.getTests()) {
                Test test = labTest.getTest();

                // Filter by test name
                if (testName != null && !testName.isBlank()) {
                    if (!test.getName().toLowerCase().contains(testName.trim().toLowerCase())) continue;
                }

                // Filter by date range
                Instant requestedAt = order.getRequestedAt();
                if (dateFrom != null && requestedAt != null && requestedAt.isBefore(dateFrom)) continue;
                if (dateTo != null && requestedAt != null && requestedAt.isAfter(dateTo)) continue;

                List<PatientHistoryRunDTO> runDTOs = buildRunDTOs(labTest, order.getCustomer());

                PatientHistoryEntryDTO entry = new PatientHistoryEntryDTO(
                        order.getId(),
                        labTest.getId(),
                        requestedAt,
                        order.getStatus(),
                        runDTOs
                );

                byTest.computeIfAbsent(test.getId(), id -> new PatientTestHistoryDTO(test.getId(), test.getName(), new ArrayList<>()))
                        .getEntries().add(entry);
            }
        }

        // Sort entries within each group by requestedAt descending
        for (PatientTestHistoryDTO group : byTest.values()) {
            group.getEntries().sort(Comparator.comparing(
                    PatientHistoryEntryDTO::getRequestedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())
            ));
        }

        // Sort groups alphabetically by test name
        return byTest.values().stream()
                .sorted(Comparator.comparing(PatientTestHistoryDTO::getTestName))
                .collect(Collectors.toList());
    }

    private List<PatientHistoryRunDTO> buildRunDTOs(LabTest labTest, Customer customer) {
        if (labTest.getRuns() == null) return Collections.emptyList();
        return labTest.getRuns().stream()
                .sorted(Comparator.comparing(TestRun::getRunNumber))
                .map(run -> new PatientHistoryRunDTO(
                        run.getId(),
                        run.getRunNumber(),
                        run.getPerformedAt(),
                        run.getIsVerified(),
                        buildResultDTOs(run),
                        buildChart(labTest, run, customer)
                ))
                .collect(Collectors.toList());
    }

    // Construye el grafico de la corrida cuando su perfil es una curva
    // (chartType = LINE). Cruza los parametros del perfil (con su X) contra los
    // resultados de la corrida y resuelve, para cada punto, el rango de referencia
    // aplicable al paciente (sexo/edad) que el frontend dibuja como banda umbral.
    private CurveChartDTO buildChart(LabTest labTest, TestRun run, Customer customer) {
        TestConfig config = labTest.getTestConfig();
        if (config == null || config.getChartType() != ChartType.LINE) return null;

        Map<Long, String> valuesByParameter = new HashMap<>();
        if (run.getResults() != null) {
            for (TestResult result : run.getResults()) {
                valuesByParameter.put(result.getParameter().getId(), result.getValue());
            }
        }

        Sex sex = customer != null ? customer.getSex() : null;
        Integer ageDays = customer != null ? customer.getAgeInDays() : null;

        List<CurveChartPointDTO> points = new ArrayList<>();
        String unit = null;
        for (TestConfigParameter cp : config.getConfigParameters()) {
            if (cp.getChartXValue() == null) continue; // solo parametros que son puntos de la curva
            Parameter param = cp.getParameter();
            if (unit == null && param.getUnit() != null) unit = param.getUnit().getUnitSymbol();

            BigDecimal y = parseNumeric(valuesByParameter.get(param.getId()));

            BigDecimal lower = null;
            BigDecimal upper = null;
            List<ReferenceRange> ranges = referenceRangeRepository.findApplicable(param.getId(), sex, ageDays);
            if (!ranges.isEmpty()) {
                ReferenceRange range = ranges.get(0);
                lower = range.getLowerLimit();
                upper = range.getUpperLimit();
            }

            points.add(new CurveChartPointDTO(cp.getChartXValue(), y, lower, upper, param.getName()));
        }

        if (points.isEmpty()) return null;
        points.sort(Comparator.comparing(CurveChartPointDTO::getX));
        return new CurveChartDTO(ChartType.LINE, config.getChartXAxisLabel(), unit, points);
    }

    // El valor del resultado se guarda como texto; solo se grafica si es numerico.
    private BigDecimal parseNumeric(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<PatientHistoryResultDTO> buildResultDTOs(TestRun run) {
        if (run.getResults() == null) return Collections.emptyList();
        return run.getResults().stream()
                .map(result -> {
                    Parameter param = result.getParameter();
                    String unit = param.getUnit() != null ? param.getUnit().getUnitSymbol() : null;
                    return new PatientHistoryResultDTO(
                            result.getId(),
                            param.getId(),
                            param.getName(),
                            unit,
                            result.getValue()
                    );
                })
                .collect(Collectors.toList());
    }
}
