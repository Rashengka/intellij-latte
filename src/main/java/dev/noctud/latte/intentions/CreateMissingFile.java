package dev.noctud.latte.intentions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Creates the template an {@code {include}} names but does not have.
 *
 * <p>The file is created through the virtual filesystem the directory it goes into belongs to,
 * rather than through java.io on a path built by hand: the same reason
 * {@link dev.noctud.latte.inspections.MissingFileInspection} looks the file up that way. A fix
 * that writes to the local disk cannot undo the report that offered it when the project is not
 * on the local disk.
 */
public class CreateMissingFile implements LocalQuickFix {
    private final @NotNull VirtualFile currentFile;
    private final @NotNull VirtualFile baseDir;
    private final @NotNull String relativePath;

    public CreateMissingFile(@NotNull VirtualFile currentFile, @NotNull VirtualFile baseDir, @NotNull String relativePath) {
        this.currentFile = currentFile;
        this.baseDir = baseDir;
        this.relativePath = relativePath;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return "Create file " + fileName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        if (baseDir.findFileByRelativePath(relativePath) != null) {
            return;
        }

        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                int lastSlash = relativePath.lastIndexOf('/');
                String directoryPath = lastSlash < 0 ? "" : relativePath.substring(0, lastSlash);
                // Looked up before being created, so that a path stepping out of the directory
                // with ".." lands in the directory above instead of in one named "..".
                VirtualFile parentDirectory = directoryPath.isEmpty() ? baseDir : baseDir.findFileByRelativePath(directoryPath);
                if (parentDirectory == null) {
                    parentDirectory = VfsUtil.createDirectoryIfMissing(baseDir, directoryPath);
                }
                if (parentDirectory == null) {
                    throw new IOException("Cannot create directory " + directoryPath + " in " + baseDir.getPath());
                }

                VirtualFile newFile = parentDirectory.createChildData(this, fileName());

                String firstLine = readFirstLine(currentFile);
                String content = firstLine.startsWith("{templateType") ? firstLine + "\n" : "";
                if (!content.isEmpty()) {
                    newFile.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
                }

                FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, newFile, content.length()), true);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    private @NotNull String fileName() {
        return relativePath.substring(relativePath.lastIndexOf('/') + 1);
    }

    private static String readFirstLine(@NotNull VirtualFile file) {
        CharSequence text = LoadTextUtil.loadText(file);
        for (int i = 0, n = text.length(); i < n; i++) {
            if (text.charAt(i) == '\n') {
                return text.subSequence(0, i).toString();
            }
        }
        return text.toString();
    }
}
