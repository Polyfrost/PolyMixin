package org.spongepowered.asm.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.MixinConfig;
import org.spongepowered.asm.service.MixinService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class LocalsCompat {

    private static final VersionNumber NEW_LOCAL_VERSION = VersionNumber.parse("0.8.4");
    private static final VersionNumber LATEST_VERSION = VersionNumber.parse(MixinEnvironment.getCurrentEnvironment().getVersion());
    private static final String LOCAL_DECORATION = "polymixin-canUseNewLocals";
    private static boolean checkedLaunchwrapper = false;
    private static boolean isLaunchwrapper = false;

    private static final ThreadLocal<IMixinInfo> contextMixinInfo = new ThreadLocal<>();
    private static VersionNumber fallbackMixinVersion = null;

    public static <T> T withContext(IMixinInfo mixinInfo, Callable<T> callable) {
        IMixinInfo context = contextMixinInfo.get();

        try {
            contextMixinInfo.set(mixinInfo);
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException("Error while executing within Mixin context", e);
        } finally {
            contextMixinInfo.set(context);
        }
    }

    public static void withContext(IMixinInfo mixinInfo, Runnable runnable) {
        IMixinInfo context = contextMixinInfo.get();

        try {
            contextMixinInfo.set(mixinInfo);
            runnable.run();
        } catch (Exception e) {
            throw new RuntimeException("Error while executing within Mixin context", e);
        } finally {
            contextMixinInfo.set(context);
        }
    }

    public static boolean isLocalsAvailable(IMixinInfo context) {
        IMixinConfig config = context.getConfig();
        if (config.hasDecoration(LOCAL_DECORATION)) {
            return config.getDecoration(LOCAL_DECORATION);
        }

        boolean newLocals;
        try {
            newLocals = checkNewLocals(context);
        } catch (Exception e) {
            newLocals = true;
        }

        config.decorate(LOCAL_DECORATION, newLocals);
        return newLocals;
    }

    public static boolean isNewLocalsAvailable() {
        IMixinInfo currentContext = contextMixinInfo.get();
        return currentContext == null || isLocalsAvailable(currentContext);
    }

    private static boolean checkNewLocals(IMixinInfo context) throws Exception {
        IMixinConfig config = context.getConfig();

        if (config instanceof MixinConfig) {
            if (VersionNumber.parse(((MixinConfig) config).getVersion()).compareTo(NEW_LOCAL_VERSION) >= 0) {
                return true;
            }
        }

        if (isLaunchwrapper()) {
            String resourceName = context.getClassRef() + ".class";
            ClassLoader launchClassLoader = (ClassLoader) Class.forName("net.minecraft.launchwrapper.Launch").getDeclaredField("classLoader").get(null);
            URL url = (URL) ClassLoader.class.getDeclaredMethod("getResource", String.class).invoke(launchClassLoader, resourceName);
            if (url != null) {
                String jarSuffix = "!/" + resourceName;
                String file = url.getFile();
                if ("jar".equals(url.getProtocol()) && file.endsWith(jarSuffix)) {
                    URI uri = new URL(file.substring(0, file.lastIndexOf(jarSuffix))).toURI();
                    try (JarFile jar = new JarFile(new File(uri))) {
                        ZipEntry mixinBootstrap = jar.getEntry("org/spongepowered/asm/launch/MixinBootstrap.class");
                        if (mixinBootstrap != null) {
                            try (InputStream stream = jar.getInputStream(mixinBootstrap)) {
                                VersionNumber bundled = getMixinVersion(stream);
                                if (bundled != null) {
                                    return bundled.compareTo(NEW_LOCAL_VERSION) >= 0;
                                }
                            }
                        }
                    } catch (Throwable ignored) {

                    }
                }
            }
        }

        return getFallbackMixinVersion().compareTo(NEW_LOCAL_VERSION) >= 0;
    }

    private static VersionNumber getMixinVersion(InputStream mixinBootstrapStream) throws IOException {
        ClassNode node = new ClassNode();
        ClassReader reader = new ClassReader(mixinBootstrapStream);
        reader.accept(node, ClassReader.SKIP_CODE);

        for (FieldNode field : node.fields) {
            if (field.name.equals("VERSION") && field.value instanceof String) {
                return VersionNumber.parse((String) field.value);
            }
        }

        return null;
    }

    private static VersionNumber getFallbackMixinVersion() {
        if (fallbackMixinVersion != null) {
            return fallbackMixinVersion;
        }

        try {
            for (URL source : MixinService.getService().getClassProvider().getClassPath()) {
                URI uri = source.toURI();
                if (!"file".equals(uri.getScheme())) {
                    continue;
                }

                File file = new File(uri);
                if (file.isFile() && file.getName().endsWith(".jar")) {
                    try (JarFile jar = new JarFile(file)) {
                        if (jar.getEntry(LocalsCompat.class.getName()) == null) {
                            ZipEntry entry = jar.getEntry("org/spongepowered/asm/launch/MixinBootstrap.class");
                            if (entry != null) {
                                try (InputStream stream = jar.getInputStream(entry)) {
                                    return fallbackMixinVersion = getMixinVersion(stream);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {

        }

        return fallbackMixinVersion = LATEST_VERSION;
    }

    private static boolean isLaunchwrapper() {
        if (checkedLaunchwrapper) {
            return isLaunchwrapper;
        }

        try {
            Class.forName("net.minecraft.launchwrapper.Launch");
            isLaunchwrapper = true;
        } catch (ClassNotFoundException e) {
            isLaunchwrapper = false;
        }

        checkedLaunchwrapper = true;
        return isLaunchwrapper;
    }

}