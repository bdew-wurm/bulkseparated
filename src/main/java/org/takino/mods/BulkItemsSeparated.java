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

    @Override
    public void configure(Properties properties) {
        betterGrouping = Boolean.parseBoolean(properties.getProperty("betterGrouping", "true"));
        fasterTransfer = Boolean.parseBoolean(properties.getProperty("fasterTransfer", "true"));
        unlimitedRemove = Boolean.parseBoolean(properties.getProperty("unlimitedRemove", "true"));
    }

    @Override
    public void preInit() {
        try {
            ModActions.init();

            ClassPool classPool = HookManager.getInstance().getClassPool();

            CtClass ctItemBehaviour = classPool.getCtClass("com.wurmonline.server.behaviours.ItemBehaviour");
            ctItemBehaviour.getMethod("moveBulkItemAsAction", "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;F)Z")
                    .instrument(new ExprEditor() {
                        @Override
                        public void edit(MethodCall m) throws CannotCompileException {
                            if (m.getMethodName().equals("getItemsAsArray")) {
                                m.replace("if (($0.getBless()!=null) || $0.isCrate()) $_ = $proceed($$); else $_ = org.takino.mods.BulkItemsHooks.getItemsAsArrayFiltered($0, source);");
                            }
                        }
                    });

            CtClass ctItem = classPool.getCtClass("com.wurmonline.server.items.Item");
            ctItem.getMethod("AddBulkItem", "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)Z")
                    .insertBefore("if ($2.getBless()==null) return org.takino.mods.BulkItemsHooks.addBulkItem($2, $1, this);");

            if (betterGrouping) {
                ctItem.getMethod("getName", "(Z)Ljava/lang/String;")
                        .instrument(new ExprEditor() {
                            @Override
                            public void edit(MethodCall m) throws CannotCompileException {
                                if (m.getMethodName().equals("getPlural")) m.replace("$_=$0.getName();");
                            }
                        });
            }

            if (fasterTransfer) {
                ctItemBehaviour.getMethod("moveBulkItemAsAction", "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;F)Z")
                        .instrument(new ExprEditor() {
                            @Override
                            public void edit(MethodCall m) throws CannotCompileException {
                                if (m.getMethodName().equals("justTickedSecond"))
                                    m.replace("$_ = true;");
                                else if (m.getMethodName().equals("setTimeLeft"))
                                    m.replace("$proceed($1/20);");
                            }
                        });
            }

            if (unlimitedRemove) {
                classPool.getCtClass("com.wurmonline.server.questions.RemoveItemQuestion")
                        .getMethod("answer", "(Ljava/util/Properties;)V")
                        .instrument(new ExprEditor() {
                            @Override
                            public void edit(MethodCall m) throws CannotCompileException {
                                if (m.getMethodName().equals("getNumItemsNotCoins"))
                                    m.replace("$_=0;");
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

