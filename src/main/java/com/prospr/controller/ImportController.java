package com.prospr.controller;

import com.prospr.dto.ImportConfirmation;
import com.prospr.model.FinancialAccount;
import com.prospr.model.ImportBatch;
import com.prospr.model.ImportTemplate;
import com.prospr.model.TransactionEntry;
import com.prospr.repository.CategoryRuleRepository;
import com.prospr.repository.FinancialAccountRepository;
import com.prospr.repository.ImportBatchRepository;
import com.prospr.repository.ImportTemplateRepository;
import com.prospr.repository.TransactionRepository;
import com.prospr.service.HouseholdAccess;
import com.prospr.service.HouseholdCategoryService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/imports")
@CrossOrigin(
        origins = "${PROSPR_WEB_ORIGIN:http://localhost:3000}"
)
public class ImportController {

    private final TransactionRepository transactions;
    private final ImportBatchRepository batches;
    private final FinancialAccountRepository accounts;
    private final CategoryRuleRepository rules;
    private final HouseholdAccess access;
    private final ImportTemplateRepository importTemplates;
    private final HouseholdCategoryService householdCategories;

    private static final Pattern CSV_SEPARATOR =
            Pattern.compile(
                    ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"
            );

    private static final Pattern DATE_AT_START =
            Pattern.compile(
                    "^\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b.*"
            );

