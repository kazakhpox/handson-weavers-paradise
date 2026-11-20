package net.handsonweavers.devtools;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationView;


/**
 * Builds a flat test world, puts an iron smithing anvil before the player, opens the station and works it with the mouse as a player
 * would, saving screenshots along the way to the folder in the system property {@code handson.shots}. Then quits.
 */
@Mod("handson_weavers_paradise_devtools")
public final class ShotDriver {
    private static final String WORLD = "handson_shots";
    
    private final Deque<Step> steps = new ArrayDeque<>();
    private int wait;
    
    private record Step(String name, BooleanSupplier until, Runnable action) {
    }

    public ShotDriver(IEventBus modBus) {
        if (System.getProperty("handson.shots") == null) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
        plan();
    }

    private void then(String name, int ticks, Runnable action) {
        steps.add(new Step(name, () -> true, action));
        steps.add(new Step(name + " wait", null, () -> wait = ticks));
    }

    private void when(String name, BooleanSupplier until, Runnable action) {
        steps.add(new Step(name, until, action));
    }

    private static final BlockPos JENNY = new BlockPos(0, -60, 0);
    private static final BlockPos LOOM = new BlockPos(2, -60, 0);
    private static final BlockPos DYES = new BlockPos(4, -60, 0);

    private void plan() {
        when("title", () -> Minecraft.getInstance().screen instanceof TitleScreen, this::createWorld);
        when("in world", () -> Minecraft.getInstance().player != null && Minecraft.getInstance().getSingleplayerServer() != null
                && Minecraft.getInstance().screen == null, () -> wait = 60);
        then("build", 50, this::build);
        then("look", 20, () -> look(2));
        then("world shot", 5, () -> shot("00_world"));

        // The spinning jenny: string and empty spools, then it spins.
        then("stand at jenny", 20, () -> standBefore(JENNY));
        then("fill jenny", 10, () -> fill(JENNY, new ItemStack(net.minecraft.world.item.Items.STRING, 20), 0,
                spool("empty_spool", 1), 1, spool("empty_spool", 1), 2, spool("empty_spool", 1), 3));
        then("aim jenny", 5, () -> lookAt(JENNY));
        then("open jenny", 70, ShotDriver::use);
        then("jenny shot", 40, () -> shot("01_jenny"));
        then("jenny later", 5, () -> shot("02_jenny_spinning"));
        then("leave jenny", 40, ShotDriver::leave);

        // The dyemaking table: three dyes, a bottle and water.
        then("stand at table", 20, () -> standBefore(DYES));
        then("fill table", 10, () -> fill(DYES, new ItemStack(net.minecraft.world.item.Items.RED_DYE, 4), 1,
                new ItemStack(net.minecraft.world.item.Items.YELLOW_DYE, 4), 5, new ItemStack(net.minecraft.world.item.Items.BLUE_DYE, 2), 9,
                new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, 3), 10,
                net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,
                        net.minecraft.world.item.alchemy.Potions.WATER), 11));
        then("aim table", 5, () -> lookAt(DYES));
        then("open table", 70, ShotDriver::use);
        then("table shot", 5, () -> shot("03_dyemaking"));
        then("leave table", 40, ShotDriver::leave);

        // The loom: six cotton spools, the shuttle clicked, rows typed.
        then("stand at loom", 20, () -> standBefore(LOOM));
        then("fill loom", 10, () -> fill(LOOM, spool("cotton_spool", 6), 0));
        then("aim loom", 5, () -> lookAt(LOOM));
        then("open loom", 70, ShotDriver::use);
        then("loom shot", 5, () -> shot("04_loom"));
        then("aim shuttle", 10, () -> aimAt(11.6F, 5.2F, 7.0F));
        then("click shuttle", 30, ShotDriver::click);
        then("board shot", 5, () -> shot("05_board"));
        for (int i = 0; i < 3; i++) {
            then("row " + i, 4, this::typeRow);
        }
        then("typing", 1, () -> typeArrows(2));
        then("typed shot", 10, () -> shot("06_typing"));
        then("finish clock", 60, () -> server(player -> {
            if (player.level().getBlockEntity(LOOM) instanceof xox.labvorty.weaversparadise.blocks.entities.ClothcraftingStationBlockEntity loom) {
                loom.setGameTime(1);
            }
        }));
        then("woven shot", 5, () -> {
            shot("07_woven");
            station().ifPresent(view -> System.out.println("[shots] output " + view.state().getOutput(
                    net.handsonweavers.station.WeaverStations.CLOTH) + " " + view.state().getOutput(
                    net.handsonweavers.station.WeaverStations.CLOTH).getComponentsPatch()));
        });
        then("leave loom", 40, ShotDriver::leave);

        // The dyeing barrel: a cotton shirt and a bottle of red dye.
        then("stand at barrel", 20, () -> standBefore(BARREL));
        then("fill barrel", 10, () -> fill(BARREL, spool("shirt_cotton", 1), 0, redDye(), 4));
        then("aim barrel", 5, () -> lookAt(BARREL));
        then("open barrel", 70, ShotDriver::use);
        then("barrel shot", 5, () -> shot("08_barrel"));
        then("aim stir", 5, () -> aimAt(4.0F, 18.0F, 12.0F));
        then("press stir", 3, () -> button(GLFW.GLFW_PRESS));
        for (int i = 0; i < 70; i++) {
            float angle = i * 0.6F;
            if (i == 15) {
                then("preview shot", 1, () -> shot("09_stirring"));
            }
            then("stir " + i, 2, () -> aimAt(8.0F + 3.5F * (float) Math.cos(angle), 14.7F, 8.0F + 3.5F * (float) Math.sin(angle)));
        }
        then("stir shot", 1, () -> shot("09b_stirred"));
        then("release stir", 20, () -> button(GLFW.GLFW_RELEASE));
        then("check barrel", 5, () -> server(player -> {
            if (player.level().getBlockEntity(BARREL) instanceof net.minecraft.world.Container barrel) {
                System.out.println("[shots] in the bath " + barrel.getItem(0) + " " + barrel.getItem(0).getComponentsPatch()
                        + " | preview " + barrel.getItem(1) + " | dye " + barrel.getItem(4).getComponentsPatch());
            }
        }));
        then("barrel after", 5, () -> shot("10_barrel_after"));
        then("quit", 5, () -> Minecraft.getInstance().stop());
    }

    private static final BlockPos BARREL = new BlockPos(6, -60, 0);

    private static ItemStack redDye() {
        ItemStack dye = spool("bottled_dye", 1);
        CompoundTag tag = new CompoundTag();
        tag.putInt("colorRedOne", 200);
        tag.putInt("colorGreenOne", 40);
        tag.putInt("colorBlueOne", 60);
        tag.putInt("colorRedTwo", 255);
        tag.putInt("colorGreenTwo", 255);
        tag.putInt("colorBlueTwo", 255);
        tag.putString("dyeType", "default");
        tag.putInt("amount", 10);
        dye.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        return dye;
    }

    private static void button(int action) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        invoke("onPress", new Class<?>[]{long.class, int.class, int.class, int.class}, window, 0, action, 0);
    }

    private static ItemStack spool(String name, int count) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("weaversparadise", name)), count);
    }

    /** Puts stacks into a block's container: stack, slot, stack, slot... */
    private static void fill(BlockPos pos, Object... stacksAndSlots) {
        server(player -> {
            if (player.level().getBlockEntity(pos) instanceof net.minecraft.world.Container container) {
                for (int i = 0; i < stacksAndSlots.length; i += 2) {
                    container.setItem((Integer) stacksAndSlots[i + 1], ((ItemStack) stacksAndSlots[i]).copy());
                }
                container.setChanged();
            }
        });
    }

    private static void standBefore(BlockPos pos) {
        server(player -> player.teleportTo(player.serverLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.6, 0.0F, 45.0F));
    }

    /** Turns the player's eyes on the middle of a block's lower half. */
    private static void lookAt(BlockPos pos) {
        Minecraft.getInstance().player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5));
    }

    private static void leave() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        }
    }

    /** Types the next row of the weaving right, key by key, as the arrow keys would. */
    private void typeRow() {
        typeArrows(5);
    }

    private void typeArrows(int count) {
        station().ifPresent(view -> {
            CompoundTag data = view.state().data();
            int score = data.getInt(net.handsonweavers.station.WeaverStations.SCORE);
            int[] row = net.handsonweavers.station.WeavingRows.row(data.getLong(net.handsonweavers.station.WeaverStations.SEED), score);
            int[] keys = {GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT};
            Minecraft mc = Minecraft.getInstance();
            for (int i = 0; i < count && mc.screen != null; i++) {
                mc.screen.keyPressed(keys[row[i]], 0, 0);
            }
            System.out.println("[shots] typed " + count + " at score " + score);
        });
    }

    /** The player stands 3 blocks from the row of stations on one side, looking at it. */
    private void look(int side) {
        server(player -> {
            float[] yaw = {180.0F, 90.0F, 0.0F, -90.0F};
            double[][] at = {{3.5, -60, 4.0}, {7.5, -60, 0.5}, {3.5, -60, -3.0}, {-0.5, -60, 0.5}};
            player.teleportTo(player.serverLevel(), at[side][0], at[side][1], at[side][2], yaw[side], 30.0F);
        });
    }

    private void tick() {
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = steps.peek();
        if (step == null) {
            return;
        }
        if (step.until() == null) {
            steps.poll();
            step.action().run();
            return;
        }
        if (step.until().getAsBoolean()) {
            steps.poll();
            System.out.println("[shots] " + step.name());
            step.action().run();
        }
    }

    private void createWorld() {
        Minecraft mc = Minecraft.getInstance();
        Path saves = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
        try {
            if (Files.exists(saves)) {
                try (var walk = Files.walk(saves)) {
                    walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions(), mc.screen);
    }

    /** A right click at what the crosshair is on, with an empty hand: the player uses the anvil as anybody would. */
    private static void use() {
        Minecraft mc = Minecraft.getInstance();
        mc.player.getInventory().selected = 4;
        System.out.println("[shots] looking at " + mc.hitResult);
        try {
            Method method = Minecraft.class.getDeclaredMethod("startUseItem");
            method.setAccessible(true);
            method.invoke(mc);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void server(java.util.function.Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayers().get(0)));
    }

    private void build() {
        server(player -> {
            ServerLevel level = player.serverLevel();
            level.setDayTime(6000);
            net.minecraft.world.level.block.Block[] blocks = {
                    xox.labvorty.weaversparadise.init.WeaversParadiseBlocks.SPINNING_JENNY.get(),
                    xox.labvorty.weaversparadise.init.WeaversParadiseBlocks.CLOTHCRAFTING_STATION.get(),
                    xox.labvorty.weaversparadise.init.WeaversParadiseBlocks.DYEMAKING_BLOCK.get(),
                    xox.labvorty.weaversparadise.init.WeaversParadiseBlocks.DYEING_BARREL_BLOCK.get()};
            for (int i = 0; i < blocks.length; i++) {
                BlockState state = blocks[i].defaultBlockState();
                if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                    state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
                }
                level.setBlockAndUpdate(new BlockPos(i * 2, -60, 0), state);
            }
        });
    }

    private static Optional<StationView> station() {
        return DiegesisClient.focusedStation();
    }

    /** Puts the mouse over a point of the scene. */
    private static void aimAt(float x, float y, float z) {
        station().ifPresent(view -> {
            Vec3 world = view.toWorld(x, y, z);
            Optional<Vector2f> screen = DiegesisClient.toScreen(world);
            if (screen.isEmpty()) {
                System.out.println("[shots] point off screen");
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            double scale = mc.getWindow().getGuiScale();
            double px = screen.get().x * scale;
            double py = screen.get().y * scale;
            long window = mc.getWindow().getWindow();
            GLFW.glfwSetCursorPos(window, px, py);
            invoke("onMove", new Class<?>[]{long.class, double.class, double.class}, window, px, py);
        });
    }

    private static void click() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        invoke("onPress", new Class<?>[]{long.class, int.class, int.class, int.class}, window, 0, GLFW.GLFW_PRESS, 0);
        invoke("onPress", new Class<?>[]{long.class, int.class, int.class, int.class}, window, 0, GLFW.GLFW_RELEASE, 0);
    }

    private static void invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method method = Minecraft.getInstance().mouseHandler.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(Minecraft.getInstance().mouseHandler, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        File dir = new File(System.getProperty("handson.shots"));
        dir.mkdirs();
        Screenshot.grab(dir, name + ".png", mc.getMainRenderTarget(), message -> System.out.println("[shots] " + message.getString()));
    }
}
