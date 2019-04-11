package org.takino.mods;

import com.wurmonline.server.FailedException;
import com.wurmonline.server.Items;
import com.wurmonline.server.behaviours.MethodsItems;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.zones.Zones;

import java.util.Iterator;

public class BulkItemsHooks {
    public static boolean addBulkItem(Item target, Creature mover, Item toInsert) {
        ItemTemplate template = toInsert.getTemplate();
        boolean full = target.isFull();
        byte auxToCheck = 0;
        if (toInsert.usesFoodState()) {
            if (!toInsert.isFresh() && !toInsert.isLive()) {
                auxToCheck = toInsert.getAuxData();
            } else {
                auxToCheck = (byte) (toInsert.getAuxData() & 127);
            }
        }

        Item toaddTo = getTargetToAdd(target,
                toInsert.getTemplateId(),
                toInsert.getMaterial(),
                toInsert.getCurrentQualityLevel(),
                auxToCheck);

        float fe;
        if (toaddTo != null) {
            if (MethodsItems.checkIfStealing(toaddTo, mover, null)) {
                int fe1 = (int) toaddTo.getPosX() >> 2;
                int percent = (int) toaddTo.getPosY() >> 2;
                Village percentAdded = Zones.getVillage(fe1, percent, mover.isOnSurface());
                if (mover.isLegal() && percentAdded != null) {
                    mover.getCommunicator().sendNormalServerMessage("That would be illegal here. You can check the settlement token for the local laws.");
                    return false;
                }

                if (mover.getDeity() != null && !mover.getDeity().isLibila() && mover.faithful) {
                    mover.getCommunicator().sendNormalServerMessage("Your deity would never allow stealing.");
                    return false;
                }
            }

            fe = toaddTo.getBulkNumsFloat(false);
            float percent1 = 1.0F;
            if (!toInsert.isFish() || toInsert.getTemplateId() == 369) {
                percent1 = (float) toInsert.getWeightGrams() / (float) template.getWeightGrams();
            }
            float percentAdded1 = percent1 / (fe + percent1);
            float qlDiff = toaddTo.getQualityLevel() - toInsert.getCurrentQualityLevel();
            float qlChange = percentAdded1 * qlDiff;
            float newQl;
            if (qlDiff > 0.0F) {
                newQl = toaddTo.getQualityLevel() - qlChange * 1.1F;
                toaddTo.setQualityLevel(Math.max(1.0F, newQl));
            } else if (qlDiff < 0.0F) {
                newQl = toaddTo.getQualityLevel() - qlChange * 0.9F;
                toaddTo.setQualityLevel(Math.max(1.0F, newQl));
            }

            toaddTo.setWeight(toaddTo.getWeightGrams() + (int) (percent1 * (float) template.getVolume()), true);
            Items.destroyItem(toInsert.getWurmId());
            mover.achievement(167, 1);
            if (full != target.isFull()) {
                target.updateModelNameOnGroundItem();
            }
            return true;
        } else {
            try {
                toaddTo = ItemFactory.createItem(669,
                        toInsert.getCurrentQualityLevel(), toInsert.getMaterial(), (byte) 0, null);
                toaddTo.setRealTemplate(toInsert.getTemplateId());
                if (toInsert.usesFoodState()) {
                    toaddTo.setAuxData(auxToCheck);
                    if (toInsert.getRealTemplateId() != -10) {
                        toaddTo.setData1(toInsert.getRealTemplateId());
                    }

                    toaddTo.setName(toInsert.getActualName());
                    ItemMealData imd = ItemMealData.getItemMealData(toInsert.getWurmId());
                    if (imd != null) {
                        ItemMealData.save(toaddTo.getWurmId(), imd.getRecipeId(), imd.getCalories(), imd.getCarbs(), imd.getFats(), imd.getProteins(), imd.getBonus(), imd.getStages(), imd.getIngredients());
                    }
                }
                fe = 1.0F;
                if (!toInsert.isFish() || toInsert.getTemplateId() == 369) {
                    fe = (float) toInsert.getWeightGrams() / (float) template.getWeightGrams();
                }
                if (!toaddTo.setWeight((int) (fe * (float) template.getVolume()), true)) {
                    target.insertItem(toaddTo, true);
                }

                Items.destroyItem(toInsert.getWurmId());
                mover.achievement(167, 1);
                if (full != target.isFull()) {
                    target.updateModelNameOnGroundItem();
                }
                toaddTo.setLastOwnerId(mover.getWurmId());
                return true;
            } catch (NoSuchTemplateException | FailedException e) {
                BulkItemsSeparated.logException("Error adding bulk item", e);
            }
            return false;
        }
    }

