package marroquinsoftware.labflowapi.service;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.PaymentMethod;
import marroquinsoftware.labflowapi.model.TestArea;
import marroquinsoftware.labflowapi.payload.CollectionsReportDTO;
import marroquinsoftware.labflowapi.payload.SalesReportDTO;
import marroquinsoftware.labflowapi.payload.TestsVolumeDTO;
import marroquinsoftware.labflowapi.payload.UserProductivityDTO;
import marroquinsoftware.labflowapi.repositories.InvoiceRepository;
import marroquinsoftware.labflowapi.repositories.LabTestRepository;
import marroquinsoftware.labflowapi.repositories.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Implementación de la reportería operativa y de ventas. Las consultas devuelven
 * filas crudas (filtradas por laboratorio vía @TenantId) y la agregación por
 * periodo/método/usuario se hace aquí, en zona horaria de Honduras, para no
 * depender de funciones de fecha propias de cada motor (H2 en tests, PostgreSQL
 * en producción).
 */
@Service
public class AnalyticsReportServiceImp implements AnalyticsReportService {

    // Misma zona que usa la facturación para interpretar los rangos de fecha.
    private static final ZoneId LAB_ZONE = ZoneId.of("America/Tegucigalpa");

    @Autowired
    private LabTestRepository labTestRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public TestsVolumeDTO getTestsVolume(LocalDate from, LocalDate to, String area) {
        requireRange(from, to);
        TestArea areaFilter = parseArea(area);

        List<TestsVolumeDTO.Row> rows = new ArrayList<>();
        long total = 0;
        for (Object[] r : labTestRepository.testsVolume(startInstant(from), endInstant(to))) {
            TestArea rowArea = (TestArea) r[2];
            if (areaFilter != null && rowArea != areaFilter) continue;
            long count = ((Number) r[3]).longValue();
            rows.add(new TestsVolumeDTO.Row((Long) r[0], (String) r[1], rowArea, count));
            total += count;
        }
        return new TestsVolumeDTO(from, to, rows, total);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesReportDTO getSales(LocalDate from, LocalDate to, String groupBy) {
        requireRange(from, to);
        boolean byMonth = "month".equalsIgnoreCase(groupBy);
        String granularity = byMonth ? "month" : "day";
        Instant fromInstant = startInstant(from);
        Instant toInstant = endInstant(to);

        // Resumen y desglose por periodo/cliente desde las cabeceras de factura.
        long count = 0;
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal otherDiscount = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal[]> byPeriod = new TreeMap<>();          // bucket -> [total, count]
        Map<String, BigDecimal[]> byCustomer = new LinkedHashMap<>();  // customerName -> [amount, count]

        for (Object[] r : invoiceRepository.salesInvoiceRows(fromInstant, toInstant)) {
            Instant issuedAt = (Instant) r[0];
            BigDecimal rowSubtotal = nz((BigDecimal) r[1]);
            BigDecimal rowDiscount = nz((BigDecimal) r[2]);
            BigDecimal rowOther = nz((BigDecimal) r[3]);
            BigDecimal rowTotal = nz((BigDecimal) r[4]);
            String customerName = r[5] != null ? (String) r[5] : "—";

            count++;
            subtotal = subtotal.add(rowSubtotal);
            discount = discount.add(rowDiscount);
            otherDiscount = otherDiscount.add(rowOther);
            total = total.add(rowTotal);

            String bucket = byMonth ? monthKey(issuedAt) : dayKey(issuedAt);
            accumulate(byPeriod, bucket, rowTotal);
            accumulate(byCustomer, customerName, rowTotal);
        }

        List<SalesReportDTO.PeriodBucket> periods = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> e : byPeriod.entrySet()) {
            periods.add(new SalesReportDTO.PeriodBucket(
                    e.getKey(), e.getValue()[0], e.getValue()[1].longValue()));
        }

        List<SalesReportDTO.CustomerSales> customers = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> e : byCustomer.entrySet()) {
            customers.add(new SalesReportDTO.CustomerSales(
                    e.getKey(), e.getValue()[1].longValue(), e.getValue()[0]));
        }
        customers.sort(Comparator.comparing(SalesReportDTO.CustomerSales::getAmount).reversed());

