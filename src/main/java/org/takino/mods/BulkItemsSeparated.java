package org.takino.mods;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import javassist.*;
import javassist.bytecode.Descriptor;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
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

        } catch (NotFoundException | CannotCompileException e) {
            logException("Error hooking moveBulkItemAsAction", e);
        }

    }

    @Override
    public void init() {
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
                            Item target = (Item) args[1];
                            Creature mover = (Creature) args[0];
                            Item toInsert = (Item) proxy;
                            if (target.getBless() != null) {
                                return method.invoke(proxy, args);
                            } else {
                                return BulkItemsHooks.addBulkItem(target, mover, toInsert);
                            }
                        }
                    };
                }
            });
        } catch (Exception e) {
            logException("Error in init", e);
        }
    }
}

