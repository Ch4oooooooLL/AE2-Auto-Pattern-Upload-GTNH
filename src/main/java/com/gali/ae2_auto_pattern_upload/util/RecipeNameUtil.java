package com.gali.ae2_auto_pattern_upload.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.gali.ae2_auto_pattern_upload.MyMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import cpw.mods.fml.common.Loader;

/**
 * 配方名称映射工具，兼容 1.7.10 环境。
 */
public final class RecipeNameUtil {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private static final Map<String, String> RAW_MAPPINGS = new HashMap<String, String>();
    private static final Map<String, String> LOOKUP_MAPPINGS = new HashMap<String, String>();

    private static final Path CONFIG_FILE;

    private static final Pattern CAMEL_CASE_SPLITTER = Pattern.compile("(?<!^)([A-Z])");

    private static String lastRecipeName = null;
    private static String lastRawRecipeId = null;

    static {
        Path configDir = Loader.instance()
            .getConfigDir()
            .toPath();
        CONFIG_FILE = configDir.resolve("ae2_auto_pattern_upload")
            .resolve("recipe_names.json");
        loadMappings();
    }

    private RecipeNameUtil() {}

    public static synchronized void setLastRecipeName(String name) {
        lastRecipeName = name;
    }

    public static synchronized void setLastRawRecipeId(String rawId) {
        lastRawRecipeId = rawId;
    }

    public static synchronized String getLastRecipeName() {
        return lastRecipeName;
    }

    public static synchronized String getLastRawRecipeId() {
        return lastRawRecipeId;
    }

    public static synchronized void clearLastRecipeName() {
        lastRecipeName = null;
        lastRawRecipeId = null;
    }

    public static synchronized boolean addOrUpdateMapping(String key, String value) {
        if (key == null || key.trim()
            .isEmpty()
            || value == null
            || value.trim()
                .isEmpty()) {
            return false;
        }
        RAW_MAPPINGS.put(key.trim(), value.trim());
        LOOKUP_MAPPINGS.put(normalizeKey(key), value.trim());
        saveMappings();
        return true;
    }