        // Desglose por examen desde las líneas de factura.
        Map<String, BigDecimal[]> byTest = new LinkedHashMap<>(); // testName -> [amount, qty]
        for (Object[] r : invoiceRepository.salesItemRows(fromInstant, toInstant)) {
            String testName = r[0] != null ? (String) r[0] : "—";
            accumulate(byTest, testName, nz((BigDecimal) r[1]));
        }
        List<SalesReportDTO.TestSales> tests = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> e : byTest.entrySet()) {
            tests.add(new SalesReportDTO.TestSales(
                    e.getKey(), e.getValue()[1].longValue(), e.getValue()[0]));
        }
        tests.sort(Comparator.comparing(SalesReportDTO.TestSales::getAmount).reversed());

        SalesReportDTO.Summary summary = new SalesReportDTO.Summary(
                count, subtotal, discount, otherDiscount, total);
        return new SalesReportDTO(from, to, granularity, summary, periods, tests, customers);
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionsReportDTO getCollections(LocalDate from, LocalDate to) {
        requireRange(from, to);

        long count = 0;
        BigDecimal total = BigDecimal.ZERO;
        Map<PaymentMethod, BigDecimal[]> byMethod = new LinkedHashMap<>(); // method -> [total, count]
        Map<String, BigDecimal> byDay = new TreeMap<>();                   // day -> total

        for (Object[] r : paymentRepository.paymentRows(startInstant(from), endInstant(to))) {
            Instant paidAt = (Instant) r[0];
            BigDecimal amount = nz((BigDecimal) r[1]);
            PaymentMethod method = (PaymentMethod) r[2];

            count++;
            total = total.add(amount);

            BigDecimal[] m = byMethod.computeIfAbsent(method, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            m[0] = m[0].add(amount);
            m[1] = m[1].add(BigDecimal.ONE);

            byDay.merge(dayKey(paidAt), amount, BigDecimal::add);
        }

        List<CollectionsReportDTO.MethodBucket> methods = new ArrayList<>();
        for (Map.Entry<PaymentMethod, BigDecimal[]> e : byMethod.entrySet()) {
            PaymentMethod method = e.getKey();
            methods.add(new CollectionsReportDTO.MethodBucket(
                    method, method != null ? method.getLabel() : "—",
                    e.getValue()[0], e.getValue()[1].longValue()));
        }

        List<CollectionsReportDTO.DayBucket> days = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : byDay.entrySet()) {
            days.add(new CollectionsReportDTO.DayBucket(e.getKey(), e.getValue()));
        }

        CollectionsReportDTO.Summary summary = new CollectionsReportDTO.Summary(count, total);
        return new CollectionsReportDTO(from, to, summary, methods, days, invoiceRepository.totalReceivable());
    }

    @Override
    @Transactional(readOnly = true)
    public UserProductivityDTO getUserProductivity(LocalDate from, LocalDate to) {
        requireRange(from, to);
        Instant fromInstant = startInstant(from);
        Instant toInstant = endInstant(to);

        Map<String, UserProductivityDTO.Row> byUser = new LinkedHashMap<>();

        for (Object[] r : invoiceRepository.salesByUser(fromInstant, toInstant)) {
            String username = r[0] != null ? (String) r[0] : "—";
            UserProductivityDTO.Row row = userRow(byUser, username);
            row.setInvoicesIssued(((Number) r[1]).longValue());
            row.setSalesAmount(nz((BigDecimal) r[2]));
        }

        for (Object[] r : paymentRepository.paymentsByUser(fromInstant, toInstant)) {
            String username = r[0] != null ? (String) r[0] : "—";
            UserProductivityDTO.Row row = userRow(byUser, username);
            row.setPaymentsCount(((Number) r[1]).longValue());
            row.setPaymentsAmount(nz((BigDecimal) r[2]));
        }

        List<UserProductivityDTO.Row> rows = new ArrayList<>(byUser.values());
        rows.sort(Comparator.comparing(UserProductivityDTO.Row::getSalesAmount).reversed());
        return new UserProductivityDTO(from, to, rows);
    }

    // --- Helpers ---

    private UserProductivityDTO.Row userRow(Map<String, UserProductivityDTO.Row> byUser, String username) {
        return byUser.computeIfAbsent(username, u -> new UserProductivityDTO.Row(
                u, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO));
    }

    private void accumulate(Map<String, BigDecimal[]> map, String key, BigDecimal amount) {
        BigDecimal[] agg = map.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        agg[0] = agg[0].add(amount);
        agg[1] = agg[1].add(BigDecimal.ONE);
    }

    private void requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new APIException("Indique el rango de fechas (desde y hasta).");
        }
        if (to.isBefore(from)) {
            throw new APIException("La fecha 'hasta' no puede ser anterior a 'desde'.");
        }
    }

    private TestArea parseArea(String area) {
        if (area == null || area.isBlank()) return null;
        try {
            return TestArea.valueOf(area.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new APIException("El área de examen indicada no es válida.");
        }
    }

    // Rango [from 00:00, to+1 00:00) en hora de Honduras (límite superior exclusivo),
    // igual que la facturación.
    private Instant startInstant(LocalDate from) {
        return from.atStartOfDay(LAB_ZONE).toInstant();
    }

    private Instant endInstant(LocalDate to) {
        return to.plusDays(1).atStartOfDay(LAB_ZONE).toInstant();
    }

    private String dayKey(Instant instant) {
        return instant.atZone(LAB_ZONE).toLocalDate().toString(); // "YYYY-MM-DD"
    }

    private String monthKey(Instant instant) {
        return YearMonth.from(instant.atZone(LAB_ZONE)).toString(); // "YYYY-MM"
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
