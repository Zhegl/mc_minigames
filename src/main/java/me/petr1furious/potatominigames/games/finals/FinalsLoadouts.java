package me.petr1furious.potatominigames.games.finals;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.command.GameCommands;
import me.petr1furious.potatominigames.core.command.Suggest;
import me.petr1furious.potatominigames.games.finals.FinalsGadgetState.Kind;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Persistent personal choices; a session applies them only at its next safe spawn. */
final class FinalsLoadouts implements Listener {
    record Choice(
            FinalsClass playerClass, FinalsWeapon weapon, Kind specialization, List<Kind> gadgets) {
        Choice {
            Objects.requireNonNull(playerClass);
            Objects.requireNonNull(weapon);
            if (weapon.playerClass() != playerClass)
                throw new IllegalArgumentException("Weapon class mismatch");
            Objects.requireNonNull(specialization);
            gadgets = List.copyOf(gadgets);
            if (!specialization.specialization() || !specialization.allowed(playerClass))
                throw new IllegalArgumentException("Specialization class mismatch");
            if (gadgets.size() != 3
                    || new HashSet<>(gadgets).size() != 3
                    || gadgets.stream()
                            .anyMatch(kind -> kind.specialization() || !kind.allowed(playerClass)))
                throw new IllegalArgumentException(
                        "Choose three distinct class-appropriate gadgets");
        }

        Choice(FinalsClass playerClass, FinalsWeapon weapon) {
            this(
                    playerClass,
                    weapon,
                    Kind.defaultSpecialization(playerClass),
                    Kind.defaultGadgets(playerClass));
        }

        Choice withClass(FinalsClass type) {
            return type == playerClass
                    ? this
                    : new Choice(type, FinalsWeapon.byClass(type).getFirst());
        }

        Choice withWeapon(FinalsWeapon next) {
            return next.playerClass() == playerClass
                    ? new Choice(playerClass, next, specialization, gadgets)
                    : new Choice(next.playerClass(), next);
        }

        Choice withSpecialization(Kind next) {
            return new Choice(playerClass, weapon, next, gadgets);
        }

        Choice withGadget(int slot, Kind next) {
            List<Kind> selected = new ArrayList<>(gadgets);
            selected.set(slot, next);
            return new Choice(playerClass, weapon, specialization, selected);
        }

        static Choice defaults() {
            return new Choice(FinalsClass.MEDIUM, FinalsWeapon.AKM);
        }
    }

    private final JavaPlugin plugin;
    private final Path file;
    private final Map<UUID, Choice> choices = new HashMap<>();
    private final Consumer<Player> changed;

