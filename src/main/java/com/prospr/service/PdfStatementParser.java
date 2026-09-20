package com.prospr.service;

import com.prospr.dto.ParsedTransaction;
import com.prospr.model.ImportTemplate;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfStatementParser
        implements BankStatementParser {

    private static final Pattern DATE_PATTERN =
            Pattern.compile(
                    "^\\s*(\\d{2}/\\d{2}/\\d{2,4})\\b.*"
            );

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile(
                    "([0-9,]+\\.\\d{2})"
            );


    @Override
    public List<ParsedTransaction> parse(
            InputStream inputStream,
            String password,
            ImportTemplate template
    ) throws Exception {

        byte[] bytes =
                inputStream.readAllBytes();

        try (
                PDDocument document =
                        password == null ||
                        password.isBlank()
                                ? Loader.loadPDF(bytes)
                                : Loader.loadPDF(
                                        bytes,
                                        password
                                )
        ) {

            PDFTextStripper stripper =
                    new PDFTextStripper();

            stripper.setSortByPosition(true);

            String text =
                    stripper.getText(document);

            return parseText(
                    text,
                    template
            );
        }
    }


    private List<ParsedTransaction> parseText(
            String text,
            ImportTemplate template
    ) {

        List<ParsedTransaction> result =
                new ArrayList<>();

        boolean tableStarted = false;

        String currentDate = null;

        String currentDescription = null;

        String currentReference = null;

        BigDecimal currentDebit = null;

        BigDecimal currentCredit = null;

        BigDecimal currentBalance = null;


        String[] lines =
                text.split("\\R");


        for (String rawLine : lines) {

            String line =
                    rawLine.trim();

            if (line.isBlank()) {
                continue;
            }


            /*
             * 1. Find transaction table.
             */
            if (!tableStarted) {

                if (isTableHeader(
                        line,
                        template
                )) {

                    tableStarted = true;
                }

                continue;
            }


            /*
             * 2. Stop when configured
             *    footer/end marker appears.
             */
            if (isEndMarker(
                    line,
                    template
            )) {

                break;
            }


            /*
             * 3. New transaction.
             */
            Matcher dateMatcher =
                    DATE_PATTERN.matcher(line);

            if (dateMatcher.find()) {

                /*
                 * Save previous transaction.
                 */
                if (currentDate != null) {

                    result.add(
                            buildTransaction(
                                    currentDate,
                                    currentDescription,
                                    currentReference,
                                    currentDebit,
                                    currentCredit,
                                    currentBalance
                            )
                    );
                }


                currentDate =
                        dateMatcher.group(1);

                currentDescription =
                        line.substring(
                                dateMatcher.end()
                        ).trim();

                currentReference = null;

                currentDebit = null;

                currentCredit = null;

                currentBalance = null;


                /*
                 * Extract amounts only
                 * from this transaction line.
                 */
                List<BigDecimal> amounts =
                        extractAmounts(line);

                /*
                 * For HDFC-like statements:
                 *
                 * amount(s) are near the
                 * transaction columns.
                 *
                 * We do NOT search the
                 * entire PDF.
                 */
                if (!amounts.isEmpty()) {

                    assignAmounts(
                            amounts,
                            template
                    );
                }

                continue;
            }


            /*
             * 4. Continuation of narration.
             */
            if (currentDate != null &&
                    template
                            .getParsingRules()
                            .isMultilineDescription()) {

                currentDescription =
                        currentDescription +
                        " " +
                        line;
            }
        }


        /*
         * Save final transaction.
         */
        if (currentDate != null) {

            result.add(
                    buildTransaction(
                            currentDate,
                            currentDescription,
                            currentReference,
                            currentDebit,
                            currentCredit,
                            currentBalance
                    )
            );
        }

        return result;
    }


    private boolean isTableHeader(
            String line,
            ImportTemplate template
    ) {

        int matches = 0;

        for (
                String header :
                template
                        .getTableDefinition()
                        .getHeaderAliases()
        ) {

            if (
                    line.toLowerCase()
                            .contains(
                                    header.toLowerCase()
                            )
            ) {

                matches++;
            }
        }

        return matches >= 2;
    }


    private boolean isEndMarker(
            String line,
            ImportTemplate template
    ) {

        for (
                String marker :
                template
                        .getTableDefinition()
                        .getEndMarkers()
        ) {

            if (
                    line.toLowerCase()
                            .contains(
                                    marker.toLowerCase()
                            )
            ) {

                return true;
            }
        }

        return false;
    }


    private List<BigDecimal> extractAmounts(
            String line
    ) {

        List<BigDecimal> result =
                new ArrayList<>();

        Matcher matcher =
                AMOUNT_PATTERN.matcher(line);

        while (matcher.find()) {

            try {

                result.add(
                        new BigDecimal(
                                matcher
                                        .group(1)
                                        .replace(
                                                ",",
                                                ""
                                        )
                        )
                );

            } catch (Exception ignored) {
            }
        }

        return result;
    }


    private void assignAmounts(
            List<BigDecimal> amounts,
            ImportTemplate template
    ) {

        /*
         * This is intentionally isolated.
         *
         * Once your PDF template stores
         * physical xStart/xEnd positions,
         * replace this with coordinate-based
         * assignment.
         */
    }


    private ParsedTransaction buildTransaction(
            String date,
            String description,
            String reference,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal balance
    ) {

        return new ParsedTransaction(
                parseDate(date),
                description,
                reference,
                null,
                debit,
                credit,
                balance
        );
    }


    private LocalDate parseDate(
            String value
    ) {

        List<DateTimeFormatter> formats =
                List.of(
                        DateTimeFormatter.ofPattern(
                                "dd/MM/yy"
                        ),
                        DateTimeFormatter.ofPattern(
                                "dd/MM/yyyy"
                        )
                );

        for (
                DateTimeFormatter formatter :
                formats
        ) {

            try {

                return LocalDate.parse(
                        value,
                        formatter
                );

            } catch (Exception ignored) {
            }
        }

        return null;
    }
}