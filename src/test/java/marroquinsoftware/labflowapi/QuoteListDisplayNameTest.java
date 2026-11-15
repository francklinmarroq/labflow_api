package marroquinsoftware.labflowapi;

import jakarta.persistence.EntityManagerFactory;
import marroquinsoftware.labflowapi.model.Role;
import marroquinsoftware.labflowapi.model.User;
import marroquinsoftware.labflowapi.payload.QuoteDTO;
import marroquinsoftware.labflowapi.payload.QuoteResponse;
import marroquinsoftware.labflowapi.repositories.UserRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El listado de cotizaciones (`getAllQuotes`, que el front baja con
 * `pageSize=1000`) resuelve, por cada fila, el nombre para mostrar de quien creó
 * (y editó) la cotización. Antes eso disparaba una consulta
 * `findNameByUsernameAndLaboratoryId` POR cotización POR campo —2N round-trips a
 * Postgres para una página de N, repitiendo la MISMA consulta para el puñado de
 * usuarios del laboratorio. Ahora una memoria por llamada colapsa esas consultas
 * en una por usuario DISTINTO, sin cambiar los nombres resueltos.
 */
class QuoteListDisplayNameTest extends QuoteTestSupport {

    @Autowired UserRepository userRepository;
    @Autowired EntityManagerFactory entityManagerFactory;

    private User seedUser(String username, String name) {
        User user = new User();
        user.setUsername(username);
        user.setName(name);
        user.setPassword("x");
        user.setRole(Role.STAFF);
        user.setLaboratory(laboratory);
        return userRepository.save(user);
    }

    private QuoteDTO createAs(String username, Long... testIds) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
        try {
            return quoteService.createQuote(registered(testIds));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void listResolvesEveryCreatorNameWithoutAPerRowQuery() {
        // Dos usuarios: uno con nombre registrado, otro sin nombre (cae al username).
        seedUser("marta@lab.hn", "Dra. Marta Pineda");
        seedUser("recepcion@lab.hn", null);

        // Cinco cotizaciones de "marta" y dos de "recepcion": siete filas, dos
        // usuarios distintos.
        for (int i = 0; i < 5; i++) createAs("marta@lab.hn", hemograma.getId());
        for (int i = 0; i < 2; i++) createAs("recepcion@lab.hn", glucosa.getId());

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        QuoteResponse response = quoteService.getAllQuotes(0, 50, "quotedAt", "DESC");

        List<QuoteDTO> quotes = response.getContent();
        assertEquals(7, quotes.size());

        // Resultado observable idéntico: cada fila trae el nombre correcto de su
        // creador (el registrado para "marta", el username como fallback para
        // "recepcion", que no tiene nombre).
        for (QuoteDTO quote : quotes) {
            if ("marta@lab.hn".equals(quote.getCreatedByUsername())) {
                assertEquals("Dra. Marta Pineda", quote.getCreatedByName());
            } else if ("recepcion@lab.hn".equals(quote.getCreatedByUsername())) {
                assertEquals("recepcion@lab.hn", quote.getCreatedByName());
            }
        }

        // La memoria por llamada evita el N+1: las consultas ejecutadas NO escalan
        // con la cantidad de cotizaciones (sin memoria serían >= 7 solo por los
        // nombres; con memoria, una por usuario distinto más la consulta de la
        // página). El límite holgado separa con claridad ambos comportamientos.
        assertTrue(stats.getQueryExecutionCount() <= 5,
                "El listado no debe lanzar una consulta de nombre por cotización; "
                        + "consultas ejecutadas = " + stats.getQueryExecutionCount());
    }
}
