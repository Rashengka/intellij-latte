package dev.noctud.latte.version;

/**
 * Where a resolved Latte version came from, and therefore how much may be concluded from it.
 *
 * The distinction is not bookkeeping: a version read from composer.lock is exact down to the
 * patch, so a diagnostic may depend on a patch-level boundary such as {@code {syntax single}}
 * or {@code n:else}. A version inferred from a constraint only names a line, so those same
 * diagnostics have to stay silent.
 */
public enum LatteVersionSource {

	/** The user chose the version in settings. Wins over everything, including a lock file. */
	OVERRIDE,

	/** Read from composer.lock. Exact, down to the patch. */
	LOCK_FILE,

	/** Inferred from the lower bound of a composer.json constraint. Line only. */
	CONSTRAINT,

	/** Nothing could be established. Not a synonym for a default - see {@link LatteVersion}. */
	UNDETERMINED,
}
