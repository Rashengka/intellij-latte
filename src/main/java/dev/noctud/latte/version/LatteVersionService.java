package dev.noctud.latte.version;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import dev.noctud.latte.settings.LatteSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Which Latte version applies to a file, for this project.
 *
 * The decision itself is {@link LatteVersionResolver} and has no IDE API in it; this is the part
 * that hands it the project's filesystem and its settings. The walk starts at the file rather than
 * at the project root because a monorepo holding one Latte 2 package and one Latte 3 package is
 * ordinary, and nearest-ancestor is what makes both of them behave.
 *
 * Nothing branches on the answer yet. Detection and the decisions taken from it are deliberately
 * separate rounds: until it is certain the version is determined correctly, acting on it can only
 * turn a wrong answer into a wrong report.
 */
public final class LatteVersionService {

	private final Project project;

	public LatteVersionService(@NotNull Project project) {
		this.project = project;
	}

	public static @NotNull LatteVersionService getInstance(@NotNull Project project) {
		return project.getService(LatteVersionService.class);
	}

	/**
	 * @param contextFile the file being looked at, or null when there is none - a settings page
	 *                    asking what detection found, say. Without a file the walk starts at the
	 *                    project's own directory, which is the best available answer.
	 */
	public @NotNull LatteVersion getVersion(@Nullable VirtualFile contextFile) {
		VirtualFile start = startDirectoryOf(contextFile);
		if (start == null) {
			return LatteVersion.undetermined();
		}
		return LatteVersionResolver.resolve(
			new VirtualFileComposerReader(),
			start.getPath(),
			LatteSettings.getInstance(project).latteVersionOverride
		);
	}

	private @Nullable VirtualFile startDirectoryOf(@Nullable VirtualFile contextFile) {
		if (contextFile != null) {
			return contextFile.isDirectory() ? contextFile : contextFile.getParent();
		}
		String basePath = project.getBasePath();
		return basePath == null ? null : com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(basePath);
	}
}
