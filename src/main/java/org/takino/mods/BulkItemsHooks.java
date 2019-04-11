package org.takino.mods;

import com.wurmonline.server.items.Item;

public class BulkItemsHooks {
    public static Item[] getItemsAsArrayFiltered(Item targetContainer, Item toMatch) {
        Item res = getTargetToAdd(targetContainer, toMatch.getRealTemplateId(), toMatch.getMaterial(), toMatch.getCurrentQualityLevel(), toMatch.getAuxData(), toMatch.getData1() == -1 ? -10 : toMatch.getData1());
        if (res == null)
            return new Item[0];
        else
            return new Item[]{res};
    }

    public static Item getTargetToAdd(Item bulkInventory, int templateId, int material, float quality, byte aux, int realTemplateId) {
        for (Item item : bulkInventory.getItems()) {
            if (item.getRealTemplateId() == templateId && item.getMaterial() == material && item.getAuxData() == aux && ((realTemplateId == -10 && item.getData1() == -1) || item.getData1() == realTemplateId)) {
                float bulkQuality = item.getQualityLevel();
                if (quality > 99.995f) {
                    if (bulkQuality > 99.995f) return item;
                } else if (bulkQuality < 90 && quality < 90) {
                    double upperBoundary = Math.round((bulkQuality + 5) / 10.0) * 10.0;
                    double lowerBoundary = Math.round((bulkQuality - 5) / 10.0) * 10.0;
                    if (quality <= upperBoundary && quality >= lowerBoundary) {
                        return item;
                    }
                } else if (quality >= 90 && bulkQuality >= 90) {
                    double upperBoundary = Math.ceil(bulkQuality);
                    double lowerBoundary = Math.floor(bulkQuality);
                    if (quality <= upperBoundary && quality >= lowerBoundary) {
                        return item;
                    }
                }
            }
        }
        return null;
    }

    public static boolean isNotCrateBulk(Item item) {
        return item.isBulkContainer() && !item.isCrate();
    }

    public static boolean isSortable(Item item) {
        return (BulkItemsSeparated.allowCrateSorting && item.isCrate()) || (BulkItemsSeparated.allowCrateSorting && isNotCrateBulk(item));
    }

    public static boolean isSorted(Item item) {
        return isSortable(item) && item.getBless() == null;
    }
}
