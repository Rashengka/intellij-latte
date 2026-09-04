package dev.noctud.latte.utils;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class LatteIdeHelper {
    public static String NOTIFICATION_GROUP = "Latte";

    public static boolean holdsReadLock() {
        Application app = ApplicationManager.getApplication();
        try {
            return ((ApplicationEx) app).holdsReadLock();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static void openUrl(String url) {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();

            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    URI uri = new URI(url);
                    desktop.browse(uri);
                } catch (URISyntaxException | IOException ignored) {
                }
            }
        }
    }

    public static void doNotify(
        @NotNull String title,
        @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String content,
        @NotNull NotificationType type,
        @Nullable Project project
    ) {
        doNotify(title, content, type, project, null);
    }

    public static void doNotify(
        @NotNull String title,
        @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String content,
        @NotNull NotificationType type,
        @Nullable Project project,
        @Nullable NotificationAction notificationAction
    ) {
        doNotify(title, content, type, project, false, notificationAction);
    }

    public static Notification doNotify(
        @NotNull String title,
        @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String content,
        @NotNull NotificationType type,
        @Nullable Project project,
        boolean important,
        @Nullable NotificationAction notificationAction
    ) {
        Notification notification = new Notification(NOTIFICATION_GROUP, title, content, type);
        notification.setImportant(important);
        if (notificationAction != null) {
            notification.addAction(notificationAction);
        }
        doNotify(notification, project);
        return notification;
    }

    public static void doNotify(Notification notification, @Nullable Project project) {
        if (project != null && !project.isDisposed() && !project.isDefault()) {
            project.getMessageBus().syncPublisher(Notifications.TOPIC).notify(notification);
        } else {
            Application app = ApplicationManager.getApplication();
            if (!app.isDisposed()) {
                app.getMessageBus().syncPublisher(Notifications.TOPIC).notify(notification);
            }
        }
    }

    public static void navigateToPsiElement(@NotNull PsiElement psiElement) {
        final Navigatable descriptor = PsiNavigationSupport.getInstance().getDescriptor(psiElement);
        if (descriptor != null) {
            descriptor.navigate(true);
        }
    }

    public static XmlFile getXmlFileForPath(Project project, Path path) {
        PsiFile psiFile = getPsiFileForPath(project, path);
        return psiFile instanceof XmlFile ? (XmlFile) psiFile : null;
    }

    public static PsiFile getPsiFileForPath(Project project, Path path) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path.toString());
        if (virtualFile == null) {
            return null;
        }
        return PsiManager.getInstance(project).findFile(virtualFile);
    }
}
