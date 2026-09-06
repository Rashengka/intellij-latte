package dev.noctud.latte.version;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
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
import java.util.concurrent.atomic.AtomicBoolean;

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

	/** The group the plugin's notifications belong to; declared in plugin.xml under this id. */
	public static final String NOTIFICATION_GROUP = "Latte";

	private final Project project;

	/**
	 * Not final, and not a second constructor either: a project service is instantiated by the
	 * platform, which picks the constructor itself, and a class offering two is a class asking it
	 * to guess.
	 */
	private LatteVersionResolver.ComposerFileReader reader = new VirtualFileComposerReader();

	/** Directory and forced line to the version they resolve to. */
	private final Map<String, LatteVersion> resolved = new ConcurrentHashMap<>();

	/** Said once per project and session, because it is one fact and not a per-file one. */
	private final AtomicBoolean announcedNewerLatte = new AtomicBoolean();

	public LatteVersionService(@NotNull Project project) {
		this.project = project;
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

	/** For tests, which need to see whether a second question reaches the filesystem at all. */
	void readThrough(@NotNull LatteVersionResolver.ComposerFileReader replacement) {
		reader = replacement;
		resolved.clear();
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
		LatteVersion version = resolved.computeIfAbsent(
			directory + '\n' + (override == null ? "" : override),
			key -> LatteVersionResolver.resolve(reader, directory, override)
		);
		announceIfNewerThanKnown(version);
		return version;
	}

	/**
	 * Say once that the project's Latte is newer than the reference tables describe.
	 *
	 * The plugin behaves as the newest line it knows, which is the right thing to do - a Latte
	 * released after those tables were written adds and almost never removes, so refusing its
	 * constructions would report correct templates. Doing it silently is the half that is wrong:
	 * the developer would be working against an older idea of the language than the one they have
	 * installed, and nothing about that is visible from the outside.
	 */
	private void announceIfNewerThanKnown(@NotNull LatteVersion version) {
		if (version.isUndetermined() || announcedNewerLatte.get()) {
			return;
		}
		List<String> known = LatteLanguageReference.getInstance().getDocumentedLines();
		String line = version.line();
		if (known.isEmpty() || line == null) {
			return;
		}
		String newest = known.get(known.size() - 1);
		if (LatteAvailability.compare(line, newest) <= 0) {
			return;
		}
		if (!LatteSettings.getInstance(project).notifyWhenLatteIsNewerThanKnown) {
			return;
		}
		if (!announcedNewerLatte.compareAndSet(false, true)) {
			return;
		}
		Notification notification = new Notification(
			NOTIFICATION_GROUP,
			"Latte " + version + " is newer than this plugin's reference",
			"The plugin's tables describe Latte up to " + newest + ", and it behaves as that line."
				+ " Anything added in " + version + " is neither offered nor recognised - but nothing"
				+ " correct is reported as an error either.",
			NotificationType.INFORMATION
		);
		notification.addAction(new NotificationAction("Don't show again") {
			@Override
			public void actionPerformed(@NotNull AnActionEvent event, @NotNull Notification acted) {
				LatteSettings.getInstance(project).notifyWhenLatteIsNewerThanKnown = false;
				acted.expire();
			}
		});
		// Notifications.Bus rather than the message bus directly: highlighting asks for the version
		// off the event thread, and this is the entry point that knows how to get onto it.
		Notifications.Bus.notify(notification, project);
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
