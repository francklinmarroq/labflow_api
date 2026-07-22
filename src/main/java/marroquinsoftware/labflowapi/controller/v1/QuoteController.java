package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.payload.AgeDiscountDTO;
import marroquinsoftware.labflowapi.payload.QuoteDTO;
import marroquinsoftware.labflowapi.payload.QuoteRequest;
import marroquinsoftware.labflowapi.payload.QuoteResponse;
import marroquinsoftware.labflowapi.service.QuoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    @Autowired
    private QuoteService quoteService;

    @GetMapping
    @PreAuthorize("hasAuthority('QUOTES_VIEW')")
    public ResponseEntity<QuoteResponse> getAllQuotes(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_QUOTES_BY) String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder) {
        return new ResponseEntity<>(quoteService.getAllQuotes(pageNumber, pageSize, sortBy, sortOrder), HttpStatus.OK);
    }

    @GetMapping("/{quoteId}")
    @PreAuthorize("hasAnyAuthority('QUOTES_VIEW','QUOTES_CREATE')")
    public ResponseEntity<QuoteDTO> getQuote(@PathVariable Long quoteId) {
        return new ResponseEntity<>(quoteService.getQuote(quoteId), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('QUOTES_CREATE')")
    public ResponseEntity<QuoteDTO> createQuote(@Valid @RequestBody QuoteRequest request) {
        return new ResponseEntity<>(quoteService.createQuote(request), HttpStatus.CREATED);
    }

    @DeleteMapping("/{quoteId}")
    @PreAuthorize("hasAuthority('QUOTES_DELETE')")
    public ResponseEntity<Void> deleteQuote(@PathVariable Long quoteId) {
        quoteService.deleteQuote(quoteId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    // Le dice a la pantalla de cotización qué descuento le tocaría al paciente
    // antes de guardar, para que la regla de edades viva solo en la API.
    @GetMapping("/discount")
    @PreAuthorize("hasAnyAuthority('QUOTES_CREATE','QUOTES_VIEW')")
    public ResponseEntity<AgeDiscountDTO> previewDiscount(@RequestParam Integer ageInDays) {
        return new ResponseEntity<>(quoteService.previewDiscount(ageInDays), HttpStatus.OK);
    }
}
