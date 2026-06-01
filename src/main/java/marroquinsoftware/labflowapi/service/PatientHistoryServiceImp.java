package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.ResourceNotFoundException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.*;
import marroquinsoftware.labflowapi.repositories.CustomerRepository;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PatientHistoryServiceImp implements PatientHistoryService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private LabOrderRepository labOrderRepository;

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

                List<PatientHistoryRunDTO> runDTOs = buildRunDTOs(labTest);

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

    private List<PatientHistoryRunDTO> buildRunDTOs(LabTest labTest) {
        if (labTest.getRuns() == null) return Collections.emptyList();
        return labTest.getRuns().stream()
                .sorted(Comparator.comparing(TestRun::getRunNumber))
                .map(run -> new PatientHistoryRunDTO(
                        run.getId(),
                        run.getRunNumber(),
                        run.getPerformedAt(),
                        run.getIsVerified(),
                        buildResultDTOs(run)
                ))
                .collect(Collectors.toList());
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
