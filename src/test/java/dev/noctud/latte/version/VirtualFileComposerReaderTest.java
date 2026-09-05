package dev.noctud.latte.version;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * The filesystem half of version detection - the half the resolver's own tests cannot reach,
 * because they hand it a reader made of maps.
 *
 * Everything written here is invented. A composer.lock from a real project never enters this
 * repository, not even as a fragment.
 */
public class VirtualFileComposerReaderTest extends BasePlatformTestCase {

	private static final String LOCK_WITH_LATTE_3 = """
		{
		    "packages": [
		        { "name": "nette/utils", "version": "v4.0.7" },
		        { "name": "latte/latte", "version": "v3.1.6" }
		    ]
		}
		""";

	private File root;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		root = FileUtil.createTempDirectory("latte-version-probe", null);
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			FileUtil.delete(root);
		} finally {
			super.tearDown();
		}
	}

	private void write(File directory, String fileName, String content) throws Exception {
		assertTrue(directory.exists() || directory.mkdirs());
		Files.write(new File(directory, fileName).toPath(), content.getBytes(StandardCharsets.UTF_8));
		LocalFileSystem.getInstance().refreshAndFindFileByIoFile(directory);
	}

	@Test
	public void testAFileThatIsNotThereReadsAsNothingRatherThanFailing() throws Exception {
		VirtualFileComposerReader reader = new VirtualFileComposerReader();

		assertNull(reader.read(root.getPath(), "composer.lock"));
		assertNull(reader.read(root.getPath() + "/no-such-directory", "composer.lock"));
	}

	@Test
	public void testTheContentOfAComposerFileIsRead() throws Exception {
		write(root, "composer.lock", LOCK_WITH_LATTE_3);
		VirtualFileComposerReader reader = new VirtualFileComposerReader();

		String content = reader.read(root.getPath(), "composer.lock");
		assertNotNull(content);
		assertTrue(content.contains("latte/latte"));
	}

	@Test
	public void testTheWalkGoesUpwardsAndStartsAtTheDirectoryItself() throws Exception {
		File deep = new File(root, "app/Presentation/templates");
		write(deep, "keep.txt", "x");
		VirtualFileComposerReader reader = new VirtualFileComposerReader();

		List<String> ancestors = reader.ancestorsOf(deep.getPath());

		assertEquals(deep.getPath(), ancestors.get(0));
		assertTrue("the walk has to reach the directory holding composer.lock",
			ancestors.contains(root.getPath()));
	}

	/**
	 * The whole decision end to end: a lock file two directories above the template is what
	 * answers for it. This is the case the plugin actually meets - templates never sit next to
	 * composer.lock.
	 */
	@Test
	public void testTheNearestLockFileAboveTheTemplateAnswersForIt() throws Exception {
		write(root, "composer.lock", LOCK_WITH_LATTE_3);
		File templates = new File(root, "app/Presentation/templates");
		write(templates, "keep.txt", "x");

		LatteVersion version = LatteVersionResolver.resolve(
			new VirtualFileComposerReader(), templates.getPath(), null);

		assertEquals(3, version.getMajor());
		assertEquals(1, version.getMinor());
		assertEquals(LatteVersionSource.LOCK_FILE, version.getSource());
	}

	/** The developer's forced line beats what is installed, which is the point of having one. */
	@Test
	public void testAForcedLineWinsOverTheLockFile() throws Exception {
		write(root, "composer.lock", LOCK_WITH_LATTE_3);

		LatteVersion version = LatteVersionResolver.resolve(
			new VirtualFileComposerReader(), root.getPath(), "2.11");

		assertEquals(2, version.getMajor());
		assertEquals(11, version.getMinor());
		assertEquals(LatteVersionSource.OVERRIDE, version.getSource());
	}
}
