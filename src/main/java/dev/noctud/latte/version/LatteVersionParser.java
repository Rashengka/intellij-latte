package dev.noctud.latte.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Latte version out of Composer files.
 *
 * No JSON library is used. The platform ships none, and pulling one in for a single field does not
 * survive the "would I write this myself in an hour?" test. What is needed here is narrow - one
 * package entry, one constraint string - so the reading is narrow too.
 */
public final class LatteVersionParser {

	private static final String PACKAGE = "latte/latte";

	/** The oldest line this plugin supports. Anything below it is not a version we can reason about. */
	private static final int FLOOR_MAJOR = 2;
	private static final int FLOOR_MINOR = 11;

	private static final Pattern LOCK_ENTRY = Pattern.compile(
		"\"name\"\\s*:\\s*\"" + Pattern.quote(PACKAGE) + "\"(.{0,4000}?)\"version\"\\s*:\\s*\"([^\"]+)\"",
		Pattern.DOTALL
	);
	private static final Pattern VERSION = Pattern.compile("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
	private static final Pattern REQUIRE_BLOCK = Pattern.compile(
		"\"(require|require-dev)\"\\s*:\\s*\\{(.*?)}", Pattern.DOTALL
	);

	private LatteVersionParser() {
	}

	/**
	 * The exact version from composer.lock, or undetermined.
	 *
	 * packages is searched before packages-dev. A Latte that is only a dev dependency is still the
	 * version the templates compile against in tests, so it is a better answer than none - but the
	 * result records where it came from so the difference stays visible.
	 *
	 * The lookup deliberately ignores what composer.json requires: most Nette projects get Latte
	 * through nette/application, and composer.lock lists it flat either way.
	 */
	public static @NotNull LatteVersion fromLockFile(@Nullable String content) {
		if (content == null || content.isEmpty()) {
			return LatteVersion.undetermined();
		}
		int devStart = indexOfKey(content, "packages-dev");
		String packages = devStart < 0 ? content : content.substring(0, devStart);
		String devPackages = devStart < 0 ? "" : content.substring(devStart);

		LatteVersion version = findInPackages(packages);
		if (!version.isUndetermined()) {
			return version;
		}
		LatteVersion devVersion = findInPackages(devPackages);
		return devVersion.isUndetermined() ? devVersion : devVersion.asDevDependency();
	}

	/**
	 * The line implied by a composer.json constraint, or undetermined. Never an exact version - a
	 * constraint is a range, and reporting its lower bound as a release would invent precision.
	 */
	public static @NotNull LatteVersion fromComposerJson(@Nullable String content) {
		if (content == null || content.isEmpty()) {
			return LatteVersion.undetermined();
		}
		String requireConstraint = null;
		String requireDevConstraint = null;
		String phpConstraint = null;

		Matcher blocks = REQUIRE_BLOCK.matcher(content);
		while (blocks.find()) {
			boolean isDev = "require-dev".equals(blocks.group(1));
			String latte = valueOf(blocks.group(2), PACKAGE);
			if (latte != null) {
				if (isDev) {
					requireDevConstraint = latte;
				} else {
					requireConstraint = latte;
				}
			}
			String php = valueOf(blocks.group(2), "php");
			if (php != null && !isDev) {
				phpConstraint = php;
			}
		}

		String constraint = requireConstraint != null ? requireConstraint : requireDevConstraint;
		LatteVersion version = fromConstraint(constraint);

		// A project pinned to PHP 7 cannot be running Latte 3. Contradictory input is not a
		// version we can report; the php requirement may only rule a line out, never pick one.
		if (!version.isUndetermined() && version.getMajor() >= 3 && !canRunLatte3(phpConstraint)) {
			return LatteVersion.undetermined();
		}
		return version;
	}

	/** The line implied by a single constraint string such as "^3.0", ">=2.11 <3.0" or "3.1.*". */
	public static @NotNull LatteVersion fromConstraint(@Nullable String constraint) {
		if (constraint == null) {
			return LatteVersion.undetermined();
		}
		Matcher matcher = VERSION.matcher(constraint.trim().replaceFirst("^[\\^~>=<\\s]+", ""));
		if (!matcher.find()) {
			return LatteVersion.undetermined();
		}
		int major = Integer.parseInt(matcher.group(1));
		int minor = Integer.parseInt(matcher.group(2));
		if (isBelowFloor(major, minor)) {
			return LatteVersion.undetermined();
		}
		return LatteVersion.of(major, minor, null, LatteVersionSource.CONSTRAINT);
	}

	/**
	 * Whether a php constraint leaves Latte 3 possible. Latte 3.0 needs PHP 8.0, so a project whose
	 * lower bound is below that cannot be on Latte 3. An absent or unparseable constraint tells us
	 * nothing, and therefore rules nothing out.
	 */
	public static boolean canRunLatte3(@Nullable String phpConstraint) {
		if (phpConstraint == null) {
			return true;
		}
		Matcher matcher = VERSION.matcher(phpConstraint.trim().replaceFirst("^[\\^~>=<\\s]+", ""));
		if (!matcher.find()) {
			return true;
		}
		return Integer.parseInt(matcher.group(1)) >= 8;
	}

	private static @NotNull LatteVersion findInPackages(@NotNull String content) {
		Matcher matcher = LOCK_ENTRY.matcher(content);
		if (!matcher.find()) {
			return LatteVersion.undetermined();
		}
		return parseExact(matcher.group(2));
	}

	/**
	 * A branch version such as "dev-master" or "3.x-dev" names no release. Guessing one would be
	 * worse than admitting the gap, because the answer turns into diagnostics later.
	 */
	private static @NotNull LatteVersion parseExact(@NotNull String raw) {
		if (raw.startsWith("dev-") || raw.endsWith("-dev")) {
			return LatteVersion.undetermined();
		}
		Matcher matcher = VERSION.matcher(raw);
		if (!matcher.find() || matcher.group(3) == null) {
			return LatteVersion.undetermined();
		}
		int major = Integer.parseInt(matcher.group(1));
		int minor = Integer.parseInt(matcher.group(2));
		if (isBelowFloor(major, minor)) {
			return LatteVersion.undetermined();
		}
		return LatteVersion.of(major, minor, Integer.parseInt(matcher.group(3)), LatteVersionSource.LOCK_FILE);
	}

	private static boolean isBelowFloor(int major, int minor) {
		return major < FLOOR_MAJOR || (major == FLOOR_MAJOR && minor < FLOOR_MINOR);
	}

	private static int indexOfKey(@NotNull String content, @NotNull String key) {
		return content.indexOf("\"" + key + "\"");
	}

	/** The value of one key inside an already-extracted JSON object body. */
	private static @Nullable String valueOf(@NotNull String objectBody, @NotNull String key) {
		Matcher matcher = Pattern
			.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
			.matcher(objectBody);
		return matcher.find() ? matcher.group(1) : null;
	}
}
