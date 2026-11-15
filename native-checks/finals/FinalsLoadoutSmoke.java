import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.damage.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scoreboard.Scoreboard;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

/** Native registries/inventories/attributes with fake player transport; production lifecycle is invoked unchanged. */
public final class FinalsLoadoutSmoke {
    private final JavaPlugin smoke, main;
    private final ClassLoader loader;
    private final Map<UUID, FakePlayer> players = new LinkedHashMap<>();
    private final Server originalServer = Bukkit.getServer();
    private Object session, worlds;
    private Field serverField;
    private Server lookupProxy;
    private int assertions;

    private FinalsLoadoutSmoke(JavaPlugin smoke, JavaPlugin main) {
        this.smoke=smoke; this.main=main; this.loader=main.getClass().getClassLoader();
    }
    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FinalsLoadoutSmoke(smoke,main).check();
    }
    public static int configOnly(JavaPlugin smoke, JavaPlugin main) throws Exception {
        FinalsLoadoutSmoke fixture=new FinalsLoadoutSmoke(smoke,main);
        fixture.cashoutConfigChecks();
        return fixture.assertions;
    }
    private Class<?> type(String name)throws Exception {
        return nativeAccess(() -> Class.forName(name.contains(".")?name:"me.petr1furious.potatominigames.games.finals."+name,true,loader));
    }
    /** Paper remapping and native factories require the real CraftServer, even in a lookup fixture. */
    private <T> T nativeAccess(java.util.concurrent.Callable<T> operation)throws Exception {
        if(serverField==null)return operation.call();
        Server previous=Bukkit.getServer();
        serverField.set(null,originalServer);
        try{return operation.call();}finally{serverField.set(null,previous);}
    }
    private void preloadProductionClasses()throws Exception {
        java.nio.file.Path jar=java.nio.file.Path.of(main.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        try(java.util.jar.JarFile archive=new java.util.jar.JarFile(jar.toFile())){
            var entries=archive.entries();
            while(entries.hasMoreElements()){
                String name=entries.nextElement().getName();
                if(name.startsWith("me/petr1furious/potatominigames/")&&name.endsWith(".class"))
                    Class.forName(name.substring(0,name.length()-6).replace('/','.'),false,loader);
            }
        }
    }
    private Object withPlayerLookup(Object target,String name,Object... args)throws Exception {
        Server previous=Bukkit.getServer();serverField.set(null,lookupProxy);
        try{return call(target,name,args);}finally{serverField.set(null,previous);}
    }
    private static boolean accepts(Class<?> parameter,Object argument) {
        if(argument==null)return !parameter.isPrimitive();
        if(parameter.isInstance(argument))return true;
        return parameter==int.class&&argument instanceof Integer || parameter==long.class&&argument instanceof Long
                ||parameter==boolean.class&&argument instanceof Boolean||parameter==float.class&&argument instanceof Float
                ||parameter==double.class&&argument instanceof Double;
    }
    private static Object invoke(Method method,Object receiver,Object... args)throws Exception {
        method.setAccessible(true);
        try{return method.invoke(receiver,args);}catch(InvocationTargetException failure){
            if(failure.getCause() instanceof Exception e)throw e;
            if(failure.getCause() instanceof Error e)throw e;
            throw failure;
        }
    }
    private Object call(Object target,String name,Object... args)throws Exception {
        Class<?> cls=target instanceof Class<?> c?c:target.getClass();
        for(Class<?> current=cls;current!=null;current=current.getSuperclass())for(Method method:methods(current)){
            if(!method.getName().equals(name)||method.getParameterCount()!=args.length)continue;
            boolean match=true;for(int i=0;i<args.length;i++)if(!accepts(method.getParameterTypes()[i],args[i]))match=false;
            if(match)return invoke(method,target instanceof Class<?>?null:target,args);
        }
        throw new NoSuchMethodException(cls.getName()+"."+name);
    }
    private Method[] methods(Class<?> type)throws Exception{return nativeAccess(type::getDeclaredMethods);}
    private Object make(String name,Object... args)throws Exception {
        for(Constructor<?> constructor:type(name).getDeclaredConstructors()){
            if(constructor.getParameterCount()!=args.length)continue;
            boolean match=true;for(int i=0;i<args.length;i++)if(!accepts(constructor.getParameterTypes()[i],args[i]))match=false;
            if(match){constructor.setAccessible(true);return constructor.newInstance(args);}
        }
        throw new NoSuchMethodException(name);
    }
    private static Field field(Object object,String name)throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object get(Object object,String name)throws Exception{return field(object,name).get(object);}
    private static void set(Object object,String name,Object value)throws Exception{field(object,name).set(object,value);}
    @SuppressWarnings({"unchecked","rawtypes"}) private Object value(String type,String name)throws Exception{return Enum.valueOf((Class)type(type),name);}
    private void require(boolean value,String message){if(!value)throw new AssertionError(message);assertions++;}
    private void close(double actual,double expected,String label){require(Math.abs(actual-expected)<.00001,label+": expected "+expected+", actual "+actual);}

    @SuppressWarnings("unchecked") private int check()throws Exception {
        cashoutConfigChecks();
        World initial=Bukkit.getWorlds().getFirst();
        FakePlayer light=new FakePlayer("NativeLight",initial,28,.27f,.14f);
        FakePlayer medium=new FakePlayer("NativeMedium",initial,32,.31f,.16f);
        FakePlayer heavy=new FakePlayer("NativeHeavy",initial,36,.29f,.18f);
        FakePlayer opponentA=new FakePlayer("NativeOpponentA",initial,20,.2f,.1f);
        FakePlayer opponentB=new FakePlayer("NativeOpponentB",initial,20,.2f,.1f);
        FakePlayer opponentC=new FakePlayer("NativeOpponentC",initial,20,.2f,.1f);
        for(FakePlayer player:List.of(light,medium,opponentA,heavy,opponentB,opponentC))players.put(player.id,player);
        serverField=Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);
        preloadProductionClasses();
        Server proxy=(Server)Proxy.newProxyInstance(Server.class.getClassLoader(),new Class<?>[]{Server.class},(receiver,method,args)->{
            if(method.getName().equals("getPlayer")&&args[0] instanceof UUID id&&players.containsKey(id))return players.get(id).player;
            if(method.getName().equals("getOnlinePlayers"))return players.values().stream().map(p->p.player).toList();
            serverField.set(null,originalServer);
            try{return method.invoke(originalServer,args);}
            catch(InvocationTargetException error){throw error.getCause();}
            finally{serverField.set(null,receiver);}
        });
        lookupProxy=proxy;
        try {
            Object registry=make("me.petr1furious.potatominigames.core.player.ParticipantRegistry");
            for(FakePlayer player:players.values())call(registry,"add",player.player);
            Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
            Object active=((sun.misc.Unsafe)unsafeField.get(null)).allocateInstance(type("me.petr1furious.potatominigames.core.session.ActiveSession"));
            set(active,"phase",value("me.petr1furious.potatominigames.core.session.GamePhase","RUNNING"));
            set(active,"test",true);set(active,"options",Map.of());
            Object configs=make("me.petr1furious.potatominigames.core.config.ConfigRegistry",main);
            Object config=make("FinalsConfig",configs);
            Object countdown=get(config,"countdownSeconds");
            Object cashout=get(config,"cashoutSeconds");
            call(cashout,"reset");
            require(call(cashout,"get").equals(90),"Native Finals configuration defaults to a ninety-second cashout");
            call(countdown,"reset");
            require(call(countdown,"get").equals(18),"Native Finals configuration defaults to an eighteen-second loadout window");
            call(countdown,"set",5);
            Object stateService=make("me.petr1furious.potatominigames.core.player.PlayerStateService",main);
            Object sidebar=make("me.petr1furious.potatominigames.core.hud.SidebarService",(Supplier<Collection<Player>>)List::of);
            Object bossbars=make("me.petr1furious.potatominigames.core.hud.BossBarService",(Supplier<Collection<Player>>)()->players.values().stream().map(p->p.player).toList());
            Object arena=make("me.petr1furious.potatominigames.core.world.ArenaService");
            worlds=make("me.petr1furious.potatominigames.core.world.SessionWorlds",make("me.petr1furious.potatominigames.core.world.ThrowawayWorldService",main),null);
            Object messages=make("me.petr1furious.potatominigames.core.text.Messages",(Supplier<Audience>)Audience::empty,null);
            Object services=make("me.petr1furious.potatominigames.core.game.SessionServices",main,active,registry,stateService,null,sidebar,bossbars,arena,worlds,null,messages,null);
            Object loadouts=make("FinalsLoadouts",main,(Consumer<Player>)player->{
                if(session!=null)try{call(session,"loadoutChanged",player);}catch(Exception error){throw new RuntimeException(error);}
            });
            Map<UUID,Object> choices=(Map<UUID,Object>)get(loadouts,"choices");
            for(var entry:Map.of(light,"LIGHT",medium,"MEDIUM",heavy,"HEAVY").entrySet()){
                Object role=value("FinalsClass",entry.getValue());
                Object weapon=((List<?>)call(type("FinalsWeapon"),"byClass",role)).getFirst();
                choices.put(entry.getKey().id,make("FinalsLoadouts$Choice",role,weapon));
            }
            session=make("FinalsSession",services,config,null,loadouts);
            // World creation and mutation require the real CraftServer. As in the original
            // fixture, fake participant transports are spawned explicitly after real startup.
            call(session,"onStart");
            Object ticker=get(session,"ticker");
            require(ticker!=null,"Prepared startup installs the production match ticker");
            call(ticker,"cancel");
            require((boolean)call(ticker,"isCancelled"),"Manual countdown checks disable the scheduled ticker to avoid interference");
            require(((Map<?,?>)get(session,"roster")).size()==players.size(),"Real onStart builds the full participant roster");
            require((boolean)get(session,"revivalMode"),"The actual initial six-player roster enables three-v-three revival rules");
            require(get(session,"world") instanceof World,"Real onStart creates the native match world");
            // These packet/display helpers have independent native coverage. Do not create
            // world entities while the scoped lookup-only Server proxy drives manual ticks.
            for(String helper:List.of("highlights","tabList")){
                Object presentation=get(session,helper);
                if(presentation!=null)call(presentation,"clear");
                set(session,helper,null);
            }
            for(FakePlayer player:players.values()){
                call(session,"spawn",player.player);
                call(loadouts,"open",player.player);
            }
            Map<UUID,Integer> startingRoster=(Map<UUID,Integer>)get(session,"roster");
            Map<Integer,Location> startingPoints=new HashMap<>();
            for(FakePlayer player:players.values()) {
                int team=startingRoster.get(player.id);
                Location actual=player.player.getLocation();
                Location first=startingPoints.putIfAbsent(team,actual.clone());
                require(first==null||first.equals(actual),
                        "The actual initial spawn places every teammate at the same team starting point");
                require(((List<?>)call(get(session,"arena"),"spawnLocations",team)).contains(actual),
                        "The actual shared starting point belongs to the team's configured spawn candidates");
            }
            require(startingPoints.size()==2&&!startingPoints.get(0).equals(startingPoints.get(1)),
                    "The two teams retain separate initial spawn locations");
            require((long)get(session,"countdownEnd")==360L,"A legacy five-second setting still grants the complete eighteen-second loadout window");
            Map<UUID,Integer> killsBeforeCountdown=(Map<UUID,Integer>)call(get(session,"kills"),"snapshot");
            require(killsBeforeCountdown.isEmpty(),"A prepared match starts with no carried-over kills");
            Map<UUID,Object> frozen=(Map<UUID,Object>)get(session,"frozen");
            for(var entry:Map.of(light,15d,medium,25d,heavy,35d).entrySet()){
                FakePlayer player=entry.getKey();double expected=entry.getValue();
                close(player.attribute.getAttribute(Attribute.MAX_HEALTH).getBaseValue(),expected,"Native spawn class max health");
                close(player.attribute.getHealth(),expected,"Native spawn class current health");
                close(player.attribute.getAttribute(Attribute.SCALE).getBaseValue(),
                        expected==15?.8:expected==25?1:1.25,"Native spawn applies the class body size");
                require(player.storage.getItem(0)!=null&&player.storage.getItem(0).getItemMeta().hasDisplayName(),"Native spawn installs a named weapon");
                for(int slot=1;slot<=4;slot++)require(player.storage.getItem(slot)!=null,"Native spawn equips each class gadget");
                require(player.storage.getItem(8).getType()==Material.NETHER_STAR,"Native spawn equips loadout menu control");
                require(player.storage.getItem(7)==null||player.storage.getItem(7).getType().isAir(),
                        "Native spawn leaves the former pickaxe slot empty");
                close(player.walkSpeed,0,"Countdown freezes movement");
                require((boolean)call(type("FinalsLoadouts"),"viewing",player.player),"The native selection fixture displays the Finals loadout menu");
            }
            close((float)call(frozen.get(light.id),"walkSpeed"),.24,"Light class speed before freeze");
            close((float)call(frozen.get(medium.id),"walkSpeed"),.20,"Medium class speed before freeze");
            close((float)call(frozen.get(heavy.id),"walkSpeed"),.17,"Heavy class speed before freeze");

            require(light.top.getSize()==54&&light.top.getItem(11).getType()==Material.FEATHER,"Native loadout GUI paints class controls");
            InventoryClickEvent countdownClick=new InventoryClickEvent(light.view,InventoryType.SlotType.CONTAINER,15,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            call(loadouts,"onClick",countdownClick);
            require(countdownClick.isCancelled(),"Loadout GUI consumes inventory clicks");
            close(light.attribute.getAttribute(Attribute.MAX_HEALTH).getBaseValue(),35,"Countdown GUI class change applies immediately");
            close(light.attribute.getAttribute(Attribute.SCALE).getBaseValue(),1.25,"Countdown GUI applies Heavy body size immediately");
            close((float)call(frozen.get(light.id),"walkSpeed"),.17,"Countdown class change saves Heavy speed while frozen");
            require(light.player.getLocation().equals(startingPoints.get(startingRoster.get(light.id))),
                    "Applying a countdown loadout keeps the player at the shared team starting point");
            withPlayerLookup(session,"onParticipantQuit",heavy.player);
            restored(heavy);
            require(((Map<?,?>)get(session,"classes")).get(heavy.id).toString().equals("HEAVY"),
                    "Temporary disconnect restores outside stats while preserving the equipped class for Tab");
            withPlayerLookup(session,"onParticipantRejoin",heavy.player);
            require(heavy.menuOpens==2&&(boolean)call(type("FinalsLoadouts"),"viewing",heavy.player),
                    "Rejoining during the countdown automatically reopens the loadout menu");
            close(heavy.walkSpeed,0,"A countdown rejoin remains frozen for the rest of the selection window");
            require(heavy.player.getLocation().equals(startingPoints.get(startingRoster.get(heavy.id))),
                    "A countdown rejoin returns to the same shared team starting point");
            set(session,"tick",358L);
            withPlayerLookup(session,"tick");
            require((long)get(session,"tick")==359L&&get(session,"state")==null,
                    "At tick359 the full loadout countdown is still active");
            for(FakePlayer player:players.values()){
                close(player.walkSpeed,0,"Players remain frozen before the exact GO tick");
                require((boolean)call(type("FinalsLoadouts"),"viewing",player.player),
                        "The selection menu remains visible through tick359");
            }
            require(light.menuOpens==1&&medium.menuOpens==1&&heavy.menuOpens==2,
                    "Countdown refreshes and class changes never reopen the menu repeatedly");
            withPlayerLookup(session,"tick");
            require((long)get(session,"tick")==360L&&get(session,"state")!=null,
                    "Tick360 creates the actual match state at the end of eighteen seconds");
            require(call(get(session,"rules"),"cashoutTicks").equals(1_800L),
                    "Production GO uses a complete ninety-second cashout timer");
            require(frozen.isEmpty(),"The actual GO transition releases every countdown freeze");
            for(FakePlayer player:players.values())
                require(!(boolean)call(type("FinalsLoadouts"),"viewing",player.player),
                        "The actual GO transition closes the visible Finals loadout menus");
            require(call(get(session,"kills"),"snapshot").equals(killsBeforeCountdown),
                    "Countdown selection, reconnect and GO leave match kill totals unchanged");
            close(light.walkSpeed,.17,"Releasing countdown restores new class speed");
            close(medium.walkSpeed,.20,"Releasing countdown restores Medium speed");
            close(heavy.walkSpeed,.17,"Releasing countdown restores Heavy speed");
            if(Boolean.getBoolean("finals.smoke.downedLoadoutOnly")) {
                downedLoadoutChecks(loadouts,light);
                smoke.getLogger().info("FINALS_DOWNED_LOADOUT_SMOKE_PASS: "+assertions+" native loadout lifecycle assertions");
                return assertions;
            }
            if(Boolean.getBoolean("finals.smoke.musicOnly")) {
                cashoutMusicChecks(light,medium);
                smoke.getLogger().info("FINALS_CASHOUT_MUSIC_SMOKE_PASS: "+assertions+" config and lifecycle assertions");
                return assertions;
            }
            withPlayerLookup(session,"onParticipantQuit",heavy.player);
            withPlayerLookup(session,"onParticipantRejoin",heavy.player);
            require(((Map<?,?>)get(session,"classes")).get(heavy.id).toString().equals("HEAVY")
                            &&((Map<?,?>)get(session,"respawnAt")).containsKey(heavy.id),
                    "A mid-match reconnect keeps the actual Heavy class throughout respawn recovery");
            call(session,"spawn",heavy.player);
            call(loadouts,"open",light.player);
            InventoryClickEvent activeClick=new InventoryClickEvent(light.view,InventoryType.SlotType.CONTAINER,13,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            ItemStack previousWeapon=light.storage.getItem(0).clone();
            call(loadouts,"onClick",activeClick);
            close(light.attribute.getAttribute(Attribute.MAX_HEALTH).getBaseValue(),35,"Active GUI change waits for next spawn");
            close(light.attribute.getAttribute(Attribute.SCALE).getBaseValue(),1.25,"Queued Medium choice preserves the current Heavy size");
            close(light.walkSpeed,.17,"Active GUI change preserves current movement speed");
            require(light.storage.getItem(0).equals(previousWeapon),"Active GUI change preserves equipped weapon until respawn");
            require(call(call(loadouts,"choice",light.id),"playerClass").toString().equals("MEDIUM"),"Active GUI change records next-spawn preference");
            call(session,"spawn",light.player);
            close(light.attribute.getAttribute(Attribute.MAX_HEALTH).getBaseValue(),25,"Next spawn applies selected Medium health");
            close(light.attribute.getAttribute(Attribute.SCALE).getBaseValue(),1,"Next spawn applies selected Medium body size");
            close(light.attribute.getHealth(),25,"Next spawn fully heals selected class");
            close(light.walkSpeed,.20,"Next spawn applies selected Medium speed");
            equipmentChecks(loadouts,light,heavy);
            customLoadoutChecks(loadouts,light);
            teamWipeChecks(light,medium,heavy);
            call(session,"onParticipantLeave",light.player);
            restored(light);
            require(!((Map<?,?>)get(session,"classes")).containsKey(light.id),"Permanent leave removes the former contestant's class entry");
            withPlayerLookup(session,"onEnd",value("me.petr1furious.potatominigames.core.game.EndReason","ABORTED"));
            restored(medium);restored(heavy);
            require(((Map<?,?>)get(session,"originalVitals")).isEmpty(),"Session end clears captured original vitals");
            require(((Map<?,?>)get(session,"classes")).isEmpty(),"Session end clears retained match class entries");
            hostedMatchChecks(services,config,registry,loadouts);
            smoke.getLogger().info("FINALS_LOADOUT_SMOKE_PASS: "+assertions+" native lifecycle, GUI, health, speed and restoration assertions");
            return assertions;
        } finally {
            if(session!=null)try{withPlayerLookup(session,"onEnd",value("me.petr1furious.potatominigames.core.game.EndReason","ABORTED"));}catch(Exception error){smoke.getLogger().warning("Loadout smoke cleanup: "+error);}
            serverField.set(null,originalServer);
            if(worlds!=null)call(worlds,"destroyVoid","finals");
            for(FakePlayer player:players.values())player.attribute.remove();
        }
    }

    private void cashoutConfigChecks()throws Exception {
        String file="games/finals.yml";
        java.nio.file.Path target=smoke.getDataFolder().toPath().resolve(file);
        byte[] previous=java.nio.file.Files.exists(target)?java.nio.file.Files.readAllBytes(target):null;
        java.nio.file.Files.createDirectories(target.getParent());
        try {
            java.nio.file.Files.writeString(target,"cashoutSeconds: 40\ndurationSeconds: 777\n");
            Object configs=make("me.petr1furious.potatominigames.core.config.ConfigRegistry",smoke);
            Object config=make("FinalsConfig",configs);
            require(call(get(config,"cashoutSeconds"),"get").equals(90),
                    "An existing unversioned forty-second default upgrades to ninety seconds");
            require(call(get(config,"durationSeconds"),"get").equals(777),
                    "Cashout migration preserves other intentional game settings");
            var persisted=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(target.toFile());
            require(persisted.getInt("cashoutSeconds")==90&&persisted.getInt("cashoutTimingVersion")==1,
                    "The migrated duration and one-time marker survive restart");
            require(call(configs,"set","finals","cashoutSeconds","40").toString().equals("SUCCESS"),
                    "Administrators can still intentionally choose the former duration");
            configs=make("me.petr1furious.potatominigames.core.config.ConfigRegistry",smoke);
            config=make("FinalsConfig",configs);
            require(call(get(config,"cashoutSeconds"),"get").equals(40),
                    "A forty-second choice made after migration survives restart");
            call(configs,"reloadFile",file);
            require(call(get(config,"cashoutSeconds"),"get").equals(40),
                    "Configuration reload preserves an intentional forty-second choice");
            java.nio.file.Files.writeString(target,"cashoutSeconds: 63\n");
            configs=make("me.petr1furious.potatominigames.core.config.ConfigRegistry",smoke);
            config=make("FinalsConfig",configs);
            require(call(get(config,"cashoutSeconds"),"get").equals(63),
                    "An existing custom duration is not replaced by the new default");
            persisted=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(target.toFile());
            require(persisted.getInt("cashoutSeconds")==63&&persisted.getInt("cashoutTimingVersion")==1,
                    "Preserved custom durations also receive the one-time migration marker");
            java.nio.file.Files.writeString(target,"durationSeconds: 777\n");
            configs=make("me.petr1furious.potatominigames.core.config.ConfigRegistry",smoke);
            config=make("FinalsConfig",configs);
            require(call(get(config,"cashoutSeconds"),"get").equals(90),
                    "A missing cashout setting uses the new ninety-second code default");
            java.nio.file.Files.writeString(target,"cashoutTimingVersion: 1\ncashoutSeconds: 63\ndurationSeconds: 360\n");
            configs=make("me.petr1furious.potatominigames.core.config.ConfigRegistry",smoke);
            config=make("FinalsConfig",configs);
            require(call(get(config,"durationSeconds"),"get").equals(600),
                    "An existing six-minute default upgrades to ten minutes");
            require(call(get(config,"cashoutSeconds"),"get").equals(63),
                    "Match duration migration preserves a custom cashout duration");
            persisted=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(target.toFile());
            require(persisted.getInt("durationSeconds")==600&&persisted.getInt("matchTimingVersion")==1,
                    "The ten-minute duration and one-time migration marker persist");
            require(call(configs,"set","finals","durationSeconds","360").toString().equals("SUCCESS"),
                    "Administrators can deliberately restore the former six-minute duration");
            configs=make("me.petr1furious.potatominigames.core.config.ConfigRegistry",smoke);
            config=make("FinalsConfig",configs);
            require(call(get(config,"durationSeconds"),"get").equals(360),
                    "A deliberate six-minute setting made after migration survives restart");
            call(configs,"reloadFile",file);
            require(call(get(config,"durationSeconds"),"get").equals(360),
                    "Configuration reload preserves an intentional six-minute match");
            java.nio.file.Files.writeString(target,"durationSeconds: 777\n");
            configs=make("me.petr1furious.potatominigames.core.config.ConfigRegistry",smoke);
            config=make("FinalsConfig",configs);
            require(call(get(config,"durationSeconds"),"get").equals(777),
                    "An existing custom match duration is not replaced");
            persisted=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(target.toFile());
            require(persisted.getInt("durationSeconds")==777&&persisted.getInt("matchTimingVersion")==1,
                    "Preserved custom match durations receive the one-time migration marker");
            java.nio.file.Files.writeString(target,"cashoutTimingVersion: 1\nmatchTimingVersion: 1\n");
            configs=make("me.petr1furious.potatominigames.core.config.ConfigRegistry",smoke);
            config=make("FinalsConfig",configs);
            require(call(get(config,"durationSeconds"),"get").equals(600),
                    "A missing match duration uses the ten-minute code default");
        } finally {
            if(previous==null)java.nio.file.Files.deleteIfExists(target);
            else java.nio.file.Files.write(target,previous);
        }
    }

    private void cashoutMusicChecks(FakePlayer carrier,FakePlayer listener)throws Exception {
        Object state=get(session,"state"),physics=get(session,"cashouts");
        // Fake player transports do not load chunks as real connected players do.
        // Keep both stations and every listener movement in accessible native chunks.
        nativeAccess(()->{
            for(Object body:(Object[])get(physics,"bodies")) {
                Location origin=((Entity)get(body,"interaction")).getLocation();
                int chunkX=origin.getBlockX()>>4,chunkZ=origin.getBlockZ()>>4;
                for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)
                    origin.getWorld().getChunkAt(chunkX+dx,chunkZ+dz).load();
            }
            return null;
        });
        Class<?> musicType=type("FinalsCashoutMusic");
        Map<UUID,TextDisplay> emitters=new HashMap<>();
        Map<UUID,Entity> transports=new HashMap<>();
        Map<UUID,List<String>> emitterTeleports=new HashMap<>();
        List<TextDisplay> created=new ArrayList<>();
        Set<UUID> tracked=new HashSet<>(players.keySet());
        tracked.remove(listener.id);
        BiFunction<Player,Location,Entity> factory=(viewer,location)->{
            try {
                TextDisplay emitter=(TextDisplay)nativeAccess(()->call(musicType,"createEmitter",main,viewer,location));
                created.add(emitter);
                emitters.put(viewer.getUniqueId(),emitter);
                require(!emitter.isVisibleByDefault()&&!emitter.isPersistent()&&!emitter.hasGravity()
                                &&emitter.isInvulnerable()&&!emitter.isSilent()
                                &&emitter.getTeleportDuration()==2
                                &&PlainTextComponentSerializer.plainText().serialize(emitter.text()).isEmpty(),
                        "The actual music factory creates an empty, private, non-silent display with two-tick movement smoothing");
                require(players.values().stream().allMatch(player->
                                player.shownEntities.contains(emitter.getUniqueId())==player.id.equals(viewer.getUniqueId())),
                        "The private native emitter is shown only to its intended listener");
                Entity transport=(Entity)Proxy.newProxyInstance(Entity.class.getClassLoader(),new Class<?>[]{Entity.class},
                        (receiver,method,args)->{
                            if(method.getName().equals("getTrackedBy"))
                                return tracked.contains(viewer.getUniqueId())?Set.of(viewer):Set.of();
                            Object result=nativeAccess(()->invoke(method,emitter,args));
                            if(method.getName().equals("teleport"))
                                emitterTeleports.computeIfAbsent(viewer.getUniqueId(),ignored->new ArrayList<>())
                                        .add("requested="+args[0]+", accepted="+result+", actual="+emitter.getLocation());
                            return result;
                        });
                transports.put(viewer.getUniqueId(),transport);
                return transport;
            }catch(Exception error){throw new IllegalStateException("Native music emitter transport failed",error);}
        };
        call(get(session,"cashoutMusic"),"clear");
        set(session,"cashoutMusic",make("FinalsCashoutMusic",factory));
        Entity source=trackedCashout(physics,0);
        for(FakePlayer player:players.values())player.location=source.getLocation().add(40,0,0);
        carrier.location=source.getLocation().add(2,0,0);
        listener.location=source.getLocation().add(24,0,0);
        require(players.values().stream().allMatch(player->player.musicStarted.isEmpty()),
                "The active match has no cashout music before a box is deposited");
        long start=(long)get(session,"tick");
        require((boolean)call(state,"activateVault",carrier.id,start),"Music fixture opens a real vault");
        long opened=start+(long)call(get(session,"rules"),"vaultOpenTicks");
        set(session,"tick",opened);
        require((boolean)call(state,"pickUp",carrier.id,opened),"Music fixture picks up the unlocked box");
        withPlayerLookup(session,"interactStation",carrier.player,0);
        require(call(state,"phase").toString().equals("CASHOUT")
                        &&(long)call(state,"phaseDeadline")==opened+1_800,
                "The real station deposit starts a ninety-second cashout");
        TextDisplay waitingEmitter=emitters.get(listener.id);
        require(carrier.musicStarted.equals(List.of("minecraft:music_disc.pigstep"))
                        &&listener.musicStarted.isEmpty()&&waitingEmitter!=null&&waitingEmitter.isValid(),
                "A real deposit defers first playback until the listener has received its private emitter;"
                        +" carrierStarted="+carrier.musicStarted+", listenerStarted="+listener.musicStarted
                        +", listenerEmitter="+waitingEmitter+", listenerEmitterValid="+(waitingEmitter!=null&&waitingEmitter.isValid())
                        +", source="+source.getLocation()+", sourceValid="+source.isValid()
                        +", listener="+listener.location+", sourceDistance="+listener.location.distance(source.getLocation()));
        tracked.add(listener.id);
        withPlayerLookup(session,"updateCashoutMusic");
        require(carrier.musicStarted.equals(List.of("minecraft:music_disc.pigstep"))
                        &&listener.musicStarted.equals(carrier.musicStarted),
                "A real deposit starts Pigstep for connected players, including listeners twenty-four blocks away");
        require(carrier.musicSource==transports.get(carrier.id)&&listener.musicSource==transports.get(listener.id)
                        &&carrier.musicCategory==SoundCategory.RECORDS,
                "Background music binds once to each listener's private emitter in the record category");
        require(emitters.get(carrier.id).getLocation().distanceSquared(carrier.player.getEyeLocation().add(0,2,0))<.000001
                        &&emitters.get(listener.id).getLocation().distanceSquared(listener.player.getEyeLocation().add(0,2,0))<.000001,
                "Audible native emitters remain two blocks above their listeners' eye positions");
        close(carrier.musicVolume,.30,"Cashout music uses thirty-percent volume");
        require(players.values().stream().filter(player->player!=carrier&&player!=listener)
                        .allMatch(player->player.musicStarted.isEmpty()),
                "Far-away contestants do not receive the cashout's music");
        FakePlayer boundary=players.values().stream().filter(player->player!=carrier&&player!=listener).findFirst().orElseThrow();
        boundary.location=source.getLocation().add(32,0,0);
        withPlayerLookup(session,"updateCashoutMusic");
        require(boundary.musicStarted.equals(List.of("minecraft:music_disc.pigstep")),
                "A contestant at the thirty-two-block boundary hears cashout music");
        TextDisplay boundaryEmitter=emitters.get(boundary.id);
        boundary.location=source.getLocation().add(32.01,0,0);
        withPlayerLookup(session,"updateCashoutMusic");
        Location mutedExpected=boundary.player.getEyeLocation().add(0,24,0);
        Location mutedActual=boundaryEmitter.getLocation();
        String boundaryDiagnostic=" started="+boundary.musicStarted+", stopped="+boundary.musicStopped
                +", valid="+boundaryEmitter.isValid()+", sameEmitter="+(emitters.get(boundary.id)==boundaryEmitter)
                +", sourceDistance="+boundary.location.distance(source.getLocation())
                +", expected="+mutedExpected+", actual="+mutedActual
                +", deltaSquared="+mutedActual.distanceSquared(mutedExpected)
                +", teleports="+emitterTeleports.getOrDefault(boundary.id,List.of());
        require(boundary.musicStopped.isEmpty(),"Leaving range does not stop playback;"+boundaryDiagnostic);
        require(boundary.musicStarted.size()==1,"Leaving range does not replay playback;"+boundaryDiagnostic);
        require(boundaryEmitter.isValid()&&emitters.get(boundary.id)==boundaryEmitter,
                "Leaving range retains the original live emitter;"+boundaryDiagnostic);
        require(mutedActual.distanceSquared(mutedExpected)<.000001,
                "Leaving thirty-two blocks moves the emitter to its muted position;"+boundaryDiagnostic);
        boundary.location=source.getLocation().add(24,0,0);
        withPlayerLookup(session,"updateCashoutMusic");
        require(boundary.musicStarted.size()==1&&boundary.musicStopped.isEmpty()&&emitters.get(boundary.id)==boundaryEmitter
                        &&boundaryEmitter.getLocation().distanceSquared(boundary.player.getEyeLocation().add(0,2,0))<.000001,
                "Returning within range restores the same emitter without restarting the current track");
        call(session,"onPlayerPostRespawn",new com.destroystokyo.paper.event.player.PlayerPostRespawnEvent(
                boundary.player,boundary.location,false,false,false,PlayerRespawnEvent.RespawnReason.DEATH));
        require(boundary.musicStopped.size()==1&&!boundaryEmitter.isValid()
                        &&carrier.musicStopped.isEmpty()&&listener.musicStopped.isEmpty(),
                "A real post-respawn event removes only that listener's old emitter and playback");
        withPlayerLookup(session,"updateCashoutMusic");
        require(boundary.musicStarted.size()==2&&boundary.musicStarted.getLast().equals("minecraft:music_disc.pigstep")
                        &&boundary.musicSource==transports.get(boundary.id)&&emitters.get(boundary.id)!=boundaryEmitter
                        &&carrier.musicStarted.size()==1&&listener.musicStarted.size()==1,
                "The next update creates fresh playback for the respawned client without restarting anyone else");
        boundary.location=source.getLocation().add(40,0,0);
        withPlayerLookup(session,"updateCashoutMusic");
        require(boundary.musicStopped.size()==1&&boundary.musicStarted.size()==2&&emitters.get(boundary.id).isValid(),
                "Leaving range after respawn keeps the replacement emitter and current playback alive");
        withPlayerLookup(session,"tick");
        require(carrier.musicStarted.size()==1&&listener.musicStarted.size()==1,
                "The regular production tick does not restart a playing track");
        withPlayerLookup(session,"onParticipantQuit",listener.player);
        require(listener.musicStopped.equals(List.of("minecraft:music_disc.pigstep"))&&!emitters.get(listener.id).isValid(),
                "Disconnect stops only the departing listener's cashout track and removes its emitter");
        set(session,"tick",(long)call(state,"phaseDeadline")-1);
        // Payout replaces a native cashout entity, which requires the real CraftServer.
        // Playback cleanup retains its recipients and does not need fake player lookup.
        nativeAccess(()->{call(session,"tick");return null;});
        require(call(state,"phase").toString().equals("VAULT_CLOSED")
                        &&carrier.musicStopped.equals(List.of("minecraft:music_disc.pigstep"))
                        &&created.stream().noneMatch(Entity::isValid),
                "The exact production payout tick stops the old cashout music and removes every private emitter");
        require(listener.musicStarted.size()==1,
                "A disconnected participant does not receive music again while still in the registry");

        source=trackedCashout(physics,1);
        carrier.location=source.getLocation().add(2,0,0);
        start=(long)get(session,"tick");
        require((boolean)call(state,"activateVault",carrier.id,start),"The next objective can unlock normally");
        opened=start+(long)call(get(session,"rules"),"vaultOpenTicks");
        set(session,"tick",opened);
        require((boolean)call(state,"pickUp",carrier.id,opened),"The next objective's cashbox can be picked up");
        withPlayerLookup(session,"interactStation",carrier.player,1);
        require(carrier.musicStarted.equals(List.of("minecraft:music_disc.pigstep","minecraft:music_disc.lava_chicken")),
                "The next real cashout uses Lava Chicken instead of layering tracks");
        withPlayerLookup(session,"onEnd",value("me.petr1furious.potatominigames.core.game.EndReason","ABORTED"));
        require(carrier.musicStopped.equals(List.of("minecraft:music_disc.pigstep","minecraft:music_disc.lava_chicken"))
                        &&created.stream().noneMatch(Entity::isValid),
                "Aborting an active cashout explicitly stops Lava Chicken and removes all private emitters");
    }

    /** Real body lifecycle with fake network tracking, matching these fixtures' player transport. */
    private Entity trackedCashout(Object physics,int slot)throws Exception {
        Object body=((Object[])get(physics,"bodies"))[slot];
        Interaction nativeBody=(Interaction)get(body,"interaction");
        Interaction transport=(Interaction)Proxy.newProxyInstance(Interaction.class.getClassLoader(),
                new Class<?>[]{Interaction.class},(receiver,method,args)->{
                    if(method.getName().equals("getTrackedBy"))
                        return players.values().stream().map(player->player.player).collect(java.util.stream.Collectors.toSet());
                    try{return method.invoke(nativeBody,args);}
                    catch(InvocationTargetException failure){throw failure.getCause();}
                });
        set(body,"interaction",transport);
        return transport;
    }

    @SuppressWarnings("unchecked") private void hostedMatchChecks(Object services,Object config,Object registry,Object loadouts)throws Exception {
        nativeAccess(()->{call(worlds,"destroyVoid","finals");return null;});
        call(registry,"clear");
        List<FakePlayer> hosted=players.values().stream().limit(5).toList();
        for(FakePlayer player:hosted)call(registry,"add",player.player);
        FakePlayer master=hosted.getLast(),first=hosted.get(0),second=hosted.get(1);
        session=make("FinalsSession",services,config,null,loadouts);
        call(session,"onStart");
        call(get(session,"ticker"),"cancel");
        for(String helper:List.of("highlights","tabList")) {
            call(get(session,helper),"clear");set(session,helper,null);
        }
        Map<UUID,Integer> roster=(Map<UUID,Integer>)get(session,"roster");
        require(roster.size()==4&&!roster.containsKey(master.id)&&master.id.equals(get(session,"gameMaster")),
                "Actual five-player startup reserves the final participant as a teamless game master");
        require(Collections.frequency(new ArrayList<>(roster.values()),0)==2
                        &&Collections.frequency(new ArrayList<>(roster.values()),1)==2,
                "The real hosted startup balances exactly two teams of two");
        require((boolean)get(session,"revivalMode")&&get(session,"revives")!=null&&get(session,"respawnInput")!=null,
                "Hosted two-v-two installs the actual statue and Space-input helpers");
        for(FakePlayer player:hosted.subList(0,4))call(session,"spawn",player.player);
        master.storage.setItem(0,new ItemStack(Material.DIAMOND_SWORD));
        call(session,"spawnGameMaster",master.player);
        require(master.mode==GameMode.SPECTATOR&&master.storage.isEmpty()
                        &&!((Map<?,?>)get(session,"classes")).containsKey(master.id),
                "The game master spawns in spectator mode without combat gear or a class");
        require(!(boolean)call(session,"isInGame",master.player)&&(boolean)call(session,"mayFollowPlayers",master.player),
                "The game master is excluded from fighting and enabled for the spectator camera");
        require(master.walkSpeed>0&&master.flySpeed>0&&!((Map<?,?>)get(session,"frozen")).containsKey(master.id),
                "The game master can move during the contestants' loadout countdown");
        Object commands=make("FinalsEventCommands",main,(Supplier<Object>)()->session);
        try {
            commandDenied(commands,first,"low_gravity","A contestant cannot use game-master event commands");
            commandDenied(commands,master,"low_gravity","The game master cannot start an event before GO");
            call(commands,"open",master.player);
            require((boolean)call(type("FinalsEventCommands"),"isMenu",master.top)
                            &&master.top.getSize()==27&&nativeText(master.top.getItem(10)).length()>0,
                    "The game master's event menu renders real localized native item controls");
            InventoryOpenEvent open=new InventoryOpenEvent(master.view);
            call(session,"onInventoryOpen",open);
            require(!open.isCancelled(),"Session inventory rules permit the spectator event menu");
            master.player.closeInventory();
            set(session,"tick",360L);
            set(session,"state",make("FinalsState",new LinkedHashMap<>(roster),360L,get(session,"rules")));
            withPlayerLookup(session,"releaseAll");
            require((boolean)call(session,"gameEventsReady"),"The GM controls become ready only after the match begins");
            Object events=get(session,"gameEvents");
            call(commands,"execute",master.player,"low_gravity");
            require(call(events,"activeKind").toString().equals("LOW_GRAVITY")&&(int)call(events,"remainingSeconds")==30,
                    "The authorized GM command starts a thirty-second event");
            commandDenied(commands,master,"meteor_shower","A second event cannot overlap an already active event");
            call(commands,"execute",master.player,"stop");
            require(call(events,"activeKind")==null,"The GM stop command ends the active event");
            call(commands,"open",master.player);
            InventoryClickEvent click=new InventoryClickEvent(master.view,InventoryType.SlotType.CONTAINER,13,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            call(commands,"onClick",click);
            require(click.isCancelled()&&call(events,"activeKind").toString().equals("METEOR_SHOWER"),
                    "Clicking the spectator menu starts the selected meteor event without moving its icon");
            call(session,"onParticipantQuit",master.player);
            require(call(events,"activeKind")==null&&!(boolean)call(session,"isGameMaster",master.player),
                    "Disconnecting the game master stops the event and removes their control authority");
            call(session,"onParticipantRejoin",master.player);
            require((boolean)call(session,"isGameMaster",master.player)&&master.mode==GameMode.SPECTATOR
                            &&!roster.containsKey(master.id),
                    "Rejoining restores the same teamless spectator game-master role");
            call(commands,"onClick",click);
            require(call(events,"activeKind")==null,"A menu closed on disconnect cannot act as a new live control");

            PlayerTeleportEvent follow=new PlayerTeleportEvent(master.player,master.location,first.location,PlayerTeleportEvent.TeleportCause.SPECTATE);
            call(session,"onPlayerTeleport",follow);
            require(!follow.isCancelled(),"The game master may use spectator follow teleports within the arena");
            PlayerTeleportEvent escape=new PlayerTeleportEvent(master.player,master.location,Bukkit.getWorlds().getFirst().getSpawnLocation(),PlayerTeleportEvent.TeleportCause.COMMAND);
            call(session,"onPlayerTeleport",escape);
            require(escape.isCancelled(),"The session keeps game-master teleports inside the match world");
            set(session,"tick",500L);
            downedSpaceChecks(first,second);
            long start=(long)get(session,"tick")+100;
            Map<UUID,Long> respawns=(Map<UUID,Long>)get(session,"respawnAt");
            for(int index=0;index<2;index++) {
                FakePlayer victim=hosted.get(index);set(session,"tick",start+index*10);
                call(session,"onPlayerDeath",new PlayerDeathEvent(victim.player,DamageSource.builder(DamageType.GENERIC).build(),new ArrayList<>(),0,Component.empty(),true));
            }
            long deadline=start+330;
            require(respawns.size()==2&&respawns.values().stream().allMatch(at->at==deadline)
                            &&!respawns.containsKey(master.id),
                    "Both duo deaths start one sixteen-second wipe without involving the spectator game master");
            require(((Map<?,?>)get(get(session,"revives"),"statues")).isEmpty(),
                    "A duo wipe clears both old revive statues");
            set(session,"tick",deadline);call(session,"prepareTeamRespawns");
            call(session,"spawn",first.player);call(session,"spawn",second.player);
            require(first.location.equals(second.location)&&respawns.isEmpty(),
                    "Both duo teammates return equipped to the identical shared spawn location");
            call(session,"onParticipantLeave",master.player);
            call(session,"onParticipantRejoin",master.player);
            require(!(boolean)call(session,"isGameMaster",master.player),
                    "A GM who permanently leaves cannot recover control by reconnecting");
        } finally {
            org.bukkit.event.HandlerList.unregisterAll((org.bukkit.event.Listener)commands);
            withPlayerLookup(session,"onEnd",value("me.petr1furious.potatominigames.core.game.EndReason","ABORTED"));
        }
    }

    private void commandDenied(Object commands,FakePlayer player,String action,String label)throws Exception {
        try {call(commands,"execute",player.player,action);throw new AssertionError(label);}
        catch(Exception expected) {
            if(!expected.getClass().getSimpleName().equals("CommandFailure"))throw expected;
            assertions++;
        }
    }
    @SuppressWarnings("unchecked") private void equipmentChecks(Object loadouts,FakePlayer source,FakePlayer victim)throws Exception {
        Object weapons=get(session,"weapons"),gadgets=get(session,"gadgets");
        Object[] roles=type("FinalsClass").getEnumConstants();
        int selections=0;long now=1000;
        for(int roleIndex=0;roleIndex<roles.length;roleIndex++){
            Object role=roles[roleIndex];
            call(loadouts,"open",source.player);click(loadouts,source,11+roleIndex*2);
            List<?> available=(List<?>)call(type("FinalsWeapon"),"byClass",role);
            require(available.size()==5,"Each class exposes five real GUI choices");
            for(int index=0;index<available.size();index++){
                Object weapon=available.get(index);
                call(loadouts,"open",source.player);
                ItemStack menuItem=source.top.getItem(28+index);
                require(menuItem!=null&&nativeText(menuItem).length()>0,"Every weapon GUI choice has resolved native metadata");
                click(loadouts,source,28+index);
                require(call(call(loadouts,"choice",source.id),"weapon")==weapon,"GUI selection saves the intended weapon");
                source.player.closeInventory();call(session,"spawn",source.player);
                require(call(weapons,"selected",source.id)==weapon,"Next spawn equips every selected primary weapon");
                require(nativeText(source.storage.getItem(0)).length()>0,"Every equipped weapon has resolved native name and lore");
                now+=200;set(session,"tick",now);
                Object equipped=((Map<UUID,Object>)get(weapons,"equipped")).get(source.id);
                if((boolean)call(weapon,"isGun")){
                    Object gun=get(equipped,"gun");int before=(int)call(gun,"ammo");
                    PlayerInteractEvent fire=new PlayerInteractEvent(source.player,Action.RIGHT_CLICK_AIR,source.storage.getItem(0),null,org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND);
                    call(session,"onPlayerInteract",fire);
                    require(fire.isCancelled()&&(int)call(gun,"ammo")==before-1,"Native gun right click fires and consumes one round");
                    PlayerSwapHandItemsEvent reload=new PlayerSwapHandItemsEvent(source.player,source.storage.getItem(0),new ItemStack(Material.AIR));
                    call(session,"onSwapHandItems",reload);
                    require(reload.isCancelled()&&(boolean)call(gun,"reloading"),"Native F event starts reload without swapping inventory");
                }else{
                    EntityDamageByEntityEvent melee=new EntityDamageByEntityEvent(source.player,victim.player,
                            EntityDamageEvent.DamageCause.ENTITY_ATTACK,DamageSource.builder(DamageType.GENERIC).build(),1);
                    call(session,"onEntityDamageByEntity",melee);
                    require(melee.isCancelled()&&(long)get(equipped,"nextMelee")>now,"Native melee event dispatches a custom swing and consumes vanilla damage");
                }
                selections++;
            }
            List<String> english=new ArrayList<>();
            source.locale=Locale.ENGLISH;call(gadgets,"equip",source.player,role);
            for(int slot=1;slot<=4;slot++)english.add(nativeText(source.storage.getItem(slot)));
            source.locale=Locale.forLanguageTag("ru");call(gadgets,"equip",source.player,role);
            for(int slot=1;slot<=4;slot++){
                String russian=nativeText(source.storage.getItem(slot));
                require(!russian.equals(english.get(slot-1)),"Every gadget name/lore changes for a Russian viewer");
            }
            call(loadouts,"open",source.player);
            for(int slot:new int[]{11,13,15,28,29,30,31,32})
                require(nativeText(source.top.getItem(slot)).length()>0,"Russian GUI class/weapon metadata is rendered natively");
            source.player.closeInventory();source.locale=Locale.ENGLISH;
        }
        require(selections==15,"All fifteen weapon GUI and equip paths were exercised");
    }
    private void click(Object loadouts,FakePlayer player,int slot)throws Exception{
        InventoryClickEvent event=new InventoryClickEvent(player.view,InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        call(loadouts,"onClick",event);require(event.isCancelled(),"Native GUI click is consumed");
    }
    @SuppressWarnings("unchecked") private void downedLoadoutChecks(Object loadouts,FakePlayer target)throws Exception {
        Map<UUID,Integer> roster=(Map<UUID,Integer>)get(session,"roster");
        FakePlayer ally=players.values().stream().filter(player->player!=target
                &&Objects.equals(roster.get(player.id),roster.get(target.id))).findFirst().orElseThrow();
        customLoadoutChecks(loadouts,target);
        Object equipped=call(loadouts,"choice",target.id);
        call(loadouts,"open",target.player);
        click(loadouts,target,15);click(loadouts,target,29);click(loadouts,target,22);
        Object pending=call(loadouts,"choice",target.id);
        target.player.closeInventory();
        require(!pending.equals(equipped),"The next-spawn selection differs from the complete equipped kit");
        assertEquipped(target,equipped,"Choosing a new kit while alive");
        set(session,"tick",500L);
        nativeAccess(()->{call(session,"onPlayerDeath",new PlayerDeathEvent(target.player,
                DamageSource.builder(DamageType.GENERIC).build(),new ArrayList<>(),0,Component.empty(),true));return null;});
        Object revival=get(session,"revives"),context=call(session,"reviveContext");
        Location point=(Location)call(revival,"location",target.id);
        require((boolean)withNativePlayerLookup(context,"complete",target,target.id,ally.player,point,false),
                "A teammate can complete the actual manual revival callback");
        assertEquipped(target,equipped,"Manual revival");
        require(call(loadouts,"choice",target.id).equals(pending),
                "Manual revival preserves the pending next-spawn choice");

        Map<UUID,Long> respawns=(Map<UUID,Long>)get(session,"respawnAt");
        set(session,"tick",600L);
        downForLoadout(target);
        long ready=respawns.get(target.id);
        withNativePlayerLookup(session,"requestDownedLoadout",target,target.id);
        require((boolean)call(type("FinalsLoadouts"),"viewing",target.player),
                "The downed-player callback opens the actual loadout inventory");
        click(loadouts,target,13);click(loadouts,target,29);click(loadouts,target,22);
        Object medium=value("FinalsClass","MEDIUM");
        List<Object> options=(List<Object>)call(type("FinalsGadgetState$Kind"),"gadgets",medium);
        List<String> nextGadgets=List.of("SMOKE_GRENADE","GAS_GRENADE","APS_TURRET");
        for(int slot=0;slot<nextGadgets.size();slot++) {
            call(loadouts,"open",target.player,slot);
            click(loadouts,target,options.indexOf(value("FinalsGadgetState$Kind",nextGadgets.get(slot))));
        }
        Object downedChoice=call(loadouts,"choice",target.id);
        require(call(downedChoice,"playerClass")==medium
                        &&call(downedChoice,"weapon").toString().equals("FCAR")
                        &&call(downedChoice,"specialization").toString().equals("GUARDIAN_TURRET")
                        &&((List<?>)call(downedChoice,"gadgets")).stream().map(Object::toString).toList().equals(nextGadgets),
                "While downed, the real menus save the chosen class, weapon, specialization and all three gadgets");
        require(respawns.get(target.id)==ready&&target.mode==GameMode.SPECTATOR
                        &&((Map<?,?>)get(session,"classes")).get(target.id)==call(equipped,"playerClass"),
                "Downed menu edits preserve the wait timer and the eliminated player's equipped class");
        Object reloaded=make("FinalsLoadouts",main,(Consumer<Player>)ignored->{});
        try {
            require(call(reloaded,"choice",target.id).equals(downedChoice),
                    "The full choice made while downed is persisted for future respawns");
        } finally {org.bukkit.event.HandlerList.unregisterAll((org.bukkit.event.Listener)reloaded);}
        set(session,"tick",ready);
        withNativePlayerLookup(session,"requestRespawn",target,target.id);
        require(respawns.containsKey(target.id),"Space cannot accidentally respawn while the loadout inventory is open");
        point=(Location)call(revival,"location",target.id);
        require((boolean)withNativePlayerLookup(context,"complete",target,target.id,ally.player,point,true),
                "A defibrillator can revive the downed player while their loadout menu is open");
        assertEquipped(target,equipped,"Defibrillator revival after downed edits");
        close(target.attribute.getHealth(),(double)call(call(equipped,"playerClass"),"health")/2,
                "Defibrillator half-health uses the equipped class");
        require(call(loadouts,"choice",target.id).equals(downedChoice)
                        &&!(boolean)call(type("FinalsLoadouts"),"viewing",target.player),
                "Revival closes the menu while preserving the full pending choice");

        set(session,"tick",(long)get(session,"tick")+100);
        downForLoadout(target);
        ready=respawns.get(target.id);
        withNativePlayerLookup(session,"requestDownedLoadout",target,target.id);
        target.player.closeInventory();
        set(session,"tick",ready-1);
        withNativePlayerLookup(session,"requestRespawn",target,target.id);
        require(respawns.containsKey(target.id),"Pending kit changes do not bypass the individual respawn timer");
        set(session,"tick",ready);
        withNativePlayerLookup(session,"requestRespawn",target,target.id);
        require(!respawns.containsKey(target.id),"Ready Space respawn consumes the downed state");
        assertEquipped(target,downedChoice,"Space respawn applies the pending kit");

        call(loadouts,"open",target.player);click(loadouts,target,15);click(loadouts,target,29);click(loadouts,target,22);
        Object wipeChoice=call(loadouts,"choice",target.id);
        target.player.closeInventory();
        set(session,"revivalMode",false);
        Location allyPosition=ally.location.clone();
        try {
            set(session,"tick",(long)get(session,"tick")+100);
            nativeAccess(()->{call(session,"onPlayerDeath",new PlayerDeathEvent(target.player,
                    DamageSource.builder(DamageType.GENERIC).build(),new ArrayList<>(),0,Component.empty(),true));return null;});
            ally.location=target.location.clone();
            Object combat=call(session,"combatContext");
            require((boolean)nativeAccess(()->call(combat,"revive",target.player,ally.player)),
                    "The legacy instant-defibrillator callback can complete a revival");
            assertEquipped(target,downedChoice,"Legacy defibrillator preserves the equipped kit");
            require(call(loadouts,"choice",target.id).equals(wipeChoice),
                    "The legacy revival also leaves the pending kit saved");
        } finally {set(session,"revivalMode",true);ally.location=allyPosition;}

        List<FakePlayer> team=players.values().stream()
                .filter(player->Objects.equals(roster.get(player.id),roster.get(target.id))).toList();
        long waveStart=(long)get(session,"tick")+100;
        for(int index=0;index<team.size();index++) {
            set(session,"tick",waveStart+index*10);
            downForLoadout(team.get(index));
        }
        long wipeDeadline=respawns.get(target.id);
        withNativePlayerLookup(session,"requestDownedLoadout",target,target.id);
        require((boolean)call(type("FinalsLoadouts"),"viewing",target.player),
                "The loadout menu remains available during a full-team wipe countdown");
        set(session,"tick",wipeDeadline-1);
        nativeAccess(()->{call(session,"spawn",target.player);return null;});
        require(respawns.containsKey(target.id),"A queued kit cannot bypass the team-wipe countdown");
        set(session,"tick",wipeDeadline);
        nativeAccess(()->{call(session,"prepareTeamRespawns");return null;});
        for(FakePlayer player:team)nativeAccess(()->{call(session,"spawn",player.player);return null;});
        assertEquipped(target,wipeChoice,"Team-wipe respawn applies the pending kit");
        require(team.stream().allMatch(player->player.location.equals(target.location))
                        &&!(boolean)call(type("FinalsLoadouts"),"viewing",target.player),
                "The wipe respawns the whole team together and closes the loadout menu");
        int menuOpens=target.menuOpens;
        withNativePlayerLookup(session,"requestDownedLoadout",target,target.id);
        require(target.menuOpens==menuOpens,"The downed shortcut does not open the menu for an alive player");
    }

    private void downForLoadout(FakePlayer target)throws Exception {
        nativeAccess(()->{call(session,"onPlayerDeath",new PlayerDeathEvent(target.player,
                DamageSource.builder(DamageType.GENERIC).build(),new ArrayList<>(),0,Component.empty(),true));
            call(session,"waitForRespawn",target.player);return null;});
    }

    private void assertEquipped(FakePlayer player,Object choice,String label)throws Exception {
        require(((Map<?,?>)get(session,"classes")).get(player.id)==call(choice,"playerClass"),
                label+" keeps the actually equipped class");
        close(player.attribute.getAttribute(Attribute.SCALE).getBaseValue(),
                ((Number)call(call(choice,"playerClass"),"scale")).doubleValue(),label+" keeps the equipped class body size");
        require(player.storage.getItem(7)==null||player.storage.getItem(7).getType().isAir(),label+" has no pickaxe");
        require(call(get(session,"weapons"),"selected",player.id)==call(choice,"weapon"),
                label+" keeps the actually equipped weapon");
        List<?> gadgets=(List<?>)call(choice,"gadgets");
        NamespacedKey key=new NamespacedKey("potatominigames","finals_gadget");
        for(int slot=1;slot<=4;slot++) {
            Object expected=slot==4?call(choice,"specialization"):gadgets.get(slot-1);
            ItemStack item=player.storage.getItem(slot);
            require(item!=null&&expected.toString().equals(item.getItemMeta().getPersistentDataContainer()
                            .get(key,org.bukkit.persistence.PersistentDataType.STRING)),
                    label+" keeps equipped gadget/specialization slot "+slot);
        }
    }
    @SuppressWarnings("unchecked") private void customLoadoutChecks(Object loadouts,FakePlayer player)throws Exception {
        call(loadouts,"open",player.player);
        click(loadouts,player,11);
        click(loadouts,player,22);
        Object selected=call(loadouts,"choice",player.id);
        require(call(selected,"specialization").toString().equals("CLOAKING_DEVICE"),
                "The actual loadout menu saves the selected class specialization");
        List<Object> available=(List<Object>)call(type("FinalsGadgetState$Kind"),"gadgets",value("FinalsClass","LIGHT"));
        Object smoke=value("FinalsGadgetState$Kind","SMOKE_GRENADE");
        Object flash=value("FinalsGadgetState$Kind","FLASHBANG");
        call(loadouts,"open",player.player,1);
        click(loadouts,player,available.indexOf(smoke));
        selected=call(loadouts,"choice",player.id);
        require(((List<?>)call(selected,"gadgets")).get(1)==smoke,
                "The gadget picker replaces only the chosen gadget slot");
        call(loadouts,"open",player.player,2);
        Object before=call(loadouts,"choice",player.id);
        click(loadouts,player,available.indexOf(value("FinalsGadgetState$Kind","FRAG_GRENADE")));
        require(before.equals(call(loadouts,"choice",player.id)),
                "The actual picker rejects a duplicate gadget without changing the saved loadout");
        click(loadouts,player,available.indexOf(flash));
        call(loadouts,"open",player.player);
        click(loadouts,player,29);
        selected=call(loadouts,"choice",player.id);
        require(call(selected,"weapon").toString().equals("XP54")
                        &&call(selected,"specialization").toString().equals("CLOAKING_DEVICE")
                        &&((List<?>)call(selected,"gadgets")).get(1)==smoke
                        &&((List<?>)call(selected,"gadgets")).get(2)==flash,
                "Selecting a new weapon preserves the custom specialization and gadget trio");
        call(session,"spawn",player.player);
        var key=new NamespacedKey("potatominigames","finals_gadget");
        for(var entry:Map.of(1,"FRAG_GRENADE",2,"SMOKE_GRENADE",3,"FLASHBANG",4,"CLOAKING_DEVICE").entrySet()) {
            ItemStack equipped=player.storage.getItem(entry.getKey());
            require(entry.getValue().equals(equipped.getItemMeta().getPersistentDataContainer()
                            .get(key,org.bukkit.persistence.PersistentDataType.STRING)),
                    "A real respawn equips the chosen gadget or specialization in its intended slot");
            nativeText(equipped);
        }
        Object reloaded=make("FinalsLoadouts",main,(Consumer<Player>)ignored -> {});
        require(selected.equals(call(reloaded,"choice",player.id)),
                "Reloading the real YAML restores all custom choices");
        org.bukkit.event.HandlerList.unregisterAll((org.bukkit.event.Listener)reloaded);
        player.player.closeInventory();
    }
    @SuppressWarnings("unchecked") private void teamWipeChecks(FakePlayer first,FakePlayer second,FakePlayer third)throws Exception {
        List<FakePlayer> cohort=List.of(first,second,third);
        Map<UUID,Integer> roster=(Map<UUID,Integer>)get(session,"roster");
        Map<UUID,Integer> previousRoster=new LinkedHashMap<>(roster);
        Object previousState=get(session,"state");
        Map<UUID,Long> respawns=(Map<UUID,Long>)get(session,"respawnAt");
        Map<UUID,Location> positions=(Map<UUID,Location>)get(session,"teamRespawnPositions");
        long base=(long)get(session,"tick")+100;
        try {
            roster.replaceAll((id,team)->cohort.stream().anyMatch(player->player.id.equals(id))?0:1);
            set(session,"state",make("FinalsState",new LinkedHashMap<>(roster),base,get(session,"rules")));
            set(session,"tick",base);
            require(respawns.isEmpty(),"The native wipe fixture begins with three living contestants");
            downedSpaceChecks(first,second);
            base=(long)get(session,"tick")+100;
            for(int wave=0;wave<2;wave++) {
                long start=base+wave*500;
                for(int index=0;index<cohort.size();index++) {
                    FakePlayer victim=cohort.get(index);
                    set(session,"tick",start+index*10);
                    nativeAccess(()->{
                        PlayerDeathEvent event=new PlayerDeathEvent(victim.player,
                                DamageSource.builder(DamageType.GENERIC).build(),new ArrayList<>(),0,Component.empty(),true);
                        call(session,"onPlayerDeath",event);return null;
                    });
                    if(index<2)require(respawns.get(victim.id)==start+index*10+160,
                            "The first two actual death handlers unlock Space eight seconds later");
                }
                long deadline=start+340;
                require(cohort.stream().allMatch(player->Objects.equals(respawns.get(player.id),deadline)),
                        "The third native death handler synchronizes all three to last death plus sixteen seconds");
                set(session,"tick",deadline-1);
                Location before=first.location.clone();
                nativeAccess(()->{call(session,"spawn",first.player);return null;});
                require(first.location.equals(before)&&respawns.containsKey(first.id),
                        "The actual spawn entry cannot release a teammate one tick before the shared deadline");
                Object context=call(session,"combatContext");
                Long held=respawns.remove(first.id);
                Location firstBefore=first.location.clone();
                GameMode modeBefore=first.mode;
                try {
                    first.mode=GameMode.SURVIVAL;
                    first.location=((Map<UUID,Location>)get(session,"deathLocations")).get(second.id).clone();
                    require(!(boolean)call(context,"revive",second.player,first.player),
                            "The production defibrillator context cannot bypass a pending wipe even with an active reviver");
                } finally { respawns.put(first.id,held);first.location=firstBefore;first.mode=modeBefore; }
                if(wave==1) {
                    nativeAccess(()->{call(session,"onParticipantQuit",third.player);return null;});
                    require(Objects.equals(respawns.get(third.id),deadline),
                            "Temporary disconnect preserves a dead cohort member's shared deadline");
                }
                set(session,"tick",deadline);
                nativeAccess(()->{call(session,"prepareTeamRespawns");return null;});
                Location chosen=positions.get(first.id).clone();
                require(positions.size()==3&&positions.values().stream().allMatch(chosen::equals),
                        "Deadline resolution chooses and clones exactly one spawn for the whole team");
                for(FakePlayer player:wave==0?cohort:List.of(first,second))
                    nativeAccess(()->{call(session,"spawn",player.player);return null;});
                if(wave==1) {
                    require(positions.containsKey(third.id)&&Objects.equals(respawns.get(third.id),deadline),
                            "An offline teammate retains its completed cohort anchor until returning");
                    nativeAccess(()->{call(session,"onParticipantRejoin",third.player);return null;});
                    require(Objects.equals(respawns.get(third.id),deadline),
                            "Late rejoin cannot replace the completed wipe with a fresh five-second wait");
                    nativeAccess(()->{call(session,"spawn",third.player);return null;});
                }
                for(FakePlayer player:cohort) {
                    require(player.location.equals(chosen)&&player.mode==GameMode.SURVIVAL,
                            "Actual equipped respawns place every teammate at the same complete Location");
                    require(player.storage.getItem(0)!=null&&player.storage.getItem(4)!=null,
                            "Group respawns reinstall primary weapons and class specialization");
                    require(player.attribute.getHealth()==player.attribute.getAttribute(Attribute.MAX_HEALTH).getValue(),
                            "Group respawns restore full class health");
                }
                require(respawns.isEmpty()&&positions.isEmpty(),
                        "Completed native waves consume every timer and cached anchor before the next wipe");
            }
        } finally {
            roster.clear();roster.putAll(previousRoster);
            set(session,"state",previousState);
        }
    }

    @SuppressWarnings("unchecked") private void downedSpaceChecks(FakePlayer target,FakePlayer ally)throws Exception {
        long start=(long)get(session,"tick");
        Map<UUID,Long> respawns=(Map<UUID,Long>)get(session,"respawnAt");
        nativeAccess(()->{call(session,"onPlayerDeath",new PlayerDeathEvent(target.player,
                DamageSource.builder(DamageType.GENERIC).build(),new ArrayList<>(),0,Component.empty(),true));return null;});
        require(respawns.get(target.id)==start+160,"A real three-v-three death records an eight-second Space unlock");
        Object revival=get(session,"revives");
        Object statue=((Map<?,?>)get(revival,"statues")).get(target.id);
        require(statue!=null&&((Entity)get(statue,"item")).isValid(),"A real elimination creates its native teammate statue");
        set(session,"tick",start+159);
        nativeAccess(()->{call(session,"requestRespawn",target.id);return null;});
        require(respawns.containsKey(target.id),"Space before the eighth second cannot respawn the eliminated player");
        // These helpers mutate native entities. Pause them only while the lookup transport
        // drives the real tick; player spawning must never execute under that proxy.
        Object cashouts=get(session,"cashouts");
        target.mode=GameMode.SPECTATOR;
        set(session,"revives",null);set(session,"cashouts",null);
        try {
            withPlayerLookup(session,"tick");
            require((long)get(session,"tick")==start+160&&respawns.containsKey(target.id),
                    "The actual eighth-second tick unlocks Space without automatically spawning");
            set(session,"tick",start+400);
            withPlayerLookup(session,"tick");
            require(respawns.containsKey(target.id),"An eliminated player remains downed well after eight seconds without pressing Space");
        } finally {set(session,"revives",revival);set(session,"cashouts",cashouts);}
        require(((Entity)get(statue,"item")).isValid(),"The statue remains revivable indefinitely after Space unlocks");
        withNativePlayerLookup(session,"requestRespawn",target,target.id);
        require(!respawns.containsKey(target.id)&&target.mode==GameMode.SURVIVAL,
                "The actual ready Space callback performs the equipped native respawn");
        require(!((Map<?,?>)get(revival,"statues")).containsKey(target.id),"Space consumes the player's statue and pending revive state");

        set(session,"tick",(long)get(session,"tick")+100);
        nativeAccess(()->{call(session,"onPlayerDeath",new PlayerDeathEvent(target.player,
                DamageSource.builder(DamageType.GENERIC).build(),new ArrayList<>(),0,Component.empty(),true));return null;});
        Object context=call(session,"reviveContext");
        Location point=(Location)call(revival,"location",target.id);
        require((boolean)withNativePlayerLookup(context,"complete",target,target.id,ally.player,point,true),
                "The accepted defibrillator rebuild callback reaches the real Session completion path");
        close(target.attribute.getHealth(),target.attribute.getAttribute(Attribute.MAX_HEALTH).getValue()/2,
                "A completed defibrillator rebuild restores exactly half class health");
        Object combat=call(session,"combatContext");
        require((boolean)call(combat,"gadgetsBlocked",target.player)&&!(boolean)call(combat,"weaponBlocked",target.player),
                "Defibrillator sickness blocks gadgets while preserving primary weapons");
        long sickness=(long)get(session,"tick")+100;
        set(session,"tick",sickness-1);
        require((boolean)call(combat,"gadgetsBlocked",target.player),"Gadget sickness lasts its complete five seconds");
        set(session,"tick",sickness);
        require(!(boolean)call(combat,"gadgetsBlocked",target.player),"Gadget sickness ends on the exact five-second boundary");

        set(session,"revivalMode",false);
        Location allyBefore=ally.location.clone();
        try {
            nativeAccess(()->{call(session,"onPlayerDeath",new PlayerDeathEvent(target.player,
                    DamageSource.builder(DamageType.GENERIC).build(),new ArrayList<>(),0,Component.empty(),true));return null;});
            ally.location=target.location.clone();
            require((boolean)nativeAccess(()->call(combat,"revive",target.player,ally.player)),
                    "Other match sizes retain the original instant Defibrillator completion path");
            require(!respawns.containsKey(target.id),"The legacy instant revive consumes its ordinary respawn timer");
            close(target.attribute.getHealth(),target.attribute.getAttribute(Attribute.MAX_HEALTH).getValue(),
                    "The legacy instant revive retains full class health");
        } finally {set(session,"revivalMode",true);ally.location=allyBefore;}

        Object geometry=get(session,"arena");
        Location cell=(Location)call(geometry,"spawn",0,0);
        World world=cell.getWorld();int x=cell.getBlockX(),y=cell.getBlockY(),z=cell.getBlockZ();
        var wall=world.getBlockAt(x+1,y,z).getState();
        var ceiling=world.getBlockAt(x,y+2,z).getState();
        try {
            nativeAccess(()->{wall.getBlock().setType(Material.STONE,false);ceiling.getBlock().setType(Material.STONE,false);return null;});
            Location edge=new Location(world,x+.95,y+.75,z+.5);
            Location safe=(Location)nativeAccess(()->call(session,"safeRevivePoint",target.id,edge));
            close(safe.getX(),x+.5,"Revival centers the complete player body away from an adjacent wall");
            close(safe.getY(),y,"Revival removes fractional height before checking ceiling clearance");
            require(safe.getX()+.3<x+1&&safe.getY()+1.8<y+2,
                    "The actual returned standing position clears both the wall and low ceiling");
        } finally {nativeAccess(()->{wall.update(true,false);ceiling.update(true,false);return null;});}
    }

    /** Resolve one fake player, then restore CraftServer before native equipment/world access. */
    private Object withNativePlayerLookup(Object owner,String method,FakePlayer target,Object... args)throws Exception {
        Server previous=Bukkit.getServer();
        Server oneShot=(Server)Proxy.newProxyInstance(Server.class.getClassLoader(),new Class<?>[]{Server.class},(receiver,operation,values)->{
            if(operation.getName().equals("getPlayer")&&values[0].equals(target.id)) {
                serverField.set(null,originalServer);return target.player;
            }
            try{return operation.invoke(originalServer,values);}
            catch(InvocationTargetException failure){throw failure.getCause();}
        });
        serverField.set(null,oneShot);
        try{return call(owner,method,args);}finally{serverField.set(null,previous);}
    }

    private String nativeText(ItemStack item){
        require(item!=null&&item.hasItemMeta()&&item.getItemMeta().displayName()!=null,"Native item name exists");
        var meta=item.getItemMeta();List<Component> components=new ArrayList<>();components.add(meta.displayName());
        if(meta.lore()!=null)components.addAll(meta.lore());
        String result=components.stream().map(PlainTextComponentSerializer.plainText()::serialize).collect(java.util.stream.Collectors.joining(" | "));
        require(!result.contains("potatominigames."),"Item metadata contains resolved text rather than custom translation keys");
        return result;
    }
    private void restored(FakePlayer player){
        close(player.attribute.getAttribute(Attribute.MAX_HEALTH).getBaseValue(),player.originalHealth,"Original max health restored");
        close(player.attribute.getAttribute(Attribute.SCALE).getBaseValue(),player.originalScale,"Original custom body size restored");
        close(player.walkSpeed,player.originalWalk,"Original walk speed restored");
        close(player.flySpeed,player.originalFly,"Original fly speed restored");
    }
    private static Object zero(Class<?> type){
        if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;
        if(type==float.class)return 0f;if(type==double.class)return 0d;return null;
    }
    private final class FakePlayer {
        final UUID id=UUID.randomUUID(); final String name;
        final ArmorStand attribute;
        final double originalHealth,originalScale;final float originalWalk,originalFly;
        final Inventory storage=Bukkit.createInventory(null,54);
        final Inventory fallback=Bukkit.createInventory(null,9);
        final Map<PotionEffectType,PotionEffect> effects=new HashMap<>();
        final List<String> musicStarted=new ArrayList<>(),musicStopped=new ArrayList<>();
        final Set<UUID> shownEntities=new HashSet<>();
        Entity musicSource;SoundCategory musicCategory;float musicVolume;
        final PlayerInventory inventory;
        final InventoryView view;
        final Player player;
        Inventory top=fallback;Locale locale=Locale.ENGLISH;
        Location location;GameMode mode=GameMode.SURVIVAL;float walkSpeed,flySpeed;int held,menuOpens;
        ItemStack offhand=new ItemStack(Material.AIR),chest;
        Scoreboard scoreboard=Bukkit.getScoreboardManager().getMainScoreboard();
        FakePlayer(String name,World world,double originalHealth,float originalWalk,float originalFly){
            this.name=name;this.originalHealth=originalHealth;this.originalWalk=originalWalk;this.originalFly=originalFly;
            originalScale=.75+originalHealth/100;
            location=world.getSpawnLocation();walkSpeed=originalWalk;flySpeed=originalFly;
            attribute=world.spawn(location,ArmorStand.class);attribute.setGravity(false);attribute.setInvulnerable(true);
            attribute.getAttribute(Attribute.MAX_HEALTH).setBaseValue(originalHealth);attribute.setHealth(originalHealth);
            attribute.getAttribute(Attribute.SCALE).setBaseValue(originalScale);
            inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class<?>[]{PlayerInventory.class},(o,m,a)->switch(m.getName()){
                case "setItem"->{storage.setItem((int)a[0],(ItemStack)a[1]);yield null;}
                case "getItem"->storage.getItem((int)a[0]);
                case "setItemInOffHand"->{offhand=(ItemStack)a[0];yield null;}
                case "getItemInOffHand"->offhand;
                case "getItemInMainHand"->Optional.ofNullable(storage.getItem(held)).orElse(new ItemStack(Material.AIR));
                case "setHeldItemSlot"->{held=(int)a[0];yield null;}
                case "getHeldItemSlot"->held;
                case "clear"->{storage.clear();yield null;}
                case "setChestplate"->{chest=(ItemStack)a[0];yield null;}
                case "getChestplate"->chest;
                case "setArmorContents","setHelmet","setLeggings","setBoots"->null;
                default->zero(m.getReturnType());
            });
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(o,m,a)->switch(m.getName()){
                case "equals"->o==a[0];case "hashCode"->System.identityHashCode(o);case "toString","getName"->name;
                case "getUniqueId"->id;case "locale"->locale;case "displayName"->Component.text(name);
                case "getWorld"->location.getWorld();case "getLocation","getEyeLocation"->location.clone();
                case "getInventory"->inventory;case "getGameMode"->mode;
                case "setGameMode"->{mode=(GameMode)a[0];yield null;}
                case "getWalkSpeed"->walkSpeed;case "getFlySpeed"->flySpeed;
                case "setWalkSpeed"->{walkSpeed=(float)a[0];yield null;}case "setFlySpeed"->{flySpeed=(float)a[0];yield null;}
                case "isOnline"->true;case "isDead","isSneaking"->false;
                case "getKiller"->null;
                case "getAttribute"->attribute.getAttribute((Attribute)a[0]);case "getHealth"->attribute.getHealth();
                case "setHealth"->{attribute.setHealth((double)a[0]);yield null;}
                case "getActivePotionEffects"->List.copyOf(effects.values());
                case "addPotionEffect"->{PotionEffect effect=(PotionEffect)a[0];effects.put(effect.getType(),effect);yield true;}
                case "removePotionEffect"->{effects.remove((PotionEffectType)a[0]);yield null;}
                case "getMaximumAir"->300;
                case "teleport"->{location=((Location)a[0]).clone();yield true;}
                case "getScoreboard"->scoreboard;case "setScoreboard"->{scoreboard=(Scoreboard)a[0];yield null;}
                case "openInventory"->{top=(Inventory)a[0];menuOpens++;yield view();}
                case "getOpenInventory"->view();case "closeInventory"->{top=fallback;yield null;}
                case "playSound"->{
                    if(a.length>=5&&a[0] instanceof Entity source&&a[1] instanceof String track
                            &&track.startsWith("minecraft:music_disc.")) {
                        musicSource=source;musicCategory=(SoundCategory)a[2];musicVolume=(float)a[3];musicStarted.add(track);
                    }
                    yield null;
                }
                case "stopSound"->{
                    if(a.length==2&&a[0] instanceof String track&&track.startsWith("minecraft:music_disc.")) {
                        require(a[1]==SoundCategory.RECORDS,"Cleanup stops the exact record category");
                        musicStopped.add(track);
                    }
                    yield null;
                }
                case "showEntity"->{shownEntities.add(((Entity)a[1]).getUniqueId());yield null;}
                default->zero(m.getReturnType());
            });
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(o,m,a)->switch(m.getName()){
                case "getTopInventory"->top;case "getBottomInventory"->inventory;case "getPlayer"->player;
                case "getType"->InventoryType.CHEST;case "getTitle","getOriginalTitle"->"Native loadout smoke";
                case "convertSlot"->a[0];case "getSlotType"->InventoryType.SlotType.CONTAINER;
                case "getInventory"->(int)a[0]<top.getSize()?top:inventory;
                case "getItem"->top.getItem((int)a[0]);case "countSlots"->top.getSize()+36;
                default->zero(m.getReturnType());
            });
        }
        private InventoryView view(){return view;}
    }
}
