package dev.noctud.latte.settings;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FileContentUtilCore;
import dev.noctud.latte.LatteFileType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells the IDE that the custom tags, filters, functions or variables have changed.
 *
 * <p>Without this a template that is already open keeps reporting the old errors until it is edited
 * or reopened, which reads as the setting not having worked at all.
 *
 * <p>Restarting the highlighting pass on its own is not enough for tags: whether a tag is a pair one
 * is decided while the file is parsed (see LatteParserUtil.checkPairMacro), so the cached tree still
 * says the tag is unknown and its closing tag is a stray one. Only the open templates are reparsed -
 * that is what the user is looking at, and everything else is parsed afresh when it is opened.
 */
public final class LatteSettingsChangeNotifier {

    private LatteSettingsChangeNotifier() {
    }

    public static void definitionsChanged(@NotNull Project project) {
        List<VirtualFile> openTemplates = new ArrayList<>();
        for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
            if (file.getFileType() == LatteFileType.INSTANCE) {
                openTemplates.add(file);
            }
        }
        if (!openTemplates.isEmpty()) {
            FileContentUtilCore.reparseFiles(openTemplates);
        }
        DaemonCodeAnalyzer.getInstance(project).restart("Latte custom definitions changed");
    }
}
