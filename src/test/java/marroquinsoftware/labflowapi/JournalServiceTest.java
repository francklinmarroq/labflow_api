package marroquinsoftware.labflowapi;

import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.model.*;
import marroquinsoftware.labflowapi.payload.ExpenseDTO;
import marroquinsoftware.labflowapi.payload.ExpenseRequest;
import marroquinsoftware.labflowapi.repositories.AccountRepository;
import marroquinsoftware.labflowapi.repositories.JournalEntryRepository;
import marroquinsoftware.labflowapi.service.AccountSeeder;
import marroquinsoftware.labflowapi.service.ExpenseServiceImp;
import marroquinsoftware.labflowapi.service.JournalService;
import marroquinsoftware.labflowapi.service.JournalService.LinePlan;
import marroquinsoftware.labflowapi.service.JournalServiceImp;
import marroquinsoftware.labflowapi.tenant.TenantContext;
import marroquinsoftware.labflowapi.tenant.TenantIdentifierResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba el motor de partida doble: partidas que no cuadran se rechazan, el
 * correlativo avanza sin huecos, el contra-asiento invierte débitos y créditos,
 * y los gastos generan su partida (y su reversa al anularse).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JournalServiceImp.class, ExpenseServiceImp.class, AccountSeeder.class,
        TenantIdentifierResolver.class, JournalServiceTest.JacksonForTest.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class JournalServiceTest {

    static class JacksonForTest {
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
    }

    @Autowired JournalService journalService;
    @Autowired ExpenseServiceImp expenseService;
    @Autowired AccountSeeder accountSeeder;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository journalEntryRepository;

    @BeforeEach
    void setUpTenant() {
        TenantContext.setLaboratoryId(1L);
        accountSeeder.seedDefaultAccounts();
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    private Account caja() { return journalService.systemAccount(SystemAccountKey.CAJA); }
    private Account capital() { return journalService.systemAccount(SystemAccountKey.CAPITAL); }

    private BigDecimal amount(String value) { return new BigDecimal(value); }

    @Test
    void postsBalancedEntryWithSequentialNumbers() {
        JournalEntry first = journalService.post(LocalDate.now(), "Aporte inicial",
                JournalSourceType.MANUAL, null, List.of(
                        LinePlan.debit(caja(), amount("1000.00")),
                        LinePlan.credit(capital(), amount("1000.00"))));
        JournalEntry second = journalService.post(LocalDate.now(), "Otro aporte",
                JournalSourceType.MANUAL, null, List.of(
                        LinePlan.debit(caja(), amount("500.00")),
                        LinePlan.credit(capital(), amount("500.00"))));

        assertEquals(1L, first.getEntryNumber());
        assertEquals(2L, second.getEntryNumber(), "el correlativo debe avanzar sin huecos");
        assertEquals(2, first.getLines().size());
    }

    @Test
    void rejectsUnbalancedEntry() {
        APIException ex = assertThrows(APIException.class, () -> journalService.post(
                LocalDate.now(), "Descuadrada", JournalSourceType.MANUAL, null, List.of(
                        LinePlan.debit(caja(), amount("100.00")),
                        LinePlan.credit(capital(), amount("90.00")))));
        assertTrue(ex.getMessage().contains("no cuadra"));
    }

    @Test
    void rejectsSingleLineEntry() {
        assertThrows(APIException.class, () -> journalService.post(
                LocalDate.now(), "Una línea", JournalSourceType.MANUAL, null, List.of(
                        LinePlan.debit(caja(), amount("100.00")))));
    }

    @Test
    void rejectsLineWithBothDebitAndCredit() {
        assertThrows(APIException.class, () -> journalService.post(
                LocalDate.now(), "Debe y haber juntos", JournalSourceType.MANUAL, null, List.of(
                        new LinePlan(caja(), amount("100.00"), amount("100.00")),
                        LinePlan.credit(capital(), amount("100.00")))));
    }

    @Test
    void rejectsInactiveAccount() {
        Account otrosGastos = accountRepository.findAll().stream()
                .filter(a -> a.getSystemKey() == null && a.getType() == AccountType.GASTO)
                .findFirst().orElseThrow();
        otrosGastos.setActive(false);
        accountRepository.save(otrosGastos);

        assertThrows(APIException.class, () -> journalService.post(
                LocalDate.now(), "Cuenta desactivada", JournalSourceType.MANUAL, null, List.of(
                        LinePlan.debit(otrosGastos, amount("100.00")),
                        LinePlan.credit(caja(), amount("100.00")))));
    }

    @Test
    void reverseSwapsDebitsAndCredits() {
        JournalEntry original = journalService.post(LocalDate.now(), "Original",
                JournalSourceType.MANUAL, null, List.of(
                        LinePlan.debit(caja(), amount("250.00")),
                        LinePlan.credit(capital(), amount("250.00"))));

        JournalEntry reversal = journalService.reverse(original,
                JournalSourceType.MANUAL, null, "Reversa de la original");

        assertEquals(2, reversal.getLines().size());
        JournalLine cajaLine = reversal.getLines().stream()
                .filter(l -> l.getAccount().getId().equals(caja().getId()))
                .findFirst().orElseThrow();
        assertEquals(0, cajaLine.getDebit().compareTo(BigDecimal.ZERO));
        assertEquals(0, cajaLine.getCredit().compareTo(amount("250.00")),
                "la reversa debe abonar lo que la original cargó");
    }

    @Test
    void expenseCreatesBalancedEntryAndAnnulmentReversesIt() {
        Account reactivos = accountRepository.findAll().stream()
                .filter(a -> "5104".equals(a.getCode()))
                .findFirst().orElseThrow();

        ExpenseDTO expense = expenseService.createExpense(new ExpenseRequest(
                LocalDate.now(), "Compra de reactivos", amount("750.00"), reactivos.getId(),
                PaymentMethod.EFECTIVO));

        JournalEntry entry = journalEntryRepository
                .findFirstBySourceTypeAndSourceId(JournalSourceType.GASTO, expense.getId())
                .orElseThrow();
        BigDecimal debits = entry.getLines().stream().map(JournalLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = entry.getLines().stream().map(JournalLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, debits.compareTo(credits), "la partida del gasto debe cuadrar");
        assertEquals(0, debits.compareTo(amount("750.00")));

        ExpenseDTO annulled = expenseService.annulExpense(expense.getId(), "Se registró dos veces");
        assertTrue(annulled.isAnnulled());
        assertTrue(journalEntryRepository
                        .findFirstBySourceTypeAndSourceId(JournalSourceType.ANULACION_GASTO, expense.getId())
                        .isPresent(),
                "anular el gasto debe generar su contra-asiento");

        assertThrows(APIException.class,
                () -> expenseService.annulExpense(expense.getId(), "otra vez"),
                "no se puede anular dos veces");
    }

    @Test
    void rejectsExpenseOnNonExpenseAccount() {
        assertThrows(APIException.class, () -> expenseService.createExpense(new ExpenseRequest(
                LocalDate.now(), "Cuenta que no es de gastos", amount("100.00"),
                caja().getId(), PaymentMethod.EFECTIVO)));
    }
}
