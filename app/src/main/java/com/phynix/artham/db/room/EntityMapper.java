package com.phynix.artham.db.room;

import com.phynix.artham.db.room.entity.CashbookEntity;
import com.phynix.artham.db.room.entity.CategoryEntity;
import com.phynix.artham.db.room.entity.TransactionEntity;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.models.TransactionModel;

import java.util.ArrayList;
import java.util.List;

/**
 * EntityMapper — Converts between Room entities and UI models.
 *
 * This keeps the Room layer completely separate from the UI layer.
 * Activities and ViewModels continue using the existing model classes,
 * while the database layer uses Room entities internally.
 */
public class EntityMapper {

    // ═══════════════════════════════════════
    //  TRANSACTION MAPPING
    // ═══════════════════════════════════════

    public static TransactionModel toModel(TransactionEntity entity) {
        if (entity == null) return null;
        TransactionModel model = new TransactionModel();
        model.setTransactionId(entity.id);
        model.setAmount(entity.amount);
        model.setType(entity.type);
        model.setTransactionCategory(entity.transactionCategory);
        model.setPartyName(entity.partyName);
        model.setPaymentMode(entity.paymentMode);
        model.setRemark(entity.remark);
        model.setTimestamp(entity.timestamp);
        model.setTags(entity.tags);
        model.setLocation(entity.location);
        model.setAttachmentUri(entity.attachmentUri);
        model.setAutoFrequency(entity.autoFrequency);
        model.setTaxRate(entity.taxRate);
        model.setTaxAmount(entity.taxAmount);
        model.setTaxInclusive(entity.taxInclusive);
        return model;
    }

    public static TransactionEntity toEntity(TransactionModel model, String cashbookId) {
        if (model == null) return null;
        TransactionEntity entity = new TransactionEntity();
        entity.id = model.getTransactionId();
        entity.cashbookId = cashbookId;
        entity.amount = model.getAmount();
        entity.type = model.getType();
        entity.transactionCategory = model.getTransactionCategory();
        entity.partyName = model.getPartyName();
        entity.paymentMode = model.getPaymentMode();
        entity.remark = model.getRemark();
        entity.timestamp = model.getTimestamp();
        entity.tags = model.getTags();
        entity.location = model.getLocation();
        entity.attachmentUri = model.getAttachmentUri();
        entity.autoFrequency = model.getAutoFrequency();
        entity.taxRate = model.getTaxRate();
        entity.taxAmount = model.getTaxAmount();
        entity.taxInclusive = model.isTaxInclusive();
        entity.lastModified = System.currentTimeMillis();
        entity.syncStatus = "PENDING";
        entity.isDeleted = false;
        return entity;
    }

    public static List<TransactionModel> toModelList(List<TransactionEntity> entities) {
        List<TransactionModel> models = new ArrayList<>();
        if (entities != null) {
            for (TransactionEntity entity : entities) {
                models.add(toModel(entity));
            }
        }
        return models;
    }

    // ═══════════════════════════════════════
    //  CASHBOOK MAPPING
    // ═══════════════════════════════════════

    public static CashbookModel toModel(CashbookEntity entity) {
        if (entity == null) return null;
        CashbookModel model = new CashbookModel(entity.id, entity.name);
        model.setDescription(entity.description);
        model.setCategory(entity.category);
        model.setThemeColor(entity.themeColor);
        model.setThemeIcon(entity.themeIcon);
        model.setCurrency(entity.currency);
        model.setTotalBalance(entity.totalBalance);
        model.setTransactionCount(entity.transactionCount);
        model.setCreatedDate(entity.createdDate);
        model.setLastModified(entity.lastModified);
        model.setLastOpenedAt(entity.lastOpenedAt);
        model.setActive(entity.isActive);
        model.setCurrent(entity.isCurrent);
        model.setFavorite(entity.isFavorite);
        model.setUserId(entity.userId);
        return model;
    }

    public static CashbookEntity toEntity(CashbookModel model) {
        if (model == null) return null;
        CashbookEntity entity = new CashbookEntity();
        entity.id = model.getCashbookId();
        entity.name = model.getName();
        entity.description = model.getDescription();
        entity.category = model.getCategory();
        entity.themeColor = model.getThemeColor();
        entity.themeIcon = model.getThemeIcon();
        entity.currency = model.getCurrency();
        entity.totalBalance = model.getTotalBalance();
        entity.transactionCount = model.getTransactionCount();
        entity.createdDate = model.getCreatedDate();
        entity.lastModified = model.getLastModified();
        entity.lastOpenedAt = model.getLastOpenedAt();
        entity.isActive = model.isActive();
        entity.isCurrent = model.isCurrent();
        entity.isFavorite = model.isFavorite();
        entity.userId = model.getUserId();
        entity.syncStatus = "PENDING";
        entity.isDeleted = false;
        return entity;
    }

    public static List<CashbookModel> toCashbookModelList(List<CashbookEntity> entities) {
        List<CashbookModel> models = new ArrayList<>();
        if (entities != null) {
            for (CashbookEntity entity : entities) {
                models.add(toModel(entity));
            }
        }
        return models;
    }

    // ═══════════════════════════════════════
    //  CATEGORY MAPPING
    // ═══════════════════════════════════════

    public static CategoryModel toModel(CategoryEntity entity) {
        if (entity == null) return null;
        CategoryModel model = new CategoryModel(entity.name, entity.type, entity.colorHex, entity.iconResId, entity.isCustom);
        model.setId(entity.id);
        return model;
    }

    public static CategoryEntity toEntity(CategoryModel model, String cashbookId) {
        if (model == null) return null;
        CategoryEntity entity = new CategoryEntity();
        entity.id = model.getId();
        entity.cashbookId = cashbookId;
        entity.name = model.getName();
        entity.type = model.getType();
        entity.colorHex = model.getColorHex();
        entity.iconResId = model.getIconResId();
        entity.isCustom = model.isCustom();
        entity.lastModified = System.currentTimeMillis();
        entity.syncStatus = "PENDING";
        entity.isDeleted = false;
        return entity;
    }

    public static List<CategoryModel> toCategoryModelList(List<CategoryEntity> entities) {
        List<CategoryModel> models = new ArrayList<>();
        if (entities != null) {
            for (CategoryEntity entity : entities) {
                models.add(toModel(entity));
            }
        }
        return models;
    }
}