    public static synchronized int removeMappingsByCnValue(String cnValue) {
        if (cnValue == null || cnValue.trim()
            .isEmpty()) {
            return 0;
        }
        String target = cnValue.trim();
        int removed = 0;
        Iterator<Map.Entry<String, String>> iterator = RAW_MAPPINGS.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (Objects.equals(entry.getValue(), target)) {
                iterator.remove();
                LOOKUP_MAPPINGS.remove(normalizeKey(entry.getKey()));
                removed++;
            }
        }
        if (removed > 0) {
            saveMappings();
        }
        return removed;
    }

    public static synchronized void reloadMappings() {
        loadMappings();
    }

    public static synchronized Map<String, String> getMappingsView() {
        return Collections.unmodifiableMap(RAW_MAPPINGS);
    }

    private static synchronized void loadMappings() {
        RAW_MAPPINGS.clear();
        LOOKUP_MAPPINGS.clear();

        if (!Files.exists(CONFIG_FILE)) {
            writeTemplate();
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(
            Files.newInputStream(CONFIG_FILE),
            StandardCharsets.UTF_8)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.trim()
                    .isEmpty()) {
                    continue;
                }
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive()) {
                    String mapped = value.getAsString();
                    if (mapped != null && !mapped.trim()
                        .isEmpty()) {
                        RAW_MAPPINGS.put(key.trim(), mapped.trim());
                        LOOKUP_MAPPINGS.put(normalizeKey(key), mapped.trim());
                    }
                }
            }
        } catch (IOException e) {
            MyMod.LOG.warn(
                StatCollector.translateToLocalFormatted("ae2_auto_pattern_upload.error.read_mappings", e.getMessage()));
        }
    }

    private static void writeTemplate() {
        JsonObject template = new JsonObject();
        template.addProperty("example.crafting", "example_crafting");
        template.addProperty("example.processing", "example_processing");

        try {
            Path parent = CONFIG_FILE.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(CONFIG_FILE),
                StandardCharsets.UTF_8)) {
                writer.write(GSON.toJson(template));
            }
        } catch (IOException e) {
            MyMod.LOG.warn(
                StatCollector
                    .translateToLocalFormatted("ae2_auto_pattern_upload.error.create_template", e.getMessage()));
        }
    }

    private static void saveMappings() {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, String> entry : RAW_MAPPINGS.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        try {
            Path parent = CONFIG_FILE.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(CONFIG_FILE),
                StandardCharsets.UTF_8)) {
                writer.write(GSON.toJson(obj));
            }
        } catch (IOException e) {
            MyMod.LOG.warn(
                StatCollector
                    .translateToLocalFormatted("ae2_auto_pattern_upload.error.write_mappings", e.getMessage()));
        }
    }

    public static String mapCategoryUidToSearchKey(String categoryUid) {
        if (categoryUid == null || categoryUid.isEmpty()) {
            return null;
        }
        String normalized = categoryUid.trim()
            .toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        int dot = normalized.indexOf('.');
        String path;
        if (colon >= 0) {
            path = normalized.substring(colon + 1);
        } else if (dot >= 0) {
            path = normalized.substring(dot + 1);
        } else {
            path = normalized;
        }
        String mapped = LOOKUP_MAPPINGS.get(path);
        if (mapped != null && !mapped.isEmpty()) {
            return mapped;
        }
        return toDisplayString(path);
    }

    public static String deriveSearchKeyFromClassName(Object recipeObj) {
        if (recipeObj == null) {
            return null;
        }
        try {
            String simpleName = recipeObj.getClass()
                .getSimpleName();
            String packageName = recipeObj.getClass()
                .getPackage()
                .getName()
                .toLowerCase(Locale.ROOT);

            String token = CAMEL_CASE_SPLITTER.matcher(simpleName)
                .replaceAll(" $1")
                .replace("_", " ")
                .replace("-", " ")
                .trim()
                .toLowerCase(Locale.ROOT);

            token = token.replace(" recipe", "")
                .replace(" handler", "")
                .trim();

            String namespace = null;
            if (packageName.contains("gregtech")) {
                namespace = "gregtech";
            } else if (packageName.contains("gtceu")) {
                namespace = "gtceu";
            } else if (packageName.contains("thermal")) {
                namespace = "thermal";
            } else if (packageName.contains("botania")) {
                namespace = "botania";
            } else if (packageName.contains("immersive")) {
                namespace = "immersive";
            }

            if (namespace != null && !token.isEmpty()) {
                return namespace + " " + token;
            }
            if (!token.isEmpty()) {
                return token;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static void captureFromRecipeHandler(IRecipeHandler handler, int recipeIndex) {
        if (handler == null) {
            return;
        }
        // 保存原始的配方标识符（用于添加映射）
        String rawId = safeOverlayIdentifier(handler);
        if (rawId != null && !rawId.isEmpty()) {
            setLastRawRecipeId(rawId);
        } else {
            try {
                String recipeName = handler.getRecipeName();
                if (recipeName != null && !recipeName.trim()
                    .isEmpty()) {
                    setLastRawRecipeId(recipeName.trim());
                }
            } catch (Throwable ignored) {}
        }

        // 保存映射后的显示名称（用于搜索）
        String keyword = mapRecipeHandlerToSearchKey(handler, recipeIndex);
        if (keyword != null && !keyword.isEmpty()) {
            setLastRecipeName(keyword);
        }
    }

    /**
     * 生成搜索关键词：配方基名 + 编程电路号 + 按序的 NC（不消耗）物品。
     * 配方基名与 NC 物品名优先使用游戏运行时本地化结果（装汉化则中文、否则英文短名），
     * 没有可用名字时回退 注册名_meta。
     */
    public static String mapRecipeHandlerToSearchKey(IRecipeHandler handler, int recipeIndex) {
        if (handler == null) {
            return null;
        }
        String overlayId = null;
        try {
            overlayId = safeOverlayIdentifier(handler);
        } catch (Throwable ignored) {}

        String base = null;
        // 1) 用户手动映射优先（recipe_names.json）
        if (base == null && overlayId != null) {
            String mapped = mapStringToMapping(overlayId);
            if (mapped != null) {
                base = mapped;
            }
        }
        // 2) 游戏运行时本地化的配方标签名（GT 的 tab 名 = translateToLocal(unlocalizedName)）
        if (base == null) {
            base = localizedRecipeTabName(handler, overlayId);
        }
        // 3) 英文/未翻译回退
        if (base == null && overlayId != null) {
            base = toUnderscoreKey(overlayId);
        }
        if (base == null) {
            try {
                String recipeName = handler.getRecipeName();
                if (recipeName != null && !recipeName.trim()
                    .isEmpty()) {
                    base = toUnderscoreKey(recipeName);
                }
            } catch (Throwable ignored) {}
        }
        if (base == null) {
            base = toUnderscoreKey(
                handler.getClass()
                    .getSimpleName());
        }

        // 附加编程电路号与 NC 物品（按配方输入槽位顺序）
        List<PositionedStack> inputs = null;
        try {
            inputs = handler.getIngredientStacks(recipeIndex);
        } catch (Throwable ignored) {}

        StringBuilder sb = new StringBuilder(base);
        String circuit = extractCircuitToken(inputs);
        List<String> ncTokens = extractNonConsumedTokens(inputs);
        if (circuit != null && !circuit.isEmpty()) {
            sb.append('_')
                .append(circuit);
        }
        for (String nc : ncTokens) {
            sb.append('_')
                .append(nc);
        }
        return sb.toString();
    }

    /** 取配方标签名，仅当其是真实翻译（不是未命中的 key 本身）时采用。 */
    private static String localizedRecipeTabName(IRecipeHandler handler, String overlayId) {
        try {
            String tabName = handler.getRecipeTabName();
            if (tabName == null) {
                return null;
            }
            String trimmed = tabName.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            // 未翻译时 translateToLocal 返回 key 本身，不能当显示名用
            if (overlayId != null && trimmed.equals(overlayId)) {
                return null;
            }
            return cleanToken(trimmed);
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * 清洗为一个关键词片段：去格式码；含中文时原样保留（仅去空白），
     * 否则小写并把空格/括号/分隔符统一为下划线。
     */
    private static String cleanToken(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll("\u00a7.", "")
            .trim();
        if (s.isEmpty()) {
            return "";
        }
        boolean hasCjk = s.codePoints()
            .anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
        if (hasCjk) {
            return s.replaceAll("\\s+", "")
                .trim();
        }
        return toUnderscoreKey(s);
    }

    /** 从配方输入中提取编程电路号（gt.integrated_circuit 的 meta，0~24）。 */
    private static String extractCircuitToken(List<?> inputs) {
        if (inputs == null) {
            return null;
        }
        List<String> numbers = new ArrayList<String>();
        for (Object obj : inputs) {
            PositionedStack ps = asPositionedStack(obj);
            ItemStack stack = primaryStack(ps);
            if (stack == null || stack.getItem() == null) {
                continue;
            }
            if (isIntegratedCircuit(stack)) {
                numbers.add(String.valueOf(stack.getItemDamage()));
            }
        }
        return numbers.isEmpty() ? null : String.join("_", numbers);
    }

    /** 提取配方中不消耗（NC）的物品，按输入顺序返回显示名token（回退 注册名_meta）。 */
    private static List<String> extractNonConsumedTokens(List<?> inputs) {
        List<String> tokens = new ArrayList<String>();
        if (inputs == null) {
            return tokens;
        }
        for (Object obj : inputs) {
            PositionedStack ps = asPositionedStack(obj);
            if (ps == null) {
                continue;
            }
            // GT 的 NC 判定：chance==0（"必须存在但不消耗"）或物品 stackSize==0
            // （模具/模头/电路等在配方里都以 stackSize==0 表示不消耗，
            // 见 GTNEIDefaultHandler.FixedPositionedStack.isNotConsumed()）
            if (ps.getChance() != 0 && !hasZeroStackSize(ps)) {
                continue;
            }
            ItemStack stack = primaryStack(ps);
            if (stack == null || stack.getItem() == null) {
                continue;
            }
            // 编程电路已单独作为电路号输出，不重复计入
            if (isIntegratedCircuit(stack)) {
                continue;
            }
            // 流体显示栈不是真正的物品输入
            if (isFluidDisplay(stack)) {
                continue;
            }
            String registryName = Item.itemRegistry.getNameForObject(stack.getItem());
            if (registryName == null) {
                continue;
            }
            String display = cleanToken(stack.getDisplayName());
            tokens.add(display.isEmpty() ? registryName + "_" + stack.getItemDamage() : display);
        }
        return tokens;
    }

    /** GT 在配方输入中把"不消耗"表示为 stackSize==0（与 FixedPositionedStack.isNotConsumed 一致）。 */
    private static boolean hasZeroStackSize(PositionedStack ps) {
        if (ps.items != null) {
            for (ItemStack stack : ps.items) {
                if (stack != null && stack.stackSize == 0) {
                    return true;
                }
            }
        }
        return ps.item != null && ps.item.stackSize == 0;
    }

    /** 判断是否编程电路：以 GT 自己使用的 unlocalizedName 前缀为准，兼注册名兜底。 */
    private static boolean isIntegratedCircuit(ItemStack stack) {
        try {
            String unloc = stack.getUnlocalizedName();
            if (unloc != null && unloc.startsWith("gt.integrated_circuit")) {
                return true;
            }
        } catch (Throwable ignored) {}
        try {
            String registryName = Item.itemRegistry.getNameForObject(stack.getItem());
            return "gt.integrated_circuit".equals(registryName)
                || (registryName != null && registryName.endsWith("integrated_circuit"));
        } catch (Throwable ignored) {}
        return false;
    }

    /** 判断是否流体显示栈（GT 配方中流体输入以 Display_Fluid 物品占位）。 */
    private static boolean isFluidDisplay(ItemStack stack) {
        try {
            String unloc = stack.getUnlocalizedName();
            if (unloc != null && unloc.toLowerCase(Locale.ROOT)
                .contains("fluiddisplay")) {
                return true;
            }
        } catch (Throwable ignored) {}
        try {
            String registryName = Item.itemRegistry.getNameForObject(stack.getItem());
            return registryName != null && registryName.toLowerCase(Locale.ROOT)
                .contains("fluiddisplay");
        } catch (Throwable ignored) {}
        return false;
    }

    private static PositionedStack asPositionedStack(Object obj) {
        return obj instanceof PositionedStack ps ? ps : null;
    }

    private static ItemStack primaryStack(PositionedStack ps) {
        if (ps == null) {
            return null;
        }
        if (ps.item != null) {
            return ps.item;
        }
        if (ps.items != null && ps.items.length > 0) {
            return ps.items[0];
        }
        return null;
    }

    private static String mapStringToMapping(String raw) {
        if (raw == null || raw.trim()
            .isEmpty()) {
            return null;
        }
        String normalized = normalizeKey(raw);
        String mapped = LOOKUP_MAPPINGS.get(normalized);
        if (mapped != null && !mapped.isEmpty()) {
            return mapped;
        }
        return null;
    }

    private static String safeOverlayIdentifier(IRecipeHandler handler) {
        try {
            String id = handler.getOverlayIdentifier();
            if (id != null && !id.trim()
                .isEmpty()) {
                return id;
            }
        } catch (Throwable ignored) {}
        try {
            String tabName = handler.getRecipeTabName();
            if (tabName != null && !tabName.trim()
                .isEmpty()) {
                return tabName;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String normalizeKey(String key) {
        // 统一把 . _ - : 都替换成空格，然后合并多余空格
        String normalized = key.trim()
            .toLowerCase(Locale.ROOT)
            .replace('.', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(':', ' ')
            .replaceAll("\\s+", " ")
            .trim();
        return normalized;
    }

    private static String toDisplayString(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace('_', ' ')
            .replace('-', ' ')
            .replace('.', ' ')
            .replace(':', ' ');
        cleaned = CAMEL_CASE_SPLITTER.matcher(cleaned)
            .replaceAll(" $1");
        cleaned = cleaned.replaceAll("\\s+", " ")
            .trim();
        return cleaned;
    }

    /** 将所有分隔符与大小写边界统一为下划线，用于生成搜索关键词（如 gt_recipe_extruder）。 */
    private static String toUnderscoreKey(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim()
            .toLowerCase(Locale.ROOT);
        s = CAMEL_CASE_SPLITTER.matcher(s)
            .replaceAll(" $1");
        s = s.replace('.', '_')
            .replace('-', '_')
            .replace(':', '_')
            .replace('(', '_')
            .replace(')', '_');
        s = s.replaceAll("[ _]+", "_")
            .replaceAll("^_+|_+$", "");
        return s;
    }
}