    FinalsLoadouts(JavaPlugin plugin, Consumer<Player> changed) {
        this.plugin = plugin;
        this.changed = changed;
        this.file = plugin.getDataFolder().toPath().resolve("finals/loadouts.yml");
        if (Files.exists(file)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            for (String key : yaml.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    choices.put(id, readChoice(yaml, key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private final Map<UUID, Choice> practiceChoices = new HashMap<>();

    Choice choice(UUID id) {
        return practiceChoices.getOrDefault(id, choices.getOrDefault(id, Choice.defaults()));
    }

    void beginPractice(UUID id) { practiceChoices.put(id, Choice.defaults()); }
    void endPractice(UUID id) { practiceChoices.remove(id); }

    static Choice readChoice(YamlConfiguration yaml, String key) {
        FinalsClass type = FinalsClass.parse(yaml.getString(key + ".class"));
        if (type == null) return Choice.defaults();
        FinalsWeapon weapon = FinalsWeapon.parse(yaml.getString(key + ".weapon"));
        if (weapon == null || weapon.playerClass() != type)
            weapon = FinalsWeapon.byClass(type).getFirst();
        Kind specialization = Kind.defaultSpecialization(type);
        List<Kind> gadgets = Kind.defaultGadgets(type);
        try {
            Kind stored =
                    Kind.valueOf(
                            yaml.getString(key + ".specialization", "").toUpperCase(Locale.ROOT));
            if (stored.specialization() && stored.allowed(type)) specialization = stored;
        } catch (IllegalArgumentException ignored) {
        }
        try {
            List<Kind> stored =
                    yaml.getStringList(key + ".gadgets").stream()
                            .map(value -> Kind.valueOf(value.toUpperCase(Locale.ROOT)))
                            .toList();
            if (stored.size() == 3
                    && new HashSet<>(stored).size() == 3
                    && stored.stream()
                            .allMatch(kind -> !kind.specialization() && kind.allowed(type)))
                gadgets = stored;
        } catch (IllegalArgumentException ignored) {
        }
        return new Choice(type, weapon, specialization, gadgets);
    }

    static void writeChoice(YamlConfiguration yaml, String key, Choice value) {
        yaml.set(key + ".class", value.playerClass().id());
        yaml.set(key + ".weapon", value.weapon().id());
        yaml.set(key + ".specialization", value.specialization().id());
        yaml.set(key + ".gadgets", value.gadgets().stream().map(Kind::id).toList());
    }

    private void choose(Player player, Choice choice) throws CommandFailure {
        UUID id = player.getUniqueId();
        if (practiceChoices.containsKey(id)) {
            practiceChoices.put(id, choice);
            changed.accept(player);
            if (viewing(player)) paint(player, player.getOpenInventory().getTopInventory());
            return;
        }
        Choice previous = choices.put(id, choice);
        try {
            Files.createDirectories(file.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            choices.forEach((owner, value) -> writeChoice(yaml, owner.toString(), value));
            Path temp = Files.createTempFile(file.getParent(), "loadouts-", ".yml");
            try {
                yaml.save(temp.toFile());
                try {
                    Files.move(
                            temp,
                            file,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException error) {
            if (previous == null) choices.remove(id);
            else choices.put(id, previous);
            plugin.getLogger().warning("Could not save Finals loadouts: " + error.getMessage());
            throw new CommandFailure(I18n.tr("finals.loadout.save_failed"));
        }
        player.sendMessage(
                I18n.tr(
                        "finals.loadout.saved",
                        choice.playerClass().display(),
                        choice.weapon().display()));
        changed.accept(player);
        if (viewing(player)) paint(player, player.getOpenInventory().getTopInventory());
    }

    void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(
                Commands.literal("loadout")
                        .executes(ctx ->
                                GameCommands.run(ctx, c -> open(GameCommands.requirePlayer(c)))));
        root.then(
                Commands.literal("class")
                        .then(
                                Commands.argument("class", StringArgumentType.word())
                                        .suggests(
                                                Suggest.dynamic(() ->
                                                        List.of(
                                                                "S", "M", "L", "light", "medium",
                                                                "heavy")))
                                        .executes(ctx ->
                                                GameCommands.run(
                                                        ctx,
                                                        c -> {
                                                            Player player =
                                                                    GameCommands.requirePlayer(c);
                                                            FinalsClass type =
                                                                    FinalsClass.parse(
                                                                            StringArgumentType
                                                                                    .getString(
                                                                                            c,
                                                                                            "class"));
                                                            if (type == null)
                                                                throw new CommandFailure(
                                                                        I18n.tr(
                                                                                "finals.loadout.unknown_class"));
                                                            Choice current =
                                                                    choice(player.getUniqueId());
                                                            choose(player, current.withClass(type));
                                                        }))));
        root.then(
                Commands.literal("weapon")
                        .then(
                                Commands.argument("weapon", StringArgumentType.word())
                                        .suggests(
                                                Suggest.dynamic(() ->
                                                        Arrays.stream(FinalsWeapon.values())
                                                                .map(FinalsWeapon::id)
                                                                .toList()))
                                        .executes(ctx ->
                                                GameCommands.run(
                                                        ctx,
                                                        c -> {
                                                            Player player =
                                                                    GameCommands.requirePlayer(c);
                                                            FinalsWeapon weapon =
                                                                    FinalsWeapon.parse(
                                                                            StringArgumentType
                                                                                    .getString(
                                                                                            c,
                                                                                            "weapon"));
                                                            if (weapon == null)
                                                                throw new CommandFailure(
                                                                        I18n.tr(
                                                                                "finals.loadout.unknown_weapon"));
                                                            choose(
                                                                    player,
                                                                    choice(player.getUniqueId())
                                                                            .withWeapon(weapon));
                                                        }))));
    }

    private static final class Menu implements InventoryHolder {
        final UUID owner;
        final int gadgetSlot;
        Inventory inventory;

        Menu(UUID owner) {
            this(owner, -1);
        }

        Menu(UUID owner, int gadgetSlot) {
            this.owner = owner;
            this.gadgetSlot = gadgetSlot;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    static boolean isMenu(Inventory inventory) {
        return inventory.getHolder(false) instanceof Menu;
    }

    static boolean viewing(Player player) {
        return isMenu(player.getOpenInventory().getTopInventory());
    }

    ItemStack button(Player player) {
        return item(
                player,
                Material.NETHER_STAR,
                I18n.tr("finals.loadout.button"),
                List.of(I18n.tr("finals.loadout.button_lore")),
                false);
    }

    void open(Player player) {
        open(player, -1);
    }

    private void open(Player player, int gadgetSlot) {
        Menu holder = new Menu(player.getUniqueId(), gadgetSlot);
        holder.inventory =
                Bukkit.createInventory(
                        holder,
                        54,
                        I18n.render(
                                gadgetSlot < 0
                                        ? I18n.tr("finals.loadout.title")
                                        : I18n.tr("finals.loadout.gadgets_title", gadgetSlot + 1),
                                player));
        paint(player, holder.inventory);
        player.openInventory(holder.inventory);
    }

    private void paint(Player player, Inventory inventory) {
        inventory.clear();
        Choice selected = choice(player.getUniqueId());
        if (((Menu) inventory.getHolder(false)).gadgetSlot >= 0) {
            paintGadgets(
                    player, inventory, selected, ((Menu) inventory.getHolder(false)).gadgetSlot);
            return;
        }
        for (FinalsClass type : FinalsClass.values()) {
            Material icon =
                    switch (type) {
                        case LIGHT -> Material.FEATHER;
                        case MEDIUM -> Material.IRON_CHESTPLATE;
                        case HEAVY -> Material.ANVIL;
                    };
            inventory.setItem(
                    11 + type.ordinal() * 2,
                    item(
                            player,
                            icon,
                            type.display(),
                            List.of(
                                    I18n.tr(
                                            "finals.loadout.stats",
                                            type.health(),
                                            Math.round(type.walkSpeed() * 500)),
                                    I18n.tr("finals.loadout.custom_kit"),
                                    I18n.tr("finals.loadout.select_class")),
                            type == selected.playerClass()));
        }
        List<Kind> specializations = Kind.specializations(selected.playerClass());
        for (int i = 0; i < specializations.size(); i++) {
            Kind kind = specializations.get(i);
            inventory.setItem(
                    20 + i * 2,
                    item(
                            player,
                            kind.material(),
                            kind.display(),
                            List.of(
                                    I18n.tr("finals.gadget." + kind.id() + ".lore"),
                                    I18n.tr("finals.loadout.select_specialization")),
                            kind == selected.specialization()));
        }
        List<FinalsWeapon> weapons = FinalsWeapon.byClass(selected.playerClass());
        for (int i = 0; i < weapons.size(); i++) {
            FinalsWeapon weapon = weapons.get(i);
            inventory.setItem(
                    28 + i,
                    item(
                            player,
                            weapon.material(),
                            weapon.display(),
                            List.of(
                                    weapon.isGun()
                                            ? I18n.tr(
                                                    "finals.loadout.gun_stats",
                                                    weapon.damage(),
                                                    weapon.range(),
                                                    weapon.magazine())
                                            : I18n.tr(
                                                    "finals.loadout.melee_stats",
                                                    weapon.damage(),
                                                    weapon.range()),
                                    I18n.tr("finals.loadout.select_weapon")),
                            weapon == selected.weapon()));
        }
        inventory.setItem(
                45,
                item(
                        player,
                        Material.BOOK,
                        I18n.tr("finals.loadout.controls"),
                        List.of(
                                I18n.tr("finals.loadout.help"),
                                I18n.tr(practiceChoices.containsKey(player.getUniqueId())
                                        ? "finals.tutorial.practice_loadout" : "finals.loadout.next_spawn")),
                        false));
        inventory.setItem(
                49,
                item(player, Material.BARRIER, I18n.tr("finals.loadout.close"), List.of(), false));
        for (int i = 0; i < 3; i++) {
            Kind kind = selected.gadgets().get(i);
            inventory.setItem(
                    38 + i * 2,
                    item(
                            player,
                            kind.material(),
                            I18n.tr("finals.loadout.gadget_slot", i + 1, kind.display()),
                            List.of(
                                    I18n.tr("finals.gadget." + kind.id() + ".lore"),
                                    I18n.tr("finals.loadout.change_gadget")),
                            false));
        }
    }

    private void paintGadgets(Player player, Inventory inventory, Choice selected, int slot) {
        List<Kind> options = Kind.gadgets(selected.playerClass());
        for (int i = 0; i < options.size(); i++) {
            Kind kind = options.get(i);
            boolean elsewhere =
                    selected.gadgets().contains(kind) && selected.gadgets().get(slot) != kind;
            inventory.setItem(
                    i,
                    item(
                            player,
                            kind.material(),
                            kind.display(),
                            List.of(
                                    I18n.tr("finals.gadget." + kind.id() + ".lore"),
                                    I18n.tr(
                                            elsewhere
                                                    ? "finals.loadout.already_equipped"
                                                    : "finals.loadout.select_gadget")),
                            selected.gadgets().get(slot) == kind));
        }
        inventory.setItem(
                49, item(player, Material.ARROW, I18n.tr("finals.loadout.back"), List.of(), false));
    }

    private static ItemStack item(
            Player player,
            Material material,
            Component name,
            List<Component> lore,
            boolean selected) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(
                    name.colorIfAbsent(selected ? NamedTextColor.GREEN : NamedTextColor.GOLD));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(selected);
            meta.setUnbreakable(true);
        });
        return I18n.item(item, player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !menu.owner.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        if (slot == 49) {
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (player.isOnline()
                                        && player.getOpenInventory().getTopInventory()
                                                == menu.inventory) {
                                    if (menu.gadgetSlot >= 0) open(player);
                                    else player.closeInventory();
                                }
                            });
            return;
        }
        Choice previous = choice(player.getUniqueId());
        Choice next = null;
        if (menu.gadgetSlot >= 0) {
            List<Kind> options = Kind.gadgets(previous.playerClass());
            if (slot < 0 || slot >= options.size()) return;
            Kind selected = options.get(slot);
            if (previous.gadgets().contains(selected)
                    && previous.gadgets().get(menu.gadgetSlot) != selected) {
                player.sendMessage(I18n.tr("finals.loadout.already_equipped"));
                return;
            }
            next = previous.withGadget(menu.gadgetSlot, selected);
        } else if (slot == 38 || slot == 40 || slot == 42) {
            int selectedSlot = (slot - 38) / 2;
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (player.isOnline()
                                        && player.getOpenInventory().getTopInventory()
                                                == menu.inventory) open(player, selectedSlot);
                            });
            return;
        } else if (slot == 20 || slot == 22 || slot == 24) {
            List<Kind> options = Kind.specializations(previous.playerClass());
            int index = (slot - 20) / 2;
            if (index < options.size()) next = previous.withSpecialization(options.get(index));
        } else if (slot == 11 || slot == 13 || slot == 15) {
            FinalsClass type = FinalsClass.values()[(slot - 11) / 2];
            next = previous.withClass(type);
        } else if (slot >= 28 && slot <= 32) {
            next = previous.withWeapon(FinalsWeapon.byClass(previous.playerClass()).get(slot - 28));
        }
        if (next != null) {
            try {
                choose(player, next);
            } catch (CommandFailure error) {
                player.sendMessage(error.text());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isMenu(event.getInventory())) event.setCancelled(true);
    }
}
