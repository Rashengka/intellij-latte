package dev.noctud.latte.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A Latte version as far as the plugin was able to establish it.
 *
 * "Undetermined" is a state of its own, not a synonym for a default. The plugin reports only what
 * it can prove, so a version-specific diagnostic has nothing to stand on when nothing could be
 * established, and callers are expected to stay silent rather than fall back to a guess.
 *
 * The patch number is optional for the same reason: a constraint in composer.json names a line,
 * not a release, and treating its lower bound as an exact version would invent precision.
 */
public final class LatteVersion {

	private static final LatteVersion UNDETERMINED =
		new LatteVersion(0, 0, null, LatteVersionSource.UNDETERMINED, false);

	private final int major;
	private final int minor;
	private final @Nullable Integer patch;
	private final @NotNull LatteVersionSource source;
	private final boolean fromDevDependencies;

	private LatteVersion(
		int major,
		int minor,
		@Nullable Integer patch,
		@NotNull LatteVersionSource source,
		boolean fromDevDependencies
	) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.source = source;
		this.fromDevDependencies = fromDevDependencies;
	}

	public static @NotNull LatteVersion undetermined() {
		return UNDETERMINED;
	}

	public static @NotNull LatteVersion of(int major, int minor, @Nullable Integer patch, @NotNull LatteVersionSource source) {
		return new LatteVersion(major, minor, patch, source, false);
	}

	public @NotNull LatteVersion asDevDependency() {
		return new LatteVersion(major, minor, patch, source, true);
	}

	public boolean isUndetermined() {
		return source == LatteVersionSource.UNDETERMINED;
	}

	public @NotNull LatteVersionSource getSource() {
		return source;
	}

	/** True when the package was only in composer.lock's packages-dev array. */
	public boolean isFromDevDependencies() {
		return fromDevDependencies;
	}

	/** True when the patch number is known, and therefore when a patch-level boundary may be used. */
	public boolean hasPatchPrecision() {
		return patch != null;
	}

	/** The minor line, for example "2.11", or null when undetermined. */
	public @Nullable String line() {
		return isUndetermined() ? null : major + "." + minor;
	}

	public int getMajor() {
		return major;
	}

	public int getMinor() {
		return minor;
	}

	/**
	 * Whether this version is at least the given one.
	 *
	 * An unknown patch is treated as the **newest** patch of its line. A user who forced "3.0", or
	 * a project whose constraint only says "^3.0", is writing for that line as a whole, and telling
	 * them a filter added in 3.0.16 is unknown would be a false error. Callers that must not
	 * tolerate that guess should check {@link #hasPatchPrecision()} first and stay silent - see
	 * {@link LatteVersionSource}.
	 */
	public boolean isAtLeast(int otherMajor, int otherMinor, int otherPatch) {
		if (isUndetermined()) {
			return false;
		}
		if (major != otherMajor) {
			return major > otherMajor;
		}
		if (minor != otherMinor) {
			return minor > otherMinor;
		}
		return patch == null || patch >= otherPatch;
	}

	public boolean isAtLeast(int otherMajor, int otherMinor) {
		return isAtLeast(otherMajor, otherMinor, 0);
	}

	/** Whether this version belongs to the given minor line. */
	public boolean isLine(int otherMajor, int otherMinor) {
		return !isUndetermined() && major == otherMajor && minor == otherMinor;
	}

	@Override
	public String toString() {
		if (isUndetermined()) {
			return "undetermined";
		}
		return patch == null ? line() : major + "." + minor + "." + patch;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof LatteVersion)) {
			return false;
		}
		LatteVersion that = (LatteVersion) other;
		return major == that.major
			&& minor == that.minor
			&& Objects.equals(patch, that.patch)
			&& source == that.source
			&& fromDevDependencies == that.fromDevDependencies;
	}

	@Override
	public int hashCode() {
		return Objects.hash(major, minor, patch, source, fromDevDependencies);
	}
}