    private static final List<DateTimeFormatter> DATE_FORMATS =
            List.of(
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                    DateTimeFormatter.ofPattern("dd/MM/yy"),
                    DateTimeFormatter.ofPattern("dd-MM-yy"),
                    DateTimeFormatter.ofPattern("d/M/yyyy"),
                    DateTimeFormatter.ofPattern("d-M-yyyy"),
                    DateTimeFormatter.ofPattern("d/M/yy"),
                    DateTimeFormatter.ofPattern("d-M-yy"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                    DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                    DateTimeFormatter.ofPattern("MM-dd-yyyy"),
                    DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
                    DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
                    DateTimeFormatter.ofPattern("dd MMM yy", Locale.ENGLISH),
                    DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH),
                    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
                    DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH)
            );

    public ImportController(
            TransactionRepository transactions,
            ImportBatchRepository batches,
            FinancialAccountRepository accounts,
            CategoryRuleRepository rules,
            HouseholdAccess access,
            ImportTemplateRepository importTemplates,
            HouseholdCategoryService householdCategories
    ) {
        this.transactions = transactions;
        this.batches = batches;
        this.accounts = accounts;
        this.rules = rules;
        this.access = access;
        this.importTemplates = importTemplates;
        this.householdCategories = householdCategories;
    }

    // ============================================================
    // PARSE / PREVIEW
    // ============================================================

    @PostMapping(
            value = "/parse",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> parse(
            @AuthenticationPrincipal String account,
            @RequestParam("file") MultipartFile file,
            @RequestParam("accountId") String accountId,
            @RequestParam(
                    value = "templateId",
                    required = false
            )
            String templateId,
            @RequestParam(
                    value = "statementPassword",
                    required = false
            )
            String statementPassword
    ) throws Exception {

        System.out.println();
        System.out.println("==========================================");
        System.out.println("          PROSPR IMPORT PARSE");
        System.out.println("==========================================");

        System.out.println("OWNER      : " + account);
        System.out.println("ACCOUNT ID : " + accountId);

        System.out.println(
                "FILE       : " +
                        (file == null
                                ? null
                                : file.getOriginalFilename())
        );

        System.out.println(
                "FILE SIZE  : " +
                        (file == null
                                ? 0
                                : file.getSize())
        );

        System.out.println(
                "TEMPLATE ID: " +
                        (templateId == null || templateId.isBlank()
                                ? "AUTOMATIC"
                                : templateId)
        );

        System.out.println(
                "PASSWORD   : " +
                        (statementPassword != null &&
                                !statementPassword.isBlank())
        );

        // --------------------------------------------------------
        // Validate authentication
        // --------------------------------------------------------

        String household = access.householdId(account);

        // --------------------------------------------------------
        // Load import template
        // --------------------------------------------------------

        ImportTemplate template = null;

        if (templateId != null &&
                !templateId.isBlank()) {

            template =
                    importTemplates
                            .findByIdAndOwnerId(
                                    templateId,
                                    account
                            )
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND,
                                                    "Import template not found or you do not have access to it."
                                            )
                            );

            if (!template.isActive()) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The selected import template is inactive."
                );
            }

            /*
             * If your ImportTemplate model contains householdId,
             * validate it here.
             */
            if (template.getHouseholdId() != null &&
                    !template.getHouseholdId().isBlank() &&
                    !Objects.equals(
                            template.getHouseholdId(),
                            household
                    )) {

                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You do not have access to this import template."
                );
            }

            System.out.println(
                    "TEMPLATE NAME: " +
                            template.getName()
            );
        }

        // --------------------------------------------------------
        // Validate file
        // --------------------------------------------------------

        if (file == null || file.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Statement file is required."
            );
        }

        // --------------------------------------------------------
        // Validate account
        // --------------------------------------------------------

        if (accountId == null ||
                accountId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Financial account is required."
            );
        }

        requireAccount(
                household,
                accountId
        );

        String fileName =
                Optional
                        .ofNullable(
                                file.getOriginalFilename()
                        )
                        .orElse("statement");

        String lowerName =
                fileName.toLowerCase(
                        Locale.ROOT
                );

        byte[] bytes =
                file.getBytes();

        List<List<String>> table;

        // --------------------------------------------------------
        // CSV
        // --------------------------------------------------------

        if (lowerName.endsWith(".csv")) {

            System.out.println(
                    "FILE TYPE  : CSV"
            );

            table =
                    csv(bytes);

        // --------------------------------------------------------
        // Excel
        // --------------------------------------------------------

        } else if (
                lowerName.endsWith(".xls") ||
                lowerName.endsWith(".xlsx")
        ) {

            System.out.println(
                    "FILE TYPE  : EXCEL"
            );

            table =
                    excel(
                            new ByteArrayInputStream(bytes)
                    );

        // --------------------------------------------------------
        // PDF
        // --------------------------------------------------------

        } else if (
                lowerName.endsWith(".pdf")
        ) {

            System.out.println(
                    "FILE TYPE  : PDF"
            );

            table =
                    pdf(
                            bytes,
                            statementPassword
                    );

        } else {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported file format. " +
                            "Please upload CSV, Excel, or PDF."
            );
        }

        System.out.println(
                "PARSED ROWS : " +
                        table.size()
        );

        if (
                table == null ||
                        table.size() < 2
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No transaction rows were found in this file."
            );
        }

        int headerIndex =
                findStatementHeader(
                        table
                );

        if (
                headerIndex < 0 ||
                        headerIndex >= table.size() - 1
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No transaction table was found. The file needs a header such as Date, Description, and Debit or Credit."
            );
        }

        List<String> headers =
                normalizeHeaders(
                        table.get(headerIndex)
                );

        System.out.println(
                "HEADER ROW : " +
                        headerIndex
        );

        System.out.println(
                "HEADERS : " +
                        headers
        );

        // --------------------------------------------------------
        // Validate template mappings
        // --------------------------------------------------------

        if (template != null) {

            validateTemplate(
                    template,
                    headers
            );
        }

        // --------------------------------------------------------
        // Load transactions once
        // --------------------------------------------------------

        List<TransactionEntry> existingTransactions =
                transactions
                        .findByHouseholdIdOrderByDateDesc(
                                household
                        );

        List<Map<String, Object>> previewRows =
                new ArrayList<>();

        int skippedRows = 0;

        for (
                int i = headerIndex + 1;
                i < table.size();
                i++
        ) {

            Map<String, Object> row =
                    toPreview(
                            table.get(i),
                            headers,
                            accountId,
                            existingTransactions,
                            template
                    );

            if (row != null) {

                previewRows.add(row);

            } else {

                skippedRows++;
            }
        }

        System.out.println(
                "PREVIEW ROWS : " +
                        previewRows.size()
        );

        System.out.println(
                "SKIPPED ROWS : " +
                        skippedRows
        );

        if (previewRows.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The statement was read, but no valid " +
                            "transaction rows could be identified. Detected columns: " +
                            String.join(", ", headers) +
                            "."
            );
        }

        Map<String, Object> response =
                new HashMap<>();

        response.put(
                "filename",
                fileName
        );

        response.put(
                "sourceType",
                extension(lowerName)
        );

        response.put(
                "checksum",
                sha(bytes)
        );

        response.put(
                "headers",
                headers
        );

        response.put(
                "rows",
                previewRows
        );

        response.put(
                "requiresReview",
                true
        );

        response.put(
                "totalRows",
                previewRows.size()
        );

        response.put(
                "skippedRows",
                skippedRows
        );

        response.put(
                "templateId",
                template != null
                        ? template.getId()
                        : null
        );

        response.put(
                "templateName",
                template != null
                        ? template.getName()
                        : null
        );

        response.put(
                "mappingMode",
                template != null
                        ? "TEMPLATE"
                        : "AUTOMATIC"
        );

        System.out.println(
                "IMPORT PARSE SUCCESS"
        );

        System.out.println(
                "=========================================="
        );

        return ResponseEntity.ok(
                response
        );
    }

    // ============================================================
    // TEMPLATE VALIDATION
    // ============================================================

    private void validateTemplate(
            ImportTemplate template,
            List<String> headers
    ) {

        if (template.getFields() == null ||
                template.getFields().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The selected import template has no field mappings."
            );
        }

        validateTemplateField(
                template,
                headers,
                "DATE",
                true
        );

        validateTemplateField(
                template,
                headers,
                "DESCRIPTION",
                true
        );

        boolean hasDebit =
                hasTemplateField(
                        template,
                        "DEBIT"
                );

        boolean hasCredit =
                hasTemplateField(
                        template,
                        "CREDIT"
                );

        if (!hasDebit && !hasCredit) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The import template must contain a DEBIT or CREDIT mapping."
            );
        }
    }

    private void validateTemplateField(
            ImportTemplate template,
            List<String> headers,
            String type,
            boolean required
    ) {

        ImportTemplate.TemplateField field =
                findTemplateField(
                        template,
                        type
                );

        if (field == null) {

            if (required) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Import template is missing required field: " +
                                type
                );
            }

            return;
        }

        if (field.getSourceColumn() == null ||
                field.getSourceColumn().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Import template field " +
                            type +
                            " does not have a source column."
            );
        }

        Integer index =
                findHeaderIndex(
                        headers,
                        field.getSourceColumn()
                );

        if (index == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Template column '" +
                            field.getSourceColumn() +
                            "' was not found in the uploaded file."
            );
        }
    }

    private boolean hasTemplateField(
            ImportTemplate template,
            String type
    ) {

        return findTemplateField(
                template,
                type
        ) != null;
    }

    private ImportTemplate.TemplateField findTemplateField(
            ImportTemplate template,
            String type
    ) {

        if (template == null ||
                template.getFields() == null ||
                type == null) {

            return null;
        }

        for (
                ImportTemplate.TemplateField field :
                template.getFields()
        ) {

            if (field == null ||
                    field.getType() == null) {

                continue;
            }

            if (type.equalsIgnoreCase(
                    field.getType()
            )) {

                return field;
            }
        }

        return null;
    }

    // ============================================================
    // CONFIRM
    // ============================================================

    @PostMapping(
            value = "/confirm",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ImportBatch> confirm(
            @AuthenticationPrincipal String account,
            @RequestBody ImportConfirmation payload
    ) {

        String household =
                access.householdId(account);

        if (payload == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Import confirmation data is required."
            );
        }

        requireAccount(
                household,
                payload.accountId()
        );

        ImportBatch batch =
                new ImportBatch();

        batch.ownerId =
                account;

        batch.householdId =
                household;

        batch.accountId =
                payload.accountId();

        batch.filename =
                payload.filename();

        batch.checksum =
                payload.checksum();

        batch.sourceType =
                payload.sourceType();

        batch.mapping =
                payload.mapping();

        batch.status =
                "COMPLETED";

        batch.parsedCount =
                payload.rows() == null
                        ? 0
                        : payload.rows().size();

        batch =
                batches.save(batch);

        List<TransactionEntry> saved =
                new ArrayList<>();

        Set<String> knownTransactions =
                new HashSet<>();

        for (
                TransactionEntry existing :
                transactions
                        .findByHouseholdIdOrderByDateDesc(
                                household
                        )
        ) {

            knownTransactions.add(
                    transactionSignature(
                            existing.accountId,
                            existing.date,
                            existing.amount,
                            existing.normalizedDescription,
                            existing.reference
                    )
            );
        }

        if (payload.rows() != null) {

            for (
                    ImportConfirmation.Row row :
                    payload.rows()
            ) {

                if (row == null) {
                    continue;
                }

                if (
                        !Boolean.TRUE.equals(
                                row.include()
                        )
                ) {

                    batch.skippedCount++;

                    continue;
                }

                if (row.date() == null) {
                    continue;
                }

                if (
                        row.description() == null ||
                                row.description().isBlank()
                ) {

                    continue;
                }

                if (row.amount() == null) {
                    continue;
                }

                String normalizedDescription =
                        TransactionController.normalize(
                                row.description()
                        );

                String signature =
                        transactionSignature(
                                payload.accountId(),
                                row.date(),
                                row.amount().abs(),
                                normalizedDescription,
                                row.reference()
                        );

                /*
                 * Never silently create duplicate bank transactions.
                 */
                if (!knownTransactions.add(
                        signature
                )) {

                    batch.skippedCount++;

                    continue;
                }

                TransactionEntry transaction =
                        new TransactionEntry();

                transaction.id =
                        java.util.UUID.randomUUID().toString();

                transaction.ownerId =
                        account;

                transaction.householdId =
                        household;

                transaction.accountId =
                        payload.accountId();

                transaction.importBatchId =
                        batch.id;

                transaction.source =
                        "bank_import";

                transaction.date =
                        row.date();

                transaction.description =
                        row.description();

                transaction.normalizedDescription =
                        normalizedDescription;

                transaction.amount =
                        row.amount().abs();

                transaction.income =
                        Boolean.TRUE.equals(
                                row.income()
                        );

                transaction.debitAmount =
                        row.debitAmount() == null
                                ? (
                                transaction.income
                                        ? null
                                        : transaction.amount
                        )
                                : row.debitAmount().abs();

                transaction.creditAmount =
                        row.creditAmount() == null
                                ? (
                                transaction.income
                                        ? transaction.amount
                                        : null
                        )
                                : row.creditAmount().abs();

                transaction.closingBalance =
                        row.closingBalance() == null
                                ? null
                                : row.closingBalance().abs();

                transaction.category =
                        row.category() == null ||
                                row.category().isBlank()
                                ? category(
                                row.description()
                        )
                                : row.category();

                com.prospr.service.CategoryCatalog.apply(transaction);
                if (householdCategories != null) {
                    householdCategories.overlay(transaction);
                }

                transaction.memberId =
                        row.memberId();

                transaction.reference =
                        row.reference();

                saved.add(
                        transaction
                );
            }
        }

        if (!saved.isEmpty()) {

            transactions.saveAll(
                    saved
            );
        }

        batch.importedCount =
                saved.size();

        batch.completedAt =
                LocalDateTime.now();

        batch.status =
                "COMPLETED";

        return ResponseEntity.ok(
                batches.save(batch)
        );
    }

    // ============================================================
    // LIST IMPORT BATCHES
    // ============================================================

    @GetMapping(
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<ImportBatch>> list(
            @AuthenticationPrincipal String account
    ) {

        return ResponseEntity.ok(
                batches
                        .findByHouseholdIdOrderByCreatedAtDesc(
                                access.householdId(account)
                        )
        );
    }

    // ============================================================
    // ACCOUNT
    // ============================================================

    private void requireAccount(
            String household,
            String accountId
    ) {

        if (accountId == null ||
                accountId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Financial account is required."
            );
        }

        FinancialAccount account =
                accounts.findById(accountId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Financial account not found."
                                        )
                        );

        if (
                !Objects.equals(
                        household,
                        account.householdId
                )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have access to this financial account."
            );
        }
    }

    // ============================================================
    // CSV
    // ============================================================

    private List<List<String>> csv(
            byte[] bytes
    ) {

        if (
                bytes == null ||
                        bytes.length == 0
        ) {

            return Collections.emptyList();
        }

        String content =
                new String(
                        bytes,
                        StandardCharsets.UTF_8
                );

        content =
                content.replace(
                        "\uFEFF",
                        ""
                );

        List<List<String>> result =
                new ArrayList<>();

        for (
                String line :
                content.split("\\R")
        ) {

            if (
                    line == null ||
                            line.isBlank()
            ) {

                continue;
            }

            String[] columns =
                    CSV_SEPARATOR.split(
                            line,
                            -1
                    );

            List<String> row =
                    new ArrayList<>();

            for (
                    String column :
                    columns
            ) {

                row.add(
                        cleanCsvValue(
                                column
                        )
                );
            }

            result.add(row);
        }

        return result;
    }

    private String cleanCsvValue(
            String value
    ) {

        if (value == null) {
            return "";
        }

        String result =
                value.trim();

        if (
                result.length() >= 2 &&
                        result.startsWith("\"") &&
                        result.endsWith("\"")
        ) {

            result =
                    result.substring(
                            1,
                            result.length() - 1
                    );
        }

        return result
                .replace(
                        "\"\"",
                        "\""
                )
                .trim();
    }

    // ============================================================
    // EXCEL
    // ============================================================

    private List<List<String>> excel(
            InputStream input
    ) throws IOException {

        List<List<String>> result =
                new ArrayList<>();

        try (
                Workbook workbook =
                        WorkbookFactory.create(input)
        ) {

            if (
                    workbook.getNumberOfSheets() == 0
            ) {

                return result;
            }

            DataFormatter formatter =
                    new DataFormatter();

            for (
                    Row row :
                    workbook.getSheetAt(0)
            ) {

                List<String> cells =
                        new ArrayList<>();

                int lastCell =
                        row.getLastCellNum();

                if (lastCell < 0) {
                    continue;
                }

                for (
                        int i = 0;
                        i < lastCell;
                        i++
                ) {

                    Cell cell =
                            row.getCell(i);

                    if (cell == null) {

                        cells.add("");

                    } else {

                        cells.add(
                                formatter
                                        .formatCellValue(
                                                cell
                                        )
                                        .trim()
                        );
                    }
                }

                boolean hasData =
                        cells.stream()
                                .anyMatch(
                                        x ->
                                                x != null &&
                                                        !x.isBlank()
                                );

                if (hasData) {
                    result.add(cells);
                }
            }
        }

        return result;
    }

    // ============================================================
    // PDF
    // ============================================================

    private List<List<String>> pdf(
            byte[] bytes,
            String password
    ) throws IOException {

        if (
                bytes == null ||
                        bytes.length == 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PDF file is empty."
            );
        }

        String text;

        try {

            String actualPassword =
                    password == null
                            ? ""
                            : password;

            try (
                    var document =
                            Loader.loadPDF(
                                    bytes,
                                    actualPassword
                            )
            ) {

                System.out.println(
                        "PDF ENCRYPTED : " +
                                document.isEncrypted()
                );

                System.out.println(
                        "PDF PAGES     : " +
                                document.getNumberOfPages()
                );

                PDFTextStripper stripper =
                        new PDFTextStripper();

                stripper.setSortByPosition(
                        true
                );

                text =
                        stripper.getText(
                                document
                        );
            }

        } catch (
                org.apache.pdfbox
                        .pdmodel
                        .encryption
                        .InvalidPasswordException e
        ) {

            System.out.println(
                    "PDF PASSWORD ERROR"
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The supplied PDF statement password is incorrect."
            );
        }

        if (
                text == null ||
                        text.isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The PDF has no selectable text."
            );
        }

        System.out.println(
                "PDF TEXT LENGTH : " +
                        text.length()
        );

        return parseBankPdfText(text);
    }

    // ============================================================
    // BANK PDF PARSER
    // ============================================================

    private List<List<String>> parseBankPdfText(
            String text
    ) {

        /*
         * First try the HDFC-specific parser.
         */
        List<List<String>> hdfcRows =
                parseHdfcStatement(text);

        if (hdfcRows.size() > 1) {
            return hdfcRows;
        }

        List<List<String>> result =
                new ArrayList<>();

        result.add(
                new ArrayList<>(
                        List.of(
                                "Date",
                                "Description",
                                "Reference",
                                "Value Date",
                                "Withdrawal Amt",
                                "Deposit Amt",
                                "Closing Balance"
                        )
                )
        );

        String[] lines =
                text.split("\\R");

        String currentDate = null;

        StringBuilder currentNarration =
                new StringBuilder();

        String currentReference = "";
        String currentValueDate = "";
        String currentWithdrawal = "";
        String currentDeposit = "";
        String currentBalance = "";

        boolean insideTransactions = false;

        for (
                String originalLine :
                lines
        ) {

            if (
                    originalLine == null ||
                            originalLine.isBlank()
            ) {

                continue;
            }

            String line =
                    originalLine.trim();

            String lower =
                    line.toLowerCase(
                            Locale.ROOT
                    );

            // ----------------------------------------------------
            // Ignore page/header information
            // ----------------------------------------------------

            if (
                    lower.contains("statement of account") ||
                            lower.contains("account number") ||
                            lower.contains("account no") ||
                            lower.contains("customer id") ||
                            lower.contains("branch") ||
                            lower.contains("ifsc") ||
                            lower.contains("micr") ||
                            lower.contains("opening balance") ||
                            lower.startsWith("page ")
            ) {

                continue;
            }

            // ----------------------------------------------------
            // Detect transaction header
            // ----------------------------------------------------

            if (
                    lower.contains("narration") &&
                            (
                                    lower.contains("withdrawal") ||
                                            lower.contains("deposit")
                            )
            ) {

                insideTransactions = true;

                continue;
            }

            if (
                    !insideTransactions &&
                            !DATE_AT_START.matcher(
                                    line
                            ).matches()
            ) {

                continue;
            }

            // ----------------------------------------------------
            // New transaction
            // ----------------------------------------------------

            java.util.regex.Matcher matcher =
                    DATE_AT_START.matcher(
                            line
                    );

            if (matcher.matches()) {

                if (currentDate != null) {

                    addPdfTransaction(
                            result,
                            currentDate,
                            currentNarration.toString(),
                            currentReference,
                            currentValueDate,
                            currentWithdrawal,
                            currentDeposit,
                            currentBalance
                    );
                }

                currentDate =
                        matcher.group(1);

                currentNarration =
                        new StringBuilder();

                currentReference = "";
                currentValueDate = "";
                currentWithdrawal = "";
                currentDeposit = "";
                currentBalance = "";

                String remaining =
                        line.substring(
                                matcher.end(1)
                        ).trim();

                parsePdfTransactionLine(
                        remaining,
                        currentNarration
                );

                continue;
            }

            // ----------------------------------------------------
            // Continuation
            // ----------------------------------------------------

            if (currentDate != null) {

                parseContinuationLine(
                        line,
                        currentNarration
                );
            }
        }

        // --------------------------------------------------------
        // Last transaction
        // --------------------------------------------------------

        if (currentDate != null) {

            addPdfTransaction(
                    result,
                    currentDate,
                    currentNarration.toString(),
                    currentReference,
                    currentValueDate,
                    currentWithdrawal,
                    currentDeposit,
                    currentBalance
            );
        }

        if (result.size() <= 1) {

            return parsePdfFallback(
                    text
            );
        }

        return result;
    }

    // ============================================================
    // HDFC PDF PARSER
    // ============================================================

    /*
     * HDFC PDFs wrap narration across lines. Matching only whole single
     * lines drops most rows and can glue the next txn into the previous
     * narration. Join content lines first, then find complete txn spans.
     */
    private static final Pattern HDFC_TRANSACTION_SPAN =
            Pattern.compile(
                    "(\\d{2}/\\d{2}/\\d{2})\\s+" +
                            "(.+?)\\s+" +
                            "([A-Za-z0-9]{10,})\\s+" +
                            "(\\d{2}/\\d{2}/\\d{2})\\s+" +
                            "([0-9,]+\\.\\d{2})\\s+" +
                            "([0-9,]+\\.\\d{2})"
            );

    private static final Pattern HDFC_INCOME_HINT =
            Pattern.compile(
                    "\\b(salary|payroll|neft\\s*cr|imps\\s*cr|credit\\s*interest|interest\\s*credit)\\b",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern HDFC_NARRATION_BLEED =
            Pattern.compile(
                    "(?i)\\s+(?:Page No\\s*\\.:|Statement of account).*$"
            );

    private List<List<String>> parseHdfcStatement(
            String text
    ) {

        List<List<String>> rows =
                new ArrayList<>();

        rows.add(
                new ArrayList<>(
                        List.of(
                                "Date",
                                "Description",
                                "Reference",
                                "Value Date",
                                "Withdrawal Amt",
                                "Deposit Amt",
                                "Closing Balance"
                        )
                )
        );

        List<String> contentLines =
                new ArrayList<>();

        for (
                String source :
                text.split("\\R")
        ) {

            String line =
                    source == null
                            ? ""
                            : source.trim();

            if (line.isBlank()) {
                continue;
            }

            String lower =
                    line.toLowerCase(
                            Locale.ROOT
                    );

            if (isHdfcNoiseLine(lower)) {
                continue;
            }

            contentLines.add(line);
        }

        if (contentLines.isEmpty()) {
            return rows;
        }

        String joined =
                String.join(
                        " ",
                        contentLines
                );

        java.util.regex.Matcher match =
                HDFC_TRANSACTION_SPAN.matcher(
                        joined
                );

        BigDecimal previousBalance = null;

        while (match.find()) {

            HdfcPdfRow current =
                    new HdfcPdfRow(
                            match.group(1),
                            match.group(2),
                            match.group(3),
                            match.group(4),
                            money(match.group(5)),
                            money(match.group(6))
                    );

            previousBalance =
                    addHdfcRow(
                            rows,
                            current,
                            previousBalance
                    );
        }

        return rows;
    }

    private boolean isHdfcNoiseLine(
            String lower
    ) {

        return lower.equals("hdfc bank limited") ||
                lower.startsWith("*closing balance") ||
                lower.startsWith("contents of this statement") ||
                lower.startsWith("state account branch") ||
                lower.startsWith("registered office") ||
                lower.startsWith("generated on:") ||
                lower.startsWith("this is a computer generated") ||
                lower.startsWith("statement summary") ||
                lower.startsWith("page no") ||
                lower.contains("statement of account") ||
                lower.contains("account branch") ||
                lower.startsWith("address") ||
                lower.startsWith("city") ||
                lower.startsWith("state") ||
                lower.startsWith("currency") ||
                lower.startsWith("email") ||
                lower.startsWith("phone no") ||
                lower.startsWith("od limit") ||
                lower.startsWith("cust id") ||
                lower.startsWith("joint holders") ||
                lower.startsWith("nomination") ||
                lower.startsWith("a/c open") ||
                lower.startsWith("account status") ||
                lower.startsWith("mr.") ||
                lower.startsWith("ms.") ||
                lower.startsWith("mrs.") ||
                lower.contains("account no") ||
                lower.contains("customer id") ||
                lower.contains("ifsc") ||
                lower.contains("micr") ||
                lower.contains("branch code") ||
                lower.contains("account type") ||
                lower.startsWith("from :") ||
                (
                        lower.contains("narration") &&
                                lower.contains("closing balance")
                );
    }

    private boolean isHdfcHeaderOrAccountLine(
            String lower
    ) {

        return isHdfcNoiseLine(lower);
    }

    private BigDecimal addHdfcRow(
            List<List<String>> rows,
            HdfcPdfRow row,
            BigDecimal previousBalance
    ) {

        String narration =
                cleanHdfcNarration(
                        row.narration.toString()
                );

        boolean credit =
                isHdfcCredit(
                        previousBalance,
                        row.closingBalance,
                        narration
                );

        rows.add(
                new ArrayList<>(
                        List.of(
                                row.date,
                                narration,
                                row.reference,
                                row.valueDate,
                                credit
                                        ? ""
                                        : row.amount.toPlainString(),
                                credit
                                        ? row.amount.toPlainString()
                                        : "",
                                row.closingBalance.toPlainString()
                        )
                )
        );

        return row.closingBalance;
    }

    private boolean isHdfcCredit(
            BigDecimal previousBalance,
            BigDecimal closingBalance,
            String narration
    ) {

        if (previousBalance != null) {
            return closingBalance.compareTo(
                    previousBalance
            ) > 0;
        }

        return HDFC_INCOME_HINT.matcher(
                narration == null ? "" : narration
        ).find();
    }

    private String cleanHdfcNarration(
            String raw
    ) {

        if (raw == null || raw.isBlank()) {
            return "";
        }

        String narration =
                raw.replaceAll("\\s+", " ").trim();

        narration =
                HDFC_NARRATION_BLEED
                        .matcher(narration)
                        .replaceFirst("");

        return narration.trim();
    }

    private BigDecimal money(
            String raw
    ) {

        return new BigDecimal(
                raw.replace(",", "")
        );
    }

    private static final class HdfcPdfRow {

        final String date;
        final String reference;
        final String valueDate;

        final StringBuilder narration;

        final BigDecimal amount;
        final BigDecimal closingBalance;

        HdfcPdfRow(
                String date,
                String narration,
                String reference,
                String valueDate,
                BigDecimal amount,
                BigDecimal closingBalance
        ) {

            this.date = date;
            this.narration =
                    new StringBuilder(
                            narration
                    );

            this.reference = reference;
            this.valueDate = valueDate;
            this.amount = amount;
            this.closingBalance =
                    closingBalance;
        }
    }

    // ============================================================
    // PDF TRANSACTION LINE
    // ============================================================

    private void parsePdfTransactionLine(
            String line,
            StringBuilder narration
    ) {

        if (
                line == null ||
                        line.isBlank()
        ) {

            return;
        }

        narration.append(
                line.trim()
        );
    }

    // ============================================================
    // PDF CONTINUATION
    // ============================================================

    private void parseContinuationLine(
            String line,
            StringBuilder narration
    ) {

        if (
                line == null ||
                        line.isBlank()
        ) {

            return;
        }

        String lower =
                line.toLowerCase(
                        Locale.ROOT
                );

        if (
                lower.contains("withdrawal amt") ||
                        lower.contains("deposit amt") ||
                        lower.contains("closing balance")
        ) {

            return;
        }

        if (
                lower.contains("generated on") ||
                        lower.contains("this is a computer generated")
        ) {

            return;
        }

        if (narration.length() > 0) {

            narration.append(" ");
        }

        narration.append(
                line.trim()
        );
    }

    // ============================================================
    // ADD PDF TRANSACTION
    // ============================================================

    private void addPdfTransaction(
            List<List<String>> result,
            String date,
            String narration,
            String reference,
            String valueDate,
            String withdrawal,
            String deposit,
            String balance
    ) {

        if (
                date == null ||
                        date.isBlank()
        ) {

            return;
        }

        if (
                narration == null ||
                        narration.isBlank()
        ) {

            return;
        }

        List<String> moneyValues =
                extractMoneyValues(
                        narration
                );

        String cleanNarration =
                narration.trim();

        String withdrawalAmount = "";
        String depositAmount = "";
        String closingBalance = "";

        if (moneyValues.size() >= 3) {

            withdrawalAmount =
                    moneyValues.get(
                            moneyValues.size() - 3
                    );

            depositAmount =
                    moneyValues.get(
                            moneyValues.size() - 2
                    );

            closingBalance =
                    moneyValues.get(
                            moneyValues.size() - 1
                    );

            cleanNarration =
                    removeLastMoneyValues(
                            cleanNarration,
                            3
                    );
        }

        List<String> row =
                new ArrayList<>();

        row.add(
                date.trim()
        );

        row.add(
                cleanNarration
        );

        row.add(
                reference == null
                        ? ""
                        : reference
        );

        row.add(
                valueDate == null
                        ? ""
                        : valueDate
        );

        row.add(
                withdrawalAmount
        );

        row.add(
                depositAmount
        );

        row.add(
                closingBalance
        );

        result.add(row);
    }

    // ============================================================
    // MONEY EXTRACTION FROM PDF
    // ============================================================

    private List<String> extractMoneyValues(
            String text
    ) {

        List<String> values =
                new ArrayList<>();

        if (
                text == null ||
                        text.isBlank()
        ) {

            return values;
        }

        String[] tokens =
                text.trim()
                        .split("\\s+");

        for (
                String token :
                tokens
        ) {

            String cleaned =
                    token
                            .replace(
                                    ",",
                                    ""
                            )
                            .replace(
                                    "₹",
                                    ""
                            )
                            .trim();

            if (
                    cleaned.matches(
                            "\\(?-?\\d+(?:\\.\\d+)?\\)?"
                    )
            ) {

                values.add(
                        cleaned
                );
            }
        }

        return values;
    }

    // ============================================================
    // REMOVE MONEY VALUES
    // ============================================================

    private String removeLastMoneyValues(
            String text,
            int count
    ) {

        String result =
                text.trim();

        for (
                int i = 0;
                i < count;
                i++
        ) {

            result =
                    result.replaceFirst(
                            "\\s+\\(?-?[0-9,]+(?:\\.[0-9]+)?\\)?\\s*$",
                            ""
                    );
        }

        return result.trim();
    }

    // ============================================================
    // PDF FALLBACK
    // ============================================================

    private List<List<String>> parsePdfFallback(
            String text
    ) {

        List<List<String>> result =
                new ArrayList<>();

        result.add(
                new ArrayList<>(
                        List.of(
                                "Date",
                                "Description",
                                "Amount"
                        )
                )
        );

        String[] lines =
                text.split("\\R");

        for (
                String line :
                lines
        ) {

            if (
                    line == null ||
                            line.isBlank()
            ) {

                continue;
            }

            String trimmed =
                    line.trim();

            java.util.regex.Matcher matcher =
                    DATE_AT_START.matcher(
                            trimmed
                    );

            if (!matcher.matches()) {
                continue;
            }

            String date =
                    matcher.group(1);

            String description =
                    trimmed.substring(
                            matcher.end(1)
                    ).trim();

            List<String> money =
                    extractMoneyValues(
                            description
                    );

            if (money.isEmpty()) {
                continue;
            }

            String amount =
                    money.get(
                            money.size() - 1
                    );

            description =
                    removeLastMoneyValues(
                            description,
                            1
                    );

            result.add(
                    new ArrayList<>(
                            List.of(
                                    date,
                                    description,
                                    amount
                            )
                    )
            );
        }

        return result;
    }

    // ============================================================
    // PREVIEW
    // ============================================================

    private Map<String, Object> toPreview(
            List<String> values,
            List<String> headers,
            String accountId,
            List<TransactionEntry> existingTransactions,
            ImportTemplate template
    ) {

        if (
                values == null ||
                        headers == null
        ) {

            return null;
        }

        String date;
        String description;
        String reference;
        String valueDate;
        String closingBalance;

        AmountResult amountResult;

        // --------------------------------------------------------
        // TEMPLATE MAPPING
        // --------------------------------------------------------

        if (template != null) {

            date =
                    findTemplateValue(
                            values,
                            headers,
                            template,
                            "DATE"
                    );

            description =
                    findTemplateValue(
                            values,
                            headers,
                            template,
                            "DESCRIPTION"
                    );

            reference =
                    findTemplateValue(
                            values,
                            headers,
                            template,
                            "REFERENCE"
                    );

            valueDate =
                    findTemplateValue(
                            values,
                            headers,
                            template,
                            "VALUE_DATE"
                    );

            closingBalance =
                    findTemplateValue(
                            values,
                            headers,
                            template,
                            "BALANCE"
                    );

            amountResult =
                    extractTemplateAmount(
                            values,
                            headers,
                            template
                    );

        } else {

            // ----------------------------------------------------
            // AUTOMATIC MAPPING
            // ----------------------------------------------------

            date =
                    findValue(
                            values,
                            headers,
                            "date",
                            "transaction date",
                            "txn date"
                    );

            description =
                    findValue(
                            values,
                            headers,
                            "description",
                            "narration",
                            "particulars",
                            "details",
                            "remarks",
                            "transaction details"
                    );

            reference =
                    findValue(
                            values,
                            headers,
                            "reference",
                            "chq ref no",
                            "chq./ref.no.",
                            "ref no"
                    );

            valueDate =
                    findValue(
                            values,
                            headers,
                            "value date",
                            "value dt"
                    );

            closingBalance =
                    findValue(
                            values,
                            headers,
                            "closing balance",
                            "balance"
                    );

            amountResult =
                    extractAmount(
                            values,
                            headers
                    );
        }

        if (
                date == null ||
                        description == null ||
                        description.isBlank()
        ) {

            return null;
        }

        try {

            LocalDate transactionDate =
                    parseDate(date);

            if (
                    amountResult == null ||
                            amountResult.amount() == null
            ) {

                return null;
            }

            BigDecimal amount =
                    amountResult
                            .amount()
                            .abs();

            if (
                    amount.compareTo(
                            BigDecimal.ZERO
                    ) == 0
            ) {

                return null;
            }

            String normalized =
                    TransactionController.normalize(
                            description
                    );

            boolean duplicate =
                    isDuplicate(
                            existingTransactions,
                            accountId,
                            transactionDate,
                            amount,
                            normalized
                    );

            Map<String, Object> result =
                    new HashMap<>();

            result.put(
                    "date",
                    transactionDate
            );

            result.put(
                    "description",
                    description.trim()
            );

            result.put(
                    "amount",
                    amount
            );

            result.put(
                    "income",
                    amountResult.income()
            );

            result.put(
                    "category",
                    category(
                            description
                    )
            );

            BigDecimal parsedClosingBalance =
                    parseAmount(
                            closingBalance
                    );

            result.put(
                    "debitAmount",
                    amountResult.income()
                            ? null
                            : amount
            );

            result.put(
                    "creditAmount",
                    amountResult.income()
                            ? amount
                            : null
            );

            result.put(
                    "closingBalance",
                    parsedClosingBalance
            );

            result.put(
                    "reference",
                    reference
            );

            result.put(
                    "valueDate",
                    valueDate
            );

            result.put(
                    "potentialDuplicate",
                    duplicate
            );

            return result;

        } catch (Exception e) {

            System.out.println(
                    "SKIPPING ROW: " +
                            values
            );

            return null;
        }
    }

    // ============================================================
    // TEMPLATE VALUE
    // ============================================================

    private String findTemplateValue(
            List<String> values,
            List<String> headers,
            ImportTemplate template,
            String fieldType
    ) {

        ImportTemplate.TemplateField field =
                findTemplateField(
                        template,
                        fieldType
                );

        if (
                field == null ||
                        field.getSourceColumn() == null ||
                        field.getSourceColumn().isBlank()
        ) {

            return null;
        }

        Integer index =
                findHeaderIndex(
                        headers,
                        field.getSourceColumn()
                );

        if (
                index == null ||
                        index < 0 ||
                        index >= values.size()
        ) {

            return null;
        }

        String value =
                values.get(index);

        if (
                value == null ||
                        value.isBlank()
        ) {

            return null;
        }

        return value
                .replace(
                        "\"",
                        ""
                )
                .trim();
    }

    // ============================================================
    // TEMPLATE AMOUNT
    // ============================================================

    private AmountResult extractTemplateAmount(
            List<String> values,
            List<String> headers,
            ImportTemplate template
    ) {

        // --------------------------------------------------------
        // CREDIT
        // --------------------------------------------------------

        String creditValue =
                findTemplateValue(
                        values,
                        headers,
                        template,
                        "CREDIT"
                );

        BigDecimal credit =
                parseAmount(
                        creditValue
                );

        if (
                credit != null &&
                        credit.compareTo(
                                BigDecimal.ZERO
                        ) != 0
        ) {

            return new AmountResult(
                    credit.abs(),
                    true
            );
        }

        // --------------------------------------------------------
        // DEBIT
        // --------------------------------------------------------

        String debitValue =
                findTemplateValue(
                        values,
                        headers,
                        template,
                        "DEBIT"
                );

        BigDecimal debit =
                parseAmount(
                        debitValue
                );

        if (
                debit != null &&
                        debit.compareTo(
                                BigDecimal.ZERO
                        ) != 0
        ) {

            return new AmountResult(
                    debit.abs(),
                    false
            );
        }

        // --------------------------------------------------------
        // Generic AMOUNT fallback
        // --------------------------------------------------------

        String amountValue =
                findTemplateValue(
                        values,
                        headers,
                        template,
                        "AMOUNT"
                );

        BigDecimal amount =
                parseAmount(
                        amountValue
                );

        if (amount != null) {

            return new AmountResult(
                    amount.abs(),
                    amount.signum() > 0
            );
        }

        return null;
    }

    // ============================================================
    // AMOUNT
    // ============================================================

    private AmountResult extractAmount(
            List<String> values,
            List<String> headers
    ) {

        Integer withdrawalIndex =
                findHeaderIndex(
                        headers,
                        "withdrawal",
                        "withdrawal amt",
                        "debit",
                        "debit amount",
                        "withdrawals"
                );

        if (withdrawalIndex == null) {
            withdrawalIndex =
                    findExactHeader(
                            headers,
                            "dr"
                    );
        }

        Integer depositIndex =
                findHeaderIndex(
                        headers,
                        "deposit",
                        "deposit amt",
                        "credit",
                        "credit amount",
                        "deposits"
                );

        if (depositIndex == null) {
            depositIndex =
                    findExactHeader(
                            headers,
                            "cr"
                    );
        }

        // --------------------------------------------------------
        // Deposit = Income
        // --------------------------------------------------------

        if (
                depositIndex != null &&
                        depositIndex < values.size()
        ) {

            BigDecimal deposit =
                    parseAmount(
                            values.get(
                                    depositIndex
                            )
                    );

            if (
                    deposit != null &&
                            deposit.compareTo(
                                    BigDecimal.ZERO
                            ) != 0
            ) {

                return new AmountResult(
                        deposit.abs(),
                        true
                );
            }
        }

        // --------------------------------------------------------
        // Withdrawal = Expense
        // --------------------------------------------------------

        if (
                withdrawalIndex != null &&
                        withdrawalIndex < values.size()
        ) {

            BigDecimal withdrawal =
                    parseAmount(
                            values.get(
                                    withdrawalIndex
                            )
                    );

            if (
                    withdrawal != null &&
                            withdrawal.compareTo(
                                    BigDecimal.ZERO
                            ) != 0
            ) {

                return new AmountResult(
                        withdrawal.abs(),
                        false
                );
            }
        }

        // --------------------------------------------------------
        // Generic amount
        // --------------------------------------------------------

        Integer amountIndex =
                findHeaderIndex(
                        headers,
                        "amount",
                        "transaction amount",
                        "net amount"
                );

        if (
                amountIndex != null &&
                        amountIndex < values.size()
        ) {

            BigDecimal amount =
                    parseAmount(
                            values.get(
                                    amountIndex
                            )
                    );

            if (amount != null) {

                return new AmountResult(
                        amount.abs(),
                        amount.signum() > 0
                );
            }
        }

        return null;
    }

    // ============================================================
    // FIND VALUE
    // ============================================================

    private String findValue(
            List<String> values,
            List<String> headers,
            String... matches
    ) {

        for (
                int i = 0;
                i < headers.size() &&
                        i < values.size();
                i++
        ) {

            String header =
                    normalizeHeader(
                            headers.get(i)
                    );

            for (
                    String match :
                    matches
            ) {

                String normalizedMatch =
                        normalizeHeader(
                                match
                        );

                if (
                        header.equals(
                                normalizedMatch
                        ) ||
                                header.contains(
                                        normalizedMatch
                                )
                ) {

                    String value =
                            values.get(i);

                    if (
                            value != null &&
                                    !value.isBlank()
                    ) {

                        return value
                                .replace(
                                        "\"",
                                        ""
                                )
                                .trim();
                    }
                }
            }
        }

        return null;
    }

    // ============================================================
    // HEADER INDEX
    // ============================================================

    private Integer findHeaderIndex(
            List<String> headers,
            String... names
    ) {

        for (
                int i = 0;
                i < headers.size();
                i++
        ) {

            String header =
                    normalizeHeader(
                            headers.get(i)
                    );

            for (
                    String name :
                    names
            ) {

                String normalized =
                        normalizeHeader(
                                name
                        );

                if (
                        header.equals(
                                normalized
                        ) ||
                                header.contains(
                                        normalized
                                )
                ) {

                    return i;
                }
            }
        }

        return null;
    }

    private Integer findExactHeader(
            List<String> headers,
            String name
    ) {

        if (headers == null || name == null) {
            return null;
        }

        for (int i = 0; i < headers.size(); i++) {

            if (name.equals(normalizeHeader(headers.get(i)))) {
                return i;
            }
        }

        return null;
    }

    // ============================================================
    // AMOUNT PARSER
    // ============================================================

    private BigDecimal parseAmount(
            String value
    ) {

        if (
                value == null ||
                        value.isBlank()
        ) {

            return null;
        }

        String cleaned =
                value
                        .trim()
                        .replace(
                                ",",
                                ""
                        )
                        .replace(
                                "₹",
                                ""
                        )
                        .replace(
                                "Rs.",
                                ""
                        )
                        .replace(
                                "Rs",
                                ""
                        )
                        .trim();

        if (
                cleaned.equals("-") ||
                        cleaned.equals("--") ||
                        cleaned.equalsIgnoreCase("NA") ||
                        cleaned.equalsIgnoreCase("N/A")
        ) {

            return null;
        }

        boolean negative =
                cleaned.startsWith("(") &&
                        cleaned.endsWith(")");

        cleaned =
                cleaned
                        .replace(
                                "(",
                                ""
                        )
                        .replace(
                                ")",
                                ""
                        );

        cleaned =
                cleaned.replaceAll(
                        "[^0-9.\\-]",
                        ""
                );

        if (cleaned.isBlank()) {
            return null;
        }

        try {

            BigDecimal amount =
                    new BigDecimal(
                            cleaned
                    );

            if (negative) {

                amount =
                        amount.negate();
            }

            return amount;

        } catch (
                NumberFormatException e
        ) {

            return null;
        }
    }

    // ============================================================
    // DATE
    // ============================================================

    private LocalDate parseDate(
            String value
    ) {

        if (
                value == null ||
                        value.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Date is empty."
            );
        }

        String cleaned =
                value.trim();

        for (
                DateTimeFormatter formatter :
                DATE_FORMATS
        ) {

            try {

                return LocalDate.parse(
                        cleaned,
                        formatter
                );

            } catch (Exception ignored) {
            }
        }

        if (cleaned.contains(" ")) {

            String dateOnly =
                    cleaned.split(
                            "\\s+"
                    )[0];

            for (
                    DateTimeFormatter formatter :
                    DATE_FORMATS
            ) {

                try {

                    return LocalDate.parse(
                            dateOnly,
                            formatter
                    );

                } catch (Exception ignored) {
                }
            }
        }

        throw new IllegalArgumentException(
                "Unsupported date format: " +
                        value
        );
    }

    // ============================================================
    // DUPLICATE
    // ============================================================

    private boolean isDuplicate(
            List<TransactionEntry> existing,
            String accountId,
            LocalDate date,
            BigDecimal amount,
            String normalizedDescription
    ) {

        if (
                existing == null ||
                        existing.isEmpty()
        ) {

            return false;
        }

        for (
                TransactionEntry transaction :
                existing
        ) {

            if (
                    transaction == null ||
                            !Objects.equals(
                                    transaction.accountId,
                                    accountId
                            )
            ) {

                continue;
            }

            if (
                    !Objects.equals(
                            transaction.date,
                            date
                    )
            ) {

                continue;
            }

            if (
                    transaction.amount == null
            ) {

                continue;
            }

            if (
                    transaction.amount.compareTo(
                            amount.abs()
                    ) != 0
            ) {

                continue;
            }

            if (
                    Objects.equals(
                            transaction.normalizedDescription,
                            normalizedDescription
                    )
            ) {

                return true;
            }
        }

        return false;
    }

    private String transactionSignature(
            String accountId,
            LocalDate date,
            BigDecimal amount,
            String description,
            String reference
    ) {

        return String.join(
                "|",
                accountId == null
                        ? ""
                        : accountId,

                date == null
                        ? ""
                        : date.toString(),

                amount == null
                        ? ""
                        : amount
                        .abs()
                        .stripTrailingZeros()
                        .toPlainString(),

                description == null
                        ? ""
                        : description,

                reference == null
                        ? ""
                        : reference
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        )
        );
    }

    // ============================================================
    // CATEGORY
    // ============================================================

    private String category(
            String text
    ) {
        return com.prospr.service.CategoryCatalog.matchName(text);
    }

    // ============================================================
    // STATEMENT HEADER
    // ============================================================

    private int findStatementHeader(
            List<List<String>> table
    ) {

        int best = -1;
        int bestScore = 1;
        int limit = Math.min(table.size(), 80);

        for (int i = 0; i < limit; i++) {

            int score =
                    statementHeaderScore(
                            table.get(i)
                    );

            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }

        return best;
    }

    private int statementHeaderScore(
            List<String> row
    ) {

        if (row == null) {
            return 0;
        }

        boolean hasDate = false;
        boolean hasMoney = false;
        int score = 0;

        for (String cell : row) {

            String header =
                    normalizeHeader(cell);

            if (header.isBlank()) {
                continue;
            }

            if (
                    header.equals("date") ||
                            header.equals("dt") ||
                            header.contains("transaction date") ||
                            header.contains("txn date") ||
                            header.endsWith(" date")
            ) {
                hasDate = true;
                score++;
            } else if (
                    header.contains("narration") ||
                            header.contains("description") ||
                            header.contains("particular") ||
                            header.contains("details") ||
                            header.contains("remarks")
            ) {
                score++;
            } else if (
                    header.contains("debit") ||
                            header.contains("credit") ||
                            header.contains("withdrawal") ||
                            header.contains("deposit") ||
                            header.equals("dr") ||
                            header.equals("cr") ||
                            header.contains("amount")
            ) {
                hasMoney = true;
                score++;
            } else if (
                    header.contains("balance") ||
                            header.contains("reference") ||
                            header.contains("ref")
            ) {
                score++;
            }
        }

        if (!hasDate || !hasMoney) {
            return 0;
        }

        return score;
    }

    // ============================================================
    // HEADER NORMALIZATION
    // ============================================================

    private List<String> normalizeHeaders(
            List<String> headers
    ) {

        if (headers == null) {

            return new ArrayList<>();
        }

        List<String> result =
                new ArrayList<>();

        for (
                String header :
                headers
        ) {

            result.add(
                    header == null
                            ? ""
                            : header.trim()
            );
        }

        return result;
    }

    private String normalizeHeader(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "[^a-z0-9]+",
                        " "
                )
                .trim();
    }

    // ============================================================
    // EXTENSION
    // ============================================================

    private String extension(
            String name
    ) {

        if (
                name == null ||
                        name.isBlank()
        ) {

            return "UNKNOWN";
        }

        int index =
                name.lastIndexOf('.');

        if (
                index < 0 ||
                        index == name.length() - 1
        ) {

            return "UNKNOWN";
        }

        return name
                .substring(index + 1)
                .toUpperCase(
                        Locale.ROOT
                );
    }

    // ============================================================
    // SHA
    // ============================================================

    private String sha(
            byte[] bytes
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        return HexFormat.of().formatHex(
                digest.digest(bytes)
        );
    }

    // ============================================================
    // RESULT
    // ============================================================

    private record AmountResult(
            BigDecimal amount,
            boolean income
    ) {
    }
}