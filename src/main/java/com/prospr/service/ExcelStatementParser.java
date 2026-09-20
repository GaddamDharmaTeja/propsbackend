package com.prospr.service;

import com.prospr.dto.ParsedTransaction;
import com.prospr.model.ImportTemplate;

import org.apache.poi.ss.usermodel.*;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExcelStatementParser
        implements BankStatementParser {

    @Override
    public List<ParsedTransaction> parse(
            InputStream inputStream,
            String password,
            ImportTemplate template
    ) throws Exception {

        List<ParsedTransaction> result =
                new ArrayList<>();

        try (Workbook workbook =
                     WorkbookFactory.create(inputStream)) {

            Sheet sheet =
                    workbook.getSheetAt(0);

            Row headerRow =
                    findHeaderRow(
                            sheet,
                            template
                    );

            if (headerRow == null) {
                throw new IllegalArgumentException(
                        "Transaction table header not found"
                );
            }

            Map<String, Integer> columns =
                    findColumns(
                            headerRow,
                            template
                    );

            for (
                    int rowIndex =
                            headerRow.getRowNum() + 1;

                    rowIndex <= sheet.getLastRowNum();

                    rowIndex++
            ) {

                Row row =
                        sheet.getRow(rowIndex);

                if (row == null) {
                    continue;
                }

                String firstCell =
                        getCellValue(
                                row,
                                0
                        );

                if (isEndMarker(
                        firstCell,
                        template
                )) {
                    break;
                }

                String dateText =
                        getMappedValue(
                                row,
                                columns,
                                "DATE"
                        );

                /*
                 * Date starts a transaction.
                 */
                if (dateText == null ||
                        dateText.isBlank()) {

                    continue;
                }

                LocalDate date =
                        parseDate(dateText);

                if (date == null) {
                    continue;
                }

                String description =
                        getMappedValue(
                                row,
                                columns,
                                "DESCRIPTION"
                        );

                String reference =
                        getMappedValue(
                                row,
                                columns,
                                "REFERENCE"
                        );

                String valueDateText =
                        getMappedValue(
                                row,
                                columns,
                                "VALUE_DATE"
                        );

                LocalDate valueDate =
                        parseDate(valueDateText);

                BigDecimal debit =
                        parseAmount(
                                getMappedValue(
                                        row,
                                        columns,
                                        "DEBIT"
                                )
                        );

                BigDecimal credit =
                        parseAmount(
                                getMappedValue(
                                        row,
                                        columns,
                                        "CREDIT"
                                )
                        );

                BigDecimal balance =
                        parseAmount(
                                getMappedValue(
                                        row,
                                        columns,
                                        "BALANCE"
                                )
                        );

                /*
                 * Ignore completely empty rows.
                 */
                if (
                        description == null &&
                        debit == null &&
                        credit == null
                ) {
                    continue;
                }

                result.add(
                        new ParsedTransaction(
                                date,
                                description,
                                reference,
                                valueDate,
                                debit,
                                credit,
                                balance
                        )
                );
            }
        }

        return result;
    }


    private Row findHeaderRow(
            Sheet sheet,
            ImportTemplate template
    ) {

        List<String> aliases =
                template
                        .getTableDefinition()
                        .getHeaderAliases();

        for (Row row : sheet) {

            Set<String> values =
                    new HashSet<>();

            for (Cell cell : row) {

                values.add(
                        normalize(
                                getCellValue(cell)
                        )
                );
            }

            int matches = 0;

            for (String alias : aliases) {

                if (values.contains(
                        normalize(alias)
                )) {
                    matches++;
                }
            }

            /*
             * Require at least two
             * matching headers.
             */
            if (matches >= 2) {
                return row;
            }
        }

        return null;
    }


    private Map<String, Integer> findColumns(
            Row headerRow,
            ImportTemplate template
    ) {

        Map<String, Integer> result =
                new HashMap<>();

        for (
                ImportTemplate.TemplateField field :
                template.getFields()
        ) {

            if (!field.isEnabled()) {
                continue;
            }

            for (Cell cell : headerRow) {

                String actual =
                        normalize(
                                getCellValue(cell)
                        );

                String expected =
                        normalize(
                                field.getSource()
                        );

                if (actual.equals(expected)) {

                    result.put(
                            field.getField(),
                            cell.getColumnIndex()
                    );

                    break;
                }
            }
        }

        return result;
    }


    private String getMappedValue(
            Row row,
            Map<String, Integer> columns,
            String field
    ) {

        Integer index =
                columns.get(field);

        if (index == null) {
            return null;
        }

        return getCellValue(
                row,
                index
        );
    }


    private String getCellValue(
            Row row,
            int index
    ) {

        Cell cell =
                row.getCell(
                        index,
                        Row.MissingCellPolicy
                                .RETURN_BLANK_AS_NULL
                );

        return getCellValue(cell);
    }


    private String getCellValue(Cell cell) {

        if (cell == null) {
            return "";
        }

        DataFormatter formatter =
                new DataFormatter();

        return formatter
                .formatCellValue(cell)
                .trim();
    }


    private boolean isEndMarker(
            String value,
            ImportTemplate template
    ) {

        if (value == null) {
            return false;
        }

        for (
                String marker :
                template
                        .getTableDefinition()
                        .getEndMarkers()
        ) {

            if (value
                    .toLowerCase()
                    .contains(
                            marker.toLowerCase()
                    )) {

                return true;
            }
        }

        return false;
    }


    private String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value
                    .trim()
                    .replaceAll("\\s+", " ")
                    .toLowerCase();
    }


    private BigDecimal parseAmount(
            String value
    ) {

        if (
                value == null ||
                value.isBlank()
        ) {
            return null;
        }

        try {

            String cleaned =
                    value
                            .replace(",", "")
                            .replace("₹", "")
                            .replace("INR", "")
                            .trim();

            if (cleaned.isBlank()) {
                return null;
            }

            return new BigDecimal(
                    cleaned
            );

        } catch (Exception e) {

            return null;
        }
    }


    private LocalDate parseDate(
            String value
    ) {

        if (
                value == null ||
                value.isBlank()
        ) {
            return null;
        }

        List<DateTimeFormatter> formats =
                List.of(
                        DateTimeFormatter.ofPattern(
                                "dd/MM/yy"
                        ),
                        DateTimeFormatter.ofPattern(
                                "dd/MM/yyyy"
                        ),
                        DateTimeFormatter.ofPattern(
                                "dd-MM-yyyy"
                        ),
                        DateTimeFormatter.ofPattern(
                                "dd-MM-yy"
                        )
                );

        for (
                DateTimeFormatter formatter :
                formats
        ) {

            try {

                return LocalDate.parse(
                        value.trim(),
                        formatter
                );

            } catch (Exception ignored) {
            }
        }

        return null;
    }
}