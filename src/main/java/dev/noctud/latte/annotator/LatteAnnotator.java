package dev.noctud.latte.annotator;

import com.intellij.lang.annotation.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.intentions.*;
import com.intellij.psi.util.PsiTreeUtil;
import dev.noctud.latte.settings.LatteTagSettings;
import dev.noctud.latte.utils.LatteBlockUtil;
import org.jetbrains.annotations.NotNull;
import dev.noctud.latte.intentions.AddCustomAttrOnlyMacro;
import dev.noctud.latte.intentions.AddCustomPairMacro;
import dev.noctud.latte.intentions.AddCustomUnpairedMacro;
import dev.noctud.latte.psi.*;

import java.util.Set;

/**
 * Annotator is mostly used to check semantic rules which can not be easily checked during parsing.
 */
public class LatteAnnotator implements Annotator {
    /**
     * The union of the {syntax} arguments accepted anywhere in the supported Latte range, not
     * the set of any single version: "latte" is the default mode name in 2.11 and in 3.0.0 to
     * 3.0.1, "single" replaced it in 3.0.24, and neither name is accepted in between. The plugin
     * cannot yet tell which version a project uses, and a mode that is correct somewhere in the
     * range must not be reported as an error.
     */
    private static final Set<String> VALID_SYNTAX_MODES = Set.of("off", "double", "single", "latte");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element instanceof LatteMacroClassic) {
            checkMacroClassic((LatteMacroClassic) element, holder);

        } else if (element instanceof LatteNetteAttr) {
            checkNetteAttr((LatteNetteAttr) element, holder);

        } else if (element instanceof LeafPsiElement && element.getParent().getLastChild() instanceof PsiErrorElement) {
            LeafPsiElement leaf = (LeafPsiElement) element;
            if (leaf.getElementType() == LatteTypes.T_MACRO_OPEN_TAG_OPEN || leaf.getElementType() == LatteTypes.T_MACRO_CLOSE_TAG_OPEN) {
                createErrorAnnotation(holder, "Malformed tag. Missing closing }");
            }
        }
    }

    private void checkNetteAttr(@NotNull LatteNetteAttr element, @NotNull AnnotationHolder holder) {
        PsiElement attrName = element.getAttrName();
        String tagName = attrName.getText();
        boolean prefixed = false;

        if (tagName.startsWith("n:inner-")) {
            prefixed = true;
            tagName = tagName.substring(8);
        } else if (tagName.startsWith("n:tag-")) {
            prefixed = true;
            tagName = tagName.substring(6);
        } else {
            tagName = tagName.substring(2);
        }

        Project project = element.getProject();
        LatteTagSettings macro = LatteConfiguration.getInstance(project).getTag(tagName, element);
        if (macro == null || macro.getType() == LatteTagSettings.Type.UNPAIRED) {
            String absence = LatteConfiguration.getInstance(project).whyTagIsAbsent(tagName, element);
            String message = macro == null && absence != null
                ? "Attribute tag " + attrName.getText() + " " + absence
                : "Unknown attribute tag " + attrName.getText();
            AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, message)
                .range(attrName)
                .withFix(new AddCustomPairMacro(tagName));
            if (!prefixed) {
                builder = builder.withFix(new AddCustomAttrOnlyMacro(tagName));
            }
            builder.create();

        } else if (prefixed && macro.getType() != LatteTagSettings.Type.PAIR && macro.getType() != LatteTagSettings.Type.AUTO_EMPTY) {
            createErrorAnnotation(holder, attrName, "Attribute tag n:" + tagName + " can not be used with prefix.");
        }

        if (tagName.equals("syntax")) {
            LatteNetteAttrValue attrValue = element.getAttrValue();
            if (attrValue != null) {
                LatteMacroContent content = attrValue.getMacroContent();
                if (content != null) {
                    String mode = content.getText().trim();
                    if (!VALID_SYNTAX_MODES.contains(mode)) {
                        createErrorAnnotation(holder, content, "Invalid syntax mode '" + mode + "'. Expected: off, double, single, or latte");
                    }
                }
            }
        }
    }

    private void checkMacroClassic(@NotNull LatteMacroClassic element, @NotNull AnnotationHolder holder) {
        LatteMacroTag openTag = element.getOpenTag();
        LatteMacroTag closeTag = element.getCloseTag();

        String openTagName = openTag.getMacroName();
        LatteTagSettings macro = LatteConfiguration.getInstance(element.getProject()).getTag(openTagName, element);
        if (macro == null || macro.getType() == LatteTagSettings.Type.ATTR_ONLY) {
            boolean isOk = false;
            LatteMacroContent content = openTag.getMacroContent();
            if (content != null) {
                LattePhpContent phpContent = content.getFirstPhpContent();
                if (phpContent != null && phpContent.getFirstChild() instanceof LattePhpVariable) {
                    isOk = true;
                }
            }

            if (macro == null && isPrintedConstant(openTagName)) {
                isOk = true;
            }

            if (!isOk) {
                if (macro != null) {
                    createErrorAnnotation(holder, openTag, "Can not use n:" + openTagName + " attribute as normal tag");
                    if (closeTag != null) {
                        createErrorAnnotation(holder, closeTag, "Tag n:" + openTagName + " can not be used as pair tag");
                    }

                } else {
                    String absence = LatteConfiguration.getInstance(element.getProject())
                        .whyTagIsAbsent(openTagName, element);
                    String message = absence == null
                        ? "Unknown tag {" + openTagName + "}"
                        : "Tag {" + openTagName + "} " + absence;
                    AnnotationBuilder annotation = holder.newAnnotation(HighlightSeverity.ERROR, message).range(openTag);
                    annotation = annotation.withFix(new AddCustomPairMacro(openTagName));
                    annotation = annotation.withFix(new AddCustomUnpairedMacro(openTagName));
                    annotation.create();
                }
            }
        }

        if (openTagName.equals("syntax")) {
            checkSyntaxModeArgument(openTag, holder);

        } else if (openTagName.equals("include")) {
            checkIncludedBlockKeyword(openTag, holder);
        }

        String closeTagName = closeTag != null ? closeTag.getMacroName() : null;
        if (closeTagName != null && !closeTagName.isEmpty() && !closeTagName.equals(openTagName)) {
            createErrorAnnotation(holder, closeTag, "Unexpected {/" + closeTagName + "}, expected {/" + openTagName + "}");
        }

        if (
            macro != null
                && closeTag == null
                && ((element instanceof LattePairMacro && macro.getType() == LatteTagSettings.Type.AUTO_EMPTY) || macro.getType() == LatteTagSettings.Type.PAIR)
        ) {
            final int[] unclosed = {0};
            openTag.getParent().acceptChildren(new PsiRecursiveElementWalkingVisitor() {
                @Override
                public void visitElement(@NotNull PsiElement element) {
                    if (element instanceof LattePairMacro) {
                        LatteMacroTag tag = ((LattePairMacro) element).getOpenTag();
                        if (tag.getMacroName().equals("block") && ((LattePairMacro) element).getCloseTag() == null) {
                            unclosed[0]++;
                        } else {
                            super.visitElement(element);
                        }

                    } else {
                        super.visitElement(element);
                    }
                }
            });
            //PsiElement el = PsiTreeUtil.getChildOfAnyType(openTag.getParent(), LattePairMacro.class);
            if (!macro.isTagBlock() || unclosed[0] > 0) {
                createErrorAnnotation(holder, openTag, "Unclosed tag " + openTagName);
            }
        }
    }

    static boolean isValidSyntaxMode(@NotNull String mode) {
        return VALID_SYNTAX_MODES.contains(mode);
    }

    /**
     * Whether {@code {NAME}} prints a constant rather than opening a tag that does not exist.
     *
     * <p>Latte reads the content of a tag it does not know as an expression, and a bare identifier
     * in an expression is a constant fetch: {@code {PHP_EOL}} and {@code {\PHP_EOL}} are both valid
     * in every version of the supported range (docs/latte/latte-3.1.md). The lexer hands the name
     * over as a tag name either way and cannot tell the two apart, so the spelling decides. PHP
     * constants are written in upper case and no tag Latte or a Nette bridge registers is, which
     * {@code LatteTagRegistryTest} holds; a tag the project registered itself is found in the
     * configuration before this is asked.
     *
     * <p>Getting it wrong in this direction costs an unreported typo in a tag name spelled in upper
     * case. Getting it wrong the other way paints a correct template with an error.
     */
    private static boolean isPrintedConstant(@NotNull String name) {
        if (name.isEmpty() || name.charAt(0) < 'A' || name.charAt(0) > 'Z') {
            return false;
        }

        for (int i = 1; i < name.length(); i++) {
            char character = name.charAt(i);
            if ((character < 'A' || character > 'Z') && (character < '0' || character > '9') && character != '_') {
                return false;
            }
        }
        return true;
    }

    private void checkSyntaxModeArgument(@NotNull LatteMacroTag tag, @NotNull AnnotationHolder holder) {
        LatteMacroContent content = tag.getMacroContent();
        if (content == null) {
            createErrorAnnotation(holder, tag, "Missing syntax mode. Expected: off, double, single, or latte");
            return;
        }
        String mode = content.getText().trim();
        if (!isValidSyntaxMode(mode)) {
            createErrorAnnotation(holder, content, "Invalid syntax mode '" + mode + "'. Expected: off, double, single, or latte");
        }
    }

    /**
     * {@code {include parent}} and {@code {include this}} render the block the tag is written in,
     * so Latte refuses them outside a block while compiling, and refuses {@code parent} while
     * rendering when no template above defines that block.
     *
     * <p>The second one is only reported where it can be proven. A template that names no parent of
     * its own is given the presenter's layout at render time, and its blocks then have a parent
     * this cannot see - saying they have none would be a guess, and a guess here reads as a broken
     * template.
     */
    private void checkIncludedBlockKeyword(@NotNull LatteMacroTag tag, @NotNull AnnotationHolder holder) {
        LatteMacroContent content = tag.getMacroContent();
        if (content == null) {
            return;
        }

        LatteFilePath keyword = PsiTreeUtil.findChildOfType(content, LatteFilePath.class);
        if (keyword == null || !LatteBlockUtil.isBlockKeyword(keyword.getFilePath())) {
            return;
        }

        if (!LatteBlockUtil.hasEnclosingBlock(keyword)) {
            createErrorAnnotation(holder, tag, "Cannot include " + keyword.getFilePath() + " block outside of any block.");
            return;
        }

        String blockName = LatteBlockUtil.findEnclosingBlockName(keyword);
        if (
            blockName == null
                || !keyword.getFilePath().equals(LatteBlockUtil.KEYWORD_PARENT)
                || !(tag.getContainingFile() instanceof LatteFile file)
        ) {
            return;
        }

        if (LatteBlockUtil.isParentChainComplete(file) && LatteBlockUtil.findParentBlock(file, blockName) == null) {
            holder.newAnnotation(HighlightSeverity.WARNING, "Cannot include undefined parent block '" + blockName + "'.")
                .range(tag)
                .create();
        }
    }

    private void createErrorAnnotation(final @NotNull AnnotationHolder holder, final @NotNull String message) {
        holder.newAnnotation(HighlightSeverity.ERROR, message).create();
    }

    private void createErrorAnnotation(
        final @NotNull AnnotationHolder holder,
        final @NotNull PsiElement element,
        final @NotNull String message
    ) {
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(element).create();
    }
}
