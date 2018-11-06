package org.takino.mods;

import com.wurmonline.server.DbConnector;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Methods;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.utils.DbUtilities;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.FINISH_ACTION;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.NO_SERVER_PROPAGATION;

public class BlessToggleAction implements ModAction, ActionPerformer, BehaviourProvider {
    private ActionEntry actionEntry;

    public BlessToggleAction() {
        actionEntry = ActionEntry.createEntry((short) ModActions.getNextActionId(), "Toggle sorting", "toggling", new int[]{
                48 /* ACTION_TYPE_ENEMY_ALWAYS */,
                37 /* ACTION_TYPE_NEVER_USE_ACTIVE_ITEM */
        });
        ModActions.registerAction(actionEntry);
    }

    @Override
    public short getActionId() {
        return actionEntry.getNumber();
    }

    @Override
    public BehaviourProvider getBehaviourProvider() {
        return this;
    }

    @Override
    public ActionPerformer getActionPerformer() {
        return this;
    }

    public boolean canUse(Creature performer, Item target) {
        return performer.isPlayer() && target != null
                && target.isBulkContainer()
                && !target.isCrate();
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item source, Item target) {
        return getBehavioursFor(performer, target);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item target) {
        if (canUse(performer, target))
            return Collections.singletonList(actionEntry);
        else
            return null;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short num, float counter) {
        return action(action, performer, target, num, counter);
    }

    public static void unBless(Item target) {
        Connection dbcon = null;
        PreparedStatement ps = null;
        try {
            dbcon = DbConnector.getItemDbCon();
            ps = dbcon.prepareStatement(target.getDbStrings().setBless());
            ps.setByte(1, (byte) 0);
            ps.setLong(2, target.getWurmId());
            ps.executeUpdate();
            ReflectionUtil.setPrivateField(target, ReflectionUtil.getField(Item.class, "bless"), (byte) 0);
        } catch (SQLException | IllegalAccessException | NoSuchFieldException e) {
            BulkItemsSeparated.logException("Failed to remove bless from " + target.getWurmId(), e);
        } finally {
            DbUtilities.closeDatabaseObjects(ps, null);
            DbConnector.returnConnection(dbcon);
        }
    }

    @Override
    public boolean action(Action action, Creature performer, Item target, short num, float counter) {
        if (!canUse(performer, target)) {
            performer.getCommunicator().sendAlertServerMessage("You can't do that now.");
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }
        if (!Methods.isActionAllowed(performer, (short) 245, target)) {
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }
        if (target.getBless() != null) {
            unBless(target);
            performer.getCommunicator().sendNormalServerMessage(String.format("The %s will now sort by QL.", target.getName()));
        } else {
            target.bless(performer.getDeity() != null ? performer.getDeity().getNumber() : 1);
            performer.getCommunicator().sendNormalServerMessage(String.format("The %s will no longer sort by QL.", target.getName()));
        }

        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }
}
