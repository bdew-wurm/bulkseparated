package org.takino.mods;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BulkItemsSeparated implements WurmServerMod, PreInitable, Initable, Configurable, ServerStartedListener {
    private static Logger logger = Logger.getLogger("BulkItemsSeparated");

    static void logException(String msg, Throwable e) {
        logger.log(Level.SEVERE, msg, e);
    }

    public static boolean betterGrouping;
    public static boolean fasterTransfer;
    public static boolean unlimitedRemove;
    public static boolean allowCrateSorting;
    public static boolean allowNonCrateSorting;
    public static boolean showSortingStatus;

    @Override
    public void configure(Properties properties) {
        allowCrateSorting = Boolean.parseBoolean(properties.getProperty("allowCrateSorting", "true"));
        allowNonCrateSorting = Boolean.parseBoolean(properties.getProperty("allowNonCrateSorting", "true"));
        betterGrouping = Boolean.parseBoolean(properties.getProperty("betterGrouping", "true"));
        fasterTransfer = Boolean.parseBoolean(properties.getProperty("fasterTransfer", "true"));
        unlimitedRemove = Boolean.parseBoolean(properties.getProperty("unlimitedRemove", "true"));
        showSortingStatus = Boolean.parseBoolean(properties.getProperty("showSortingStatus", "true"));
    }

    @Override
    public void preInit() {
        try {
            ModActions.init();

            ClassPool classPool = HookManager.getInstance().getClassPool();

            CtClass ctItem = classPool.getCtClass("com.wurmonline.server.items.Item");
            CtClass ctItemBehaviour = classPool.getCtClass("com.wurmonline.server.behaviours.ItemBehaviour");

            if (allowCrateSorting || allowNonCrateSorting) {
                ctItemBehaviour.getMethod("moveBulkItemAsAction", "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;F)Z")
                        .instrument(new ExprEditor() {
                            @Override
                            public void edit(MethodCall m) throws CannotCompileException {
                                if (m.getMethodName().equals("getItemsAsArray")) {
                                    m.replace("if (org.takino.mods.BulkItemsHooks.isSorted($0)) " +
                                            "$_ = org.takino.mods.BulkItemsHooks.getItemsAsArrayFiltered($0, source);" +
                                            "else $_ = $proceed($$);");
                                    logger.info(String.format("Patched getItemsAsArray in %s.%s at %d", m.where().getDeclaringClass().getName(), m.where().getName(), m.getLineNumber()));
                                }
                            }
                        });

                if (showSortingStatus) {
                    ctItem.getMethod("getName", "(Z)Ljava/lang/String;")
                            .insertAfter("if(org.takino.mods.BulkItemsHooks.isSortable(this)) " +
                                    "$_ = $_ + (this.getBless() == null ? \" (sorted)\" : \" (unsorted)\");");
                }

                ExprEditor bulkInsertEditor = new ExprEditor() {
                    @Override
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("getItemWithTemplateAndMaterial")) {
                            m.replace("if (org.takino.mods.BulkItemsHooks.isSorted(target))" +
                                    "$_=org.takino.mods.BulkItemsHooks.getTargetToAdd(target,$1,$2,getCurrentQualityLevel(),$3,$4);" +
                                    "else $_=$proceed($$);");
                            logger.info(String.format("Patched getItemWithTemplateAndMaterial in %s.%s at %d", m.where().getDeclaringClass().getName(), m.where().getName(), m.getLineNumber()));
                        }
                    }
                };

                if (allowNonCrateSorting) {
                    ctItem.getMethod("AddBulkItem", "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)Z")
                            .instrument(bulkInsertEditor);
                }

                if (allowCrateSorting) {
                    ctItem.getMethod("AddBulkItemToCrate", "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)Z")
                            .instrument(bulkInsertEditor);
                }

            }

            if (betterGrouping) {
                ctItem.getMethod("getName", "(Z)Ljava/lang/String;")
                        .instrument(new ExprEditor() {
                            @Override
                            public void edit(MethodCall m) throws CannotCompileException {
                                if (m.getMethodName().equals("getPlural")) {
                                    m.replace("$_=$0.getName();");
                                    logger.info(String.format("Patched getPlural in %s.%s at %d", m.where().getDeclaringClass().getName(), m.where().getName(), m.getLineNumber()));
                                }
                            }
                        });
            }

            if (fasterTransfer) {
                ctItemBehaviour.getMethod("moveBulkItemAsAction", "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;F)Z")
                        .instrument(new ExprEditor() {
                            @Override
                            public void edit(MethodCall m) throws CannotCompileException {
                                if (m.getMethodName().equals("justTickedSecond")) {
                                    m.replace("$_ = true;");
                                    logger.info(String.format("Patched justTickedSecond in %s.%s at %d", m.where().getDeclaringClass().getName(), m.where().getName(), m.getLineNumber()));
                                } else if (m.getMethodName().equals("setTimeLeft")) {
                                    m.replace("$proceed($1/20);");
                                    logger.info(String.format("Patched setTimeLeft in %s.%s at %d", m.where().getDeclaringClass().getName(), m.where().getName(), m.getLineNumber()));
                                }
                            }
                        });
            }

            if (unlimitedRemove) {
                classPool.getCtClass("com.wurmonline.server.questions.RemoveItemQuestion")
                        .getMethod("answer", "(Ljava/util/Properties;)V")
                        .instrument(new ExprEditor() {
                            @Override
                            public void edit(MethodCall m) throws CannotCompileException {
                                if (m.getMethodName().equals("getNumItemsNotCoins")) {
                                    m.replace("$_=0;");
                                    logger.info(String.format("Patched getNumItemsNotCoins in %s.%s at %d", m.where().getDeclaringClass().getName(), m.where().getName(), m.getLineNumber()));
                                } else if (m.getMethodName().equals("getCarryCapacityFor")) {
                                    m.replace("if (targetInventory == null || targetInventory.getOwnerId() != -10L) $_=$proceed($$); else $_=100;");
                                    logger.info(String.format("Patched getCarryCapacityFor in %s.%s at %d", m.where().getDeclaringClass().getName(), m.where().getName(), m.getLineNumber()));
                                } else if (m.getMethodName().equals("canCarry")) {
                                    m.replace("if (targetInventory == null || targetInventory.getOwnerId() != -10L) $_=$proceed($$); else $_=true;");
                                    logger.info(String.format("Patched canCarry in %s.%s at %d", m.where().getDeclaringClass().getName(), m.where().getName(), m.getLineNumber()));
                                }
                            }
                        });
            }

        } catch (NotFoundException | CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init() {
    }

    @Override
    public void onServerStarted() {
        ModActions.registerAction(new BlessToggleAction());
    }
}

