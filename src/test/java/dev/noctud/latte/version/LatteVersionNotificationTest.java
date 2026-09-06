package dev.noctud.latte.version;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.noctud.latte.settings.LatteSettings;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * That a Latte newer than the reference tables is said out loud, once.
 *
 * Behaving as the newest known line is deliberate and correct; doing it without a word is the
 * half that is not. A developer on a Latte the plugin has never heard of would otherwise get an
 * older idea of the language with nothing to tell them apart from a current one.
 */
public class LatteVersionNotificationTest extends BasePlatformTestCase {

	private List<Notification> seen;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		seen = new ArrayList<>();
		getProject().getMessageBus().connect(getTestRootDisposable())
			.subscribe(Notifications.TOPIC, new Notifications() {
				@Override
				public void notify(@NotNull Notification notification) {
					seen.add(notification);
				}
			});
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			LatteSettings.getInstance(getProject()).latteVersionOverride = "";
			LatteSettings.getInstance(getProject()).notifyWhenLatteIsNewerThanKnown = true;
		} finally {
			super.tearDown();
		}
	}

	/**
	 * Both versions have to be in it. "The plugin is out of date" is not actionable; "you have 3.9,
	 * I know up to 3.1" says what to expect and what to do about it.
	 */
	@Test
	public void testAVersionAboveTheTablesIsAnnouncedWithBothVersionsInIt() {
		LatteVersionService service = serviceUnder("3.9");

		service.getVersion(null);

		assertEquals(1, seen.size());
		Notification notification = seen.get(0);
		String text = notification.getTitle() + " " + notification.getContent();
		assertTrue("the version found has to be in it: " + text, text.contains("3.9"));
		String newest = LatteLanguageReference.getInstance().getDocumentedLines()
			.get(LatteLanguageReference.getInstance().getDocumentedLines().size() - 1);
		assertTrue("the version behaved as has to be in it: " + text, text.contains(newest));
	}

	/**
	 * The registry asks for the version once per tag, so anything said here is said thousands of
	 * times per file unless it is said once per project.
	 */
	@Test
	public void testItIsSaidOnceAndNotOncePerQuestion() {
		LatteVersionService service = serviceUnder("3.9");

		service.getVersion(null);
		service.getVersion(null);
		service.getVersion(null);

		assertEquals(1, seen.size());
	}

	/** A version the tables describe is not news. */
	@Test
	public void testAVersionTheTablesDescribeIsNotAnnounced() {
		LatteVersionService service = serviceUnder("3.1");

		service.getVersion(null);

		assertEquals(List.of(), seen);
	}

	/** Nothing is known, so there is nothing to compare and nothing to say. */
	@Test
	public void testAnUndeterminedVersionIsNotAnnounced() {
		LatteVersionService service = serviceUnder("");

		service.getVersion(null);

		assertEquals(List.of(), seen);
	}

	/** Turned off means silent, which is what the notification's own action turns it into. */
	@Test
	public void testItCanBeTurnedOff() {
		LatteSettings.getInstance(getProject()).notifyWhenLatteIsNewerThanKnown = false;
		LatteVersionService service = serviceUnder("3.9");

		service.getVersion(null);

		assertEquals(List.of(), seen);
	}

	private @NotNull LatteVersionService serviceUnder(@NotNull String line) {
		LatteSettings.getInstance(getProject()).latteVersionOverride = line;
		return new LatteVersionService(getProject());
	}
}
