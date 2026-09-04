package dev.noctud.latte.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Decides which Latte version applies to a given directory.
 *
 * The resolution order is: the user's override, then composer.lock, then a composer.json
 * constraint, then nothing. Each step is more of a guess than the one before it, and the last one
 * is not a guess at all - it says so.
 *
 * The walk is nearest-ancestor rather than project-root. A monorepo holding one Latte 2 package
 * and one Latte 3 package is ordinary, and starting from the file is what makes both of them
 * behave; in the single-package case it costs nothing.
 *
 * The filesystem arrives through {@link ComposerFileReader} rather than through IDE API, so the
 * whole decision can be exercised without a project.
 */
public final class LatteVersionResolver {

	private LatteVersionResolver() {
	}

	/** Reads Composer files without committing this class to any particular filesystem. */
	public interface ComposerFileReader {

		/** The file's content, or null when the directory does not hold it. */
		@Nullable String read(@NotNull String directory, @NotNull String fileName);

		/** The directory itself and every ancestor, nearest first. */
		@NotNull List<String> ancestorsOf(@NotNull String directory);
	}

	/**
	 * @param override the user's forced line such as "2.11", or null/blank to auto-detect. An
	 *                 unrecognised value falls back to detection rather than failing - a stale
	 *                 value in latte.xml should not stop the plugin from working.
	 */
	public static @NotNull LatteVersion resolve(
		@NotNull ComposerFileReader reader,
		@NotNull String startDirectory,
		@Nullable String override
	) {
		LatteVersion forced = fromOverride(override);
		if (!forced.isUndetermined()) {
			return forced;
		}

		for (String directory : reader.ancestorsOf(startDirectory)) {
			LatteVersion locked = LatteVersionParser.fromLockFile(reader.read(directory, "composer.lock"));
			if (!locked.isUndetermined()) {
				return locked;
			}
			LatteVersion declared = LatteVersionParser.fromComposerJson(reader.read(directory, "composer.json"));
			if (!declared.isUndetermined()) {
				return declared;
			}
		}
		return LatteVersion.undetermined();
	}

	/**
	 * A forced line carries no patch on purpose. The user picks a line, and the point of forcing
	 * one is to write for a version that is not installed - so being permissive within it is
	 * correct. See {@link LatteVersion#isAtLeast}.
	 */
	private static @NotNull LatteVersion fromOverride(@Nullable String override) {
		if (override == null || override.trim().isEmpty()) {
			return LatteVersion.undetermined();
		}
		LatteVersion parsed = LatteVersionParser.fromConstraint(override.trim());
		if (parsed.isUndetermined()) {
			return parsed;
		}
		return LatteVersion.of(parsed.getMajor(), parsed.getMinor(), null, LatteVersionSource.OVERRIDE);
	}
}
