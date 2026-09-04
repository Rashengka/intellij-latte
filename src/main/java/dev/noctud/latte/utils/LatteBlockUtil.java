package dev.noctud.latte.utils;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import dev.noctud.latte.psi.LatteFile;
import dev.noctud.latte.psi.LatteHtmlPairTag;
import dev.noctud.latte.psi.LatteHtmlTagContent;
import dev.noctud.latte.psi.LatteMacroClassic;
import dev.noctud.latte.psi.LatteMacroContent;
import dev.noctud.latte.psi.LatteMacroTag;
import dev.noctud.latte.psi.LatteNetteAttr;
import dev.noctud.latte.psi.LatteNetteAttrValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Blocks, the templates they are inherited from, and the two keywords that name neither a file nor
 * a block.
 *
 * <p>{@code {include parent}} and {@code {include this}} stand for the block the tag is written in;
 * {@code parent} takes it from the template the current one extends. Latte refuses both outside a
 * named block at compile time and refuses an undefined parent block when the template renders.
 *
 * <p>Everything here answers "can this be proven from the sources", not "is this likely". A
 * template that declares no {@code {extends}} is the case that matters: Nette gives it the
 * presenter's layout while rendering, so its blocks do have a parent that no amount of reading the
 * template can find. Only a template that names its parent itself has a chain this class will
 * follow, and only a chain with no {@code {import}} and no dynamic block name is complete enough to
 * say a block is missing from it.
 */
public final class LatteBlockUtil {

    public static final String KEYWORD_PARENT = "parent";
    public static final String KEYWORD_THIS = "this";

    /**
     * Tags that define a block by name. {@code {snippet}} and {@code {embed}} are blocks too but
     * are left out on purpose - see {@link #BLOCK_CONTEXT_TAGS}.
     */
    private static final List<String> BLOCK_TAGS = List.of("block", "define");

    /**
     * Tags that put the code inside them in a block of some kind. A wider set than
     * {@link #BLOCK_TAGS}: whether Latte counts each of them as the enclosing block of
     * {@code {include parent}} differs between the supported lines, so they only ever stop the
     * search - they never make the plugin claim to know which block it is standing in.
     */
    private static final List<String> BLOCK_CONTEXT_TAGS = List.of("block", "define", "snippet", "snippetArea", "embed");

    private static final List<String> PARENT_TAGS = List.of("extends", "layout");

    private static final int MAX_CHAIN_LENGTH = 16;

    private LatteBlockUtil() {
    }

    public static boolean isBlockKeyword(@NotNull String text) {
        return text.equals(KEYWORD_PARENT) || text.equals(KEYWORD_THIS);
    }

    /**
     * The name of the block the element is written in, or null when there is none or when the name
     * cannot be read - a dynamic {@code {block $name}}, an unnamed {@code {block}}, a tag from
     * {@link #BLOCK_CONTEXT_TAGS} that does not name a block on its own.
     */
    public static @Nullable String findEnclosingBlockName(@NotNull PsiElement element) {
        PsiElement block = findEnclosingBlock(element);
        if (block instanceof LatteMacroTag tag && BLOCK_TAGS.contains(tag.getMacroName())) {
            return readBlockName(tag.getMacroContent());
        }
        if (block instanceof LatteNetteAttr attr && BLOCK_TAGS.contains(attributeTagName(attr))) {
            LatteNetteAttrValue value = attr.getAttrValue();
            return value == null ? null : readBlockName(value.getMacroContent());
        }
        return null;
    }

    /**
     * Whether the element stands inside anything Latte treats as a block. False is the only case
     * Latte is certain to refuse, so it is the only case worth reporting.
     */
    public static boolean hasEnclosingBlock(@NotNull PsiElement element) {
        return findEnclosingBlock(element) != null;
    }

    /**
     * The tag defining the block of that name in the templates the file extends, searched from the
     * closest parent upwards. Null when there is no such block or when the chain cannot be
     * followed.
     */
    public static @Nullable PsiElement findParentBlock(@NotNull LatteFile file, @NotNull String blockName) {
        for (LatteFile parent : collectParents(file)) {
            PsiElement block = findBlock(parent, blockName);
            if (block != null) {
                return block;
            }
        }
        return null;
    }

