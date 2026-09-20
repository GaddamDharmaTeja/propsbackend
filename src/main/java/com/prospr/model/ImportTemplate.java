package com.prospr.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "import_templates")
public class ImportTemplate {

    @Id
    private String id;

    private String ownerId;

    private String householdId;

    private String name;

    private String bankName;

    private String fileType;

    private boolean active = true;

    private TableDefinition tableDefinition =
            new TableDefinition();

    private List<TemplateField> fields =
            new ArrayList<>();

    private ParsingRules parsingRules =
            new ParsingRules();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


    // =========================================================
    // BASIC GETTERS / SETTERS
    // =========================================================

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }


    public String getHouseholdId() {
        return householdId;
    }

    public void setHouseholdId(String householdId) {
        this.householdId = householdId;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }


    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }


    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }


    public TableDefinition getTableDefinition() {
        return tableDefinition;
    }

    public void setTableDefinition(
            TableDefinition tableDefinition
    ) {
        this.tableDefinition =
                tableDefinition;
    }


    public List<TemplateField> getFields() {
        return fields;
    }

    public void setFields(
            List<TemplateField> fields
    ) {
        this.fields =
                fields != null
                        ? fields
                        : new ArrayList<>();
    }


    public ParsingRules getParsingRules() {
        return parsingRules;
    }

    public void setParsingRules(
            ParsingRules parsingRules
    ) {
        this.parsingRules =
                parsingRules;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(
            LocalDateTime createdAt
    ) {
        this.createdAt = createdAt;
    }


    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(
            LocalDateTime updatedAt
    ) {
        this.updatedAt = updatedAt;
    }


    // =========================================================
    // TABLE DEFINITION
    // =========================================================

    public static class TableDefinition {

        private int headerRow = 0;

        private int dataStartRow = 1;

        private int sheetIndex = 0;

        private String headerContains;

        private boolean hasHeader = true;

        /*
         * Alternative names that may identify the
         * statement header.
         *
         * Example:
         *
         * Date
         * Transaction Date
         * Txn Date
         */
        private List<String> headerAliases =
                new ArrayList<>();

        /*
         * Text markers that indicate the end
         * of transaction data.
         *
         * Example:
         *
         * Total
         * Closing Balance
         * Statement Summary
         */
        private List<String> endMarkers =
                new ArrayList<>();


        public int getHeaderRow() {
            return headerRow;
        }

        public void setHeaderRow(
                int headerRow
        ) {
            this.headerRow = headerRow;
        }


        public int getDataStartRow() {
            return dataStartRow;
        }

        public void setDataStartRow(
                int dataStartRow
        ) {
            this.dataStartRow = dataStartRow;
        }


        public int getSheetIndex() {
            return sheetIndex;
        }

        public void setSheetIndex(
                int sheetIndex
        ) {
            this.sheetIndex = sheetIndex;
        }


        public String getHeaderContains() {
            return headerContains;
        }

        public void setHeaderContains(
                String headerContains
        ) {
            this.headerContains =
                    headerContains;
        }


        public boolean isHasHeader() {
            return hasHeader;
        }

        public void setHasHeader(
                boolean hasHeader
        ) {
            this.hasHeader = hasHeader;
        }


        public List<String> getHeaderAliases() {
            return headerAliases;
        }

        public void setHeaderAliases(
                List<String> headerAliases
        ) {
            this.headerAliases =
                    headerAliases != null
                            ? headerAliases
                            : new ArrayList<>();
        }


        public List<String> getEndMarkers() {
            return endMarkers;
        }

        public void setEndMarkers(
                List<String> endMarkers
        ) {
            this.endMarkers =
                    endMarkers != null
                            ? endMarkers
                            : new ArrayList<>();
        }
    }


    // =========================================================
    // TEMPLATE FIELD
    // =========================================================

    public static class TemplateField {

        /*
         * Target Prospr field.
         *
         * DATE
         * DESCRIPTION
         * REFERENCE
         * VALUE_DATE
         * DEBIT
         * CREDIT
         * BALANCE
         */
        private String field;

        /*
         * Source bank statement column.
         */
        private String source;

        private boolean enabled = true;


        public String getField() {
            return field;
        }

        public void setField(
                String field
        ) {
            this.field = field;
        }


        public String getSource() {
            return source;
        }

        public void setSource(
                String source
        ) {
            this.source = source;
        }


        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(
                boolean enabled
        ) {
            this.enabled = enabled;
        }


        /*
         * React/UI aliases.
         */

        public String getType() {
            return field;
        }

        public void setType(
                String type
        ) {
            this.field = type;
        }


        public String getSourceColumn() {
            return source;
        }

        public void setSourceColumn(
                String sourceColumn
        ) {
            this.source = sourceColumn;
        }
    }


    // =========================================================
    // PARSING RULES
    // =========================================================

    public static class ParsingRules {

        private String dateFormat;

        private String dateLocale;

        private String decimalSeparator = ".";

        private String thousandsSeparator = ",";

        private boolean skipBlankRows = true;

        private boolean skipFooter = true;

        private boolean validateDebitCredit = true;

        private boolean neverUseClosingBalanceAsAmount = true;

        /*
         * Bank statements sometimes split a single
         * transaction description across multiple lines.
         */
        private boolean multilineDescription = true;

        private String narrationCleanupRegex;


        public String getDateFormat() {
            return dateFormat;
        }

        public void setDateFormat(
                String dateFormat
        ) {
            this.dateFormat = dateFormat;
        }


        public String getDateLocale() {
            return dateLocale;
        }

        public void setDateLocale(
                String dateLocale
        ) {
            this.dateLocale = dateLocale;
        }


        public String getDecimalSeparator() {
            return decimalSeparator;
        }

        public void setDecimalSeparator(
                String decimalSeparator
        ) {
            this.decimalSeparator =
                    decimalSeparator;
        }


        public String getThousandsSeparator() {
            return thousandsSeparator;
        }

        public void setThousandsSeparator(
                String thousandsSeparator
        ) {
            this.thousandsSeparator =
                    thousandsSeparator;
        }


        public boolean isSkipBlankRows() {
            return skipBlankRows;
        }

        public void setSkipBlankRows(
                boolean skipBlankRows
        ) {
            this.skipBlankRows =
                    skipBlankRows;
        }


        public boolean isSkipFooter() {
            return skipFooter;
        }

        public void setSkipFooter(
                boolean skipFooter
        ) {
            this.skipFooter = skipFooter;
        }


        public boolean isValidateDebitCredit() {
            return validateDebitCredit;
        }

        public void setValidateDebitCredit(
                boolean validateDebitCredit
        ) {
            this.validateDebitCredit =
                    validateDebitCredit;
        }


        public boolean isNeverUseClosingBalanceAsAmount() {
            return neverUseClosingBalanceAsAmount;
        }

        public void setNeverUseClosingBalanceAsAmount(
                boolean value
        ) {
            this.neverUseClosingBalanceAsAmount =
                    value;
        }


        public boolean isMultilineDescription() {
            return multilineDescription;
        }

        public void setMultilineDescription(
                boolean multilineDescription
        ) {
            this.multilineDescription =
                    multilineDescription;
        }


        public String getNarrationCleanupRegex() {
            return narrationCleanupRegex;
        }

        public void setNarrationCleanupRegex(
                String narrationCleanupRegex
        ) {
            this.narrationCleanupRegex =
                    narrationCleanupRegex;
        }
    }
}