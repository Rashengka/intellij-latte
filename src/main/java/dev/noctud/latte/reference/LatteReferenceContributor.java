package dev.noctud.latte.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import dev.noctud.latte.LatteLanguage;
import dev.noctud.latte.reference.references.*;
import dev.noctud.latte.reference.references.*;
import dev.noctud.latte.reference.references.LattePhpClassReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import dev.noctud.latte.psi.*;

public class LatteReferenceContributor extends PsiReferenceContributor {
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(LatteTypes.PHP_VARIABLE),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    if (!(element instanceof LattePhpVariable)) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    PsiElement value = ((LattePhpVariable) element).getTextElement();
                    if (value != null && value.getTextLength() > 0) {
                        PsiReference reference = new LattePhpVariableReference((LattePhpVariable) element, new TextRange(0, value.getTextLength()));
                        return new PsiReference[]{reference};
                    }

                    return PsiReference.EMPTY_ARRAY;
                }
            });
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(LatteTypes.PHP_VARIABLE),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    if (!(element instanceof LattePhpVariable) || !((LattePhpVariable) element).isDefinition()) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    PsiElement value = ((LattePhpVariable) element).getTextElement();
                    if (value != null && value.getTextLength() > 0) {
                        return new PsiReference[]{
                            new LattePhpVariableReference((LattePhpVariable) element, new TextRange(0, value.getTextLength()))
                        };
                    }

                    return PsiReference.EMPTY_ARRAY;
                }
            });

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(LatteTypes.PHP_METHOD),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    if (!(element instanceof LattePhpMethod)) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    String methodName = ((LattePhpMethod) element).getMethodName();
                    if (methodName != null && methodName.length() > 0) {
                        int addition = element.getFirstChild().getNode().getElementType() == LatteTypes.T_PHP_NAMESPACE_RESOLUTION ? 1 : 0;
                        return new PsiReference[]{
                            new LattePhpMethodReference((LattePhpMethod) element, new TextRange(addition, methodName.length() + addition))
                        };
                    }
                    return PsiReference.EMPTY_ARRAY;
                }
            });

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(LatteTypes.PHP_PROPERTY),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    if (!(element instanceof LattePhpProperty)) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    PsiElement value = ((LattePhpProperty) element).getTextElement();
                    if (value != null && value.getTextLength() > 0) {
                        return new PsiReference[]{
                            new LattePhpPropertyReference((LattePhpProperty) element, new TextRange(0, value.getTextLength()))
                        };
                    }

                    return PsiReference.EMPTY_ARRAY;
                }
            });

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(LatteTypes.PHP_CONSTANT),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    if (!(element instanceof LattePhpConstant)) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    PsiElement value = ((LattePhpConstant) element).getTextElement();
                    if (value != null && value.getTextLength() > 0) {
                        return new PsiReference[]{
                            new LattePhpConstantReference((LattePhpConstant) element, new TextRange(0, value.getTextLength()))
                        };
                    }

                    return PsiReference.EMPTY_ARRAY;
                }
            });

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(LatteTypes.PHP_STATIC_VARIABLE),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    if (!(element instanceof LattePhpStaticVariable)) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    PsiElement value = ((LattePhpStaticVariable) element).getTextElement();
                    if (value != null && value.getTextLength() > 0) {
                        return new PsiReference[]{
                            new LattePhpStaticVariableReference((LattePhpStaticVariable) element, new TextRange(0, value.getTextLength()))
                        };
                    }

                    return PsiReference.EMPTY_ARRAY;
                }
            });

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(LatteTypes.PHP_CLASS_USAGE),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    if (!(element instanceof LattePhpClassUsage)) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    if (element.getTextLength() > 0) {
                        String name = element.getText();
                        TextRange range = new TextRange(name.startsWith("\\") && name.length() > 1 ? 1 : 0, name.length());
                        return new PsiReference[]{new LattePhpClassReference((LattePhpClassUsage) element, range)};
                    }

                    return PsiReference.EMPTY_ARRAY;
                }
            });

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(LattePhpNamespaceReference.class).withLanguage(LatteLanguage.INSTANCE),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    if (!(element instanceof LattePhpNamespaceReference)) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    String name = element.getText();
                    if (name != null && name.length() > 0) {
                        return new PsiReference[]{
                            new LatteNamespaceReference((LattePhpNamespaceReference) element, new TextRange(0, name.length()))
                        };
                    }
                    return PsiReference.EMPTY_ARRAY;
                }
            });

        registrar.registerReferenceProvider(
            PlatformPatterns.or(
                PlatformPatterns.psiElement(LatteTypes.MACRO_OPEN_TAG),
                PlatformPatterns.psiElement(LatteTypes.MACRO_CLOSE_TAG)
            ),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    if (!(element instanceof LatteMacroTag)) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    int valueLength = ((LatteMacroTag) element).getMacroNameLength();
                    if (valueLength == 0) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    int offset = 1;
                    int length = element instanceof LatteMacroCloseTag ? 1 : 0;

                    return new PsiReference[]{new LatteMacroTagReference((LatteMacroTag) element, new TextRange(offset + length, offset + valueLength + length))};
                }
            });

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(LatteTypes.MACRO_OPEN_TAG),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    if (!(element instanceof LatteMacroTag tag) || !tag.getMacroName().equals("asset")) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    LatteMacroContent content = tag.getMacroContent();
                    LattePhpString path = content == null ? null : PsiTreeUtil.findChildOfType(content, LattePhpString.class);
                    if (path == null) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    TextRange range = getAssetPathRange(path.getText());
                    if (range == null) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    // The reference hangs on the tag rather than on the string, because the tag is
                    // the element the platform asks for references - the string is a plain
                    // generated one and is never asked.
                    int offset = path.getTextRange().getStartOffset() - element.getTextRange().getStartOffset();
                    return new PsiReference[]{new LatteAssetReference(element, range.shiftRight(offset))};
                }
            });

        registrar.registerReferenceProvider(
            PlatformPatterns.or(
                PlatformPatterns.psiElement(LatteTypes.MACRO_MODIFIER)
            ),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    if (!(element instanceof LatteMacroModifier)) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    LatteMacroModifier constantElement = (LatteMacroModifier) element;
                    PsiElement textElement = ((LatteMacroModifier) element).getTextElement();
                    if (textElement != null && textElement.getTextLength() > 0) {
                        return new PsiReference[]{new LatteFilterReference(constantElement, new TextRange(0, textElement.getTextLength()))};
                    }
                    return PsiReference.EMPTY_ARRAY;
                }
            });
    }

    /**
     * The path inside a string argument of {@code {asset}}, as a range of that string element: the
     * quotes are outside it and so is the mapper name in front of the path, because
     * {@code 'vite:assets/app.ts'} names the mapper "vite" and the file "assets/app.ts".
     *
     * <p>Null for anything that is not a plain path written out in full - an unterminated literal,
     * an empty one, a name put together while rendering. Nothing is looked up here; that belongs in
     * the reference itself.
     */
    @Nullable
    private static TextRange getAssetPathRange(@NotNull String text) {
        if (text.length() < 3) {
            return null;
        }

        char quote = text.charAt(0);
        if ((quote != '\'' && quote != '"') || text.charAt(text.length() - 1) != quote) {
            return null;
        }

        String value = text.substring(1, text.length() - 1);
        if (value.isEmpty() || value.indexOf(quote) >= 0 || value.indexOf('$') >= 0 || value.indexOf('{') >= 0) {
            return null;
        }

        int colon = value.indexOf(':');
        int slash = value.indexOf('/');
        int pathStart = colon > 0 && (slash < 0 || colon < slash) ? colon + 1 : 0;
        if (pathStart >= value.length()) {
            return null;
        }

        return new TextRange(1 + pathStart, 1 + value.length());
    }

    @Nullable
    private PsiReferenceBase<PsiElement> getXmlReferenceByTag(@NotNull String tag, XmlAttributeValue element) {
        String text = element.getValue();
        if (text.length() == 0) {
            return null;
        }

        TextRange range = new TextRange(1, text.length() + 1);
        switch (tag) {
            case "filter":
                return new LatteXmlFilterDeclarationReference(element, range);
            case "function":
                return new LatteXmlFunctionDeclarationReference(element, range);
        }
        return null;
    }
}
