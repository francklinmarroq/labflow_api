package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.config.AppConstants;
import marroquinsoftware.labflowapi.model.JournalSourceType;
import marroquinsoftware.labflowapi.payload.JournalEntryDTO;
import marroquinsoftware.labflowapi.payload.JournalEntryRequest;
import marroquinsoftware.labflowapi.payload.JournalEntryResponse;
import marroquinsoftware.labflowapi.service.JournalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/journal-entries")
public class JournalController {

    @Autowired
    private JournalService journalService;

    @GetMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_VIEW')")
    public ResponseEntity<JournalEntryResponse> getEntries(
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(defaultValue = AppConstants.SORT_JOURNAL_BY) String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) JournalSourceType sourceType) {
        return new ResponseEntity<>(
                journalService.getEntries(pageNumber, pageSize, sortBy, sortOrder, from, to, sourceType),
                HttpStatus.OK);
    }

    @GetMapping("/{entryId}")
    @PreAuthorize("hasAuthority('ACCOUNTING_VIEW')")
    public ResponseEntity<JournalEntryDTO> getEntry(@PathVariable Long entryId) {
        return new ResponseEntity<>(journalService.getEntry(entryId), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<JournalEntryDTO> createManualEntry(@Valid @RequestBody JournalEntryRequest request) {
        return new ResponseEntity<>(journalService.createManualEntry(request), HttpStatus.CREATED);
    }
}
