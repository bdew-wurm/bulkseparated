package org.takino.mods;

import com.wurmonline.server.FailedException;
import com.wurmonline.server.Items;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.MethodsItems;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.NoSuchTemplateException;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.zones.Zones;
import javassist.CtClass;
import javassist.CtPrimitiveType;
import javassist.bytecode.Descriptor;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * BulkItemsSeparated is a mod that separates bulk stored items by QL. Only for BSBs.
 */
public class BulkItemsSeparated implements WurmServerMod, Configurable {

    private static Logger logger = Logger.getLogger("bulkItemsSeparated");
    boolean debug = false;

    @Override
    public void configure(Properties properties) {
        try {
            CtClass returnType = CtPrimitiveType.booleanType;
            CtClass[] inputTypes = {
                    HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature"),
                    HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item")
            };

            HookManager.getInstance().registerHook("com.wurmonline.server.items.Item", "AddBulkItem", Descriptor.ofMethod(returnType, inputTypes), new InvocationHandlerFactory() {
                @Override
                public InvocationHandler createInvocationHandler() {
                    return new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                           // logWarn("Executing");
                            Item target = (Item) args[1];
                            Creature mover = (Creature) args[0];
                            Item toInsert = (Item) proxy;
                            if (target.getBless() != null) {
                                return method.invoke(proxy,args);
                            }
                            ItemTemplate template = toInsert.getTemplate();

                            boolean full = target.isFull();
                            Item toaddTo = getTargetToAdd(target,
                                    toInsert.getTemplateId(),
                                    toInsert.getMaterial(),
                                    toInsert.getQualityLevel());

                            float fe;
                            if (toaddTo != null) {
                                if (MethodsItems.checkIfStealing(toaddTo, mover, (Action) null)) {
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
                                float percent1 = (float) toInsert.getWeightGrams() / (float) template.getWeightGrams();
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
                                            toInsert.getCurrentQualityLevel(), toInsert.getMaterial(), (byte) 0, (String) null);
                                    toaddTo.setRealTemplate(toInsert.getTemplateId());
                                    fe = (float) toInsert.getWeightGrams() / (float) template.getWeightGrams();
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
                                } catch (NoSuchTemplateException | FailedException var11) {
                                   logWarn(var11.getMessage() + var11);
                                }

                                return false;
                            }
                        }

                    };
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    private void logWarn(String message) {
        if (debug) {
            logger.info(message);
        }
    }

    private Item getTargetToAdd(Item bulkInventory, long templateId, byte material, float quality) {
        Iterator<Item> iterator = bulkInventory.getItems().iterator();
        Item item;
        while (iterator.hasNext()) {
            item = iterator.next();
            byte bulkMaterial = item.getMaterial();
            long bulkTemplateId = item.getRealTemplateId();
            if (bulkMaterial == material && bulkTemplateId == templateId) {
                float bulkQuality = item.getQualityLevel();
                if (bulkQuality < 90 && quality < 90) {
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

}