    /**
     * Whether the templates the file inherits from are all readable from here: the file names its
     * parent, every parent in the chain resolves, none of them pulls in blocks with
     * {@code {import}} or {@code {embed}}, and no block in them is named by an expression. Only
     * then does a block missing from the chain mean the block is missing.
     */
    public static boolean isParentChainComplete(@NotNull LatteFile file) {
        if (findParentTag(file) == null) {
            return false;
        }

        List<LatteFile> chain = collectParents(file);
        if (chain.isEmpty()) {
            return false;
        }

        LatteFile last = chain.get(chain.size() - 1);
        LatteMacroTag unresolved = findParentTag(last);
        if (unresolved != null && !isNoParent(unresolved)) {
            return false;
        }

        for (LatteFile template : chain) {
            if (hasUnreadableBlocks(template)) {
                return false;
            }
        }
        return !hasUnreadableBlocks(file);
    }

    private static @Nullable PsiElement findEnclosingBlock(@NotNull PsiElement element) {
        for (PsiElement current = element.getParent(); current != null && !(current instanceof PsiFile); current = current.getParent()) {
            if (current instanceof LatteMacroClassic macro) {
                LatteMacroTag openTag = macro.getOpenTag();
                if (BLOCK_CONTEXT_TAGS.contains(openTag.getMacroName())) {
                    return openTag;
                }

            } else if (current instanceof LatteHtmlPairTag pairTag) {
                LatteNetteAttr attr = findBlockAttribute(pairTag);
                if (attr != null) {
                    return attr;
                }
            }
        }
        return null;
    }

    private static @Nullable LatteNetteAttr findBlockAttribute(@NotNull LatteHtmlPairTag pairTag) {
        LatteHtmlTagContent content = pairTag.getHtmlOpenTag().getHtmlTagContent();
        if (content == null) {
            return null;
        }

        for (LatteNetteAttr attr : content.getNetteAttrList()) {
            if (BLOCK_CONTEXT_TAGS.contains(attributeTagName(attr))) {
                return attr;
            }
        }
        return null;
    }

    private static @NotNull String attributeTagName(@NotNull LatteNetteAttr attr) {
        return attr.getAttrName().getText()
            .replace("n:inner-", "")
            .replace("n:tag-", "")
            .replace("n:", "");
    }

    /**
     * The first argument of a block tag, without the {@code local} modifier and without the
     * {@code #} some spellings put in front of the name. Null for a name the plugin cannot read.
     */
    private static @Nullable String readBlockName(@Nullable LatteMacroContent content) {
        if (content == null) {
            return null;
        }

        String name = firstArgument(content.getText());
        if (name.equals("local")) {
            name = firstArgument(content.getText().trim().substring("local".length()));
        }
        if (name.startsWith("#")) {
            name = name.substring(1);
        }

        return name.matches("[\\w-]+") ? name : null;
    }