    public static boolean addBulkItemToCrate(Item target, Creature mover, Item toInsert) {
        ItemTemplate template = toInsert.getTemplate();
        int remainingSpaces = target.getRemainingCrateSpace();
        if (remainingSpaces <= 0) {
            return false;
        } else {
            byte auxToCheck = 0;
            if (toInsert.usesFoodState()) {
                if (!toInsert.isFresh() && !toInsert.isLive()) {
                    auxToCheck = toInsert.getAuxData();
                } else {
                    auxToCheck = (byte) (toInsert.getAuxData() & 127);
                }
            }

            Item toaddTo = getTargetToAdd(target,
                    toInsert.getTemplateId(),
                    toInsert.getMaterial(),
                    toInsert.getCurrentQualityLevel(),
                    auxToCheck);
            boolean destroyOriginal;
            int remove;
            float percent;
            if (toaddTo != null) {
                if (MethodsItems.checkIfStealing(toaddTo, mover, null)) {
                    int tilex = (int) toaddTo.getPosX() >> 2;
                    int tiley = (int) toaddTo.getPosY() >> 2;
                    Village vil = Zones.getVillage(tilex, tiley, mover.isOnSurface());
                    if (mover.isLegal() && vil != null) {
                        mover.getCommunicator().sendNormalServerMessage("That would be illegal here. You can check the settlement token for the local laws.");
                        return false;
                    }

                    if (mover.getDeity() != null && !mover.getDeity().isLibila() && mover.faithful) {
                        mover.getCommunicator().sendNormalServerMessage("Your deity would never allow stealing.");
                        return false;
                    }
                }

                percent = 1.0F;
                if (!toInsert.isFish() || toInsert.getTemplateId() == 369) {
                    percent = (float) toInsert.getWeightGrams() / (float) template.getWeightGrams();
                }

                destroyOriginal = true;
                if (percent > (float) remainingSpaces) {
                    percent = Math.min((float) remainingSpaces, percent);
                    destroyOriginal = false;
                }

                remove = template.getWeightGrams();
                Item tempItem = null;
                if (!destroyOriginal) {
                    try {
                        int newWeight = (int) ((float) remove * percent);
                        tempItem = ItemFactory.createItem(template.getTemplateId(), toInsert.getCurrentQualityLevel(), toInsert.getMaterial(), (byte) 0, null);
                        tempItem.setWeight(newWeight, true);
                        if (toInsert.usesFoodState()) {
                            tempItem.setAuxData(auxToCheck);
                        }

                        toInsert.setWeight(toInsert.getWeightGrams() - newWeight, true);
                    } catch (NoSuchTemplateException | FailedException e) {
                        BulkItemsSeparated.logException("Error adding bulk item", e);
                    }
                }

                if (tempItem == null) {
                    tempItem = toInsert;
                }

                float existingNumsBulk = toaddTo.getBulkNumsFloat(false);
                float percentAdded = percent / (existingNumsBulk + percent);
                float qlDiff = toaddTo.getQualityLevel() - toInsert.getCurrentQualityLevel();
                float qlChange = percentAdded * qlDiff;
                float newQl;
                if (qlDiff > 0.0F) {
                    newQl = toaddTo.getQualityLevel() - qlChange * 1.1F;
                    toaddTo.setQualityLevel(Math.max(1.0F, newQl));
                } else if (qlDiff < 0.0F) {
                    newQl = toaddTo.getQualityLevel() - qlChange * 0.9F;
                    toaddTo.setQualityLevel(Math.max(1.0F, newQl));
                }

                toaddTo.setWeight(toaddTo.getWeightGrams() + (int) (percent * (float) template.getVolume()), true);
                if (destroyOriginal) {
                    Items.destroyItem(toInsert.getWurmId());
                } else {
                    Items.destroyItem(tempItem.getWurmId());
                }

                mover.achievement(167, 1);
                target.updateModelNameOnGroundItem();
                return true;
            } else {
                try {
                    toaddTo = ItemFactory.createItem(669, toInsert.getCurrentQualityLevel(), toInsert.getMaterial(), (byte) 0, null);
                    toaddTo.setRealTemplate(toInsert.getTemplateId());
                    if (toInsert.usesFoodState()) {
                        toaddTo.setAuxData(auxToCheck);
                        if (toInsert.getRealTemplateId() != -10) {
                            toaddTo.setData1(toInsert.getRealTemplateId());
                        }

                        toaddTo.setName(toInsert.getActualName());
                        ItemMealData imd = ItemMealData.getItemMealData(toInsert.getWurmId());
                        if (imd != null) {
                            ItemMealData.save(toaddTo.getWurmId(), imd.getRecipeId(), imd.getCalories(), imd.getCarbs(), imd.getFats(), imd.getProteins(), imd.getBonus(), imd.getStages(), imd.getIngredients());
                        }
                    }

                    percent = 1.0F;
                    if (!toInsert.isFish() || toInsert.getTemplateId() == 369) {
                        percent = (float) toInsert.getWeightGrams() / (float) template.getWeightGrams();
                    }

                    destroyOriginal = true;
                    if (percent > (float) remainingSpaces) {
                        percent = Math.min((float) remainingSpaces, percent);
                        destroyOriginal = false;
                    }

                    if (!toaddTo.setWeight((int) (percent * (float) template.getVolume()), true)) {
                        target.insertItem(toaddTo, true);
                    }

                    if (destroyOriginal) {
                        Items.destroyItem(toInsert.getWurmId());
                    } else {
                        remove = (int) ((float) template.getWeightGrams() * percent);
                        toInsert.setWeight(toInsert.getWeightGrams() - remove, true);
                    }

                    mover.achievement(167, 1);
                    target.updateModelNameOnGroundItem();
                    toaddTo.setLastOwnerId(mover.getWurmId());
                    return true;
                } catch (NoSuchTemplateException | FailedException e) {
                    BulkItemsSeparated.logException("Error adding bulk item", e);
                }
                return false;
            }
        }
    }

    public static Item[] getItemsAsArrayFiltered(Item targetContainer, Item toMatch) {
        Item res = getTargetToAdd(targetContainer, toMatch.getRealTemplateId(), toMatch.getMaterial(), toMatch.getCurrentQualityLevel(), toMatch.getAuxData());
        if (res == null)
            return new Item[0];
        else
            return new Item[]{res};
    }

    private static Item getTargetToAdd(Item bulkInventory, long templateId, byte material, float quality, byte aux) {
        Iterator<Item> iterator = bulkInventory.getItems().iterator();
        Item item;
        while (iterator.hasNext()) {
            item = iterator.next();
            byte bulkMaterial = item.getMaterial();
            long bulkTemplateId = item.getRealTemplateId();
            if (bulkMaterial == material && bulkTemplateId == templateId && item.getAuxData() == aux) {
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

    public static String renameSorted(Item container) {
        return container.getName(true) + (container.getBless() == null ? " (sorted)" : " (unsorted)");
    }
}
