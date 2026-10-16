package dev.aegisac.companion;

import java.util.*;

/** Curated compiled-code family signatures; never a filename or loader blacklist. */
public final class CheatSignatures {
    public record Rule(String id,String display,List<String> required,List<String> additional,int minimum) {}
    public static final List<Rule> RULES=List.of(
        new Rule("METEOR_CODE_V1","Meteor Client",List.of("MeteorClient","systems/modules/Module","systems/modules/Modules"),List.of("systems/modules/combat/KillAura","systems/modules/movement/Flight","systems/modules/player/NoFall","systems/modules/render/Xray","systems/modules/world/Nuker","systems/modules/movement/Spider"),3),
        new Rule("WURST_CODE_V1","Wurst Client",List.of("WurstClient","hack/Hack","hack/HackList"),List.of("hacks/KillauraHack","hacks/FlightHack","hacks/XRayHack","hacks/NukerHack","hacks/JesusHack"),3),
        new Rule("LIQUIDBOUNCE_CODE_V1","LiquidBounce",List.of("LiquidBounce","features/module/ClientModule","features/module/ModuleManager"),List.of("features/module/modules/combat/killaura/ModuleKillAura","features/module/modules/movement/fly/ModuleFly","features/module/modules/world/scaffold/ModuleScaffold","features/module/modules/render/ModuleXRay","features/module/modules/render/ModuleFreeCam"),3)
    );
    public static String display(String id){return RULES.stream().filter(r->r.id().equals(id)).map(Rule::display).findFirst().orElse("Unrecognised report rule");}
    public static boolean known(String id){return RULES.stream().anyMatch(r->r.id().equals(id));}
    public static boolean candidate(String name) {
        for(Rule r:RULES)for(String suffix:all(r))if(name.endsWith("/"+suffix)||name.equals(suffix))return true;return false;
    }
    static List<String> all(Rule r){var all=new ArrayList<>(r.required());all.addAll(r.additional());return all;}
    public static List<String> match(Collection<String> actualClassNames) {
        List<String> hits=new ArrayList<>();
        for(Rule r:RULES) {
            Set<String> names=new HashSet<>(actualClassNames);String first=r.required().get(0);
            for(String name:names)if(name.endsWith("/"+first)||name.equals(first)) {
                String root=name.substring(0,name.length()-first.length());
                if(r.required().stream().allMatch(s->names.contains(root+s))&&r.additional().stream().filter(s->names.contains(root+s)).count()>=r.minimum()){hits.add(r.id());break;}
            }
        }
        return List.copyOf(hits);
    }
    private CheatSignatures(){}
}