    private static @NotNull String firstArgument(@NotNull String text) {
        String trimmed = text.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char character = trimmed.charAt(i);
            if (Character.isWhitespace(character) || character == ',' || character == '|') {
                return trimmed.substring(0, i);
            }
        }
        return trimmed;
    }

    /**
     * The templates the file extends, closest first. Stops at the first parent that cannot be
     * resolved, so a chain that ends early is shorter rather than wrong.
     */
    private static @NotNull List<LatteFile> collectParents(@NotNull LatteFile file) {
        List<LatteFile> parents = new ArrayList<>();
        Set<VirtualFile> visited = new HashSet<>();
        VirtualFile start = file.getOriginalFile().getVirtualFile();
        if (start != null) {
            visited.add(start);
        }

        LatteFile current = file;
        for (int depth = 0; depth < MAX_CHAIN_LENGTH; depth++) {
            LatteMacroTag parentTag = findParentTag(current);
            if (parentTag == null || isNoParent(parentTag)) {
                break;
            }

            LatteFile parent = resolveTemplate(current, readTemplatePath(parentTag));
            if (parent == null) {
                break;
            }

            VirtualFile virtual = parent.getOriginalFile().getVirtualFile();
            if (virtual != null && !visited.add(virtual)) {
                break;
            }

            parents.add(parent);
            current = parent;
        }

        return parents;
    }

    private static @Nullable LatteMacroTag findParentTag(@NotNull LatteFile file) {
        for (LatteMacroClassic macro : PsiTreeUtil.findChildrenOfType(file, LatteMacroClassic.class)) {
            LatteMacroTag openTag = macro.getOpenTag();
            if (PARENT_TAGS.contains(openTag.getMacroName())) {
                return openTag;
            }
        }
        return null;
    }

    /**
     * {@code {extends none}} and {@code {layout none}} say the template has no parent at all.
     */
    private static boolean isNoParent(@NotNull LatteMacroTag tag) {
        LatteMacroContent content = tag.getMacroContent();
        return content != null && content.getText().trim().equals("none");
    }

    /**
     * The file a {@code {extends}} names, or null when it is named by an expression. Only a plain
     * quoted path counts - anything else is a value the plugin cannot follow.
     */
    private static @Nullable String readTemplatePath(@NotNull LatteMacroTag tag) {
        LatteMacroContent content = tag.getMacroContent();
        if (content == null) {
            return null;
        }

        String text = content.getText().trim();
        if (text.length() < 3) {
            return null;
        }

        char quote = text.charAt(0);
        if ((quote != '\'' && quote != '"') || text.charAt(text.length() - 1) != quote) {
            return null;
        }

        String path = text.substring(1, text.length() - 1);
        return path.isEmpty() || path.contains("$") || path.contains(String.valueOf(quote)) ? null : path;
    }

    private static @Nullable LatteFile resolveTemplate(@NotNull LatteFile from, @Nullable String path) {
        if (path == null) {
            return null;
        }

        VirtualFile directory = from.getOriginalFile().getVirtualFile();
        directory = directory == null ? null : directory.getParent();
        if (directory == null) {
            return null;
        }

        VirtualFile target = directory.findFileByRelativePath(path.startsWith("/") ? path.substring(1) : path);
        if (target == null || target.isDirectory()) {
            return null;
        }

        PsiFile psiFile = PsiManager.getInstance(from.getProject()).findFile(target);
        return psiFile instanceof LatteFile latteFile ? latteFile : null;
    }

    private static @Nullable PsiElement findBlock(@NotNull LatteFile file, @NotNull String blockName) {
        for (LatteMacroClassic macro : PsiTreeUtil.findChildrenOfType(file, LatteMacroClassic.class)) {
            LatteMacroTag openTag = macro.getOpenTag();
            if (BLOCK_TAGS.contains(openTag.getMacroName()) && blockName.equals(readBlockName(openTag.getMacroContent()))) {
                return openTag;
            }
        }

        for (LatteNetteAttr attr : PsiTreeUtil.findChildrenOfType(file, LatteNetteAttr.class)) {
            if (!BLOCK_TAGS.contains(attributeTagName(attr))) {
                continue;
            }
            LatteNetteAttrValue value = attr.getAttrValue();
            if (value != null && blockName.equals(readBlockName(value.getMacroContent()))) {
                return attr;
            }
        }

        return null;
    }

    /**
     * Whether the template brings in blocks the plugin cannot enumerate: from another file with
     * {@code {import}} or {@code {embed}}, or under a name that is only known while rendering.
     */
    private static boolean hasUnreadableBlocks(@NotNull LatteFile file) {
        for (LatteMacroClassic macro : PsiTreeUtil.findChildrenOfType(file, LatteMacroClassic.class)) {
            LatteMacroTag openTag = macro.getOpenTag();
            String macroName = openTag.getMacroName();
            if (macroName.equals("import") || macroName.equals("embed")) {
                return true;
            }
            if (BLOCK_TAGS.contains(macroName) && readBlockName(openTag.getMacroContent()) == null) {
                return true;
            }
        }

        for (LatteNetteAttr attr : PsiTreeUtil.findChildrenOfType(file, LatteNetteAttr.class)) {
            if (!BLOCK_TAGS.contains(attributeTagName(attr))) {
                continue;
            }
            LatteNetteAttrValue value = attr.getAttrValue();
            if (value == null || readBlockName(value.getMacroContent()) == null) {
                return true;
            }
        }

        return false;
    }
}
