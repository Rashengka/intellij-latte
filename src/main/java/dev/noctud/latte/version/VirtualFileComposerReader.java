package dev.noctud.latte.version;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The resolver's filesystem, backed by the IDE's own.
 *
 * Reading goes through the virtual filesystem rather than through java.io so that a composer.lock
 * edited in the editor and not yet saved reads as what is on screen. A file that cannot be read is
 * the same answer as a file that is not there: the resolver moves on to the next ancestor, which
 * is what should happen for an unreadable file too.
 */
final class VirtualFileComposerReader implements LatteVersionResolver.ComposerFileReader {

	/** Enough for any real checkout, and a stop for a symlink loop or a path that keeps resolving. */
	private static final int MAX_ANCESTORS = 64;

	@Override
	public @Nullable String read(@NotNull String directory, @NotNull String fileName) {
		VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(directory);
		if (dir == null || !dir.isDirectory()) {
			return null;
		}
		VirtualFile file = dir.findChild(fileName);
		if (file == null || file.isDirectory()) {
			return null;
		}
		try {
			return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return null;
		}
	}

	@Override
	public @NotNull List<String> ancestorsOf(@NotNull String directory) {
		List<String> ancestors = new ArrayList<>();
		VirtualFile current = LocalFileSystem.getInstance().findFileByPath(directory);
		while (current != null && ancestors.size() < MAX_ANCESTORS) {
			if (current.isDirectory()) {
				ancestors.add(current.getPath());
			}
			current = current.getParent();
		}
		return ancestors;
	}
}
