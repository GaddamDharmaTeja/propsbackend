package com.prospr.controller;

import com.prospr.model.HouseholdCategory;
import com.prospr.repository.HouseholdCategoryRepository;
import com.prospr.service.CategoryCatalog;
import com.prospr.service.HouseholdAccess;
import com.prospr.service.HouseholdCategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "${PROSPR_WEB_ORIGIN:http://localhost:3000}")
public class CategoryController {

    private static final Set<String> CLASSIFICATIONS = Set.of("NECESSARY", "DISCRETIONARY", "MISCELLANEOUS", "REST");

    private final HouseholdCategoryRepository categories;
    private final HouseholdCategoryService service;
    private final HouseholdAccess access;

    public CategoryController(HouseholdCategoryRepository categories, HouseholdCategoryService service, HouseholdAccess access) {
        this.categories = categories;
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal String account) {
        String householdId = access.householdId(account);
        Map<String, HouseholdCategory> saved = new LinkedHashMap<>();
        for (HouseholdCategory category : categories.findByHouseholdId(householdId)) {
            if (category.name != null) {
                saved.put(category.name.toLowerCase(Locale.ROOT), category);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (CategoryCatalog.Category builtIn : CategoryCatalog.ALL) {
            HouseholdCategory override = saved.remove(builtIn.name().toLowerCase(Locale.ROOT));
            result.add(view(
                    override == null ? builtIn.code() : override.id,
                    builtIn.name(),
                    override == null ? builtIn.classification() : override.classification,
                    override == null ? "" : override.keyword,
                    false
            ));
        }
        for (HouseholdCategory category : saved.values()) {
            result.add(view(category.id, category.name, category.classification, category.keyword, true));
        }
        return result;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@AuthenticationPrincipal String account, @RequestBody Map<String, String> body) {
        String householdId = access.householdId(account);
        String name = cleanName(body.get("name"));
        if (builtIn(name) || categories.findByHouseholdIdAndNameIgnoreCase(householdId, name).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That category already exists. Change its classification instead.");
        }
        HouseholdCategory category = new HouseholdCategory();
        category.householdId = householdId;
        category.name = name;
        category.classification = classification(body.get("classification"));
        category.keyword = cleanKeyword(body.get("keyword"));
        category.custom = true;
        categories.save(category);
        service.reclassify(householdId, category.name, category.classification);
        return view(category.id, category.name, category.classification, category.keyword, true);
    }

    @PutMapping
    public Map<String, Object> update(@AuthenticationPrincipal String account, @RequestBody Map<String, String> body) {
        String householdId = access.householdId(account);
        String name = cleanName(body.get("name"));
        HouseholdCategory category = categories.findByHouseholdIdAndNameIgnoreCase(householdId, name).orElseGet(HouseholdCategory::new);
        if (category.id == null && !builtIn(name)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Create the category before changing it.");
        }
        category.householdId = householdId;
        category.name = builtInName(name);
        category.classification = classification(body.get("classification"));
        category.keyword = cleanKeyword(body.get("keyword"));
        category.custom = category.id != null && category.custom;
        if (category.id == null) {
            category.custom = false;
        }
        categories.save(category);
        service.reclassify(householdId, category.name, category.classification);
        return view(category.id, category.name, category.classification, category.keyword, category.custom);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal String account, @PathVariable String id) {
        String householdId = access.householdId(account);
        HouseholdCategory category = categories.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found."));
        if (!householdId.equals(category.householdId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to delete this category.");
        }
        if (!category.custom) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Built-in categories stay in the list. Change the classification instead.");
        }
        categories.delete(category);
    }

    private Map<String, Object> view(String id, String name, String classification, String keyword, boolean custom) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("classification", classification == null ? "MISCELLANEOUS" : classification);
        row.put("keyword", keyword == null ? "" : keyword);
        row.put("custom", custom);
        return row;
    }

    private String cleanName(String value) {
        String name = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category name must be 2 to 40 characters.");
        }
        return name;
    }

    private String cleanKeyword(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String classification(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!CLASSIFICATIONS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose Necessary, Lifestyle creep, Other, or Not counted.");
        }
        return normalized;
    }

    private boolean builtIn(String name) {
        return CategoryCatalog.ALL.stream().anyMatch(category -> category.name().equalsIgnoreCase(name));
    }

    private String builtInName(String name) {
        return CategoryCatalog.ALL.stream()
                .filter(category -> category.name().equalsIgnoreCase(name))
                .map(CategoryCatalog.Category::name)
                .findFirst()
                .orElse(name);
    }
}
