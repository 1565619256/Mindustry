package mindustry.ai.types;

import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class LogicAI extends AIController{
    /** Minimum delay between item transfers. */
    public static final float transferDelay = 60f * 2f;
    /** Time after which the unit resets its controlled and reverts to a normal unit. */
    public static final float logicControlTimeout = 10f * 60f;

    public LUnitControl control = LUnitControl.stop;
    public float moveX, moveY, moveRad;
    public float itemTimer, payTimer, controlTimer = logicControlTimeout, targetTimer;
    @Nullable
    public Building controller;
    public BuildPlan plan = new BuildPlan();

    //special cache for instruction to store data
    public ObjectMap<Object, Object> execCache = new ObjectMap<>();

    //type of aiming to use
    public LUnitControl aimControl = LUnitControl.stop;

    //whether to use the boost (certain units only)
    public boolean boost;
    //main target set for shootP
    public Teamc mainTarget;
    //whether to shoot at all
    public boolean shoot;
    //target shoot positions for manual aiming
    public PosTeam posTarget = PosTeam.create();

    private ObjectSet<Object> radars = new ObjectSet<>();

    @Override
    protected void updateMovement(){
        if(itemTimer > 0) itemTimer -= Time.delta;
        if(payTimer > 0) payTimer -= Time.delta;

        if(targetTimer > 0f){
            targetTimer -= Time.delta;
        }else{
            radars.clear();
            targetTimer = 40f;
        }

        //timeout when not controlled by logic for a while
        if(controlTimer > 0 && controller != null && controller.isValid()){
            controlTimer -= Time.delta;
        }else{
            unit.resetController();
            return;
        }

        switch(control){
            case move -> {
                moveTo(Tmp.v1.set(moveX, moveY), 1f, 30f);
            }
            case approach -> {
                moveTo(Tmp.v1.set(moveX, moveY), moveRad - 8f, 8f);
            }
            case pathfind -> {
                Building core = unit.closestEnemyCore();

                if((core == null || !unit.within(core, unit.range() * 0.5f)) && command() == UnitCommand.attack){
                    boolean move = true;

                    if(state.rules.waves && unit.team == state.rules.defaultTeam){
                        Tile spawner = getClosestSpawner();
                        if(spawner != null && unit.within(spawner, state.rules.dropZoneRadius + 120f)) move = false;
                    }

                    if(move) pathfind(Pathfinder.fieldCore);
                }

                if(command() == UnitCommand.rally){
                    Teamc target = targetFlag(unit.x, unit.y, BlockFlag.rally, false);

                    if(target != null && !unit.within(target, 70f)){
                        pathfind(Pathfinder.fieldRally);
                    }
                }
            }
            case stop -> {
                if(unit instanceof Builderc build){
                    build.clearBuilding();
                }
            }
        }

        if(unit.type.canBoost && !unit.type.flying){
            unit.elevation = Mathf.approachDelta(unit.elevation, Mathf.num(boost || unit.onSolid()), 0.08f);
        }

        //look where moving if there's nothing to aim at
        if(!shoot){
            if(unit.moving()){
                unit.lookAt(unit.vel().angle());
            }
        }else if(unit.hasWeapons()){ //if there is, look at the object
            unit.lookAt(unit.mounts[0].aimX, unit.mounts[0].aimY);
        }
    }

    public boolean checkTargetTimer(Object radar){
        return radars.add(radar);
    }

    //always retarget
    @Override
    protected boolean retarget(){
        return true;
    }

    @Override
    protected boolean invalid(Teamc target){
        return false;
    }

    @Override
    protected boolean shouldShoot(){
        return shoot && !(unit.type.canBoost && boost);
    }

    //always aim for the main target
    @Override
    protected Teamc target(float x, float y, float range, boolean air, boolean ground){
        return switch(aimControl){
            case target -> posTarget;
            case targetp -> mainTarget;
            default -> null;
        };
    }
}
