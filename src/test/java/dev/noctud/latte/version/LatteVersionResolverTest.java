package dev.noctud.latte.version;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LatteVersionResolverTest {

	/**
	 * composer.lock in the directory the template sits in, shaped the way Composer writes one:
	 * packages sorted by name, and an earlier package requiring latte/latte in its own require map
	 * before the real entry appears.
	 */
	@Test
	public void testRealisticLockFileShape() {
		String lock = "{\n"
			+ "    \"_readme\": [\"This file locks the dependencies\"],\n"
			+ "    \"content-hash\": \"abc123\",\n"
			+ "    \"packages\": [\n"
			+ "        {\n"
			+ "            \"name\": \"contributte/application\",\n"
			+ "            \"version\": \"v0.6.0\",\n"
			+ "            \"require\": { \"php\": \">=8.1\", \"latte/latte\": \"^2.11\" }\n"
			+ "        },\n"
			+ "        {\n"
			+ "            \"name\": \"latte/latte\",\n"
			+ "            \"version\": \"v2.11.7\",\n"
			+ "            \"source\": { \"type\": \"git\" },\n"
			+ "            \"require\": { \"php\": \">=7.1 <8.4\" }\n"
			+ "        },\n"
			+ "        {\n"
			+ "            \"name\": \"nette/application\",\n"
			+ "            \"version\": \"v3.2.6\"\n"
			+ "        }\n"
			+ "    ],\n"
			+ "    \"packages-dev\": []\n"
			+ "}";
		Fs fs = new Fs().put("/app/templates", "composer.lock", lock);
		assertEquals("2.11.7", resolve(fs, "/app/templates").toString());
	}

	@Test
	public void testNearestAncestorWins() {
		Fs fs = new Fs()
			.put("/repo", "composer.lock", lock("v3.1.6"))
			.put("/repo/packages/legacy", "composer.lock", lock("v2.11.7"));
		assertEquals("2.11.7", resolve(fs, "/repo/packages/legacy/templates").toString());
		assertEquals("3.1.6", resolve(fs, "/repo/apps/web/templates").toString());
	}

	@Test
	public void testLockFileBeatsComposerJsonInTheSameDirectory() {
		Fs fs = new Fs()
			.put("/repo", "composer.lock", lock("v3.0.26"))
			.put("/repo", "composer.json", "{\"require\":{\"latte/latte\":\"^2.11\"}}");
		LatteVersion version = resolve(fs, "/repo/templates");
		assertEquals("3.0.26", version.toString());
		assertEquals(LatteVersionSource.LOCK_FILE, version.getSource());
	}

	/**
	 * A composer.json nearer to the file beats a composer.lock further away. The rule is
	 * nearest-ancestor, not "the most precise source anywhere" - in a monorepo the far-away lock
	 * belongs to a different package.
	 */
	@Test
	public void testNearerComposerJsonBeatsFartherLockFile() {
		Fs fs = new Fs()
			.put("/repo", "composer.lock", lock("v3.1.6"))
			.put("/repo/packages/legacy", "composer.json", "{\"require\":{\"latte/latte\":\"^2.11\"}}");
		LatteVersion version = resolve(fs, "/repo/packages/legacy/templates");
		assertEquals("2.11", version.toString());
		assertEquals(LatteVersionSource.CONSTRAINT, version.getSource());
	}

	@Test
	public void testNothingFoundIsUndetermined() {
		assertTrue(resolve(new Fs(), "/somewhere/templates").isUndetermined());
	}

	/**
	 * A composer.json that does not mention Latte must not stop the walk. The package may simply
	 * not declare it, while an ancestor's lock file knows the answer.
	 */
	@Test
	public void testWalkContinuesPastAComposerFileWithoutLatte() {
		Fs fs = new Fs()
			.put("/repo", "composer.lock", lock("v3.1.6"))
			.put("/repo/tools", "composer.json", "{\"require\":{\"php\":\"^8.2\"}}");
		assertEquals("3.1.6", resolve(fs, "/repo/tools/templates").toString());
	}

	@Test
	public void testOverrideWinsOverEverything() {
		Fs fs = new Fs().put("/repo", "composer.lock", lock("v2.11.7"));
		LatteVersion version = LatteVersionResolver.resolve(fs, "/repo/templates", "3.1");
		assertEquals("3.1", version.toString());
		assertEquals(LatteVersionSource.OVERRIDE, version.getSource());
	}

	@Test
	public void testOverrideIsUsedEvenWhenNothingIsInstalled() {
		LatteVersion version = LatteVersionResolver.resolve(new Fs(), "/nowhere", "2.11");
		assertEquals("2.11", version.toString());
		assertEquals(LatteVersionSource.OVERRIDE, version.getSource());
	}

	@Test
	public void testBlankOverrideMeansAutoDetect() {
		Fs fs = new Fs().put("/repo", "composer.lock", lock("v2.11.7"));
		assertEquals(LatteVersionSource.LOCK_FILE, LatteVersionResolver.resolve(fs, "/repo", "").getSource());
		assertEquals(LatteVersionSource.LOCK_FILE, LatteVersionResolver.resolve(fs, "/repo", null).getSource());
	}

	@Test
	public void testUnknownOverrideFallsBackToDetection() {
		Fs fs = new Fs().put("/repo", "composer.lock", lock("v2.11.7"));
		assertEquals("2.11.7", LatteVersionResolver.resolve(fs, "/repo", "nonsense").toString());
	}

	private LatteVersion resolve(Fs fs, String startDirectory) {
		return LatteVersionResolver.resolve(fs, startDirectory, null);
	}

	private String lock(String version) {
		return "{\"packages\":[{\"name\":\"latte/latte\",\"version\":\"" + version + "\"}],\"packages-dev\":[]}";
	}

	/** A directory tree that exists only in this test - no IDE, no filesystem. */
	private static final class Fs implements LatteVersionResolver.ComposerFileReader {

		private final Map<String, String> files = new LinkedHashMap<>();

		Fs put(String directory, String fileName, String content) {
			files.put(directory + "/" + fileName, content);
			return this;
		}

		@Override
		public String read(String directory, String fileName) {
			return files.get(directory + "/" + fileName);
		}

		@Override
		public List<String> ancestorsOf(String directory) {
			List<String> result = new ArrayList<>();
			String current = directory;
			while (current != null && !current.isEmpty()) {
				result.add(current);
				int slash = current.lastIndexOf('/');
				current = slash <= 0 ? null : current.substring(0, slash);
			}
			return result;
		}
	}
}
