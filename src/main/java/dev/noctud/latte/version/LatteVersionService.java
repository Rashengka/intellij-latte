package dev.noctud.latte.version;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import dev.noctud.latte.settings.LatteSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which Latte version applies to a file, for this project.
 *
 * The decision itself is {@link LatteVersionResolver} and has no IDE API in it; this is the part
 * that hands it the project's filesystem and its settings. The walk starts at the file rather than
 * at the project root because a monorepo holding one Latte 2 package and one Latte 3 package is
 * ordinary, and nearest-ancestor is what makes both of them behave.
 *
 * <p>The answer is remembered, and that is not an optimisation that could have been left for
 * later. Resolving reads whole Composer files - a real composer.lock is hundreds of kilobytes -
 * and runs a pattern over them. The registry asks this question once per tag while annotating and
 * once per keystroke while completing, so an unremembered answer would turn every version-aware
 * lookup into a file read.
 *
 * <p>What is remembered is dropped when a Composer file anywhere changes, which is the only thing
 * that can change the answer besides the user's forced line - and that one is part of the key
 * rather than something to watch, so a changed override simply misses.
 */
public final class LatteVersionService implements Disposable {

	private final Project project;

	private final LatteVersionResolver.ComposerFileReader reader;

	/** Directory and forced line to the version they resolve to. */
	private final Map<String, LatteVersion> resolved = new ConcurrentHashMap<>();

	public LatteVersionService(@NotNull Project project) {
		this(project, new VirtualFileComposerReader());
	}

	/** For tests, which need to see whether a second question reaches the filesystem at all. */
	LatteVersionService(@NotNull Project project, @NotNull LatteVersionResolver.ComposerFileReader reader) {
		this.project = project;
		this.reader = reader;
		project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
			@Override
			public void after(@NotNull List<? extends VFileEvent> events) {
				for (VFileEvent event : events) {
					if (isComposerFile(event.getPath())) {
						resolved.clear();
						return;
					}
				}
			}
		});
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
		String override = LatteSettings.getInstance(project).latteVersionOverride;
		String directory = start.getPath();
		return resolved.computeIfAbsent(
			directory + '\n' + (override == null ? "" : override),
			key -> LatteVersionResolver.resolve(reader, directory, override)
		);
	}

	@Override
	public void dispose() {
		resolved.clear();
	}

	/**
	 * A Composer file by name, wherever it sits. Being this coarse is deliberate: the alternative
	 * is to remember which directories were walked for which file, and a cache whose invalidation
	 * needs its own bookkeeping is how a stale answer becomes possible. Composer files change
	 * rarely, so throwing everything away when one does costs one resolution.
	 */
	private static boolean isComposerFile(@NotNull String path) {
		return path.endsWith("/composer.lock") || path.endsWith("/composer.json");
	}

	private @Nullable VirtualFile startDirectoryOf(@Nullable VirtualFile contextFile) {
		if (contextFile != null) {
			return contextFile.isDirectory() ? contextFile : contextFile.getParent();
		}
		String basePath = project.getBasePath();
		return basePath == null ? null : LocalFileSystem.getInstance().findFileByPath(basePath);
	}
}
