package org.takino.mods;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * BulkItemsSeparated is a mod that separates bulk stored items by QL. Only for BSBs.
 * Blessed BSBs act WurmOnline way.
 */
public class BulkItemsSeparated implements WurmServerMod, PreInitable, Initable {

    private static Logger logger = Logger.getLogger("bulkItemsSeparated");

    static void logException(String msg, Throwable e) {
        logger.log(Level.SEVERE, msg, e);
    }

    @Override
    public void preInit() {
        try {
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
                    .insertBefore("if ($1.getBless()==null) return org.takino.mods.BulkItemsHooks.addBulkItem($1, $0, this);");

        } catch (NotFoundException | CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init() {
    }
}

