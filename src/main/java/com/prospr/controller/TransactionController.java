package com.prospr.controller;

import com.prospr.model.TransactionEntry;
import com.prospr.repository.TransactionRepository;
import com.prospr.service.CategoryCatalog;
import com.prospr.service.HouseholdAccess;
import com.prospr.service.HouseholdCategoryService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "${PROSPR_WEB_ORIGIN:http://localhost:3000}")
public class TransactionController {

    private static final Set<String> OVERRIDES = Set.of("NECESSARY", "LIFESTYLE_CREEP", "NOT_SURE");

    private final TransactionRepository repo;
    private final HouseholdAccess access;
    private final MongoTemplate mongo;
    private final HouseholdCategoryService categories;

    public TransactionController(TransactionRepository repo, HouseholdAccess access, MongoTemplate mongo, HouseholdCategoryService categories) {
        this.repo = repo;
        this.access = access;
        this.mongo = mongo;
        this.categories = categories;
    }

    @GetMapping
    public List<TransactionEntry> list(@AuthenticationPrincipal String account, @RequestParam(defaultValue = "family") String view) {
        boolean mine = "mine".equalsIgnoreCase(view);
        return repo.findByHouseholdIdOrderByDateDesc(access.householdId(account)).stream()
                .filter(row -> !mine || uploadedOrUpdatedBy(row, account))
                .map(row -> maskReason(row, mine))
                .toList();
    }

    @PostMapping
    public TransactionEntry create(@AuthenticationPrincipal String account, @RequestBody TransactionEntry item) {
        item.id = UUID.randomUUID().toString();
        item.ownerId = account;
        item.householdId = access.householdId(account);
        item.source = item.source == null ? "manual" : item.source;
        normalizeAmounts(item);
        item.normalizedDescription = normalize(item.description);
        CategoryCatalog.apply(item);
        categories.overlay(item);
        return repo.save(item);
    }

    @PutMapping("/{id}")
    public TransactionEntry update(@AuthenticationPrincipal String account, @PathVariable String id, @RequestBody TransactionEntry item) {
        TransactionEntry old = owned(account, id);
        item.id = id;
        item.ownerId = old.ownerId;
        item.householdId = old.householdId;
        item.userClassification = old.userClassification;
        item.classificationSource = old.classificationSource;
        item.reasonHidden = old.reasonHidden;
        item.updatedBy = account;
        if ("Anonymous".equals(item.description)) {
            item.description = old.description;
            item.reference = old.reference;
        }
        normalizeAmounts(item);
        item.normalizedDescription = normalize(item.description);
        item.updatedAt = LocalDateTime.now();
        CategoryCatalog.apply(item);
        categories.overlay(item);
        return persist(item);
    }

    @PatchMapping("/{id}/classification")
    public TransactionEntry classify(@AuthenticationPrincipal String account, @PathVariable String id, @RequestBody Map<String, String> body) {
        TransactionEntry item = owned(account, id);
        String value = body.getOrDefault("userClassification", "").trim().toUpperCase(Locale.ROOT);
        if (!OVERRIDES.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose Necessary, Lifestyle creep, or Not sure.");
        }
        item.userClassification = value;
        item.classificationSource = "USER";
        item.updatedBy = account;
        item.updatedAt = LocalDateTime.now();
        return persist(item);
    }

    @PatchMapping("/{id}/privacy")
    public TransactionEntry privacy(@AuthenticationPrincipal String account, @PathVariable String id, @RequestBody Map<String, Object> body) {
        TransactionEntry item = owned(account, id);
        if (!uploadedOrUpdatedBy(item, account)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can hide a reason only on transactions you uploaded or updated.");
        }
        Object flag = body.get("reasonHidden");
        item.reasonHidden = Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag));
        item.updatedBy = account;
        item.updatedAt = LocalDateTime.now();
        return persist(item);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal String account, @PathVariable String id) {
        owned(account, id);
        mongo.remove(idQuery(id), TransactionEntry.class);
    }

    private TransactionEntry owned(String account, String id) {
        TransactionEntry item = mongo.findOne(idQuery(id), TransactionEntry.class);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found.");
        }
        if (!access.householdId(account).equals(item.householdId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to change this transaction.");
        }
        return item;
    }

    private static boolean uploadedOrUpdatedBy(TransactionEntry row, String account) {
        return account != null && (account.equals(row.ownerId) || account.equals(row.updatedBy));
    }

    private static TransactionEntry maskReason(TransactionEntry row, boolean reveal) {
        if (row.reasonHidden && !reveal) {
            row.description = "Anonymous";
            row.reference = null;
        }
        return row;
    }

    private Query idQuery(String id) {
        List<Criteria> ids = new ArrayList<>();
        ids.add(Criteria.where("_id").is(id));
        if (id != null && ObjectId.isValid(id)) {
            ids.add(Criteria.where("_id").is(new ObjectId(id)));
        }
        return new Query(new Criteria().orOperator(ids));
    }

    private TransactionEntry persist(TransactionEntry item) {
        Document document = new Document();
        mongo.getConverter().write(item, document);
        document.remove("_id");
        var result = mongo.updateFirst(idQuery(item.id), Update.fromDocument(document), TransactionEntry.class);
        if (result.getMatchedCount() == 0) {
            return repo.save(item);
        }
        return item;
    }

    static String normalize(String text) {
        return text == null ? "" : text.toLowerCase().replaceAll("[^a-z0-9]", "").trim();
    }

    static void normalizeAmounts(TransactionEntry item) {
        if (item.amount != null) item.amount = item.amount.abs();
        if (item.income) {
            item.creditAmount = item.amount;
            item.debitAmount = null;
        } else {
            item.debitAmount = item.amount;
            item.creditAmount = null;
        }
    }
}
