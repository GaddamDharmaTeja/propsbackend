package com.prospr.controller;

import com.prospr.dto.ImportTemplateAnalysisResponse;
import com.prospr.dto.SaveImportTemplateRequest;
import com.prospr.model.ImportTemplate;
import com.prospr.repository.ImportTemplateRepository;
import com.prospr.service.ImportTemplateService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/import-templates")
@CrossOrigin(
        origins = "${PROSPR_WEB_ORIGIN:http://localhost:3000}"
)
public class ImportTemplateController {

    private final ImportTemplateRepository repository;
        private final ImportTemplateService importTemplateService;

    public ImportTemplateController(
                        ImportTemplateRepository repository,
                        ImportTemplateService importTemplateService
    ) {
        this.repository = repository;
                this.importTemplateService = importTemplateService;
    }


    @GetMapping
    public List<ImportTemplate> getTemplates(
            @AuthenticationPrincipal String ownerId
    ) {

        return repository.findByOwnerIdAndActiveTrue(ownerId);
    }


    @GetMapping("/{id}")
    public ImportTemplate getTemplate(
            @AuthenticationPrincipal String ownerId,
            @PathVariable String id
    ) {

        return repository
                .findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Import template not found"
                        )
                );
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImportTemplate createTemplate(
            @AuthenticationPrincipal String ownerId,
            @RequestBody SaveImportTemplateRequest request
    ) {

        ImportTemplate template =
                new ImportTemplate();

        template.setOwnerId(ownerId);
        template.setName(request.name());
        template.setBankName(request.bankName());
        template.setFileType(request.fileType());

        template.setTableDefinition(
                request.tableDefinition()
        );

        template.setFields(
                request.fields()
        );

        template.setParsingRules(
                request.parsingRules()
        );

        template.setCreatedAt(
                LocalDateTime.now()
        );

        template.setUpdatedAt(
                LocalDateTime.now()
        );

        return repository.save(template);
    }


    @PutMapping("/{id}")
    public ImportTemplate updateTemplate(
            @AuthenticationPrincipal String ownerId,
            @PathVariable String id,
            @RequestBody SaveImportTemplateRequest request
    ) {

        ImportTemplate template =
                repository
                        .findByIdAndOwnerId(id, ownerId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Import template not found"
                                )
                        );

        template.setName(request.name());
        template.setBankName(request.bankName());
        template.setFileType(request.fileType());

        template.setTableDefinition(
                request.tableDefinition()
        );

        template.setFields(
                request.fields()
        );

        template.setParsingRules(
                request.parsingRules()
        );

        template.setUpdatedAt(
                LocalDateTime.now()
        );

        return repository.save(template);
    }


    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(
            @AuthenticationPrincipal String ownerId,
            @PathVariable String id
    ) {

        ImportTemplate template =
                repository
                        .findByIdAndOwnerId(id, ownerId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Import template not found"
                                )
                        );

        template.setActive(false);

        template.setUpdatedAt(
                LocalDateTime.now()
        );

        repository.save(template);
    }

    @PostMapping(
            value = "/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ImportTemplateAnalysisResponse analyze(
            @RequestPart("file") MultipartFile file
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please upload a statement file."
            );
        }

        return importTemplateService.analyze(file);
    }
}